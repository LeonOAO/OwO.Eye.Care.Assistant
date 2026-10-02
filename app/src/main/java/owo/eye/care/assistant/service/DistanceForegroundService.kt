package owo.eye.care.assistant.service
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
import owo.eye.care.assistant.overlay.DistanceOverlayController
import java.util.concurrent.Executors

class DistanceForegroundService : Service(), LifecycleOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private lateinit var distanceState: DistanceStateStore
    private lateinit var distanceOverlay: DistanceOverlayController
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override val lifecycle: Lifecycle get() = lifecycleRegistry

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        distanceState = DistanceStateStore(this)
        distanceOverlay = DistanceOverlayController(this)
        distanceState.setServiceRunning(true)
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
        distanceState.setServiceRunning(false)
        distanceState.setBlocked(false)
        distanceState.setLastDistanceCm(0)
        distanceState.setLastFaceWidthPx(0)
        mainHandler.post { distanceOverlay.hide() }
        super.onDestroy()
    }

    private fun startForegroundServiceNotification() {
        val channelId = "owo_distance_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "距離守護服務", NotificationManager.IMPORTANCE_LOW)
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
            val imageAnalysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            val detector = FaceDetection.getClient(FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST).build())

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage != null) {
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    detector.process(image).addOnSuccessListener { faces ->
                        if (faces.isNotEmpty()) {
                            val faceWidthPx = faces[0].boundingBox.width()
                            distanceState.setLastFaceWidthPx(faceWidthPx)
                            val refWidth = distanceState.getRefFaceWidthPxAt30cm()
                            
                            if (refWidth > 0) {
                                val cm = (refWidth.toFloat() / faceWidthPx.toFloat() * 30f).toInt()
                                distanceState.setLastDistanceCm(cm)
                                
                                if (cm < 30) {
                                    distanceState.setBlocked(true)
                                    mainHandler.post { distanceOverlay.show() }
                                } else {
                                    distanceState.setBlocked(false)
                                    mainHandler.post { distanceOverlay.hide() }
                                }
                            } else {
                                distanceState.setLastDistanceCm(0)
                                distanceState.setBlocked(false)
                                mainHandler.post { distanceOverlay.hide() }
                            }
                        } else {
                            distanceState.setLastFaceWidthPx(0)
                            distanceState.setLastDistanceCm(0)
                            distanceState.setBlocked(false)
                            mainHandler.post { distanceOverlay.hide() } 
                        }
                    }.addOnCompleteListener { imageProxy.close() }
                } else { imageProxy.close() }
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, imageAnalysis)
            } catch (e: Exception) { e.printStackTrace() }
        }, ContextCompat.getMainExecutor(this))
    }
}