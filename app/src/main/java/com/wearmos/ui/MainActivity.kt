package com.wearmos.ui

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

private val COLOR_BG = 0xFF000000.toInt()
private val COLOR_ACCENT = 0xFF97C459.toInt()
private val COLOR_DIM = 0xFF8A8A86.toInt()

/**
 * M0 链路验证壳。
 *
 * 目标只有一个：证明「构建 → 侧载 → 启动 → 方屏渲染 → Canvas 绘制」这条链路通。
 * 屏幕上直接打印设备实测参数，方便与 adb 读到的值交叉核对。
 * M1 起本类会被真正的敲击台（KeyerActivity）取代。
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val dm = resources.displayMetrics

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(COLOR_BG)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        root.addView(label("WEAR·MOS", 24f, COLOR_ACCENT, bold = true))
        root.addView(label("M0 链路验证", 12f, COLOR_DIM).withTop(dp(4)))

        root.addView(
            MorseStripView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(20)
                ).also { it.topMargin = dp(14) }
            }
        )

        listOf(
            "${dm.widthPixels} × ${dm.heightPixels} px",
            "${dm.widthPixels / dm.density} × ${dm.heightPixels / dm.density} dp @ ${dm.densityDpi} dpi",
            "ABI ${Build.SUPPORTED_ABIS.joinToString("/")}",
            "API ${Build.VERSION.SDK_INT} · ${Build.MODEL}",
        ).forEach { root.addView(label(it, 11f, COLOR_DIM).withTop(dp(3))) }

        root.addView(label("· − · −  READY", 13f, COLOR_ACCENT, bold = true).withTop(dp(16)))

        setContentView(root)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun label(s: String, sizeSp: Float, color: Int, bold: Boolean = false): TextView {
        val typeface = Typeface.create(Typeface.MONOSPACE, if (bold) Typeface.BOLD else Typeface.NORMAL)
        return TextView(this).apply {
            text = s
            textSize = sizeSp
            setTextColor(color)
            this.typeface = typeface
            gravity = Gravity.CENTER
        }
    }

    private fun TextView.withTop(px: Int): TextView = apply { setPadding(0, px, 0, 0) }

    /**
     * 最小的 Canvas 绘制验证：把一段摩斯节奏画成点划条。
     * 这是 M1 里 PulseStripView 的雏形 —— 先确认自绘在这块 372×430 方屏上没问题。
     */
    private class MorseStripView(context: Context) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_ACCENT }

        /** 1 = 点，3 = 划（单位数）。内容是 "HI" 的节奏。 */
        private val seq = intArrayOf(1, 1, 1, 1, 3, 1)

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (width == 0 || seq.isEmpty()) return

            val totalUnits = seq.sum() + (seq.size - 1)
            val unit = width.toFloat() / totalUnits
            val cy = height / 2f
            val ry = height * 0.24f

            var x = 0f
            for (s in seq) {
                val w = unit * s
                canvas.drawRoundRect(x, cy - ry, x + w, cy + ry, ry, ry, paint)
                x += w + unit
            }
        }
    }
}
