package com.wearmos.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import com.wearmos.R
import com.wearmos.core.DisplayGeometry

/**
 * 内置精简键盘：5 列 × 6 行 = 30 格，正好装下 26 个字母 + 4 个功能键。
 *
 * 为什么不用系统输入法：这台表装的是搜狗手表输入法，弹出后是整个系统 UI 盖在我们的
 * 界面上，转换结果会被挡住；而且每次拉起都要等输入法窗口就绪，在 860MB 的手表上很钝。
 * 自绘一块键盘既没有这些代价，也让「文字 → 摩斯」的实时预览始终在同一屏上可见。
 *
 * 尺寸交由父布局决定（每格宽约 34dp、高约 20dp，320dpi 下 68×40px）。
 * 这个尺寸在手指面前偏小，但 186dp 宽的屏要排 30 格，这已经是能给出的最大值。
 *
 * 抬手才触发：`清空` 与 `删` 挨得很近，按下即触发的话误触代价太高。
 */
class KeyboardView(context: Context) : View(context) {

    var onLetter: ((Char) -> Unit)? = null
    var onSpace: (() -> Unit)? = null
    var onBackspace: (() -> Unit)? = null
    var onMorse: (() -> Unit)? = null
    var onClear: (() -> Unit)? = null

    private sealed interface Key {
        data class Letter(val char: Char) : Key
        data object Space : Key
        data object Backspace : Key
        data object Morse : Key
        data object Clear : Key
    }

    private val keys: List<Key> = buildList {
        for (char in 'A'..'Z') add(Key.Letter(char))
        add(Key.Space)
        add(Key.Backspace)
        add(Key.Morse)
        add(Key.Clear)
    }

    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE
    }

    private val fillColor = context.getColor(R.color.key_fill)
    private val accentColor = context.getColor(R.color.accent)
    private val textColor = context.getColor(R.color.text_primary)
    private val dimColor = context.getColor(R.color.dim)
    private val pressedTextColor = 0xFF101010.toInt()

    private val cell = RectF()
    private val radius = dp(5f)

    private val geometry = DisplayGeometry(
        widthPx = resources.displayMetrics.widthPixels,
        heightPx = resources.displayMetrics.heightPixels,
        density = resources.displayMetrics.density,
        isRound = resources.configuration.isScreenRound,
    )

    /** 圆屏上收窄后的格阵区域；方屏上等于整个视图。 */
    private val gridBounds = RectF()
    private var gridKey: String? = null

    private var pressedIndex = -1
    private var downIndex = -1

    init {
        isClickable = true
        contentDescription = "字母键盘，5 列 6 行"
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downIndex = indexAt(event.x, event.y)
                pressedIndex = downIndex
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                val index = indexAt(event.x, event.y)
                if (index != pressedIndex) {
                    pressedIndex = index
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP -> {
                val index = indexAt(event.x, event.y)
                if (index >= 0 && index == downIndex) dispatch(keys[index])
                pressedIndex = -1
                downIndex = -1
                invalidate()
            }

            MotionEvent.ACTION_CANCEL -> {
                pressedIndex = -1
                downIndex = -1
                invalidate()
            }
        }
        return true
    }

    private fun dispatch(key: Key) {
        when (key) {
            is Key.Letter -> onLetter?.invoke(key.char)
            Key.Space -> onSpace?.invoke()
            Key.Backspace -> onBackspace?.invoke()
            Key.Morse -> onMorse?.invoke()
            Key.Clear -> onClear?.invoke()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        updateGridBounds()
        val cellW = gridBounds.width() / COLS
        val cellH = gridBounds.height() / ROWS
        val gap = dp(GAP_DP)

        keys.forEachIndexed { index, key ->
            val col = index % COLS
            val row = index / COLS
            cell.set(
                gridBounds.left + col * cellW + gap,
                gridBounds.top + row * cellH + gap,
                gridBounds.left + (col + 1) * cellW - gap,
                gridBounds.top + (row + 1) * cellH - gap,
            )

            val pressed = index == pressedIndex
            cellPaint.color = if (pressed) accentColor else fillColor
            canvas.drawRoundRect(cell, radius, radius, cellPaint)

            val isLetter = key is Key.Letter
            textPaint.color = when {
                pressed -> pressedTextColor
                isLetter -> textColor
                key == Key.Morse -> accentColor
                else -> dimColor
            }
            textPaint.textSize = (cellH * if (isLetter) 0.50f else 0.40f)
                .coerceIn(sp(9f), sp(15f))

            val baseline = cell.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(labelOf(key), cell.centerX(), baseline, textPaint)
        }
    }

    private fun labelOf(key: Key): String = when (key) {
        is Key.Letter -> key.char.toString()
        Key.Space -> "空"
        Key.Backspace -> "删"
        Key.Morse -> "摩"
        Key.Clear -> "清"
    }

    private fun indexAt(x: Float, y: Float): Int {
        updateGridBounds()
        if (!gridBounds.contains(x, y)) return -1
        val col = ((x - gridBounds.left) / (gridBounds.width() / COLS)).toInt().coerceIn(0, COLS - 1)
        val row = ((y - gridBounds.top) / (gridBounds.height() / ROWS)).toInt().coerceIn(0, ROWS - 1)
        return row * COLS + col
    }

    /**
     * 键盘格阵实际占据的矩形。
     *
     * 圆屏上必须把格阵整体收进圆内 —— 5×6 的四个角格一旦落到表圈外，
     * 那几颗键的字就看不清、也点不到了。判据是所有格子的四个外角都要在圆内，
     * 于是可用半宽取「格阵上下两边中离中线更远的那边」对应的弦长的一半。
     *
     * 方屏上这个矩形就是整个视图，行为与从前一致。
     */
    private fun updateGridBounds() {
        if (!geometry.isRound) {
            gridBounds.set(0f, 0f, width.toFloat(), height.toFloat())
            gridKey = null
            return
        }
        val location = IntArray(2)
        getLocationOnScreen(location)
        val key = "$width,$height,${location[0]},${location[1]}"
        if (key == gridKey) return
        gridKey = key

        val top = location[1].toFloat()
        val bottom = top + height
        val usable = geometry.widthForBandPx(top, bottom) - geometry.px(GRID_MARGIN_DP)
        val gridWidth = minOf(width.toFloat(), maxOf(usable, 0f))
        val left = (width - gridWidth) / 2f
        gridBounds.set(left, 0f, left + gridWidth, height.toFloat())
    }

    private fun sp(value: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private fun dp(value: Float) = value * resources.displayMetrics.density

    private companion object {
        const val COLS = 5
        const val ROWS = 6
        const val GAP_DP = 1.5f

        /** 格阵整体再往里收一点，避免边格压在表圈上 */
        const val GRID_MARGIN_DP = 2f
    }
}
