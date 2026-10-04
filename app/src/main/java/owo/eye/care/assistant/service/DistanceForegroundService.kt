package owo.eye.care.assistant.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
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
import java.util.concurrent.Executors

class DistanceForegroundService : LifecycleService() {
    private lateinit var stateStore: DistanceStateStore
    private lateinit var distanceOverlay: DistanceOverlayController
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
    )
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    
    private var tooCloseFrames = 0
    private var normalFrames = 0

    override fun onCreate() {
        super.onCreate()
        stateStore = DistanceStateStore(this)
        distanceOverlay = DistanceOverlayController(this)
        startForeground(2002, createNotification())
        startCamera()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        stateStore.setServiceRunning(true)
        return START_STICKY
    }

    override fun onDestroy() {
        stateStore.setServiceRunning(false)
        distanceOverlay.hide()
        cameraExecutor.shutdown()
        detector.close()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    @SuppressLint("UnsafeOptInUsageError")
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        detector.process(image)
                            .addOnSuccessListener { faces ->
                                if (faces.isNotEmpty()) {
                                    val face = faces.maxByOrNull { it.boundingBox.width() }
                                    val wPx = face?.boundingBox?.width() ?: 0
                                    stateStore.setLastFaceWidthPx(wPx)

                                    val ref = stateStore.getRefFaceWidthPxAt30cm()
                                    if (ref > 0 && wPx > 0) {
                                        val currentCm = (ref * 30) / wPx
                                        stateStore.setLastDistanceCm(currentCm)
                                        
                                        // 假設距離小於等於 25 公分 (即 wPx >= ref * 1.2) 即視為太近
                                        if (currentCm in 1..25) {
                                            tooCloseFrames++
                                            normalFrames = 0
                                            if (tooCloseFrames >= 3) {
                                                if (!distanceOverlay.isShowing()) {
                                                    distanceOverlay.show()
                                                }
                                            }
                                        } else {
                                            normalFrames++
                                            tooCloseFrames = 0
                                            if (normalFrames >= 2) {
                                                if (distanceOverlay.isShowing()) {
                                                    distanceOverlay.hide()
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    stateStore.setLastFaceWidthPx(0)
                                    if (distanceOverlay.isShowing()) {
                                        distanceOverlay.hide()
                                    }
                                }
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    } else {
                        imageProxy.close()
                    }
                }

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun createNotification(): Notification {
        val channelId = "owo_distance_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "護眼距離偵測服務", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("OwO 護眼距離守護中 (OwO) \uD83D\uDEE1\uFE0F")
            .setContentText("正在背景監測您的用眼距離")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
    }
}
