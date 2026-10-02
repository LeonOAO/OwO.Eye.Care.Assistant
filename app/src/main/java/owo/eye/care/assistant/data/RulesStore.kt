package owo.eye.care.assistant.data

import android.content.Context

class RulesStore(context: Context) {

    private val sp = context.getSharedPreferences("guardian_rules", Context.MODE_PRIVATE)

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
}