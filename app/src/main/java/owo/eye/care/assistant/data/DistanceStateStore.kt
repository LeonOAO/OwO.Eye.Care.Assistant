package owo.eye.care.assistant.data

import android.content.Context

class DistanceStateStore(context: Context) {
    private val sp = context.getSharedPreferences("distance_state", Context.MODE_PRIVATE)

    fun setBlocked(blocked: Boolean) = sp.edit().putBoolean("is_blocked", blocked).apply()
    fun isBlocked(): Boolean = sp.getBoolean("is_blocked", false)

    fun setLastDistanceCm(cm: Int) = sp.edit().putInt("last_distance_cm", cm).apply()
    fun getLastDistanceCm(): Int = sp.getInt("last_distance_cm", 0)

    fun setLastFaceWidthPx(px: Int) = sp.edit().putInt("last_face_width_px", px).apply()
    fun getLastFaceWidthPx(): Int = sp.getInt("last_face_width_px", 0)

    fun setRefFaceWidthPxAt30cm(px: Int) = sp.edit().putInt("ref_face_width_px", px).apply()
    fun getRefFaceWidthPxAt30cm(): Int = sp.getInt("ref_face_width_px", 0)
}