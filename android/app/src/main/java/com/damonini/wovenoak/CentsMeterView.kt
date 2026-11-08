/*
 * Woven Oak — Copyright (C) 2026 Paul Blanchard (Damonini) and Pepper —
 * Damonini and Pepper Software Development.
 * Free software under the GNU General Public License v3; see LICENSE.
 */
package com.damonini.wovenoak

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Sharp/flat drift meter: a horizontal track with a centre tick (in
 * tune) and a needle showing cents off the nearest note, -50..+50.
 * Green within ±6 cents, amber within ±20, red beyond. Dimmed when
 * there is no voiced pitch.
 */
class CentsMeterView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var cents: Int = 0
    var active: Boolean = false
        set(value) { field = value; invalidate() }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3A4149"); strokeWidth = 6f; style = Paint.Style.STROKE
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9AA4AE"); strokeWidth = 4f
    }
    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        val midY = h / 2f; val cx = w / 2f
        val left = 16f; val right = w - 16f
        canvas.drawLine(left, midY, right, midY, trackPaint)
        // Ticks at -50, -25, 0, +25, +50 cents.
        for (c in intArrayOf(-50, -25, 0, 25, 50)) {
            val x = cx + (c / 50f) * (right - cx - 0f)
            val tickH = if (c == 0) h * 0.36f else h * 0.22f
            canvas.drawLine(x, midY - tickH / 2, x, midY + tickH / 2, tickPaint)
        }
        if (!active) return
        val clamped = cents.coerceIn(-50, 50)
        val x = cx + (clamped / 50f) * (right - cx)
        val a = kotlin.math.abs(clamped)
        needlePaint.color = when {
            a <= 6 -> Color.parseColor("#58C472")
            a <= 20 -> Color.parseColor("#E8B93E")
            else -> Color.parseColor("#E5533C")
        }
        canvas.drawCircle(x, midY, h * 0.30f, needlePaint)
    }

    fun setReading(centsValue: Int, isActive: Boolean) {
        cents = centsValue; active = isActive; invalidate()
    }
}
