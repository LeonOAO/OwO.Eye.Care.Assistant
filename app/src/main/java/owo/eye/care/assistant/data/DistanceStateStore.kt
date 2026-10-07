package owo.eye.care.assistant.data
import android.content.Context

class DistanceStateStore(context: Context) {
    private val prefs = context.getSharedPreferences("owo_distance", Context.MODE_PRIVATE)
    fun setBlocked(b: Boolean) = prefs.edit().putBoolean("blocked", b).apply()
    fun isBlocked() = prefs.getBoolean("blocked", false)
    fun setLastDistanceCm(cm: Int) {
        if (getLastDistanceCm() != cm) prefs.edit().putInt("last_cm", cm).apply()
    }
    fun getLastDistanceCm() = prefs.getInt("last_cm", 0)
    fun setLastFaceWidthPx(px: Int) {
        if (getLastFaceWidthPx() != px) prefs.edit().putInt("last_face_px", px).apply()
    }
    fun getLastFaceWidthPx() = prefs.getInt("last_face_px", 0)
    fun setRefFaceWidthPxAt30cm(px: Int) = prefs.edit().putInt("ref_face_30", px).apply()
    fun getRefFaceWidthPxAt30cm() = prefs.getInt("ref_face_30", 0)
    fun setServiceRunning(b: Boolean) = prefs.edit().putBoolean("running", b).apply()
    fun isServiceRunning() = prefs.getBoolean("running", false)
    // running 表示服務仍啟用；paused 表示待機暫停，兩者不可混用。
    fun setDistancePaused(paused: Boolean) = prefs.edit().putBoolean("distance_paused", paused).apply()
    fun isDistancePaused() = prefs.getBoolean("distance_paused", false)
    fun setCameraError(error: String) {
        if (getCameraError() != error) prefs.edit().putString("camera_error", error).apply()
    }
    fun getCameraError() = prefs.getString("camera_error", "") ?: ""
    fun getElapsedSeconds(): Int = prefs.getInt("elapsed_seconds", 0)
    fun setElapsedSeconds(sec: Int) = prefs.edit().putInt("elapsed_seconds", sec).apply()
    // 舊 ref_face_30 保留供回滾；新版本僅使用方向／實際影像尺寸吻合的基準。
    fun saveProfile(key: String, width: Int) {
        prefs.edit().putInt("profile_$key", width).putInt("ref_face_30", width).apply()
    }
    fun getProfile(key: String) = prefs.getInt("profile_$key", 0)
    fun setDistanceStatus(status: String) {
        if (getDistanceStatus() != status) prefs.edit().putString("distance_status", status).apply()
    }
    fun getDistanceStatus() = prefs.getString("distance_status", "尚未取得") ?: "尚未取得"
    fun setCurrentProfile(key: String) {
        if (getCurrentProfile() != key) prefs.edit().putString("current_profile", key).apply()
    }
    fun getCurrentProfile() = prefs.getString("current_profile", "") ?: ""
}