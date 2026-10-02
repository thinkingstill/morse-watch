package com.wearmos.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 震动反馈。
 *
 * 学摩斯时震动不是装饰：它要把「点」和「划」的 1:3 时长比例直接打到手腕上，
 * 让节奏感先于视觉建立起来 —— 所以点划的震动时长刻意按比例拉开，
 * 而字符落地 / 词间隔用「短促多击」区分，避免和点划混淆。
 *
 * 单次震动全部 ≤ 50ms，多击序列总长 ≤ 150ms，控制耗电。
 */
class HapticEngine(
    context: Context,
    private val enabled: () -> Boolean = { true },
) {

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    /** 设备是否真的有马达。没有时要静默降级，不能崩。 */
    val available: Boolean = vibrator?.hasVibrator() == true

    fun onDot() = oneShot(DOT_MS)

    fun onDash() = oneShot(DASH_MS)

    /**
     * 按指定时长震动一次，供示范播放使用。
     *
     * 点划的真实时长来自当前 WPM —— 12 WPM 下一个点是 100ms，远长于 [DOT_MS]。
     * 示范必须把真实节奏打到手腕上，否则学习者建立的是错误的时长感。
     */
    fun pulse(durationMs: Long) {
        if (!enabled() || !available) return
        vibrator?.vibrate(
            VibrationEffect.createOneShot(
                durationMs.coerceIn(MIN_PULSE_MS, MAX_PULSE_MS),
                VibrationEffect.DEFAULT_AMPLITUDE,
            ),
        )
    }

    /** 字符落地：两连击。 */
    fun onCharacter() = waveform(CHAR_PATTERN)

    /** 词间隔：三连击。 */
    fun onWord() = waveform(WORD_PATTERN)

    fun cancel() {
        if (!available) return
        vibrator?.cancel()
    }

    private fun oneShot(durationMs: Long) {
        if (!enabled() || !available) return
        vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun waveform(timings: LongArray) {
        if (!enabled() || !available) return
        vibrator?.vibrate(VibrationEffect.createWaveform(timings, NO_REPEAT))
    }

    private companion object {
        const val NO_REPEAT = -1
        const val DOT_MS = 15L
        const val DASH_MS = 45L

        /** 示范震动的时长范围：下限保证能被感知，上限防止异常参数把马达一直点着。 */
        const val MIN_PULSE_MS = 12L
        const val MAX_PULSE_MS = 600L

        val CHAR_PATTERN = longArrayOf(0L, 6L, 40L, 6L)
        val WORD_PATTERN = longArrayOf(0L, 6L, 40L, 6L, 80L, 6L)
    }
}
