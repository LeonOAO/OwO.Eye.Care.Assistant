package owo.eye.care.assistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.EditText
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
            vb.tvStatus.text = "請先允許相機權限"
        }
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

        vb.btnSetPin.setOnClickListener {
            val pin = vb.etPin.text?.toString() ?: ""
            if (pin.length >= 4) {
                rules.setParentPin(pin)
                vb.tvStatus.text = "解除鎖定 PIN 已儲存"
            } else {
                vb.tvStatus.text = "解除鎖定 PIN 必須至少 4 碼"
            }
            refreshUi()
        }

        vb.btnSetStopPin.setOnClickListener {
            val pin = vb.etStopPin.text?.toString() ?: ""
            if (pin.length >= 4) {
                rules.setStopPin(pin)
                vb.tvStatus.text = "停止管控 PIN 已儲存"
            } else {
                vb.tvStatus.text = "停止管控 PIN 必須至少 4 碼"
            }
            refreshUi()
        }

        vb.btnSaveRules.setOnClickListener {
            val limitMin = vb.etLimitMinutes.text?.toString()?.toIntOrNull()
            if (limitMin == null || limitMin !in 1..1440) {
                vb.tvStatus.text = "使用時間必須是 1 到 1440 分鐘"
                return@setOnClickListener
            }

            rules.setCycleLimitSeconds(limitMin * 60)
            vb.tvStatus.text = "時間設定已儲存"
            refreshUi()
        }

        vb.btnToggleControl.setOnClickListener {
            val enabled = rules.isControlEnabled()

            if (!enabled) {
                if (!rules.hasParentPin()) {
                    vb.tvStatus.text = "請先設定至少 4 碼的解除鎖定 PIN"
                    return@setOnClickListener
                }
                if (!isAccessibilityEnabled()) {
                    vb.tvStatus.text = "請先啟用 OwO 護眼小助手的無障礙服務"
                    return@setOnClickListener
                }
                if (!Settings.canDrawOverlays(this)) {
                    vb.tvStatus.text = "請先授予「顯示在其他應用程式上層」權限"
                    return@setOnClickListener
                }
                rules.setControlEnabled(true)
                vb.tvStatus.text = "時間管控已開啟"
                refreshUi()
            } else {
                if (rules.hasStopPin()) {
                    showStopPinDialog()
                } else {
                    vb.tvStatus.text = "請先設定停止管控 PIN，才能停止管控"
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
            vb.tvStatus.text = "距離守護已關閉"
        }

        vb.btnCalibrate30.setOnClickListener {
            val wPx = distanceState.getLastFaceWidthPx()
            if (wPx <= 0) {
                vb.tvStatus.text = "尚未偵測到臉部，請稍後再試"
                return@setOnClickListener
            }
            distanceState.setRefFaceWidthPxAt30cm(wPx)
            vb.tvStatus.text = "30 公分校正已完成"
        }

        vb.etLimitMinutes.setText((rules.getCycleLimitSeconds() / 60).toString())

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
        vb.tvStatus.text = "距離守護已開啟"
    }

    private fun showStopPinDialog() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        val pad = (20 * resources.displayMetrics.density).toInt()
        input.setPadding(pad, pad, pad, pad)
        input.hint = "輸入停止管控 PIN"

        AlertDialog.Builder(this)
            .setTitle("停止管控")
            .setView(input)
            .setPositiveButton("確定") { _, _ ->
                val pin = input.text.toString()
                if (rules.verifyStopPin(pin)) {
                    rules.setControlEnabled(false)
                    vb.tvStatus.text = "時間管控已關閉"
                    refreshUi()
                } else {
                    vb.tvStatus.text = "停止管控 PIN 錯誤，無法關閉"
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun refreshUi() {
        val accEnabled = isAccessibilityEnabled()
        val overlayGranted = Settings.canDrawOverlays(this)

        vb.tvAccStatus.text = "無障礙服務：${if (accEnabled) "已開啟" else "尚未開啟"}"
        vb.tvOverlayStatus.text = "上層顯示權限：${if (overlayGranted) "已開啟" else "尚未開啟"}"
        
        vb.btnOpenOverlay.text = "授權顯示在其他應用程式上層"
        vb.btnOpenOverlay.isEnabled = true

        vb.btnToggleControl.text = if (rules.isControlEnabled()) "停止管控" else "開始管控"

        val limitMin = rules.getCycleLimitSeconds() / 60
        vb.tvStatus.text = "單次時間：$limitMin 分鐘\n時間管控：${if (rules.isControlEnabled()) "已開啟" else "已關閉"}\n停止管控 PIN：${if (rules.hasStopPin()) "已設定" else "尚未設定"}"
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        if (enabled != 1) return false
        val settingValue = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return settingValue.contains(packageName)
    }
}