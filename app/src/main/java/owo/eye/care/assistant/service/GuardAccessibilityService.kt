package owo.eye.care.assistant.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
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
    private val handler = Handler(Looper.getMainLooper())
    
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (rules.isControlEnabled()) {
                if (stateStore.isBlocked()) {
                    if (rules.getProtectionMode() == 0 && blockController?.isShowing() == false) {
                        blockController?.show()
                    }
                } else {
                    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                    val isScreenOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                        pm.isInteractive
                    } else {
                        @Suppress("DEPRECATION")
                        pm.isScreenOn
                    }
                    
                    if (isScreenOn) {
                        val elapsed = stateStore.getElapsedSeconds() + 1
                        stateStore.setElapsedSeconds(elapsed)
                        val limit = rules.getCycleLimitSeconds()
                        
                        if (elapsed >= limit) {
                            if (rules.getProtectionMode() == 0) {
                                stateStore.setBlocked(true)
                                if (blockController?.isShowing() == false) blockController?.show()
                            } else {
                                stateStore.setElapsedSeconds(0)
                                reminderController?.show()
                                playLocalVoice()
                            }
                        }
                    }
                }
            } else {
                if (blockController?.isShowing() == true) {
                    blockController?.hide()
                }
            }
            handler.postDelayed(this, 1000L)
        }
    }

    private fun playLocalVoice() {
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
        handler.postDelayed(timerRunnable, 1000L)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!rules.isControlEnabled()) {
            if (blockController?.isShowing() == true) blockController?.hide()
            return
        }
        if (stateStore.isBlocked() && rules.getProtectionMode() == 0) {
            if (blockController?.isShowing() == false) blockController?.show()
        } else {
            if (blockController?.isShowing() == true) blockController?.hide()
        }
    }

    override fun onInterrupt() { 
        handler.removeCallbacks(timerRunnable)
        blockController?.hide()
        reminderController?.hide()
        try { mediaPlayer?.release(); mediaPlayer = null } catch (e: Exception) {}
    }
    
    override fun onDestroy() { 
        handler.removeCallbacks(timerRunnable)
        blockController?.hide()
        reminderController?.hide()
        try { mediaPlayer?.release(); mediaPlayer = null } catch (e: Exception) {}
        super.onDestroy() 
    }
}