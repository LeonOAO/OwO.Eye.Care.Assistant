package owo.eye.care.assistant.distance

/** 只在校正時收集十次有效且穩定的讀值；畫面方向或影像尺寸改變就重收。 */
class CalibrationSamples {
    private val widths = ArrayList<Int>(10)
    var key = ""
        private set
    val count: Int get() = widths.size

    fun reset() { widths.clear(); key = "" }

    fun add(profile: String, width: Int): Int? {
        if (profile != key) { reset(); key = profile }
        if (width <= 0) { reset(); return null }
        if (widths.isNotEmpty()) {
            val baseline = widths.sorted()[widths.size / 2]
            if (kotlin.math.abs(width - baseline) > baseline * 0.15) widths.clear()
        }
        if (widths.size == 10) widths.clear()
        widths.add(width)
        if (widths.size < 10) return null
        val sorted = widths.sorted()
        val median = ((sorted[4].toLong() + sorted[5]) / 2).toInt()
        if (sorted.last() - sorted.first() > median * 0.20) {
            widths.clear()
            return null
        }
        return median
    }
}
