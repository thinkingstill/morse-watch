package com.wearmos.core

/**
 * 键控状态机：把 [KeyEvent] 流解成 摩斯元素 → 字符 → 文本。
 *
 * 摩斯输入的本质是时间判定，不是字符匹配：
 *  - 按压时长 → 点 / 划
 *  - 静默间隔 → 同一字符内继续 / 字符结束 / 词结束
 *
 * 一段静默按时间先后分两级结算，各自只结一次（[charDone] / [spaceDone] 是这段静默的守卫）：
 *  1. 静默 ≥ 字符间隔 → 当前字符落地
 *  2. 静默 ≥ 词间隔   → 补一个词间隔（等价于 Gboard 的 word timeout）
 *
 * 静默既在下一次按下时结算，也由 [onTick] 轮询结算；后者保证用户一停手字符就出现，
 * 这是学习场景最关键的体感，等价于 Gboard 的 character timeout。
 */
class KeyerStateMachine(
    private val classifier: DotDashClassifier,
    private val gaps: GapPolicy,
    private val listener: Listener = Listener.NO_OP,
) {

    interface Listener {
        fun onElement(element: MorseElement) {}
        fun onCharacter(char: Char, code: String) {}
        fun onWord() {}

        companion object {
            val NO_OP: Listener = object : Listener {}
        }
    }

    private val pending = StringBuilder()
    private var downAtMs = -1L

    /** 当前静默段的起点（上次抬手时刻）；-1 表示没有正在计时的静默段。 */
    private var idleSinceMs = -1L
    private var charDone = false
    private var spaceDone = false

    /** 已解出的文本。 */
    var text: String = ""
        private set

    /** 当前正在拼装的符号序列，如 ".-"。 */
    val pendingCode: String get() = pending.toString()

    val isPressed: Boolean get() = downAtMs >= 0

    /** 最近一次按压的实际时长，供 UI 显示「你按了多久 / 阈值在哪」。 */
    var lastPressMs: Float = 0f
        private set

    fun onKey(event: KeyEvent) {
        if (event.isDown) {
            if (downAtMs >= 0) return // 已有未配对的 DOWN，视为抖动，忽略
            settleIdle(event.timeMs)
            downAtMs = event.timeMs
            idleSinceMs = -1L // 按压期间不累计静默
        } else {
            val start = downAtMs
            if (start < 0) return // 没有对应 DOWN，忽略
            val duration = (event.timeMs - start).toFloat().coerceAtLeast(0f)
            lastPressMs = duration
            val element = classifier.classify(duration)
            classifier.observe(duration, element)
            pending.append(element.symbol)
            listener.onElement(element)
            downAtMs = -1L
            beginIdle(event.timeMs)
        }
    }

    /** 空闲轮询：让字符在用户停手后立刻落地。建议 10–20 ms 一次。 */
    fun onTick(nowMs: Long) {
        if (downAtMs >= 0) return
        settleIdle(nowMs)
    }

    /** 侧键手动出字：立刻结算当前缓冲（对应 Gboard 的 "Character timeout = Never"）。 */
    fun flush(nowMs: Long) {
        commitCharacter()
        beginIdle(nowMs)
        charDone = true
    }

    /** 显式插入词间隔。 */
    fun space(nowMs: Long) {
        commitCharacter()
        appendSpace()
        beginIdle(nowMs)
        charDone = true
        spaceDone = true
    }

    /** 删掉当前缓冲；缓冲为空则删掉已出文本的最后一个字符。 */
    fun backspace() {
        if (pending.isNotEmpty()) {
            pending.setLength(0)
            return
        }
        if (text.isNotEmpty()) text = text.dropLast(1)
    }

    fun clear() {
        pending.setLength(0)
        text = ""
        downAtMs = -1L
        idleSinceMs = -1L
        charDone = false
        spaceDone = false
        lastPressMs = 0f
    }

    private fun beginIdle(atMs: Long) {
        idleSinceMs = atMs
        charDone = false
        spaceDone = false
    }

    private fun settleIdle(nowMs: Long) {
        if (idleSinceMs < 0) return
        val idle = (nowMs - idleSinceMs).toFloat()
        if (!charDone && pending.isNotEmpty() && idle >= gaps.interCharGapMs()) {
            commitCharacter()
            charDone = true
        }
        if (!spaceDone && charDone && idle >= gaps.wordGapMs()) {
            appendSpace()
            spaceDone = true
        }
    }

    private fun commitCharacter() {
        if (pending.isEmpty()) return
        val code = pending.toString()
        pending.setLength(0)
        val char = MorseTable.char(code) ?: UNKNOWN_CHAR
        text += char
        listener.onCharacter(char, code)
    }

    private fun appendSpace() {
        if (text.isEmpty() || text.last() == ' ') return
        text += ' '
        listener.onWord()
    }

    companion object {
        const val UNKNOWN_CHAR = '?'
    }
}
