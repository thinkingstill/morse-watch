package com.wearmos.core

/**
 * 文本 ⇄ 摩斯 双向转换。
 *
 * 编码约定：字母间单空格，词间 " / "。
 * 解码容忍：任意数量空格、"/" 或 "|" 作词分隔 —— 用户手抄的摩斯格式千奇百怪。
 */
object MorseCodec {

    private val WORD_SPLIT = Regex("\\s*/\\s*|\\s{2,}")
    private val LETTER_SPLIT = Regex("\\s+")

    /** 文本 → 摩斯。不支持的字符会被跳过，用 [unsupportedChars] 单独提示。 */
    fun encode(text: String): String =
        text.trim()
            .split(LETTER_SPLIT)
            .filter { it.isNotEmpty() }
            .joinToString(" / ") { word ->
                word.mapNotNull { MorseTable.code(it) }.joinToString(" ")
            }

    /** 摩斯 → 文本。无法识别的符号序列给出 '?'。 */
    fun decode(morse: String): String =
        morse.replace('|', '/')
            .split(WORD_SPLIT)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ") { word ->
                word.split(LETTER_SPLIT)
                    .filter { it.isNotEmpty() }
                    .joinToString("") { token -> MorseTable.char(token)?.toString() ?: UNKNOWN }
            }

    /** 单个符号序列 → 字符；无法识别返回 null。 */
    fun decodeCode(code: String): Char? = MorseTable.char(code.trim())

    /** 列出文本里不支持的字符，供 UI 提示。 */
    fun unsupportedChars(text: String): List<Char> =
        text.filter { !it.isWhitespace() && MorseTable.code(it) == null }.toList()

    const val UNKNOWN = "?"
}
