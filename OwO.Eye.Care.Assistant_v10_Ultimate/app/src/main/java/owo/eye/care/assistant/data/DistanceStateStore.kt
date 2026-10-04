package owo.eye.care.assistant.data
import android.content.Context
class DistanceStateStore(context: Context) {
    fun getElapsedSeconds(): Int = prefs.getInt("elapsed_seconds", 0)
    fun setElapsedSeconds(sec: Int) = prefs.edit().putInt("elapsed_seconds", sec).apply()
    private val prefs = context.getSharedPreferences("owo_distance", Context.MODE_PRIVATE)
    fun setBlocked(b: Boolean) = prefs.edit().putBoolean("blocked", b).apply()
    fun isBlocked() = prefs.getBoolean("blocked", false)
    fun setLastDistanceCm(cm: Int) = prefs.edit().putInt("last_cm", cm).apply()
    fun getLastDistanceCm() = prefs.getInt("last_cm", 0)
    fun setLastFaceWidthPx(px: Int) = prefs.edit().putInt("last_face_px", px).apply()
    fun getLastFaceWidthPx() = prefs.getInt("last_face_px", 0)
    fun setRefFaceWidthPxAt30cm(px: Int) = prefs.edit().putInt("ref_face_30", px).apply()
    fun getRefFaceWidthPxAt30cm() = prefs.getInt("ref_face_30", 0)
    fun setServiceRunning(b: Boolean) = prefs.edit().putBoolean("running", b).apply()
    fun isServiceRunning() = prefs.getBoolean("running", false)
}