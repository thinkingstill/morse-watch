package com.wearmos.learn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 关卡推进的边界测试。
 *
 * 这一层刻意不含 Android 依赖，就是为了能把「第几题算答完」「答错算不算数」
 * 「什么时候解锁新字符」这些规则钉死在 JVM 里，而不是靠推 APK 到手表上点出来。
 */
class LessonEngineTest {

    private fun engine(seed: Int = 42) = LessonEngine(Random(seed))

    /** 连续作答：先答对 [correctCount] 题，再故意答错 [wrongCount] 题。 */
    private fun LessonEngine.play(correctCount: Int, wrongCount: Int) {
        repeat(correctCount) {
            onTyped(prompt)
            if (!finished) next()
        }
        repeat(wrongCount) {
            onTyped("QQQQQQ") // 任何单/双字符题目都不可能是它的前缀
            if (!finished) next()
        }
    }

    // ---------- Koch 解锁基础 ----------

    @Test
    fun unlockedSetStartsAtKochStartingPoint() {
        val e = engine()
        assertEquals(KochOrder.STARTING_UNLOCKED, e.unlockedCount)
        assertEquals(KochOrder.STARTING_UNLOCKED, e.unlockedChars.size)
        assertEquals(listOf('K', 'M', 'R', 'S', 'U', 'A', 'P', 'T'), e.unlockedChars)
    }

    @Test
    fun unlockedCountIsClamped() {
        val e = engine()
        e.setUnlockedCount(9999)
        assertEquals(KochOrder.maxUnlocked(), e.unlockedCount)
        e.setUnlockedCount(0)
        assertEquals(1, e.unlockedCount)
    }

    @Test
    fun kochSequenceHasNoDuplicatesAndCoversFullTable() {
        assertEquals(KochOrder.SEQUENCE.size, KochOrder.SEQUENCE.toSet().size)
        assertEquals(26 + 10, KochOrder.SEQUENCE.size)
        assertEquals(listOf('K', 'M', 'R', 'S'), KochOrder.SEQUENCE.take(4))
        assertEquals('D', KochOrder.SEQUENCE[25])   // 字母段收尾
        assertEquals('5', KochOrder.SEQUENCE[26])   // 数字段起点
    }

    // ---------- 出题约束 ----------

    @Test
    fun singleLevelPromptIsExactlyOneUnlockedChar() {
        val e = engine()
        e.setLevel(LessonLevel.SINGLE)
        e.start()
        repeat(40) {
            assertEquals("单字符关的题目必须只有一个字符：${e.prompt}", 1, e.prompt.length)
            assertTrue(e.prompt[0] in e.unlockedChars)
            e.onTyped(e.prompt)
            if (e.finished) e.start() else e.next()
        }
    }

    @Test
    fun groupLevelPromptIsTwoUnlockedChars() {
        val e = engine()
        e.setLevel(LessonLevel.GROUP)
        e.start()
        repeat(40) {
            assertEquals(2, e.prompt.length)
            assertTrue(e.prompt.all { it in e.unlockedChars })
            e.onTyped(e.prompt)
            if (e.finished) e.start() else e.next()
        }
    }

    @Test
    fun everyLevelNeverUsesLockedChars() {
        LessonLevel.entries.forEach { level ->
            val e = engine(seed = 7)
            e.setLevel(level)
            e.start()
            repeat(60) {
                val allowed = e.unlockedChars.toSet()
                assertTrue(
                    "$level 的题目 ${e.prompt} 用了未解锁字符",
                    e.prompt.isNotEmpty() && e.prompt.all { it in allowed },
                )
                e.onTyped(e.prompt)
                if (e.finished) e.start() else e.next()
            }
        }
    }

    @Test
    fun wordLevelFallsBackToSingleCharWhenNoWordFits() {
        // 只解锁 4 个字符（K M R S）时词库里一个字都凑不出来，必须优雅退化而不是出空题
        val e = engine()
        e.setUnlockedCount(4)
        e.setLevel(LessonLevel.WORD)
        e.start()
        assertEquals(1, e.prompt.length)
        assertTrue(e.prompt[0] in setOf('K', 'M', 'R', 'S'))
    }

