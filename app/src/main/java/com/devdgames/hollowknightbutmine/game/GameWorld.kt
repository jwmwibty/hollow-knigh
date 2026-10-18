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
import kotlin.math.abs

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

    // The map is authored as three connected biomes, but played as one continuous world.
    private data class RoomPlacement(val room: Room, val offsetX: Float)
    private val rooms = mutableMapOf<String, Room>()
    private val roomPlacements = ArrayList<RoomPlacement>()
    private val worldHazards = ArrayList<RectF>()
    var currentRoom: Room? = null
    private var activeRoomId: String? = null
    private var worldLeft = -100f
    private var worldRight = 13800f
    var gameTime = 0f
    private var deathTimer = 0f

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
        loadContinuousWorld()
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

    /**
     * Loads every biome into one shared coordinate space. Room art stays authored locally;
     * collision, enemies, camera and player all use world coordinates.
     */
    private fun loadContinuousWorld(startRoomId: String = "mossy_bridge") {
        roomPlacements.clear()
        roomPlacements.add(RoomPlacement(rooms.getValue("mossy_bridge"), 0f))
        roomPlacements.add(RoomPlacement(rooms.getValue("dark_ribs"), 4400f))
        roomPlacements.add(RoomPlacement(rooms.getValue("ruined_sanctuary"), 9000f))

        enemies.clear()
        projectiles.clear()
        platforms.clear()
        worldHazards.clear()
        particles.clear()

        worldLeft = roomPlacements.first().room.bounds.left
        val lastPlacement = roomPlacements.last()
        worldRight = lastPlacement.offsetX + lastPlacement.room.bounds.right
        // Always start a fresh run with a full set of masks and enough Soul for one Focus.
        player.currentMasks = player.maxMasks
        player.soul = 33
        deathTimer = 0f

        for (placement in roomPlacements) {
            val room = placement.room
            val offset = placement.offsetX

            // Remove internal room walls so passageways stay open. Keep the two outermost
            // boundary walls, especially the final wall where the last floor ends.
            for (local in room.platforms) {
                val isLeftWall = local.height() > 800f && abs(local.left - room.bounds.left) < 1f
                val isRightWall = local.height() > 800f && abs(local.right - room.bounds.right) < 1f
                val isOuterWall =
                    (placement == roomPlacements.first() && isLeftWall) ||
                    (placement == roomPlacements.last() && isRightWall)
                if ((!isLeftWall && !isRightWall) || isOuterWall) {
                    platforms.add(RectF(local.left + offset, local.top, local.right + offset, local.bottom))
                }
            }

            for (hazard in room.hazards) {
                worldHazards.add(RectF(hazard.left + offset, hazard.top, hazard.right + offset, hazard.bottom))
            }

            for (spawn in room.enemySpawns) {
                val enemyType = spawn.type ?: when {
                    spawn.boss -> EnemyType.MOSS_CHARGER
                    spawn.y < 250f -> EnemyType.FLYER
                    spawn.x in 1100f..1400f && room.id == "mossy_bridge" -> EnemyType.SPITTER
                    spawn.x in 1300f..1400f && room.id == "dark_ribs" -> EnemyType.SPITTER
                    spawn.x in 550f..650f && room.id == "ruined_sanctuary" -> EnemyType.SPITTER
                    spawn.x in 1600f..1700f && room.id == "mossy_bridge" -> EnemyType.SHIELDER
                    spawn.x in 1000f..1100f && room.id == "dark_ribs" -> EnemyType.SHIELDER
                    spawn.x in 1300f..1400f && room.id == "ruined_sanctuary" -> EnemyType.SHIELDER
                    else -> EnemyType.CRAWLER
                }

                val enemy = Enemy(
                    x = spawn.x + offset,
                    y = spawn.y,
                    type = enemyType,
                    isBoss = spawn.boss
                )
                if (enemyType == EnemyType.SPITTER) {
                    enemy.onShootProjectile = { px, py, pvx, pvy ->
                        projectiles.add(Projectile(x = px, y = py, vx = pvx, vy = pvy))
                        spawnSparks(px, py, 6, Color.rgb(255, 180, 40))
                    }
                }
                enemies.add(enemy)
            }
        }

        // Keep the entire connected map enclosed, not each biome individually.
        platforms.add(RectF(worldLeft - 80f, -500f, worldLeft, 900f))
        platforms.add(RectF(worldRight, -500f, worldRight + 100f, 900f))

        val start = roomPlacements.firstOrNull { it.room.id == startRoomId } ?: roomPlacements.first()
        currentRoom = start.room
        activeRoomId = start.room.id
        player.x = start.offsetX + start.room.spawnX
        player.y = start.room.spawnY
        player.vx = 0f
        player.vy = 0f
        player.state = PlayerState.IDLE
        player.activeHitbox = null
        player.grabbedEnemy = null
        player.isGrounded = false
        cameraX = (player.x - 420f).coerceIn(worldLeft, worldRight - 1f)
        cameraY = player.y - 300f
        showCombatText(start.room.title, Color.rgb(220, 245, 240))
    }

    private fun placementForX(worldX: Float): RoomPlacement {
        for (placement in roomPlacements.asReversed()) {
            // The next biome's floor starts at its local left bound, not at x=0.
            if (worldX >= placement.offsetX + placement.room.bounds.left) return placement
        }
        return roomPlacements.first()
    }

    fun reset() {
        val startZone = placementForX(player.x)
        loadContinuousWorld(startZone.room.id)
        player.currentMasks = player.maxMasks
        player.soul = 33
    }

    fun triggerShake(intensity: Float, duration: Float) {
        screenShakeIntensity = intensity
        screenShakeTime = duration
    }

    // Combat feedback is intentionally visual-only; no action callouts clutter the screen.
    fun showCombatText(text: String, color: Int) {
        combatMessage = ""
        combatMessageTimer = 0f
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
        // Death pause and respawn: losing the last mask now has a real consequence.
        if (player.state == PlayerState.DEAD) {
            deathTimer -= dt
            if (deathTimer <= 0f) {
                val deathZone = placementForX(player.x)
                player.x = deathZone.offsetX + deathZone.room.spawnX
                player.y = deathZone.room.spawnY
                player.vx = 0f
                player.vy = 0f
                player.currentMasks = player.maxMasks
                player.soul = 0 // Soul is lost on death; rebuild it through combat.
                player.state = PlayerState.IDLE
                player.isGrounded = false
                player.invincibilityTimer = 1.35f
                player.flashTimer = 0f
                player.activeHitbox = null
                player.grabbedEnemy = null
            }
        }

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

        // Enter a short death pause when the last mask breaks.
        if (player.currentMasks <= 0 && player.state != PlayerState.DEAD) {
            player.state = PlayerState.DEAD
            player.vx = 0f
            player.vy = 0f
            player.activeHitbox = null
            player.grabbedEnemy?.isGrabbed = false
            player.grabbedEnemy = null
            deathTimer = 1.15f
            triggerShake(12f, 0.35f)
        }

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

        // Walk across biome boundaries without teleporting or reloading the room.
        val zone = placementForX(player.x)
        if (zone.room.id != activeRoomId) {
            activeRoomId = zone.room.id
            currentRoom = zone.room
            showCombatText(zone.room.title, Color.rgb(220, 245, 240))
        }

        // Water and spike hazards remain active after the biomes are joined.
        val playerBox = player.getBoundingBox()
        var hitHazard = false
        for (haz in worldHazards) {
            if (RectF.intersects(playerBox, haz)) {
                hitHazard = true
                break
            }
        }
        if ((hitHazard || player.y > 900f) && player.state != PlayerState.DEAD) {
            player.grabbedEnemy?.isGrabbed = false
            player.grabbedEnemy = null
            player.x = zone.offsetX + zone.room.spawnX
            player.y = zone.room.spawnY
            player.vx = 0f
            player.vy = 0f
            player.state = PlayerState.IDLE
            player.activeHitbox = null
            triggerShake(10f, 0.3f)
            spawnSparks(player.x, player.y, 16, Color.rgb(240, 80, 80))
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

        // Camera follows through the full connected world and stops at its outer edges.
        val maxCameraX = (worldRight - screenWidth).coerceAtLeast(worldLeft)
        val targetCamX = (player.x - screenWidth / 2f).coerceIn(worldLeft, maxCameraX)
        val targetCamY = player.y - screenHeight * 0.62f
        cameraX += (targetCamX - cameraX) * 8f * dt
        cameraX = cameraX.coerceIn(worldLeft, maxCameraX)
        cameraY += (targetCamY - cameraY) * 8f * dt
    }

    fun draw(canvas: Canvas, paint: Paint, screenWidth: Float, screenHeight: Float) {
        // One continuous parallax backdrop. The first biome paints the shared void gradient;
        // the following biomes add their silhouettes into that same background.
        for ((index, placement) in roomPlacements.withIndex()) {
            canvas.save()
            if (index > 0) canvas.translate(placement.offsetX * 0.25f, 0f)
            placement.room.drawBackdrop(canvas, paint, cameraX, cameraY, screenWidth, screenHeight, gameTime)
            canvas.restore()
        }

        canvas.save()
        var shakeOffsetX = 0f
        var shakeOffsetY = 0f
        if (screenShakeTime > 0f) {
            shakeOffsetX = (Random.nextFloat() - 0.5f) * screenShakeIntensity * 2f
            shakeOffsetY = (Random.nextFloat() - 0.5f) * screenShakeIntensity * 2f
        }
        canvas.translate(-cameraX + shakeOffsetX, -cameraY + shakeOffsetY)

        // Draw all biome geometry in the shared world coordinate system.
        for (placement in roomPlacements) {
            canvas.save()
            canvas.translate(placement.offsetX, 0f)
            placement.room.drawWorld(canvas, paint, gameTime)
            canvas.restore()
        }

        for (enemy in enemies) enemy.draw(canvas, paint)
        for (proj in projectiles) proj.draw(canvas, paint)
        player.draw(canvas, paint)

        paint.style = Paint.Style.FILL
        for (particle in particles) {
            paint.color = particle.color
            canvas.drawCircle(particle.x, particle.y, particle.radius, paint)
        }
        canvas.restore()

        // Front-layer particles, light shafts and edge vignettes scroll with each biome.
        for (placement in roomPlacements) {
            placement.room.drawFront(
                canvas, paint,
                cameraX - placement.offsetX, cameraY,
                screenWidth, screenHeight, gameTime
            )
        }
        drawHUD(canvas, paint, screenWidth, screenHeight)
    }

    private fun drawHUD(canvas: Canvas, paint: Paint, screenWidth: Float, screenHeight: Float) {
        // Compact, text-free HUD: Soul meter + five readable masks.
        val panel = RectF(18f, 16f, 278f, 84f)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(190, 8, 13, 23)
        canvas.drawRoundRect(panel, 18f, 18f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = Color.argb(155, 125, 165, 183)
        canvas.drawRoundRect(panel, 18f, 18f, paint)

        val soulX = 52f
        val soulY = 50f
        val soulPct = (player.soul.toFloat() / player.maxSoul).coerceIn(0f, 1f)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(16, 26, 39)
        canvas.drawCircle(soulX, soulY, 23f, paint)
        paint.color = Color.rgb(29, 47, 62)
        canvas.drawCircle(soulX, soulY, 17f, paint)

        // Soul is shown as a clean arc instead of a giant white bubble.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.rgb(118, 211, 235)
        canvas.drawArc(RectF(soulX - 19f, soulY - 19f, soulX + 19f, soulY + 19f),
            -90f, 360f * soulPct, false, paint)
        paint.strokeWidth = 2f
        paint.color = Color.rgb(185, 225, 239)
        canvas.drawCircle(soulX, soulY, 23f, paint)

        // Focus charge gets its own warm progress ring while the player holds Heal.
        if (player.state == PlayerState.HEALING) {
            paint.strokeWidth = 3f
            paint.color = Color.rgb(255, 211, 112)
            canvas.drawArc(RectF(soulX - 27f, soulY - 27f, soulX + 27f, soulY + 27f),
                -90f, 360f * (player.healCharge / player.healChargeTime).coerceIn(0f, 1f),
                false, paint)
        }

        // Hollow-Knight-inspired mask pips: full masks are bright; broken masks are dim.
        val maskStartX = 101f
        val maskSpacing = 32f
        for (i in 0 until player.maxMasks) {
            val mx = maskStartX + i * maskSpacing
            val my = soulY
            if (i < player.currentMasks) {
                paint.style = Paint.Style.FILL
                paint.color = Color.rgb(238, 242, 244)
                canvas.drawOval(mx - 10f, my - 13f, mx + 10f, my + 13f, paint)
                val horn = android.graphics.Path().apply {
                    moveTo(mx - 8f, my - 7f)
                    lineTo(mx - 13f, my - 15f)
                    lineTo(mx - 2f, my - 10f)
                    close()
                    moveTo(mx + 8f, my - 7f)
                    lineTo(mx + 13f, my - 15f)
                    lineTo(mx + 2f, my - 10f)
                    close()
                }
                canvas.drawPath(horn, paint)
                paint.color = Color.rgb(15, 22, 31)
                canvas.drawOval(mx - 5f, my - 1f, mx - 2f, my + 5f, paint)
                canvas.drawOval(mx + 2f, my - 1f, mx + 5f, my + 5f, paint)
            } else {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                paint.color = Color.rgb(73, 91, 108)
                canvas.drawOval(mx - 10f, my - 13f, mx + 10f, my + 13f, paint)
                canvas.drawLine(mx - 5f, my + 2f, mx + 2f, my - 5f, paint)
            }
        }
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        paint.strokeCap = Paint.Cap.BUTT
    }

}
