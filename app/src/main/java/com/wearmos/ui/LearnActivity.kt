package com.wearmos.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.wearmos.R
import com.wearmos.core.MorseElement
import com.wearmos.data.ProgressStore
import com.wearmos.feedback.DemoPlayer
import com.wearmos.learn.LessonEngine
import com.wearmos.ui.view.MorseKeyView
import com.wearmos.ui.view.PulseStripView

/**
 * 引导学习屏：需求 2 的主体。
 *
 * 闭环是「出题 → 你敲 → 立刻判 → 进下一题」，判定是逐字实时的：
 * 敲错第一个字符马上就会看到红字，不用等敲完一整串才被否定。
 *
 * 三处刻意的教学设计：
 *  - 「听抄」关把目标字符藏起来（显示为 ?），改成先播一遍节奏。撤掉视觉拐杖
 *    才算真的把码记住了；长按目标区可以偷看一眼，靠自觉。
 *  - 答错时不立刻翻页，多停 0.8 秒让你看清正确答案，否则错得毫无收获。
 *  - 只有「单字符」关过关会解锁新字符；其余三关是练习，不动进度 ——
 *    四条线同时推动进度条会让「我学到哪了」变得说不清。
 */
class LearnActivity : KeyerScreen() {

    private lateinit var progress: ProgressStore
    private lateinit var engine: LessonEngine
    private lateinit var demo: DemoPlayer

    private lateinit var statusView: TextView
    private lateinit var targetView: TextView
    private lateinit var codeView: TextView
    private lateinit var feedbackView: TextView

    private val main = Handler(Looper.getMainLooper())

    /** 上一次提交给引擎的解码文本，用来避免每帧重复判定。 */
    private var lastTyped = ""

    /** 是否正在等待「答完一题的停顿」结束。防止重复排程。 */
    private var advancing = false

    /** 听抄关的临时偷看开关，每题重置。 */
    private var peeked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        progress = ProgressStore(this)
        engine = LessonEngine().apply {
            setUnlockedCount(progress.unlockedCount)
            setLevel(progress.lastLevel)
        }
        demo = DemoPlayer(haptics) { refresh() }