    // ---------- 判定 ----------

    @Test
    fun exactAnswerIsCorrect() {
        val e = engine()
        e.setLevel(LessonLevel.SINGLE)
        e.start()
        assertEquals(LessonEngine.Verdict.IDLE, e.verdict)
        assertEquals(LessonEngine.Verdict.CORRECT, e.onTyped(e.prompt))
        assertEquals(1, e.correct)
        assertEquals(1, e.answered)
    }

    @Test
    fun prefixIsIncompleteUntilFullyMatched() {
        val e = engine()
        e.setLevel(LessonLevel.GROUP)
        e.start()
        val prompt = e.prompt

        assertEquals(LessonEngine.Verdict.INCOMPLETE, e.onTyped(prompt.substring(0, 1)))
        assertEquals(0, e.answered) // 未答完不计入统计
        assertEquals(LessonEngine.Verdict.CORRECT, e.onTyped(prompt))
        assertEquals(1, e.answered)
    }

    @Test
    fun deviationIsWrongImmediately() {
        val e = engine()
        e.setLevel(LessonLevel.GROUP)
        e.start()
        val prompt = e.prompt
        val alien = if ('K' in prompt) "M" else "K" // 造一个不是前缀的字符
        assertEquals(LessonEngine.Verdict.WRONG, e.onTyped(alien))
        assertEquals(0, e.correct)
        assertEquals(1, e.answered)
    }

    @Test
    fun verdictIsStickyAndCountedOnce() {
        val e = engine()
        e.start()
        val prompt = e.prompt

        assertEquals(LessonEngine.Verdict.WRONG, e.onTyped("QQQQQQ"))
        // 界面每帧都会重新提交一次，不能因此重复计数、更不能翻案
        assertEquals(LessonEngine.Verdict.WRONG, e.onTyped("QQQQQQ"))
        assertEquals(LessonEngine.Verdict.WRONG, e.onTyped(prompt))
        assertEquals(1, e.answered)
        assertEquals(0, e.correct)
    }

    @Test
    fun whitespaceIsIgnoredWhenJudging() {
        val e = engine()
        e.setLevel(LessonLevel.WORD)
        e.start()
        val prompt = e.prompt

        // 状态机在停顿偏久时会自动补词间隔，这种「多停一下」不该判错
        assertEquals(LessonEngine.Verdict.CORRECT, e.onTyped("  $prompt  "))
    }

    @Test
    fun charSeparatedBySpacesStillMatchesWord() {
        val e = engine()
        e.setLevel(LessonLevel.WORD)
        e.start()
        val prompt = e.prompt

        assertEquals(LessonEngine.Verdict.CORRECT, e.onTyped(prompt.toCharArray().joinToString(" ")))
    }

    @Test
    fun emptyTypedStaysIncomplete() {
        val e = engine()
        e.start()
        assertEquals(LessonEngine.Verdict.INCOMPLETE, e.onTyped(""))
        assertEquals(0, e.answered)
    }

    // ---------- 一轮的推进与结算 ----------

    @Test
    fun roundFinishesAfterTenQuestions() {
        val e = engine()
        e.start()
        e.play(correctCount = 10, wrongCount = 0)

        assertTrue(e.finished)
        assertEquals(10, e.answered)
        assertEquals(10, e.correct)
        assertTrue(e.passed)
    }

    @Test
    fun nextDoesNothingAfterRoundFinished() {
        val e = engine()
        e.start()
        e.play(10, 0)
        val prompt = e.prompt
        e.next()
        assertEquals("答完一轮后不应再出题", prompt, e.prompt)
    }

    @Test
    fun passedRoundUnlocksExactlyOneNewChar() {
        val e = engine()
        e.setLevel(LessonLevel.SINGLE)
        e.start()
        val before = e.unlockedCount

        e.play(correctCount = 8, wrongCount = 2)
        assertTrue(e.finished)
        assertEquals(8, e.correct)
        assertTrue(e.passed)

        assertTrue(e.settle())
        assertEquals(before + 1, e.unlockedCount)
        assertFalse("同一轮不能重复解锁", e.settle())
        assertEquals(before + 1, e.unlockedCount)
    }

