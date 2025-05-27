package com.example.schatzy.ui.dashboard

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import com.example.schatzy.R
import kotlin.random.Random

class FallingBallView(context: Context) : View(context) {

    data class FallingImage(
        var x: Float,
        var y: Float,
        val speed: Float,
        var bitmap: Bitmap,
        val isGood: Boolean
    )

    private val numberOfGoodImages = 3
    private val numberOfBadImages = 3

    private val amelieOffsetFromBottom = 240f
    private val handler = Handler(Looper.getMainLooper())
    private val frameRate = 16L

    private val fallingImages = mutableListOf<FallingImage>()
    private var score = 0
    private var lives = 3

    private val goodImageResIds = listOf(
        R.drawable.pasta,
        R.drawable.tofu,
        R.drawable.broccoli
    )

    private val badImageResIds = listOf(
        R.drawable.steak,
        R.drawable.cheese,
        R.drawable.my_jokes
    )

    private val bottomImageResIds = listOf(
        R.drawable.amelie1,
        R.drawable.amelie2,
        R.drawable.amelie3,
        R.drawable.amelie4,
        R.drawable.amelie5,
        R.drawable.amelie6
    )

    private var goodScaledBitmaps: List<Bitmap> = emptyList()
    private var badScaledBitmaps: List<Bitmap> = emptyList()
    private var bottomScaledBitmaps: List<Bitmap> = emptyList()

    private lateinit var amelieBitmap: Bitmap
    private var amelieX = 300f
    private var amelieY = 0f

    private var imagesLoaded = false

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 50f
        isAntiAlias = true
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            for (image in fallingImages) {
                image.y += image.speed

                if (checkCollision(image)) {
                    if (image.isGood) score++ else lives--

                    image.y = -image.bitmap.height.toFloat()
                    image.x = Random.nextFloat() * width.toFloat()
                    image.bitmap = if (image.isGood) goodScaledBitmaps.random() else badScaledBitmaps.random()
                }

                if (image.y > height) {
                    image.y = -image.bitmap.height.toFloat()
                    image.x = Random.nextFloat() * width.toFloat()
                    image.bitmap = if (image.isGood) goodScaledBitmaps.random() else badScaledBitmaps.random()
                }
            }

            invalidate()
            if (lives > 0) {
                handler.postDelayed(this, frameRate)
            }
        }
    }

    init {
        Thread {
            val goodBitmaps = goodImageResIds.map {
                val bmp = BitmapFactory.decodeResource(resources, it)
                Bitmap.createScaledBitmap(bmp, 100, 100, true)
            }
            val badBitmaps = badImageResIds.map {
                val bmp = BitmapFactory.decodeResource(resources, it)
                Bitmap.createScaledBitmap(bmp, 100, 100, true)
            }
            val bottomBitmaps = bottomImageResIds.map {
                val bmp = BitmapFactory.decodeResource(resources, it)
                Bitmap.createScaledBitmap(bmp, 150, 250, true)
            }

            post {
                goodScaledBitmaps = goodBitmaps
                badScaledBitmaps = badBitmaps
                bottomScaledBitmaps = bottomBitmaps
                amelieBitmap = bottomScaledBitmaps.random()

                amelieY = height.toFloat() - amelieBitmap.height - amelieOffsetFromBottom

                repeat(numberOfGoodImages) {
                    fallingImages.add(
                        FallingImage(
                            x = Random.nextFloat() * 800f,
                            y = Random.nextFloat() * -1000f,
                            speed = Random.nextFloat() * 10f + 5f,
                            bitmap = goodScaledBitmaps.random(),
                            isGood = true
                        )
                    )
                }

                repeat(numberOfBadImages) {
                    fallingImages.add(
                        FallingImage(
                            x = Random.nextFloat() * 800f,
                            y = Random.nextFloat() * -1000f,
                            speed = Random.nextFloat() * 10f + 5f,
                            bitmap = badScaledBitmaps.random(),
                            isGood = false
                        )
                    )
                }

                imagesLoaded = true
                handler.post(updateRunnable)
                invalidate()
            }
        }.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)

        if (!imagesLoaded) {
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("Loading...", width / 2f, height / 2f, textPaint)
            return
        }

        for (image in fallingImages) {
            canvas.drawBitmap(image.bitmap, image.x, image.y, null)
        }

        canvas.drawBitmap(amelieBitmap, amelieX, amelieY, null)

        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Score: $score", 20f, 60f, textPaint)
        canvas.drawText("Lives: $lives", 20f, 120f, textPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(updateRunnable)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!imagesLoaded) return false

        if (event.action == MotionEvent.ACTION_MOVE || event.action == MotionEvent.ACTION_DOWN) {
            amelieX = (event.x - amelieBitmap.width / 2f).coerceIn(0f, width.toFloat() - amelieBitmap.width)
            invalidate()
        }
        return true
    }

    private fun checkCollision(image: FallingImage): Boolean {
        val imageRect = RectF(image.x, image.y, image.x + image.bitmap.width, image.y + image.bitmap.height)
        val amelieRect = RectF(amelieX, amelieY, amelieX + amelieBitmap.width, amelieY + amelieBitmap.height)
        return RectF.intersects(imageRect, amelieRect)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (imagesLoaded) {
            amelieY = h.toFloat() - amelieBitmap.height - amelieOffsetFromBottom
        }
    }

}
