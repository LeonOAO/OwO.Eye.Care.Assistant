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
            val blocked = distanceState.isBlocked()
            val cm = distanceState.getLastDistanceCm()
            val ref = distanceState.getRefFaceWidthPxAt30cm()
            vb.tvDistanceStatus.text =
                "守護距離：${if (blocked) "距離過近" else if (cm > 0) "距離正常" else "等待偵測"}\n估算距離：${if (cm > 0) "約 ${cm} 公分" else "尚未取得"}\n校正狀態：${if (ref > 0) "已完成" else "尚未完成"}"
            uiHandler.postDelayed(this, 500L)
        }
    }

    private val requestCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startDistanceService()
        } else {
            showToast("請先允許相機權限")
        }
    }

    //  全新的浮動提示函數，取代原本在畫面最下方的醜文字
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vb = ActivityMainBinding.inflate(layoutInflater)
        setContentView(vb.root)

        rules = RulesStore(this)
        distanceState = DistanceStateStore(this)

        vb.btnOpenAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        vb.btnOpenOverlay.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        // 初始化時間框
        vb.etLimitMinutes.setText((rules.getCycleLimitSeconds() / 60).toString())

        // 一鍵儲存所有設定邏輯
        vb.btnSaveSettings.setOnClickListener {
            val limitStr = vb.etLimitMinutes.text?.toString()
            val pin = vb.etPin.text?.toString() ?: ""
            val stopPin = vb.etStopPin.text?.toString() ?: ""

            val limitMin = limitStr?.toIntOrNull()
            if (limitMin == null || limitMin !in 1..1440) {
                showToast("使用時間必須是 1 到 1440 分鐘")
                return@setOnClickListener
            }

            if (pin.isNotEmpty() && pin.length < 4) {
                showToast("解除鎖定 PIN 必須至少 4 碼")
                return@setOnClickListener
            }

            if (stopPin.isNotEmpty() && stopPin.length < 4) {
                showToast("停止鎖定 PIN 必須至少 4 碼")
                return@setOnClickListener
            }

            // 儲存邏輯
            rules.setCycleLimitSeconds(limitMin * 60)
            if (pin.isNotEmpty()) {
                rules.setParentPin(pin)
                vb.etPin.setText("") 
            }
            if (stopPin.isNotEmpty()) {
                rules.setStopPin(stopPin)
                vb.etStopPin.setText("") 
            }

            showToast("保護設定已全部儲存！")
            refreshUi()
        }

        // 啟用/停用保護
        vb.btnToggleControl.setOnClickListener {
            val enabled = rules.isControlEnabled()

            if (!enabled) {
                if (!rules.hasParentPin()) {
                    showToast("請先設定解除鎖定 PIN")
                    return@setOnClickListener
                }
                if (!isAccessibilityEnabled()) {
                    showToast("請先啟用 OwO 護眼小助手的無障礙服務")
                    return@setOnClickListener
                }
                if (!Settings.canDrawOverlays(this)) {
                    showToast("請先開啟「顯示在其他應用程式上層」權限")
                    return@setOnClickListener
                }
                rules.setControlEnabled(true)
                showToast("保護功能已啟用")
                refreshUi()
            } else {
                if (rules.hasStopPin()) {
                    showStopPinDialog()
                } else {
                    showToast("請先設定停止鎖定 PIN，才能停用保護")
                }
            }
        }

        vb.btnStartDistance.setOnClickListener {
            val has = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (has) startDistanceService() else requestCamera.launch(Manifest.permission.CAMERA)
        }

        vb.btnStopDistance.setOnClickListener {
            stopService(Intent(this, DistanceForegroundService::class.java))
            distanceState.setBlocked(false)
            showToast("距離守護已關閉")
        }

        vb.btnCalibrate30.setOnClickListener {
            val wPx = distanceState.getLastFaceWidthPx()
            if (wPx <= 0) {
                showToast("尚未偵測到臉部，請稍後再試")
                return@setOnClickListener
            }
            distanceState.setRefFaceWidthPxAt30cm(wPx)
            showToast("30 公分校正已完成")
        }

        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
        uiHandler.post(distanceUiRunnable)
    }

    override fun onPause() {
        uiHandler.removeCallbacksAndMessages(null)
        super.onPause()
    }

    private fun startDistanceService() {
        val intent = Intent(this, DistanceForegroundService::class.java)
        ContextCompat.startForegroundService(this, intent)
        showToast("距離守護已開啟")
    }

    private fun showStopPinDialog() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        val pad = (20 * resources.displayMetrics.density).toInt()
        input.setPadding(pad, pad, pad, pad)
        input.hint = "輸入停止鎖定 PIN"

        AlertDialog.Builder(this)
            .setTitle("停用保護")
            .setView(input)
            .setPositiveButton("確定") { _, _ ->
                val pin = input.text.toString()
                if (rules.verifyStopPin(pin)) {
                    rules.setControlEnabled(false)
                    showToast("保護功能已停用")
                    refreshUi()
                } else {
                    showToast("密碼錯誤，無法停用")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun refreshUi() {
        val accEnabled = isAccessibilityEnabled()
        val overlayGranted = Settings.canDrawOverlays(this)

        vb.tvAccStatus.text = "無障礙服務：${if (accEnabled) "已開啟" else "尚未開啟"}"
        vb.tvOverlayStatus.text = "顯示在其他應用程式上層：${if (overlayGranted) "已開啟" else "尚未開啟"}"
        
        vb.btnOpenOverlay.text = "顯示在其他應用程式上層"
        vb.btnOpenOverlay.isEnabled = true

        vb.btnToggleControl.text = if (rules.isControlEnabled()) "停用保護" else "啟用保護"

        //  這裡更新了保護卡片最下方的文字，只顯示這兩行！
        val limitMin = rules.getCycleLimitSeconds() / 60
        vb.tvProtectionStatus.text = "循環時間：$limitMin 分鐘\n保護控管：${if (rules.isControlEnabled()) "已啟動" else "未啟動"}"
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        if (enabled != 1) return false
        val settingValue = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return settingValue.contains(packageName)
    }
}