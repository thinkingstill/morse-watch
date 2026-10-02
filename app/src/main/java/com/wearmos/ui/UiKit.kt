package com.wearmos.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.wearmos.R
import com.wearmos.core.DisplayGeometry
import com.wearmos.core.MorseCodec

/**
 * 极简 UI 工具。
 *
 * 屏幕只有 186×215dp，四张屏共用的东西（字号、字色、返回位、码的排印）
 * 必须只有一份定义，否则改一处忘一处，屏幕上就会出现两种字号混排。
 */
object UiKit {

    fun dp(context: Context, value: Int): Int =
        Math.round(value * context.resources.displayMetrics.density)

    fun dp(context: Context, value: Float): Int =
        Math.round(value * context.resources.displayMetrics.density)

    /** 统一入口的文本视图。默认：12sp、暗色、居中、单行省略。 */
    fun label(
        context: Context,
        text: CharSequence = "",
        sizeSp: Float = 12f,
        colorRes: Int = R.color.dim,
        mono: Boolean = false,
        bold: Boolean = false,
        gravity: Int = Gravity.CENTER,
    ): TextView = TextView(context).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(context.getColor(colorRes))
        typeface = when {
            mono && bold -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            mono -> Typeface.MONOSPACE
            else -> Typeface.DEFAULT
        }
        this.gravity = gravity
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
    }

    data class Header(val root: LinearLayout, val title: TextView, val action: TextView?)

    /**
     * 顶栏：返回位 + 标题 + 右侧动作。
     *
     * 返回位固定 26dp 宽 —— 手表上拇指可点区域本就不多，再小就点不中了；
     * 而系统的手势返回在第三方应用上并不总是可靠，每个子屏都要有明确的退路。
     */
    fun header(
        context: Context,
        title: CharSequence = "",
        rightLabel: CharSequence? = null,
        onBack: (() -> Unit)? = null,
        onAction: (() -> Unit)? = null,
    ): Header {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val back = label(context, "‹", 16f, R.color.text_primary)
        if (onBack != null) {
            back.isClickable = true
            back.contentDescription = "返回"
            back.setOnClickListener { onBack() }
            row.addView(back, LinearLayout.LayoutParams(dp(context, 26), ViewGroup.LayoutParams.MATCH_PARENT))
        }

        val titleView = label(context, title, 11f, R.color.dim)
        row.addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))

        val actionView = if (rightLabel != null) {
            label(context, rightLabel, 11f, R.color.accent).apply {
                if (onAction != null) {
                    isClickable = true
                    setOnClickListener { onAction() }
                }
            }.also { row.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)) }
        } else {
            null
        }

        return Header(row, titleView, actionView)
    }

    /** 单个字符的码：把 ASCII 的 .- 换成视觉上等重的 ·−，并逐元素留空格。 */
    fun prettyCode(code: String): String =
        code.map { if (it == '-') DASH else DOT }.joinToString(" ")

    /** 整段文本的码：字符之间空一格，词之间空两格。 */
    fun prettyMorse(text: String): String =
        MorseCodec.encode(text)
            .split(" / ")
            .joinToString("  ") { word -> word.split(" ").joinToString(" ") { prettyCode(it) } }

    /** 导航条每格的标签是两个字，10sp 下约占 20dp —— 186dp 宽够排四个。 */
    const val NAV_HEIGHT_DP = 16

    // ---------- 删除键 ----------

    /**
     * 退格符号 U+232B。
     *
     * 本机实测过字形，不是猜的：`/system/fonts/NotoSansSymbols-Regular-Subsetted.ttf`
     * 含该码位，且它在 `/system/etc/fonts.xml` 里是一个**无名 family**（这类 family 会
     * 自动进入 fallback 链），所以主字体 OPlusSans / Roboto 虽然都没有它，
     * 渲染时仍会 fallback 过去。
     */
    const val BACKSPACE_GLYPH = "⌫"

    /** 删除键宽度：够指腹点中，又不至于把同行的文本挤得太短。 */
    const val DELETE_KEY_WIDTH_DP = 22

    /**
     * 删除键与下方键控区之间必须留的间距。
     *
     * 两块直接相接时，点键控区上沿、手指略偏上就会误触删除 —— 手指落点精度远大于 1dp。
     * 宁可少一行高度，也不要让「敲错一个符号」变成「删掉一个字」。
     */
    const val DELETE_SAFE_GAP_DP = 10

    /** 删除的震动反馈：比「点」（15ms）长一点，和点划区分开。 */
    const val DELETE_PULSE_MS = 30L

    /**
     * 统一的删除键。外观与行为只有一份 —— 敲击台与学习屏各写一套的话，
     * 「按了没反应」和「按了删过头」这类分叉在表上极难发现。
     */
    fun deleteKey(context: Context, onClick: () -> Unit): TextView =
        label(context, BACKSPACE_GLYPH, 13f, R.color.dim).apply {
            isClickable = true
            setBackgroundColor(context.getColor(R.color.key_fill))
            contentDescription = "删除最后一个字符"
            setOnClickListener { onClick() }
        }

    // ---------- 方屏 / 圆屏的自适应 ----------

    /**
     * 圆屏的顶部留白。
     *
     * 这不是审美问题而是硬约束：圆屏顶边几乎窄到放不下任何横向内容
     * （y=3dp 处只有约 53dp 宽）。把内容整体压下来 26dp 之后，
     * 导航条所在的竖直带才有 285px 可用，四格才排得开。
     * 方屏没有这个约束，保持原来的 3dp。
     */
    const val ROUND_TOP_PAD_DP = 26
    const val ROUND_BOTTOM_PAD_DP = 18
    const val ROUND_SIDE_PAD_DP = 8
    const val SQUARE_TOP_PAD_DP = 3
    const val SQUARE_BOTTOM_PAD_DP = 6
    const val SQUARE_SIDE_PAD_DP = 6

    fun geometry(context: Context): DisplayGeometry {
        val metrics = context.resources.displayMetrics
        return DisplayGeometry(
            widthPx = metrics.widthPixels,
            heightPx = metrics.heightPixels,
            density = metrics.density,
            isRound = context.resources.configuration.isScreenRound,
        )
    }

    fun topPadDp(geometry: DisplayGeometry): Int =
        if (geometry.isRound) ROUND_TOP_PAD_DP else SQUARE_TOP_PAD_DP

    fun bottomPadDp(geometry: DisplayGeometry): Int =
        if (geometry.isRound) ROUND_BOTTOM_PAD_DP else SQUARE_BOTTOM_PAD_DP

    fun sidePadDp(geometry: DisplayGeometry): Int =
        if (geometry.isRound) ROUND_SIDE_PAD_DP else SQUARE_SIDE_PAD_DP

    /**
     * 把根布局里的每一行收进屏幕的圆形可见区。
     *
     * 做法是**等布局完成后再量一次**：读每个子视图在屏幕上的实际 y，用圆方程算出
     * 这条竖直带最窄处的宽度，再把该行改成居中的定宽。
     *
     * 为什么不在建布局时一次算准：竖排布局里带权重的行高度要等测量完才知道，
     * 先算必然算错。为什么不用 `post`：视图还没 attach 时 `post` 的 runnable 会在
     * attach 阶段就执行，那时布局尚未发生，量到的尺寸全是 0。
     *
     * 改宽度不会反过来改变高度（这几行要么固定高、要么靠权重分配高度），
     * 所以量一次就够。`tag` 作哨兵防止自触发循环，尺寸变化时哨兵失效会自动重算。
     *
     * 方屏直接返回，行为与从前完全一致。
     *
     * @param clipOnly 这些子视图**不缩窄**，而是自己把绘制内容裁到圆内
     *   （键控区那种大块触摸区，缩窄会小到没法用，裁形状反而更像刻意设计）。
     */
    fun constrainRowsToCircle(
        root: LinearLayout,
        clipOnly: List<View> = emptyList(),
        marginDp: Float = 2f,
    ) {
        val geometry = geometry(root.context)
        if (!geometry.isRound) return

        root.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            applyCircleWidths(view as LinearLayout, geometry, clipOnly, marginDp)
        }
    }

    private fun applyCircleWidths(
        root: LinearLayout,
        geometry: DisplayGeometry,
        clipOnly: List<View>,
        marginDp: Float,
    ) {
        val marker = "round:${root.width}x${root.height}"
        if (root.tag == marker) return
        root.tag = marker

        val contentWidth = root.width - root.paddingLeft - root.paddingRight
        val marginPx = geometry.px(marginDp)
        val location = IntArray(2)

        for (index in 0 until root.childCount) {
            val child = root.getChildAt(index) ?: continue
            if (child.visibility == View.GONE) continue
            if (clipOnly.any { it === child }) continue

            val params = child.layoutParams as? LinearLayout.LayoutParams ?: continue
            child.getLocationOnScreen(location)
            val top = location[1].toFloat()
            val bottom = top + child.height

            val limit = geometry.widthForBandPx(top, bottom) - 2f * marginPx
            val target = minOf(contentWidth.toFloat(), limit).toInt()
            if (target <= 0 || params.width == target) continue

            params.width = target
            params.gravity = Gravity.CENTER_HORIZONTAL
            child.layoutParams = params
        }
    }

    fun navBar(activity: Activity, current: Screen): LinearLayout {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        Screen.entries.forEachIndexed { index, screen ->
            val on = screen == current
            val cell = label(
                activity,
                screen.label,
                10f,
                if (on) R.color.bg else R.color.dim,
            ).apply {
                setBackgroundColor(activity.getColor(if (on) R.color.accent else R.color.key_fill))
                if (!on) {
                    isClickable = true
                    contentDescription = "切换到${screen.label}"
                    setOnClickListener { goTo(activity, screen) }
                }
            }
            row.addView(
                cell,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    if (index < Screen.entries.lastIndex) rightMargin = dp(activity, 3)
                },
            )
        }
        return row
    }

    /**
     * 切屏用 startActivity + finish 而不是回退栈：四个屏是并列关系不是层级关系，
     * 来回跳几次后按返回不该退出一长串历史。顺带关掉转场动画 —— 手表上动画只会拖沓。
     */
    @Suppress("DEPRECATION")
    private fun goTo(activity: Activity, screen: Screen) {
        activity.startActivity(Intent(activity, screen.activityClass))
        activity.overridePendingTransition(0, 0)
        activity.finish()
    }

    const val DOT = '·'
    const val DASH = '−'
}

/**
 * 四个并列的功能屏。
 *
 * 用常驻导航条而不是手势唤出：186dp 的屏上，隐藏入口等于没有入口 ——
 * 用户第一次打开不可能知道要往哪滑。导航条固定占 16dp，一眼看得到全部功能。
 */
enum class Screen(val label: String, val activityClass: Class<out Activity>) {
    KEYER("敲击", KeyerActivity::class.java),
    LEARN("学习", LearnActivity::class.java),
    TABLE("码表", ReferenceActivity::class.java),
    CONVERT("转换", ConvertActivity::class.java),
}
