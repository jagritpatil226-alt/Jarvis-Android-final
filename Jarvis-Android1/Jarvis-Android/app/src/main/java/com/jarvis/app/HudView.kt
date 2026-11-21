package com.jarvis.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin

/** Iron-Man style HUD: glowing arc reactor + telemetry readouts. */
class HudView(c: Context) : View(c) {
    @Volatile var rows: List<Pair<String, String>> = emptyList()
    @Volatile var status = "BOOTING"
    @Volatile var pct = -1
    private val cy = 0xFF19E3FF.toInt()
    private val gold = 0xFFF5C451.toInt()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var t = 0f
    private val anim = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 9000; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { t = it.animatedValue as Float; invalidate() }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(c: Canvas) {
        c.drawColor(0xFF05080F.toInt())
        val w = width.toFloat(); val h = height.toFloat(); val x = w / 2; val y = w * 0.62f; val r = w * 0.27f
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(x, y, r * 1.5f, intArrayOf(0x9919E3FF.toInt(), 0x0019E3FF), null, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r * 1.5f, p); p.shader = null
        p.style = Paint.Style.STROKE; p.color = cy; p.strokeWidth = 6f; c.drawCircle(x, y, r, p)
        p.strokeWidth = 2f; c.drawCircle(x, y, r * 0.78f, p)
        p.color = gold; p.strokeWidth = 5f
        val o = RectF(x - r * 1.22f, y - r * 1.22f, x + r * 1.22f, y + r * 1.22f)
        for (i in 0 until 3) c.drawArc(o, t + i * 120f, 70f, false, p)
        p.color = cy; p.strokeWidth = 2f; p.pathEffect = DashPathEffect(floatArrayOf(6f, 14f), 0f)
        c.save(); c.rotate(-t, x, y); c.drawCircle(x, y, r * 1.1f, p); c.restore(); p.pathEffect = null
        p.strokeWidth = 4f
        for (i in 0 until 10) {
            val a = Math.toRadians((i * 36 + t / 2).toDouble()); val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
            c.drawLine(x + ca * r * 0.82f, y + sa * r * 0.82f, x + ca * r * 0.95f, y + sa * r * 0.95f, p)
        }
        val tri = Path().apply { moveTo(x, y - r * 0.5f); lineTo(x + r * 0.45f, y + r * 0.3f); lineTo(x - r * 0.45f, y + r * 0.3f); close() }
        c.drawPath(tri, p)
        p.style = Paint.Style.FILL; p.color = 0xFFD8FBFF.toInt()
        c.drawCircle(x, y, r * 0.12f * (1f + 0.15f * sin(Math.toRadians((t * 2).toDouble())).toFloat()), p)

        p.typeface = Typeface.MONOSPACE; p.textAlign = Paint.Align.CENTER
        p.color = gold; p.textSize = w * 0.075f; p.letterSpacing = 0.3f; c.drawText("J.A.R.V.I.S", x, w * 0.14f, p)
        p.color = cy; p.textSize = w * 0.03f; p.letterSpacing = 0.2f; c.drawText("PERSONAL ASSISTANT SYSTEM", x, w * 0.2f, p)

        p.style = Paint.Style.STROKE; p.color = gold; p.strokeWidth = 4f; p.letterSpacing = 0f
        val m = 24f; val l = 60f
        c.drawLines(floatArrayOf(m, m, m + l, m, m, m, m, m + l, w - m, m, w - m - l, m, w - m, m, w - m, m + l,
            m, h - m, m + l, h - m, m, h - m, m, h - m - l, w - m, h - m, w - m - l, h - m, w - m, h - m, w - m, h - m - l), p)

        p.style = Paint.Style.FILL; p.textAlign = Paint.Align.LEFT; p.textSize = w * 0.038f
        var ty = y + r * 1.75f; val lx = w * 0.1f
        p.color = gold; c.drawText("STATUS", lx, ty, p); p.color = Color.WHITE; c.drawText(status.take(26), lx + w * 0.26f, ty, p)
        for ((k, v) in rows) {
            ty += w * 0.062f
            p.color = cy; c.drawText(k, lx, ty, p); p.color = Color.WHITE; c.drawText(v.take(26), lx + w * 0.26f, ty, p)
        }
        if (pct in 0..100) {
            ty += w * 0.07f
            p.color = 0xFF14303A.toInt(); c.drawRect(lx, ty, w - lx, ty + 16f, p)
            p.color = cy; c.drawRect(lx, ty, lx + (w - 2 * lx) * pct / 100f, ty + 16f, p)
        }
    }
}
