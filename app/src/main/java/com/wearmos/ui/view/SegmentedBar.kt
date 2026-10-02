package com.wearmos.ui.view

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.wearmos.R

/**
 * 分段选择条（字母 / 数字 / 符号，文字 / 摩斯）。
 *
 * 极简风：选中段用实心强调色，未选中段用键面色，不画圆角也不画边框。
 * 之所以不做成下拉或轮盘：手表上「一眼看到有几个选项、当前在哪个」
 * 比「点开再选」重要得多。
 */
class SegmentedBar(context: Context) : LinearLayout(context) {

    var onPick: ((Int) -> Unit)? = null

    private val cells = mutableListOf<TextView>()

    var selectedIndex: Int = 0
        private set

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun setItems(items: List<String>, selected: Int = 0) {
        removeAllViews()
        cells.clear()
        items.forEachIndexed { index, item ->
            val cell = TextView(context).apply {
                text = item
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                gravity = Gravity.CENTER
                isSingleLine = true
                isClickable = true
                contentDescription = item
                setOnClickListener {
                    select(index)
                    onPick?.invoke(index)
                }
            }
            addView(
                cell,
                LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    if (index < items.lastIndex) rightMargin = dp(3)
                },
            )
            cells += cell
        }
        select(selected)
    }

    fun select(index: Int) {
        if (cells.isEmpty()) return
        selectedIndex = index.coerceIn(0, cells.lastIndex)
        cells.forEachIndexed { i, cell ->
            val on = i == selectedIndex
            cell.setBackgroundColor(context.getColor(if (on) R.color.accent else R.color.key_fill))
            cell.setTextColor(context.getColor(if (on) R.color.bg else R.color.dim))
        }
    }

    private fun dp(value: Int) = Math.round(value * resources.displayMetrics.density)
}
