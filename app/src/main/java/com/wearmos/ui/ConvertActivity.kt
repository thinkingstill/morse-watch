package com.wearmos.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.wearmos.R
import com.wearmos.ui.view.KeyboardView
import com.wearmos.ui.view.MorseKeyView
import com.wearmos.ui.view.PulseStripView

/**
 * 双向转换屏：需求 3 的完整形态。
 *
 * 两个方向共用一块结果区，只是「输入 / 输出」的含义互换：
 *  - 文字 → 摩斯：用自绘键盘打字，下方实时给出码
 *  - 摩斯 → 文字：复用敲击台的键控区，上方实时给出你敲的码、下方给出解出的字
 *
 * 摩斯侧不另写一套解码，而是直接复用 [KeyerScreen] 的状态机 —— 从敲击台学到的
 * 手感（包括自适应阈值）在这里原样延续。
 */
class ConvertActivity : KeyerScreen() {

    private enum class Mode { TEXT, MORSE }

    private var mode = Mode.TEXT
    private val text = StringBuilder()
    private val sessionMorse = StringBuilder()

    /** 上一次提交给引擎的文本，避免每帧重复渲染。 */
    private var lastRendered = ""

    private val main = Handler(Looper.getMainLooper())

    private lateinit var modeCell: TextView
    private lateinit var copyCell: TextView
    private lateinit var inputView: TextView
    private lateinit var outputView: TextView
    private lateinit var textPane: View
    private lateinit var morsePane: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        applyMode()
    }

    override fun onKeyerPause() {
        main.removeCallbacksAndMessages(null)
    }

    override fun onCharacter(char: Char, code: String) {
        if (mode != Mode.MORSE) return
        if (sessionMorse.isNotEmpty()) sessionMorse.append(' ')
        sessionMorse.append(code)
        refresh()
    }

    override fun onWord() {
        if (mode != Mode.MORSE) return
        sessionMorse.append(" / ")
        refresh()
    }

    override fun onKeyerTick(now: Long) {
        val key = if (mode == Mode.MORSE) machine.text + "|" + machine.pendingCode else text.toString()
        if (key != lastRendered) {
            lastRendered = key
            render()
        }
    }

    // ---------- 模式 ----------

    private fun toggleMode() {
        mode = if (mode == Mode.TEXT) Mode.MORSE else Mode.TEXT
        lastRendered = ""
        applyMode()
    }

    private fun applyMode() {
        val isText = mode == Mode.TEXT
        modeCell.text = if (isText) "→ 摩斯" else "→ 文字"
        textPane.visibility = if (isText) View.VISIBLE else View.GONE
        morsePane.visibility = if (isText) View.GONE else View.VISIBLE
        // 摩斯模式下解出的文字是「越新越靠右」，必须保留尾部而不是截断尾部
        outputView.ellipsize = if (isText) TextUtils.TruncateAt.END else TextUtils.TruncateAt.START
        render()
    }

    // ---------- 编辑 ----------

    private fun typeLetter(char: Char) {
        text.append(char)
        render()
    }

    private fun typeSpace() {
        if (text.isNotEmpty() && text.last() != ' ') text.append(' ')
        render()
    }

    private fun backspace() {
        when {
            mode == Mode.MORSE -> machine.backspace()
            text.isNotEmpty() -> text.deleteCharAt(text.length - 1)
        }
        render()
    }

    private fun clearAll() {
        text.setLength(0)
        sessionMorse.setLength(0)
        machine.clear()
        render()
    }

    private fun copyResult() {
        val value = outputView.text.toString()
        if (value.isEmpty()) return
        getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("morse", value))

        copyCell.text = "已复制"
        main.postDelayed({ copyCell.text = "复制" }, COPY_FLASH_MS)
        Log.d(TAG, "copied=${value.take(80)}")
    }

    // ---------- 渲染 ----------

    private fun render() {
        if (mode == Mode.TEXT) {
            inputView.text = if (text.isEmpty()) PLACEHOLDER_TEXT else text.toString()
            outputView.text = if (text.isEmpty()) PLACEHOLDER_MORSE else UiKit.prettyMorse(text.toString())
        } else {
            val shown = buildString {
                append(sessionMorse)
                val pending = machine.pendingCode
                if (pending.isNotEmpty()) {
                    if (isNotEmpty()) append(' ')
                    append(pending)
                }
            }.trim()

            // 逐段转换：词分隔符要原样留成空白，不能让 prettyCode 把它当成摩斯符号
            inputView.text = if (shown.isEmpty()) {
                PLACEHOLDER_MORSE
            } else {
                shown.split(" ")
                    .filter { it.isNotEmpty() }
                    .joinToString(" ") { token -> if (token == "/") "  " else UiKit.prettyCode(token) }
            }
            outputView.text = if (machine.text.isEmpty()) PLACEHOLDER_TEXT else machine.text
        }
    }

    // ---------- 界面 ----------

    private fun buildUi() {
        val geometry = UiKit.geometry(this)
        val side = dp(UiKit.sidePadDp(geometry))
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.bg))
            setPadding(side, dp(UiKit.topPadDp(geometry)), side, dp(UiKit.bottomPadDp(geometry)))
        }

        root.addView(
            UiKit.navBar(this, Screen.CONVERT),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(UiKit.NAV_HEIGHT_DP)),
        )

        inputView = UiKit.label(this, PLACEHOLDER_TEXT, 13f, R.color.text_primary, mono = true)
            .apply { ellipsize = TextUtils.TruncateAt.END }
        root.addView(inputView, lp(dp(20)))

        outputView = UiKit.label(this, PLACEHOLDER_MORSE, 13f, R.color.accent, mono = true)
        root.addView(outputView, lp(dp(20)))

        // ---- 内容区：文字模式放键盘，摩斯模式放键控区 ----
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        textPane = KeyboardView(this).apply {
            onLetter = { typeLetter(it) }
            onSpace = { typeSpace() }
            onBackspace = { backspace() }
            onMorse = { toggleMode() }
            onClear = { clearAll() }
        }

        morsePane = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val pulseView = PulseStripView(this)
        morsePane.addView(pulseView, lp(dp(16)))
        val keyView = MorseKeyView(this)
        attachKeyer(keyView, pulseView)
        morsePane.addView(
            keyView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )

        content.addView(
            textPane,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        content.addView(
            morsePane,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        root.addView(
            content,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = dp(3)
            },
        )

        // ---- 动作行：模式切换 / 清空 / 复制 ----
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        modeCell = actionCell("→ 摩斯", R.color.accent) { toggleMode() }
        val clearCell = actionCell("清空", R.color.dim) { clearAll() }
        copyCell = actionCell("复制", R.color.dim) { copyResult() }
        listOf(modeCell, clearCell, copyCell).forEachIndexed { index, cell ->
            actions.addView(
                cell,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    if (index < 2) rightMargin = dp(3)
                },
            )
        }
        root.addView(
            actions,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(18)).apply {
                topMargin = dp(3)
            },
        )

        // 内容块（键盘 / 键控区）由各自视图按圆自行收窄或裁形，这里不缩它
        UiKit.constrainRowsToCircle(root)
        setContentView(root)
    }

    private fun actionCell(text: String, colorRes: Int, onClick: () -> Unit): TextView =
        UiKit.label(this, text, 11f, colorRes).apply {
            setBackgroundColor(getColor(R.color.key_fill))
            isClickable = true
            setOnClickListener { onClick() }
        }

    private fun lp(heightPx: Int) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx)

    private fun dp(value: Int) = UiKit.dp(this, value)

    private companion object {
        const val TAG = "WearMos"
        const val COPY_FLASH_MS = 1200L
        const val PLACEHOLDER_TEXT = "—"
        const val PLACEHOLDER_MORSE = "· − · −"
    }
}
