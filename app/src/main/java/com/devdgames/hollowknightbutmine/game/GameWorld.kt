package com.devdgames.hollowknightbutmine.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.devdgames.hollowknightbutmine.game.env.AreaScene
import com.devdgames.hollowknightbutmine.game.env.Backdrops
import com.devdgames.hollowknightbutmine.game.env.EnemySpawnPoint
import com.devdgames.hollowknightbutmine.game.env.EnvArt
import com.devdgames.hollowknightbutmine.game.env.GrassPatch
import com.devdgames.hollowknightbutmine.game.world.AreaDef
import com.devdgames.hollowknightbutmine.game.world.WorldAtlas
import com.devdgames.hollowknightbutmine.game.world.WorldLayout
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
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
    var maxLife: Float,
    /** Downward acceleration, for flecks that should arc back to the ground. */
    var gravity: Float = 0f,
    /** Fraction of velocity lost per second (0 = none). */
    var drag: Float = 0f,
    var rotation: Float = 0f,
    var spin: Float = 0f,
    /** Blades and grass flecks draw as little slivers instead of round sparks. */
    var shard: Boolean = false
) {
    fun update(dt: Float) {
        if (drag > 0f) {
            val f = (1f - drag * dt).coerceAtLeast(0f)
            vx *= f
            vy *= f
        }
        vy += gravity * dt
        x += vx * dt
        y += vy * dt
        rotation += spin * dt
        life -= dt
    }

    fun isDead() = life <= 0f
}

/**
 * The kingdom.
 *
 * One continuous 2D world assembled from the authored areas in [WorldAtlas]: a surface
 * town over a central hub, with branches climbing, descending and spreading sideways
 * into thirteen distinct regions. GameWorld owns the player, enemies, benches, camera,
 * area transitions, hazards, particles, HUD and the map overlay.
 */
class GameWorld {
    // ------------------------------------------------------------------ world data
    val layout = WorldLayout(WorldAtlas.areas)
    val scenes: List<AreaScene> = layout.grids.map { AreaScene(it, layout) }
    private val sceneById: Map<String, AreaScene> = scenes.associateBy { it.def.id }
    private val solids = SpatialGrid(320f)
    private val hazards = SpatialGrid(320f)
    private val fillers = ArrayList<RectF>()
    private val worldBounds = RectF(
        layout.minCol * AreaDef.TILE, layout.minRow * AreaDef.TILE,
        layout.maxCol * AreaDef.TILE, layout.maxRow * AreaDef.TILE
    )

    // ------------------------------------------------------------------ actors
    val player: Player
    val enemies = mutableListOf<Enemy>()
    val projectiles = mutableListOf<Projectile>()
    val particles = mutableListOf<Particle>()
    val benches = ArrayList<Bench>()

    private class SpawnRecord(val areaId: String, val point: EnemySpawnPoint, var enemy: Enemy?)
    private val spawnRecords = ArrayList<SpawnRecord>()

    // ------------------------------------------------------------------ progress
    val discovered = LinkedHashSet<String>()
    var currentScene: AreaScene? = null
        private set
    private var currentBench: Bench? = null
    private val worldSpawnX: Float
    private val worldSpawnY: Float
    private var lastSafeX = 0f
    private var lastSafeY = 0f
    private var safeTimer = 0f

    // ------------------------------------------------------------------ presentation
    var gameTime = 0f
    private var deathTimer = 0f
    private var areaCooldown = 0f
    private var titleName = ""
    private var titleSub = ""
    private var titleTimer = 0f
    private var titleDuration = 0f
    private var fadeTimer = 0f
    private var hurtFlash = 0f
    var mapOpen = false
        private set
    private val worldMap = WorldMap(layout)
    private var backdropScene: AreaScene? = null
    private var fadeIsDip = false

    // Camera: top-left corner of the view, in world units. `zoom` maps world → screen.
    var cameraX = 0f
    var cameraY = 0f
    private var camTargetY = 0f
    private var zoom = 1f
    private var viewW = 1200f
    private var viewH = DESIGN_VIEW_H
    private val view = RectF()

    // Screen shake for heavy hits (Headbutt / Parry)
    var screenShakeTime = 0f
    var screenShakeIntensity = 0f

