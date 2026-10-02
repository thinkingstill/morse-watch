package com.wearmos.data

import android.content.Context
import com.wearmos.learn.KochOrder
import com.wearmos.learn.LessonLevel

/**
 * 学习进度持久化。
 *
 * 与 [SettingsStore] 分开两个 prefs 文件：设置是「怎么用」，进度是「学到哪」，
 * 重置进度时不该把用户调好的速度和震动偏好一起清掉。
 *
 * 设备无 GMS、纯表上运行，所以同样只用 SharedPreferences，不引数据库、不联网。
 */
class ProgressStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 已解锁字符数（Koch 顺序的前 N 个）。 */
    var unlockedCount: Int
        get() = prefs.getInt(KEY_UNLOCKED, KochOrder.STARTING_UNLOCKED)
            .coerceIn(1, KochOrder.maxUnlocked())
        set(value) {
            prefs.edit()
                .putInt(KEY_UNLOCKED, value.coerceIn(1, KochOrder.maxUnlocked()))
                .apply()
        }

    /** 上次练习的关卡，下次打开直接续上，不用每次重新切一遍。 */
    var lastLevel: LessonLevel
        get() = LessonLevel.entries.getOrElse(prefs.getInt(KEY_LEVEL, 0)) { LessonLevel.SINGLE }
        set(value) {
            prefs.edit().putInt(KEY_LEVEL, value.ordinal).apply()
        }

    /** 该关卡的历史最好成绩（答对题数）。 */
    fun best(level: LessonLevel): Int = prefs.getInt(bestKey(level), 0)

    fun recordRound(level: LessonLevel, correct: Int) {
        if (correct > best(level)) prefs.edit().putInt(bestKey(level), correct).apply()
    }

    /** 单个字符的 (作答次数, 正确次数)。 */
    fun charStat(char: Char): Pair<Int, Int> {
        val parts = (prefs.getString(statKey(char), null) ?: return 0 to 0).split('/')
        if (parts.size != 2) return 0 to 0
        return (parts[0].toIntOrNull() ?: 0) to (parts[1].toIntOrNull() ?: 0)
    }

    /** 该字符的历史正确率；没有样本时返回 null（而不是 0，二者含义完全不同）。 */
    fun accuracy(char: Char): Float? {
        val (attempts, correct) = charStat(char)
        return if (attempts == 0) null else correct.toFloat() / attempts
    }

    /**
     * 记录一次作答涉及的字符。同一题里重复出现的字符只记一次 ——
     * 题目 "SSS" 不该把 S 的样本数一次顶高三倍。
     */
    fun recordChars(chars: CharSequence, correct: Boolean) {
        val editor = prefs.edit()
        // 注意：CharSequence.filter 返回的是 String，String 没有 distinct()，必须先 toList()
        chars.filter { it in 'A'..'Z' }
            .toList()
            .distinct()
            .forEach { char ->
                val (attempts, corrects) = charStat(char)
                editor.putString(
                    statKey(char),
                    "${attempts + 1}/${corrects + if (correct) 1 else 0}",
                )
            }
        editor.apply()
    }

    /** 清空学习进度，但保留速度 / 震动等使用设置。 */
    fun resetProgress() {
        prefs.edit().clear().apply()
    }

    private fun bestKey(level: LessonLevel) = "best_${level.name}"

    private fun statKey(char: Char) = "stat_$char"

    private companion object {
        const val PREFS = "wearmos_progress"
        const val KEY_UNLOCKED = "unlocked_count"
        const val KEY_LEVEL = "last_level"
    }
}
