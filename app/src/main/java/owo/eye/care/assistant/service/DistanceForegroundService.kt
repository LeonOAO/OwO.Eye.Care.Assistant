package owo.eye.care.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import owo.eye.care.assistant.data.DistanceStateStore
import owo.eye.care.assistant.overlay.DistanceOverlayController
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DistanceForegroundService : LifecycleService() {
    private lateinit var stateStore: DistanceStateStore
    private lateinit var distanceOverlay: DistanceOverlayController
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private var isCameraStarted = false

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
    )

    override fun onCreate() {
        super.onCreate()
        stateStore = DistanceStateStore(this)
        distanceOverlay = DistanceOverlayController(this)
        cameraExecutor = Executors.newSingleThreadExecutor()
        startForeground(2002, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        stateStore.setServiceRunning(true)
        if (!isCameraStarted) {
            isCameraStarted = true
            startCamera()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stateStore.setServiceRunning(false)
        stateStore.setBlocked(false)
        distanceOverlay.hide()
        try {
            cameraProvider?.unbindAll()
        } catch (_: Exception) {}
        cameraExecutor.shutdown()
        stopForeground(true)
        super.onDestroy()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    processImageProxy(imageProxy)
                }

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(this, cameraSelector, imageAnalysis)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            detector.process(image)
                .addOnSuccessListener { faces ->
                    if (faces.isNotEmpty()) {
                        val face = faces[0]
                        val wPx = face.boundingBox.width()
                        stateStore.setLastFaceWidthPx(wPx)

                        val ref = stateStore.getRefFaceWidthPxAt30cm()
                        if (ref > 0) {
                            val cm = (ref * 30) / wPx
                            stateStore.setLastDistanceCm(cm)

                            if (cm < 28) {
                                distanceOverlay.show()
                            } else {
                                distanceOverlay.hide()
                            }
                        }
                    } else {
                        stateStore.setLastFaceWidthPx(0)
                    }
                }
                .addOnFailureListener {}
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun createNotification(): Notification {
        val channelId = "owo_distance_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "OwO Guard Service", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
        val shield = "\uD83D\uDEE1\uFE0F"
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("OwO 護眼距離守護中 (OwO) $shield")
            .setContentText("正在背景監測您的用眼距離")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
    }
}