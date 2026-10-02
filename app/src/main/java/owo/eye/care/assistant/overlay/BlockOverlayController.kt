package owo.eye.care.assistant.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import owo.eye.care.assistant.R
import owo.eye.care.assistant.data.RulesStore

class BlockOverlayController(
    private val context: Context,
    private val rules: RulesStore,
    private val onUnlocked: () -> Unit,
    private val onStopControl: () -> Unit
) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: View? = null

    fun isShowing(): Boolean = view != null

    fun show() {
        if (view != null) return

        val v = LayoutInflater.from(context).inflate(R.layout.overlay_block, null, false)

        val pinInput = v.findViewById<EditText>(R.id.pinInput)
        val unlockBtn = v.findViewById<Button>(R.id.unlockBtn)
        val msg = v.findViewById<TextView>(R.id.blockMsg)
        val remainingTv = v.findViewById<TextView>(R.id.blockRemaining)

        val stopPinInput = v.findViewById<EditText>(R.id.stopPinInput)
        val stopBtn = v.findViewById<Button>(R.id.stopBtn)
        val stopHint = v.findViewById<TextView>(R.id.stopHint)

        fun refreshRemaining() {
            remainingTv.text = "今日可解鎖：${rules.getRemainingUnlocksToday()} 次"
        }

        refreshRemaining()
        msg.text = "請輸入家長 PIN"

        unlockBtn.setOnClickListener {
            val input = pinInput.text?.toString() ?: ""

            if (!rules.verifyParentPin(input)) {
                msg.text = "家長 PIN 不正確，請重新輸入"
                pinInput.setText("")
                return@setOnClickListener
            }

            val ok = rules.consumeOneUnlockToday()
            if (!ok) {
                msg.text = "今日解鎖額度已用完"
                pinInput.setText("")
                refreshRemaining()
                return@setOnClickListener
            }

            hide()
            onUnlocked()
        }

        stopBtn.setOnClickListener {
            if (rules.getRemainingUnlocksToday() > 0) {
                stopHint.text = "解鎖額度用完後才可停止管控"
                stopPinInput.setText("")
                return@setOnClickListener
            }

            val input = stopPinInput.text?.toString() ?: ""
            if (!rules.hasStopPin()) {
                stopHint.text = "尚未設定停止管控 PIN"
                stopPinInput.setText("")
                return@setOnClickListener
            }

            if (!rules.verifyStopPin(input)) {
                stopHint.text = "停止管控 PIN 不正確，請重新輸入"
                stopPinInput.setText("")
                return@setOnClickListener
            }

            hide()
            onStopControl()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START

        wm.addView(v, params)
        view = v
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
