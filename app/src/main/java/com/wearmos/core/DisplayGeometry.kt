package com.wearmos.core

import kotlin.math.sqrt

/**
 * 屏幕几何：把「这块表是方屏还是圆屏」变成可计算的量。
 *
 * 为什么需要它：圆屏的四角被表圈挡掉，一行通栏内容在靠近上下两端时**两端会被吃掉**。
 * 466×466 的圆屏上，y=6px 处横向只剩 105px 可见（全宽 466px）——
 * 顶部导航条的四格里有两格会完全落在表圈外，**物理上也点不到**（圆表的触摸面板是圆的）。
 *
 * 所以圆屏上「通栏」是不能用的，每一行的可用宽度必须按它所在的竖直位置算：
 *
 *     halfWidth(y) = √(R² − (centerY − y)²)
 *
 * 方屏（squircle）没有这个约束，[isRound] 为 false 时所有方法一律返回全宽，
 * 调用方不需要写分支 —— 这也保证了方屏上的既有行为一个像素都不变。
 *
 * 纯逻辑、不依赖 Android，因此可以对着真实的 466/372 数值直接跑单测。
 */
data class DisplayGeometry(
    val widthPx: Int,
    val heightPx: Int,
    val density: Float,
    val isRound: Boolean,
) {

    val widthDp: Float get() = widthPx / density
    val heightDp: Float get() = heightPx / density

    /** 内切圆半径。圆屏取短边的一半。 */
    val radiusPx: Float get() = minOf(widthPx, heightPx) / 2f

    val centerXPx: Float get() = widthPx / 2f
    val centerYPx: Float get() = heightPx / 2f

    /** 距屏幕顶端 [yPx] 处，横向能看到多宽。方屏恒为全宽。 */
    fun widthAtPx(yPx: Float): Float {
        if (!isRound) return widthPx.toFloat()
        val dy = centerYPx - yPx
        val inner = radiusPx * radiusPx - dy * dy
        return if (inner <= 0f) 0f else 2f * sqrt(inner)
    }

    /**
     * 一条竖直带 [topPx, bottomPx] 内**最窄处**的宽度。
     *
     * 圆在上下两端都收窄，所以最窄处一定出现在离中线更远的那一端 ——
     * 取两端的小值即可，不必扫描整条带。
     */
    fun widthForBandPx(topPx: Float, bottomPx: Float): Float {
        if (!isRound) return widthPx.toFloat()
        return minOf(widthAtPx(topPx), widthAtPx(bottomPx))
    }

    /** 内接正方形边长：四角都还在圆内的最大宽度。列表这类矩形内容用它最省事。 */
    val inscribedSidePx: Float
        get() = if (isRound) minOf(widthPx, heightPx) / SQRT2 else minOf(widthPx, heightPx).toFloat()

    fun px(dp: Float): Float = dp * density

    fun dp(px: Float): Float = px / density

    companion object {
        const val SQRT2 = 1.41421356f
    }
}
