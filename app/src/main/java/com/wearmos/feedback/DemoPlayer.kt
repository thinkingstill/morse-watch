package com.wearmos.feedback

import android.os.Handler
import android.os.Looper
import com.wearmos.core.DemoTimeline
import com.wearmos.core.MorseTiming

/**
 * 节奏示范播放器。
 *
 * 时序计算全部在 [DemoTimeline] 里（纯逻辑、有单测）；这里只做一件事：
 * 把时序表上的每个元素，在对应时刻变成一次震动。
 *
 * 故意自己持有一个 Handler，不与界面的刷新 Handler 共用 ——
 * 否则 [stop] 时一句 removeCallbacksAndMessages(null) 会顺手把界面定时器也清掉。
 *
 * 音频是留好的扩展点：将来若要加蜂鸣，只需在这里按同一份时序表再驱动一个
 * ToneGenerator，音振天然同步，不必重算时间轴。
 */
class DemoPlayer(
    private val haptics: HapticEngine,
    private val onFinished: () -> Unit = {},
) {

    private val handler = Handler(Looper.getMainLooper())
    private var playing = false

    val isPlaying: Boolean get() = playing

    fun play(text: String, timing: MorseTiming) {
        stop()
        val steps = DemoTimeline.forText(text, timing)
        if (steps.isEmpty()) {
            onFinished()
            return
        }
        playing = true
        steps.forEach { step ->
            handler.postDelayed({ haptics.pulse(step.durationMs) }, step.startMs)
        }
        // 末尾留一点余量再宣告结束，避免最后一次震动的尾音和「播放中」指示同时消失
        handler.postDelayed({
            playing = false
            onFinished()
        }, DemoTimeline.totalMs(text, timing) + TAIL_MS)
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        playing = false
    }

    private companion object {
        const val TAIL_MS = 150L
    }
}
