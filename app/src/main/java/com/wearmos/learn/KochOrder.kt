package com.wearmos.learn

/**
 * Koch 学习顺序。
 *
 * 教学顺序不等于字母表顺序：Koch 法的做法是**先教听觉/敲击上最容易区分的字符**，
 * 一开始只用少数几个字符出题，等准确率上来了再解锁下一个 —— 这样学习者不会
 * 一上来就面对 26 选 1 的识别压力，也不会先学会一堆互相干扰的相似码。
 *
 * 本表沿用通行的 Koch 序列（字母在前、数字在后），不做任何自定义调整，
 * 以便和网上现成的练习材料对照。
 */
object KochOrder {

    /**
     * 字母的 Koch 顺序：
     * K M R S 是四个最早教的字符（时长差异最大、互相不易混淆），
     * 随后按「新增一个、复习全部」的节奏推进。
     */
    val LETTERS: List<Char> = "KMRSUAPTLOWINJEFYVGQZHBCXD".toList()

    /**
     * 数字的 Koch 顺序：按「先中间后两端」的思路排 ——
     * 5(.....)、0(−−−−−) 是两个极端，9(−−−−.) 与 1(.−−−−) 各自只差一位，
     * 依次向外收，避免把 2(..−−−) 和 3(...−−) 这类近邻放在一起学。
     */
    val DIGITS: List<Char> = "5091827364".toList()

    /** 完整学习序列：字母 → 数字。 */
    val SEQUENCE: List<Char> = LETTERS + DIGITS

    /**
     * 初始解锁的字符数。
     *
     * 取 8（K M R S U A P T）是刻意选的：这个集合已经能拼出足够多的真实短词
     * （AR / AT / UP / US / RAT / TAR / ART / TAP / PAT / SAT / STAR / RUST），
     * 让「短词」关卡从第一天就有内容可练；再少就只有零星几个词了。
     */
    const val STARTING_UNLOCKED = 8

    fun maxUnlocked(): Int = SEQUENCE.size

    /** 已解锁的字符列表，按 Koch 顺序。count 会被夹到合法范围。 */
    fun unlocked(count: Int): List<Char> =
        SEQUENCE.take(count.coerceIn(1, SEQUENCE.size))

    fun unlockedSet(count: Int): Set<Char> = unlocked(count).toSet()

    /** 下一个待解锁的字符；全部解锁完返回 null。 */
    fun nextAfter(count: Int): Char? = SEQUENCE.getOrNull(count)
}
