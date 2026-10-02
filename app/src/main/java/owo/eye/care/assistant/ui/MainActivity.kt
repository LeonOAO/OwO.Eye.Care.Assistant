package owo.eye.care.assistant.ui
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import owo.eye.care.assistant.data.DistanceStateStore
import owo.eye.care.assistant.data.RulesStore
import owo.eye.care.assistant.databinding.ActivityMainBinding
import owo.eye.care.assistant.service.DistanceForegroundService

class MainActivity : AppCompatActivity() {
    private lateinit var vb: ActivityMainBinding
    private lateinit var rules: RulesStore
    private lateinit var distanceState: DistanceStateStore
    private val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())
    
    private val distanceUiRunnable = object : Runnable {
        override fun run() {
            val cm = distanceState.getLastDistanceCm()
            val ref = distanceState.getRefFaceWidthPxAt30cm()
            val isRun = distanceState.isServiceRunning()
            
            vb.tvDistanceStatus.text = "守護距離狀態：\n" +
                "服務狀態：${if (isRun) "偵測中" else "未啟動"}\n" +
                "估算距離：${if (cm > 0) "約 ${cm} 公分" else "尚未取得"}\n" +
                "校正狀態：${if (ref > 0) "已完成" else "尚未完成"}"
            uiHandler.postDelayed(this, 500L)
        }
    }

    private val requestCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startDistanceService() else showToast("請先允許相機權限")
    }

    private fun showToast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vb = ActivityMainBinding.inflate(layoutInflater)
        setContentView(vb.root)
        rules = RulesStore(this)
        distanceState = DistanceStateStore(this)

        vb.btnOpenAccessibility.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        vb.btnOpenOverlay.setOnClickListener { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
        
        vb.etLimitMinutes.setText((rules.getCycleLimitSeconds() / 60).toString())

        vb.btnSaveSettings.setOnClickListener {
            val limitMin = vb.etLimitMinutes.text?.toString()?.toIntOrNull()
            if (limitMin == null || limitMin !in 1..1440) { showToast("使用時間必須是 1 到 1440 分鐘"); return@setOnClickListener }
            rules.setCycleLimitSeconds(limitMin * 60)
            vb.etPin.text?.toString()?.takeIf { it.isNotBlank() }?.let { if (it.length < 4) showToast("解除鎖定 PIN 必須至少 4 碼") else { rules.setParentPin(it); vb.etPin.setText("") } }
            vb.etStopPin.text?.toString()?.takeIf { it.isNotBlank() }?.let { if (it.length < 4) showToast("停止鎖定 PIN 必須至少 4 碼") else { rules.setStopPin(it); vb.etStopPin.setText("") } }
            showToast("保護設定已全部儲存！")
            refreshUi()
        }

        // 單一開關：護眼魔法
        vb.btnToggleControl.setOnClickListener {
            if (!rules.isControlEnabled()) {
                if (!rules.hasParentPin()) { showToast("請先設定解除鎖定 PIN"); return@setOnClickListener }
                if (!isAccessibilityEnabled()) { showToast("請先開啟無障礙服務"); return@setOnClickListener }
                if (!Settings.canDrawOverlays(this)) { showToast("請先允許顯示在其他應用程式上層"); return@setOnClickListener }
                rules.setControlEnabled(true)
                showToast("護眼魔法已啟動！")
                refreshUi()
            } else {
                if (rules.hasStopPin()) showStopPinDialog() else showToast("請先設定停止鎖定 PIN")
            }
        }

        // 校正按鈕
        vb.btnCalibrate30.setOnClickListener {
            val wPx = distanceState.getLastFaceWidthPx()
            if (wPx <= 0) { showToast("尚未偵測到臉部，請稍後再試"); return@setOnClickListener }
            distanceState.setRefFaceWidthPxAt30cm(wPx)
            showToast("護眼基準線已設定完成！")
        }

        // 單一開關：距離偵測
        vb.btnToggleDistance.setOnClickListener {
            if (!distanceState.isServiceRunning()) {
                if (distanceState.getRefFaceWidthPxAt30cm() <= 0) {
                    showToast("請先設定護眼基準線哦！")
                    return@setOnClickListener
                }
                val hasCam = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                if (hasCam) startDistanceService() else requestCamera.launch(Manifest.permission.CAMERA)
            } else {
                stopService(Intent(this, DistanceForegroundService::class.java))
                distanceState.setServiceRunning(false)
                distanceState.setBlocked(false)
                showToast("距離偵測已解除")
                refreshUi()
            }
        }

        refreshUi()
    }

    override fun onResume() { super.onResume(); refreshUi(); uiHandler.post(distanceUiRunnable) }
    override fun onPause() { uiHandler.removeCallbacksAndMessages(null); super.onPause() }

    private fun startDistanceService() {
        ContextCompat.startForegroundService(this, Intent(this, DistanceForegroundService::class.java))
        distanceState.setServiceRunning(true)
        showToast("距離偵測已啟動！")
        refreshUi()
    }

    private fun showStopPinDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setPadding(60, 60, 60, 60)
            hint = "請輸入密碼"
        }
        AlertDialog.Builder(this)
            .setTitle("停用保護 (OwO) \u2728") // \u2728 = 
            .setView(input)
            .setPositiveButton("確定") { _, _ ->
                if (rules.verifyStopPin(input.text.toString())) {
                    rules.setControlEnabled(false)
                    showToast("護眼魔法已解除")
                    refreshUi()
                } else showToast("密碼錯誤，無法解除")
            }.setNegativeButton("取消", null).show()
    }

    private fun refreshUi() {
        vb.tvAccStatus.text = "無障礙服務：${if (isAccessibilityEnabled()) "已開啟" else "尚未開啟"}"
        vb.tvOverlayStatus.text = "顯示在其他應用程式上層：${if (Settings.canDrawOverlays(this)) "已允許" else "尚未允許"}"
        
        // 按鈕文字替換 (使用安全的 Unicode 編碼避免亂碼)
        // \u2728 = 
        vb.btnToggleControl.text = if (rules.isControlEnabled()) "解除護眼魔法 (OwO)" else "啟動護眼魔法 (OwO) \u2728"
        vb.btnCalibrate30.text = "設定護眼基準線 \u2728"
        
        // \uD83D\uDC41\uFE0F = 
        vb.btnToggleDistance.text = if (distanceState.isServiceRunning()) "解除距離偵測" else "啟動距離偵測 (OwO) \uD83D\uDC41\uFE0F"

        vb.tvProtectionStatus.text = "循環時間：${rules.getCycleLimitSeconds() / 60} 分鐘\n保護控管：${if (rules.isControlEnabled()) "已啟動" else "未啟動"}"
    }

    private fun isAccessibilityEnabled(): Boolean {
        if (Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) return false
        return Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?.contains(packageName) == true
    }
}