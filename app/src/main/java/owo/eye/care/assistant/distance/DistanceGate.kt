package owo.eye.care.assistant.distance

/** 純判斷邏輯：同一側需連續三次有效讀值，不使用計時延遲、不新增影像分析。 */
class DistanceGate {
    var showing = false
        private set
    private var closeCount = 0
    private var farCount = 0

    fun update(cm: Int): Boolean {
        when {
            cm <= 0 -> reset()
            cm < 30 -> {
                farCount = 0
                closeCount = (closeCount + 1).coerceAtMost(3)
                if (closeCount == 3) showing = true
            }
            else -> {
                closeCount = 0
                farCount = (farCount + 1).coerceAtMost(3)
                if (farCount == 3) showing = false
            }
        }
        return showing
    }

    fun reset() {
        showing = false
        closeCount = 0
        farCount = 0
    }
}
