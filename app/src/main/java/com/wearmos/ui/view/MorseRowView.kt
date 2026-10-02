package com.wearmos.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import com.wearmos.R
import com.wearmos.ui.UiKit

/**
 * 码表列表的一行：字符 + 码 + 历史正确率 + 播放指示三角。
 *
 * 整行自绘而不是拼三个 TextView：滚动时列表会频繁复用这些行，
 * 每行少三个 View 意味着少三次 measure/layout，在 860MB 的手表上这个差别是实打实的。
 */
class MorseRowView(context: Context) : View(context) {

    private val charPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = sp(15f)
        textAlign = Paint.Align.LEFT
    }
    private val codePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = sp(13f)
        textAlign = Paint.Align.LEFT
    }
    private val statPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = sp(10f)
        textAlign = Paint.Align.RIGHT
    }
    private val playPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val triangle = Path()

    private val primary = context.getColor(R.color.text_primary)
    private val accent = context.getColor(R.color.accent)
    private val dim = context.getColor(R.color.dim)
    private val edge = context.getColor(R.color.key_edge)

    private var char: Char = ' '
    private var code: String = ""
    private var accuracy: Float? = null

    private var lastKey: String? = null

    init {
        playPaint.color = dim
        dividerPaint.color = edge
        dividerPaint.strokeWidth = 1f
    }

    fun bind(char: Char, code: String, accuracy: Float?) {
        // 列表复用时会反复 bind 同一行，先比对再决定要不要重绘
        val key = "$char|$code|$accuracy"
        if (key == lastKey) return
        lastKey = key
        this.char = char
        this.code = code
        this.accuracy = accuracy
        contentDescription = "$char，摩斯 ${UiKit.prettyCode(code)}" +
            (accuracy?.let { "，历史正确率 ${(it * 100).toInt()}%" } ?: "")
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cy = h / 2f
        val baseline = cy - (charPaint.descent() + charPaint.ascent()) / 2f
        val left = dp(10f)
        val playW = dp(16f)

        charPaint.color = primary
        canvas.drawText(char.toString(), left, baseline, charPaint)

        codePaint.color = accent
        canvas.drawText(UiKit.prettyCode(code), left + dp(20f), baseline, codePaint)

        val statText = accuracy?.let { "${(it * 100).toInt()}%" }
        if (statText != null) {
            statPaint.color = if (accuracy!! >= 0.9f) accent else dim
            canvas.drawText(statText, w - playW - dp(4f), baseline, statPaint)
        }

        // 右端的播放三角：整行可点，但没有这个记号用户不会知道点了会响
        val tipX = w - dp(6f)
        val midX = w - playW
        val r = dp(4f)
        triangle.reset()
        triangle.moveTo(midX, cy - r)
        triangle.lineTo(tipX, cy)
        triangle.lineTo(midX, cy + r)
        triangle.close()
        playPaint.color = dim
        canvas.drawPath(triangle, playPaint)

        canvas.drawLine(0f, h - 0.5f, w, h - 0.5f, dividerPaint)
    }

    private fun sp(value: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private fun dp(value: Float) = value * resources.displayMetrics.density
}
