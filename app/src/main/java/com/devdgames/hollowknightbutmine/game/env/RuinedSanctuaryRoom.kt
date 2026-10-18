package com.devdgames.hollowknightbutmine.game.env

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.devdgames.hollowknightbutmine.game.EnemyType
import kotlin.math.sin

/**
 * Biome 3: The Ruined Sanctuary & Grand Colonnade (~4,800px).
 *
 * - Outer Portico (0–1500): courtyard terraces plus a climbable bell tower
 *   on the west wall (cache in the belfry). Landmark: the broken statue.
 * - Grand Balconies (1500–3200): two-story balconies and chandelier ledges
 *   above; below, the colonnade floor is broken by two shafts into a
 *   candle-lit crypt with its own bench and cache.
 * - Throne Sanctum (3200–4800): audience galleries, a hanging ledge over
 *   the arena, braziers and the royal box (cache). Landmark: rose window.
 */
class RuinedSanctuaryRoom : Room(
    id = "ruined_sanctuary",
    title = "The Overgrown Colonnade",
    bounds = RectF(-100f, -250f, 4800f, 750f),
    subtitle = "Ruined Sanctuary of the Pale Court"
) {
    override val skyTop: Int = EnvPalette.sanctSkyTop
    override val skyMid: Int = EnvPalette.sanctSkyMid
    override val skyBottom: Int = EnvPalette.sanctSkyBottom
    override val fogColor: Int = EnvPalette.sanctFog

    private val lightShafts = ArrayList<LightShaft>()
    private val lushVines = Path()
    private val cathedralArches = Path()
    private val farTowers = Path()
    private val farRidge = Path()
    private val nearCeiling = Path()
    private val foreground = Path()
    private val cryptRoots = Path()
    private val pillarX = floatArrayOf(1880f, 2180f, 2660f, 2960f, 3600f, 4400f, 4600f)

    override fun build() {
        spawnX = 140f
        spawnY = 460f

        // ============ SECTION 1: OUTER PORTICO & COURTYARD (0–1500) ============
        addCutStone(RectF(-100f, 480f, 1500f, 620f), seed = 301)                      // courtyard floor
        addCutStone(RectF(320f, 410f, 480f, 440f), seed = 309)                        // terraces
        addCutStone(RectF(540f, 340f, 720f, 370f), seed = 310)
        addCutStone(RectF(780f, 270f, 960f, 300f), seed = 311)
        addCutStone(RectF(1020f, 340f, 1200f, 370f), seed = 312)
        addCutStone(RectF(1260f, 410f, 1420f, 440f), seed = 313)
        // Bell tower spiralling up the west wall.
        addCutStone(RectF(140f, 395f, 265f, 425f), seed = 322)
        addCutStone(RectF(305f, 320f, 430f, 350f), seed = 323)
        addCutStone(RectF(160f, 245f, 285f, 275f), seed = 324)
        addCutStone(RectF(325f, 170f, 450f, 200f), seed = 325)
        addCutStone(RectF(185f, 95f, 345f, 125f), seed = 326)                         // belfry (cache)

        // ============ SECTION 2: BALCONIES & THE CRYPT (1500–3200) ============
        // Colonnade floor in three segments; two shafts drop into the crypt.
        addCutStone(RectF(1500f, 480f, 2100f, 560f), seed = 314)
        addCutStone(RectF(2280f, 480f, 2680f, 560f), seed = 327)
        addCutStone(RectF(2860f, 480f, 3200f, 560f), seed = 328)
        // Balconies.
        addCutStone(RectF(1560f, 410f, 1720f, 440f), seed = 306)                      // ascent step
        addCutStone(RectF(1780f, 340f, 2280f, 375f), seed = 302)                      // balcony I
        addCutStone(RectF(2340f, 355f, 2480f, 385f), seed = 307)                      // sky bridge (low)
        addCutStone(RectF(2360f, 275f, 2500f, 305f), seed = 304)                      // sky perch
        addCutStone(RectF(2560f, 340f, 3060f, 375f), seed = 303)                      // balcony II
        addCutStone(RectF(2000f, 260f, 2180f, 290f), seed = 315)                      // chandelier I
        addCutStone(RectF(2700f, 260f, 2880f, 290f), seed = 316)                      // chandelier II (cache)
        addCutStone(RectF(2220f, 185f, 2380f, 215f), seed = 329)                      // high traverse I
        addCutStone(RectF(2520f, 185f, 2660f, 215f), seed = 330)                      // high traverse II
        // The crypt below the floor.
        addCutStone(RectF(2040f, 700f, 2920f, 745f), seed = 331, mossy = false)       // crypt floor
        addCutStone(RectF(2000f, 560f, 2040f, 750f), seed = 332, mossy = false)       // crypt west wall
        addCutStone(RectF(2920f, 560f, 2960f, 750f), seed = 333, mossy = false)       // crypt east wall
        addCutStone(RectF(2140f, 620f, 2240f, 648f), seed = 334, mossy = false)       // shaft I steps
        addCutStone(RectF(2180f, 545f, 2265f, 573f), seed = 335, mossy = false)
        addCutStone(RectF(2720f, 620f, 2820f, 648f), seed = 336, mossy = false)       // shaft II steps
        addCutStone(RectF(2740f, 545f, 2845f, 573f), seed = 337, mossy = false)

        // ============ SECTION 3: THE THRONE ARENA (3200–4800) ============
        addCutStone(RectF(3200f, 480f, 4700f, 620f), seed = 317)                      // arena floor
        addCutStone(RectF(3300f, 410f, 3480f, 440f), seed = 318)                      // audience steps
        addCutStone(RectF(3420f, 335f, 3620f, 365f), seed = 319)
        addCutStone(RectF(3560f, 255f, 3740f, 285f), seed = 338)                      // high gallery
        addCutStone(RectF(3860f, 300f, 4020f, 330f), seed = 339)                      // hanging ledge over arena
        addCutStone(RectF(4200f, 410f, 4352f, 440f), seed = 340)                      // royal box stair
        addCutStone(RectF(4350f, 340f, 4650f, 370f), seed = 320)                      // royal box
        addCutStone(RectF(4450f, 265f, 4650f, 295f), seed = 321)                      // royal box crown (cache)

        // --- Boundaries / legacy exit ---
        solid(-100f, -350f, -20f, 750f)
        solid(4680f, -350f, 4800f, 750f)
        exit(-60f, 380f, 20f, 480f, "dark_ribs", 4380f, 440f)

        // --- Benches & soul caches ---
        bench(340f, 480f)    // courtyard, under the bell tower
        bench(2320f, 700f)   // crypt
        bench(3260f, 480f)   // arena approach, before the Knight
        cache(265f, 95f)     // belfry
        cache(2790f, 260f)   // chandelier II
        cache(2560f, 700f)   // crypt centre
        cache(4550f, 265f)   // royal box crown

        // --- Enemies ---
        enemySpawns.add(EnemySpawn(x = 630f, y = 340f, type = EnemyType.CRAWLER))
        enemySpawns.add(EnemySpawn(x = 265f, y = 52f, type = EnemyType.FLYER))
        enemySpawns.add(EnemySpawn(x = 880f, y = 205f, type = EnemyType.FLYER))
        enemySpawns.add(EnemySpawn(x = 1110f, y = 340f, type = EnemyType.SPITTER))
        enemySpawns.add(EnemySpawn(x = 1450f, y = 480f, type = EnemyType.CRAWLER))
        enemySpawns.add(EnemySpawn(x = 1950f, y = 340f, type = EnemyType.SPITTER))
        enemySpawns.add(EnemySpawn(x = 2150f, y = 340f, type = EnemyType.SHIELDER))
        enemySpawns.add(EnemySpawn(x = 2430f, y = 205f, type = EnemyType.FLYER))
        enemySpawns.add(EnemySpawn(x = 2590f, y = 120f, type = EnemyType.FLYER))
        enemySpawns.add(EnemySpawn(x = 2750f, y = 340f, type = EnemyType.SHIELDER))
        enemySpawns.add(EnemySpawn(x = 2950f, y = 340f, type = EnemyType.SPITTER))
        enemySpawns.add(EnemySpawn(x = 2440f, y = 700f, type = EnemyType.CRAWLER))
        enemySpawns.add(EnemySpawn(x = 2660f, y = 700f, type = EnemyType.SPITTER))
        enemySpawns.add(EnemySpawn(x = 3520f, y = 335f, type = EnemyType.SHIELDER))
        enemySpawns.add(EnemySpawn(x = 4550f, y = 205f, type = EnemyType.FLYER))
        enemySpawns.add(EnemySpawn(x = 4050f, y = 480f, boss = true, type = EnemyType.COLONNADE_KNIGHT))

        // --- God rays (front layer) ---
        lightShafts.add(LightShaft(x = 300f, top = -200f, bottom = 480f, halfTop = 36f, halfBot = 105f, lean = 45f, color = EnvPalette.windowGlow, alpha = 40))
        lightShafts.add(LightShaft(x = 900f, top = -200f, bottom = 540f, halfTop = 45f, halfBot = 130f, lean = 60f, color = EnvPalette.windowGlow, alpha = 48))
        lightShafts.add(LightShaft(x = 2050f, top = -200f, bottom = 540f, halfTop = 50f, halfBot = 150f, lean = 70f, color = EnvPalette.windowGlow, alpha = 52))
        lightShafts.add(LightShaft(x = 2820f, top = -200f, bottom = 540f, halfTop = 50f, halfBot = 150f, lean = -60f, color = EnvPalette.windowGlow, alpha = 52))
        lightShafts.add(LightShaft(x = 4100f, top = -200f, bottom = 540f, halfTop = 68f, halfBot = 185f, lean = 80f, color = EnvPalette.sporeWarm, alpha = 56))

        // --- Backdrop silhouettes ---
        EnvArt.ridge(farRidge, bounds.left, bounds.right, 660f, 150f, 320f, seed = 97, step = 170f)
        EnvArt.stalactiteCeiling(nearCeiling, bounds.left, bounds.right, -300f, 110f, 280f, seed = 103, step = 96f)
        EnvArt.buildForeground(foreground, bounds.left, bounds.width(), seed = 109)

        // Distant ruined towers with pitched roofs.
        for (i in 0..14) {
            val tx = 40f + i * 330f + EnvArt.hash01(i * 7) * 60f
            val towerH = 170f + EnvArt.hash01(i * 13) * 210f
            val topY = 500f - towerH
            farTowers.addRect(tx, topY, tx + 84f, 540f, Path.Direction.CW)
            farTowers.moveTo(tx - 10f, topY)
            farTowers.lineTo(tx + 42f, topY - 44f)
            farTowers.lineTo(tx + 94f, topY)
            farTowers.close()
        }
        // Grand gothic arches (mid layer).
        for (i in 0..15) {
            val ax = i * 300f + 80f
            cathedralArches.moveTo(ax - 58f, 500f)
            cathedralArches.lineTo(ax - 58f, 210f)
            cathedralArches.quadTo(ax, 118f, ax + 58f, 210f)
            cathedralArches.lineTo(ax + 58f, 500f)
        }
        // Overgrown vines (world layer ceiling).
        for (i in 0..32) {
            val vx = i * 140f + 40f
            val len = 70f + ((i * 51) % 115)
            EnvArt.tapered(lushVines, vx, -130f, vx + 12f, -130f + len * 0.5f, vx + 6f, -130f + len, 10f, 1.2f, 6)
        }
        // Roots through the crypt ceiling.
        for (i in 0..9) {
            val vx = 2080f + i * 88f + EnvArt.hash01(i * 3) * 30f
            val len = 26f + EnvArt.hash01(i * 11) * 44f
            EnvArt.tapered(cryptRoots, vx, 558f, vx + 4f, 558f + len * 0.5f, vx + 2f, 558f + len, 5f, 0.8f, 5)
        }
    }

    override fun drawBackdrop(canvas: Canvas, paint: Paint, camX: Float, camY: Float, viewW: Float, viewH: Float, t: Float) {
        // FAR: broken towers + ridge + the rose window over the throne.
        val sxFar = beginLayer(canvas, 0.12f, camX, camY, viewW)
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = EnvPalette.sanctFar
        canvas.drawPath(farRidge, paint)
        canvas.drawPath(farTowers, paint)
        drawRoseWindow(canvas, paint, 4080f, 215f, sxFar, t)
        drawTowerWindows(canvas, paint, t)
        endLayer(canvas)

        // MID: cathedral arches, swaying court banners, window glows.
        beginLayer(canvas, 0.30f, camX, camY, viewW)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 17f
        paint.color = EnvPalette.sanctMidLayer
        canvas.drawPath(cathedralArches, paint)
        paint.style = Paint.Style.FILL
        for (i in 0..15) {
            val wx = i * 300f + 80f
            EnvArt.bloom(canvas, paint, wx, 235f, 42f, 82f, EnvArt.glowGold, 36)
        }
        drawBanners(canvas, paint, t)
        endLayer(canvas)

        // NEAR: vine-choked ceiling band.
        beginLayer(canvas, 0.55f, camX, camY, viewW)
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.sanctNear
        canvas.drawPath(nearCeiling, paint)
        canvas.drawRect(-100f, -320f, 4800f, -300f, paint)
        endLayer(canvas)
        paint.alpha = 255
    }

    /** The great rose window behind the throne (far layer coords). */
    private fun drawRoseWindow(canvas: Canvas, paint: Paint, cx: Float, cy: Float, sx: Float, t: Float) {
        val pulse = (44 + sin(t * 0.6f) * 10).toInt().coerceIn(16, 90)
        EnvArt.bloom(canvas, paint, cx, cy, 150f / sx.coerceAtLeast(0.05f), 150f, EnvArt.glowGold, pulse)
        paint.style = Paint.Style.STROKE
        paint.color = EnvPalette.sanctFar
        paint.strokeWidth = 10f
        canvas.drawCircle(cx, cy, 108f, paint)
        paint.strokeWidth = 5f
        canvas.drawCircle(cx, cy, 62f, paint)
        canvas.drawCircle(cx, cy, 22f, paint)
        paint.strokeWidth = 4f
        for (k in 0..7) {
            val a = k * 0.7853982f
            canvas.drawLine(
                cx + kotlin.math.cos(a) * 22f, cy + sin(a) * 22f,
                cx + kotlin.math.cos(a) * 108f, cy + sin(a) * 108f, paint
            )
        }
    }

    /** Pinprick lit windows in the far towers. */
    private fun drawTowerWindows(canvas: Canvas, paint: Paint, t: Float) {
        paint.style = Paint.Style.FILL
        for (i in 0..14) {
            val tx = 40f + i * 330f + EnvArt.hash01(i * 7) * 60f
            val towerH = 170f + EnvArt.hash01(i * 13) * 210f
            val topY = 500f - towerH
            for (w in 0..2) {
                val lit = EnvArt.hash01(i * 31 + w * 7) > 0.35f
                if (!lit) continue
                val flicker = (56 + sin(t * 1.3f + i + w * 2f) * 22).toInt().coerceIn(12, 130)
                paint.color = EnvPalette.sporeWarm
                paint.alpha = flicker
                val wx = tx + 16f + w * 22f
                val wy = topY + 26f + ((i + w) % 4) * 34f
                canvas.drawRoundRect(wx, wy, wx + 7f, wy + 13f, 3f, 3f, paint)
            }
        }
        paint.alpha = 255
    }

    /** Tattered court banners swaying under the arches (mid layer). */
    private fun drawBanners(canvas: Canvas, paint: Paint, t: Float) {
        paint.style = Paint.Style.FILL
        for (i in 0..7) {
            val bx = 240f + i * 590f
            val sway = sin(t * 1.1f + i * 1.4f) * 7f
            paint.color = EnvPalette.clothMid
            paint.alpha = 205
            EnvArt.scratch.reset()
            EnvArt.scratch.moveTo(bx, 108f)
            EnvArt.scratch.lineTo(bx + 30f, 108f)
            EnvArt.scratch.lineTo(bx + 27f + sway, 196f)
            EnvArt.scratch.lineTo(bx + 15f + sway * 0.8f, 178f)
            EnvArt.scratch.lineTo(bx + 3f + sway, 198f)
            EnvArt.scratch.close()
            canvas.drawPath(EnvArt.scratch, paint)
            paint.color = EnvPalette.clothDeep
            canvas.drawCircle(bx + 15f + sway * 0.5f, 138f, 6f, paint)
        }
        paint.alpha = 255
    }

    override fun drawWorld(canvas: Canvas, paint: Paint, t: Float, view: RectF) {
        // 1. Throne silhouette behind the boss arena.
        if (spanVisible(view, 4040f, 4280f)) drawThrone(canvas, paint)

        // 2. Balcony support pillars.
        paint.style = Paint.Style.FILL
        for (px in pillarX) {
            if (!nearView(view, px, 120f)) continue
            val isRoyal = px >= 4400f
            val top = if (isRoyal) 368f else 372f
            paint.color = EnvPalette.cutStoneShadow
            canvas.drawRect(px - 13f, top, px + 13f, 480f, paint)
            paint.color = EnvPalette.cutStoneBody
            canvas.drawRect(px - 13f, top, px - 6f, 480f, paint)
            paint.color = EnvPalette.mossDeep
            canvas.drawRoundRect(px - 19f, top - 4f, px + 19f, top + 12f, 6f, 6f, paint)
            canvas.drawRoundRect(px - 18f, 464f, px + 18f, 480f, 5f, 5f, paint)
        }

        // 3. Slabs + animated grass.
        drawSlabs(canvas, paint, t, view)
        drawSlabGrass(canvas, paint, t, view)

        // 4. The belfry bell.
        if (spanVisible(view, 160f, 380f)) drawBell(canvas, paint, t)

        // 5. Broken statue in the courtyard.
        if (spanVisible(view, 660f, 900f)) drawBrokenStatue(canvas, paint)

        // 6. Crypt dressing: ceiling roots + leaning coffin lids.
        if (spanVisible(view, 2000f, 2960f)) {
            paint.style = Paint.Style.FILL
            paint.color = EnvPalette.ceilingRoot
            canvas.drawPath(cryptRoots, paint)
            paint.color = EnvPalette.cutStoneBody
            canvas.save()
            canvas.translate(2120f, 700f)
            canvas.rotate(-14f)
            canvas.drawRoundRect(-11f, -74f, 11f, 0f, 9f, 9f, paint)
            canvas.restore()
            canvas.save()
            canvas.translate(2866f, 700f)
            canvas.rotate(11f)
            canvas.drawRoundRect(-11f, -68f, 11f, 0f, 9f, 9f, paint)
            canvas.restore()
            paint.color = EnvPalette.candleGold
            for (k in 0..3) {
                val flicker = (150 + sin(t * 6f + k * 2.2f) * 60).toInt().coerceIn(40, 255)
                paint.alpha = flicker
                canvas.drawCircle(2296f + k * 9f, 692f - (k % 2) * 5f, 2.2f, paint)
            }
            paint.alpha = 255
            EnvArt.bloom(canvas, paint, 2310f, 690f, 40f, 30f, EnvArt.glowWarm, 60)
        }

        // 7. Arena braziers.
        drawBrazier(canvas, paint, 3806f, 480f, t, view)
        drawBrazier(canvas, paint, 4394f, 480f, t, view)

        // 8. Ceiling vines.
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.ivyDeep
        canvas.drawPath(lushVines, paint)

        // 9. Pale flower clusters along the balcony crowns.
        paint.color = EnvPalette.mossLit
        for (i in 0..16) {
            val fx = 1800f + i * 28f
            if (fx > view.left - 20f && fx < view.right + 20f) {
                canvas.drawCircle(fx, 338f + sin(fx * 0.09f) * 2.5f, 3.2f, paint)
            }
            val gx = 2580f + i * 28f
            if (gx > view.left - 20f && gx < view.right + 20f) {
                canvas.drawCircle(gx, 338f + sin(gx * 0.09f) * 2.5f, 3.2f, paint)
            }
        }
        paint.alpha = 255
    }

    private fun drawThrone(canvas: Canvas, paint: Paint) {
        val cx = 4152f
        val base = 480f
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.midRock
        canvas.drawRect(cx - 46f, base - 210f, cx + 46f, base, paint)
        canvas.drawRect(cx - 66f, base - 70f, cx + 66f, base, paint)
        paint.color = EnvPalette.cutStoneRim
        paint.alpha = 150
        canvas.drawRect(cx - 46f, base - 210f, cx + 46f, base - 196f, paint)
        paint.alpha = 255
        paint.color = EnvPalette.midRock
        canvas.drawOval(cx - 58f, base - 252f, cx - 34f, base - 196f, paint)
        canvas.drawOval(cx + 34f, base - 252f, cx + 58f, base - 196f, paint)
        EnvArt.bloom(canvas, paint, cx, base - 150f, 90f, 130f, EnvArt.glowGold, 40)
    }

    private fun drawBell(canvas: Canvas, paint: Paint, t: Float) {
        val cx = 265f
        val top = 125f
        val swing = sin(t * 0.9f) * 2.4f
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.cutStoneRim
        paint.alpha = 220
        EnvArt.scratch.reset()
        EnvArt.scratch.moveTo(cx - 7f + swing * 0.2f, top)
        EnvArt.scratch.lineTo(cx + 7f + swing * 0.2f, top)
        EnvArt.scratch.quadTo(cx + 15f + swing, top + 26f, cx + 19f + swing, top + 38f)
        EnvArt.scratch.lineTo(cx - 19f + swing, top + 38f)
        EnvArt.scratch.quadTo(cx - 15f + swing, top + 26f, cx - 7f + swing * 0.2f, top)
        EnvArt.scratch.close()
        canvas.drawPath(EnvArt.scratch, paint)
        paint.color = EnvPalette.sporeWarm
        canvas.drawCircle(cx + swing, top + 42f, 4f, paint)
        paint.alpha = 255
        EnvArt.bloom(canvas, paint, cx + swing, top + 22f, 42f, 40f, EnvArt.glowWarm, 52)
    }

    private fun drawBrokenStatue(canvas: Canvas, paint: Paint) {
        val base = 480f
        val x = 762f
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.midRock
        canvas.drawRect(x - 44f, base - 26f, x + 44f, base, paint)          // plinth
        canvas.drawOval(x - 26f, base - 128f, x + 26f, base - 24f, paint)   // torso
        canvas.drawRect(x - 8f, base - 150f, x + 8f, base - 120f, paint)    // neck stub
        paint.color = EnvPalette.stoneMid
        canvas.drawCircle(x + 74f, base - 17f, 17f, paint)                  // fallen head
        paint.color = EnvPalette.ivyDeep
        canvas.drawOval(x - 30f, base - 96f, x + 6f, base - 58f, paint)     // ivy over shoulder
        paint.color = EnvPalette.cutStoneRim
        paint.alpha = 120
        canvas.drawCircle(x + 68f, base - 20f, 3f, paint)                   // eye glint
        canvas.drawCircle(x + 82f, base - 20f, 3f, paint)
        paint.alpha = 255
    }

    private fun drawBrazier(canvas: Canvas, paint: Paint, x: Float, floorY: Float, t: Float, view: RectF) {
        if (!nearView(view, x, 220f)) return
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.cutStoneShadow
        canvas.drawRect(x - 4f, floorY - 30f, x + 4f, floorY, paint)
        canvas.drawOval(x - 19f, floorY - 44f, x + 19f, floorY - 24f, paint)
        paint.color = EnvPalette.brazierEmber
        val flameH = 15f + sin(t * 7.3f + x) * 4f
        paint.alpha = 215
        canvas.drawOval(x - 8f, floorY - 42f - flameH, x + 8f, floorY - 30f, paint)
        paint.color = EnvPalette.candleGold
        canvas.drawOval(x - 4f, floorY - 40f - flameH * 0.7f, x + 4f, floorY - 32f, paint)
        paint.alpha = 255
        EnvArt.bloom(canvas, paint, x, floorY - 40f, 58f, 52f, EnvArt.glowWarm, 78)
    }

    override fun drawFront(canvas: Canvas, paint: Paint, camX: Float, camY: Float, viewW: Float, viewH: Float, t: Float) {
        canvas.save()
        canvas.translate(-camX, -camY)
        for (shaft in lightShafts) shaft.draw(canvas, paint)
        EnvArt.drawMotes(
            canvas, paint, t,
            areaW = bounds.width(), areaH = 760f, count = 44, seed = 29,
            viewL = camX, viewR = camX + viewW, warmEvery = 2
        )
        canvas.restore()

        beginLayer(canvas, 1.22f, camX, camY, viewW)
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = EnvPalette.foreground
        paint.alpha = 145
        canvas.drawPath(foreground, paint)
        endLayer(canvas)
        paint.alpha = 255
    }
}