    // Kept for API compatibility with the input layer; feedback is visual-only.
    var combatMessage = ""
    var combatMessageTimer = 0f
    var combatMessageColor = Color.WHITE

    private val serif: Typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
    private val scratchPath = Path()
    private var vignetteShader: Shader? = null
    private var vignetteW = 0f
    private var vignetteH = 0f

    init {
        // Collision: every area's solids and ledges plus the rock that fills the gaps between areas.
        for (s in scenes) {
            solids.addAll(s.solidRects)
            solids.addAll(s.ledgeRects)
            hazards.addAll(s.hazardRects)
            for (b in s.benchSpots) benches.add(Bench(s.def.id, b[0], b[1]))
            for (p in s.enemySpawns) spawnRecords.add(SpawnRecord(s.def.id, p, null))
        }
        for (f in layout.fillerRects(12)) {
            val r = RectF(f.col * AreaDef.TILE, f.row * AreaDef.TILE, f.right * AreaDef.TILE, f.bottom * AreaDef.TILE)
            fillers.add(r)
            solids.add(r)
        }

        val spawnScene = scenes.first { !it.spawnX.isNaN() }
        worldSpawnX = spawnScene.spawnX
        worldSpawnY = spawnScene.spawnY
        player = Player(x = worldSpawnX, y = worldSpawnY)
        lastSafeX = worldSpawnX
        lastSafeY = worldSpawnY

        populateEnemies()
        enterArea(spawnScene, announce = true)
        cameraX = player.x - 600f
        cameraY = player.y - DESIGN_VIEW_H * 0.6f
        camTargetY = cameraY
    }

    // ===================================================================== population

    private fun makeEnemy(p: EnemySpawnPoint): Enemy {
        val enemy = when (p.char) {
            'c' -> Enemy(p.x, p.y, type = EnemyType.CRAWLER)
            'f' -> Enemy(p.x, p.y, type = EnemyType.FLYER)
            'p' -> Enemy(p.x, p.y, type = EnemyType.SPITTER)
            'h' -> Enemy(p.x, p.y, type = EnemyType.SHIELDER)
            'm' -> Enemy(p.x, p.y, type = EnemyType.MOSS_CHARGER)
            'M' -> Enemy(p.x, p.y, type = EnemyType.MOSS_CHARGER, isBoss = true)
            '1' -> Enemy(p.x, p.y, type = EnemyType.BROODMOTHER, isBoss = true)
            '2' -> Enemy(p.x, p.y, type = EnemyType.OSSUARY_TITAN, isBoss = true)
            '3' -> Enemy(p.x, p.y, type = EnemyType.COLONNADE_KNIGHT, isBoss = true)
            else -> Enemy(p.x, p.y)
        }
        enemy.onShootProjectile = { px, py, pvx, pvy ->
            projectiles.add(Projectile(x = px, y = py, vx = pvx, vy = pvy))
            spawnSparks(px, py, 6, Color.rgb(255, 180, 40))
        }
        enemy.onGroundSlam = { sx, sy ->
            triggerShake(14f, 0.4f)
            spawnSparks(sx, sy, 24, Color.rgb(220, 210, 190))
        }
        return enemy
    }

    private fun populateEnemies() {
        enemies.clear()
        projectiles.clear()
        for (rec in spawnRecords) {
            val e = makeEnemy(rec.point)
            rec.enemy = e
            enemies.add(e)
        }
    }

    /** Benches bring the common fauna back; slain bosses stay slain. */
    private fun respawnRegularEnemies() {
        for (rec in spawnRecords) {
            val e = rec.enemy
            if (e != null && e.isBoss) continue
            if (e == null || !e.isAlive) {
                if (e != null) enemies.remove(e)
                val fresh = makeEnemy(rec.point)
                rec.enemy = fresh
                enemies.add(fresh)
            }
        }
        projectiles.clear()
    }

    // ===================================================================== areas

    private fun sceneAt(x: Float, y: Float): AreaScene? {
        val col = floor(x / AreaDef.TILE).toInt()
        val row = floor(y / AreaDef.TILE).toInt()
        val g = layout.areaAt(col, row) ?: return null
        return sceneById[g.def.id]
    }

