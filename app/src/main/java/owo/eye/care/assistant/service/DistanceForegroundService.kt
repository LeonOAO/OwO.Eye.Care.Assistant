package owo.eye.care.assistant.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.LifecycleService
import owo.eye.care.assistant.data.DistanceStateStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class DistanceForegroundService : LifecycleService() {

    private val channelId = "guardian_distance_channel"
    private val notificationId = 2001

    private lateinit var state: DistanceStateStore
    private val executor = Executors.newSingleThreadExecutor()

    private var tooCloseAccumMs: Long = 0
    private var farAccumMs: Long = 0
    private var lastFrameTsMs: Long = 0
    private var lastAnalysisStartMs: Long = 0

    companion object {
        // 遊戲低負載模式：每秒最多執行 3 次 ML Kit 推論。
        private const val ANALYSIS_INTERVAL_MS = 333L
        private val ANALYSIS_RESOLUTION = Size(480, 360)
    }

    override fun onCreate() {
        super.onCreate()
        state = DistanceStateStore(applicationContext)
        state.setBlocked(false)
        state.setLastDistanceCm(-1)
        state.setLastFaceWidthPx(0)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val granted = PermissionChecker.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PermissionChecker.PERMISSION_GRANTED

        if (!granted) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification("遊戲低負載距離守護：強制 ≥30cm")
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        } else 0

        ServiceCompat.startForeground(this, notificationId, notification, fgsType)

        startCameraAnalysis()
        return START_STICKY
    }

    private fun startCameraAnalysis() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setMinFaceSize(0.15f)
                .build()
            val detector = FaceDetection.getClient(options)

            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(ANALYSIS_RESOLUTION)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(executor) { imageProxy ->
                analyzeFrame(detector, imageProxy)
            }

            val selector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build()

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, selector, analysis)

        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyzeFrame(detector: com.google.mlkit.vision.face.FaceDetector, imageProxy: ImageProxy) {
        val analysisStartMs = SystemClock.elapsedRealtime()
        if (analysisStartMs - lastAnalysisStartMs < ANALYSIS_INTERVAL_MS) {
            imageProxy.close()
            return
        }
        lastAnalysisStartMs = analysisStartMs

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        val image = InputImage.fromMediaImage(mediaImage, rotation)

        detector.process(image)
            .addOnSuccessListener { faces ->
                val now = SystemClock.elapsedRealtime()
                if (lastFrameTsMs == 0L) lastFrameTsMs = now
                val delta = now - lastFrameTsMs
                lastFrameTsMs = now

                val face = faces.maxByOrNull { it.boundingBox.width() }

                if (face == null) {
                    // no face -> do not lock
                    tooCloseAccumMs = 0
                    farAccumMs = 0
                    state.setBlocked(false)
                    state.setLastDistanceCm(-1)
                    state.setLastFaceWidthPx(0)
                } else {
                    val wPx = face.boundingBox.width()
                    state.setLastFaceWidthPx(wPx)

                    val refW = state.getRefFaceWidthPxAt30cm()
                    val estCm = if (refW > 0) {
                        (30.0 * refW.toDouble() / wPx.toDouble()).roundToInt()
                    } else {
                        if (wPx >= 380) 25 else 40
                    }

                    state.setLastDistanceCm(estCm)

                    val tooClose = estCm in 1..29
                    val farEnough = estCm >= 30

                    if (tooClose) {
                        tooCloseAccumMs += delta
                        farAccumMs = 0
                    } else if (farEnough) {
                        farAccumMs += delta
                        tooCloseAccumMs = 0
                    } else {
                        tooCloseAccumMs = 0
                        farAccumMs = 0
                    }

                    if (tooCloseAccumMs >= 1200) state.setBlocked(true)
                    if (farAccumMs >= 700) state.setBlocked(false)
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("OwO 護眼小助手・距離守護")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(channelId, "OwO 護眼小助手・距離守護", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(ch)
        }
    }

    override fun onDestroy() {
        executor.shutdown()
        state.setBlocked(false)
        super.onDestroy()
    }
}
