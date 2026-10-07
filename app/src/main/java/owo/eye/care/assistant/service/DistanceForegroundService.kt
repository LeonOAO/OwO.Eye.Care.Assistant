package owo.eye.care.assistant.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.view.WindowManager
import androidx.camera.core.Preview
import com.google.mlkit.vision.face.Face
import owo.eye.care.assistant.distance.CalibrationBridge
import owo.eye.care.assistant.distance.DistanceGate
import android.os.Handler
import android.os.Looper
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import owo.eye.care.assistant.data.DistanceStateStore
import owo.eye.care.assistant.overlay.DistanceOverlayController
import owo.eye.care.assistant.util.DeviceUsageMonitor
import java.util.concurrent.Executors

/** 保留已由主畫面啟動的前景服務；待機只解除相機綁定，不從解鎖廣播新建服務。 */
class DistanceForegroundService : LifecycleService() {
    private lateinit var stateStore: DistanceStateStore
    private lateinit var distanceOverlay: DistanceOverlayController
    private lateinit var usageMonitor: DeviceUsageMonitor
    private val callbackExecutor by lazy { ContextCompat.getMainExecutor(this) }
    private val handler = Handler(Looper.getMainLooper())
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .build()
    )
    private var cameraProvider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    @Volatile private var active = false
    @Volatile private var destroyed = false
    @Volatile private var generation = 0
    private val distanceGate = DistanceGate()
    private var preview: Preview? = null
    private var lastProfile = ""
    private val frameCounter = java.util.concurrent.atomic.AtomicInteger(0)
    private var processing = false
    private val retry = Runnable {
        if (!destroyed && usageMonitor.isActive() && active) startCamera()
    }

    override fun onCreate() {
        super.onCreate()
        stateStore = DistanceStateStore(this)
        distanceOverlay = DistanceOverlayController(this)
        CalibrationBridge.onPreviewChanged = {
            if (!destroyed) {
                val available = usageMonitor.isActive()
                pauseCamera()
                active = available
                if (available) { stateStore.setDistancePaused(false); startCamera() }
            }
        }
        usageMonitor = DeviceUsageMonitor(this) { available ->
            active = available
            if (available) {
                stateStore.setDistancePaused(false)
                startCamera()
            } else {
                pauseCamera()
            }
            updateNotification()
        }
        // stateStore.setServiceRunning(true) removed for calibration check
        stateStore.setCameraError("")
        startForeground(2002, createNotification())
        usageMonitor.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val isCalib = intent?.getBooleanExtra("is_calibration_only", false) ?: false
        if (!isCalib) {
            stateStore.setServiceRunning(true)
        }
        return START_STICKY
    }

    /** 使舊回呼失效，解除相機並清除即時讀值；校正值與時間進度保持不變。 */
    private fun pauseCamera() {
        active = false
        generation++
        handler.removeCallbacks(retry)
        analysis?.clearAnalyzer()
        analysis?.let { cameraProvider?.unbind(it) }
        analysis = null
        preview?.let { cameraProvider?.unbind(it) }
        preview = null
        distanceOverlay.hide()
        distanceGate.reset()
        CalibrationBridge.samples.reset()
        stateStore.setDistanceStatus("待機暫停")
        frameCounter.set(0)
        stateStore.setLastFaceWidthPx(0)
        stateStore.setLastDistanceCm(0)
        stateStore.setDistancePaused(true)
    }

    private fun startCamera() {
        if (destroyed || !active || !usageMonitor.isActive() || analysis != null) return
        val session = ++generation
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (destroyed || !active || session != generation || !usageMonitor.isActive()) return@addListener
            try {
                val provider = future.get()
                cameraProvider = provider
                val useCase = ImageAnalysis.Builder()
                    .setTargetResolution(Size(480, 360))
                    .setTargetRotation(displayRotation())
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                useCase.setAnalyzer(cameraExecutor) { proxy ->
                    // 在背景執行緒直接丟棄跳過的影格，不把每個影格送到主執行緒。
                    if (destroyed || !active || session != generation || frameCounter.incrementAndGet() % 2 != 0) {
                        proxy.close()
                        return@setAnalyzer
                    }
                    // ML Kit 與遮罩更新統一回到主執行緒，避免暫停和結果更新競態。
                    callbackExecutor.execute {
                        if (destroyed || !active || session != generation ||
                            !usageMonitor.isActive() || processing) {
                            proxy.close()
                        } else {
                            if (useCase.targetRotation != displayRotation()) {
                                useCase.targetRotation = displayRotation()
                                preview?.targetRotation = displayRotation()
                                invalidateDistance("方向已改變，等待有效讀值")
                                proxy.close()
                                return@execute
                            }
                            @SuppressLint("UnsafeOptInUsageError")
                            val media = proxy.image
                            if (media == null) {
                                proxy.close()
                            } else {
                                processing = true
                                try {
                                    val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                                    detector.process(image)
                                        .addOnSuccessListener(callbackExecutor) { faces ->
                                            if (!destroyed && active && session == generation && usageMonitor.isActive()) {
                                                val rotated = proxy.imageInfo.rotationDegrees % 180 != 0
                                                val imageWidth = if (rotated) proxy.height else proxy.width
                                                val imageHeight = if (rotated) proxy.width else proxy.height
                                                handleFaces(faces, imageWidth, imageHeight)
                                            }
                                        }
                                        .addOnFailureListener(callbackExecutor) { error ->
                                            if (!destroyed && session == generation) {
                                                stateStore.setCameraError(error.message ?: "臉部分析失敗")
                                                invalidateDistance("相機異常")
                                            }
                                        }
                                        .addOnCompleteListener(callbackExecutor) {
                                            proxy.close()
                                            processing = false
                                            if (destroyed) detector.close()
                                        }
                                } catch (error: Exception) {
                                    processing = false
                                    proxy.close()
                                    if (!destroyed) {
                                        stateStore.setCameraError(error.message ?: "影像分析失敗")
                                        invalidateDistance("相機異常")
                                    }
                                }
                            }
                        }
                    }
                }
                analysis = useCase
                val surface = CalibrationBridge.surface
                if (surface != null) {
                    val calibrationPreview = Preview.Builder().setTargetResolution(Size(480, 360)).setTargetRotation(displayRotation()).build()
                    calibrationPreview.setSurfaceProvider(surface)
                    preview = calibrationPreview
                    provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, useCase, calibrationPreview)
                } else {
                    provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, useCase)
                }
                stateStore.setCameraError("")
                updateNotification()
            } catch (error: Exception) {
                analysis?.clearAnalyzer()
                analysis?.let { cameraProvider?.unbind(it) }
                analysis = null
                preview?.let { cameraProvider?.unbind(it) }
                preview = null
                invalidateDistance("相機異常")
                stateStore.setCameraError(error.message ?: "相機啟動失敗")
                CalibrationBridge.report("相機啟動失敗，請檢查權限或其他 App 是否占用相機", 0)
                updateNotification()
                handler.removeCallbacks(retry)
                handler.postDelayed(retry, 5000L)
            }
        }, callbackExecutor)
    }

    @Suppress("DEPRECATION")
    private fun displayRotation(): Int =
        (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 重新建立 session，舊方向的在途影像結果不會套用到新方向基準。
        val available = usageMonitor.isActive()
        pauseCamera()
        lastProfile = ""
        active = available
        if (available) {
            stateStore.setDistancePaused(false)
            stateStore.setDistanceStatus("方向已改變，等待有效讀值")
            startCamera()
        }
    }

    private fun invalidateDistance(status: String) {
        distanceGate.reset()
        distanceOverlay.hide()
        stateStore.setLastFaceWidthPx(0)
        stateStore.setLastDistanceCm(0)
        stateStore.setDistanceStatus(status)
    }

    /** 遊戲期間只用既有臉框與整數比較；品質篩選僅在校正畫面收集時執行。 */
    private fun handleFaces(faces: List<Face>, imageWidth: Int, imageHeight: Int) {
        val profile = "${resources.configuration.orientation}:${imageWidth}x${imageHeight}"
        if (profile != lastProfile) {
            lastProfile = profile
            distanceGate.reset()
            distanceOverlay.hide()
        }
        stateStore.setCurrentProfile(profile)
        if (CalibrationBridge.surface != null) {
            invalidateDistance("校正畫面")
            if (CalibrationBridge.completed) return
            if (!CalibrationBridge.collecting) {
                CalibrationBridge.report("請將平板與臉部保持實際 30 公分，再按開始收集")
                return
            }
            val face = faces.singleOrNull()
            val box = face?.boundingBox
            val quality = box != null && box.width() >= 100 && box.height() >= 100 &&
                box.left >= 0 && box.top >= 0 && box.right <= imageWidth && box.bottom <= imageHeight &&
                kotlin.math.abs(box.centerX() - imageWidth / 2) <= imageWidth * 0.25 &&
                kotlin.math.abs(box.centerY() - imageHeight / 2) <= imageHeight * 0.30
            if (!quality || box == null) {
                CalibrationBridge.samples.reset()
                CalibrationBridge.report("請保持單一完整臉部置中、光線充足並正對鏡頭", 0)
                return
            }
            val median = CalibrationBridge.samples.add(profile, box.width())
            if (median != null) {
                stateStore.saveProfile(profile, median)
                CalibrationBridge.collectingFinished()
                CalibrationBridge.report("校正完成：已保存目前方向與影像尺寸的基準", 10, true)
            } else {
                CalibrationBridge.report("收集中，請保持實際 30 公分與臉部穩定")
            }
            return
        }
        val width = faces.maxByOrNull { it.boundingBox.width() }?.boundingBox?.width() ?: 0
        if (width <= 0) { invalidateDistance("未偵測到臉"); return }
        stateStore.setLastFaceWidthPx(width)
        val ref = stateStore.getProfile(profile)
        if (ref <= 0) {
            distanceGate.reset()
            distanceOverlay.hide()
            stateStore.setLastDistanceCm(0)
            stateStore.setDistanceStatus("目前方向／影像尺寸尚未校正")
            return
        }
        val cm = (ref.toLong() * 30 / width).toInt()
        stateStore.setLastDistanceCm(cm)
        if (distanceGate.update(cm)) distanceOverlay.show() else distanceOverlay.hide()
        stateStore.setDistanceStatus(if (distanceGate.showing) "距離過近" else if (cm < 25) "過近確認中" else "距離正常")
    }

    override fun onDestroy() {
        destroyed = true
        CalibrationBridge.onPreviewChanged = null
        usageMonitor.stop()
        pauseCamera()
        stateStore.setServiceRunning(false)
        stateStore.setDistancePaused(false)
        cameraExecutor.shutdown()
        // 在途影像先完成再關閉偵測器，避免釋放後回呼仍使用它。
        if (!processing) detector.close()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun updateNotification() {
        if (!destroyed) {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(2002, createNotification())
        }
    }

    private fun createNotification(): Notification {
        val channelId = "owo_distance_service"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(channelId, "護眼距離偵測服務", NotificationManager.IMPORTANCE_LOW))
        val message = when {
            stateStore.isDistancePaused() -> "待機／鎖定中：相機與距離提醒已暫停"
            stateStore.getCameraError().isNotEmpty() -> "相機暫時不可用，正在等待重試"
            else -> "正在背景監測您的用眼距離"
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("OwO 護眼距離守護中 (OwO)")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }
}
