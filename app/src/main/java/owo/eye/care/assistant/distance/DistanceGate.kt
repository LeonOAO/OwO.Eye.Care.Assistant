package owo.eye.care.assistant.distance

/** 純判斷邏輯：不使用計時延遲、不新增影像分析。 */
class DistanceGate {
    var showing = false
        private set
    private var closeCount = 0
    private var farCount = 0

    fun update(cm: Int): Boolean {
        when {
            cm <= 0 -> reset()
            cm < 25 -> {
                farCount = 0
                closeCount = (closeCount + 1).coerceAtMost(2)
                if (closeCount == 2) showing = true
            }
            cm >= 30 -> {
                closeCount = 0
                farCount = (farCount + 1).coerceAtMost(2)
                if (farCount == 2) showing = false
            }
            else -> {
                closeCount = 0
                farCount = 0
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
