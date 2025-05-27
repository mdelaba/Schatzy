package com.example.schatzy.ui.dashboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

class FallingBallView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private data class Ball(var x: Float, var y: Float, var velocity: Float)

    private val paint = Paint().apply {
        color = Color.RED
        isAntiAlias = true
    }

    private val balls = mutableListOf<Ball>()
    private val radius = 60f
    private val handler = Handler(Looper.getMainLooper())
    private val frameRate = 16L // ~60 FPS
    private val ballCount = 10
    private val minHorizontalSpacing = radius * 2.5f

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateBalls()
            invalidate()
            handler.postDelayed(this, frameRate)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.post(updateRunnable)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(updateRunnable)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)
        for (ball in balls) {
            canvas.drawCircle(ball.x, ball.y, radius, paint)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        initializeBalls()
    }

    private fun initializeBalls() {
        balls.clear()
        val usedXPositions = mutableListOf<Float>()

        repeat(ballCount) {
            var x: Float
            var attempts = 0
            do {
                x = Random.nextFloat() * width
                attempts++
            } while (
                usedXPositions.any { kotlin.math.abs(it - x) < minHorizontalSpacing } && attempts < 100
            )

            usedXPositions.add(x)

            val y = Random.nextFloat() * height
            val velocity = Random.nextFloat() * 15 + 5
            balls.add(Ball(x, y, velocity))
        }
    }

    private fun updateBalls() {
        for (ball in balls) {
            ball.y += ball.velocity
            if (ball.y > height + radius) {
                ball.y = -radius
            }
        }
    }
}
