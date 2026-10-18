package com.devdgames.hollowknightbutmine.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.random.Random

/**
 * Dust / Spark / Hit particle
 */
data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var radius: Float,
    var color: Int,
    var life: Float,
    var maxLife: Float
) {
    fun update(dt: Float) {
        x += vx * dt
        y += vy * dt
        life -= dt
    }

    fun isDead() = life <= 0f
}

/**
 * GameWorld manages map platforms, player, enemies, camera, combat feedback, and particles.
 */
class GameWorld {
    val player = Player(x = 180f, y = 420f)
    val enemies = mutableListOf<Enemy>()
    val platforms = mutableListOf<RectF>()
    val particles = mutableListOf<Particle>()

    // Camera viewport offset
    var cameraX = 0f
    var cameraY = 0f

    // Screen Shake for heavy hits (Headbutt / Parry)
    var screenShakeTime = 0f
    var screenShakeIntensity = 0f

    // Combat banner message ("PARRIED!", "HEADBUTT!", etc.)
    var combatMessage = ""
    var combatMessageTimer = 0f
    var combatMessageColor = Color.WHITE

    init {
        setupRoom()
    }

    private fun setupRoom() {
        // Main Ground
        platforms.add(RectF(-200f, 480f, 1600f, 600f))

        // Left & Right boundaries
        platforms.add(RectF(-200f, -400f, -100f, 600f))
        platforms.add(RectF(1500f, -400f, 1600f, 600f))

        // Stepping platforms for jumping / pogoing / high ground
        platforms.add(RectF(250f, 370f, 420f, 395f))
        platforms.add(RectF(500f, 290f, 670f, 315f))
        platforms.add(RectF(750f, 220f, 950f, 245f))
        platforms.add(RectF(320f, 180f, 480f, 205f))

        // Elevated platform with crawling bug
        platforms.add(RectF(1020f, 380f, 1260f, 405f))

        // Spawn test enemies
        spawnEnemies()
    }

    fun reset() {
        enemies.clear()
        particles.clear()
        player.x = 180f
        player.y = 420f
        player.vx = 0f
        player.vy = 0f
        player.currentMasks = player.maxMasks
        player.soul = 33
        player.state = PlayerState.IDLE
        player.grabbedEnemy = null
        spawnEnemies()
    }

    private fun spawnEnemies() {
        // 1. Ground crawler beetle (good for testing stab, grab, headbutt, and parry)
        enemies.add(Enemy(x = 520f, y = 480f, width = 44f, height = 38f))

        // 2. High platform beetle
        enemies.add(Enemy(x = 1140f, y = 380f, width = 44f, height = 38f))

        // 3. Mini-boss husk on far right (high HP to test headbutt damage reduction)
        enemies.add(Enemy(x = 1350f, y = 480f, width = 56f, height = 62f, isBoss = true))
    }

    fun triggerShake(intensity: Float, duration: Float) {
        screenShakeIntensity = intensity
        screenShakeTime = duration
    }

    fun showCombatText(text: String, color: Int) {
        combatMessage = text
        combatMessageTimer = 1.0f
        combatMessageColor = color
    }

