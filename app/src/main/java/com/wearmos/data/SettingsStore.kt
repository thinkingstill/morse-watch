package com.wearmos.data

import android.content.Context

/**
 * 轻量设置持久化。
 *
 * 设备无 GMS、纯表上独立运行，所以只用 SharedPreferences —— 不引数据库、不联网。
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var wpm: Int
        get() = prefs.getInt(KEY_WPM, DEFAULT_WPM).coerceIn(WPM_STEPS.first(), WPM_STEPS.last())
        set(value) {
            prefs.edit().putInt(KEY_WPM, value.coerceIn(WPM_STEPS.first(), WPM_STEPS.last())).apply()
        }

    var hapticsEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_HAPTICS, value).apply()
        }

    var adaptiveEnabled: Boolean
        get() = prefs.getBoolean(KEY_ADAPTIVE, true)
        set(value) {
            prefs.edit().putBoolean(KEY_ADAPTIVE, value).apply()
        }

    companion object {
        private const val PREFS = "wearmos"
        private const val KEY_WPM = "wpm"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_ADAPTIVE = "adaptive"

        const val DEFAULT_WPM = 12

        /** 初学者档位。5 WPM 下 unit=240ms，几乎是「跟着数拍子」的速度。 */
        val WPM_STEPS = intArrayOf(5, 8, 10, 12, 16, 20)
    }
}
