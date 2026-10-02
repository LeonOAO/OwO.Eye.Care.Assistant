package owo.eye.care.assistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
                "距離狀態：${if (blocked) "過近(遮罩中)" else "正常"}｜估算：${if (cm > 0) "${cm}cm" else "未知"}｜校正：${if (ref > 0) "已完成" else "未完成"}"
            uiHandler.postDelayed(this, 500L)
        }
    }

    private val requestCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startDistanceService()
        } else {
            vb.tvStatus.text = "未授予相機權限，無法啟動距離守護"
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

        vb.btnSetPin.setOnClickListener {
            val pin = vb.etPin.text?.toString() ?: ""
            if (pin.length >= 4) {
                rules.setParentPin(pin)
                vb.tvStatus.text = "已設定 家長 PIN"
            } else {
                vb.tvStatus.text = "家長 PIN 至少 4 碼"
            }
            refreshUi()
        }

        vb.btnSetStopPin.setOnClickListener {
            val pin = vb.etStopPin.text?.toString() ?: ""
            if (pin.length >= 4) {
                rules.setStopPin(pin)
                vb.tvStatus.text = "已設定 停止 PIN"
            } else {
                vb.tvStatus.text = "停止 PIN 至少 4 碼"
            }
            refreshUi()
        }

        vb.btnSaveRules.setOnClickListener {
            val limitMin = vb.etLimitMinutes.text?.toString()?.toIntOrNull() ?: 20
            val quota = vb.etDailyQuota.text?.toString()?.toIntOrNull() ?: 3

            rules.setCycleLimitSeconds(limitMin * 60)
            rules.setDailyUnlockQuota(quota)

            vb.tvStatus.text = "已儲存規則：每次 $limitMin 分鐘｜每天可解鎖 $quota 次"
            refreshUi()
        }

        vb.btnToggleControl.setOnClickListener {
            val enabled = rules.isControlEnabled()
            rules.setControlEnabled(!enabled)
            vb.tvStatus.text = if (!enabled) "已開始管控（開始全域計時）" else "已停止管控"
            refreshUi()
        }

        vb.btnResetTodayQuota.setOnClickListener {
            rules.resetUnlockCountToday()
            vb.tvStatus.text = "已手動重置：今日解鎖次數歸零"
            refreshUi()
        }

        vb.btnStartDistance.setOnClickListener {
            val has = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (has) startDistanceService() else requestCamera.launch(Manifest.permission.CAMERA)
        }

        vb.btnStopDistance.setOnClickListener {
            stopService(Intent(this, DistanceForegroundService::class.java))
            distanceState.setBlocked(false)
            vb.tvStatus.text = "已停止距離守護"
        }

        vb.btnCalibrate30.setOnClickListener {
            val wPx = distanceState.getLastFaceWidthPx()
            if (wPx <= 0) {
                vb.tvStatus.text = "校正失敗：尚未偵測到人臉。請先啟動距離守護並正對臉 2 秒再按校正。"
                return@setOnClickListener
            }
            distanceState.setRefFaceWidthPxAt30cm(wPx)
            vb.tvStatus.text = "校正完成：已記錄 30cm 參考人臉寬度（${wPx}px）"
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
        vb.tvStatus.text = "已啟動距離守護（通知列常駐）"
    }

    private fun refreshUi() {
        vb.tvAccStatus.text = "無障礙狀態：${if (isAccessibilityEnabled()) "已啟用" else "未啟用（請到設定開啟）"}"
        vb.btnToggleControl.text = if (rules.isControlEnabled()) "停止管控" else "開始管控"

        val remaining = rules.getRemainingUnlocksToday()
        val limitMin = rules.getCycleLimitSeconds() / 60
        val quota = rules.getDailyUnlockQuota()

        vb.tvStatus.text =
            "目前規則：每次 $limitMin 分鐘｜每天可解鎖 $quota 次｜今日剩餘解鎖 $remaining 次｜管控：${if (rules.isControlEnabled()) "ON" else "OFF"}｜停止 PIN：${if (rules.hasStopPin()) "已設定" else "未設定"}"
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        if (enabled != 1) return false
        val settingValue = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return settingValue.contains(packageName)
    }
}