    fun spawnSparks(x: Float, y: Float, count: Int, color: Int) {
        for (i in 0 until count) {
            val angle = Random.nextFloat() * 6.28f
            val speed = Random.nextFloat() * 220f + 50f
            val life = Random.nextFloat() * 0.25f + 0.15f
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = kotlin.math.cos(angle) * speed,
                    vy = kotlin.math.sin(angle) * speed,
                    radius = Random.nextFloat() * 3.5f + 2f,
                    color = color,
                    life = life,
                    maxLife = life
                )
            )
        }
    }

    fun update(dt: Float, inputX: Float, screenWidth: Float, screenHeight: Float) {
        // Update player
        val prevHeadbutt = player.state == PlayerState.HEADBUTTING
        val prevParry = player.parrySuccess

        player.update(
            dt = dt,
            inputX = inputX,
            platforms = platforms,
            enemies = enemies,
            onParryTriggered = {
                triggerShake(9f, 0.25f)
                showCombatText("⚔️ PARRIED! [STUNNED]", Color.rgb(255, 230, 80))
                spawnSparks(player.x + 20f, player.y - player.height / 2, 16, Color.YELLOW)
            }
        )

        // Detect headbutt execution trigger
        if (!prevHeadbutt && player.state == PlayerState.HEADBUTTING) {
            triggerShake(12f, 0.3f)
            showCombatText("💥 HEADBUTT SMASH!", Color.rgb(255, 100, 80))
            spawnSparks(player.x + 30f, player.y - player.height / 2, 22, Color.rgb(255, 120, 40))
        }

        // Update enemies
        for (enemy in enemies) {
            enemy.update(dt, player.x, player.y, platforms)
        }

        // Update particles
        val pIter = particles.iterator()
        while (pIter.hasNext()) {
            val p = pIter.next()
            p.update(dt)
            if (p.isDead()) {
                pIter.remove()
            }
        }

        // Screen shake decay
        if (screenShakeTime > 0f) {
            screenShakeTime -= dt
        }

        // Combat message timer
        if (combatMessageTimer > 0f) {
            combatMessageTimer -= dt
        }

        // Smooth Camera Follow Player
        val targetCamX = player.x - screenWidth / 2f
        val targetCamY = player.y - screenHeight * 0.62f
        cameraX += (targetCamX - cameraX) * 8f * dt
        cameraY += (targetCamY - cameraY) * 8f * dt
    }

    fun draw(canvas: Canvas, paint: Paint, screenWidth: Float, screenHeight: Float) {
        canvas.save()

        // Apply screen shake
        var shakeOffsetX = 0f
        var shakeOffsetY = 0f
        if (screenShakeTime > 0f) {
            shakeOffsetX = (Random.nextFloat() - 0.5f) * screenShakeIntensity * 2f
            shakeOffsetY = (Random.nextFloat() - 0.5f) * screenShakeIntensity * 2f
        }

        canvas.translate(-cameraX + shakeOffsetX, -cameraY + shakeOffsetY)

        // 1. Draw Hallownest background ambiance (deep caverns / forgotten crossroads vibe)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(12, 14, 22)
        canvas.drawRect(cameraX - 100f, cameraY - 100f, cameraX + screenWidth + 100f, cameraY + screenHeight + 100f, paint)

        // Distant background arches / pillars
        paint.color = Color.rgb(20, 24, 36)
        for (i in 0..6) {
            val px = i * 280f - 100f
            canvas.drawRect(px, 100f, px + 80f, 500f, paint)
        }

        // 2. Draw Platforms (Dark slate ruins)
        paint.color = Color.rgb(28, 34, 48)
        paint.style = Paint.Style.FILL
        for (plat in platforms) {
            canvas.drawRoundRect(plat, 8f, 8f, paint)
        }

        // Platform glowing moss / highlights on top surface
        paint.color = Color.rgb(55, 75, 95)
        paint.strokeWidth = 3f
        paint.style = Paint.Style.STROKE
        for (plat in platforms) {
            canvas.drawLine(plat.left + 4f, plat.top + 1.5f, plat.right - 4f, plat.top + 1.5f, paint)
        }

        // 3. Draw Enemies
        for (enemy in enemies) {
            enemy.draw(canvas, paint)
        }

        // 4. Draw Player
        player.draw(canvas, paint)

        // 5. Draw Particles
        paint.style = Paint.Style.FILL
        for (p in particles) {
            paint.color = p.color
            canvas.drawCircle(p.x, p.y, p.radius, paint)
        }

        canvas.restore()

        // 6. Draw HUD (Masks + Soul Vessel + Combat Banners) on UI layer
        drawHUD(canvas, paint, screenWidth, screenHeight)
    }

    private fun drawHUD(canvas: Canvas, paint: Paint, screenWidth: Float, screenHeight: Float) {
        val startX = 50f
        val startY = 70f

        // Soul Vessel Circle
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(20, 24, 35)
        canvas.drawCircle(startX, startY + 10f, 32f, paint)

        // Soul Liquid fill inside vessel
        val soulPct = (player.soul.toFloat() / player.maxSoul).coerceIn(0f, 1f)
        if (soulPct > 0f) {
            paint.color = Color.rgb(230, 245, 255)
            canvas.drawCircle(startX, startY + 10f, 26f * soulPct, paint)
        }

        // Vessel border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = Color.rgb(180, 200, 220)
        canvas.drawCircle(startX, startY + 10f, 32f, paint)

        // Masks (HP)
        val maskSpacing = 36f
        val maskStartX = startX + 50f
        paint.style = Paint.Style.FILL
        for (i in 0 until player.maxMasks) {
            val mx = maskStartX + i * maskSpacing
            val my = startY + 10f

            if (i < player.currentMasks) {
                // Healthy Mask (white knight head shape)
                paint.color = Color.rgb(240, 245, 255)
                canvas.drawOval(mx - 12f, my - 14f, mx + 12f, my + 14f, paint)

                // Mask eye holes
                paint.color = Color.rgb(20, 20, 28)
                canvas.drawCircle(mx - 4f, my, 3f, paint)
                canvas.drawCircle(mx + 4f, my, 3f, paint)
            } else {
                // Broken Mask
                paint.color = Color.rgb(55, 60, 72)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.5f
                canvas.drawOval(mx - 12f, my - 14f, mx + 12f, my + 14f, paint)
                paint.style = Paint.Style.FILL
            }
        }

        // Safety Pin Weapon Status Indicator
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(200, 215, 235)
        paint.textSize = 28f
        paint.isFakeBoldText = true
        val weaponName = when (player.state) {
            PlayerState.HOLDING_ENEMY -> "🧷 GRABBED! Press HEADBUTT (💥)"
            PlayerState.PARRYING -> "🛡️ PARRYING!"
            PlayerState.HEALING -> "✨ FOCUSING SOUL..."
            else -> "🧷 Safety Pin Equipped"
        }
        canvas.drawText(weaponName, startX, startY + 70f, paint)

        // Combat Text Popup (e.g. "PARRIED!", "HEADBUTT SMASH!")
        if (combatMessageTimer > 0f) {
            paint.color = combatMessageColor
            paint.textSize = 42f
            val alpha = (combatMessageTimer.coerceAtMost(1f) * 255).toInt()
            paint.alpha = alpha
            val textWidth = paint.measureText(combatMessage)
            canvas.drawText(combatMessage, (screenWidth - textWidth) / 2f, 160f, paint)
            paint.alpha = 255
        }
    }
}
