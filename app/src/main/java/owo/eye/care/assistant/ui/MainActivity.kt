package owo.eye.care.assistant.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.text.method.ScrollingMovementMethod
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.MotionEvent
import owo.eye.care.assistant.diagnostics.ExecutionLogStore
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import owo.eye.care.assistant.data.DistanceStateStore
import owo.eye.care.assistant.data.RulesStore
import owo.eye.care.assistant.data.ReminderMode
import owo.eye.care.assistant.databinding.ActivityMainBinding
import owo.eye.care.assistant.service.DistanceForegroundService

class MainActivity : AppCompatActivity() {
    private lateinit var vb: ActivityMainBinding
    private lateinit var rules: RulesStore
    private lateinit var distanceState: DistanceStateStore
    private var executionLogVisible = false
    private var executionLogReadPending = false
    private var executionLogDirty = false
    private var lastLogTouchY = 0f
    private val executionLogListener: () -> Unit = { refreshExecutionLog() }

    private fun refreshExecutionLog() {
        if (!executionLogVisible) return
        if (executionLogReadPending) { executionLogDirty = true; return }
        executionLogReadPending = true
        ExecutionLogStore.read { text ->
            executionLogReadPending = false
            if (executionLogVisible) {
                vb.tvExecutionLog.text = if (text.isEmpty()) "尚無執行紀錄" else formatExecutionLog(text)
                vb.tvExecutionLog.scrollTo(0, 0)
                if (executionLogDirty) {
                    executionLogDirty = false
                    refreshExecutionLog()
                }
            }
        }
    }

    /** 舊版已保存的類別也會在顯示時歸入四個兩字類別。 */
    private fun logCategory(raw: String): String = when (raw) {
        "距離", "相機", "校正", "感測" -> "感測"
        "提醒", "遮罩", "警示" -> "警示"
        "錯誤", "診斷", "系統退出", "退出", "異常" -> "異常"
        else -> "系統"
    }

    private fun formatExecutionLog(text: String): CharSequence {
        val result = SpannableStringBuilder()
        text.lineSequence().filter { it.isNotBlank() }.forEachIndexed { index, line ->
            val match = Regex("""^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}) \[(.+?)] (.*)$""").matchEntire(line)
            if (index > 0) result.append('\n')
            if (match == null) {
                result.append(line)
            } else {
                val (time, rawCategory, message) = match.destructured
                val category = logCategory(rawCategory)
                result.append(time).append("  ")
                val start = result.length
                result.append(" $category ")
                val end = result.length
                val colors = when (category) {
                    "感測" -> Color.parseColor("#E2F5EC") to Color.parseColor("#137A4E")
                    "警示" -> Color.parseColor("#FFF0D8") to Color.parseColor("#A85B00")
                    "異常" -> Color.parseColor("#FDE4E4") to Color.parseColor("#B42318")
                    else -> Color.parseColor("#E6EDFF") to Color.parseColor("#3157D5")
                }
                result.setSpan(BackgroundColorSpan(colors.first), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                result.setSpan(ForegroundColorSpan(colors.second), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                result.setSpan(StyleSpan(android.graphics.Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                result.append("  ").append(message)
            }
        }
        return result
    }

    private fun setupExecutionLog() {
        vb.tvExecutionLog.movementMethod = ScrollingMovementMethod()
        vb.tvExecutionLog.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> lastLogTouchY = event.y
                MotionEvent.ACTION_MOVE -> {
                    val direction = if (event.y < lastLogTouchY) 1 else -1
                    view.parent.requestDisallowInterceptTouchEvent(view.canScrollVertically(direction))
                    lastLogTouchY = event.y
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.parent.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
        vb.btnCopyExecutionLog.setOnClickListener {
            ExecutionLogStore.read(newestFirst = false) { text ->
                if (!executionLogVisible) return@read
                if (text.isEmpty()) { showToast("尚無執行紀錄"); return@read }
                try {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("OwO 執行紀錄", text))
                    showToast("執行紀錄已複製")
                } catch (error: Exception) { showToast("複製失敗"); ExecutionLogStore.error("複製紀錄", error) }
            }
        }
        vb.btnClearExecutionLog.setOnClickListener {
            ExecutionLogStore.clear { success ->
                if (executionLogVisible) {
                    refreshExecutionLog()
                    showToast(if (success) "執行紀錄已清除" else "清除紀錄失敗，請重試")
                }
            }
        }
    }

    private val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())
    
    private val distanceUiRunnable = object : Runnable {
        override fun run() {
            val cm = distanceState.getLastDistanceCm()
            val profile = distanceState.getCurrentProfile()
            val ref = distanceState.getProfile(profile)
            val isRun = distanceState.isServiceRunning()
            val paused = distanceState.isDistancePaused()
            val error = distanceState.getCameraError()
            val wPx = distanceState.getLastFaceWidthPx()
            val status = when {
                !isRun -> "未啟動"
                paused -> "待機暫停"
                error.isNotEmpty() -> "相機異常：$error"
                else -> distanceState.getDistanceStatus()
            }
            vb.tvDistanceStatus.text = "守護距離狀態：\n" +
                "服務狀態：$status\n" +
                "當前臉部：${if (isRun && !paused && wPx > 0) "已偵測 ($wPx px)" else "—"}\n" +
                "估算距離：${if (isRun && !paused && error.isEmpty() && cm > 0) "約 $cm 公分" else "—"}\n" +
                "方向校正：${if (ref > 0) "已完成 ($ref px)" else "尚未完成，請開啟設定護眼基準線"}\n" +
                "影像基準：${profile.ifEmpty { "—" }}"
            uiHandler.postDelayed(this, 500L)
        }
    }

    private val requestUiCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshUi()
    }

    private val requestCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startDistanceService() else showToast("請開相機喲 \uD83D\uDCF7")
    }

    private val requestCalibrationCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openCalibration() else showToast("請開相機喲 \uD83D\uDCF7")
    }

