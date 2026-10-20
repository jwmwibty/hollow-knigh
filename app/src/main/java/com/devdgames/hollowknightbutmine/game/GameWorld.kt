package com.devdgames.hollowknightbutmine.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.devdgames.hollowknightbutmine.game.env.AreaScene
import com.devdgames.hollowknightbutmine.game.env.Backdrops
import com.devdgames.hollowknightbutmine.game.boss.BossArena
import com.devdgames.hollowknightbutmine.game.boss.BossArt
import com.devdgames.hollowknightbutmine.game.boss.BossEncounter
import com.devdgames.hollowknightbutmine.game.boss.BossEnemy
import com.devdgames.hollowknightbutmine.game.boss.BossProgress
import com.devdgames.hollowknightbutmine.game.boss.BossRoster
import com.devdgames.hollowknightbutmine.game.boss.BossSignal
import com.devdgames.hollowknightbutmine.game.boss.BossSpec
import com.devdgames.hollowknightbutmine.game.boss.SignalType
import com.devdgames.hollowknightbutmine.game.env.EnemySpawnPoint
import com.devdgames.hollowknightbutmine.game.world.AreaDef
import com.devdgames.hollowknightbutmine.game.world.AreaGrid
import com.devdgames.hollowknightbutmine.game.world.GrassCutState
import com.devdgames.hollowknightbutmine.game.world.WorldAtlas
import com.devdgames.hollowknightbutmine.game.world.WorldLayout
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
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

    /** Tracks which grass chunks the player has cut; regrows after 4 s (purely visual). */
    private val grassCuts = GrassCutState()
    private class SpawnRecord(val areaId: String, val point: EnemySpawnPoint, var enemy: Enemy?)
    private val spawnRecords = ArrayList<SpawnRecord>()

    // ------------------------------------------------------------------ bosses
    /** One boss hall: its encounter, the animated doors and the HUD bar. */
    private class BossRig(val spec: BossSpec, val arena: BossArena, val encounter: BossEncounter, val enemy: BossEnemy) {
        val gateRect = RectF(arena.gate.left, arena.gate.top, arena.gate.right, arena.gate.bottom)

        /** 0 = raised, 1 = shut (animated). */
        var gate = 0f
        var barAlpha = 0f
        var barHp = 1f
        var barGhost = 1f
        val shotGlow: Int = spec.accent
        val shotCore: Int = Color.rgb(
            (Color.red(spec.accent) + 255) / 2, (Color.green(spec.accent) + 255) / 2, (Color.blue(spec.accent) + 255) / 2
        )
    }

    private val bossProgress = BossProgress()
    private val bossRigs = ArrayList<BossRig>()
    private val frameSolids = ArrayList<RectF>()

    // ------------------------------------------------------------------ progress
    val discovered = LinkedHashSet<String>()
    var currentScene: AreaScene? = null
        private set
    private var currentBench: Bench? = null
    private val worldSpawnX: Float
    private val worldSpawnY: Float
    private lateinit var storyArea: StoryArea
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

    // Screen shake for heavy hits (enemy deaths, damage taken, boss slams)
    var screenShakeTime = 0f
    var screenShakeIntensity = 0f

    // Kept for API compatibility with the input layer; feedback is visual-only.
    var combatMessage = ""
    var combatMessageTimer = 0f
    var combatMessageColor = Color.WHITE

    private val serif: Typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
    private val scratchPath = Path()

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
        storyArea = StoryArea(worldSpawnX, worldSpawnY)
        lastSafeX = worldSpawnX
        lastSafeY = worldSpawnY

        populateEnemies()
        buildBosses()
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
            // ---- new enemy types -----------------------------------------------
            'j' -> Enemy(p.x, p.y, type = EnemyType.LURKER)
            'w' -> Enemy(p.x, p.y, type = EnemyType.SWOOPER).also { sw ->
                // Swooper spawns at its tile Y; that tile's top is the ceiling it clings to.
                // AreaScene places non-flyer enemies at (row+1)*T so y is the floor of the row.
                // The ceiling is one tile above the spawn row, i.e. spawn_y - TILE.
                sw.ceilingY = p.y - AreaDef.TILE
                sw.y = sw.ceilingY + sw.height   // start clung to ceiling
            }
            'r' -> Enemy(p.x, p.y, type = EnemyType.RUNEWEAVER)
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

    /**
     * The east-wing bosses: each has its own hall, doors and encounter. Victories are remembered
     * in [bossProgress] for the run, like the older bosses that stay slain; the permanent mask
     * each one grants is derived from it, so a reward can never be granted twice.
     */
    private fun buildBosses() {
        for (spec in BossRoster.all) {
            val arena = BossRoster.arenaFor(spec, layout)
            val encounter = BossEncounter(spec, arena, BossRoster.brainFor(spec, arena), bossProgress)
            val enemy = BossEnemy(encounter)
            enemies.add(enemy)
            bossRigs.add(BossRig(spec, arena, encounter, enemy))
            if (bossProgress.isDefeated(spec.id)) player.maxMasks += spec.maskReward
        }
        player.currentMasks = player.maxMasks
        player.damageInCurrentMask = 0
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

    /** Interact with nearby story spirits or the first rune puzzle. */
    fun interact() {
        if (!mapOpen && player.state != PlayerState.DEAD) storyArea.interact(player.x, player.y)
    }

    /** The heal control doubles as Interact, but only when a story object is in reach. */
    fun startHealOrInteract() {
        if (mapOpen || player.state == PlayerState.DEAD) return
        if (storyArea.canInteract(player.x, player.y)) {
            player.stopHeal()
            storyArea.interact(player.x, player.y)
        } else {
            player.startHeal()
        }
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
                    vx = kotlin.math.cos(angle) * speed,
                    vy = kotlin.math.sin(angle) * speed,
                    radius = Random.nextFloat() * 3.5f + 2f,
                    color = color, life = life, maxLife = life
                )
            )
        }
    }

    private fun respawnPlayer(full: Boolean) {
        abortBossFights()
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
        player.combat.cancel()
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
        storyArea.update(dt)
        if (mapOpen) return

        if (player.state == PlayerState.DEAD) {
            deathTimer -= dt
            if (deathTimer <= 0f) respawnPlayer(full = false)
        }

        // ---- player
        val pbox = player.getBoundingBox()
        val nearSolids = solids.query(pbox, 160f)
        player.update(
            dt = dt,
            inputX = inputX,
            platforms = withGates(nearSolids),
            enemies = enemies
        )

        if (player.currentMasks <= 0 && player.state != PlayerState.DEAD) {
            player.state = PlayerState.DEAD
            player.vx = 0f
            player.vy = 0f
            player.activeHitbox = null
            player.combat.cancel()
            deathTimer = 2.2f
            triggerShake(12f, 0.35f)
            abortBossFights()
        }

        // ---- grass cut: tick timers
        grassCuts.update(dt)

        // ---- grass cut: detect player attack hitting grass chunks (purely visual)
        val activeHitbox = player.activeHitbox
        if (activeHitbox != null && (player.state == PlayerState.STABBING || player.state == PlayerState.SLASHING)) {
            val scene = currentScene
            if (scene != null) {
                val chunks = scene.floraChunks
                val hitLeft = activeHitbox.rect.left
                val hitTop = activeHitbox.rect.top
                val hitRight = activeHitbox.rect.right
                val hitBottom = activeHitbox.rect.bottom + 24f
                for (i in chunks.indices) {
                    val chunk = chunks[i]
                    if (hitRight > chunk.bounds.left && hitLeft < chunk.bounds.right &&
                        hitBottom > chunk.bounds.top && hitTop < chunk.bounds.bottom) {
                        val key = GrassCutState.key(scene.def.id, i)
                        if (!grassCuts.isCut(key)) {
                            grassCuts.cut(key)
                            // Crisp burst of cut blade particles in biome vegetation colors
                            val mx = (activeHitbox.rect.centerX() + chunk.bounds.centerX()) * 0.5f
                            val my = chunk.bounds.bottom - 6f
                            spawnSparks(mx, my, 4, scene.pal.mossLit)
                            spawnSparks(mx, my, 2, scene.pal.moss)
                        }
                    }
                }
            }
        }

        // ---- enemies (only the ones near enough to matter)
        val playerDying = player.state == PlayerState.DEAD
        for (rig in bossRigs) rig.enemy.playerDead = playerDying
        for (enemy in enemies) {
            if (!enemy.isAlive) continue
            // A boss fight in progress always runs, however wide its hall is.
            val inFight = enemy is BossEnemy && enemy.encounter.gateClosed
            if (!inFight && (abs(enemy.x - player.x) > ACTIVE_RANGE_X || abs(enemy.y - player.y) > ACTIVE_RANGE_Y)) continue
            val ebox = enemy.getBoundingBox()
            enemy.update(dt, player.x, player.y, solids.query(ebox, 120f))
        }
        updateBosses(dt)

        // ---- projectiles
        val projIter = projectiles.iterator()
        while (projIter.hasNext()) {
            val proj = projIter.next()
            proj.update(dt, solids.query(proj.x - 30f, proj.y - 30f, proj.x + 30f, proj.y + 30f))
            if (!proj.isAlive) { projIter.remove(); continue }

            val projBox = proj.getBoundingBox()
            if (RectF.intersects(player.getBoundingBox(), projBox) && player.invincibilityTimer <= 0f) {
                player.takeDamage(1)
                hurtFlash = 0.25f
                proj.isAlive = false
                projIter.remove()
                spawnSparks(player.x, player.y - player.height / 2, 8, Color.YELLOW)
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
                player.x = lastSafeX
                player.y = lastSafeY
                player.vx = 0f
                player.vy = 0f
                player.isGrounded = true
                player.activeHitbox = null
                player.combat.cancel()
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
        spawnSparks(b.x, b.y - 30f, 18, Color.rgb(255, 230, 170))
    }

    // ===================================================================== bosses

    /** The doors of any fight in progress join the solids the player collides with. */
    private fun withGates(near: List<RectF>): List<RectF> {
        var shut = false
        for (rig in bossRigs) if (rig.encounter.gateClosed) { shut = true; break }
        if (!shut) return near
        frameSolids.clear()
        frameSolids.addAll(near)
        for (rig in bossRigs) if (rig.encounter.gateClosed) frameSolids.add(rig.gateRect)
        return frameSolids
    }

    /** The player fell: every fight in progress ends, bosses go back to sleep at full health. */
    private fun abortBossFights() {
        for (rig in bossRigs) rig.encounter.onPlayerDied()
    }

    private fun updateBosses(dt: Float) {
        for (rig in bossRigs) {
            val brain = rig.encounter.brain
            for (s in brain.shots) {
                projectiles.add(
                    Projectile(x = s.x, y = s.y, vx = s.vx, vy = s.vy, radius = s.radius, glowColor = rig.shotGlow, coreColor = rig.shotCore)
                )
            }
            brain.shots.clear()
            for (sig in brain.signals) onBossSignal(rig, sig)
            brain.signals.clear()

            val reward = rig.encounter.takeReward()
            if (reward > 0) grantBossReward(reward)

            val shut = if (rig.encounter.gateClosed) 1f else 0f
            rig.gate += (shut - rig.gate).coerceIn(-dt / 0.55f, dt / 0.55f)

            val fighting = rig.encounter.gateClosed
            rig.barAlpha += ((if (fighting) 1f else 0f) - rig.barAlpha).coerceIn(-dt / 0.6f, dt / 0.35f)
            val frac = brain.healthFraction
            if (!fighting && frac >= 0.999f) {
                rig.barHp = 1f
                rig.barGhost = 1f
            } else {
                rig.barHp += (frac - rig.barHp) * (1f - kotlin.math.exp(-dt * 14f))
                rig.barGhost = if (rig.barGhost > frac) max(frac, rig.barGhost - dt * 0.25f) else frac
            }
        }
    }

    private fun onBossSignal(rig: BossRig, s: BossSignal) {
        when (s.type) {
            SignalType.AWAKEN -> {
                triggerShake(7f, 0.5f)
                showTitle(rig.spec.name, rig.spec.title, 3.4f)
                bindHallBench(rig)
            }
            SignalType.HIT -> spawnSparks(s.x, s.y, 8, Color.rgb(255, 245, 215))
            SignalType.GUARD -> spawnSparks(s.x, s.y, 3, Color.rgb(150, 160, 190))
            SignalType.STAGGER -> {
                triggerShake(8f, 0.25f)
                spawnSparks(s.x, s.y, 16, Color.rgb(255, 224, 120))
            }
            SignalType.PHASE -> {
                triggerShake(14f, 0.7f)
                spawnSparks(s.x, s.y, 30, rig.spec.accent)
            }
            SignalType.DYING -> {
                triggerShake(16f, 0.9f)
                spawnSparks(s.x, s.y, 44, rig.spec.accent)
            }
            SignalType.DEFEATED -> {
                showTitle(rig.spec.name, rig.spec.defeatLine, 3.8f)
                spawnSparks(s.x, s.y, 40, rig.spec.accent)
            }
            SignalType.LAND -> {
                triggerShake(9f, 0.3f)
                spawnSparks(s.x, s.y, 10, Color.rgb(200, 190, 220))
            }
            SignalType.WALL_CRASH -> {
                triggerShake(16f, 0.45f)
                spawnSparks(s.x, s.y, 30, Color.rgb(255, 190, 240))
            }
            SignalType.SHOT -> spawnSparks(s.x, s.y, 5, rig.spec.accent)
            SignalType.TELEGRAPH, SignalType.STRIKE -> {}
        }
    }

    /**
     * A defeat must send the player back to the hall's door, not across the kingdom, even if they
     * never sat on its bench: the moment a fight starts, that bench becomes the respawn point.
     */
    private fun bindHallBench(rig: BossRig) {
        val bench = benches.firstOrNull { it.areaId == rig.spec.areaId } ?: return
        for (o in benches) o.isCurrent = false
        bench.isCurrent = true
        bench.discovered = true
        currentBench = bench
    }

    private fun showTitle(name: String, sub: String, duration: Float) {
        titleName = name
        titleSub = sub
        titleDuration = duration
        titleTimer = duration
    }

    /** One permanent mask per victory, granted exactly once by [BossEncounter.takeReward]. */
    private fun grantBossReward(masks: Int) {
        player.maxMasks += masks
        player.currentMasks = player.maxMasks
        player.soul = max(player.soul, 66)
        spawnSparks(player.x, player.y - player.height / 2f, 26, Color.rgb(255, 232, 160))
    }

    /** The fight's arena, while its doors are shut. */
    private fun fightingRig(): BossRig? {
        for (rig in bossRigs) if (rig.encounter.gateClosed) return rig
        return null
    }

    private fun updateCamera(dt: Float) {
        val look = if (player.facing == Facing.RIGHT) 70f else -70f
        var targetX = player.x + look - viewW / 2f
        // During a boss fight the view stays on the arena: it never wanders into the antechamber.
        val lock = fightingRig()
        if (lock != null) {
            val a = lock.arena
            val lo = a.left - 24f
            val hi = a.right + 24f - viewW
            targetX = if (hi >= lo) targetX.coerceIn(lo, hi) else a.centerX - viewW / 2f
        }

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

        // Grounded neighboring biome visibility: if player is within 400px of an adjacent biome boundary,
        // blend subtle parallax hints along the screen edge (PURELY VISUAL, separate from map discovery).
        for (s in scenes) {
            if (s.def.id != scene.def.id && s.biome != scene.biome) {
                val isLeft = s.bounds.right in (scene.bounds.left - 2f)..(scene.bounds.left + 2f) &&
                    s.bounds.bottom > scene.bounds.top && s.bounds.top < scene.bounds.bottom
                val isRight = s.bounds.left in (scene.bounds.right - 2f)..(scene.bounds.right + 2f) &&
                    s.bounds.bottom > scene.bounds.top && s.bounds.top < scene.bounds.bottom
                val isBelow = s.bounds.top in (scene.bounds.bottom - 2f)..(scene.bounds.bottom + 2f) &&
                    s.bounds.right > scene.bounds.left && s.bounds.left < scene.bounds.right

                val dir: Int
                val dist: Float
                if (isLeft) {
                    dir = -1
                    dist = (player.x - scene.bounds.left).coerceAtLeast(0f)
                } else if (isRight) {
                    dir = 1
                    dist = (scene.bounds.right - player.x).coerceAtLeast(0f)
                } else if (isBelow) {
                    dir = 0
                    dist = (scene.bounds.bottom - player.y).coerceAtLeast(0f)
                } else {
                    dir = 999
                    dist = Float.MAX_VALUE
                }

                if (dist < 400f) {
                    val alpha = ((1f - dist / 400f) * 0.35f).coerceIn(0f, 0.35f)
                    Backdrops.drawNeighborHint(canvas, paint, s.biome, s.pal, dir, alpha, camX, camY, viewW, viewH, gameTime)
                }
            }
        }

        // 2. world layer
        canvas.save()
        canvas.translate(-camX, -camY)
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = FILLER_COLOR
        for (f in fillers) if (RectF.intersects(f, view)) canvas.drawRect(f, paint)
        val cutKeys = grassCuts.cutKeys()
        for (s in scenes) if (RectF.intersects(s.bounds, view)) s.drawWorldWithCuts(canvas, paint, view, gameTime, cutKeys, s.def.id)
        for (b in benches) if (b.x > view.left - 80f && b.x < view.right + 80f && b.y > view.top - 80f && b.y < view.bottom + 80f) b.draw(canvas, paint, gameTime)

        for (rig in bossRigs) {
            if (rig.gate > 0.01f && RectF.intersects(rig.gateRect, view)) {
                BossArt.drawGate(canvas, paint, rig.arena.gate, rig.gate, rig.spec.accent, gameTime)
            }
        }
        for (enemy in enemies) {
            if (!enemy.isAlive) continue
            if (enemy.x < view.left - 200f || enemy.x > view.right + 200f || enemy.y < view.top - 200f || enemy.y > view.bottom + 200f) continue
            enemy.draw(canvas, paint)
        }
        for (proj in projectiles) proj.draw(canvas, paint)
        storyArea.drawWorld(canvas, paint, gameTime, view)
        player.draw(canvas, paint)
        for (s in scenes) if (RectF.intersects(s.bounds, view)) s.drawLights(canvas, paint, view)

        paint.shader = null
        paint.style = Paint.Style.FILL
        for (particle in particles) {
            paint.color = particle.color
            canvas.drawCircle(particle.x, particle.y, particle.radius, paint)
        }
        canvas.restore()

        // 3. front layer: weather, darkness, transitions, titles (design units)
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
        storyArea.drawDialogue(canvas, paint, screenWidth, screenHeight)
        if (player.state == PlayerState.DEAD) drawDefeatedScreen(canvas, paint, screenWidth, screenHeight)
        if (mapOpen) {
            worldMap.draw(canvas, paint, screenWidth, screenHeight, gameTime, discovered, benches, player.x, player.y, currentScene?.def?.id)
        }
    }

    private fun drawEdgeVignette(canvas: Canvas, paint: Paint) {
        // Soft darkening at the screen edges, cheap and constant.
        paint.shader = null
        paint.style = Paint.Style.FILL
        val a = 70
        val h = viewH * 0.18f
        for (i in 0 until 6) {
            paint.color = Color.argb(a / 6, 0, 0, 0)
            canvas.drawRect(0f, 0f, viewW, h * (1f - i / 6f), paint)
            canvas.drawRect(0f, viewH - h * (1f - i / 6f), viewW, viewH, paint)
        }
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
        val panel = RectF(18f, 16f, 278f + 32f * (player.maxMasks - 5), 84f)
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
                if (i == player.currentMasks - 1 && player.damageInCurrentMask > 0) {
                    // A subtle crack marks the first of two damage points absorbed by this mask.
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 1.6f
                    paint.color = Color.rgb(85, 105, 123)
                    canvas.drawLine(mx + 1f, my - 11f, mx - 2f, my - 2f, paint)
                    canvas.drawLine(mx - 2f, my - 2f, mx + 3f, my + 2f, paint)
                    paint.style = Paint.Style.FILL
                }
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
        drawBossBar(canvas, paint, screenWidth)
    }

    /** Name, hit points and phase mark of the boss being fought, in the same quiet panel style. */
    private fun drawBossBar(canvas: Canvas, paint: Paint, screenWidth: Float) {
        for (rig in bossRigs) {
            val a = rig.barAlpha
            if (a <= 0.01f) continue
            val w = min(screenWidth * 0.44f, 560f)
            val left = (screenWidth - w) / 2f
            val cx = screenWidth / 2f
            val top = 46f
            val h = 14f
            val accent = rig.spec.accent

            paint.shader = null
            paint.style = Paint.Style.FILL
            paint.typeface = serif
            paint.textAlign = Paint.Align.CENTER
            paint.letterSpacing = 0.16f
            paint.textSize = 17f
            paint.color = Color.argb((a * 235f).toInt(), 232, 236, 246)
            canvas.drawText(rig.spec.name.uppercase(), cx, top - 9f, paint)

            paint.color = Color.argb((a * 190f).toInt(), 8, 13, 23)
            canvas.drawRoundRect(left, top, left + w, top + h, 7f, 7f, paint)
            val inner = w - 6f
            if (rig.barGhost > rig.barHp) {
                paint.color = Color.argb((a * 120f).toInt(), 255, 255, 255)
                canvas.drawRoundRect(left + 3f, top + 3f, left + 3f + inner * rig.barGhost, top + h - 3f, 4f, 4f, paint)
            }
            paint.color = Color.argb((a * 255f).toInt(), Color.red(accent), Color.green(accent), Color.blue(accent))
            canvas.drawRoundRect(left + 3f, top + 3f, left + 3f + inner * rig.barHp.coerceIn(0f, 1f), top + h - 3f, 4f, 4f, paint)
            // where the second phase begins
            val notch = left + 3f + inner * (rig.spec.phase2Hp.toFloat() / rig.spec.maxHp)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f
            paint.color = Color.argb((a * 190f).toInt(), 200, 210, 230)
            canvas.drawLine(notch, top + 2f, notch, top + h - 2f, paint)
            paint.color = Color.argb((a * 155f).toInt(), 125, 165, 183)
            canvas.drawRoundRect(left, top, left + w, top + h, 7f, 7f, paint)

            paint.style = Paint.Style.FILL
            paint.letterSpacing = 0.08f
            paint.textSize = 11f
            paint.color = Color.argb((a * 170f).toInt(), 190, 200, 220)
            canvas.drawText(rig.spec.title, cx, top + h + 16f, paint)

            paint.letterSpacing = 0f
            paint.typeface = Typeface.DEFAULT
            paint.textAlign = Paint.Align.LEFT
            paint.alpha = 255
            paint.strokeWidth = 1f
        }
    }

    companion object {
        const val DESIGN_VIEW_H = 760f
        const val FADE_TIME = 0.7f
        const val ACTIVE_RANGE_X = 1500f
        const val ACTIVE_RANGE_Y = 1000f
        val FILLER_COLOR = Color.rgb(9, 10, 16)
    }
}
