package owo.eye.care.assistant.distance

import androidx.camera.core.Preview

/** 所有存取皆在主執行緒；只傳遞預覽 surface 與進度，不保存影像。 */
object CalibrationBridge {
    var surface: Preview.SurfaceProvider? = null
        private set
    var completed = false
        private set
    var collecting = false
        private set
    var listener: ((String, Int, Boolean) -> Unit)? = null
    var onPreviewChanged: (() -> Unit)? = null
    val samples = CalibrationSamples()

    fun attach(provider: Preview.SurfaceProvider, callback: (String, Int, Boolean) -> Unit) {
        surface = provider
        listener = callback
        collecting = false
        completed = false
        samples.reset()
        onPreviewChanged?.invoke()
    }

    fun begin() {
        samples.reset()
        completed = false
        collecting = true
        report("請維持實際 30 公分距離，保持臉部穩定", 0, false)
    }

    fun collectingFinished() { collecting = false; completed = true }

    fun report(message: String, count: Int = samples.count, done: Boolean = false) {
        listener?.invoke(message, count, done)
    }

    fun detach() {
        collecting = false
        completed = false
        samples.reset()
        listener = null
        surface = null
        onPreviewChanged?.invoke()
    }
}
