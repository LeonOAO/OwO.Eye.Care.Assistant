package owo.eye.care.assistant.overlay
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.*
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import owo.eye.care.assistant.R

class DistanceOverlayController(private val context: Context) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: View? = null
    fun isShowing(): Boolean = view != null
    fun show() {
        if (view != null) return
        try {
            val themedContext = ContextThemeWrapper(context, R.style.Theme_AppCompat_Light)
            val v = LayoutInflater.from(themedContext).inflate(R.layout.overlay_distance, null, false)
            v.fitsSystemWindows = false
            
            // 使用純 Unicode 字元避免亂碼
            val msg = v.findViewById<TextView>(R.id.distanceMsg)
            val shield = "\uD83D\uDEE1\uFE0F"
            msg.text = "太近囉 (OwO) $shield"
            
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.FILL
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            wm.addView(v, params)
            view = v
        } catch (e: Exception) { e.printStackTrace(); view = null }
    }
    fun hide() {
        val v = view ?: return
        try { wm.removeView(v) } catch (_: Exception) {}
        view = null
    }
}