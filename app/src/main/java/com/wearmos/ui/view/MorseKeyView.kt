package com.wearmos.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import com.wearmos.R
import com.wearmos.core.DisplayGeometry

/**
 * 敲击键控区。
 *
 * 整块区域就是一个键：短按 = 点，长按 = 划。
 * 底部有一条随按压时长增长的进度条，越过点划阈值时整块区域变亮 ——
 * 让「点正在变成划」在视觉上可预期，而不是靠盲猜。
 *
 * 触摸在这里只做归一化，不做时长判定；判定统一交给 KeyerStateMachine。
 *
 * 圆屏适配：这是整屏最大的一块触摸区，缩窄它会小到没法用，所以改成
 * **把形状裁到屏幕圆内** —— 下缘变成一段贴着表圈的弧线，看着像刻意设计的。
 * 圆屏的触摸面板本身是圆的，被裁掉的部分本来也点不到，功能上没有任何损失。
 */
class MorseKeyView(context: Context) : View(context) {

    var onDown: ((Int) -> Unit)? = null
    var onUp: ((Int) -> Unit)? = null
    var onCancel: (() -> Unit)? = null

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            12f,
            resources.displayMetrics,
        )
    }

    private val geometry = DisplayGeometry(
        widthPx = resources.displayMetrics.widthPixels,
        heightPx = resources.displayMetrics.heightPixels,
        density = resources.displayMetrics.density,
        isRound = resources.configuration.isScreenRound,
    )

    private val accent = context.getColor(R.color.accent)
    private val accentBright = context.getColor(R.color.accent_bright)
    private val dim = context.getColor(R.color.dim)

    /** 底形状。圆屏上是「圆角矩形 ∩ 屏幕圆」，算一次缓存住，别每帧都做布尔运算。 */
    private val shapePath = Path()
    private var shapeKey: String? = null

    private var pressProgress = 0f
    private var crossed = false

    init {
        fillPaint.color = COLOR_PAD_FILL
        trackPaint.color = withAlpha(accent, 40)
        barPaint.color = accent
        borderPaint.color = dim
        textPaint.color = dim
        isClickable = true
        contentDescription = "摩斯键控区，短按输入点，长按输入划"
    }

    fun setPress(progress: Float, crossed: Boolean) {
        val p = progress.coerceIn(0f, 1f)
        if (kotlin.math.abs(p - pressProgress) < 0.01f && crossed == this.crossed) return
        pressProgress = p
        this.crossed = crossed
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val id = event.getPointerId(event.actionIndex)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN,
            -> onDown?.invoke(id)

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP,
            -> {
                onUp?.invoke(id)
                performClick()
            }

            MotionEvent.ACTION_CANCEL -> onCancel?.invoke()
        }
        // 全量消费触摸：杜绝系统的长按 / 滑动返回手势把按键时长抢走
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        ensureShape(w, h)

        canvas.drawPath(shapePath, fillPaint)

        // 轨道与进度条都裁进底形状：圆屏上底形状的下缘是弧，进度条也得跟着走
        val meterH = h * METER_RATIO
        val meterY = h - INSET - meterH
        canvas.save()
        canvas.clipPath(shapePath)
        canvas.drawRoundRect(INSET, meterY, w - INSET, h - INSET, meterH / 2f, meterH / 2f, trackPaint)
        if (pressProgress > 0f) {
            barPaint.color = if (crossed) accentBright else accent
            val barW = (w - INSET * 2f) * pressProgress
            if (barW > 1f) {
                canvas.drawRoundRect(
                    INSET, meterY, INSET + barW, h - INSET,
                    meterH / 2f, meterH / 2f, barPaint,
                )
            }
        }
        canvas.restore()

        borderPaint.color = if (crossed) accentBright else dim
        canvas.drawPath(shapePath, borderPaint)

        // 文字保持不裁剪：它是居中的，本来就在圆内，裁了反而边缘发毛
        textPaint.color = if (crossed) accentBright else dim
        val cx = w / 2f
        val cy = meterY / 2f
        val lineHeight = textPaint.textSize * 1.25f
        canvas.drawText("短按 ·", cx, cy - lineHeight * 0.15f, textPaint)
        canvas.drawText("长按 −", cx, cy + lineHeight * 0.85f, textPaint)
    }

    private fun ensureShape(w: Float, h: Float) {
        val location = IntArray(2)
        getLocationOnScreen(location)
        val key = "$w,$h,${location[0]},${location[1]}"
        if (key == shapeKey) return
        shapeKey = key
        buildShape(w, h, location)
    }

    private fun buildShape(w: Float, h: Float, location: IntArray) {
        val radius = h * CORNER_RATIO
        shapePath.reset()
        shapePath.addRoundRect(INSET, INSET, w - INSET, h - INSET, radius, radius, Path.Direction.CW)

        if (!geometry.isRound) return

        // 屏幕圆换算到本视图的局部坐标系
        val cx = geometry.centerXPx - location[0]
        val cy = geometry.centerYPx - location[1]
        val r = geometry.radiusPx - geometry.px(ROUND_MARGIN_DP)
        if (r <= 0f) return

        val circle = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
        shapePath.op(circle, Path.Op.INTERSECT)
    }

    private fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha shl 24)

    private companion object {
        const val COLOR_PAD_FILL = 0xFF0E0E0E.toInt()
        const val INSET = 1f
        const val CORNER_RATIO = 0.16f
        const val METER_RATIO = 0.10f

        /** 裁圆时往里收一点，免得形状正好压在表圈边缘、看着像被切了一刀 */
        const val ROUND_MARGIN_DP = 2f
    }
}
