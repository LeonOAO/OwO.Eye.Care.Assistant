package owo.eye.care.assistant.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import owo.eye.care.assistant.diagnostics.ExecutionLogStore
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import owo.eye.care.assistant.util.DeviceUsageMonitor
import android.view.accessibility.AccessibilityEvent
import owo.eye.care.assistant.R
import owo.eye.care.assistant.data.DistanceStateStore
import owo.eye.care.assistant.data.RulesStore
import owo.eye.care.assistant.overlay.BlockOverlayController
import owo.eye.care.assistant.overlay.ReminderOverlayController

class GuardAccessibilityService : AccessibilityService() {
    private lateinit var rules: RulesStore
    private lateinit var stateStore: DistanceStateStore
    private var blockController: BlockOverlayController? = null
    private var reminderController: ReminderOverlayController? = null
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var usageMonitor: DeviceUsageMonitor
    private val handler = Handler(Looper.getMainLooper())
    
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (!usageMonitor.isActive() || !rules.isControlEnabled()) {
                suspendReminders()
            } else if (stateStore.isBlocked()) {
                if (rules.getProtectionMode() == 0) blockController?.show()
                else blockController?.hide()
            } else {
                val elapsed = stateStore.getElapsedSeconds() + 1
                stateStore.setElapsedSeconds(elapsed)
                if (elapsed >= rules.getCycleLimitSeconds()) {
                    if (rules.getProtectionMode() == 0) {
                        stateStore.setBlocked(true)
                        ExecutionLogStore.event("提醒", "使用時間達上限，進入 PIN 鎖定")
                        blockController?.show()
                    } else {
                        stateStore.setElapsedSeconds(0)
                        ExecutionLogStore.event("提醒", "使用時間達上限，語音與橫幅提醒")
                        reminderController?.show()
                        playLocalVoice()
                    }
                }
            }
            handler.postDelayed(this, 1000L)
        }
    }

    /** 暫停輸出而非解除 PIN 鎖定；不清除已累計時間。 */
    private fun suspendReminders() {
        blockController?.hide()
        reminderController?.hide()
        try { mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
    }

    private fun playLocalVoice() {
        if (!usageMonitor.isActive() || !rules.isControlEnabled()) return
        try {
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer.create(applicationContext, R.raw.voice_reminder)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mediaPlayer?.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                }
                mediaPlayer?.setOnCompletionListener { 
                    it.release()
                    mediaPlayer = null 
                }
            }
            mediaPlayer?.start()
        } catch (e: Exception) {
            ExecutionLogStore.error("語音播放", e)
            e.printStackTrace()
            mediaPlayer = null
        }
    }

    override fun onCreate() {
        super.onCreate()
        ExecutionLogStore.event("無障礙", "服務建立（尚未代表已連線）")
        rules = RulesStore(this)
        stateStore = DistanceStateStore(this)
        reminderController = ReminderOverlayController(this)
        blockController = BlockOverlayController(this, rules) {
            stateStore.setBlocked(false)
            stateStore.setElapsedSeconds(0)
            blockController?.hide()
        }
        usageMonitor = DeviceUsageMonitor(this) { available ->
            ExecutionLogStore.transition("guard_usage", "控管", if (available) "裝置亮屏且解鎖，控管依原設定運作" else "裝置待機／鎖定，控管提醒暫停")
            if (!available) suspendReminders()
            else if (rules.isControlEnabled() && stateStore.isBlocked() && rules.getProtectionMode() == 0) {
                blockController?.show()
            }
        }
        usageMonitor.start()
        handler.postDelayed(timerRunnable, 1000L)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ExecutionLogStore.event("無障礙", "系統已完成服務連線")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        ExecutionLogStore.event("無障礙", "收到解除綁定回呼（原因未確認）")
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!usageMonitor.isActive() || !rules.isControlEnabled()) {
            suspendReminders()
            return
        }
        if (stateStore.isBlocked() && rules.getProtectionMode() == 0) {
            if (blockController?.isShowing() == false) blockController?.show()
        } else {
            if (blockController?.isShowing() == true) blockController?.hide()
        }
    }

    override fun onInterrupt() {
        ExecutionLogStore.event("無障礙", "收到中斷回呼（不等於服務斷線）") 
        // 中斷輸出不永久停止計時迴圈；下次 tick 仍檢查裝置狀態。
        blockController?.hide()
        reminderController?.hide()
        try { mediaPlayer?.release(); mediaPlayer = null } catch (e: Exception) {}
    }
    
    override fun onDestroy() {
        ExecutionLogStore.event("無障礙", "收到服務銷毀回呼（不代表所有程序終止都會觸發）") 
        usageMonitor.stop()
        handler.removeCallbacks(timerRunnable)
        blockController?.hide()
        reminderController?.hide()
        try { mediaPlayer?.release(); mediaPlayer = null } catch (e: Exception) {}
        super.onDestroy() 
    }
}