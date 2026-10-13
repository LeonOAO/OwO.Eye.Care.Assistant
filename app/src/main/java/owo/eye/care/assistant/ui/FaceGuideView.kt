package owo.eye.care.assistant.ui

import android.content.Context
import android.content.res.Configuration
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
 * 2. 會發光的薄荷綠虛線橢圓：大小依相機畫面換算，臉部對齊時約 30 公分。
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

        // ===== 橢圓大小：對齊臉部時約為 30 公分 =====
        // 1. 先找出相機畫面實際顯示的區域：
        //    PreviewView 採 FIT_CENTER，直向畫面為 3:4、橫向畫面為 4:3，置中顯示。
        val portrait = resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        val imageAspect = if (portrait) 3f / 4f else 4f / 3f   // 寬 ÷ 高
        val imageWidth: Float
        val imageHeight: Float
        if (w.toFloat() / h > imageAspect) {
            imageHeight = h.toFloat()
            imageWidth = imageHeight * imageAspect
        } else {
            imageWidth = w.toFloat()
            imageHeight = imageWidth / imageAspect
        }
        val imageLongSide = maxOf(imageWidth, imageHeight)

        // 2. 一般前鏡頭在 30 公分時，畫面長邊約可拍到 40 公分；
        //    成人頭部（下巴到頭頂）約 22 公分 → 橢圓高度約為畫面長邊的 55%。
        //    直向時長邊是高度（約佔 55%）；橫向時長邊是寬度（約佔畫面高度 73%）。
        var ovalHeight = imageLongSide * FACE_HEIGHT_RATIO_AT_30CM

        // 3. 保護：不可超出相機畫面高度的 90%，也不可超出畫面寬度的 90%
        ovalHeight = minOf(ovalHeight, imageHeight * 0.90f, imageWidth * 0.90f * OVAL_ASPECT)
        val ovalWidth = ovalHeight / OVAL_ASPECT
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

    private companion object {
        /** 30 公分時，臉部（頭部）高度約佔相機畫面長邊的比例。 */
        const val FACE_HEIGHT_RATIO_AT_30CM = 0.55f

        /** 橢圓高寬比（高 ÷ 寬），接近臉部輪廓。 */
        const val OVAL_ASPECT = 1.31f
    }
}
