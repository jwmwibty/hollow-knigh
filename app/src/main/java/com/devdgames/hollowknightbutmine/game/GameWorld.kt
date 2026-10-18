package com.devdgames.hollowknightbutmine.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.devdgames.hollowknightbutmine.game.env.DarkRibsRoom
import com.devdgames.hollowknightbutmine.game.env.MossyBridgeRoom
import com.devdgames.hollowknightbutmine.game.env.Room
import com.devdgames.hollowknightbutmine.game.env.RuinedSanctuaryRoom
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
 * GameWorld manages map rooms, platforms, player, enemies, camera, combat feedback, and particles.
 */
class GameWorld {
    val player = Player(x = 140f, y = 460f)
    val enemies = mutableListOf<Enemy>()
    val projectiles = mutableListOf<Projectile>()
    val platforms = mutableListOf<RectF>()
    val particles = mutableListOf<Particle>()

    // Room System
    private val rooms = mutableMapOf<String, Room>()
    var currentRoom: Room? = null
    var gameTime = 0f

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
        registerRooms()
        loadRoom("mossy_bridge", 140f, 460f)
    }

    private fun registerRooms() {
        val r1 = MossyBridgeRoom()
        r1.build()
        rooms[r1.id] = r1

        val r2 = DarkRibsRoom()
        r2.build()
        rooms[r2.id] = r2

        val r3 = RuinedSanctuaryRoom()
        r3.build()
        rooms[r3.id] = r3
    }

    fun loadRoom(roomId: String, targetX: Float, targetY: Float) {
        val room = rooms[roomId] ?: return
        currentRoom = room

        // Clear room entities
        enemies.clear()
        projectiles.clear()
        platforms.clear()

        // Sync platform collision
        platforms.addAll(room.platforms)

        // Position player
        player.x = targetX
        player.y = targetY
        player.vx = 0f
        player.vy = 0f

        // Spawn room enemies
        for (spawn in room.enemySpawns) {
            val enemyType = when {
                spawn.boss -> EnemyType.MOSS_CHARGER
                spawn.y < 250f -> EnemyType.FLYER
                spawn.x in 1100f..1400f && roomId == "mossy_bridge" -> EnemyType.SPITTER
                spawn.x in 1300f..1400f && roomId == "dark_ribs" -> EnemyType.SPITTER
                spawn.x in 550f..650f && roomId == "ruined_sanctuary" -> EnemyType.SPITTER
                spawn.x in 1600f..1700f && roomId == "mossy_bridge" -> EnemyType.SHIELDER
                spawn.x in 1000f..1100f && roomId == "dark_ribs" -> EnemyType.SHIELDER
                spawn.x in 1300f..1400f && roomId == "ruined_sanctuary" -> EnemyType.SHIELDER
                else -> EnemyType.CRAWLER
            }

            val enemy = Enemy(x = spawn.x, y = spawn.y, type = enemyType, isBoss = spawn.boss)
            if (enemyType == EnemyType.SPITTER) {
                enemy.onShootProjectile = { px, py, pvx, pvy ->
                    projectiles.add(Projectile(x = px, y = py, vx = pvx, vy = pvy))
                    spawnSparks(px, py, 6, Color.rgb(255, 180, 40))
                }
            }
            enemies.add(enemy)
        }

        // Display room title announcement
        showCombatText(room.title, Color.rgb(220, 245, 240))
    }

    fun reset() {
        val room = currentRoom ?: return
        loadRoom(room.id, room.spawnX, room.spawnY)
        player.currentMasks = player.maxMasks
        player.soul = 33
        player.state = PlayerState.IDLE
        player.grabbedEnemy = null
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

        // Update projectiles & check player / parry collisions
        val projIter = projectiles.iterator()
        while (projIter.hasNext()) {
            val proj = projIter.next()
            proj.update(dt, platforms)

            if (!proj.isAlive) {
                projIter.remove()
                continue
            }

            val projBox = proj.getBoundingBox()

            if (!proj.isReflected) {
                // Check collision with player
                if (RectF.intersects(player.getBoundingBox(), projBox)) {
                    if (player.state == PlayerState.PARRYING) {
                        // Deflect projectile back with safety pin!
                        val reflectDir = if (player.facing == Facing.RIGHT) 1f else -1f
                        proj.reflect(reflectDir * 420f, 0f)
                        triggerShake(8f, 0.2f)
                        showCombatText("🛡️ DEFLECTED!", Color.rgb(100, 240, 255))
                        spawnSparks(proj.x, proj.y, 14, Color.CYAN)
                        player.parrySuccess = true
                        player.soul = (player.soul + 15).coerceAtMost(player.maxSoul)
                    } else if (player.invincibilityTimer <= 0f) {
                        player.takeDamage(1)
                        proj.isAlive = false
                        projIter.remove()
                        spawnSparks(player.x, player.y - player.height / 2, 8, Color.YELLOW)
                    }
                }
            } else {
                // Reflected projectile can damage enemies!
                for (enemy in enemies) {
                    if (enemy.isAlive && RectF.intersects(enemy.getBoundingBox(), projBox)) {
                        enemy.takeDamage(35, if (proj.vx > 0) 180f else -180f, -60f)
                        proj.isAlive = false
                        projIter.remove()
                        spawnSparks(enemy.x, enemy.y - enemy.height / 2, 12, Color.CYAN)
                        break
                    }
                }
            }
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

        // Check room exits
        val room = currentRoom
        if (room != null) {
            val playerBox = player.getBoundingBox()

            // Check doors / transitions to other rooms
            for (exit in room.exits) {
                if (RectF.intersects(playerBox, exit.rect)) {
                    loadRoom(exit.targetRoomId, exit.targetX, exit.targetY)
                    return
                }
            }

            // Check water / spike hazards
            for (haz in room.hazards) {
                if (RectF.intersects(playerBox, haz)) {
                    player.takeDamage(1)
                    player.x = room.spawnX
                    player.y = room.spawnY
                    player.vx = 0f
                    player.vy = 0f
                    triggerShake(10f, 0.3f)
                    spawnSparks(player.x, player.y, 16, Color.rgb(240, 80, 80))
                    break
                }
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

        gameTime += dt

        // Smooth Camera Follow Player
        val targetCamX = player.x - screenWidth / 2f
        val targetCamY = player.y - screenHeight * 0.62f
        cameraX += (targetCamX - cameraX) * 8f * dt
        cameraY += (targetCamY - cameraY) * 8f * dt
    }

    fun draw(canvas: Canvas, paint: Paint, screenWidth: Float, screenHeight: Float) {
        val room = currentRoom

        // 1. Draw Room Backdrop (screen-space parallax)
        room?.drawBackdrop(canvas, paint, cameraX, cameraY, screenWidth, screenHeight, gameTime)

        canvas.save()

        // Apply screen shake
        var shakeOffsetX = 0f
        var shakeOffsetY = 0f
        if (screenShakeTime > 0f) {
            shakeOffsetX = (Random.nextFloat() - 0.5f) * screenShakeIntensity * 2f
            shakeOffsetY = (Random.nextFloat() - 0.5f) * screenShakeIntensity * 2f
        }

        canvas.translate(-cameraX + shakeOffsetX, -cameraY + shakeOffsetY)

        // 2. Draw Room World Geometry (slabs, ruins, shrines, water)
        room?.drawWorld(canvas, paint, gameTime)

        // 3. Draw Enemies
        for (enemy in enemies) {
            enemy.draw(canvas, paint)
        }

        // 3.5 Draw Projectiles
        for (proj in projectiles) {
            proj.draw(canvas, paint)
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

        // 6. Draw Room Front Layer (volumetric God rays, light shafts, drifting spores, vignette)
        room?.drawFront(canvas, paint, cameraX, cameraY, screenWidth, screenHeight, gameTime)

        // 7. Draw HUD (Masks + Soul Vessel + Combat Banners) on top
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
            PlayerState.HOLDING_ENEMY -> "🧷 PULLING ENEMY IN..."
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