        buildUi()
        startRound()
    }

    override fun onKeyerPause() {
        demo.stop()
        main.removeCallbacksAndMessages(null)
        advancing = false
    }

    /** 本轮结束后按键不再给震动反馈，免得敲了半天以为还在做题。 */
    override fun onElement(element: MorseElement) {
        if (!engine.finished) super.onElement(element)
    }

    override fun onKeyerTick(now: Long) {
        val text = machine.text
        if (text != lastTyped) {
            lastTyped = text
            when (engine.onTyped(text)) {
                LessonEngine.Verdict.CORRECT,
                LessonEngine.Verdict.WRONG,
                -> handleAnswered()
                else -> Unit
            }
        }
        render()
    }

    // ---------- 流程 ----------

    private fun startRound() {
        main.removeCallbacksAndMessages(null)
        advancing = false
        demo.stop()
        engine.start()
        resetInput()
        if (engine.level.playsDemoFirst) playDemo()
        refresh()
    }

    private fun handleAnswered() {
        if (advancing) return
        advancing = true

        val correct = engine.verdict == LessonEngine.Verdict.CORRECT
        progress.recordChars(engine.prompt, correct)
        if (correct) haptics.onCharacter() else haptics.pulse(WRONG_BUZZ_MS)

        main.postDelayed({ advance() }, if (correct) PAUSE_OK_MS else PAUSE_BAD_MS)
    }

    private fun advance() {
        advancing = false
        if (engine.finished) {
            finishRound()
            return
        }
        engine.next()
        resetInput()
        if (engine.level.playsDemoFirst) playDemo()
        refresh()
    }

    private fun finishRound() {
        demo.stop()
        val unlocked = engine.settle()
        progress.recordRound(engine.level, engine.correct)
        if (unlocked) {
            progress.unlockedCount = engine.unlockedCount
            // 用一个和「答对」明显不同的手感宣告升级
            haptics.onWord()
        }
        refresh()
    }

    private fun resetInput() {
        machine.clear()
        lastTyped = ""
        peeked = false
    }

    private fun playDemo() {
        if (engine.prompt.isEmpty() || engine.finished) return
        demo.play(engine.prompt, timing)
        refresh()
    }

    private fun cycleLevel() {
        engine.setLevel(engine.level.next)
        progress.lastLevel = engine.level
        startRound()
    }

    // ---------- 渲染 ----------

    private fun render() {
        if (engine.finished) renderSummary() else renderQuestion()
        setTextIfChanged(statusView, statusText())
    }

    private fun renderQuestion() {
        val e = engine
        val reveal = !e.level.hidesCode || peeked || e.verdict != LessonEngine.Verdict.IDLE

        val target = when {
            reveal -> e.prompt
            demo.isPlaying -> "▶"
            else -> "?"
        }
        setTextIfChanged(targetView, target)
        targetView.setTextSize(TypedValue.COMPLEX_UNIT_SP, targetSizeSp(reveal, e.prompt))
        targetView.setTextColor(
            getColor(
                when (e.verdict) {
                    LessonEngine.Verdict.CORRECT -> R.color.accent_bright
                    LessonEngine.Verdict.WRONG -> R.color.bad
                    else -> R.color.text_primary
                },
            ),
        )

        setTextIfChanged(codeView, codeHint(reveal))
        codeView.setTextColor(
            getColor(if (e.verdict == LessonEngine.Verdict.WRONG) R.color.bad else R.color.accent),
        )

        setTextIfChanged(feedbackView, feedbackText())
        feedbackView.setTextColor(
            getColor(
                when (e.verdict) {
                    LessonEngine.Verdict.CORRECT -> R.color.accent_bright
                    LessonEngine.Verdict.WRONG -> R.color.bad
                    else -> R.color.dim
                },
            ),
        )
    }

    private fun renderSummary() {
        val e = engine
        setTextIfChanged(targetView, "${e.correct}/${e.total}")
        targetView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
        targetView.setTextColor(getColor(if (e.passed) R.color.accent_bright else R.color.bad))

        setTextIfChanged(
            codeView,
            if (e.passed) "通过 · 点这里再来一轮" else "差 ${e.passLine - e.correct} 题 · 点这里重试",
        )
        codeView.setTextColor(getColor(R.color.dim))

        val best = progress.best(e.level)
        setTextIfChanged(
            feedbackView,
            if (e.passed) "已解锁 ${e.unlockedChars.size} 个字符" else "本关最好 $best/${e.total}",
        )
        feedbackView.setTextColor(getColor(R.color.dim))
    }

    private fun codeHint(reveal: Boolean): String = when {
        reveal -> UiKit.prettyMorse(engine.prompt)
        demo.isPlaying -> "播放中…"
        else -> "听节奏 · ${engine.prompt.length} 个字符（长按偷看）"
    }

    private fun feedbackText(): String {
        val pending = machine.pendingCode
        val typed = if (pending.isNotEmpty()) UiKit.prettyCode(pending) else engine.typed
        return when (engine.verdict) {
            LessonEngine.Verdict.CORRECT -> "对"
            LessonEngine.Verdict.WRONG -> "错 · 你敲 $typed"
            LessonEngine.Verdict.INCOMPLETE -> if (typed.isEmpty()) START_HINT else "你敲 $typed"
            LessonEngine.Verdict.IDLE -> START_HINT
        }
    }

    private fun statusText(): String {
        val e = engine
        // 中文按 10dp、其余按 6dp 估算，总宽压在 174dp 内
        return "${e.level.label}  ${e.answered}/${e.total}  对${e.correct}  解锁${e.unlockedChars.size} ⇄"
    }

    /** 目标区字号随题目长度收缩：186dp 的屏上，长词必须主动让位给键控区。 */
    private fun targetSizeSp(reveal: Boolean, prompt: String): Float = when {
        !reveal -> 30f
        prompt.length <= 2 -> 30f
        prompt.length <= 4 -> 23f
        prompt.length <= 7 -> 17f
        else -> 14f
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
            UiKit.navBar(this, Screen.LEARN),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(UiKit.NAV_HEIGHT_DP)),
        )

        statusView = UiKit.label(this, "", 10f, R.color.dim, mono = true).apply {
            isClickable = true
            setOnClickListener { cycleLevel() }
            setOnLongClickListener { startRound(); true }
            contentDescription = "点击切换关卡，长按重开一轮"
        }
        root.addView(statusView, lp(dp(15)))

        val pulseView = PulseStripView(this)
        root.addView(pulseView, lp(dp(18)))

        targetView = UiKit.label(this, "", 30f, R.color.text_primary, mono = true, bold = true)
        root.addView(
            targetView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )

        codeView = UiKit.label(this, "", 11f, R.color.accent, mono = true)
        root.addView(codeView, lp(dp(16)))

        feedbackView = UiKit.label(this, START_HINT, 11f, R.color.dim, mono = true)
        // 反馈行是这屏里唯一「显示你正在敲什么」的地方（正在拼的码，或已判文本），
        // 所以删除键挂它右端 —— 与敲击台挂在文本行右端是同一个语义。
        val feedbackRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        feedbackRow.addView(
            feedbackView,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f),
        )
        feedbackRow.addView(
            UiKit.deleteKey(this) { deleteLast() },
            LinearLayout.LayoutParams(
                dp(UiKit.DELETE_KEY_WIDTH_DP),
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        // 与下方键控区之间留间距：直接相接时点键控区上沿会误触删除
        root.addView(
            feedbackRow,
            lp(dp(16)).apply { bottomMargin = dp(UiKit.DELETE_SAFE_GAP_DP) },
        )

        val keyView = MorseKeyView(this)
        attachKeyer(keyView, pulseView)
        root.addView(
            keyView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.45f),
        )

        // 目标区的三种点法：结算后重开一轮 / 重播节奏 / 听抄关偷看
        targetView.setOnClickListener {
            if (engine.finished) startRound() else playDemo()
        }
        targetView.setOnLongClickListener {
            if (engine.finished || !engine.level.hidesCode) {
                false
            } else {
                peeked = !peeked
                refresh()
                true
            }
        }

        // 圆屏上键控区不缩窄，改成把形状裁到屏幕圆内（缩窄会小到点不准）
        UiKit.constrainRowsToCircle(root, clipOnly = listOf(keyView))
        setContentView(root)
    }

    /**
     * 删除一步（字符级）。与敲击台共用同一份 [KeyerStateMachine.backspace] 语义：
     * 先撤掉正在拼的符号序列，缓冲空了才删已判文本的末字符。
     *
     * 在这屏里它主要在「判定还没落地之前」有用 —— 敲到一半发现多敲了一个符号，
     * 自己退回去重敲。引擎一旦给出判定就是粘住的，那之后再删也改不了结果。
     */
    private fun deleteLast() {
        machine.backspace()
        haptics.pulse(UiKit.DELETE_PULSE_MS)
        refresh()
    }

    private fun setTextIfChanged(view: TextView, text: CharSequence) {
        if (view.text.toString() != text.toString()) view.text = text
    }

    private fun lp(heightPx: Int) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx)

    private fun dp(value: Int) = UiKit.dp(this, value)

    private companion object {
        const val PAUSE_OK_MS = 700L
        const val PAUSE_BAD_MS = 1500L
        const val WRONG_BUZZ_MS = 260L
        const val START_HINT = "开始敲吧"
    }
}
