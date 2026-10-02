package owo.eye.care.assistant.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import owo.eye.care.assistant.data.DistanceStateStore
import owo.eye.care.assistant.data.RulesStore
import owo.eye.care.assistant.overlay.BlockOverlayController

class GuardAccessibilityService : AccessibilityService() {
    private lateinit var rules: RulesStore
    private lateinit var stateStore: DistanceStateStore
    private var blockController: BlockOverlayController? = null
    private val handler = Handler(Looper.getMainLooper())
    
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (rules.isControlEnabled()) {
                // 如果已經被設定為封鎖，確保遮罩顯示
                if (stateStore.isBlocked()) {
                    if (blockController?.isShowing() == false) {
                        blockController?.show()
                    }
                } else {
                    // 檢查螢幕是否開啟 (螢幕關閉時暫停計時)
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
                            stateStore.setBlocked(true)
                            if (blockController?.isShowing() == false) {
                                blockController?.show()
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
        blockController = BlockOverlayController(this, rules) {
            stateStore.setBlocked(false)
            stateStore.setElapsedSeconds(0) // 解鎖後重置計時，進入下一輪
            blockController?.hide()
        }
        handler.postDelayed(timerRunnable, 1000L)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!rules.isControlEnabled()) {
            if (blockController?.isShowing() == true) blockController?.hide()
            return
        }
        if (stateStore.isBlocked()) {
            if (blockController?.isShowing() == false) blockController?.show()
        } else {
            if (blockController?.isShowing() == true) blockController?.hide()
        }
    }

    override fun onInterrupt() { 
        handler.removeCallbacks(timerRunnable)
        blockController?.hide() 
    }
    
    override fun onDestroy() { 
        handler.removeCallbacks(timerRunnable)
        blockController?.hide() 
        super.onDestroy() 
    }
}
