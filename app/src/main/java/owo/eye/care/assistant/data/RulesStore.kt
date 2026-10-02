package owo.eye.care.assistant.data

import android.content.Context
import java.time.LocalDate

class RulesStore(context: Context) {

    private val sp = context.getSharedPreferences("guardian_rules", Context.MODE_PRIVATE)

    private fun todayKey(): String = LocalDate.now().toString() // yyyy-MM-dd

    fun setControlEnabled(enabled: Boolean) {
        sp.edit().putBoolean("control_enabled", enabled).apply()
    }

    fun isControlEnabled(): Boolean {
        return sp.getBoolean("control_enabled", false)
    }

    fun setParentPin(pin: String) {
        sp.edit().putString("parent_pin", pin).apply()
    }

    fun hasParentPin(): Boolean = !sp.getString("parent_pin", null).isNullOrEmpty()

    fun verifyParentPin(input: String): Boolean {
        val pin = sp.getString("parent_pin", null) ?: return false
        return pin == input
    }

    fun setStopPin(pin: String) {
        sp.edit().putString("stop_pin", pin).apply()
    }

    fun hasStopPin(): Boolean = !sp.getString("stop_pin", null).isNullOrEmpty()

    fun verifyStopPin(input: String): Boolean {
        val pin = sp.getString("stop_pin", null) ?: return false
        return pin == input
    }

    fun setCycleLimitSeconds(seconds: Int) {
        sp.edit().putInt("cycle_limit_seconds", seconds).apply()
    }

    fun getCycleLimitSeconds(): Int {
        return sp.getInt("cycle_limit_seconds", 20 * 60)
    }

    fun setDailyUnlockQuota(quota: Int) {
        sp.edit().putInt("daily_unlock_quota", quota).apply()
    }

    fun getDailyUnlockQuota(): Int {
        return sp.getInt("daily_unlock_quota", 3)
    }

    fun getUsedUnlockCountToday(): Int {
        val key = "unlock_used_${todayKey()}"
        return sp.getInt(key, 0)
    }

    fun getRemainingUnlocksToday(): Int {
        val quota = getDailyUnlockQuota()
        val used = getUsedUnlockCountToday()
        return (quota - used).coerceAtLeast(0)
    }

    fun consumeOneUnlockToday(): Boolean {
        val remaining = getRemainingUnlocksToday()
        if (remaining <= 0) return false
        val key = "unlock_used_${todayKey()}"
        val used = sp.getInt(key, 0)
        sp.edit().putInt(key, used + 1).apply()
        return true
    }

    fun resetUnlockCountToday() {
        val key = "unlock_used_${todayKey()}"
        sp.edit().putInt(key, 0).apply()
    }
}
