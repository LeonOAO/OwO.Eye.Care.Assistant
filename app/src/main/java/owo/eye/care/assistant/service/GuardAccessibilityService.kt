package owo.eye.care.assistant.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
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
                                
                                // 1. 播放系統提示音 (確保就算 TTS 壞掉也一定有聲音)
                                try {
                                    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                                    val rt = RingtoneManager.getRingtone(applicationContext, uri)
                                    rt.play()
                                } catch (e: Exception) { e.printStackTrace() }
                                
                                // 2. 播放 TTS 語音
                                try {
                                    val text = "語音護眼小幫手提醒您，請休息一下。"
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reminder_tts")
                                    } else {
                                        @Suppress("DEPRECATION")
                                        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null)
                                    }
                                } catch (e: Exception) { e.printStackTrace() }
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
                val result = tts?.setLanguage(Locale.TAIWAN)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.CHINESE)
                }
                
                // 將聲音通道設定為 Alarm (鬧鐘聲道)，確保在玩遊戲靜音時也能發出聲音
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    tts?.setAudioAttributes(audioAttributes)
                }
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