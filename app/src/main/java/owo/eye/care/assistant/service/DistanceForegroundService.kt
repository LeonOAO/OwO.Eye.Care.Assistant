package owo.eye.care.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import owo.eye.care.assistant.data.DistanceStateStore
import owo.eye.care.assistant.overlay.DistanceOverlayController
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DistanceForegroundService : Service(), LifecycleOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    override fun getLifecycle(): Lifecycle = lifecycleRegistry

    private lateinit var stateStore: DistanceStateStore
    private lateinit var distanceOverlay: DistanceOverlayController
    private lateinit var cameraExecutor: ExecutorService
    private var isCameraStarted = false

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
    )

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        stateStore = DistanceStateStore(this)
        distanceOverlay = DistanceOverlayController(this)
        cameraExecutor = Executors.newSingleThreadExecutor()
        startForeground(2002, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        stateStore.setServiceRunning(true)
        if (!isCameraStarted) {
            isCameraStarted = true
            startCamera()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        stateStore.setServiceRunning(false)
        stateStore.setBlocked(false)
        distanceOverlay.hide()
        cameraExecutor.shutdown()
        stopForeground(true)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    processImageProxy(imageProxy)
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis)
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
        val shieldEmoji = String(intArrayOf(0x1F6E1), 0, 1) + "\uFE0F"
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("OwO 護眼距離守護中 (OwO) $shieldEmoji")
            .setContentText("正在背景監測您的用眼距離")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
    }
}