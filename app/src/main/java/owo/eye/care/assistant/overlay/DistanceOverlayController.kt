package owo.eye.care.assistant.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import owo.eye.care.assistant.R

class DistanceOverlayController(private val context: Context) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: View? = null

    fun isShowing(): Boolean = view != null

    fun show(distanceCm: Int) {
        if (view == null) {
            try {
                val themedContext = ContextThemeWrapper(context, R.style.AppTheme)
                val v = LayoutInflater.from(themedContext).inflate(R.layout.overlay_distance, null, false)
                v.fitsSystemWindows = false

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    PixelFormat.TRANSLUCENT
                )
                params.gravity = Gravity.FILL

                // 解決頂部挖孔與瀏海屏留白
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    params.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }

                wm.addView(v, params)
                view = v
            } catch (e: Exception) {
                e.printStackTrace()
                return
            }
        }

        val msg = view?.findViewById<TextView>(R.id.distanceMsg) ?: return
        msg.text = if (distanceCm > 0) {
            "目前距離：約 ${distanceCm} 公分\n請將裝置移至 30 公分以上"
        } else {
            "請將裝置移至 30 公分以上"
        }
    }

    fun hide() {
        val v = view ?: return
        try {
            wm.removeView(v)
        } catch (_: Exception) {
        }
        view = null
    }

    fun destroy() {
        hide()
    }
}