package com.wearmos.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import com.wearmos.R

/**
 * 实时点划波形条。
 *
 * 上方把正在拼装的符号序列画成 ·− 图形（最后一个元素高亮），
 * 下方一条细进度条表示「距离这个字符落地还有多久」——
 * 这是初学者建立时间感最直接的可视化：看得见间隔在消耗，就知道该什么时候停手。
 */
class PulseStripView(context: Context) : View(context) {

    private val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val meterPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val accent = context.getColor(R.color.accent)
    private val accentBright = context.getColor(R.color.accent_bright)

    private var code: String = ""
    private var idleProgress: Float = 0f
    private var pressed = false

    init {
        shapePaint.color = accent
        meterPaint.color = accent
        baselinePaint.color = withAlpha(accent, 40)
    }

    fun submit(code: String, pressed: Boolean, idleProgress: Float) {
        val p = idleProgress.coerceIn(0f, 1f)
        if (code == this.code && pressed == this.pressed && kotlin.math.abs(p - this.idleProgress) < 0.02f) {
            return
        }
        this.code = code
        this.pressed = pressed
        this.idleProgress = p
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val meterH = h * 0.11f
        val shapeArea = h - meterH * 2.2f
        val cy = shapeArea / 2f + meterH * 0.5f
        val half = shapeArea / 2f

        canvas.drawRect(0f, cy - 0.5f, w, cy + 0.5f, baselinePaint)

        if (code.isNotEmpty()) {
            val totalUnits = code.sumOf { if (it == '-') 3 else 1 } + (code.length - 1)
            val unit = w / totalUnits
            val radius = half * 0.2f
            var x = 0f
            code.forEachIndexed { index, ch ->
                val elementW = unit * (if (ch == '-') 3 else 1)
                shapePaint.color = if (index == code.lastIndex) accentBright else accent
                canvas.drawRoundRect(x, cy - half, x + elementW, cy + half, radius, radius, shapePaint)
                x += elementW + unit
            }
        }

        if (idleProgress > 0f) {
            val y0 = h - meterH
            meterPaint.color = if (pressed) accentBright else accent
            canvas.drawRoundRect(0f, y0, w * idleProgress, h, meterH / 2f, meterH / 2f, meterPaint)
        }
    }

    private fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha shl 24)
}
