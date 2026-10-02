package owo.eye.care.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import owo.eye.care.assistant.data.DistanceStateStore
import owo.eye.care.assistant.overlay.DistanceOverlayController

class DistanceForegroundService : Service() {
    private lateinit var stateStore: DistanceStateStore
    private lateinit var distanceOverlay: DistanceOverlayController
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
    )

    override fun onCreate() {
        super.onCreate()
        stateStore = DistanceStateStore(this)
        distanceOverlay = DistanceOverlayController(this)
        startForeground(2002, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stateStore.setServiceRunning(true)
        return START_STICKY
    }

    override fun onDestroy() {
        stateStore.setServiceRunning(false)
        stateStore.setBlocked(false)
        distanceOverlay.hide()
        stopForeground(true)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        val channelId = "owo_distance_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "護眼距離偵測服務", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("OwO 護眼距離守護中 (OwO) ")
            .setContentText("正在背景監測您的用眼距離")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
    }
}