    private fun openCalibration() {
        startActivity(Intent(this, CalibrationActivity::class.java))
    }

    private fun showToast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vb = ActivityMainBinding.inflate(layoutInflater)
        setContentView(vb.root)
        setupExecutionLog()

        vb.root.setOnTouchListener { _, _ ->
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            currentFocus?.windowToken?.let { imm.hideSoftInputFromWindow(it, 0) }
            currentFocus?.clearFocus()
            false
        }

        val imeListener = android.widget.TextView.OnEditorActionListener { v, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
                v.clearFocus()
                true
            } else {
                false
            }
        }
        vb.etLimitMinutes.setOnEditorActionListener(imeListener)
        vb.etPin.setOnEditorActionListener(imeListener)
        vb.etStopPin.setOnEditorActionListener(imeListener)
        
        rules = RulesStore(this)
        distanceState = DistanceStateStore(this)

        vb.btnOpenAccessibility.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        vb.btnOpenOverlay.setOnClickListener { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }

        vb.btnOpenCamera.setOnClickListener {
            val hasCam = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (!hasCam) requestUiCamera.launch(Manifest.permission.CAMERA) else showToast("相機已就緒 \u2728")
        }
        
        vb.btnToggleMode.setOnClickListener {
            val modes = ReminderMode.selectionOrder()
            val currentIndex = modes.indexOf(rules.getProtectionMode()).coerceAtLeast(0)
            val newMode = modes[(currentIndex + 1) % modes.size]
            rules.setProtectionMode(newMode)
            if (!ReminderMode.usesPin(newMode)) distanceState.setBlocked(false)
            ExecutionLogStore.event("設定", "提醒模式：${ReminderMode.label(newMode)}")
            showToast("${ReminderMode.label(newMode)} ✨")
            refreshUi()
        }

        vb.etLimitMinutes.setText((rules.getCycleLimitSeconds() / 60).toString())

        vb.btnSaveSettings.setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            currentFocus?.windowToken?.let { imm.hideSoftInputFromWindow(it, 0) }
            currentFocus?.clearFocus()
            val limitMin = vb.etLimitMinutes.text?.toString()?.toIntOrNull()
            if (limitMin == null || limitMin !in 1..1440) { showToast("須 1 ~ 1440 分 \u23F1\uFE0F"); return@setOnClickListener }
            rules.setCycleLimitSeconds(limitMin * 60)
            vb.etPin.text?.toString()?.takeIf { it.isNotBlank() }?.let { if (it.length < 4) showToast("解除需 4 碼 \uD83D\uDD12") else { rules.setParentPin(it); vb.etPin.setText("") } }
            vb.etStopPin.text?.toString()?.takeIf { it.isNotBlank() }?.let { if (it.length < 4) showToast("停止需 4 碼 \uD83D\uDEE1\uFE0F") else { rules.setStopPin(it); vb.etStopPin.setText("") } }
            ExecutionLogStore.event("設定", "儲存設定操作完成（不記錄 PIN）")
            showToast("儲存成功囉 \uD83C\uDF89")
            refreshUi()
        }

        vb.btnToggleControl.setOnClickListener {
            if (!rules.isControlEnabled()) {
                if (!rules.hasParentPin()) { showToast("請設 PIN 碼 \uD83D\uDD11"); return@setOnClickListener }
                if (!isAccessibilityEnabled()) { showToast("請開無障礙 \uD83E\uDD16"); return@setOnClickListener }
                if (!Settings.canDrawOverlays(this)) { showToast("請開上層權限 \uD83D\uDCF1"); return@setOnClickListener }
                rules.setControlEnabled(true)
                ExecutionLogStore.event("控管", "護眼控管啟動")
                showToast("護眼魔法啟動 \u2728")
                refreshUi()
            } else {
                if (rules.hasStopPin()) {
                    showVerifyStopPinDialog("解除護眼魔法 (OwO) \uD83E\uDE84") {
                        rules.setControlEnabled(false)
                        ExecutionLogStore.event("控管", "護眼控管停止")
                        showToast("護眼魔法解除 \uD83D\uDCA4")
                        refreshUi()
                    }
                } else showToast("請設停止碼 \uD83D\uDEE1\uFE0F")
            }
        }

        vb.btnCalibrate30.setOnClickListener {
            val hasCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (!hasCamera) { showToast("請開相機喲 \uD83D\uDCF7"); return@setOnClickListener }
            openCalibration()
        }

        vb.btnToggleDistance.setOnClickListener {
            if (!distanceState.isServiceRunning()) {
                val hasCam = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                if (!hasCam) { showToast("請開相機喲 \uD83D\uDCF7"); return@setOnClickListener }
                
                if (distanceState.getRefFaceWidthPxAt30cm() <= 0) {
                    showToast("請先設基準線 \uD83D\uDCCF")
                    return@setOnClickListener
                }
                startDistanceService()
            } else {
                if (rules.hasStopPin()) {
                    showVerifyStopPinDialog("解除距離偵測 (OwO) \uD83D\uDEE1\uFE0F") {
                        stopService(Intent(this, DistanceForegroundService::class.java))
                        distanceState.setServiceRunning(false)
                        distanceState.setBlocked(false)
                        showToast("距離偵測解除 \uD83D\uDEE1\uFE0F")
                        refreshUi()
                    }
                } else showToast("請設停止碼 \uD83D\uDEE1\uFE0F")
            }
        }

        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        executionLogVisible = true
        ExecutionLogStore.addListener(executionLogListener)
        ExecutionLogStore.importExitReasons()
        refreshExecutionLog()
        refreshUi()
        uiHandler.post(distanceUiRunnable)
    }
    override fun onPause() {
        executionLogVisible = false
        ExecutionLogStore.removeListener(executionLogListener)
        uiHandler.removeCallbacksAndMessages(null)
        super.onPause()
    }

    private fun startDistanceService() {
        ContextCompat.startForegroundService(this, Intent(this, DistanceForegroundService::class.java))
        distanceState.setServiceRunning(true)
        showToast("守護眼睛中 \uD83C\uDF1F")
        refreshUi()
    }

    private fun showVerifyStopPinDialog(title: String, onSuccess: () -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setPadding(60, 60, 60, 60)
            hint = "請輸入密碼"
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("確定") { _, _ ->
                if (rules.verifyStopPin(input.text.toString())) {
                    onSuccess()
                } else showToast("密碼錯誤 \uD83D\uDE3F")
            }.setNegativeButton("取消", null).show()
    }

    private fun refreshUi() {
        vb.etPin.hint = if (rules.hasParentPin()) "******" else "（空白）"
        vb.etStopPin.hint = if (rules.hasStopPin()) "******" else "（空白）"
        ExecutionLogStore.transition("accessibility_setting", "權限", if (isAccessibilityEnabled()) "無障礙設定已開啟（不代表服務已連線）" else "無障礙設定尚未開啟")
        vb.tvAccStatus.text = "無障礙服務：${if (isAccessibilityEnabled()) "已開啟" else "尚未開啟"}"
        vb.tvOverlayStatus.text = "顯示在其他應用程式上層：${if (Settings.canDrawOverlays(this)) "已允許" else "尚未允許"}"

        val hasCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        vb.tvCameraStatus.text = "相機權限：${if (hasCamera) "已允許" else "尚未允許"}"
        
        vb.btnToggleMode.text = "提醒模式（點擊切換）：${ReminderMode.label(rules.getProtectionMode())}"
        
        if (rules.isControlEnabled()) {
            vb.btnToggleControl.text = "解除護眼魔法 (OwO)"
            vb.btnToggleControl.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E53935"))
            vb.btnToggleControl.setTextColor(Color.WHITE)
        } else {
            vb.btnToggleControl.text = "啟動護眼魔法 (OwO) \uD83E\uDE84"
            vb.btnToggleControl.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
            vb.btnToggleControl.setTextColor(Color.WHITE)
        }

        vb.btnCalibrate30.text = "設定護眼基準線 \uD83E\uDE84"
        
        if (distanceState.isServiceRunning()) {
            vb.btnToggleDistance.text = "解除距離偵測"
            vb.btnToggleDistance.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E53935"))
            vb.btnToggleDistance.setTextColor(Color.WHITE)
        } else {
            vb.btnToggleDistance.text = "啟動距離偵測 (OwO) \uD83D\uDEE1\uFE0F"
            vb.btnToggleDistance.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
            vb.btnToggleDistance.setTextColor(Color.WHITE)
        }

        vb.tvProtectionStatus.text = "循環時間：${rules.getCycleLimitSeconds() / 60} 分鐘\n保護控管：${if (rules.isControlEnabled()) "已啟動" else "未啟動"}"
    }

    private fun isAccessibilityEnabled(): Boolean {
        if (Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) return false
        return Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?.contains(packageName) == true
    }
}