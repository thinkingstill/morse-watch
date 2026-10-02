package com.wearmos.core

/**
 * 自适应点划判定。
 *
 * 初学者按点普遍偏长、按划又偏短，固定阈值会持续误判；这里用滚动中位数分别估计
 * 用户的「点时长」与「划时长」，阈值取两者中点。
 *
 * 冷启动用标称值做种子（如 20 WPM 下的 60 / 180 ms），随后被真实样本逐步挤掉。
 * 注意：种子只是起点，不保证从任意手速收敛 —— 主力控制仍是用户手动设定的 WPM，
 * 自适应只做细化。
 */
class AdaptiveThreshold(
    nominalDotMs: Float,
    nominalDashMs: Float,
    private val window: Int = 24,
    private val minMs: Float = 25f,
    private val maxMs: Float = 700f,
) : DotDashClassifier {

    private val dots = FloatRing(window)
    private val dashes = FloatRing(window)

    init {
        require(nominalDotMs > 0f && nominalDashMs > nominalDotMs) {
            "标称点划时长非法: dot=$nominalDotMs dash=$nominalDashMs"
        }
        dots.add(nominalDotMs)
        dashes.add(nominalDashMs)
    }

    override fun thresholdMs(): Float =
        ((dots.median() + dashes.median()) / 2f).coerceIn(minMs, maxMs)

    override fun classify(pressDurationMs: Float): MorseElement =
        if (pressDurationMs >= thresholdMs()) MorseElement.DASH else MorseElement.DOT

    override fun observe(pressDurationMs: Float, element: MorseElement) {
        if (pressDurationMs < minMs || pressDurationMs > maxMs) return
        when (element) {
            MorseElement.DOT -> dots.add(pressDurationMs)
            MorseElement.DASH -> dashes.add(pressDurationMs)
        }
    }

    /** 由学习结果反推单位时长：点 = 1u、划 = 3u，故 u ≈ (点 + 划) / 4。 */
    fun learnedUnitMs(): Float = (dots.median() + dashes.median()) / 4f

    /** 换 WPM 或手动重置时回到标称值。 */
    fun reset(nominalDotMs: Float, nominalDashMs: Float) {
        dots.clear()
        dashes.clear()
        dots.add(nominalDotMs)
        dashes.add(nominalDashMs)
    }
}

/**
 * 自适应间隔策略：用学到的单位时长推算字符间 / 词间阈值。
 * 这样手速慢的用户不会因为间隔被误判成字符结束，而把一个字母拆成两个。
 */
class AdaptiveGapPolicy(
    private val adaptive: AdaptiveThreshold,
    private val fallback: () -> MorseTiming,
) : GapPolicy {

    override fun interCharGapMs(): Float {
        val unit = adaptive.learnedUnitMs()
        return if (unit > 0f) unit * 3f else fallback().interCharGapMs
    }

    override fun wordGapMs(): Float {
        val unit = adaptive.learnedUnitMs()
        return if (unit > 0f) unit * 7f else fallback().wordGapMs
    }
}

/** 定长环形缓冲，用于滚动中位数。 */
private class FloatRing(private val capacity: Int) {
    private val buf = FloatArray(capacity)
    private var count = 0
    private var next = 0

    fun add(v: Float) {
        buf[next] = v
        next = (next + 1) % capacity
        if (count < capacity) count++
    }

    fun clear() {
        count = 0
        next = 0
    }

    fun median(): Float {
        if (count == 0) return 0f
        val a = FloatArray(count) { buf[it] }
        a.sort()
        return if (count % 2 == 1) a[count / 2]
        else (a[count / 2 - 1] + a[count / 2]) / 2f
    }
}