    private fun enterArea(scene: AreaScene, announce: Boolean) {
        val first = discovered.add(scene.def.id)
        currentScene = scene
        for (b in benches) if (b.areaId == scene.def.id) b.discovered = true
        if (announce || first) {
            titleName = scene.def.name
            titleSub = scene.def.subtitle
            titleDuration = if (first) 3.6f else 2.0f
            titleTimer = titleDuration
        }
        if (announce) {
            backdropScene = scene
        } else {
            // Dip to black; the backdrop swaps at the darkest point (see update()).
            fadeTimer = FADE_TIME
            fadeIsDip = true
        }
    }

    // ===================================================================== public API

    fun reset() {
        respawnPlayer(full = true)
    }

    fun toggleMap() {
        mapOpen = !mapOpen
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
                    x = x, y = y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    radius = Random.nextFloat() * 3.5f + 2f,
                    color = color, life = life, maxLife = life,
                    gravity = 340f, drag = 0.9f,
                    rotation = Random.nextFloat() * 6.28f,
                    spin = (Random.nextFloat() - 0.5f) * 16f,
                    shard = Random.nextFloat() < 0.32f
                )
            )
        }
    }

    /** Green (or pale, or violet) flecks thrown out when a clump of grass is sheared off. */
    private fun burstGrass(g: GrassPatch) {
        val count = 8 + Random.nextInt(5)
        for (i in 0 until count) {
            val outward = if (Random.nextBoolean()) 1f else -1f
            val life = 0.5f + Random.nextFloat() * 0.55f
            val shard = i % 3 != 0
            particles.add(
                Particle(
                    x = g.x + (Random.nextFloat() - 0.5f) * g.width,
                    y = g.baseY - Random.nextFloat() * g.maxHeight * 0.85f,
                    vx = outward * (24f + Random.nextFloat() * 126f),
                    vy = -(60f + Random.nextFloat() * 190f),
                    radius = 1.3f + Random.nextFloat() * 1.5f,
                    color = if (Random.nextFloat() < 0.35f) g.deepColor else g.fleckColor,
                    life = life, maxLife = life,
                    gravity = 600f, drag = 0.65f,
                    rotation = Random.nextFloat() * 6.28f,
                    spin = (Random.nextFloat() - 0.5f) * 22f,
                    shard = shard
                )
            )
        }
        // A little dust kicked up from the cut stems.
        for (i in 0 until 3) {
            val life = 0.3f + Random.nextFloat() * 0.25f
            particles.add(
                Particle(
                    x = g.x + (Random.nextFloat() - 0.5f) * g.width * 0.7f,
                    y = g.baseY - 2f,
                    vx = (Random.nextFloat() - 0.5f) * 40f,
                    vy = -8f - Random.nextFloat() * 24f,
                    radius = 3f + Random.nextFloat() * 3f,
                    color = g.deepColor,
                    life = life, maxLife = life,
                    gravity = -20f, drag = 2.4f
                )
            )
        }
    }

    private fun respawnPlayer(full: Boolean) {
        val bench = currentBench
        player.x = bench?.x ?: worldSpawnX
        player.y = bench?.y ?: worldSpawnY
        player.vx = 0f
        player.vy = 0f
        player.currentMasks = player.maxMasks
        player.soul = if (full) 33 else 0
        player.state = PlayerState.IDLE
        player.isGrounded = false
        player.invincibilityTimer = 1.35f
        player.flashTimer = 0f
        player.activeHitbox = null
        player.grabbedEnemy?.isGrabbed = false
        player.grabbedEnemy = null
        lastSafeX = player.x
        lastSafeY = player.y
        deathTimer = 0f
        respawnRegularEnemies()
        particles.clear()
        val scene = sceneAt(player.x, player.y - 10f)
        if (scene != null && scene !== currentScene) enterArea(scene, announce = true)
        cameraX = player.x - viewW / 2f
        camTargetY = player.y - viewH * 0.6f
        cameraY = camTargetY
        backdropScene = currentScene
        fadeTimer = FADE_TIME
        fadeIsDip = false
    }

    // ===================================================================== update

    fun update(dt: Float, inputX: Float, screenWidth: Float, screenHeight: Float) {
        zoom = screenHeight / DESIGN_VIEW_H
        viewW = screenWidth / zoom
        viewH = DESIGN_VIEW_H
        gameTime += dt
        if (mapOpen) return

        if (player.state == PlayerState.DEAD) {
            deathTimer -= dt
            if (deathTimer <= 0f) respawnPlayer(full = false)
        }

        // ---- player
        val prevHeadbutt = player.state == PlayerState.HEADBUTTING
        val pbox = player.getBoundingBox()
        val nearSolids = solids.query(pbox, 160f)
        player.update(
            dt = dt,
            inputX = inputX,
            platforms = nearSolids,
            enemies = enemies,
            onParryTriggered = {
                triggerShake(9f, 0.25f)
                spawnSparks(player.x + 20f, player.y - player.height / 2, 16, Color.YELLOW)
            }
        )

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

        if (!prevHeadbutt && player.state == PlayerState.HEADBUTTING) {
            triggerShake(12f, 0.3f)
            spawnSparks(player.x + 30f, player.y - player.height / 2, 22, Color.rgb(255, 120, 40))
        }

        // ---- breakable grass: the nail shears it, movement bends it aside
        val nail = player.activeHitbox
        if (nail != null && player.state != PlayerState.DEAD) {
            for (s in scenes) {
                if (!RectF.intersects(s.bounds, nail.rect)) continue
                s.cutGrass(nail.rect) { patch -> burstGrass(patch) }
            }
        }
        val bodyBox = player.getBoundingBox()
        for (s in scenes) s.updateGrass(dt, bodyBox, player.vx)

        // ---- enemies (only the ones near enough to matter)
        for (enemy in enemies) {
            if (!enemy.isAlive) continue
            if (abs(enemy.x - player.x) > ACTIVE_RANGE_X || abs(enemy.y - player.y) > ACTIVE_RANGE_Y) continue
            val ebox = enemy.getBoundingBox()
            enemy.update(dt, player.x, player.y, solids.query(ebox, 120f))
        }

        // ---- projectiles
        val projIter = projectiles.iterator()
        while (projIter.hasNext()) {
            val proj = projIter.next()
            proj.update(dt, solids.query(proj.x - 30f, proj.y - 30f, proj.x + 30f, proj.y + 30f))
            if (!proj.isAlive) { projIter.remove(); continue }

            val projBox = proj.getBoundingBox()
            if (!proj.isReflected) {
                if (RectF.intersects(player.getBoundingBox(), projBox)) {
                    if (player.state == PlayerState.PARRYING) {
                        val reflectDir = if (player.facing == Facing.RIGHT) 1f else -1f
                        proj.reflect(reflectDir * 420f, 0f)
                        triggerShake(8f, 0.2f)
                        spawnSparks(proj.x, proj.y, 14, Color.CYAN)
                        player.parrySuccess = true
                        player.soul = (player.soul + 15).coerceAtMost(player.maxSoul)
                    } else if (player.invincibilityTimer <= 0f) {
                        player.takeDamage(1)
                        hurtFlash = 0.25f
                        proj.isAlive = false
                        projIter.remove()
                        spawnSparks(player.x, player.y - player.height / 2, 8, Color.YELLOW)
                    }
                }
            } else {
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

        // ---- particles
        val pIter = particles.iterator()
        while (pIter.hasNext()) {
            val p = pIter.next()
            p.update(dt)
            if (p.isDead()) pIter.remove()
        }

        // ---- hazards: spikes and acid cost a mask and return you to solid ground
        if (player.state != PlayerState.DEAD) {
            val box = player.getBoundingBox()
            var hit: RectF? = null
            for (h in hazards.query(box, 0f)) if (RectF.intersects(box, h)) { hit = h; break }
            if (hit != null) {
                if (player.invincibilityTimer <= 0f) {
                    player.takeDamage(1)
                    hurtFlash = 0.35f
                }
                player.grabbedEnemy?.isGrabbed = false
                player.grabbedEnemy = null
                player.x = lastSafeX
                player.y = lastSafeY
                player.vx = 0f
                player.vy = 0f
                player.isGrounded = true
                player.activeHitbox = null
                if (player.state != PlayerState.DEAD) player.state = PlayerState.IDLE
                player.invincibilityTimer = max(player.invincibilityTimer, 1.2f)
                triggerShake(10f, 0.3f)
                spawnSparks(player.x, player.y - 20f, 16, Color.rgb(240, 80, 80))
            } else if (player.isGrounded) {
                safeTimer += dt
                if (safeTimer > 0.35f) {
                    // remember this spot only if nothing hostile lies directly beneath it
                    var clean = true
                    for (h in hazards.query(player.x - 30f, player.y - 10f, player.x + 30f, player.y + 60f)) {
                        if (h.left < player.x + 30f && h.right > player.x - 30f) { clean = false; break }
                    }
                    if (clean) { lastSafeX = player.x; lastSafeY = player.y }
                    safeTimer = 0f
                }
            }
        }

        // ---- benches
        for (b in benches) {
            if (player.isGrounded && b.playerOnBench(player.x, player.y) && abs(player.vx) < 8f && player.state != PlayerState.DEAD) {
                b.restTimer += dt
                if (b.restTimer >= 0.8f && !b.resting) {
                    b.resting = true
                    rest(b)
                }
            } else {
                b.restTimer = 0f
                b.resting = false
            }
        }

        // ---- area tracking
        if (areaCooldown > 0f) areaCooldown -= dt
        val here = sceneAt(player.x, player.y - player.height * 0.5f)
        if (here != null && here !== currentScene && areaCooldown <= 0f) {
            areaCooldown = 1.5f
            enterArea(here, announce = false)
        }

        // ---- timers
        if (screenShakeTime > 0f) screenShakeTime -= dt
        if (titleTimer > 0f) titleTimer -= dt
        if (fadeTimer > 0f) {
            fadeTimer -= dt
            if (fadeIsDip && fadeTimer <= FADE_TIME * 0.5f) backdropScene = currentScene
        }
        if (hurtFlash > 0f) hurtFlash -= dt

        updateCamera(dt)
    }

    private fun rest(b: Bench) {
        for (o in benches) o.isCurrent = false
        b.isCurrent = true
        currentBench = b
        player.currentMasks = player.maxMasks
        player.soul = max(player.soul, 33)
        respawnRegularEnemies()
        for (s in scenes) s.restoreGrass()
        spawnSparks(b.x, b.y - 30f, 18, Color.rgb(255, 230, 170))
    }

    private fun updateCamera(dt: Float) {
        val look = if (player.facing == Facing.RIGHT) 70f else -70f
        val targetX = player.x + look - viewW / 2f

        // Vertical: lock to the ground when standing; while airborne only move when the
        // player leaves a generous band, which keeps jumps from bouncing the view.
        val groundTarget = player.y - viewH * 0.6f
        if (player.isGrounded) {
            camTargetY = groundTarget
        } else {
            val top = camTargetY + viewH * 0.22f
            val bottom = camTargetY + viewH * 0.78f
            if (player.y < top) camTargetY -= (top - player.y)
            else if (player.y > bottom) camTargetY += (player.y - bottom)
        }

        val minX = worldBounds.left + 40f
        val maxX = worldBounds.right - 40f - viewW
        val minY = worldBounds.top
        val maxY = worldBounds.bottom - viewH
        val tx = targetX.coerceIn(minX, max(minX, maxX))
        val ty = camTargetY.coerceIn(minY, max(minY, maxY))
        val k = 1f - kotlin.math.exp(-dt * 7f)
        cameraX += (tx - cameraX) * k
        cameraY += (ty - cameraY) * (1f - kotlin.math.exp(-dt * 5f))
        if (abs(tx - cameraX) < 0.3f) cameraX = tx
    }

    // ===================================================================== draw

    fun draw(canvas: Canvas, paint: Paint, screenWidth: Float, screenHeight: Float) {
        zoom = screenHeight / DESIGN_VIEW_H
        viewW = screenWidth / zoom
        viewH = DESIGN_VIEW_H
        val scene = backdropScene ?: currentScene ?: scenes.first()

        var shakeX = 0f
        var shakeY = 0f
        if (screenShakeTime > 0f) {
            shakeX = (Random.nextFloat() - 0.5f) * screenShakeIntensity * 2f
            shakeY = (Random.nextFloat() - 0.5f) * screenShakeIntensity * 2f
        }
        val camX = cameraX + shakeX
        val camY = cameraY + shakeY
        view.set(camX - 60f, camY - 60f, camX + viewW + 60f, camY + viewH + 60f)

        canvas.save()
        canvas.scale(zoom, zoom)

        // 1. sky + parallax for the area the player is in
        Backdrops.drawSky(canvas, paint, scene.pal, viewW, viewH, camX, camY, scene.def.openSky, gameTime)
        Backdrops.drawParallax(canvas, paint, scene.biome, scene.pal, camX, camY, viewW, viewH, gameTime)

        // 2. world layer
        canvas.save()
        canvas.translate(-camX, -camY)
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = FILLER_COLOR
        for (f in fillers) if (RectF.intersects(f, view)) canvas.drawRect(f, paint)
        for (s in scenes) if (RectF.intersects(s.bounds, view)) s.drawWorld(canvas, paint, view, gameTime)
        for (b in benches) if (b.x > view.left - 80f && b.x < view.right + 80f && b.y > view.top - 80f && b.y < view.bottom + 80f) b.draw(canvas, paint, gameTime)

        for (enemy in enemies) {
            if (!enemy.isAlive) continue
            if (enemy.x < view.left - 200f || enemy.x > view.right + 200f || enemy.y < view.top - 200f || enemy.y > view.bottom + 200f) continue
            enemy.draw(canvas, paint)
        }
        for (proj in projectiles) proj.draw(canvas, paint)

        // A faint halo behind the knight keeps the silhouette readable in the dark caverns.
        val haloAlpha = (14f + scene.def.darkness * 46f).toInt().coerceIn(0, 70)
        if (haloAlpha > 0) {
            EnvArt.bloom(
                canvas, paint, player.x, player.y - player.height * 0.5f,
                118f, 128f, EnvArt.glowPale, haloAlpha
            )
        }
        player.draw(canvas, paint)
        // Grass at the knight's own feet is repeated in front, so the boots disappear
        // into the cover while taller blades sway around the mask.
        val playerBody = player.getBoundingBox()
        for (s in scenes) if (RectF.intersects(s.bounds, view)) s.drawGrassFront(canvas, paint, view, gameTime, playerBody)
        for (s in scenes) if (RectF.intersects(s.bounds, view)) s.drawLights(canvas, paint, view)

        paint.shader = null
        paint.style = Paint.Style.FILL
        for (particle in particles) {
            val fade = (particle.life / particle.maxLife).coerceIn(0f, 1f)
            paint.color = particle.color
            paint.alpha = (255f * fade).toInt().coerceIn(0, 255)
            if (particle.shard) {
                canvas.save()
                canvas.translate(particle.x, particle.y)
                canvas.rotate(particle.rotation)
                canvas.drawRect(
                    -particle.radius * 0.6f, -particle.radius * 1.9f,
                    particle.radius * 0.6f, particle.radius * 1.9f, paint
                )
                canvas.restore()
            } else {
                canvas.drawCircle(particle.x, particle.y, particle.radius * (0.55f + 0.45f * fade), paint)
            }
        }
        paint.alpha = 255
        canvas.restore()

        // 3. front layer: atmosphere, weather, darkness, transitions, titles (design units)
        Backdrops.drawAtmosphere(canvas, paint, scene.pal, viewW, viewH)
        Backdrops.drawForegroundMotes(canvas, paint, scene.pal, scene.biome, camX, camY, viewW, viewH, gameTime)
        Backdrops.drawWeather(canvas, paint, scene.biome, scene.pal, camX, camY, viewW, viewH, gameTime)
        Backdrops.drawDarkness(canvas, paint, player.x - camX, player.y - camY - player.height * 0.5f, scene.def.darkness, viewW, viewH)
        drawEdgeVignette(canvas, paint)
        if (hurtFlash > 0f) {
            paint.color = Color.argb((hurtFlash / 0.35f * 90f).toInt().coerceIn(0, 90), 200, 30, 40)
            canvas.drawRect(0f, 0f, viewW, viewH, paint)
        }
        if (fadeTimer > 0f) {
            val p = (fadeTimer / FADE_TIME).coerceIn(0f, 1f)
            val a = if (fadeIsDip) kotlin.math.sin(p * Math.PI).toFloat() * 0.9f else p
            paint.color = Color.argb((a * 255f).toInt(), 0, 0, 0)
            canvas.drawRect(0f, 0f, viewW, viewH, paint)
        }
        if (titleTimer > 0f) drawTitleCard(canvas, paint, scene)
        canvas.restore()

        // 4. screen-space UI
        drawHUD(canvas, paint, screenWidth, screenHeight)
        if (player.state == PlayerState.DEAD) drawDefeatedScreen(canvas, paint, screenWidth, screenHeight)
        if (mapOpen) {
            worldMap.draw(canvas, paint, screenWidth, screenHeight, gameTime, discovered, benches, player.x, player.y, currentScene?.def?.id)
        }
    }

    private fun drawEdgeVignette(canvas: Canvas, paint: Paint) {
        // Smooth radial fall-off at the frame edges. Cached because the design-space
        // view only changes when the device aspect ratio does.
        if (vignetteShader == null || vignetteW != viewW || vignetteH != viewH) {
            val radius = kotlin.math.sqrt(viewW * viewW + viewH * viewH) * 0.62f
            vignetteShader = RadialGradient(
                viewW * 0.5f, viewH * 0.5f, radius,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.TRANSPARENT,
                    Color.argb(46, 0, 0, 0),
                    Color.argb(128, 0, 0, 0)
                ),
                floatArrayOf(0f, 0.54f, 0.82f, 1f),
                Shader.TileMode.CLAMP
            )
            vignetteW = viewW
            vignetteH = viewH
        }
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        paint.shader = vignetteShader
        canvas.drawRect(0f, 0f, viewW, viewH, paint)
        paint.shader = null
    }

    private fun drawTitleCard(canvas: Canvas, paint: Paint, scene: AreaScene) {
        val elapsed = titleDuration - titleTimer
        val alpha = when {
            elapsed < 0.6f -> elapsed / 0.6f
            titleTimer < 0.9f -> titleTimer / 0.9f
            else -> 1f
        }.coerceIn(0f, 1f)
        val cx = viewW / 2f
        val cy = viewH * 0.3f
        val rise = (1f - alpha) * 6f

        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = serif
        paint.textSize = 46f
        paint.letterSpacing = 0.12f
        paint.color = Color.argb((alpha * 90).toInt(), 0, 0, 0)
        canvas.drawText(titleName, cx + 2f, cy + 2f + rise, paint)
        paint.color = scene.pal.titleTint
        paint.alpha = (alpha * 255).toInt()
        paint.setShadowLayer(14f, 0f, 0f, Color.argb((alpha * 120).toInt(), 160, 200, 230))
        canvas.drawText(titleName, cx, cy + rise, paint)
        paint.clearShadowLayer()

        paint.strokeWidth = 1.2f
        paint.style = Paint.Style.STROKE
        paint.color = Color.argb((alpha * 160).toInt(), 200, 212, 230)
        val w = paint.measureText(titleName) * 0.5f + 30f
        canvas.drawLine(cx - w, cy + 16f + rise, cx - 12f, cy + 16f + rise, paint)
        canvas.drawLine(cx + 12f, cy + 16f + rise, cx + w, cy + 16f + rise, paint)
        paint.style = Paint.Style.FILL
        scratchPath.reset()
        scratchPath.moveTo(cx, cy + 10f + rise); scratchPath.lineTo(cx + 5f, cy + 16f + rise)
        scratchPath.lineTo(cx, cy + 22f + rise); scratchPath.lineTo(cx - 5f, cy + 16f + rise); scratchPath.close()
        canvas.drawPath(scratchPath, paint)

        paint.letterSpacing = 0.22f
        paint.textSize = 15f
        paint.color = Color.argb((alpha * 200).toInt(), 205, 214, 228)
        canvas.drawText(titleSub.uppercase(), cx, cy + 42f + rise, paint)
        paint.letterSpacing = 0f
        paint.typeface = Typeface.DEFAULT
        paint.textAlign = Paint.Align.LEFT
        paint.alpha = 255
    }

    /**
     * Minimal, native Canvas death overlay. Kept as text and shapes so it scales cleanly
     * on every display and doesn't depend on a bitmap asset.
     */
    private fun drawDefeatedScreen(canvas: Canvas, paint: Paint, screenWidth: Float, screenHeight: Float) {
        val centerX = screenWidth / 2f
        val centerY = screenHeight * 0.46f
        val titleSize = (screenWidth * 0.055f).coerceIn(28f, 42f)

        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(178, 4, 8, 16)
        canvas.drawRect(0f, 0f, screenWidth, screenHeight, paint)

        val lineY = centerY + titleSize * 0.72f
        val lineHalf = (screenWidth * 0.17f).coerceAtMost(150f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.4f
        paint.color = Color.argb(205, 183, 205, 220)
        canvas.drawLine(centerX - lineHalf, lineY, centerX - 15f, lineY, paint)
        canvas.drawLine(centerX + 15f, lineY, centerX + lineHalf, lineY, paint)

        paint.style = Paint.Style.FILL
        scratchPath.reset()
        scratchPath.moveTo(centerX, lineY - 9f)
        scratchPath.lineTo(centerX + 7f, lineY)
        scratchPath.lineTo(centerX, lineY + 9f)
        scratchPath.lineTo(centerX - 7f, lineY)
        scratchPath.close()
        canvas.drawPath(scratchPath, paint)

        paint.typeface = serif
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = titleSize
        paint.color = Color.rgb(226, 234, 242)
        paint.setShadowLayer(8f, 0f, 0f, Color.argb(100, 145, 190, 220))
        canvas.drawText("Defeated", centerX, centerY, paint)
        paint.clearShadowLayer()

        paint.typeface = Typeface.DEFAULT
        paint.textSize = 12f
        paint.color = Color.argb(190, 169, 185, 199)
        val where = if (currentBench != null) "Returning to the last bench" else "Returning to Dustmere"
        canvas.drawText(where, centerX, lineY + 30f, paint)

        paint.textAlign = Paint.Align.LEFT
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.strokeWidth = 1f
    }

    private fun drawHUD(canvas: Canvas, paint: Paint, screenWidth: Float, screenHeight: Float) {
        // Compact, text-free HUD: Soul meter + five readable masks.
        val panel = RectF(18f, 16f, 278f, 84f)
        paint.shader = null
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

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.rgb(118, 211, 235)
        canvas.drawArc(RectF(soulX - 19f, soulY - 19f, soulX + 19f, soulY + 19f), -90f, 360f * soulPct, false, paint)
        paint.strokeWidth = 2f
        paint.color = Color.rgb(185, 225, 239)
        canvas.drawCircle(soulX, soulY, 23f, paint)

        if (player.state == PlayerState.HEALING) {
            paint.strokeWidth = 3f
            paint.color = Color.rgb(255, 211, 112)
            canvas.drawArc(RectF(soulX - 27f, soulY - 27f, soulX + 27f, soulY + 27f),
                -90f, 360f * (player.healCharge / player.healChargeTime).coerceIn(0f, 1f), false, paint)
        }

        val maskStartX = 101f
        val maskSpacing = 32f
        for (i in 0 until player.maxMasks) {
            val mx = maskStartX + i * maskSpacing
            val my = soulY
            if (i < player.currentMasks) {
                paint.style = Paint.Style.FILL
                paint.color = Color.rgb(238, 242, 244)
                canvas.drawOval(mx - 10f, my - 13f, mx + 10f, my + 13f, paint)
                scratchPath.reset()
                scratchPath.moveTo(mx - 8f, my - 7f); scratchPath.lineTo(mx - 13f, my - 15f); scratchPath.lineTo(mx - 2f, my - 10f); scratchPath.close()
                scratchPath.moveTo(mx + 8f, my - 7f); scratchPath.lineTo(mx + 13f, my - 15f); scratchPath.lineTo(mx + 2f, my - 10f); scratchPath.close()
                canvas.drawPath(scratchPath, paint)
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

        // Current area, quietly, under the masks.
        val scene = currentScene
        if (scene != null) {
            paint.style = Paint.Style.FILL
            paint.typeface = serif
            paint.textSize = 13f
            paint.textAlign = Paint.Align.LEFT
            paint.color = Color.argb(170, 200, 212, 228)
            canvas.drawText(scene.def.name, 22f, 104f, paint)
            paint.typeface = Typeface.DEFAULT
        }
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        paint.strokeCap = Paint.Cap.BUTT
    }

    companion object {
        const val DESIGN_VIEW_H = 760f
        const val FADE_TIME = 0.7f
        const val ACTIVE_RANGE_X = 1500f
        const val ACTIVE_RANGE_Y = 1000f
        val FILLER_COLOR = Color.rgb(9, 10, 16)
    }
}
