package owo.eye.care.assistant.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import owo.eye.care.assistant.data.DistanceStateStore
import owo.eye.care.assistant.data.RulesStore
import owo.eye.care.assistant.overlay.BlockOverlayController
import owo.eye.care.assistant.overlay.DistanceOverlayController

class GuardAccessibilityService : AccessibilityService() {

    private lateinit var rules: RulesStore
    private lateinit var distanceState: DistanceStateStore

    private lateinit var timeOverlay: BlockOverlayController
    private lateinit var distanceOverlay: DistanceOverlayController

    private val handler = Handler(Looper.getMainLooper())
    private var lastTickMs: Long = 0L

    private var cycleUsedSec: Int = 0
    private var screenInteractive: Boolean = true

    private val tickRunnable = object : Runnable {
        override fun run() {
            try {
                tick()
            } finally {
                handler.postDelayed(this, 1000L)
            }
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action == Intent.ACTION_SCREEN_OFF) {
                screenInteractive = false
            } else if (action == Intent.ACTION_SCREEN_ON || action == Intent.ACTION_USER_PRESENT) {
                screenInteractive = true
            }
        }
    }

    override fun onServiceConnected() {
        rules = RulesStore(applicationContext)
        distanceState = DistanceStateStore(applicationContext)

        // 傳入 this 獲得合法的 Accessibility Window Token
        distanceOverlay = DistanceOverlayController(this)

        timeOverlay = BlockOverlayController(
            this,
            rules,
            onUnlocked = {
                cycleUsedSec = 0
                lastTickMs = System.currentTimeMillis()
            },
            onStopControl = {
                rules.setControlEnabled(false)
                cycleUsedSec = 0
                lastTickMs = System.currentTimeMillis()
            }
        )

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        screenInteractive = pm.isInteractive

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        cycleUsedSec = 0
        lastTickMs = System.currentTimeMillis()
        handler.postDelayed(tickRunnable, 1000L)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // no-op
    }

    private fun tick() {
        val now = System.currentTimeMillis()

        if (!rules.isControlEnabled()) {
            if (distanceOverlay.isShowing()) distanceOverlay.hide()
            if (timeOverlay.isShowing()) timeOverlay.hide()
            lastTickMs = now
            return
        }

        // 距離過近具備最高優先級
        if (distanceState.isBlocked()) {
            val cm = distanceState.getLastDistanceCm()
            if (timeOverlay.isShowing()) timeOverlay.hide()
            distanceOverlay.show(cm)
            lastTickMs = now
            return
        } else {
            if (distanceOverlay.isShowing()) distanceOverlay.hide()
        }

        if (!rules.hasParentPin()) {
            lastTickMs = now
            return
        }

        if (!screenInteractive) {
            lastTickMs = now
            return
        }

        if (timeOverlay.isShowing()) {
            lastTickMs = now
            return
        }

        val deltaSec = ((now - lastTickMs) / 1000L).toInt().coerceAtLeast(0)
        lastTickMs = now
        if (deltaSec <= 0) return

        cycleUsedSec += deltaSec

        val limitSec = rules.getCycleLimitSeconds()
        if (cycleUsedSec >= limitSec) {
            timeOverlay.show()
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {
        }
        handler.removeCallbacksAndMessages(null)
        distanceOverlay.destroy()
        timeOverlay.destroy()
        super.onDestroy()
    }
}