package com.wearmos.core

/**
 * 示范节奏时序表。
 *
 * 这是「听抄 / 摸抄」教学路径的核心：把一段文本摊平成 (起始时刻, 持续时长, 是否划)
 * 的序列，播放端只负责照着这一刻表在对应时刻震动/发声。
 *
 * 为什么单独抽成纯逻辑：
 *  - 时序是学习场景里最不能算错的东西，必须能被单测钉死；
 *  - 震动与（将来要加的）音频共用同一份时序，避免音振各算各的、越播越不同步。
 *
 * 时序遵循 PARIS 标准，与判定用的 [MorseTiming] 取同一组参数：
 * 元素之间留 1u（字符内间隔），字符之间留 3u（字符间间隔），
 * 词之间额外补到 7u。末尾不留尾巴 —— 播放结束即结束。
 */
object DemoTimeline {

    data class Step(val startMs: Long, val durationMs: Long, val isDash: Boolean)

    /**
     * 把 [text] 摊平成时序表。不支持的字符与多余空白直接跳过。
     *
     * 字符之间的间隔按「前一个字符播放完」起算，所以整条时间轴是严格递增的。
     */
    fun forText(text: String, timing: MorseTiming): List<Step> {
        val steps = ArrayList<Step>()
        var cursor = 0L
        var emitted = 0

        text.uppercase().forEach { ch ->
            if (ch == ' ') {
                // 词间隔 7u 里已经隐含了一次字符间隔 3u，这里只补差额
                if (emitted > 0) {
                    cursor += (timing.wordGapMs - timing.interCharGapMs).toLong().coerceAtLeast(0L)
                }
                return@forEach
            }
            val code = MorseTable.code(ch) ?: return@forEach
            if (emitted > 0) cursor += timing.interCharGapMs.toLong()

            code.forEachIndexed { index, symbol ->
                if (index > 0) cursor += timing.intraCharGapMs.toLong()
                val duration = (if (symbol == '-') timing.dashMs else timing.dotMs).toLong()
                steps += Step(cursor, duration, symbol == '-')
                cursor += duration
            }
            emitted++
        }
        return steps
    }

    /** 整段示范的总时长（含最后一个元素自身的时长，不含尾部静默）。 */
    fun totalMs(text: String, timing: MorseTiming): Long =
        forText(text, timing).lastOrNull()?.let { it.startMs + it.durationMs } ?: 0L
}
