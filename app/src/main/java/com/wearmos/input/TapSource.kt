package com.wearmos.input

import com.wearmos.core.KeyEvent
import com.wearmos.core.KeySource

/**
 * 触摸 → [KeyEvent] 适配器。
 *
 * 只做两件事：把 ACTION_DOWN / UP 归一成按下 / 抬起，并吞掉多指与重复按下。
 * 时长判定不在这里 —— 那是 [com.wearmos.core.KeyerStateMachine] 的职责，
 * 触摸与盲敲两个输入源必须共用同一套判定，否则会分裂出两套解码逻辑。
 */
class TapSource(
    private val clock: () -> Long,
    private val onEvent: (KeyEvent) -> Unit,
) {

    private var activePointer = -1
    private var pressed = false

    val isPressed: Boolean get() = pressed

    /** 本次按下的起始时刻；-1 表示当前没有按下。 */
    var pressedSinceMs: Long = -1L
        private set

    /** 最近一次抬手的时刻；-1 表示还没有过完整按键。 */
    var releasedAtMs: Long = -1L
        private set

    fun onPointerDown(pointerId: Int) {
        if (pressed) return // 多指或重复 DOWN：只认第一根手指
        pressed = true
        activePointer = pointerId
        pressedSinceMs = clock()
        onEvent(KeyEvent(KeySource.TOUCH, isDown = true, timeMs = pressedSinceMs))
    }

    fun onPointerUp(pointerId: Int) {
        if (!pressed || pointerId != activePointer) return
        finish()
    }

    /** 触摸被系统手势抢走时补一个抬起事件，避免状态机卡在按下态。 */
    fun cancel() {
        if (!pressed) return
        finish()
    }

    private fun finish() {
        pressed = false
        activePointer = -1
        releasedAtMs = clock()
        pressedSinceMs = -1L
        onEvent(KeyEvent(KeySource.TOUCH, isDown = false, timeMs = releasedAtMs))
    }
}
