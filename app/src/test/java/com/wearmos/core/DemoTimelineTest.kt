package com.wearmos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 示范节奏的时序测试。
 * 20 WPM 下 u = 60ms —— 点 60、划 180、字符内间隔 60、字符间间隔 180、词间隔 420。
 */
class DemoTimelineTest {

    private val timing = MorseTiming(charWpm = 20, effectiveWpm = 20)

    @Test
    fun dotIsOneUnit() {
        val steps = DemoTimeline.forText("E", timing)
        assertEquals(1, steps.size)
        assertEquals(0L, steps[0].startMs)
        assertEquals(60L, steps[0].durationMs)
        assertFalse(steps[0].isDash)
        assertEquals(60L, DemoTimeline.totalMs("E", timing))
    }

    @Test
    fun dashIsThreeUnits() {
        val steps = DemoTimeline.forText("T", timing)
        assertEquals(1, steps.size)
        assertEquals(180L, steps[0].durationMs)
        assertTrue(steps[0].isDash)
        assertEquals(180L, DemoTimeline.totalMs("T", timing))
    }

    @Test
    fun intraCharGapSeparatesElementsOfOneChar() {
        // A = ".-" → 点 1u + 字符内间隔 1u + 划 3u = 5u = 300ms
        val steps = DemoTimeline.forText("A", timing)
        assertEquals(2, steps.size)
        assertEquals(0L, steps[0].startMs)
        assertEquals(60L, steps[0].durationMs)
        assertEquals(120L, steps[1].startMs) // 点 60 播完 + 字符内间隔 60
        assertEquals(180L, steps[1].durationMs)
        assertEquals(300L, DemoTimeline.totalMs("A", timing))
    }

    @Test
    fun interCharGapSeparatesCharacters() {
        val steps = DemoTimeline.forText("EE", timing)
        assertEquals(2, steps.size)
        assertEquals(0L, steps[0].startMs)
        assertEquals(240L, steps[1].startMs) // 点 60 + 字符间隔 180
    }

    @Test
    fun wordGapStretchesToSevenUnits() {
        val steps = DemoTimeline.forText("E E", timing)
        assertEquals(2, steps.size)
        // 首个字符结束于 60，词间隔 420 → 第二个字符起于 480
        assertEquals(480L, steps[1].startMs)
        assertEquals(420L, steps[1].startMs - 60L)
    }

    @Test
    fun timelineNeverOverlaps() {
        val steps = DemoTimeline.forText("SOS TEST", timing)
        assertTrue(steps.isNotEmpty())
        steps.zipWithNext().forEach { (a, b) ->
            assertTrue("时间轴必须单调递增：$a → $b", b.startMs >= a.startMs + a.durationMs)
        }
    }

    @Test
    fun sosHasNineElements() {
        val steps = DemoTimeline.forText("SOS", timing)
        assertEquals(9, steps.size)
        assertEquals(3, steps.count { it.isDash })
    }

    @Test
    fun unsupportedCharsAreSkipped() {
        val steps = DemoTimeline.forText("E#E", timing)
        assertEquals(2, steps.size)
        assertEquals(0L, steps[0].startMs)
        assertEquals(240L, steps[1].startMs)
    }

    @Test
    fun lowercaseIsAccepted() {
        assertEquals(
            DemoTimeline.totalMs("SOS", timing),
            DemoTimeline.totalMs("sos", timing),
        )
    }

    @Test
    fun emptyOrUnsupportedTextYieldsNothing() {
        assertTrue(DemoTimeline.forText("", timing).isEmpty())
        assertTrue(DemoTimeline.forText("   ", timing).isEmpty())
        // 注意别拿 '$' 当"不支持字符"——它确实在码表里（...-..-）
        assertTrue(DemoTimeline.forText("^^^", timing).isEmpty())
        assertEquals(0L, DemoTimeline.totalMs("", timing))
    }

    @Test
    fun currencySignIsActuallyInTheTable() {
        // 反面教材的回归保护：'$' 有码，被当成非法字符就会被静默漏掉
        assertEquals("...-..-", MorseTable.code('$'))
        assertEquals(7, DemoTimeline.forText("$", timing).size)
    }

    @Test
    fun farnsworthStretchesGapsButNotElements() {
        val slow = MorseTiming.farnsworth(charWpm = 20, effectiveWpm = 8)
        val steps = DemoTimeline.forText("EE", slow)

        assertEquals(60L, steps[0].durationMs)  // 元素时长仍按 20 WPM
        assertEquals(510L, steps[1].startMs)    // 60 + 字符间隔 1200/8*3 = 450
        assertTrue(slow.isFarnsworth)
    }
}
