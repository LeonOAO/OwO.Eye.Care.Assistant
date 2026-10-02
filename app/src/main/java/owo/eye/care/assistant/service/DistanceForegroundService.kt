package owo.eye.care.assistant.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import owo.eye.care.assistant.data.DistanceStateStore
import java.util.concurrent.Executors

class DistanceForegroundService : Service(), LifecycleOwner {

    //  手動建立生命週期，讓 CameraX 可以成功綁定
    private val lifecycleRegistry = LifecycleRegistry(this)
    private lateinit var distanceState: DistanceStateStore
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    override fun getLifecycle(): Lifecycle = lifecycleRegistry

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        distanceState = DistanceStateStore(this)
        startForegroundServiceNotification()
        startCamera()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        cameraExecutor.shutdown()
        
        // 關閉時重置狀態
        distanceState.setBlocked(false)
        distanceState.setLastDistanceCm(0)
        distanceState.setLastFaceWidthPx(0)
        super.onDestroy()
    }

    private fun startForegroundServiceNotification() {
        val channelId = "owo_distance_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "距離守護服務",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("OwO 護眼小助手")
            .setContentText("正在背景監測螢幕距離...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(2, notification)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build()
            val detector = FaceDetection.getClient(options)

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage != null) {
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    detector.process(image)
                        .addOnSuccessListener { faces ->
                            if (faces.isNotEmpty()) {
                                val face = faces[0]
                                val faceWidthPx = face.boundingBox.width()
                                distanceState.setLastFaceWidthPx(faceWidthPx)

                                val refWidth = distanceState.getRefFaceWidthPxAt30cm()
                                if (refWidth > 0) {
                                    // 距離計算：(參考臉寬 / 當前臉寬) * 30cm
                                    val cm = (refWidth.toFloat() / faceWidthPx.toFloat() * 30f).toInt()
                                    distanceState.setLastDistanceCm(cm)
                                    distanceState.setBlocked(cm < 30)
                                } else {
                                    distanceState.setLastDistanceCm(0)
                                    distanceState.setBlocked(false)
                                }
                            } else {
                                distanceState.setLastFaceWidthPx(0)
                                distanceState.setLastDistanceCm(0)
                                distanceState.setBlocked(false)
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
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }
}