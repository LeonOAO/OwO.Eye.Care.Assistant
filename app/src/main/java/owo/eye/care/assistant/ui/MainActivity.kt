package owo.eye.care.assistant.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
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
            val wPx = distanceState.getLastFaceWidthPx()
            
            vb.tvDistanceStatus.text = "守護距離狀態：\n" +
                "服務狀態：${if (isRun) "偵測中" else "未啟動"}\n" +
                "當前臉部：${if (wPx > 0) "已偵測 ($wPx px)" else "未偵測到"}\n" +
                "估算距離：${if (cm > 0) "約 ${cm} 公分" else "尚未取得"}\n" +
                "校正狀態：${if (ref > 0) "已完成 (基準: $ref px)" else "尚未完成"}"
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

        vb.root.setOnTouchListener { _, _ ->
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            currentFocus?.windowToken?.let { imm.hideSoftInputFromWindow(it, 0) }
            currentFocus?.clearFocus()
            false
        }

        val imeListener = android.widget.TextView.OnEditorActionListener { v, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
                v.clearFocus()
                true
            } else {
                false
            }
        }
        vb.etLimitMinutes.setOnEditorActionListener(imeListener)
        vb.etPin.setOnEditorActionListener(imeListener)
        vb.etStopPin.setOnEditorActionListener(imeListener)
        
        rules = RulesStore(this)
        distanceState = DistanceStateStore(this)

        vb.btnOpenAccessibility.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        vb.btnOpenOverlay.setOnClickListener { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
        
        vb.btnToggleMode.setOnClickListener {
            val newMode = if (rules.getProtectionMode() == 0) 1 else 0
            rules.setProtectionMode(newMode)
            if (newMode == 1) {
                distanceState.setBlocked(false)
            }
            showToast(if (newMode == 0) "已切換為：強制鎖定模式" else "已切換為：溫和語音提醒")
            refreshUi()
        }

        vb.etLimitMinutes.setText((rules.getCycleLimitSeconds() / 60).toString())

        vb.btnSaveSettings.setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            currentFocus?.windowToken?.let { imm.hideSoftInputFromWindow(it, 0) }
            currentFocus?.clearFocus()
            val limitMin = vb.etLimitMinutes.text?.toString()?.toIntOrNull()
            if (limitMin == null || limitMin !in 1..1440) { showToast("使用時間必須是 1 到 1440 分鐘"); return@setOnClickListener }
            rules.setCycleLimitSeconds(limitMin * 60)
            vb.etPin.text?.toString()?.takeIf { it.isNotBlank() }?.let { if (it.length < 4) showToast("解除鎖定 PIN 必須至少 4 碼") else { rules.setParentPin(it); vb.etPin.setText("") } }
            vb.etStopPin.text?.toString()?.takeIf { it.isNotBlank() }?.let { if (it.length < 4) showToast("停止鎖定 PIN 必須至少 4 碼") else { rules.setStopPin(it); vb.etStopPin.setText("") } }
            showToast("保護設定已全部儲存！")
            refreshUi()
        }

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

        vb.btnCalibrate30.setOnClickListener {
            val wPx = distanceState.getLastFaceWidthPx()
            if (wPx <= 0) {
                if (!distanceState.isServiceRunning()) {
                    val hasCam = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    if (hasCam) {
                        startDistanceService()
                        showToast("相機已啟動！請將臉部對準後『再按一次』本按鈕完成定位")
                    } else {
                        requestCamera.launch(Manifest.permission.CAMERA)
                    }
                } else {
                    showToast("尚未偵測到臉部，請保持光線充足並正對前鏡頭")
                }
                return@setOnClickListener
            }
            distanceState.setRefFaceWidthPxAt30cm(wPx)
            showToast("護眼基準線已設定完成！")
        }

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
            .setTitle("解除護眼魔法 (OwO) \uD83E\uDE84")
            .setView(input)
            .setPositiveButton("確定") { _, _ ->
                if (rules.verifyStopPin(input.text.toString())) {
                    rules.setControlEnabled(false)
                    showToast("護眼魔法已解除")
                    refreshUi()
                } else showToast("密碼錯誤，請重新輸入 (QwQ)")
            }.setNegativeButton("取消", null).show()
    }

    private fun refreshUi() {
        vb.tvAccStatus.text = "無障礙服務：${if (isAccessibilityEnabled()) "已開啟" else "尚未開啟"}"
        vb.tvOverlayStatus.text = "顯示在其他應用程式上層：${if (Settings.canDrawOverlays(this)) "已允許" else "尚未允許"}"
        
        vb.btnToggleMode.text = if (rules.getProtectionMode() == 0) "防護模式：強制鎖定 (點擊切換)" else "防護模式：溫和語音提醒 (點擊切換)\n(時間到僅彈出小視窗與語音)"
        
        if (rules.isControlEnabled()) {
            vb.btnToggleControl.text = "解除護眼魔法 (OwO)"
            vb.btnToggleControl.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E53935"))
            vb.btnToggleControl.setTextColor(Color.WHITE)
        } else {
            vb.btnToggleControl.text = "啟動護眼魔法 (OwO) \uD83E\uDE84"
            vb.btnToggleControl.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
            vb.btnToggleControl.setTextColor(Color.WHITE)
        }

        vb.btnCalibrate30.text = "設定護眼基準線 \uD83E\uDE84"
        
        if (distanceState.isServiceRunning()) {
            vb.btnToggleDistance.text = "解除距離偵測"
            vb.btnToggleDistance.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E53935"))
            vb.btnToggleDistance.setTextColor(Color.WHITE)
        } else {
            vb.btnToggleDistance.text = "啟動距離偵測 (OwO) \uD83D\uDEE1\uFE0F"
            vb.btnToggleDistance.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
            vb.btnToggleDistance.setTextColor(Color.WHITE)
        }

        vb.tvProtectionStatus.text = "循環時間：${rules.getCycleLimitSeconds() / 60} 分鐘\n保護控管：${if (rules.isControlEnabled()) "已啟動" else "未啟動"}"
    }

    private fun isAccessibilityEnabled(): Boolean {
        if (Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) return false
        return Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?.contains(packageName) == true
    }
}