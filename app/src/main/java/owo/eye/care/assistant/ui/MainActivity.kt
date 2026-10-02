package owo.eye.care.assistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
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
            if (hasOverlayPermission()) {
                vb.tvStatus.text = "「顯示在其他應用程式上層」權限已啟用"
            } else {
                requestOverlayPermission()
            }
        }

        vb.btnOpenManual.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/LeonOAO/OwO.Eye.Care.Assistant/blob/main/README.md")))
        }

        vb.btnSetPin.setOnClickListener {
            val pin = vb.etPin.text?.toString() ?: ""
            if (pin.length >= 4) {
                rules.setParentPin(pin)
                vb.tvStatus.text = "家長 PIN 已儲存"
            } else {
                vb.tvStatus.text = "家長 PIN 必須至少 4 碼"
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
            val quota = vb.etDailyQuota.text?.toString()?.toIntOrNull()
            if (limitMin == null || limitMin !in 1..1440) {
                vb.tvStatus.text = "使用時間必須是 1 到 1440 分鐘"
                return@setOnClickListener
            }
            if (quota == null || quota !in 0..100) {
                vb.tvStatus.text = "每日解鎖次數必須是 0 到 100 次"
                return@setOnClickListener
            }

            rules.setCycleLimitSeconds(limitMin * 60)
            rules.setDailyUnlockQuota(quota)

            vb.tvStatus.text = "時間設定已儲存"
            refreshUi()
        }

        vb.btnToggleControl.setOnClickListener {
            val enabled = rules.isControlEnabled()
            if (!enabled && !rules.hasParentPin()) {
                vb.tvStatus.text = "請先設定至少 4 碼的家長 PIN"
                return@setOnClickListener
            }
            if (!enabled && !isAccessibilityEnabled()) {
                vb.tvStatus.text = "請先啟用 OwO 護眼小助手的無障礙服務"
                return@setOnClickListener
            }
            if (!enabled && !hasOverlayPermission()) {
                vb.tvStatus.text = "請先授予「顯示在其他應用程式上層」權限"
                requestOverlayPermission()
                return@setOnClickListener
            }
            rules.setControlEnabled(!enabled)
            vb.tvStatus.text = if (!enabled) "時間管控已開啟" else "時間管控已關閉"
            refreshUi()
        }

        vb.btnResetTodayQuota.setOnClickListener {
            rules.resetUnlockCountToday()
            vb.tvStatus.text = "今日解鎖額度已恢復"
            refreshUi()
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
        vb.etDailyQuota.setText(rules.getDailyUnlockQuota().toString())

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

    private fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(this)
    }

    private fun requestOverlayPermission() {
        if (!hasOverlayPermission()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun refreshUi() {
        val accEnabled = isAccessibilityEnabled()
        val overlayGranted = hasOverlayPermission()

        vb.tvAccStatus.text = "無障礙服務：${if (accEnabled) "已開啟" else "尚未開啟"}"
        vb.tvOverlayStatus.text = "上層顯示權限：${if (overlayGranted) "已開啟" else "尚未開啟"}"

        vb.btnOpenOverlay.text = if (overlayGranted) "上層顯示權限已授權" else "開啟上層顯示權限"
        vb.btnOpenOverlay.isEnabled = !overlayGranted

        vb.btnToggleControl.text = if (rules.isControlEnabled()) "停止管控" else "開始管控"

        val remaining = rules.getRemainingUnlocksToday()
        val limitMin = rules.getCycleLimitSeconds() / 60
        val quota = rules.getDailyUnlockQuota()

        vb.tvStatus.text =
            "單次時間：$limitMin 分鐘\n每日額度：$quota 次\n今日可解鎖：$remaining 次\n時間管控：${if (rules.isControlEnabled()) "已開啟" else "已關閉"}\n停止管控 PIN：${if (rules.hasStopPin()) "已設定" else "尚未設定"}"
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        if (enabled != 1) return false
        val settingValue = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return settingValue.contains(packageName)
    }
}