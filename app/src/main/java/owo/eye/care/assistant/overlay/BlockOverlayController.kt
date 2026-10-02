package owo.eye.care.assistant.overlay
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.*
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import owo.eye.care.assistant.R
import owo.eye.care.assistant.data.RulesStore

class BlockOverlayController(private val context: Context, private val rules: RulesStore, private val onUnlocked: () -> Unit) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: View? = null
    fun isShowing(): Boolean = view != null
    fun show() {
        if (view != null) return
        try {
            val themedContext = ContextThemeWrapper(context, R.style.Theme_AppCompat_Light)
            val v = LayoutInflater.from(themedContext).inflate(R.layout.overlay_block, null, false)
            v.fitsSystemWindows = false
            v.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            val pinInput = v.findViewById<EditText>(R.id.pinInput)
            val unlockBtn = v.findViewById<Button>(R.id.unlockBtn)
            val msg = v.findViewById<TextView>(R.id.blockMsg)
            
            // 使用純 Unicode 字元避免亂碼
            val zzz = "\uD83D\uDCA4"
            val wand = "\uD83E\uDE84"
            msg.text = "眼睛需要休息一下哦 (OwO) $zzz"
            unlockBtn.text = "休息完畢，解鎖 (OwO) $wand"

            pinInput.showSoftInputOnFocus = false
            pinInput.requestFocus()

            val numButtons = mapOf(R.id.btn0 to "0", R.id.btn1 to "1", R.id.btn2 to "2", R.id.btn3 to "3", R.id.btn4 to "4", R.id.btn5 to "5", R.id.btn6 to "6", R.id.btn7 to "7", R.id.btn8 to "8", R.id.btn9 to "9")
            for ((id, digit) in numButtons) {
                v.findViewById<Button>(id).setOnClickListener {
                    val start = pinInput.selectionStart
                    val end = pinInput.selectionEnd
                    if (start >= 0 && end >= 0) { pinInput.text.replace(start, end, digit) } else { pinInput.append(digit) }
                }
            }
            v.findViewById<Button>(R.id.btnDel).setOnClickListener {
                val start = pinInput.selectionStart
                val end = pinInput.selectionEnd
                if (start > 0 && start == end) { pinInput.text.delete(start - 1, start) } else if (start != end) { pinInput.text.delete(start, end) }
            }
            v.findViewById<Button>(R.id.btnClear).setOnClickListener { pinInput.text.clear() }

            unlockBtn.setOnClickListener {
                val input = pinInput.text?.toString() ?: ""
                if (!rules.verifyParentPin(input)) {
                    msg.text = "密碼錯誤，請重新輸入 (QwQ)"
                    pinInput.setText("")
                    return@setOnClickListener
                }
                hide()
                onUnlocked()
            }

            val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_FULLSCREEN, PixelFormat.TRANSLUCENT)
            params.gravity = Gravity.FILL
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }
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