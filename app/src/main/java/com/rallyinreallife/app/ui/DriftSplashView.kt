package com.rallyinreallife.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Scena animata della splash screen: un'auto rally disegnata (stile Subaru
 * blu con cerchi oro) che drifta nel fango, con spruzzi di fango dietro le
 * ruote posteriori e strisciate sul terreno.
 */
class DriftSplashView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private data class Particle(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        var radius: Float, var alpha: Int
    )

    private val mudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#5B4327") }
    private val mudLightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#8A6A3F") }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4A3620")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0B1E5B") }
    private val roofPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#12308F") }
    private val wheelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F5C518") }
    private val tirePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#111111") }
    private val windowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#9FD4E8") }
    private val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFF3B0") }

    private val particles = mutableListOf<Particle>()
    private val trailPoints = mutableListOf<Pair<Float, Float>>()
    private var animator: ValueAnimator? = null
    private var t = 0f

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = Long.MAX_VALUE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                t = it.animatedValue as Float
                spawnParticles()
                updateParticles()
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    private fun spawnParticles() {
        val cx = width * (0.15f + 0.7f * t)
        val cy = height * 0.62f
        repeat(3) {
            particles.add(
                Particle(
                    x = cx - width * 0.10f,
                    y = cy + height * 0.04f,
                    vx = -(40f + Random.nextFloat() * 160f),
                    vy = -(60f + Random.nextFloat() * 220f),
                    radius = 3f + Random.nextFloat() * 9f,
                    alpha = 255
                )
            )
        }
        trailPoints.add(cx to cy + height * 0.05f)
        if (trailPoints.size > 120) trailPoints.removeAt(0)
    }

    private fun updateParticles() {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.x += p.vx * 0.016f
            p.y += p.vy * 0.016f
            p.vy += 18f
            p.alpha -= 6
            if (p.alpha <= 0 || p.y > height) iterator.remove()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#0A0A12"))

        val groundY = height * 0.62f

        mudPaint.alpha = 255
        canvas.drawRect(0f, groundY + height * 0.06f, width.toFloat(), height.toFloat(), mudLightPaint)
        canvas.drawOval(width * 0.1f, groundY, width * 0.9f, groundY + height * 0.12f, mudPaint)

        trailPaint.strokeWidth = height * 0.02f
        for (i in 1 until trailPoints.size) {
            val (x1, y1) = trailPoints[i - 1]
            val (x2, y2) = trailPoints[i]
            trailPaint.alpha = (i * 255 / trailPoints.size).coerceIn(0, 120)
            canvas.drawLine(x1, y1, x2, y2, trailPaint)
        }

        particles.forEach { p ->
            mudLightPaint.alpha = p.alpha
            canvas.drawCircle(p.x, p.y, p.radius, mudLightPaint)
        }
        mudLightPaint.alpha = 255

        drawCar(canvas, groundY)
    }

    private fun drawCar(canvas: Canvas, groundY: Float) {
        val cx = width * (0.15f + 0.7f * t)
        val cy = groundY + height * 0.01f
        val bob = sin(t * 40f) * height * 0.006f
        val driftAngle = Math.toDegrees(sin(t * 6f) * 0.45).toFloat()

        canvas.save()
        canvas.translate(cx, cy + bob)
        canvas.rotate(driftAngle)

        val unit = width / 100f
        val bodyW = 42f * unit
        val bodyH = 12f * unit

        canvas.drawRect(-bodyW / 2, -bodyH, bodyW / 2, 0f, bodyPaint)

        val cabin = Path().apply {
            moveTo(-bodyW * 0.22f, -bodyH)
            lineTo(-bodyW * 0.12f, -bodyH * 2.1f)
            lineTo(bodyW * 0.12f, -bodyH * 2.1f)
            lineTo(bodyW * 0.24f, -bodyH)
            close()
        }
        canvas.drawPath(cabin, roofPaint)
        canvas.drawPath(cabin, windowPaint.apply { alpha = 160 })

        canvas.drawRect(-bodyW * 0.42f, -bodyH * 1.35f, -bodyW * 0.05f, -bodyH * 1.2f, bodyPaint)

        canvas.drawCircle(-bodyW * 0.30f, 0f, 4.6f * unit, tirePaint)
        canvas.drawCircle(bodyW * 0.30f, 0f, 4.6f * unit, tirePaint)
        canvas.drawCircle(-bodyW * 0.30f, 0f, 2.6f * unit, wheelPaint)
        canvas.drawCircle(bodyW * 0.30f, 0f, 2.6f * unit, wheelPaint)

        canvas.drawCircle(bodyW * 0.48f, -bodyH * 0.6f, 1.6f * unit, lightPaint)

        canvas.restore()
    }
}
