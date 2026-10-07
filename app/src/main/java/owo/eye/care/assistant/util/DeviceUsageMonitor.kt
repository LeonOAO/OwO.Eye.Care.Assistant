package owo.eye.care.assistant.util

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.core.content.ContextCompat

/** 僅在亮屏、解鎖且非螢幕保護程式時允許護眼運算，不以未觸碰螢幕判斷閒置。 */
class DeviceUsageMonitor(
    private val context: Context,
    private val onChanged: (Boolean) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var registered = false
    private var dreaming = false
    private var lastActive: Boolean? = null
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_DREAMING_STARTED -> dreaming = true
                Intent.ACTION_DREAMING_STOPPED, Intent.ACTION_USER_PRESENT -> dreaming = false
            }
            // SCREEN_OFF 廣播優先暫停，避免系統狀態切換瞬間仍顯示警示。
            dispatch(if (intent?.action == Intent.ACTION_SCREEN_OFF) false else isActive())
        }
    }
    private val poll = object : Runnable {
        override fun run() {
            if (!registered) return
            dispatch(isActive())
            handler.postDelayed(this, 1000L)
        }
    }

    fun isActive(): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return power.isInteractive && !keyguard.isKeyguardLocked && !dreaming
    }

    private fun dispatch(active: Boolean) {
        if (lastActive == active) return
        lastActive = active
        onChanged(active)
    }

    fun start() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_DREAMING_STARTED)
            addAction(Intent.ACTION_DREAMING_STOPPED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        registered = true
        dispatch(isActive())
        handler.postDelayed(poll, 1000L)
    }

    fun stop() {
        handler.removeCallbacks(poll)
        if (registered) {
            context.unregisterReceiver(receiver)
            registered = false
        }
        lastActive = null
    }
}
