package com.wearmos.ui

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.wearmos.R
import com.wearmos.core.MorseTable
import com.wearmos.core.MorseTiming
import com.wearmos.data.ProgressStore
import com.wearmos.data.SettingsStore
import com.wearmos.feedback.DemoPlayer
import com.wearmos.feedback.HapticEngine
import com.wearmos.ui.view.MorseRowView
import com.wearmos.ui.view.SegmentedBar

/**
 * 码表：需求 2 里最基础的那一半 —— 先把「谁对应什么码」摆在眼前。
 *
 * 与学习屏的分工：这里不考你，只给你查、给你听。
 * 点任意一行会用**当前 WPM 的真实节奏**把那个字符震一遍，
 * 所以码表同时也是「定义节奏基准」的地方。
 *
 * 字母按字母表排而非 Koch 序：这是查阅用的表，找得到比学得快更重要。
 */
class ReferenceActivity : Activity() {

    private lateinit var progress: ProgressStore
    private lateinit var haptics: HapticEngine
    private lateinit var demo: DemoPlayer
    private lateinit var timing: MorseTiming

    private lateinit var adapter: RowAdapter
    private lateinit var hintView: TextView

    private var groupIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settings = SettingsStore(this)
        progress = ProgressStore(this)
        haptics = HapticEngine(this) { settings.hapticsEnabled }
        timing = MorseTiming(settings.wpm, settings.wpm)
        demo = DemoPlayer(haptics) { resetHint() }

        buildUi()
        adapter.items = currentGroup()
        adapter.notifyDataSetChanged()
    }

    override fun onPause() {
        super.onPause()
        demo.stop()
        haptics.cancel()
    }

    // ---------- 数据 ----------

    private fun currentGroup(): List<Char> = when (groupIndex) {
        GROUP_DIGITS -> ('0'..'9').toList()
        GROUP_SYMBOLS -> SYMBOLS
        else -> ('A'..'Z').toList()
    }

    // ---------- 交互 ----------

    private fun play(char: Char) {
        val code = MorseTable.code(char) ?: return
        demo.play(char.toString(), timing)
        hintView.text = "$char  ${UiKit.prettyCode(code)}"
        hintView.setTextColor(getColor(R.color.accent))
    }

    private fun resetHint() {
        hintView.text = HINT
        hintView.setTextColor(getColor(R.color.dim))
    }

    // ---------- 界面 ----------

    private fun buildUi() {
        val geometry = UiKit.geometry(this)
        val side = dp(UiKit.sidePadDp(geometry))
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.bg))
            setPadding(side, dp(UiKit.topPadDp(geometry)), side, dp(UiKit.bottomPadDp(geometry)))
        }

        root.addView(
            UiKit.navBar(this, Screen.TABLE),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(UiKit.NAV_HEIGHT_DP)),
        )

        val segments = SegmentedBar(this).apply {
            setItems(listOf("字母", "数字", "符号"), groupIndex)
            onPick = { index ->
                groupIndex = index
                adapter.items = currentGroup()
                adapter.notifyDataSetChanged()
            }
        }
        root.addView(
            segments,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(20)).apply {
                topMargin = dp(3)
            },
        )

        adapter = RowAdapter()
        val list = ListView(this).apply {
            divider = null
            dividerHeight = 0
            setBackgroundColor(getColor(R.color.bg))
            isVerticalScrollBarEnabled = false
            adapter = this@ReferenceActivity.adapter
            setOnItemClickListener { _, _, position, _ ->
                play(this@ReferenceActivity.adapter.items[position])
            }
        }
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        hintView = UiKit.label(this, HINT, 10f, R.color.dim)
        root.addView(hintView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(14)))

        // 列表行右端有播放三角、左端是字符，两端被表圈切掉会真的丢信息，所以整行收窄
        UiKit.constrainRowsToCircle(root)
        setContentView(root)
    }

    /** 每行自绘，滚动时只做 bind 不重建 View。 */
    private inner class RowAdapter : BaseAdapter() {

        var items: List<Char> = emptyList()

        override fun getCount(): Int = items.size

        override fun getItem(position: Int): Any = items[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView as? MorseRowView ?: MorseRowView(this@ReferenceActivity)
            row.layoutParams = AbsListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(ROW_HEIGHT_DP),
            )
            val char = items[position]
            row.bind(char, MorseTable.code(char).orEmpty(), progress.accuracy(char))
            return row
        }
    }

    private fun dp(value: Int) = UiKit.dp(this, value)

    private companion object {
        const val GROUP_DIGITS = 1
        const val GROUP_SYMBOLS = 2
        const val ROW_HEIGHT_DP = 32
        const val HINT = "点一行，用当前速度震一遍"

        /** 标点只收常用的一部分：全收会拉出一条在手表上翻不到底的列表。 */
        val SYMBOLS = listOf(
            '.', ',', '?', '!', '\'', '/', '=', '+',
            '-', '&', ':', ';', '(', ')', '@', '$',
        )
    }
}
