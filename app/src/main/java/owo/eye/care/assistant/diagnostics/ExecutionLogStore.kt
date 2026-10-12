package owo.eye.care.assistant.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** 私有本機事件紀錄。單一背景寫入器、有限佇列、有限筆數；不記錄 PIN 或影像。 */
object ExecutionLogStore {
    private const val MAX_LINES = 300
    private const val MAX_BYTES = 64 * 1024
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val writer = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue<Runnable>(128),
        { task -> Thread(task, "ExecutionLogWriter").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy()
    )
    private val lines = ExecutionLogBuffer(MAX_LINES, MAX_BYTES)
    private val transitions = HashMap<String, String>()
    private var loaded = false
    @Volatile private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
        event("系統", "APP 程序啟動")
        importExitReasons()
    }

    private fun submit(onFailure: () -> Unit = {}, task: () -> Unit) {
        try {
            writer.execute {
                try { task() } catch (_: Exception) { mainHandler.post { onFailure() } }
            }
        } catch (_: Exception) { mainHandler.post { onFailure() } }
    }

    private fun storage(): AtomicFile {
        val context = appContext ?: error("Log context not initialized")
        val directory = File(context.filesDir, "ExecutionLogs")
        if (!directory.exists() && !directory.mkdirs()) error("Cannot create log directory")
        return AtomicFile(File(directory, "Execution.log"))
    }

    private fun load() {
        if (loaded) return
        val file = storage()
        try {
            if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) {
                val bytes = file.openRead().use { it.readBytes() }
                lines.addAll(bytes.toString(Charsets.UTF_8).lineSequence().filter { it.isNotBlank() }.takeLastBounded(MAX_LINES))
            }
        } catch (_: Exception) { }
        loaded = true
    }

    private fun Sequence<String>.takeLastBounded(limit: Int): List<String> {
        val queue = java.util.ArrayDeque<String>()
        for (line in this) { queue.addLast(line); if (queue.size > limit) queue.removeFirst() }
        return queue.toList()
    }

    private fun persist() {
        val file = storage()
        val stream = file.startWrite()
        try {
            stream.write(lines.snapshot(false).joinToString("\n", postfix = if (lines.isEmpty()) "" else "\n").toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) { file.failWrite(stream); throw error }
    }

    private fun append(category: String, message: String, timestamp: Long = System.currentTimeMillis()) {
        load()
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.TAIWAN).format(Date(timestamp))
        val clean = message.replace('\n', ' ').replace('\r', ' ').take(600)
        lines.add("$time [$category] $clean")
        persist()
        notifyChanged()
    }

    fun event(category: String, message: String) { submit { append(category, message) } }

    /** 只記錄狀態變化，避免相機每個影格重複寫檔。 */
    fun transition(key: String, category: String, value: String) {
        submit {
            if (transitions[key] != value) {
                transitions[key] = value
                append(category, value)
            }
        }
    }

    /** 不寫入 exception.message，避免錯誤訊息帶入使用者輸入。 */
    fun error(component: String, error: Throwable) {
        val location = error.stackTrace.firstOrNull { it.className.startsWith("owo.eye.care.assistant") }
        val detail = "${error.javaClass.simpleName}" +
            (location?.let { "；${it.fileName}:${it.lineNumber}" } ?: "")
        event("錯誤", "$component：$detail")
    }

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    private fun notifyChanged() {
        for (listener in listeners) mainHandler.post { if (listeners.contains(listener)) listener() }
    }

    fun read(newestFirst: Boolean = true, callback: (String) -> Unit) {
        submit(onFailure = { callback("執行紀錄暫時無法讀取，請重新開啟主畫面") }) {
            load()
            val text = lines.snapshot(newestFirst).joinToString("\n")
            mainHandler.post { callback(text) }
        }
    }

    /** 清除只影響 APP 本機紀錄，不刪系統歷史；舊退出事件不重新匯入。 */
    fun clear(callback: (Boolean) -> Unit) {
        submit(onFailure = { callback(false) }) {
            load()
            val context = appContext ?: return@submit
            val saved = context.getSharedPreferences("execution_log_meta", Context.MODE_PRIVATE)
                .edit().putLong("exit_cutoff", System.currentTimeMillis()).remove("exit_ids").commit()
            if (!saved) { mainHandler.post { callback(false) }; return@submit }
            lines.clear()
            transitions.clear()
            persist()
            notifyChanged()
            mainHandler.post { callback(true) }
        }
    }

    fun importExitReasons() {
        submit {
            val context = appContext ?: return@submit
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                if (transitions["exit_support"] == null) {
                    transitions["exit_support"] = "unsupported"
                    append("診斷", "Android 版本低於 11，無法讀取系統程序退出原因")
                }
                return@submit
            }
            try {
                val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val prefs = context.getSharedPreferences("execution_log_meta", Context.MODE_PRIVATE)
                val cutoff = prefs.getLong("exit_cutoff", 0L)
                val seen = prefs.getStringSet("exit_ids", emptySet())!!.toMutableSet()
                if (!ActivityManager.isLowMemoryKillReportSupported() && transitions["low_memory_support"] == null) {
                    transitions["low_memory_support"] = "unsupported"
                    append("診斷", "此裝置不保證回報 LOW_MEMORY；其他或未知原因不能直接判定為省電")
                }
                val records = manager.getHistoricalProcessExitReasons(context.packageName, 0, 20)
                var added = false
                for (record in records.sortedBy { it.timestamp }) {
                    val id = "${record.timestamp}:${record.pid}:${record.reason}"
                    if (record.timestamp <= cutoff || id in seen) continue
                    // 系統分類不等於已證實無障礙斷線根因；不推測為省電。
                    val text = "程序=${record.processName}；PID=${record.pid}；原因=${reasonName(record.reason)} (${record.reason})；" +
                        "狀態碼=${record.status}；PSS=${record.pss} KB；RSS=${record.rss} KB；此紀錄不直接證明無障礙失效原因"
                    append("系統退出", text, record.timestamp)
                    seen.add(id)
                    added = true
                }
                if (added) prefs.edit().putStringSet("exit_ids", seen.sorted().takeLast(60).toSet()).commit()
                if (records.isEmpty() && transitions["exit_empty"] == null) {
                    transitions["exit_empty"] = "empty"
                    append("診斷", "系統目前沒有可讀取的程序退出紀錄，不代表程序從未被終止")
                }
            } catch (error: Exception) {
                val key = error.javaClass.simpleName
                if (transitions["exit_error"] != key) {
                    transitions["exit_error"] = key
                    append("診斷", "讀取系統退出原因失敗：$key")
                }
            }
        }
    }

    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_LOW_MEMORY -> "記憶體不足"
        ApplicationExitInfo.REASON_CRASH -> "程式例外崩潰"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "原生程式庫崩潰"
        ApplicationExitInfo.REASON_ANR -> "程式無回應"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "使用者要求停止"
        ApplicationExitInfo.REASON_USER_STOPPED -> "使用者環境停止"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "權限變更"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "初始化失敗"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "資源使用過量"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "依賴程序終止"
        ApplicationExitInfo.REASON_EXIT_SELF -> "程序自行退出"
        ApplicationExitInfo.REASON_SIGNALED -> "收到系統訊號，需進一步判讀"
        ApplicationExitInfo.REASON_OTHER -> "其他系統原因，未確認為省電"
        else -> "其他或未知原因，需進一步判讀"
    }
}
