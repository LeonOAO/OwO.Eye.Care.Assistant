package owo.eye.care.assistant.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.view.accessibility.AccessibilityEvent
import owo.eye.care.assistant.data.DistanceStateStore
import owo.eye.care.assistant.data.RulesStore
import owo.eye.care.assistant.overlay.BlockOverlayController
import owo.eye.care.assistant.overlay.ReminderOverlayController
import java.util.Locale

class GuardAccessibilityService : AccessibilityService() {
    private lateinit var rules: RulesStore
    private lateinit var stateStore: DistanceStateStore
    private var blockController: BlockOverlayController? = null
    private var reminderController: ReminderOverlayController? = null
    private var tts: TextToSpeech? = null
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
                                tts?.speak("語音護眼小幫手提醒您，請休息一下。", TextToSpeech.QUEUE_FLUSH, null, null)
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
        
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.TAIWAN
            }
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
    }
    
    override fun onDestroy() { 
        handler.removeCallbacks(timerRunnable)
        blockController?.hide()
        reminderController?.hide()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy() 
    }
}