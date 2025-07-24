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
    private var hearts = 5
    private var food = 20
    private var coins = 100
    private var highScore = 0

    // Farm areas
    private val healingFenceArea = RectF(50f, 300f, 200f, 450f) // Primary healing fence area
    private val healingFenceArea2 = RectF(50f, 470f, 200f, 620f) // Secondary healing fence area
    private val healingFenceArea3 = RectF(220f, 300f, 370f, 450f) // Third healing fence area
    private val healingFenceArea4 = RectF(220f, 470f, 370f, 620f) // Fourth healing fence area
    private val cropArea = RectF(250f, 100f, 600f, 250f) // Crop growing area

    // Game settings
    private val maxAnimals = 8
    private val cropGridRows = 2
    private val cropGridCols = 4
    private var maxCrops = 0 // Start with no crops, must buy them
    private val cropGrowthTime = 8000L // 8 seconds to grow fully
    private val healingTime = 5000L // 5 seconds to heal
    private var lastCropPlant = System.currentTimeMillis()
    
    // Upgrades system
    private var healingFenceCapacity = 0 // Start with no healing fence
    private var coinEarningRate = 1.0f // Coins per second multiplier
    private var lastCoinEarning = System.currentTimeMillis()
    
    // Grid for crop positions
    private val cropGrid = Array(cropGridRows) { Array(cropGridCols) { false } }
    
    // Heart animation system
    data class FloatingHeart(
        var x: Float,
        var y: Float,
        var startTime: Long,
        val duration: Long = 1500L
    )
    private val floatingHearts = mutableListOf<FloatingHeart>()

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
    private var heartsBitmap: Bitmap? = null
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
            
            // Load hearts image
            val heartsOriginal = BitmapFactory.decodeResource(resources, R.drawable.hearts)
            val hearts = Bitmap.createScaledBitmap(heartsOriginal, 60, 60, true)

            post {
                animalBitmaps = bitmapMap
                wheatUngrownBitmap = wheatUngrown
                wheatGrownBitmap = wheatGrown
                heartsBitmap = hearts
                imagesLoaded = true
                spawnInitialAnimals()
                handler.post(gameLoop)
                invalidate()
            }
        }.start()
    }

    private fun spawnInitialAnimals() {
        // Start with no animals - must buy them
    }

    private fun plantInitialCrops() {
        // Start with no crops - player must buy them
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
        
        // Plant new crops periodically (only if maxCrops > 0)
        if (maxCrops > 0 && currentTime - lastCropPlant > 6000L && crops.size < maxCrops) {
            plantCrop()
            lastCropPlant = currentTime
        }
        
        // Earn coins from healthy animals
        if (currentTime - lastCoinEarning > 1000L) { // Every second
            val happyAnimals = animals.count { it.state == AnimalState.HAPPY }
            coins += (happyAnimals * coinEarningRate).toInt()
            lastCoinEarning = currentTime
        }
        
        // Update floating hearts animation
        floatingHearts.removeAll { heart ->
            currentTime - heart.startTime > heart.duration
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
        
        // Draw floating hearts
        drawFloatingHearts(canvas)

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
        
        // Draw healing fence areas based on capacity
        val fencePostPaint = Paint().apply {
            color = Color.rgb(139, 69, 19)
            style = Paint.Style.FILL
        }
        
        val fenceAreas = listOf(healingFenceArea, healingFenceArea2, healingFenceArea3, healingFenceArea4)
        
        for (i in 0 until healingFenceCapacity.coerceAtMost(4)) {
            val fence = fenceAreas[i]
            // Draw fence outline
            canvas.drawRect(fence, fencePaint)
            
            // Draw fence posts
            canvas.drawRect(fence.left - 5f, fence.top - 5f, 
                           fence.left + 5f, fence.bottom + 5f, fencePostPaint)
            canvas.drawRect(fence.right - 5f, fence.top - 5f, 
                           fence.right + 5f, fence.bottom + 5f, fencePostPaint)
        }
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
        canvas.drawText("Coins: $coins", margin, margin + lineHeight * 4, textPaint)
        canvas.drawText("High Score: $highScore", width - 300f, margin + lineHeight, textPaint)
        
        // Upgrades info
        textPaint.textSize = 30f
        canvas.drawText("Crops: ${crops.size}/$maxCrops", width - 250f, margin + lineHeight * 2, textPaint)
        canvas.drawText("Fence: ${animals.count { it.state == AnimalState.HEALING }}/$healingFenceCapacity", width - 250f, margin + lineHeight * 3, textPaint)
        
        // Upgrade buttons
        drawUpgradeButtons(canvas)
        
        // Instructions
        textPaint.textSize = 24f
        canvas.drawText("Buy animals & crops with upgrades!", margin, height - 120f, textPaint)
        canvas.drawText("Tap ready crops to harvest!", margin, height - 95f, textPaint)
        canvas.drawText("Drag sick animals to fence to heal!", margin, height - 70f, textPaint)
        canvas.drawText("Tap animals to rescue/care for them!", margin, height - 45f, textPaint)
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

                // Check if touch hits any upgrade button
                if (handleUpgradeButtonTouch(touchX, touchY)) {
                    return true
                }

                // Check if touch hits any animal
                for (animal in animals) {
                    if (animal.getBounds().contains(touchX, touchY)) {
                        if (animal.state == AnimalState.SICK) {
                            // Start dragging sick animal
                            draggedAnimal = animal
                        } else if (animal.state == AnimalState.HAPPY && isAnimalInAnyFence(animal)) {
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
                    if (isAnimalInAnyFence(animal)) {
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
                // Give bonus resources and show heart animation (costs food)
                if (food >= 1) {
                    food -= 1
                    hearts += 1
                    score += 2
                    addFloatingHeart(animal.x + animal.size / 2, animal.y)
                }
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
        } while (isAnimalInAnyFence(animal))
        
        score += 3
        
        // Check for new high score
        if (score > highScore) {
            highScore = score
            saveHighScore()
        }
    }

    private fun startHealing(animal: FarmAnimal) {
        val fenceAreas = listOf(healingFenceArea, healingFenceArea2, healingFenceArea3, healingFenceArea4)
        
        // Check if fence has capacity
        val animalsInFence = animals.count { it.state == AnimalState.HEALING }
        
        if (animalsInFence >= healingFenceCapacity) {
            return // Fence is full
        }
        
        // Find an available fence area
        var targetFence: RectF? = null
        for (i in 0 until healingFenceCapacity.coerceAtMost(4)) {
            val fence = fenceAreas[i]
            val animalInThisFence = animals.any { 
                it.state == AnimalState.HEALING && 
                fence.contains(it.x + it.size / 2, it.y + it.size / 2) 
            }
            if (!animalInThisFence) {
                targetFence = fence
                break
            }
        }
        
        if (targetFence == null) return // No available fence
        
        animal.state = AnimalState.HEALING
        animal.healingStartTime = System.currentTimeMillis()
        score += 12
        
        // Position animal in center of the available healing area
        animal.x = targetFence.centerX() - animal.size / 2
        animal.y = targetFence.centerY() - animal.size / 2
        
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
    
    private fun addFloatingHeart(x: Float, y: Float) {
        floatingHearts.add(FloatingHeart(x, y, System.currentTimeMillis()))
    }
    
    private fun drawFloatingHearts(canvas: Canvas) {
        val currentTime = System.currentTimeMillis()
        for (heart in floatingHearts) {
            val elapsed = currentTime - heart.startTime
            val progress = elapsed.toFloat() / heart.duration
            if (progress <= 1f) {
                val alpha = (255 * (1f - progress)).toInt().coerceIn(0, 255)
                val yOffset = progress * 100f // Float upward
                
                val paint = Paint().apply {
                    this.alpha = alpha
                }
                
                heartsBitmap?.let { bitmap ->
                    canvas.drawBitmap(bitmap, heart.x - 30f, heart.y - yOffset, paint)
                }
            }
        }
    }
    
    // Upgrade system
    data class Upgrade(
        val name: String,
        val description: String,
        val cost: Int,
        val maxLevel: Int,
        var currentLevel: Int = 0
    )
    
    private val upgrades = mutableMapOf(
        "crop_slot" to Upgrade("Add Crop Slot", "Adds 1 more crop slot", 50, 8),
        "fence_capacity" to Upgrade("Expand Fence", "Heal +1 more animal", 100, 4),
        "coin_rate" to Upgrade("Coin Boost", "Earn coins 25% faster", 75, 5),
        "buy_animal" to Upgrade("Buy Animal", "Purchase a new animal", 30, 999),
        "crop_growth" to Upgrade("Fast Growth", "Crops grow 20% faster", 90, 4)
    )
    
    private fun drawUpgradeButtons(canvas: Canvas) {
        val buttonWidth = 140f
        val buttonHeight = 80f
        val margin = 10f
        val startX = width - 160f
        val startY = height - 750f
        
        val buttonPaint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        
        val borderPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.WHITE
            isAntiAlias = true
        }
        
        textPaint.textSize = 22f
        var yOffset = 0f
        
        for ((key, upgrade) in upgrades) {
            if (upgrade.currentLevel >= upgrade.maxLevel) continue
            
            val cost = upgrade.cost * (upgrade.currentLevel + 1)
            val canAfford = coins >= cost
            
            buttonPaint.color = if (canAfford) Color.rgb(0, 150, 0) else Color.rgb(150, 0, 0)
            
            val buttonRect = RectF(
                startX, startY + yOffset,
                startX + buttonWidth, startY + yOffset + buttonHeight
            )
            
            canvas.drawRect(buttonRect, buttonPaint)
            canvas.drawRect(buttonRect, borderPaint)
            
            // Text
            canvas.drawText(upgrade.name, startX + 5f, startY + yOffset + 25f, textPaint)
            canvas.drawText("$${cost}", startX + 5f, startY + yOffset + 50f, textPaint)
            canvas.drawText("Lv ${upgrade.currentLevel}", startX + 5f, startY + yOffset + 75f, textPaint)
            
            yOffset += buttonHeight + margin
        }
        
        textPaint.textSize = 40f
    }
    
    private fun handleUpgradeButtonTouch(touchX: Float, touchY: Float): Boolean {
        val buttonWidth = 140f
        val buttonHeight = 80f
        val margin = 10f
        val startX = width - 160f
        val startY = height - 750f
        
        var yOffset = 0f
        
        for ((key, upgrade) in upgrades) {
            if (upgrade.currentLevel >= upgrade.maxLevel) continue
            
            val cost = upgrade.cost * (upgrade.currentLevel + 1)
            
            val buttonRect = RectF(
                startX, startY + yOffset,
                startX + buttonWidth, startY + yOffset + buttonHeight
            )
            
            if (buttonRect.contains(touchX, touchY) && coins >= cost) {
                purchaseUpgrade(key, cost)
                return true
            }
            
            yOffset += buttonHeight + margin
        }
        
        return false
    }
    
    private fun purchaseUpgrade(upgradeKey: String, cost: Int) {
        val upgrade = upgrades[upgradeKey] ?: return
        
        coins -= cost
        upgrade.currentLevel++
        
        when (upgradeKey) {
            "crop_slot" -> {
                maxCrops++
            }
            "fence_capacity" -> {
                healingFenceCapacity++
            }
            "coin_rate" -> {
                coinEarningRate += 0.25f
            }
            "buy_animal" -> {
                purchaseAnimal()
            }
            "crop_growth" -> {
                // Reduce crop growth time by 20% each level
                // This would require modifying the growth logic
            }
        }
    }

    private fun purchaseAnimal() {
        if (animals.size >= maxAnimals) return
        
        val animalType = AnimalType.values().random()
        val bitmap = animalBitmaps[animalType] ?: return
        
        // Find a safe spawn position (avoid fence, crop areas, corners, and UI elements)
        var x: Float
        var y: Float
        var attempts = 0
        do {
            // More restrictive spawn area: avoid corners, UI areas, and upgrade buttons
            x = Random.nextFloat() * (width - 320f).coerceAtLeast(150f) + 150f
            y = Random.nextFloat() * (height - 650f).coerceAtLeast(300f) + 300f
            attempts++
        } while ((isPositionInAnyFence(x + 60f, y + 60f) || 
                 cropArea.contains(x + 60f, y + 60f)) && attempts < 10)
        
        val animal = FarmAnimal(
            x = x,
            y = y,
            type = animalType,
            state = AnimalState.DISTRESSED,
            bitmap = bitmap
        )
        
        animals.add(animal)
    }
    
    private fun isAnimalInAnyFence(animal: FarmAnimal): Boolean {
        val fenceAreas = listOf(healingFenceArea, healingFenceArea2, healingFenceArea3, healingFenceArea4)
        return fenceAreas.take(healingFenceCapacity.coerceAtMost(4)).any { fence ->
            fence.contains(animal.x + animal.size / 2, animal.y + animal.size / 2)
        }
    }
    
    private fun isPositionInAnyFence(x: Float, y: Float): Boolean {
        val fenceAreas = listOf(healingFenceArea, healingFenceArea2, healingFenceArea3, healingFenceArea4)
        return fenceAreas.take(healingFenceCapacity.coerceAtMost(4)).any { fence ->
            fence.contains(x, y)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        gameRunning = false
        handler.removeCallbacks(gameLoop)
    }

    fun restartGame() {
        score = 0
        hearts = 5
        food = 20
        coins = 100
        animals.clear()
        crops.clear()
        floatingHearts.clear()
        draggedAnimal = null
        
        // Reset upgrades
        maxCrops = 0
        healingFenceCapacity = 0
        coinEarningRate = 1.0f
        for (upgrade in upgrades.values) {
            upgrade.currentLevel = 0
        }
        
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