    @Test
    fun failedRoundDoesNotUnlock() {
        val e = engine()
        e.setLevel(LessonLevel.SINGLE)
        e.start()
        val before = e.unlockedCount

        e.play(correctCount = 7, wrongCount = 3)
        assertTrue(e.finished)
        assertFalse(e.passed)
        assertFalse(e.settle())
        assertEquals(before, e.unlockedCount)
    }

    @Test
    fun onlySingleLevelUnlocks() {
        listOf(LessonLevel.GROUP, LessonLevel.WORD, LessonLevel.COPY).forEach { level ->
            val e = engine()
            e.setLevel(level)
            e.start()
            val before = e.unlockedCount
            e.play(correctCount = 10, wrongCount = 0)

            assertTrue("$level 应该能过关", e.passed)
            assertFalse("$level 不该推进解锁进度", e.settle())
            assertEquals(before, e.unlockedCount)
        }
    }

    @Test
    fun newRoundResetsCountersButKeepsUnlockedCount() {
        val e = engine()
        e.setLevel(LessonLevel.SINGLE)
        e.start()
        e.play(8, 2)
        e.settle()
        val unlocked = e.unlockedCount

        e.start()
        assertEquals(0, e.answered)
        assertEquals(0, e.correct)
        assertFalse(e.finished)
        assertEquals(unlocked, e.unlockedCount)
    }

    @Test
    fun startAfterFailedRoundAllowsSettleAgain() {
        val e = engine()
        e.setLevel(LessonLevel.SINGLE)

        e.start()
        e.play(3, 7)
        assertFalse(e.settle())

        e.start()
        assertEquals(0, e.answered)
        e.play(9, 1)
        assertTrue(e.settle()) // 新一轮的结算不能被上一轮「已结算」标记挡住
    }

    @Test
    fun resetReturnsToInitialState() {
        val e = engine()
        e.setLevel(LessonLevel.WORD)
        e.start()
        e.play(10, 0)
        e.settle()

        e.reset()
        assertEquals(LessonLevel.SINGLE, e.level)
        assertEquals(KochOrder.STARTING_UNLOCKED, e.unlockedCount)
        assertEquals(0, e.answered)
        assertEquals(0, e.correct)
        assertEquals("", e.prompt)
        assertEquals(LessonEngine.Verdict.IDLE, e.verdict)
    }

    @Test
    fun maxUnlockStopsAdvancing() {
        val e = engine()
        e.setUnlockedCount(KochOrder.maxUnlocked())
        e.setLevel(LessonLevel.SINGLE)
        e.start()
        e.play(10, 0)
        assertFalse("全部解锁后不应再推进", e.settle())
        assertEquals(KochOrder.maxUnlocked(), e.unlockedCount)
        assertNull(KochOrder.nextAfter(KochOrder.maxUnlocked()))
        assertNotNull(KochOrder.nextAfter(0))
    }

    // ---------- 关卡定义 ----------

    @Test
    fun copyLevelHidesCodeAndPlaysDemo() {
        assertTrue(LessonLevel.COPY.playsDemoFirst)
        assertTrue(LessonLevel.COPY.hidesCode)
        assertFalse(LessonLevel.SINGLE.playsDemoFirst)
        assertFalse(LessonLevel.SINGLE.hidesCode)
        assertFalse(LessonLevel.GROUP.hidesCode)
    }

    @Test
    fun levelCyclesThroughAllFour() {
        assertEquals(LessonLevel.GROUP, LessonLevel.SINGLE.next)
        assertEquals(LessonLevel.WORD, LessonLevel.GROUP.next)
        assertEquals(LessonLevel.COPY, LessonLevel.WORD.next)
        assertEquals(LessonLevel.SINGLE, LessonLevel.COPY.next)
    }
}
