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
        SICK,        // Purple - needs healing in fence
        HEALING      // Blue - currently healing in fence
    }

    enum class CropState {
        PLANTED,     // Small seedling
        GROWING,     // Medium plant
        READY        // Full grown, ready to harvest
    }

    data class Crop(
        var x: Float,
        var y: Float,
        var state: CropState,
        var plantedTime: Long = System.currentTimeMillis(),
        val size: Float = 80f,
        val gridRow: Int,
        val gridCol: Int
    ) {
        fun getBounds(): RectF {
            return RectF(x, y, x + size, y + size)
        }
    }

    data class FarmAnimal(
        var x: Float,
        var y: Float,
        val type: AnimalType,
        var state: AnimalState,
        var bitmap: Bitmap,
        var lastCareTime: Long = System.currentTimeMillis(),
        var stateTimer: Long = 0,
        var healingStartTime: Long = 0,
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
    private val crops = mutableListOf<Crop>()
    private var score = 0
    private var hearts = 50
    private var food = 20
    private var coins = 100
    private var highScore = 0

    // Farm areas
    private val healingFenceArea = RectF(50f, 300f, 200f, 450f) // Healing fence area
    private val cropArea = RectF(250f, 100f, 600f, 250f) // Crop growing area

    // Game settings
    private val maxAnimals = 8
    private val cropGridRows = 2
    private val cropGridCols = 4
    private val maxCrops = cropGridRows * cropGridCols // 2x4 = 8 max crops
    private val animalSpawnRate = 5000L // 5 seconds
    private val cropGrowthTime = 8000L // 8 seconds to grow fully
    private val healingTime = 5000L // 5 seconds to heal
    private var lastSpawnTime = System.currentTimeMillis()
    private var lastCropPlant = System.currentTimeMillis()
    
    // Grid for crop positions
    private val cropGrid = Array(cropGridRows) { Array(cropGridCols) { false } }

    // Animal images
    private val animalImageMap = mapOf(
        AnimalType.COW to R.drawable.cow,
        AnimalType.HORSE to R.drawable.horse,
        AnimalType.PIG to R.drawable.pig,
        AnimalType.CHICKEN to R.drawable.chicken,
        AnimalType.SHEEP to R.drawable.sheep
    )

    private var animalBitmaps: Map<AnimalType, Bitmap> = emptyMap()
    private var wheatUngrownBitmap: Bitmap? = null
    private var wheatGrownBitmap: Bitmap? = null
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

    private val fencePaint = Paint().apply {
        color = Color.rgb(139, 69, 19) // Brown fence
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private val cropAreaPaint = Paint().apply {
        color = Color.rgb(101, 67, 33) // Brown soil
        style = Paint.Style.FILL
    }

    private val cropPaint = Paint().apply {
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
        plantInitialCrops()
    }

    private fun loadAnimalImages() {
        Thread {
            val bitmapMap = mutableMapOf<AnimalType, Bitmap>()
            for ((animalType, resourceId) in animalImageMap) {
                val bitmap = BitmapFactory.decodeResource(resources, resourceId)
                bitmapMap[animalType] = Bitmap.createScaledBitmap(bitmap, 120, 120, true)
            }
            
            // Load wheat images
            val wheatUngrownOriginal = BitmapFactory.decodeResource(resources, R.drawable.wheat_ungrown)
            val wheatGrownOriginal = BitmapFactory.decodeResource(resources, R.drawable.wheat_grown)
            val wheatUngrown = Bitmap.createScaledBitmap(wheatUngrownOriginal, 80, 80, true)
            val wheatGrown = Bitmap.createScaledBitmap(wheatGrownOriginal, 80, 80, true)

            post {
                animalBitmaps = bitmapMap
                wheatUngrownBitmap = wheatUngrown
                wheatGrownBitmap = wheatGrown
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

    private fun plantInitialCrops() {
        repeat(4) {
            plantCrop()
        }
    }

    private fun plantCrop() {
        if (crops.size >= maxCrops) return

        // Find an empty grid spot
        for (row in 0 until cropGridRows) {
            for (col in 0 until cropGridCols) {
                if (!cropGrid[row][col]) {
                    // Calculate position in grid
                    val cellWidth = cropArea.width() / cropGridCols
                    val cellHeight = cropArea.height() / cropGridRows
                    val x = cropArea.left + (col * cellWidth) + (cellWidth - 80f) / 2
                    val y = cropArea.top + (row * cellHeight) + (cellHeight - 80f) / 2
                    
                    cropGrid[row][col] = true
                    crops.add(Crop(x, y, CropState.PLANTED, System.currentTimeMillis(), 80f, row, col))
                    return
                }
            }
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

        // Plant new crops periodically
        if (currentTime - lastCropPlant > 6000L && crops.size < maxCrops) {
            plantCrop()
            lastCropPlant = currentTime
        }

        // Update animal states
        for (animal in animals) {
            updateAnimalState(animal, currentTime)
        }

        // Update crop growth
        for (crop in crops) {
            updateCropGrowth(crop, currentTime)
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
                // Sick animals need to be moved to healing fence
            }
            AnimalState.HEALING -> {
                // Check if healing is complete
                if (currentTime - animal.healingStartTime > healingTime) {
                    animal.state = AnimalState.HAPPY
                    animal.lastCareTime = currentTime
                }
            }
            AnimalState.DISTRESSED -> {
                // Distressed animals stay distressed until rescued
            }
        }
    }

    private fun updateCropGrowth(crop: Crop, currentTime: Long) {
        val growthTime = currentTime - crop.plantedTime
        
        when (crop.state) {
            CropState.PLANTED -> {
                if (growthTime > cropGrowthTime / 3) {
                    crop.state = CropState.GROWING
                }
            }
            CropState.GROWING -> {
                if (growthTime > cropGrowthTime) {
                    crop.state = CropState.READY
                }
            }
            CropState.READY -> {
                // Ready for harvest, no state change
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

        // Draw farm areas
        drawFarmAreas(canvas)

        // Draw crops
        for (crop in crops) {
            drawCrop(canvas, crop)
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
            AnimalState.HEALING -> Color.BLUE
        }
        
        canvas.drawCircle(indicatorX, indicatorY, radius, statePaint)
    }

    private fun drawFarmAreas(canvas: Canvas) {
        // Draw crop area (soil)
        canvas.drawRect(cropArea, cropAreaPaint)
        
        // Draw healing fence area
        canvas.drawRect(healingFenceArea, fencePaint)
        
        // Draw fence posts
        val fencePostPaint = Paint().apply {
            color = Color.rgb(139, 69, 19)
            style = Paint.Style.FILL
        }
        canvas.drawRect(healingFenceArea.left - 5f, healingFenceArea.top - 5f, 
                       healingFenceArea.left + 5f, healingFenceArea.bottom + 5f, fencePostPaint)
        canvas.drawRect(healingFenceArea.right - 5f, healingFenceArea.top - 5f, 
                       healingFenceArea.right + 5f, healingFenceArea.bottom + 5f, fencePostPaint)
    }

    private fun drawCrop(canvas: Canvas, crop: Crop) {
        when (crop.state) {
            CropState.PLANTED, CropState.GROWING -> {
                // Draw ungrown wheat
                wheatUngrownBitmap?.let { bitmap ->
                    canvas.drawBitmap(bitmap, crop.x, crop.y, null)
                }
            }
            CropState.READY -> {
                // Draw grown wheat
                wheatGrownBitmap?.let { bitmap ->
                    canvas.drawBitmap(bitmap, crop.x, crop.y, null)
                }
                // Add golden outline to show it's ready to harvest
                val outlinePaint = Paint().apply {
                    color = Color.YELLOW
                    style = Paint.Style.STROKE
                    strokeWidth = 4f
                }
                canvas.drawRect(crop.x, crop.y, crop.x + crop.size, crop.y + crop.size, outlinePaint)
            }
        }
    }

    private fun drawUI(canvas: Canvas) {
        val margin = 20f
        val lineHeight = 50f
        
        // Resource counters
        canvas.drawText("Score: $score", margin, margin + lineHeight, textPaint)
        canvas.drawText("Hearts: $hearts", margin, margin + lineHeight * 2, textPaint)
        canvas.drawText("Food: $food", margin, margin + lineHeight * 3, textPaint)
        canvas.drawText("High Score: $highScore", width - 300f, margin + lineHeight, textPaint)
        
        // Instructions
        textPaint.textSize = 28f
        canvas.drawText("Tap ready crops to harvest!", margin, height - 100f, textPaint)
        canvas.drawText("Drag sick animals to fence to heal!", margin, height - 70f, textPaint)
        canvas.drawText("Tap animals to rescue/care for them!", margin, height - 40f, textPaint)
        textPaint.textSize = 40f
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!imagesLoaded) return false

        val touchX = event.x
        val touchY = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Check if touch hits any crop
                for (crop in crops) {
                    if (crop.getBounds().contains(touchX, touchY) && crop.state == CropState.READY) {
                        harvestCrop(crop)
                        return true
                    }
                }

                // Check if touch hits any animal
                for (animal in animals) {
                    if (animal.getBounds().contains(touchX, touchY)) {
                        if (animal.state == AnimalState.SICK) {
                            // Start dragging sick animal
                            draggedAnimal = animal
                        } else if (animal.state == AnimalState.HAPPY && healingFenceArea.contains(animal.x + animal.size / 2, animal.y + animal.size / 2)) {
                            // Remove healed animal from fence
                            removeAnimalFromFence(animal)
                        } else {
                            rescueAnimal(animal)
                        }
                        return true
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                // Handle dragging sick animals
                draggedAnimal?.let { animal ->
                    animal.x = touchX - animal.size / 2
                    animal.y = touchY - animal.size / 2
                }
            }
            MotionEvent.ACTION_UP -> {
                // Check if sick animal was dropped in healing fence
                draggedAnimal?.let { animal ->
                    if (healingFenceArea.contains(animal.x + animal.size / 2, animal.y + animal.size / 2)) {
                        startHealing(animal)
                    }
                    draggedAnimal = null
                }
            }
        }
        
        return true
    }

    private var draggedAnimal: FarmAnimal? = null

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
                // Sick animals need to be dragged to fence, not tapped
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
                score += 2
            }
            AnimalState.HEALING -> {
                // Animals healing cannot be interacted with
            }
        }
        
        // Check for new high score
        if (score > highScore) {
            highScore = score
            saveHighScore()
        }
    }

    private fun harvestCrop(crop: Crop) {
        food += 3
        score += 8
        
        // Clear the grid spot
        cropGrid[crop.gridRow][crop.gridCol] = false
        crops.remove(crop)
        
        // Check for new high score
        if (score > highScore) {
            highScore = score
            saveHighScore()
        }
        
        // Plant a new crop after a short delay
        handler.postDelayed({
            if (crops.size < maxCrops) {
                plantCrop()
            }
        }, 2000)
    }

    private fun removeAnimalFromFence(animal: FarmAnimal) {
        // Move animal to a random position outside the fence
        do {
            animal.x = Random.nextFloat() * (width - 120f).coerceAtLeast(0f)
            animal.y = Random.nextFloat() * (height - 300f).coerceAtLeast(200f) + 200f
        } while (healingFenceArea.contains(animal.x + animal.size / 2, animal.y + animal.size / 2))
        
        score += 3
        
        // Check for new high score
        if (score > highScore) {
            highScore = score
            saveHighScore()
        }
    }

    private fun startHealing(animal: FarmAnimal) {
        animal.state = AnimalState.HEALING
        animal.healingStartTime = System.currentTimeMillis()
        score += 12
        
        // Position animal in center of healing area
        animal.x = healingFenceArea.centerX() - animal.size / 2
        animal.y = healingFenceArea.centerY() - animal.size / 2
        
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
        animals.clear()
        crops.clear()
        draggedAnimal = null
        
        // Clear crop grid
        for (row in 0 until cropGridRows) {
            for (col in 0 until cropGridCols) {
                cropGrid[row][col] = false
            }
        }
        
        gameRunning = true
        spawnInitialAnimals()
        plantInitialCrops()
        handler.post(gameLoop)
    }
}