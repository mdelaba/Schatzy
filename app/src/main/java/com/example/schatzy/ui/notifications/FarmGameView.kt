package com.example.schatzy.ui.notifications

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import com.example.schatzy.R
import kotlin.random.Random

class FarmGameView(context: Context) : View(context) {

    enum class AnimalType(val displayName: String) {
        COW("Cow"),
        HORSE("Horse"), 
        PIG("Pig"),
        CHICKEN("Chicken"),
        SHEEP("Sheep")
    }

    enum class AnimalState {
        DISTRESSED,  // Red - needs rescue
        HAPPY,       // Green - well cared for
        NEUTRAL,     // Yellow - needs attention
        SICK         // Purple - needs medicine
    }

    data class FarmAnimal(
        var x: Float,
        var y: Float,
        val type: AnimalType,
        var state: AnimalState,
        var bitmap: Bitmap,
        var lastCareTime: Long = System.currentTimeMillis(),
        var stateTimer: Long = 0,
        val size: Float = 120f
    ) {
        fun getBounds(): RectF {
            return RectF(x, y, x + size, y + size)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val frameRate = 16L
    private var gameRunning = true

    // Game state
    private val animals = mutableListOf<FarmAnimal>()
    private var score = 0
    private var hearts = 50
    private var food = 20
    private var medicine = 5
    private var coins = 100
    private var highScore = 0

    // Game settings
    private val maxAnimals = 8
    private val animalSpawnRate = 5000L // 5 seconds
    private var lastSpawnTime = System.currentTimeMillis()

    // Animal images
    private val animalImageMap = mapOf(
        AnimalType.COW to R.drawable.cow,
        AnimalType.HORSE to R.drawable.horse,
        AnimalType.PIG to R.drawable.pig,
        AnimalType.CHICKEN to R.drawable.chicken,
        AnimalType.SHEEP to R.drawable.sheep
    )

    private var animalBitmaps: Map<AnimalType, Bitmap> = emptyMap()
    private var imagesLoaded = false

    // Paint objects
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        isAntiAlias = true
        textAlign = Paint.Align.LEFT
    }

    private val backgroundPaint = Paint().apply {
        color = Color.rgb(34, 139, 34) // Forest green for farm field
    }

    private val statePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val gameLoop = object : Runnable {
        override fun run() {
            if (gameRunning) {
                updateGame()
                invalidate()
                handler.postDelayed(this, frameRate)
            }
        }
    }

    init {
        loadHighScore()
        loadAnimalImages()
    }

    private fun loadAnimalImages() {
        Thread {
            val bitmapMap = mutableMapOf<AnimalType, Bitmap>()
            for ((animalType, resourceId) in animalImageMap) {
                val bitmap = BitmapFactory.decodeResource(resources, resourceId)
                bitmapMap[animalType] = Bitmap.createScaledBitmap(bitmap, 120, 120, true)
            }

            post {
                animalBitmaps = bitmapMap
                imagesLoaded = true
                spawnInitialAnimals()
                handler.post(gameLoop)
                invalidate()
            }
        }.start()
    }

    private fun spawnInitialAnimals() {
        repeat(3) {
            spawnAnimal()
        }
    }

    private fun spawnAnimal() {
        if (animals.size >= maxAnimals) return

        val animalType = AnimalType.values().random()
        val bitmap = animalBitmaps[animalType] ?: return
        
        val x = Random.nextFloat() * (width - 120f).coerceAtLeast(0f)
        val y = Random.nextFloat() * (height - 200f).coerceAtLeast(100f) + 100f
        
        val animal = FarmAnimal(
            x = x,
            y = y,
            type = animalType,
            state = AnimalState.DISTRESSED,
            bitmap = bitmap
        )
        
        animals.add(animal)
    }

    private fun updateGame() {
        val currentTime = System.currentTimeMillis()
        
        // Spawn new animals periodically
        if (currentTime - lastSpawnTime > animalSpawnRate && animals.size < maxAnimals) {
            spawnAnimal()
            lastSpawnTime = currentTime
        }

        // Update animal states
        for (animal in animals) {
            updateAnimalState(animal, currentTime)
        }

        // Remove animals that have been distressed too long
        animals.removeAll { animal ->
            val timeSinceLastCare = currentTime - animal.lastCareTime
            animal.state == AnimalState.DISTRESSED && timeSinceLastCare > 15000 // 15 seconds
        }
    }

    private fun updateAnimalState(animal: FarmAnimal, currentTime: Long) {
        val timeSinceLastCare = currentTime - animal.lastCareTime
        
        when (animal.state) {
            AnimalState.HAPPY -> {
                if (timeSinceLastCare > 8000) { // 8 seconds
                    animal.state = AnimalState.NEUTRAL
                }
            }
            AnimalState.NEUTRAL -> {
                if (timeSinceLastCare > 12000) { // 12 seconds  
                    animal.state = if (Random.nextFloat() < 0.3f) AnimalState.SICK else AnimalState.DISTRESSED
                }
            }
            AnimalState.SICK -> {
                // Sick animals need medicine, don't change state automatically
            }
            AnimalState.DISTRESSED -> {
                // Distressed animals stay distressed until rescued
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw farm background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        
        if (!imagesLoaded) {
            canvas.drawText("Loading Farm...", width / 2f - 100f, height / 2f, textPaint)
            return
        }

        // Draw animals
        for (animal in animals) {
            drawAnimal(canvas, animal)
        }

        // Draw UI
        drawUI(canvas)
    }

    private fun drawAnimal(canvas: Canvas, animal: FarmAnimal) {
        // Draw animal bitmap
        canvas.drawBitmap(animal.bitmap, animal.x, animal.y, null)
        
        // Draw state indicator circle
        val indicatorX = animal.x + animal.size - 20f
        val indicatorY = animal.y + 10f
        val radius = 15f
        
        statePaint.color = when (animal.state) {
            AnimalState.DISTRESSED -> Color.RED
            AnimalState.HAPPY -> Color.GREEN
            AnimalState.NEUTRAL -> Color.YELLOW
            AnimalState.SICK -> Color.MAGENTA
        }
        
        canvas.drawCircle(indicatorX, indicatorY, radius, statePaint)
    }

    private fun drawUI(canvas: Canvas) {
        val margin = 20f
        val lineHeight = 50f
        
        // Resource counters
        canvas.drawText("Score: $score", margin, margin + lineHeight, textPaint)
        canvas.drawText("Hearts: $hearts", margin, margin + lineHeight * 2, textPaint)
        canvas.drawText("Food: $food", margin, margin + lineHeight * 3, textPaint)
        canvas.drawText("Medicine: $medicine", margin, margin + lineHeight * 4, textPaint)
        canvas.drawText("High Score: $highScore", width - 300f, margin + lineHeight, textPaint)
        
        // Instructions
        textPaint.textSize = 30f
        canvas.drawText("Tap animals to rescue them!", margin, height - 60f, textPaint)
        textPaint.textSize = 40f
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!imagesLoaded || event.action != MotionEvent.ACTION_DOWN) return false

        val touchX = event.x
        val touchY = event.y

        // Check if touch hits any animal
        for (animal in animals) {
            if (animal.getBounds().contains(touchX, touchY)) {
                rescueAnimal(animal)
                return true
            }
        }
        
        return true
    }

    private fun rescueAnimal(animal: FarmAnimal) {
        when (animal.state) {
            AnimalState.DISTRESSED -> {
                if (hearts >= 2) {
                    hearts -= 2
                    score += 10
                    animal.state = AnimalState.HAPPY
                    animal.lastCareTime = System.currentTimeMillis()
                }
            }
            AnimalState.SICK -> {
                if (medicine >= 1) {
                    medicine -= 1
                    score += 15
                    animal.state = AnimalState.HAPPY
                    animal.lastCareTime = System.currentTimeMillis()
                }
            }
            AnimalState.NEUTRAL -> {
                if (food >= 1) {
                    food -= 1
                    score += 5
                    animal.state = AnimalState.HAPPY
                    animal.lastCareTime = System.currentTimeMillis()
                }
            }
            AnimalState.HAPPY -> {
                // Give bonus resources
                hearts += 1
                food += 1
                score += 2
            }
        }
        
        // Check for new high score
        if (score > highScore) {
            highScore = score
            saveHighScore()
        }
    }

    private fun loadHighScore() {
        val sharedPrefs = context.getSharedPreferences("farm_game", Context.MODE_PRIVATE)
        highScore = sharedPrefs.getInt("high_score", 0)
    }

    private fun saveHighScore() {
        val sharedPrefs = context.getSharedPreferences("farm_game", Context.MODE_PRIVATE)
        sharedPrefs.edit().putInt("high_score", highScore).apply()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        gameRunning = false
        handler.removeCallbacks(gameLoop)
    }

    fun restartGame() {
        score = 0
        hearts = 50
        food = 20
        medicine = 5
        animals.clear()
        gameRunning = true
        spawnInitialAnimals()
        handler.post(gameLoop)
    }
}