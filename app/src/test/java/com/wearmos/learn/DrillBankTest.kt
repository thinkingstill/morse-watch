package com.wearmos.learn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 词库约束测试。
 *
 * 这里只有一条真正重要的规则：**出题不能用还没教的字符**。
 * 它在实际使用中极难手工发现（要在解锁到某个字符、又恰好抽到某个词时才暴露），
 * 所以必须由单测覆盖。
 */
class DrillBankTest {

    @Test
    fun allWordsAreUppercaseLettersOnly() {
        DrillBank.WORDS.forEach { word ->
            assertTrue("词库含非法字符：$word", word.isNotEmpty() && word.all { it in 'A'..'Z' })
        }
    }

    @Test
    fun wordListHasNoDuplicates() {
        assertEquals(DrillBank.WORDS.size, DrillBank.WORDS.toSet().size)
    }

    @Test
    fun poolOnlyReturnsWordsFullyCoveredByUnlockedChars() {
        var count = 1
        while (count <= KochOrder.maxUnlocked()) {
            val unlocked = KochOrder.unlockedSet(count)
            DrillBank.pool(unlocked).forEach { word ->
                assertTrue(
                    "解锁 $count 个字符时不该出现 $word",
                    word.all { it in unlocked },
                )
            }
            count++
        }
    }

    @Test
    fun startingUnlockCountAlreadyYieldsUsableWords() {
        // 初始解锁 8 个字符就必须有词可练，否则「短词」关一开局就是空的
        val pool = DrillBank.pool(KochOrder.unlockedSet(KochOrder.STARTING_UNLOCKED))
        assertTrue("初始解锁集应能凑出词：$pool", pool.size >= 8)
        assertTrue(pool.contains("STAR"))
        assertFalse(pool.contains("TEST")) // E 还没解锁
    }

    @Test
    fun fourUnlockedCharsYieldNoWords() {
        // 只解锁 K M R S 时确实一个词都凑不出来 —— 这正是初始值取 8 而不是 4 的原因
        assertTrue(DrillBank.pool(KochOrder.unlockedSet(4)).isEmpty())
    }

    @Test
    fun fullAlphabetUnlocksEveryWord() {
        val all = KochOrder.unlockedSet(KochOrder.maxUnlocked())
        assertEquals(DrillBank.WORDS.size, DrillBank.pool(all).size)
    }

    @Test
    fun randomNeverReturnsTheExcludedWord() {
        val all = KochOrder.unlockedSet(KochOrder.maxUnlocked())
        val random = Random(20260930)
        repeat(50) {
            val word = DrillBank.random(all, random, exclude = "TEST")
            assertNotNull(word)
            assertFalse("不该连续出同一个词", word == "TEST")
        }
    }

    @Test
    fun randomFallsBackWhenOnlyOneWordIsAvailable() {
        // A/R 只能拼出 AR 一个词；此时即使它正是上一题，也必须给出题而不是返回 null
        val onlyAr = setOf('A', 'R')
        assertEquals(listOf("AR"), DrillBank.pool(onlyAr))
        assertEquals("AR", DrillBank.random(onlyAr, Random(1), exclude = "AR"))
    }

    @Test
    fun randomReturnsNullWhenNothingFits() {
        assertNull(DrillBank.random(setOf('X'), Random(1)))
    }
}
