package com.wearmos.core

/**
 * PARIS 标准计时。
 *
 * unit = 1200 / WPM 毫秒；点 = 1u，划 = 3u，字符内间隔 = 1u，字符间间隔 = 3u，词间间隔 = 7u。
 *
 * [charWpm] 与 [effectiveWpm] 不同时即为 **Farnsworth 间隔法**：
 * 点划长度仍按 [charWpm] 给出（保住字符音调辨识度），
 * 而字符间 / 词间间隔按 [effectiveWpm] 拉长（降低初学者跟不上的门槛）。
 */
data class MorseTiming(
    val charWpm: Int = 20,
    val effectiveWpm: Int = 20,
) {
    init {
        require(charWpm > 0) { "charWpm 必须为正，当前 $charWpm" }
        require(effectiveWpm > 0) { "effectiveWpm 必须为正，当前 $effectiveWpm" }
    }

    /** 字符内单位时长，决定点划长度。 */
    val charUnitMs: Float get() = 1200f / charWpm

    /** 间隔单位时长，决定字符 / 词间隙。 */
    val spaceUnitMs: Float get() = 1200f / effectiveWpm

    val dotMs: Float get() = charUnitMs
    val dashMs: Float get() = charUnitMs * 3f
    val intraCharGapMs: Float get() = charUnitMs
    val interCharGapMs: Float get() = spaceUnitMs * 3f
    val wordGapMs: Float get() = spaceUnitMs * 7f

    val isFarnsworth: Boolean get() = charWpm > effectiveWpm

    companion object {
        /** 构造 Farnsworth 计时；effective 不会超过 char，否则语义倒挂。 */
        fun farnsworth(charWpm: Int, effectiveWpm: Int) =
            MorseTiming(charWpm, minOf(effectiveWpm, charWpm))
    }
}

/** 间隔判定策略：回答「静默多久算字符结束 / 词结束」。 */
interface GapPolicy {
    fun interCharGapMs(): Float
    fun wordGapMs(): Float
}

/** 固定 WPM 的间隔策略。 */
class ParisGapPolicy(private val timing: () -> MorseTiming) : GapPolicy {
    override fun interCharGapMs(): Float = timing().interCharGapMs
    override fun wordGapMs(): Float = timing().wordGapMs
}
