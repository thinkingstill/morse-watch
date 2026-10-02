package com.wearmos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 屏幕几何测试。用的是两台真机的实测参数：
 *  - 方屏 OWW212：372×430 @320dpi → 186×215dp，squircle
 *  - 圆屏 OPWW234：466×466 @320dpi → 233×233dp，FLAG_ROUND
 */
class DisplayGeometryTest {

    private val square = DisplayGeometry(372, 430, 2.0f, isRound = false)
    private val round = DisplayGeometry(466, 466, 2.0f, isRound = true)

    // ---------- dp / px ----------

    @Test
    fun squareDeviceMatchesMeasuredDp() {
        assertEquals(186f, square.widthDp, 0.01f)
        assertEquals(215f, square.heightDp, 0.01f)
    }

    @Test
    fun roundDeviceMatchesMeasuredDp() {
        assertEquals(233f, round.widthDp, 0.01f)
        assertEquals(233f, round.heightDp, 0.01f)
        assertEquals(233f, round.radiusPx, 0.01f)
    }

    @Test
    fun dpPxRoundTrip() {
        assertEquals(32f, round.px(16f), 0.01f)
        assertEquals(16f, round.dp(32f), 0.01f)
    }

    // ---------- 方屏：必须完全退化成全宽 ----------

    @Test
    fun squareScreenAlwaysReportsFullWidth() {
        listOf(0f, 6f, 100f, 215f, 429f, 1000f).forEach { y ->
            assertEquals("方屏 y=$y 应为全宽", 372f, square.widthAtPx(y), 0.01f)
        }
        assertEquals(372f, square.widthForBandPx(6f, 38f), 0.01f)
        assertEquals(372f, square.inscribedSidePx, 0.01f)
    }

    // ---------- 圆屏：弦长 ----------

    @Test
    fun roundScreenIsWidestAtTheVerticalCenter() {
        assertEquals(466f, round.widthAtPx(233f), 0.01f)
    }

    @Test
    fun roundScreenTapersToZeroAtThePoles() {
        assertEquals(0f, round.widthAtPx(0f), 0.01f)
        assertEquals(0f, round.widthAtPx(466f), 0.01f)
        // 极端值也不能算出 NaN（半径平方减 dy 平方会变负数）
        assertEquals(0f, round.widthAtPx(-50f), 0.01f)
        assertEquals(0f, round.widthAtPx(999f), 0.01f)
    }

    @Test
    fun roundScreenIsSymmetricAroundTheCenter() {
        listOf(10f, 40f, 100f, 200f, 232f).forEach { dy ->
            assertEquals(
                "y=$dy 与 y=${466 - dy} 应等宽",
                round.widthAtPx(dy),
                round.widthAtPx(466f - dy),
                0.01f,
            )
        }
    }

    @Test
    fun knownChordWidthsOnTheRoundDevice() {
        // 2*sqrt(233^2 - 227^2) = 105.0
        assertEquals(105.0f, round.widthAtPx(6f), 0.5f)
        // 2*sqrt(233^2 - 195^2) = 255.0
        assertEquals(255.0f, round.widthAtPx(38f), 0.5f)
        // 2*sqrt(233^2 - 116^2) = 404.14
        assertEquals(404.1f, round.widthAtPx(117f), 0.5f)
    }

    // ---------- 竖直带取最窄处 ----------

    @Test
    fun bandWidthTakesTheNarrowerEnd() {
        // 顶部导航条带：y 6..38，最窄在顶边
        assertEquals(105.0f, round.widthForBandPx(6f, 38f), 0.5f)
        // 跨过中线的带：最窄在离中线更远的那端
        assertEquals(255.0f, round.widthForBandPx(38f, 428f), 0.5f)
    }

    @Test
    fun bandContainingTheCenterIsLimitedByItsOuterEdge() {
        val band = round.widthForBandPx(100f, 366f)
        assertEquals(round.widthAtPx(100f), band, 0.01f)
        assertTrue("中线处的带不该比全宽还宽", band <= 466f)
    }

    // ---------- 内接正方形 ----------

    @Test
    fun inscribedSquareFitsEntirelyInsideTheCircle() {
        val side = round.inscribedSidePx
        assertEquals(466f / DisplayGeometry.SQRT2, side, 0.5f)

        // 四角到圆心的距离必须 <= 半径
        val half = side / 2f
        val cornerDistance = kotlin.math.sqrt(half * half + half * half)
        assertTrue("内接正方形四角必须落在圆内", cornerDistance <= round.radiusPx + 0.01f)
    }

    // ---------- 真实布局的可用性断言 ----------

    @Test
    fun roundNavBarBandLeavesEnoughRoomForFourCells() {
        // 圆屏上导航条被压到 y 26..42dp（= 52..84px），否则四格里有两格会被表圈切掉
        val band = round.widthForBandPx(52f, 84f)
        val usable = band - round.px(2f * 2f) // 两侧各留 2dp 余量
        assertTrue("导航条可用宽度 $usable px 不足以排四格", usable >= round.px(4 * 30f))
    }

    @Test
    fun naiveFullWidthNavBarWouldBeUnusableOnRound() {
        // 反面教材：老布局把导航条贴在 y=3..19dp（= 6..38px）通栏排，
        // 圆屏上这条带最窄处只有 105px，四格根本没戏 —— 这就是要改的原因
        val naive = round.widthForBandPx(6f, 38f)
        assertTrue("通栏导航在圆屏顶边确实放不下", naive < round.px(4 * 30f))
    }

    @Test
    fun squareWatchNavBarBandIsUnaffected() {
        // 同样的位置搬到方屏上就是全宽，所以方屏不需要任何特殊处理
        assertEquals(372f, square.widthForBandPx(6f, 38f), 0.01f)
    }

    // ---------- 数据类的常规约束 ----------

    @Test
    fun geometryIsAValueType() {
        assertEquals(round, DisplayGeometry(466, 466, 2.0f, true))
        assertNotNull(round)
        assertTrue(round.isRound)
        assertTrue(!square.isRound)
    }
}
