package com.wearmos.learn

import kotlin.random.Random

/**
 * 关卡引擎：出题 → 收键 → 判定 → 统计 → 过关。
 *
 * 刻意做成纯 Kotlin、不认识任何 Android 类型 —— 这样关卡推进的边界
 * （第几题算答完、答错是否计数、什么条件下解锁新字符）全都能在 JVM 单测里跑。
 * 上一版敲击台就是靠这套做法抓出了「词间隔插错位置」的 bug，这里沿用。
 *
 * 判定是**逐字实时**的，不是等用户敲完再一次性比对：
 * 已经敲出的内容只要还是目标的前缀就继续等，一旦偏离立刻判错。
 * 这样学习者敲错第一个字符时马上就知道，而不是敲完一整串才被否定。
 *
 * 判定前会把空白全部剥掉：状态机在停顿偏久时会自动补词间隔，
 * 学习初期这种「多停一下」不该被判成错误，否则挫败感会盖过教学效果。
 */
class LessonEngine(private val random: Random = Random.Default) {

    enum class Verdict {
        /** 还没开始敲。 */
        IDLE,

        /** 已敲内容是目标的前缀，继续敲。 */
        INCOMPLETE,

        /** 完全命中。 */
        CORRECT,

        /** 偏离目标，本题结束。 */
        WRONG,
    }

    var level: LessonLevel = LessonLevel.SINGLE
        private set

    var unlockedCount: Int = KochOrder.STARTING_UNLOCKED
        private set

    /** 当前题目。 */
    var prompt: String = ""
        private set

    /** 用户已敲出的内容（已剥除空白）。 */
    var typed: String = ""
        private set

    var verdict: Verdict = Verdict.IDLE
        private set

    /** 本轮已答题数（含答错）。 */
    var answered: Int = 0
        private set

    /** 本轮答对题数。 */
    var correct: Int = 0
        private set

    private var lastPrompt: String? = null

    /** 本轮是否已经结算过。防止调用方反复读结果时把解锁进度顶上去。 */
    private var settled = false

    val total: Int get() = QUESTIONS_PER_ROUND
    val passLine: Int get() = PASS_LINE

    /** 本轮题目已答完。 */
    val finished: Boolean get() = answered >= total

    /** 本轮已答完且达到过关线。 */
    val passed: Boolean get() = finished && correct >= passLine

    /** 已解锁字符列表，UI 用来显示进度。 */
    val unlockedChars: List<Char> get() = KochOrder.unlocked(unlockedCount)

    fun setLevel(value: LessonLevel) {
        level = value
    }

    fun setUnlockedCount(value: Int) {
        unlockedCount = value.coerceIn(1, KochOrder.maxUnlocked())
    }

    /** 开始新一轮：清空统计，发第一题。 */
    fun start() {
        answered = 0
        correct = 0
        settled = false
        clearAnswer()
        prompt = newPrompt()
    }

    /** 发下一题。本轮已答完时不做任何事（由调用方读 [finished] 去展示结算）。 */
    fun next() {
        if (finished) return
        clearAnswer()
        prompt = newPrompt()
    }

    /**
     * 提交当前已敲出的内容。同一题内重复调用是安全的：
     * 本题一旦判出 [Verdict.CORRECT] / [Verdict.WRONG]，后续调用原样返回该结果，
     * 不会重复计数。调用方可以放心地在每次界面刷新时都提交一遍。
     */
    fun onTyped(raw: String): Verdict {
        if (finished) return verdict
        if (verdict == Verdict.CORRECT || verdict == Verdict.WRONG) return verdict

        val got = normalize(raw)
        typed = got
        val want = normalize(prompt)

        verdict = when {
            got.isEmpty() -> Verdict.INCOMPLETE
            got == want -> Verdict.CORRECT
            want.startsWith(got) -> Verdict.INCOMPLETE
            else -> Verdict.WRONG
        }

        when (verdict) {
            Verdict.CORRECT -> {
                correct++
                answered++
            }
            Verdict.WRONG -> answered++
            else -> Unit
        }
        return verdict
    }

    /**
     * 本关结算。单字符关过关即解锁下一个 Koch 字符，返回是否真的解锁了。
     * 其余关卡只记成绩、不动解锁进度 —— 让「学到哪了」有唯一来源。
     *
     * 每轮只生效一次：重复调用返回 false，不会被界面刷新反复顶高解锁进度。
     */
    fun settle(): Boolean {
        if (settled) return false
        settled = true
        if (!finished || !passed) return false
        if (level != LessonLevel.SINGLE) return false
        if (unlockedCount >= KochOrder.maxUnlocked()) return false
        unlockedCount++
        return true
    }

    /** 把引擎恢复到初始态（换关卡 / 重置进度时用）。 */
    fun reset() {
        answered = 0
        correct = 0
        settled = false
        level = LessonLevel.SINGLE
        prompt = ""
        lastPrompt = null
        clearAnswer()
    }

    private fun clearAnswer() {
        typed = ""
        verdict = Verdict.IDLE
    }

    private fun newPrompt(): String {
        val unlocked = KochOrder.unlocked(unlockedCount)
        val candidate = when (level) {
            LessonLevel.SINGLE, LessonLevel.COPY -> pickOne(unlocked) { it.toString() }

            LessonLevel.GROUP -> pickOne(unlocked) { first ->
                pickOne(unlocked) { second -> "$first$second" }
            }

            LessonLevel.WORD -> DrillBank.random(unlocked.toSet(), random, lastPrompt)
                ?: pickOne(unlocked) { it.toString() }
        }
        lastPrompt = candidate
        return candidate
    }

    /** 从池子里挑一个尽量不等于上一题的表达式结果。池子只有一项时允许重复。 */
    private fun pickOne(pool: List<Char>, build: (Char) -> String): String {
        if (pool.size == 1) return build(pool[0])
        var candidate = build(pool.random(random))
        var guard = 0
        while (candidate == lastPrompt && guard++ < REPEAT_GUARD) {
            candidate = build(pool.random(random))
        }
        return candidate
    }

    private fun normalize(text: String) = text.filter { !it.isWhitespace() }.uppercase()

    companion object {
        /** 一轮 10 题。手表上单屏能装下的量，做完一轮大约一两分钟。 */
        const val QUESTIONS_PER_ROUND = 10

        /** 过关线 8/10。留两题容错，对应「刚学会、偶尔失手」的状态。 */
        const val PASS_LINE = 8

        private const val REPEAT_GUARD = 8
    }
}
