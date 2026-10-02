package com.wearmos.learn

/**
 * 关卡类型。四关共用同一套「出题 → 敲 → 判定」流程，只是题目形态不同。
 *
 * 递进逻辑：
 * [SINGLE] 只对着一个字符敲，建立点划时长感；
 * [GROUP] 连着敲两个字符，开始需要维持节奏不塌；
 * [WORD] 直接敲真实词汇，长度不再是固定值；
 * [COPY] 把画面上的码撤掉、改成先播放一遍节奏，从「照着抄」进阶到「听着抄」。
 *
 * 只有 [SINGLE] 过关会解锁新字符，其余三关都是练习 —— 避免四条线同时推动进度条，
 * 让「我学到哪了」这件事有唯一答案。
 */
enum class LessonLevel(val label: String) {

    /** 单字符复刻：屏幕给出目标字符，敲出来。 */
    SINGLE("单字符"),

    /** 双字符连打：两个字符连着敲，考察节奏保持。 */
    GROUP("双字符"),

    /** 短词：题目来自 [DrillBank]，只使用已解锁字符。 */
    WORD("短词"),

    /** 听抄：先播放一遍节奏，屏幕不显示码。 */
    COPY("听抄"),
    ;

    /** 界面是否要在出题时先播放一遍示范节奏。 */
    val playsDemoFirst: Boolean get() = this == COPY

    /** 界面是否要隐藏目标字符的摩斯码（听抄关必须隐藏，否则就不是听抄了）。 */
    val hidesCode: Boolean get() = this == COPY

    val next: LessonLevel get() = entries[(ordinal + 1) % entries.size]
}
