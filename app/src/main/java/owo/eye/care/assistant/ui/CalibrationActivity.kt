package owo.eye.care.assistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import owo.eye.care.assistant.R
import owo.eye.care.assistant.data.DistanceStateStore
import owo.eye.care.assistant.databinding.ActivityCalibrationBinding
import owo.eye.care.assistant.distance.CalibrationBridge
import owo.eye.care.assistant.service.DistanceForegroundService

/** 僅在可見校正畫面綁定預覽；返回遊戲或熄屏時立即解除。 */
class CalibrationActivity : AppCompatActivity() {

    private lateinit var vb: ActivityCalibrationBinding
    private lateinit var state: DistanceStateStore
    private var temporaryService = false
    private var attached = false

    /** 0＝收集橫屏基準值；1＝收集豎屏基準值。 */
    private var step = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state = DistanceStateStore(this)
        temporaryService = savedInstanceState?.getBoolean("temporary") ?: !state.isServiceRunning()
        step = savedInstanceState?.getInt("step") ?: 0

        // 直向／橫向各自載入 layout 與 layout-land 的排版，元件 ID 一致
        vb = ActivityCalibrationBinding.inflate(layoutInflater)
        setContentView(vb.root)

        // 相機預覽：維持原本的相容模式與等比例完整顯示
        vb.previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        vb.previewView.scaleType = PreviewView.ScaleType.FIT_CENTER

        // 內層依背景圓角裁切，讓預覽畫面也呈現圓角
        vb.cameraInner.clipToOutline = true

        vb.btnBegin.text = if (step == 0) "開始收集橫屏基準值" else "開始收集豎屏基準值"
        vb.btnBegin.setOnClickListener {
            it.isEnabled = false
            CalibrationBridge.begin()
        }
        vb.btnBack.setOnClickListener { finish() }

        renderSteps()
        updateProgress(0)
    }

    override fun onStart() {
        super.onStart()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            vb.tvStatus.text = "請先允許相機權限，再重新設定護眼基準線。"
            vb.btnBegin.isEnabled = false
            return
        }
        vb.btnBegin.isEnabled = true
        CalibrationBridge.attach(vb.previewView.surfaceProvider) { message, count, done ->
            vb.tvStatus.text = "$message\n有效讀值：$count / 10"
            updateProgress(count)
            if (done) {
                if (step == 0) {
                    step = 1
                    vb.btnBegin.isEnabled = true
                    vb.btnBegin.text = "開始收集豎屏基準值"
                    renderSteps()
                } else {
                    finish()
                }
            }
        }
        attached = true
        try {
            ContextCompat.startForegroundService(this, Intent(this, DistanceForegroundService::class.java).apply { putExtra("is_calibration_only", true) })
        } catch (error: Exception) {
            vb.tvStatus.text = "相機服務啟動失敗：${error.message}"
            vb.btnBegin.isEnabled = false
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

    // ===== 畫面呈現（只影響外觀，不改變校正流程） =====

    /** 步驟標籤的三種外觀。 */
    private enum class StepLook { ACTIVE, DONE, PENDING }

    /** 依目前步驟更新「1 橫屏」「2 豎屏」標籤。 */
    private fun renderSteps() {
        if (step == 0) {
            applyStep(vb.tvStepLandscape, "1 橫屏", StepLook.ACTIVE)
            applyStep(vb.tvStepPortrait, "2 豎屏", StepLook.PENDING)
        } else {
            applyStep(vb.tvStepLandscape, "1 橫屏 ✓", StepLook.DONE)
            applyStep(vb.tvStepPortrait, "2 豎屏", StepLook.ACTIVE)
        }
    }

    private fun applyStep(view: TextView, label: String, look: StepLook) {
        val (background, color) = when (look) {
            StepLook.ACTIVE -> R.drawable.bg_step_on to Color.parseColor("#D16A8A")
            StepLook.DONE -> R.drawable.bg_step_done to Color.parseColor("#137A4E")
            StepLook.PENDING -> R.drawable.bg_step_off to Color.parseColor("#B07A6A")
        }
        view.text = label
        view.setBackgroundResource(background)
        view.setTextColor(color)
        // 左側橫屏／豎屏小圖示跟著文字同色
        view.compoundDrawableTintList = ColorStateList.valueOf(color)
    }

    /** 有效讀值進度條：0～10。 */
    private fun updateProgress(count: Int) {
        vb.progressReadings.progress = count.coerceIn(0, 10)
    }
}
