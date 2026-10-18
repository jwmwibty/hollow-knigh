package com.devdgames.hollowknightbutmine.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.devdgames.hollowknightbutmine.game.env.DarkRibsRoom
import com.devdgames.hollowknightbutmine.game.env.EnvArt
import com.devdgames.hollowknightbutmine.game.env.EnvPalette
import com.devdgames.hollowknightbutmine.game.env.MossyBridgeRoom
import com.devdgames.hollowknightbutmine.game.env.Room
import com.devdgames.hollowknightbutmine.game.env.RuinedSanctuaryRoom
import kotlin.random.Random
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

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

    // --- World map features: benches (respawn + heal), soul caches, bosses ---
    private class BenchState(val x: Float, val y: Float, var lit: Boolean = false, var cooldown: Float = 0f)
    private class CacheState(val x: Float, val y: Float, var taken: Boolean = false)
    private val worldBenches = ArrayList<BenchState>()
    private val worldCaches = ArrayList<CacheState>()
    private val bossMarkers = ArrayList<Float>() // flat x,y pairs in world coords
    private var respawnX = 140f
    private var respawnY = 460f
    private var safeX = 140f
    private var safeY = 460f
    private var groundedTimer = 0f
    private val visitedRooms = HashSet<String>()

    // --- Full-screen map overlay ---
    var mapOpen = false
        private set
    private var mapPulse = 0f

    fun toggleMap() {
        mapOpen = !mapOpen
    }

    fun closeMap() {
        mapOpen = false
    }

    // --- Area title cards & small toast messages ---
    private var areaTitle = ""
    private var areaSubtitle = ""
    private var areaTitleTimer = 0f
    private val areaTitleDuration = 3.6f
    private var toastText = ""
    private var toastTimer = 0f
    private val toastDuration = 2.4f

    private fun showAreaTitle(title: String, subtitle: String) {
        areaTitle = title
        areaSubtitle = subtitle
        areaTitleTimer = areaTitleDuration
    }

    private fun showToast(text: String) {
        toastText = text
        toastTimer = toastDuration
    }

    // --- Cached screen-space shaders (sky blend, vignette, floor haze) ---
    private var skyShader: LinearGradient? = null
    private var skyW = -1f
    private var skyH = -1f
    private var skyTopC = 0
    private var skyMidC = 0
    private var skyBotC = 0
    private var vignetteShader: RadialGradient? = null
    private var vigW = -1f
    private var vigH = -1f
    private var hazeShader: LinearGradient? = null
    private var hazeH = -1f
    private var hazeColor = 0

    private val localView = RectF()

    private companion object {
        const val WORLD_TOP = -250f
        const val WORLD_BOTTOM = 750f
        const val SEAM_BLEND = 460f
    }

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
        worldBenches.clear()
        worldCaches.clear()
        bossMarkers.clear()
        visitedRooms.clear()
        mapOpen = false

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

            for (bench in room.benches) {
                worldBenches.add(BenchState(bench.x + offset, bench.y))
            }
            for (cachePt in room.soulCaches) {
                worldCaches.add(CacheState(cachePt.x + offset, cachePt.y))
            }
            for (spawn in room.enemySpawns) {
                if (spawn.boss) {
                    bossMarkers.add(spawn.x + offset)
                    bossMarkers.add(spawn.y)
                }
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
        visitedRooms.add(start.room.id)
        player.x = start.offsetX + start.room.spawnX
        player.y = start.room.spawnY
        player.vx = 0f
        player.vy = 0f
        player.state = PlayerState.IDLE
        player.activeHitbox = null
        player.grabbedEnemy = null
        player.isGrounded = false
        respawnX = player.x
        respawnY = player.y
        safeX = player.x
        safeY = player.y
        groundedTimer = 0f
        cameraX = (player.x - 420f).coerceIn(worldLeft, worldRight - 1f)
        cameraY = player.y - 300f
        showAreaTitle(start.room.title, start.room.subtitle)
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
        // The world pauses while the map is open.
        if (mapOpen) {
            mapPulse += dt
            return
        }

        if (areaTitleTimer > 0f) areaTitleTimer -= dt
        if (toastTimer > 0f) toastTimer -= dt

        // Death pause and respawn at the last bench rested at.
        if (player.state == PlayerState.DEAD) {
            deathTimer -= dt
            if (deathTimer <= 0f) {
                player.x = respawnX
                player.y = respawnY
                safeX = respawnX
                safeY = respawnY
                groundedTimer = 0f
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
            deathTimer = 2.2f
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
            visitedRooms.add(zone.room.id)
            showAreaTitle(zone.room.title, zone.room.subtitle)
        }

        // Remember the last patch of solid ground stood on for a moment.
        // Hazards return the player here instead of to the biome entrance.
        if (player.isGrounded && player.state != PlayerState.DEAD) {
            groundedTimer += dt
            if (groundedTimer > 0.32f) {
                safeX = player.x
                safeY = player.y
            }
        } else {
            groundedTimer = 0f
        }

        // Benches: resting restores masks and moves the death respawn here.
        for (bench in worldBenches) {
            if (bench.cooldown > 0f) bench.cooldown -= dt
            if (player.state == PlayerState.DEAD) break
            val nearBench = abs(player.x - bench.x) < 52f && abs(player.y - bench.y) < 36f
            if (nearBench && player.isGrounded && bench.cooldown <= 0f) {
                val isNew = !bench.lit || respawnX != bench.x || respawnY != bench.y
                if (isNew || player.currentMasks < player.maxMasks) {
                    bench.lit = true
                    bench.cooldown = 1.4f
                    respawnX = bench.x
                    respawnY = bench.y
                    safeX = bench.x
                    safeY = bench.y
                    player.currentMasks = player.maxMasks
                    spawnSparks(bench.x, bench.y - 26f, 14, Color.rgb(255, 214, 130))
                    showToast(if (isNew) "Bench — rested. Respawn set here." else "Rested — masks restored.")
                }
            }
        }

        // Soul caches tucked onto the high/secret routes.
        if (player.state != PlayerState.DEAD) {
            for (cacheSt in worldCaches) {
                if (cacheSt.taken) continue
                val dx = player.x - cacheSt.x
                val dy = (player.y - player.height * 0.5f) - (cacheSt.y - 42f)
                if (dx * dx + dy * dy < 58f * 58f) {
                    cacheSt.taken = true
                    player.soul = (player.soul + 33).coerceAtMost(player.maxSoul)
                    spawnSparks(cacheSt.x, cacheSt.y - 42f, 18, Color.rgb(150, 232, 206))
                    showToast("Soul cache  +33 Soul")
                }
            }
        }

        // Water and spike hazards: return to the last safe ground.
        val playerBox = player.getBoundingBox()
        var hitHazard = false
        for (haz in worldHazards) {
            if (RectF.intersects(playerBox, haz)) {
                hitHazard = true
                break
            }
        }
        if ((hitHazard || player.y > 900f) && player.state != PlayerState.DEAD) {
            spawnSparks(player.x, player.y - 20f, 14, Color.rgb(240, 80, 80))
            player.grabbedEnemy?.isGrabbed = false
            player.grabbedEnemy = null
            player.x = safeX
            player.y = safeY
            player.vx = 0f
            player.vy = 0f
            player.state = PlayerState.IDLE
            player.activeHitbox = null
            player.invincibilityTimer = 1.0f.coerceAtLeast(player.invincibilityTimer)
            groundedTimer = 0f
            triggerShake(9f, 0.28f)
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

        // Camera: look-ahead toward facing/velocity, a gentle fall offset,
        // frame-rate-independent smoothing, and clamps on all four sides so
        // the player never sees past the world's top or the crypt floors.
        val maxCameraX = (worldRight - screenWidth).coerceAtLeast(worldLeft)
        val facingSign = if (player.facing == Facing.RIGHT) 1f else -1f
        val lookAhead = (facingSign * 64f + player.vx * 0.16f).coerceIn(-118f, 118f)
        val targetCamX = (player.x + lookAhead - screenWidth / 2f).coerceIn(worldLeft, maxCameraX)

        val worldH = WORLD_BOTTOM - WORLD_TOP
        val targetCamYRaw = player.y - screenHeight * 0.62f + (player.vy * 0.045f).coerceIn(-46f, 64f)
        val targetCamY = if (screenHeight >= worldH) {
            WORLD_TOP - (screenHeight - worldH) * 0.5f
        } else {
            targetCamYRaw.coerceIn(WORLD_TOP, WORLD_BOTTOM - screenHeight)
        }

        val blendX = 1f - exp(-6.0f * dt)
        val blendY = 1f - exp(-5.0f * dt)
        cameraX += (targetCamX - cameraX) * blendX.toFloat()
        cameraX = cameraX.coerceIn(worldLeft, maxCameraX)
        cameraY += (targetCamY - cameraY) * blendY.toFloat()
    }

    /**
     * Blended sky colours at a world X: each biome owns a gradient, and the
     * two gradients cross-fade over ±[SEAM_BLEND]px around a seam so walking
     * between biomes feels like the air itself changing.
     */
    private val skyArr = IntArray(4)
    private var skyArrCenter = Float.NaN

    private fun skyAt(worldX: Float): IntArray {
        if (worldX == skyArrCenter) return skyArr
        skyArrCenter = worldX
        val here = placementForX(worldX)
        val idx = roomPlacements.indexOf(here)
        var top = here.room.skyTop
        var mid = here.room.skyMid
        var bot = here.room.skyBottom
        var fog = here.room.fogColor

        fun blendToward(other: RoomPlacement, t: Float) {
            val s = EnvArt.smooth01(t)
            top = EnvArt.lerpColor(top, other.room.skyTop, s)
            mid = EnvArt.lerpColor(mid, other.room.skyMid, s)
            bot = EnvArt.lerpColor(bot, other.room.skyBottom, s)
            fog = EnvArt.lerpColor(fog, other.room.fogColor, s)
        }

        // Near the left seam of this biome?
        if (idx > 0) {
            val seam = here.offsetX + here.room.bounds.left
            val d = worldX - seam
            if (d < SEAM_BLEND) blendToward(roomPlacements[idx - 1], 1f - (d + SEAM_BLEND) / (SEAM_BLEND * 2f))
        }
        // Near the right seam?
        if (idx < roomPlacements.size - 1) {
            val next = roomPlacements[idx + 1]
            val seam = next.offsetX + next.room.bounds.left
            val d = seam - worldX
            if (d < SEAM_BLEND) blendToward(next, 1f - (d + SEAM_BLEND) / (SEAM_BLEND * 2f))
        }
        skyArr[0] = top
        skyArr[1] = mid
        skyArr[2] = bot
        skyArr[3] = fog
        return skyArr
    }

    private fun fillSky(canvas: Canvas, paint: Paint, viewW: Float, viewH: Float) {
        val sky = skyAt(cameraX + viewW * 0.5f)
        if (skyShader == null || skyW != viewW || skyH != viewH ||
            skyTopC != sky[0] || skyMidC != sky[1] || skyBotC != sky[2]
        ) {
            skyShader = LinearGradient(
                0f, 0f, 0f, viewH,
                intArrayOf(sky[0], sky[1], sky[2]),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP
            )
            skyW = viewW
            skyH = viewH
            skyTopC = sky[0]
            skyMidC = sky[1]
            skyBotC = sky[2]
        }
        paint.style = Paint.Style.FILL
        paint.shader = skyShader
        paint.alpha = 255
        canvas.drawRect(0f, 0f, viewW, viewH, paint)
        paint.shader = null
    }

    /** Ground haze + drifting fog between the backdrop and the solid world. */
    private fun drawAtmosphere(canvas: Canvas, paint: Paint, viewW: Float, viewH: Float) {
        val fog = skyAt(cameraX + viewW * 0.5f)[3]
        if (hazeShader == null || hazeH != viewH || hazeColor != fog) {
            val r = Color.red(fog)
            val g = Color.green(fog)
            val b = Color.blue(fog)
            hazeShader = LinearGradient(
                0f, viewH * 0.55f, 0f, viewH,
                Color.argb(0, r, g, b), Color.argb(74, r, g, b),
                Shader.TileMode.CLAMP
            )
            hazeH = viewH
            hazeColor = fog
        }
        paint.style = Paint.Style.FILL
        paint.shader = hazeShader
        paint.alpha = 255
        canvas.drawRect(0f, viewH * 0.55f, viewW, viewH, paint)
        paint.shader = null
        EnvArt.drawFogBank(canvas, paint, gameTime, viewW, viewH * 0.78f, viewH * 0.10f, 11f, 54)
        EnvArt.drawFogBank(canvas, paint, gameTime * 0.7f + 40f, viewW, viewH * 0.60f, viewH * 0.07f, -7f, 34)
    }

    private fun drawVignette(canvas: Canvas, paint: Paint, viewW: Float, viewH: Float) {
        if (vignetteShader == null || vigW != viewW || vigH != viewH) {
            vignetteShader = RadialGradient(
                viewW * 0.5f, viewH * 0.46f, maxOf(viewW, viewH) * 0.72f,
                intArrayOf(
                    Color.argb(0, 2, 4, 8),
                    Color.argb(0, 2, 4, 8),
                    Color.argb(150, 2, 4, 8)
                ),
                floatArrayOf(0f, 0.58f, 1f),
                Shader.TileMode.CLAMP
            )
            vigW = viewW
            vigH = viewH
        }
        paint.style = Paint.Style.FILL
        paint.shader = vignetteShader
        paint.alpha = 255
        canvas.drawRect(0f, 0f, viewW, viewH, paint)
        paint.shader = null
    }

    /** A rest bench. [lit] benches burn gold; unvisited ones glow faint teal. */
    private fun drawBench(canvas: Canvas, paint: Paint, b: BenchState) {
        val x = b.x
        val y = b.y
        // Worn stone seat + legs + back posts.
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.alpha = 255
        paint.color = EnvPalette.stoneMid
        canvas.drawRoundRect(x - 30f, y - 18f, x + 30f, y - 10f, 4f, 4f, paint)
        canvas.drawRect(x - 26f, y - 10f, x - 19f, y, paint)
        canvas.drawRect(x + 19f, y - 10f, x + 26f, y, paint)
        paint.color = EnvPalette.stoneBody
        canvas.drawRect(x - 29f, y - 38f, x - 24f, y - 16f, paint)
        canvas.drawRect(x + 24f, y - 38f, x + 29f, y - 16f, paint)
        canvas.drawRoundRect(x - 30f, y - 40f, x + 30f, y - 33f, 3f, 3f, paint)
        // Candle on the arm.
        paint.color = EnvPalette.boneLit
        canvas.drawRect(x + 12f, y - 27f, x + 16f, y - 18f, paint)
        val flicker = sin(gameTime * 6.3f + x * 0.11f) * 1.6f
        paint.color = EnvPalette.candleGold
        canvas.drawOval(x + 12.4f, y - 33f + flicker * 0.4f, x + 15.6f, y - 26f, paint)
        if (b.lit) {
            EnvArt.bloom(canvas, paint, x + 14f, y - 29f, 38f, 38f, EnvArt.glowWarm, 95)
            EnvArt.bloom(canvas, paint, x, y - 18f, 56f, 26f, EnvArt.glowWarm, 34)
        } else {
            EnvArt.bloom(canvas, paint, x + 14f, y - 29f, 24f, 24f, EnvArt.glowTeal, 46)
        }
        // Moss tuft on the seat.
        paint.color = EnvPalette.mossDeep
        canvas.drawOval(x - 18f, y - 21f, x - 4f, y - 15f, paint)
        paint.alpha = 255
    }

    /** A hovering soul vessel waiting on a high route. */
    private fun drawCache(canvas: Canvas, paint: Paint, c: CacheState) {
        val bob = sin(gameTime * 2.1f + c.x * 0.07f) * 5f
        val cx = c.x
        val cy = c.y - 42f + bob
        // Vessel: faceted diamond with a bright core.
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.alpha = 255
        val vessel = EnvArt.scratch
        vessel.reset()
        vessel.moveTo(cx, cy - 17f)
        vessel.lineTo(cx + 10f, cy - 3f)
        vessel.lineTo(cx, cy + 16f)
        vessel.lineTo(cx - 10f, cy - 3f)
        vessel.close()
        paint.color = EnvPalette.waterMid
        canvas.drawPath(vessel, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f
        paint.color = EnvPalette.sporeTeal
        canvas.drawPath(vessel, paint)
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.sporeTeal
        val pulse = 2.6f + sin(gameTime * 3.2f + c.x) * 1.1f
        canvas.drawCircle(cx, cy - 1f, pulse.coerceAtLeast(1.2f), paint)
        // Orbiting motes ring the prize.
        paint.color = EnvPalette.sporeWarm
        for (k in 0..2) {
            val a = gameTime * 1.9f + k * 2.094f
            canvas.drawCircle(cx + kotlin.math.cos(a) * 19f, cy + sin(a) * 7f, 1.7f, paint)
        }
        EnvArt.bloom(canvas, paint, cx, cy, 44f, 44f, EnvArt.glowTeal, 72)
        paint.alpha = 255
    }

    fun draw(canvas: Canvas, paint: Paint, screenWidth: Float, screenHeight: Float) {
        // 1. Biome-blended sky.
        fillSky(canvas, paint, screenWidth, screenHeight)

        // 2. Backdrops: each biome draws with its OWN local camera and is
        // clipped to its own stretch of screen, so the three parallax
        // scenes meet cleanly at the seams instead of stacking.
        for ((index, placement) in roomPlacements.withIndex()) {
            val seamLOfs = if (index == 0) 0f else (placement.offsetX + placement.room.bounds.left - cameraX)
            val seamROfs = if (index == roomPlacements.size - 1) screenWidth else {
                val next = roomPlacements[index + 1]
                next.offsetX + next.room.bounds.left - cameraX
            }
            val clipL = seamLOfs.coerceIn(0f, screenWidth)
            val clipR = seamROfs.coerceIn(0f, screenWidth)
            if (clipR - clipL < 1f) continue
            canvas.save()
            canvas.clipRect(clipL, 0f, clipR, screenHeight)
            placement.room.drawBackdrop(
                canvas, paint,
                cameraX - placement.offsetX, cameraY,
                screenWidth, screenHeight, gameTime
            )
            canvas.restore()
        }

        // 3. Fog banks separating backdrop from the solid world.
        drawAtmosphere(canvas, paint, screenWidth, screenHeight)

        // 4. Solid world + actors, in world coordinates.
        var shakeOffsetX = 0f
        var shakeOffsetY = 0f
        if (screenShakeTime > 0f) {
            shakeOffsetX = (Random.nextFloat() - 0.5f) * screenShakeIntensity * 2f
            shakeOffsetY = (Random.nextFloat() - 0.5f) * screenShakeIntensity * 2f
        }
        canvas.save()
        canvas.translate(-cameraX + shakeOffsetX, -cameraY + shakeOffsetY)

        for (placement in roomPlacements) {
            val localCamX = cameraX - placement.offsetX
            if (localCamX > placement.room.bounds.right + 240f ||
                localCamX + screenWidth < placement.room.bounds.left - 240f
            ) continue
            localView.set(localCamX - 90f, cameraY - 90f, localCamX + screenWidth + 90f, cameraY + screenHeight + 90f)
            canvas.save()
            canvas.translate(placement.offsetX, 0f)
            placement.room.drawWorld(canvas, paint, gameTime, localView)
            canvas.restore()
        }

        // Benches & soul caches sit in the world, behind the actors.
        for (b in worldBenches) {
            if (b.x > cameraX - 160f && b.x < cameraX + screenWidth + 160f) drawBench(canvas, paint, b)
        }
        for (c in worldCaches) {
            if (!c.taken && c.x > cameraX - 160f && c.x < cameraX + screenWidth + 160f) drawCache(canvas, paint, c)
        }

        for (enemy in enemies) {
            if (enemy.x > cameraX - 320f && enemy.x < cameraX + screenWidth + 320f &&
                enemy.y > cameraY - 320f && enemy.y < cameraY + screenHeight + 320f
            ) enemy.draw(canvas, paint)
        }
        for (proj in projectiles) proj.draw(canvas, paint)
        player.draw(canvas, paint)

        paint.style = Paint.Style.FILL
        paint.alpha = 255
        for (particle in particles) {
            paint.color = particle.color
            canvas.drawCircle(particle.x, particle.y, particle.radius, paint)
        }
        canvas.restore()

        // 5. Front layers (light shafts, motes, foreground silhouettes),
        // only for biomes near the camera.
        for (placement in roomPlacements) {
            val localCamX = cameraX - placement.offsetX
            if (localCamX > placement.room.bounds.right + screenWidth * 0.6f ||
                localCamX + screenWidth < placement.room.bounds.left - screenWidth * 0.6f
            ) continue
            placement.room.drawFront(
                canvas, paint,
                localCamX, cameraY,
                screenWidth, screenHeight, gameTime
            )
        }

        // 6. Unified vignette, HUD, title cards, overlays.
        drawVignette(canvas, paint, screenWidth, screenHeight)
        drawHUD(canvas, paint, screenWidth, screenHeight)
        drawAreaTitleCard(canvas, paint, screenWidth, screenHeight)
        drawToast(canvas, paint, screenWidth, screenHeight)
        if (mapOpen) drawMapOverlay(canvas, paint, screenWidth, screenHeight)
        if (player.state == PlayerState.DEAD) {
            drawDefeatedScreen(canvas, paint, screenWidth, screenHeight)
        }
        paint.alpha = 255
        paint.shader = null
    }

    /** fade-in / hold / fade-out curve for title cards and toasts. */
    private fun cardAlpha(timer: Float, duration: Float): Float {
        if (timer <= 0f) return 0f
        val age = duration - timer
        val fadeIn = (age / 0.45f).coerceIn(0f, 1f)
        val fadeOut = (timer / 0.9f).coerceIn(0f, 1f)
        return minOf(fadeIn, fadeOut)
    }

    private fun drawAreaTitleCard(canvas: Canvas, paint: Paint, viewW: Float, viewH: Float) {
        if (areaTitleTimer <= 0f || mapOpen) return
        val a = cardAlpha(areaTitleTimer, areaTitleDuration)
        if (a <= 0.01f) return
        val cx = viewW * 0.5f
        val cy = viewH * 0.30f
        val drift = (1f - a) * 10f
        val titleSize = (viewW * 0.042f).coerceIn(30f, 54f)

        paint.typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.SERIF, android.graphics.Typeface.NORMAL
        )
        paint.textAlign = Paint.Align.CENTER
        try {
            paint.letterSpacing = 0.16f
        } catch (ignored: Exception) {
            // letterSpacing unsupported on this device; titles still render.
        }
        paint.style = Paint.Style.FILL
        paint.textSize = titleSize
        paint.color = Color.argb((232 * a).toInt(), 228, 238, 240)
        paint.setShadowLayer(10f, 0f, 0f, Color.argb((120 * a).toInt(), 110, 200, 185))
        canvas.drawText(areaTitle, cx, cy - drift, paint)
        paint.clearShadowLayer()

        if (areaSubtitle.isNotEmpty()) {
            paint.textSize = (titleSize * 0.42f).coerceIn(13f, 20f)
            paint.color = Color.argb((190 * a).toInt(), 168, 196, 196)
            canvas.drawText(areaSubtitle, cx, cy + titleSize * 0.72f - drift, paint)
        }

        // Fine rules either side of a small diamond crest.
        val lineY = cy + titleSize * 1.18f - drift
        val lineHalf = (viewW * 0.16f).coerceAtMost(220f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.3f
        paint.color = Color.argb((190 * a).toInt(), 170, 200, 200)
        canvas.drawLine(cx - lineHalf, lineY, cx - 16f, lineY, paint)
        canvas.drawLine(cx + 16f, lineY, cx + lineHalf, lineY, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb((210 * a).toInt(), 190, 220, 215)
        val crest = EnvArt.scratch
        crest.reset()
        crest.moveTo(cx, lineY - 7f)
        crest.lineTo(cx + 6f, lineY)
        crest.lineTo(cx, lineY + 7f)
        crest.lineTo(cx - 6f, lineY)
        crest.close()
        canvas.drawPath(crest, paint)

        try {
            paint.letterSpacing = 0f
        } catch (ignored: Exception) {
        }
        paint.typeface = android.graphics.Typeface.DEFAULT
        paint.textAlign = Paint.Align.LEFT
        paint.alpha = 255
    }

    private fun drawToast(canvas: Canvas, paint: Paint, viewW: Float, viewH: Float) {
        if (toastTimer <= 0f || mapOpen) return
        val a = cardAlpha(toastTimer, toastDuration)
        if (a <= 0.01f) return
        paint.typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.SERIF, android.graphics.Typeface.NORMAL
        )
        paint.textAlign = Paint.Align.CENTER
        paint.style = Paint.Style.FILL
        paint.textSize = (viewW * 0.016f).coerceIn(15f, 22f)
        val tw = paint.measureText(toastText)
        val cx = viewW * 0.5f
        val cy = viewH - 118f
        paint.color = Color.argb((165 * a).toInt(), 6, 12, 18)
        canvas.drawRoundRect(cx - tw * 0.5f - 20f, cy - 26f, cx + tw * 0.5f + 20f, cy + 12f, 14f, 14f, paint)
        paint.color = Color.argb((230 * a).toInt(), 224, 236, 232)
        canvas.drawText(toastText, cx, cy, paint)
        paint.typeface = android.graphics.Typeface.DEFAULT
        paint.textAlign = Paint.Align.LEFT
        paint.alpha = 255
    }

    /**
     * Full-screen chart of the connected world, generated from the actual
     * collision geometry: visited biomes are drawn in detail, unvisited
     * ones remain blank parchment. Player, benches, bosses and unclaimed
     * soul caches are marked.
     */
    private fun drawMapOverlay(canvas: Canvas, paint: Paint, viewW: Float, viewH: Float) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(216, 4, 8, 13)
        canvas.drawRect(0f, 0f, viewW, viewH, paint)

        val mx = viewW * 0.045f
        val my = viewH * 0.075f
        val mw = viewW - mx * 2f
        val mh = viewH * 0.72f
        val panel = RectF(mx, my, mx + mw, my + mh)
        paint.color = Color.argb(242, 10, 17, 24)
        canvas.drawRoundRect(panel, 20f, 20f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.6f
        paint.color = Color.argb(170, 130, 170, 175)
        canvas.drawRoundRect(panel, 20f, 20f, paint)

        val worldW = (worldRight - worldLeft).coerceAtLeast(1f)
        val worldHgt = WORLD_BOTTOM - WORLD_TOP
        fun mapX(wx: Float) = mx + 14f + (wx - worldLeft) / worldW * (mw - 28f)
        fun mapY(wy: Float) = my + 44f + (wy - WORLD_TOP) / worldHgt * (mh - 84f)

        // Biome bands.
        paint.style = Paint.Style.FILL
        for (placement in roomPlacements) {
            val l = mapX(placement.offsetX + placement.room.bounds.left)
            val r = mapX(placement.offsetX + placement.room.bounds.right)
            val visited = visitedRooms.contains(placement.room.id)
            paint.color = EnvArt.lerpColor(placement.room.skyBottom, Color.BLACK, 0.35f)
            paint.alpha = if (visited) 150 else 60
            canvas.drawRect(l, my + 44f, r, my + mh - 40f, paint)
            paint.alpha = 255
        }

        // Geometry of visited biomes (outer walls skipped: too tall to read).
        for (plat in platforms) {
            if (plat.height() > 420f) continue
            val zoneId = placementForX(plat.centerX()).room.id
            if (!visitedRooms.contains(zoneId)) continue
            paint.color = Color.argb(215, 178, 212, 208)
            paint.style = Paint.Style.FILL
            val l = mapX(plat.left)
            val r = mapX(plat.right)
            val t = mapY(plat.top)
            val b = maxOf(mapY(plat.bottom), t + 1.6f)
            canvas.drawRect(l, t, maxOf(r, l + 1.2f), b, paint)
        }
        // Hazards in visited biomes.
        paint.color = Color.argb(200, 235, 110, 100)
        for (haz in worldHazards) {
            val zoneId = placementForX(haz.centerX()).room.id
            if (!visitedRooms.contains(zoneId)) continue
            canvas.drawRect(mapX(haz.left), mapY(haz.top), mapX(haz.right), mapY(haz.top) + 2.2f, paint)
        }

        // Soul caches (unclaimed, visited biomes).
        paint.color = EnvPalette.sporeTeal
        for (c in worldCaches) {
            if (c.taken) continue
            if (!visitedRooms.contains(placementForX(c.x).room.id)) continue
            canvas.drawCircle(mapX(c.x), mapY(c.y - 42f), 3.4f, paint)
        }

        // Benches: gold = rested respawn, grey = not yet used.
        for (b in worldBenches) {
            if (!visitedRooms.contains(placementForX(b.x).room.id)) continue
            val bx = mapX(b.x)
            val by = mapY(b.y)
            paint.color = if (b.lit) EnvPalette.candleGold else Color.rgb(130, 150, 160)
            val d = EnvArt.scratch
            d.reset()
            d.moveTo(bx, by - 7f)
            d.lineTo(bx + 5.5f, by)
            d.lineTo(bx, by + 7f)
            d.lineTo(bx - 5.5f, by)
            d.close()
            canvas.drawPath(d, paint)
        }

        // Bosses.
        paint.color = Color.rgb(242, 92, 108)
        var bi = 0
        while (bi + 1 < bossMarkers.size) {
            val bx = bossMarkers[bi]
            val by = bossMarkers[bi + 1]
            if (visitedRooms.contains(placementForX(bx).room.id)) {
                val px = mapX(bx)
                val py = mapY(by)
                canvas.drawCircle(px, py - 4f, 5.5f, paint)
                canvas.drawRect(px - 8f, py - 13f, px - 4f, py - 6f, paint)
                canvas.drawRect(px + 4f, py - 13f, px + 8f, py - 6f, paint)
            }
            bi += 2
        }

        // The knight, pulsing.
        val kx = mapX(player.x)
        val ky = mapY(player.y - player.height * 0.5f)
        val pulse = 5f + sin(mapPulse * 4.5f) * 1.8f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.6f
        paint.color = Color.WHITE
        canvas.drawCircle(kx, ky, pulse + 4f, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(kx, ky, 4.2f, paint)

        // Titles, biome names, legend.
        paint.typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.SERIF, android.graphics.Typeface.NORMAL
        )
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = (viewW * 0.02f).coerceIn(18f, 26f)
        paint.color = Color.rgb(230, 238, 240)
        canvas.drawText("Chart of the Verdant Chasm", mx + 16f, my + 30f, paint)

        paint.textSize = (viewW * 0.0125f).coerceIn(12f, 16f)
        for (placement in roomPlacements) {
            val l = mapX(placement.offsetX + placement.room.bounds.left)
            val visited = visitedRooms.contains(placement.room.id)
            paint.color = if (visited) Color.rgb(190, 214, 210) else Color.rgb(105, 125, 135)
            canvas.drawText(if (visited) placement.room.title else "Uncharted", l + 8f, my + mh - 18f, paint)
        }

        paint.textSize = (viewW * 0.0125f).coerceIn(12f, 16f)
        val legendY = my + mh + 30f
        var lx = mx + 6f
        fun legendDot(color: Int, label: String) {
            paint.style = Paint.Style.FILL
            paint.color = color
            canvas.drawCircle(lx + 5f, legendY - 5f, 4.5f, paint)
            val labelW = paint.measureText(label)
            paint.color = Color.rgb(200, 216, 220)
            canvas.drawText(label, lx + 15f, legendY, paint)
            lx += 15f + labelW + 26f
        }
        legendDot(Color.WHITE, "You")
        legendDot(EnvPalette.candleGold, "Bench (respawn)")
        legendDot(EnvPalette.sporeTeal, "Soul cache")
        legendDot(Color.rgb(242, 92, 108), "Boss")
        legendDot(Color.argb(255, 235, 110, 100), "Hazard")

        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.argb(180, 165, 185, 195)
        canvas.drawText("Press M or tap Map again to close", viewW * 0.5f, viewH - 16f, paint)

        paint.typeface = android.graphics.Typeface.DEFAULT
        paint.textAlign = Paint.Align.LEFT
        paint.style = Paint.Style.FILL
        paint.alpha = 255
    }

    /**
     * Minimal, native Canvas death overlay. Kept as text and shapes so it scales cleanly
     * on every display and doesn't depend on a bitmap asset.
     */
    private fun drawDefeatedScreen(
        canvas: Canvas,
        paint: Paint,
        screenWidth: Float,
        screenHeight: Float
    ) {
        val centerX = screenWidth / 2f
        val centerY = screenHeight * 0.46f
        val titleSize = (screenWidth * 0.055f).coerceIn(28f, 42f)

        // Quietly dim the world without hiding the scene completely.
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(178, 4, 8, 16)
        canvas.drawRect(0f, 0f, screenWidth, screenHeight, paint)

        // Fine framing lines and a small central crest.
        val lineY = centerY + titleSize * 0.72f
        val lineHalf = (screenWidth * 0.17f).coerceAtMost(150f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.4f
        paint.color = Color.argb(205, 183, 205, 220)
        canvas.drawLine(centerX - lineHalf, lineY, centerX - 15f, lineY, paint)
        canvas.drawLine(centerX + 15f, lineY, centerX + lineHalf, lineY, paint)

        paint.style = Paint.Style.FILL
        val crest = android.graphics.Path().apply {
            moveTo(centerX, lineY - 9f)
            lineTo(centerX + 7f, lineY)
            lineTo(centerX, lineY + 9f)
            lineTo(centerX - 7f, lineY)
            close()
        }
        canvas.drawPath(crest, paint)

        // Normal-sized, elegant text with a restrained cool-white tint.
        paint.typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.SERIF,
            android.graphics.Typeface.NORMAL
        )
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = titleSize
        paint.color = Color.rgb(226, 234, 242)
        paint.setShadowLayer(8f, 0f, 0f, Color.argb(100, 145, 190, 220))
        canvas.drawText("Defeated", centerX, centerY, paint)
        paint.clearShadowLayer()

        paint.typeface = android.graphics.Typeface.DEFAULT
        paint.textSize = 12f
        paint.color = Color.argb(190, 169, 185, 199)
        canvas.drawText("Returning to the last area", centerX, lineY + 30f, paint)

        paint.textAlign = Paint.Align.LEFT
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.strokeWidth = 1f
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
