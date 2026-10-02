package com.wearmos.ui

import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.wearmos.R
import com.wearmos.core.MorseElement
import com.wearmos.ui.view.MorseKeyView
import com.wearmos.ui.view.PulseStripView

/**
 * 敲击台：需求 1（极客 UI）与需求 3（摩斯→文字）的运行态，也是桌面入口。
 *
 * 屏幕上从下往上对应输入的因果链：
 *  1. 键控区 —— 短按是点、长按是划，底部进度条让「点正在变成划」可见
 *  2. 大字符 —— 你刚敲出的字（按压时改为实时显示「·／− 与已按时长」）
 *  3. 波形条 —— 当前正在拼装的符号序列，以及距离字符落地还剩多久
 *  4. 状态条 —— WPM / 单位时长 / 点划阈值（点击切换速度，长按清空）
 *
 * 交互取舍：键控区全量消费触摸，防止系统长按与滑动返回把手势和按键时长抢走。
 */
class KeyerActivity : KeyerScreen() {

    private lateinit var statusView: TextView
    private lateinit var charView: TextView
    private lateinit var textView: TextView

    private var lastChar: Char? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        refresh()
    }

    override fun onCharacter(char: Char, code: String) {
        lastChar = char
        haptics.onCharacter()
        Log.d(TAG, "char=$char code=$code text=[${machine.text}]")
    }

    override fun onWord() {
        haptics.onWord()
        Log.d(TAG, "word text=[${machine.text}]")
    }

    override fun onElement(element: MorseElement) {
        super.onElement(element)
        // 元素一到就把大字区从「字符」切到「实时按压」，不等下一帧
        refresh()
    }

    override fun onKeyerTick(now: Long) {
        val pressed = tapSource.isPressed
        val head = if (pressed) {
            (if (pressCrossed) "" + UiKit.DASH else "" + UiKit.DOT) + " " + pressElapsedMs.toInt() + "ms"
        } else {
            lastChar?.toString() ?: ""
        }
        setTextIfChanged(charView, head)
        setTextIfChanged(textView, machine.text)
        setTextIfChanged(statusView, statusText())
    }

    private fun statusText(): String {
        val mode = if (adaptiveEnabled) "AUTO" else "FIX"
        // 刻意压缩到 22 字符以内：186dp 宽的方屏上，10sp 等宽字体约能容纳 29 字符
        return "$wpm WPM  u${timing.charUnitMs.toInt()}  $DOT_SLASH${thresholdMs.toInt()}  $mode"
    }

    private fun clearAll() {
        machine.clear()
        lastChar = null
        refresh()
    }

    /**
     * 删除一步（字符级）。与侧键退格共用同一份 [KeyerStateMachine.backspace]，
     * 所以两条路的语义不会分叉：
     *  1. 正在拼装的符号序列非空 → 撤掉它（那是还没成字的材料，正是「敲错了」的对象）
     *  2. 否则删掉已出文本的最后一个字符
     *
     * 删完把大字区拉回「当前文本的末字符」，避免它继续显示刚被删掉的那个字。
     */
    private fun deleteLast() {
        machine.backspace()
        lastChar = machine.text.lastOrNull()
        haptics.pulse(UiKit.DELETE_PULSE_MS)
        refresh()
    }

    private fun buildUi() {
        val geometry = UiKit.geometry(this)
        val side = dp(UiKit.sidePadDp(geometry))
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.bg))
            setPadding(side, dp(UiKit.topPadDp(geometry)), side, dp(UiKit.bottomPadDp(geometry)))
        }

        root.addView(
            UiKit.navBar(this, Screen.KEYER),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(UiKit.NAV_HEIGHT_DP)),
        )

        statusView = UiKit.label(
            this,
            "",
            10f,
            R.color.dim,
            mono = true,
        ).apply {
            isClickable = true
            setOnClickListener { cycleWpm() }
            setOnLongClickListener { clearAll(); true }
            contentDescription = "速度与判定参数，点击切换速度，长按清空"
        }
        root.addView(statusView, lp(dp(16)))

        val pulseView = PulseStripView(this)
        root.addView(pulseView, lp(dp(22)))

        charView = UiKit.label(
            this,
            "",
            30f,
            R.color.accent_bright,
            mono = true,
            bold = true,
        )
        // 大字区按权重伸缩，但文本行必须有固定高度 ——
        // 否则 30sp 大字会把 12sp 的已解文本挤出可视区（实测踩过）
        root.addView(charView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        textView = UiKit.label(this, "", 12f, R.color.dim, mono = true).apply {
            ellipsize = TextUtils.TruncateAt.START
        }
        // 文本行右端挂一个看得见的删除键。
        // 屏幕上原本没有任何删除入口，只能靠「短按侧键」这种隐藏手势 —— 而那条路
        // 在本机从未验证过。手表上隐藏手势等于没有入口，所以补一个显式的。
        val textRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        textRow.addView(
            textView,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f),
        )
        textRow.addView(
            UiKit.deleteKey(this) { deleteLast() },
            LinearLayout.LayoutParams(
                dp(UiKit.DELETE_KEY_WIDTH_DP),
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        // 与下方键控区之间留出间距：直接相接时点键控区上沿会误触删除
        root.addView(
            textRow,
            lp(dp(18)).apply { bottomMargin = dp(UiKit.DELETE_SAFE_GAP_DP) },
        )

        val keyView = MorseKeyView(this)
        attachKeyer(keyView, pulseView)
        root.addView(
            keyView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.7f),
        )

        // 圆屏上键控区不缩窄，改成把形状裁到屏幕圆内（缩窄会小到点不准）
        UiKit.constrainRowsToCircle(root, clipOnly = listOf(keyView))
        setContentView(root)
    }

    private fun setTextIfChanged(tv: TextView, text: CharSequence) {
        if (tv.text.toString() != text.toString()) tv.text = text
    }

    private fun lp(heightPx: Int) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx)

    private fun dp(v: Int) = UiKit.dp(this, v)

    private companion object {
        const val TAG = "WearMos"
        const val DOT_SLASH = "·/−"
    }
}
