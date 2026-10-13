package owo.eye.care.assistant.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * 校正畫面的臉部引導框（只負責繪圖，不參與相機或校正邏輯）。
 *
 * 繪製內容：
 * 1. 橢圓框外的半透明暗化遮罩，讓視線集中在框內。
 * 2. 會發光的薄荷綠虛線橢圓。
 * 3. 左下、右下兩個白色圓角角框。
 */
class FaceGuideView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ===== 尺寸工具 =====
    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density

    // ===== 畫筆 =====

    /** 橢圓框外暗化遮罩 */
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(72, 30, 20, 40)
        style = Paint.Style.FILL
    }

    /** 發光虛線橢圓 */
    private val ovalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(125, 240, 192)
        style = Paint.Style.STROKE
        strokeWidth = dp(4f)
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(dp(9f), dp(7f)), 0f)
        setShadowLayer(dp(9f), 0f, 0f, Color.rgb(125, 240, 192))
    }

    /** 白色角框 */
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(4f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // ===== 重複使用的繪圖物件，避免每次 onDraw 配置記憶體 =====
    private val ovalRect = RectF()
    private val dimPath = Path()
    private val cornerPath = Path()

    init {
        // setShadowLayer 的外光暈需要軟體圖層
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        // 橢圓：高寬比約 1.31，依可用空間取較小者，確保完整顯示
        val maxHeight = h * 0.78f
        val maxWidth = w * 0.62f
        val ovalHeight = minOf(maxHeight, maxWidth * 1.31f)
        val ovalWidth = ovalHeight / 1.31f
        val cx = w / 2f
        val cy = h / 2f
        ovalRect.set(cx - ovalWidth / 2f, cy - ovalHeight / 2f, cx + ovalWidth / 2f, cy + ovalHeight / 2f)

        // 暗化遮罩：整個矩形挖掉橢圓（EVEN_ODD）
        dimPath.reset()
        dimPath.fillType = Path.FillType.EVEN_ODD
        dimPath.addRect(0f, 0f, w.toFloat(), h.toFloat(), Path.Direction.CW)
        dimPath.addOval(ovalRect, Path.Direction.CW)

        // 角框：左下、右下各一個 L 形圓角
        val inset = dp(14f)
        val arm = dp(22f)
        val radius = dp(10f)
        val bottom = h - inset
        cornerPath.reset()
        // 左下
        cornerPath.moveTo(inset, bottom - arm)
        cornerPath.lineTo(inset, bottom - radius)
        cornerPath.quadTo(inset, bottom, inset + radius, bottom)
        cornerPath.lineTo(inset + arm, bottom)
        // 右下
        val right = w - inset
        cornerPath.moveTo(right, bottom - arm)
        cornerPath.lineTo(right, bottom - radius)
        cornerPath.quadTo(right, bottom, right - radius, bottom)
        cornerPath.lineTo(right - arm, bottom)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (ovalRect.isEmpty) return
        canvas.drawPath(dimPath, dimPaint)
        canvas.drawOval(ovalRect, ovalPaint)
        canvas.drawPath(cornerPath, cornerPaint)
    }
}
