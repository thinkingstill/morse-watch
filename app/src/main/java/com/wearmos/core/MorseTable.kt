package com.wearmos.core

/**
 * 国际摩斯电码表（ITU-R M.1677-1）。
 *
 * 只承载数据与查表，不含任何 Android 依赖 —— 这样它能在纯 JVM 单测里被验证，
 * 不需要把 APK 推到表上跑。
 */
object MorseTable {

    private val FORWARD: Map<Char, String> = mapOf(
        'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".",
        'F' to "..-.", 'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---",
        'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.", 'O' to "---",
        'P' to ".--.", 'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-",
        'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-", 'Y' to "-.--",
        'Z' to "--..",

        '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--",
        '4' to "....-", '5' to ".....", '6' to "-....", '7' to "--...",
        '8' to "---..", '9' to "----.",

        '.' to ".-.-.-", ',' to "--..--", '?' to "..--..", '\'' to ".----.",
        '!' to "-.-.--", '/' to "-..-.", '(' to "-.--.", ')' to "-.--.-",
        '&' to ".-...", ':' to "---...", ';' to "-.-.-.", '=' to "-...-",
        '+' to ".-.-.", '-' to "-....-", '_' to "..--.-", '"' to ".-..-.",
        '$' to "...-..-", '@' to ".--.-.",
    )

    private val REVERSE: Map<String, Char> =
        FORWARD.entries.associate { (ch, code) -> code to ch }

    /** 支持的全部字符。 */
    val supported: Set<Char> get() = FORWARD.keys

    /** 字符 → 摩斯码；不支持返回 null。大小写不敏感。 */
    fun code(char: Char): String? = FORWARD[char.uppercaseChar()]

    /** 摩斯码 → 字符；不合法序列返回 null。 */
    fun char(code: String): Char? = REVERSE[code]
}
