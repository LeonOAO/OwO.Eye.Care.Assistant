package owo.eye.care.assistant.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
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
                        blockController?.show()
                    } else {
                        stateStore.setElapsedSeconds(0)
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
            e.printStackTrace()
            mediaPlayer = null
        }
    }

    override fun onCreate() {
        super.onCreate()
        rules = RulesStore(this)
        stateStore = DistanceStateStore(this)
        reminderController = ReminderOverlayController(this)
        blockController = BlockOverlayController(this, rules) {
            stateStore.setBlocked(false)
            stateStore.setElapsedSeconds(0)
            blockController?.hide()
        }
        usageMonitor = DeviceUsageMonitor(this) { available ->
            if (!available) suspendReminders()
            else if (rules.isControlEnabled() && stateStore.isBlocked() && rules.getProtectionMode() == 0) {
                blockController?.show()
            }
        }
        usageMonitor.start()
        handler.postDelayed(timerRunnable, 1000L)
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
        // 中斷輸出不永久停止計時迴圈；下次 tick 仍檢查裝置狀態。
        blockController?.hide()
        reminderController?.hide()
        try { mediaPlayer?.release(); mediaPlayer = null } catch (e: Exception) {}
    }
    
    override fun onDestroy() { 
        usageMonitor.stop()
        handler.removeCallbacks(timerRunnable)
        blockController?.hide()
        reminderController?.hide()
        try { mediaPlayer?.release(); mediaPlayer = null } catch (e: Exception) {}
        super.onDestroy() 
    }
}