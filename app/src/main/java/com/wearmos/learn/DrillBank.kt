package com.wearmos.learn

import kotlin.random.Random

/**
 * 练习词库。
 *
 * 只收 A–Z 组成的词，方便一个约束贯穿始终：**出题只能使用已解锁的字符**。
 * 这条约束由 [pool] 强制执行，并由单测钉死 —— 学习中突然冒出一个没教过的字母
 * 是这类应用最常见也最伤体验的 bug。
 *
 * 选词思路：先收两三字符的高频短词（建立「连续敲两个字符」的手感），
 * 再收常见词与北约音标词（字母覆盖面广，能把整张码表练一遍）。
 */
object DrillBank {

    val WORDS: List<String> = listOf(
        // 两三字符：先把「连着敲」这件事练顺
        "AR", "AS", "AT", "IS", "IT", "NO", "OK", "ON", "SO", "TO",
        "UP", "US", "AM", "AN", "ME", "MY", "GO",
        // 四到五字符的常用词
        "RUN", "SUN", "RAT", "TAR", "ART", "TAP", "PAT", "SAT", "SET",
        "MOON", "NEST", "TEST", "STAR", "RUST", "MENU", "NAME", "NOTE",
        "KILO", "MIKE", "ROMEO", "ZULU", "GOLF", "LIMA", "ECHO", "JULIET",
        // 北约音标词：字母覆盖面广，适合把整张码表过一遍
        "ALPHA", "BRAVO", "DELTA", "HOTEL", "INDIA", "PAPA", "QUEBEC",
        "SIERRA", "TANGO", "UNIFORM", "VICTOR", "YANKEE", "OSCAR",
        "NOVEMBER", "FOXTROT",
    )

    /** 仅由已解锁字符组成的词。 */
    fun pool(unlocked: Set<Char>): List<String> =
        WORDS.filter { word -> word.isNotEmpty() && word.all { it in unlocked } }

    /**
     * 随机取一个词，尽量避开 [exclude]（避免连续两题同一个词）。
     * 池子为空时返回 null，调用方需自行降级 —— 学习初期确实可能一个词都凑不出来。
     */
    fun random(unlocked: Set<Char>, random: Random, exclude: String? = null): String? {
        val all = pool(unlocked)
        if (all.isEmpty()) return null
        val filtered = all.filter { it != exclude }
        return (filtered.ifEmpty { all }).random(random)
    }
}
