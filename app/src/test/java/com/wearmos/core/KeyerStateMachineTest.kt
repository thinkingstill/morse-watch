package com.wearmos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 时间判定的边界测试。
 * 20 WPM 下：u = 60ms —— 点 60、划 180、字符间间隔 180、词间间隔 420。
 */
class KeyerStateMachineTest {

    private val timing = MorseTiming(charWpm = 20, effectiveWpm = 20)

    private fun machine(listener: KeyerStateMachine.Listener = KeyerStateMachine.Listener.NO_OP) =
        KeyerStateMachine(
            classifier = AdaptiveThreshold(timing.dotMs, timing.dashMs),
            gaps = ParisGapPolicy { timing },
            listener = listener,
        )

    private fun KeyerStateMachine.press(downMs: Long, durMs: Long, source: KeySource = KeySource.TOUCH) {
        onKey(KeyEvent(source, true, downMs))
        onKey(KeyEvent(source, false, downMs + durMs))
    }

    @Test
    fun parisTimingNumbers() {
        assertEquals(60f, timing.dotMs, 0.01f)
        assertEquals(180f, timing.dashMs, 0.01f)
        assertEquals(60f, timing.intraCharGapMs, 0.01f)
        assertEquals(180f, timing.interCharGapMs, 0.01f)
        assertEquals(420f, timing.wordGapMs, 0.01f)
        assertFalse(timing.isFarnsworth)
    }

    @Test
    fun farnsworthKeepsCharSpeedAndStretchesGaps() {
        val slow = MorseTiming.farnsworth(charWpm = 20, effectiveWpm = 8)
        assertEquals(60f, slow.dotMs, 0.01f)           // 字符速度不变
        assertEquals(450f, slow.interCharGapMs, 0.01f) // 1200/8*3
        assertEquals(1050f, slow.wordGapMs, 0.01f)     // 1200/8*7
        assertTrue(slow.isFarnsworth)
    }

    @Test
    fun farnsworthEffectiveNeverExceedsChar() {
        val t = MorseTiming.farnsworth(charWpm = 10, effectiveWpm = 25)
        assertEquals(10, t.charWpm)
        assertEquals(10, t.effectiveWpm)
    }

    @Test
    fun pressDurationDecidesDotOrDash() {
        val m = machine()
        m.press(0, 60)     // 60 < 120 → 点
        m.press(120, 200)  // 200 ≥ 120 → 划
        assertEquals(".-", m.pendingCode)
    }

    @Test
    fun charIsCommittedOnceInterCharGapElapses() {
        val m = machine()
        m.press(0, 60)
        m.press(120, 60)
        m.press(240, 60)
        assertEquals("...", m.pendingCode)
        assertEquals("", m.text)

        m.onTick(480) // 距最后一次抬手 180ms，正好达到字符间隔
        assertEquals("S", m.text)
        assertEquals("", m.pendingCode)
    }

    @Test
    fun shortGapKeepsBuildingSameChar() {
        val m = machine()
        m.press(0, 60)
        m.press(120, 60) // 间隔 60ms < 180ms，仍在同一字符内
        assertEquals("..", m.pendingCode)
        assertEquals("", m.text)
    }

    @Test
    fun wordSpaceAppearsBetweenCharactersAfterWordGap() {
        val m = machine()
        m.press(0, 60)
        m.onTick(250) // 字符间隔已过 → 出字
        assertEquals("E", m.text)

        m.press(500, 60)      // 距上次抬手 440ms ≥ 420ms，词间隔在下一次按下时补齐
        m.onTick(760)         // 距本次抬手 200ms → 出字，落在同一词内
        assertEquals("E E", m.text)
    }

    @Test
    fun longIdleEmitsExactlyOneWordSpace() {
        val m = machine()
        m.press(0, 60)
        m.onTick(250)
        m.onTick(5000) // 越过词间隔，补一个空格
        assertEquals("E ", m.text)
        m.onTick(9000) // 继续空闲不应再补第二个
        m.onTick(20000)
        assertEquals("E ", m.text)
    }

    @Test
    fun invalidSequenceYieldsQuestionMark() {
        val m = machine()
        repeat(6) { i -> m.press(i * 120L, 60) } // "......" 不在码表里
        m.onTick(840)
        assertEquals("?", m.text)
    }

