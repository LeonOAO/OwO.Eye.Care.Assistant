package owo.eye.care.assistant.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.*
import owo.eye.care.assistant.R

class ReminderOverlayController(private val context: Context) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: View? = null
    private val handler = Handler(Looper.getMainLooper())

    fun show() {
        if (view != null) return
        try {
            val themedContext = ContextThemeWrapper(context, R.style.AppTheme)
            val v = LayoutInflater.from(themedContext).inflate(R.layout.overlay_reminder, null, false)
            v.fitsSystemWindows = false
            
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP
            params.windowAnimations = android.R.style.Animation_Dialog
            wm.addView(v, params)
            view = v
            handler.postDelayed({ hide() }, 4000)
        } catch (e: Exception) { e.printStackTrace(); view = null }
    }
    fun hide() {
        val v = view ?: return
        try { wm.removeView(v) } catch (_: Exception) {}
        view = null
    }
}