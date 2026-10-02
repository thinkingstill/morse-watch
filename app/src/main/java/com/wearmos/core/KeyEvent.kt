package com.wearmos.core

/** 输入来源。触摸为主路径，盲敲为实验路径，侧键用于手动出字。 */
enum class KeySource { TOUCH, KNOCK, BUTTON }

/**
 * 统一的键控事件。
 *
 * 双输入源架构的关键抽象：不论信号来自触摸屏还是加速度计，
 * 都必须合成为一对 [isDown] = true / false 的事件再进入状态机，
 * 这样「时长判点划」的解码逻辑永远只有一份。
 */
data class KeyEvent(
    val source: KeySource,
    val isDown: Boolean,
    val timeMs: Long,
)

/** 摩斯的基本元素。 */
enum class MorseElement(val symbol: Char) {
    DOT('.'),
    DASH('-'),
    ;

    companion object {
        fun of(symbol: Char): MorseElement = when (symbol) {
            '.' -> DOT
            '-' -> DASH
            else -> throw IllegalArgumentException("非法摩斯元素: $symbol")
        }
    }
}

/**
 * 按压时长 → 点 / 划 判定器。
 *
 * 摩斯输入的本质是时间判定而非字符匹配，所以这是整条链路里最需要被单测钉死的一环。
 */
interface DotDashClassifier {

    /** 当前判定阈值：按压时长 ≥ 阈值判为划。 */
    fun thresholdMs(): Float

    fun classify(pressDurationMs: Float): MorseElement

    /** 用一次真实按压修正判定阈值。 */
    fun observe(pressDurationMs: Float, element: MorseElement)
}
