package com.wearmos.ui

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import com.wearmos.core.AdaptiveGapPolicy
import com.wearmos.core.AdaptiveThreshold
import com.wearmos.core.DotDashClassifier
import com.wearmos.core.GapPolicy
import com.wearmos.core.KeyerStateMachine
import com.wearmos.core.MorseElement
import com.wearmos.core.MorseTiming
import com.wearmos.core.ParisGapPolicy
import com.wearmos.data.SettingsStore
import com.wearmos.feedback.HapticEngine
import com.wearmos.input.TapSource
import com.wearmos.ui.view.MorseKeyView
import com.wearmos.ui.view.PulseStripView

/**
 * 敲击类屏幕的公共底座（敲击台与学习屏共用）。
 *
 * 抽出来的动机很实际：整套「触摸 → 归一化 → 时长判点划 → 静默结算 → 出字」
 * 的接线有十来处，其中 `switchableClassifier` / `switchableGaps` 这两个包装器
 * 是最容易写错、也最难靠肉眼发现的地方（写错表现为「换 WPM 后判定不跟着变」，
 * 而且在表上点半天都未必看得出来）。让两张屏共用一份实现，就不会有一张对一张错。
 *
 * 子类要做的只有两件事：把 [attachKeyer] 指到自己的键控区，
 * 以及按需在 [onKeyerTick] 里刷新自己的 UI。
 */
abstract class KeyerScreen : Activity(), KeyerStateMachine.Listener {

    protected lateinit var settings: SettingsStore
    protected lateinit var haptics: HapticEngine

    protected var wpm: Int = SettingsStore.DEFAULT_WPM
        private set
    protected var adaptiveEnabled: Boolean = true
        private set
    protected lateinit var timing: MorseTiming
        private set

    /** 当前点划判定阈值，供状态条显示。 */
    protected val thresholdMs: Float get() = adaptive.thresholdMs()

    private lateinit var adaptive: AdaptiveThreshold
    private lateinit var gaps: GapPolicy

    /**
     * 状态机构造时就抓住了分类器与间隔策略的引用，所以这两个包装器始终转发到当前实现。
     * 换 WPM 时只替换内部的 timing —— 不必重建状态机，也不会丢掉已经解出的文本。
     */
    private val switchableClassifier = object : DotDashClassifier {
        override fun thresholdMs() = adaptive.thresholdMs()
        override fun classify(pressDurationMs: Float) = adaptive.classify(pressDurationMs)
        override fun observe(pressDurationMs: Float, element: MorseElement) =
            adaptive.observe(pressDurationMs, element)
    }
    private val switchableGaps = object : GapPolicy {
        override fun interCharGapMs() = gaps.interCharGapMs()
        override fun wordGapMs() = gaps.wordGapMs()
    }

    protected lateinit var machine: KeyerStateMachine
        private set
    protected lateinit var tapSource: TapSource
        private set

    private var keyView: MorseKeyView? = null
    private var pulseView: PulseStripView? = null

    /** 最近一帧的按压状态，子类画自定义反馈时用得上。 */
    protected var pressElapsedMs: Float = 0f
        private set
    protected var pressCrossed: Boolean = false
        private set

    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            machine.onTick(now)
            renderInput(now)
            onKeyerTick(now)
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        settings = SettingsStore(this)
        wpm = settings.wpm
        adaptiveEnabled = settings.adaptiveEnabled
        haptics = HapticEngine(this) { settings.hapticsEnabled }
        applyTiming()

        machine = KeyerStateMachine(switchableClassifier, switchableGaps, this)
        tapSource = TapSource(
            clock = { SystemClock.uptimeMillis() },
            onEvent = {
                machine.onKey(it)
                renderInput(SystemClock.uptimeMillis())
                onKeyerTick(SystemClock.uptimeMillis())
            },
        )
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
        tapSource.cancel()
        haptics.cancel()
        onKeyerPause()
    }

    /** 侧键 = 退格；长按侧键仍是系统关机，不受影响。 */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_STEM_PRIMARY) {
            machine.backspace()
            val now = SystemClock.uptimeMillis()
            renderInput(now)
            onKeyerTick(now)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onElement(element: MorseElement) {
        when (element) {
            MorseElement.DOT -> haptics.onDot()
            MorseElement.DASH -> haptics.onDash()
        }
    }

    /** 把键控区与波形条接到状态机上。子类建完 View 后调用一次。 */
    protected fun attachKeyer(key: MorseKeyView, pulse: PulseStripView) {
        keyView = key
        pulseView = pulse
        key.onDown = { tapSource.onPointerDown(it) }
        key.onUp = { tapSource.onPointerUp(it) }
        key.onCancel = { tapSource.cancel() }
    }

    /** 切换 WPM 档位。只换判定参数，保留已解出的文本。 */
    protected fun cycleWpm() {
        val steps = SettingsStore.WPM_STEPS
        val index = steps.indexOf(wpm).coerceAtLeast(0)
        wpm = steps[(index + 1) % steps.size]
        settings.wpm = wpm
        applyTiming()
        val now = SystemClock.uptimeMillis()
        renderInput(now)
        onKeyerTick(now)
    }

    protected fun toggleAdaptive() {
        adaptiveEnabled = !adaptiveEnabled
        settings.adaptiveEnabled = adaptiveEnabled
        applyTiming()
        val now = SystemClock.uptimeMillis()
        renderInput(now)
        onKeyerTick(now)
    }

    private fun applyTiming() {
        timing = MorseTiming(wpm, wpm)
        adaptive = AdaptiveThreshold(timing.dotMs, timing.dashMs)
        gaps = if (adaptiveEnabled) AdaptiveGapPolicy(adaptive) { timing } else ParisGapPolicy { timing }
    }

    private fun renderInput(now: Long) {
        val pressed = tapSource.isPressed
        val limit = thresholdMs
        val elapsed = if (pressed) (now - tapSource.pressedSinceMs).toFloat() else 0f
        val crossed = pressed && elapsed >= limit

        val idle = if (!pressed && tapSource.releasedAtMs >= 0 && machine.pendingCode.isNotEmpty()) {
            val gap = gaps.interCharGapMs()
            if (gap > 0f) ((now - tapSource.releasedAtMs) / gap).coerceIn(0f, 1f) else 0f
        } else {
            0f
        }

        pressElapsedMs = elapsed
        pressCrossed = crossed

        pulseView?.submit(machine.pendingCode, pressed, idle)
        keyView?.setPress(if (pressed) elapsed / limit else 0f, crossed)
    }

    /** 每帧回调（约 16ms）。子类在这里刷新自己的状态文本。 */
    protected open fun onKeyerTick(now: Long) {}

    protected open fun onKeyerPause() {}

    /** 只刷一帧，用于点击等非连续动作之后立即更新界面。 */
    protected fun refresh() {
        val now = SystemClock.uptimeMillis()
        renderInput(now)
        onKeyerTick(now)
    }

    companion object {
        const val TICK_MS = 16L
    }
}