    @Test
    fun manualFlushCommitsImmediately() {
        val m = machine()
        m.press(0, 60)
        assertEquals("", m.text)
        m.flush(70)
        assertEquals("E", m.text)
    }

    @Test
    fun spaceInsertsWordGapExplicitly() {
        val m = machine()
        m.press(0, 60)
        m.space(70)
        assertEquals("E ", m.text)

        m.press(200, 60)
        m.flush(270)
        assertEquals("E E", m.text)
    }

    @Test
    fun backspaceClearsPendingFirstThenText() {
        val m = machine()
        m.press(0, 60)
        m.flush(70)
        assertEquals("E", m.text)

        m.press(100, 60) // 缓冲里又有一个点
        m.backspace()    // 先清缓冲
        assertEquals("", m.pendingCode)
        assertEquals("E", m.text)

        m.backspace()    // 再删文本
        assertEquals("", m.text)
    }

    @Test
    fun clearResetsEverything() {
        val m = machine()
        m.press(0, 60)
        m.flush(70)
        m.clear()
        assertEquals("", m.text)
        assertEquals("", m.pendingCode)
        assertFalse(m.isPressed)
    }

    @Test
    fun duplicateDownAndOrphanUpAreIgnored() {
        val m = machine()
        m.onKey(KeyEvent(KeySource.TOUCH, true, 0))
        m.onKey(KeyEvent(KeySource.TOUCH, true, 10))   // 未配对的第二个 DOWN，忽略
        m.onKey(KeyEvent(KeySource.TOUCH, false, 60))  // 时长按 0→60 算，即 60ms → 点
        assertEquals(".", m.pendingCode)

        val m2 = machine()
        m2.onKey(KeyEvent(KeySource.TOUCH, false, 100)) // 无 DOWN 的 UP
        assertEquals("", m2.pendingCode)
        assertEquals("", m2.text)
    }

    @Test
    fun knockSourceFeedsTheSamePipeline() {
        val m = machine()
        m.press(0, 60, KeySource.KNOCK)
        m.press(120, 200, KeySource.KNOCK)
        m.onTick(500)
        assertEquals("A", m.text)
    }

    @Test
    fun listenerReceivesElementCharAndWordCallbacks() {
        val elements = mutableListOf<MorseElement>()
        val chars = mutableListOf<Char>()
        var words = 0
        val m = machine(object : KeyerStateMachine.Listener {
            override fun onElement(element: MorseElement) { elements += element }
            override fun onCharacter(char: Char, code: String) { chars += char }
            override fun onWord() { words++ }
        })

        m.press(0, 60)
        m.press(120, 200) // ".-" = A
        m.onTick(500)
        m.press(900, 60)  // 距上次抬手 580ms，越过词间隔
        m.onTick(1160)

        assertEquals(listOf(MorseElement.DOT, MorseElement.DASH, MorseElement.DOT), elements)
        assertEquals(listOf('A', 'E'), chars)
        assertEquals(1, words)
    }

    @Test
    fun adaptiveThresholdTracksUserSpeed() {
        val t = AdaptiveThreshold(60f, 180f)
        assertEquals(120f, t.thresholdMs(), 0.01f)

        repeat(5) { t.observe(100f, MorseElement.DOT) }
        repeat(5) { t.observe(320f, MorseElement.DASH) }

        val threshold = t.thresholdMs()
        assertTrue("阈值应落在 100~320 之间，实际 $threshold", threshold in 150f..260f)
        assertEquals(MorseElement.DOT, t.classify(100f))
        assertEquals(MorseElement.DASH, t.classify(320f))
        assertEquals(105f, t.learnedUnitMs(), 0.01f)
    }

    @Test
    fun adaptiveThresholdIgnoresOutliers() {
        val t = AdaptiveThreshold(60f, 180f)
        t.observe(5f, MorseElement.DOT)    // 低于 minMs，忽略
        t.observe(900f, MorseElement.DASH) // 高于 maxMs，忽略
        assertEquals(120f, t.thresholdMs(), 0.01f)
    }

    @Test
    fun adaptiveGapPolicyFollowsLearnedUnit() {
        val t = AdaptiveThreshold(60f, 180f)
        val gaps = AdaptiveGapPolicy(t) { timing }
        assertEquals(180f, gaps.interCharGapMs(), 0.01f)
        assertEquals(420f, gaps.wordGapMs(), 0.01f)
    }
}
