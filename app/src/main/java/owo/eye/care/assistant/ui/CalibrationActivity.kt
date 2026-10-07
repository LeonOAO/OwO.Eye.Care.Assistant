package owo.eye.care.assistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import owo.eye.care.assistant.data.DistanceStateStore
import owo.eye.care.assistant.distance.CalibrationBridge
import owo.eye.care.assistant.service.DistanceForegroundService

/** 僅在可見校正畫面綁定預覽；返回遊戲或熄屏時立即解除。 */
class CalibrationActivity : AppCompatActivity() {
    private lateinit var preview: PreviewView
    private lateinit var status: TextView
    private lateinit var begin: Button
    private lateinit var state: DistanceStateStore
    private var temporaryService = false
    private var attached = false
    private var step = 0

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state = DistanceStateStore(this)
        temporaryService = savedInstanceState?.getBoolean("temporary") ?: !state.isServiceRunning()
        step = savedInstanceState?.getInt("step") ?: 0
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            setBackgroundColor(Color.rgb(245, 247, 252))
        }
        fun label(text: String, size: Float): TextView = TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(Color.rgb(24, 32, 51))
            setPadding(0, dp(8), 0, dp(12))
            setLineSpacing(dp(5).toFloat(), 1f)
        }
        content.addView(label("偵測護眼基準線設定", 24f))
        content.addView(label("請先用尺確認臉部與平板相距 30 公分。保持單一完整臉部置中、光線充足並正對鏡頭。", 16f))
        val frame = FrameLayout(this)
        preview = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
        frame.addView(preview, FrameLayout.LayoutParams(-1, -1))
        val guide = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dp(2), Color.rgb(21, 163, 138))
            }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        frame.addView(guide, FrameLayout.LayoutParams(dp(220), dp(290), Gravity.CENTER))
        content.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f))
        status = label("等待相機啟動。", 16f)
        content.addView(status)
        begin = Button(this).apply {
            text = if (step == 0) "開始收集橫屏基準值" else "開始收集豎屏基準值"
            minHeight = dp(56)
            setPadding(dp(18), dp(12), dp(18), dp(12))
            setOnClickListener {
                isEnabled = false
                CalibrationBridge.begin()
            }
        }
        content.addView(begin, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        content.addView(Button(this).apply {
            text = "完成／返回"
            minHeight = dp(56)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-1, -2))
        content.layoutParams = FrameLayout.LayoutParams(-1, -1)
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(content) })
    }

    override fun onStart() {
        super.onStart()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            status.text = "請先允許相機權限，再重新設定護眼基準線。"
            begin.isEnabled = false
            return
        }
        begin.isEnabled = true
        CalibrationBridge.attach(preview.surfaceProvider) { message, count, done ->
            status.text = "$message\n有效讀值：$count / 10"
            if (done) {
                if (step == 0) {
                    step = 1
                    begin.isEnabled = true
                    begin.text = "開始收集豎屏基準值"
                } else {
                    finish()
                }
            }
        }
        attached = true
        try {
            ContextCompat.startForegroundService(this, Intent(this, DistanceForegroundService::class.java).apply { putExtra("is_calibration_only", true) })
        } catch (error: Exception) {
            status.text = "相機服務啟動失敗：${error.message}"
            begin.isEnabled = false
            CalibrationBridge.detach()
            attached = false
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("temporary", temporaryService)
        outState.putInt("step", step)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        if (attached) {
            CalibrationBridge.detach()
            attached = false
        }
        // 校正前未啟用偵測時，不讓校正操作永久開啟背景相機。
        if (temporaryService && !isChangingConfigurations) {
            stopService(Intent(this, DistanceForegroundService::class.java).apply { putExtra("is_calibration_only", true) })
        }
        super.onStop()
    }
}
