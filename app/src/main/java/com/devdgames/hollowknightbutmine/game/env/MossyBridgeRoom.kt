package com.devdgames.hollowknightbutmine.game.env

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.devdgames.hollowknightbutmine.game.EnemyType
import kotlin.math.sin

/**
 * Biome 1: Moss Grotto & The Sunken Bridge (~4,400px).
 *
 * Three sections, each with a low route, a high route and a landmark:
 * - Verdant Shore (0–1400): terraces over a safe riverbed; a canopy climb
 *   loops back over the spawn shore to a soul cache. Landmark: the falls.
 * - The Sunken Bridge (1400–2950): a broken stone bridge over the lake,
 *   stepping-stone islets beneath it (falls are forgiving), and a canopy
 *   route far above. Landmark: the mushroom tree on the far shore.
 * - Carapace Shrine (2950–4400): causeway + terrace ascent to the shrine
 *   plaza, roof sanctum with a cache, gate arch at the biome seam.
 *   Landmark: the carapace shrine itself.
 */
class MossyBridgeRoom : Room(
    id = "mossy_bridge",
    title = "Moss Grotto",
    bounds = RectF(-100f, -250f, 4400f, 750f),
    subtitle = "The Sunken Bridge"
) {
    override val skyTop: Int = EnvPalette.mossSkyTop
    override val skyMid: Int = EnvPalette.mossSkyMid
    override val skyBottom: Int = EnvPalette.mossSkyBottom
    override val fogColor: Int = EnvPalette.mossFog

    private val lightShafts = ArrayList<LightShaft>()
    private val hangingTendrils = Path()
    private val shrinePath = Path()
    private val shrineDoor = Path()
    private val farRidge = Path()
    private val midRidge = Path()
    private val nearCeiling = Path()
    private val foreground = Path()
    private val wavePath = Path()
    private val bridgePillars = floatArrayOf(1740f, 1900f, 2080f, 2220f, 2380f, 2500f)

    override fun build() {
        spawnX = 140f
        spawnY = 460f

        // ============ SECTION 1: VERDANT SHORE & MOSS TERRACES (0–1400) ============
        addSlab(RectF(-100f, 480f, 420f, 620f), seed = 101, mossy = true)            // entry shore
        addSlab(RectF(420f, 560f, 960f, 620f), seed = 118, mossy = true)             // riverbed (safe floor)
        addSlab(RectF(880f, 510f, 985f, 540f), seed = 119, mossy = true)             // riverbed step up
        addSlab(RectF(480f, 435f, 640f, 465f), seed = 102, mossy = true)             // terrace 1
        addSlab(RectF(700f, 385f, 880f, 415f), seed = 103, mossy = true)             // terrace 2
        addSlab(RectF(580f, 310f, 740f, 340f), seed = 104, mossy = true)             // terrace 3
        addSlab(RectF(820f, 260f, 1060f, 290f), seed = 110, mossy = true)            // canopy A
        addSlab(RectF(640f, 185f, 800f, 215f), seed = 120, mossy = true)             // canopy B
        addSlab(RectF(400f, 140f, 580f, 170f), seed = 121, mossy = true)             // canopy nook (cache)
        addSlab(RectF(960f, 450f, 1340f, 500f), seed = 111, mossy = true)            // lower shore

        // ============ SECTION 2: THE SUNKEN BRIDGE & LAKE (1400–2950) ============
        addSlab(RectF(1420f, 420f, 1580f, 450f), seed = 112, mossy = true)           // water entry stone
        addCutStone(RectF(1660f, 360f, 1970f, 392f), seed = 105)                     // broken bridge I
        addCutStone(RectF(2030f, 360f, 2270f, 392f), seed = 122)                     // broken bridge II
        addCutStone(RectF(2330f, 370f, 2530f, 402f), seed = 123)                     // broken bridge III
        addSlab(RectF(1640f, 465f, 1745f, 495f), seed = 124, mossy = true)           // islet 0
        addSlab(RectF(1760f, 475f, 1960f, 505f), seed = 106, mossy = true)           // islet A
        addSlab(RectF(2040f, 470f, 2240f, 500f), seed = 113, mossy = true)           // islet B
        addSlab(RectF(2320f, 480f, 2480f, 510f), seed = 125, mossy = true)           // islet C
        addCutStone(RectF(2400f, 285f, 2560f, 315f), seed = 114)                     // vantage pillar top
        addSlab(RectF(2550f, 450f, 2950f, 520f), seed = 115, mossy = true)           // eastern shore
        // Canopy route high over the lake (skips the bridge fight, holds a cache)
        addSlab(RectF(1120f, 205f, 1290f, 235f), seed = 126, mossy = true)
        addSlab(RectF(1370f, 160f, 1540f, 190f), seed = 127, mossy = true)
        addSlab(RectF(1620f, 200f, 1790f, 230f), seed = 128, mossy = true)
        addSlab(RectF(1870f, 150f, 2040f, 180f), seed = 129, mossy = true)           // (cache)
        addSlab(RectF(2120f, 205f, 2290f, 235f), seed = 130, mossy = true)

        // ============ SECTION 3: CARAPACE SHRINE (2800–4400) ============
        addSlab(RectF(3020f, 400f, 3180f, 430f), seed = 116, mossy = true)           // ascent step
        addSlab(RectF(3240f, 340f, 3420f, 370f), seed = 117, mossy = true)           // ascent step
        addSlab(RectF(3100f, 452f, 3485f, 482f), seed = 131, mossy = true)           // low causeway (return path)
        addSlab(RectF(3480f, 460f, 4300f, 620f), seed = 107, mossy = true)           // shrine plaza
        addSlab(RectF(3680f, 372f, 3840f, 402f), seed = 109, mossy = true)           // shrine roof step
        addSlab(RectF(3880f, 284f, 4120f, 314f), seed = 108, mossy = true)           // sanctum
        addSlab(RectF(3960f, 198f, 4100f, 228f), seed = 132, mossy = true)           // sanctum crown (cache)
        addCutStone(RectF(4240f, 320f, 4380f, 350f), seed = 133)                     // seam gate beam

        // --- Boundaries, hazards, legacy exit ---
        solid(-100f, -300f, -20f, 750f)
        solid(4300f, -300f, 4400f, 750f)
        hazard(1340f, 590f, 2550f, 650f) // the lake
        exit(4240f, 360f, 4340f, 460f, "dark_ribs", 160f, 460f)

        // --- Benches & soul caches ---
        bench(230f, 480f)    // entry shore
        bench(2680f, 450f)   // eastern shore, under the mushroom tree
        bench(3600f, 460f)   // shrine plaza, before the Broodmother
        cache(490f, 140f)    // canopy nook above spawn
        cache(1955f, 150f)   // canopy route over the lake
        cache(4030f, 198f)   // sanctum crown

        // --- Enemies ---
        enemySpawns.add(EnemySpawn(x = 600f, y = 435f, type = EnemyType.CRAWLER))
        enemySpawns.add(EnemySpawn(x = 700f, y = 560f, type = EnemyType.CRAWLER))
        enemySpawns.add(EnemySpawn(x = 800f, y = 200f, type = EnemyType.FLYER))
        enemySpawns.add(EnemySpawn(x = 500f, y = 92f, type = EnemyType.FLYER))
        enemySpawns.add(EnemySpawn(x = 1150f, y = 450f, type = EnemyType.CRAWLER))
        enemySpawns.add(EnemySpawn(x = 1500f, y = 350f, type = EnemyType.FLYER))
        enemySpawns.add(EnemySpawn(x = 1900f, y = 360f, type = EnemyType.SPITTER))
        enemySpawns.add(EnemySpawn(x = 2150f, y = 360f, type = EnemyType.SHIELDER))
        enemySpawns.add(EnemySpawn(x = 2140f, y = 470f, type = EnemyType.CRAWLER))
        enemySpawns.add(EnemySpawn(x = 2480f, y = 285f, type = EnemyType.SPITTER))
        enemySpawns.add(EnemySpawn(x = 2560f, y = 210f, type = EnemyType.FLYER))
        enemySpawns.add(EnemySpawn(x = 1960f, y = 105f, type = EnemyType.FLYER))
        enemySpawns.add(EnemySpawn(x = 3100f, y = 400f, type = EnemyType.CRAWLER))
        enemySpawns.add(EnemySpawn(x = 3330f, y = 340f, type = EnemyType.SPITTER))
        enemySpawns.add(EnemySpawn(x = 3750f, y = 460f, type = EnemyType.SHIELDER))
        enemySpawns.add(EnemySpawn(x = 3950f, y = 460f, type = EnemyType.CRAWLER))
        enemySpawns.add(EnemySpawn(x = 4200f, y = 460f, boss = true, type = EnemyType.BROODMOTHER))

        // --- Light shafts (front layer) ---
        lightShafts.add(LightShaft(x = 550f, top = -180f, bottom = 560f, halfTop = 40f, halfBot = 120f, lean = 70f, color = EnvPalette.windowGlow, alpha = 42))
        lightShafts.add(LightShaft(x = 1320f, top = -180f, bottom = 560f, halfTop = 34f, halfBot = 100f, lean = -30f, color = EnvPalette.windowGlow, alpha = 52))
        lightShafts.add(LightShaft(x = 2050f, top = -180f, bottom = 560f, halfTop = 55f, halfBot = 150f, lean = 80f, color = EnvPalette.windowGlow, alpha = 48))
        lightShafts.add(LightShaft(x = 2750f, top = -180f, bottom = 520f, halfTop = 42f, halfBot = 120f, lean = -60f, color = EnvPalette.windowGlow, alpha = 44))
        lightShafts.add(LightShaft(x = 3920f, top = -180f, bottom = 480f, halfTop = 60f, halfBot = 165f, lean = 55f, color = EnvPalette.windowGlow, alpha = 58))

        // --- Backdrop silhouettes (authored in full room coords) ---
        EnvArt.ridge(farRidge, bounds.left, bounds.right, 660f, 240f, 460f, seed = 11, step = 150f)
        EnvArt.ridge(midRidge, bounds.left, bounds.right, 640f, 130f, 300f, seed = 23, step = 104f)
        EnvArt.stalactiteCeiling(nearCeiling, bounds.left, bounds.right, -300f, 120f, 300f, seed = 37, step = 88f)
        EnvArt.buildForeground(foreground, bounds.left, bounds.width(), seed = 41)

        // Carapace shrine silhouette (world layer).
        val sx = 3920f
        val sy = 460f
        shrinePath.moveTo(sx - 80f, sy)
        shrinePath.cubicTo(sx - 90f, sy - 180f, sx - 20f, sy - 280f, sx + 20f, sy - 280f)
        shrinePath.cubicTo(sx + 80f, sy - 250f, sx + 95f, sy - 140f, sx + 85f, sy)
        shrinePath.close()
        shrineDoor.moveTo(sx - 18f, sy)
        shrineDoor.lineTo(sx - 18f, sy - 60f)
        shrineDoor.quadTo(sx, sy - 85f, sx + 18f, sy - 60f)
        shrineDoor.lineTo(sx + 18f, sy)
        shrineDoor.close()

        // Ceiling tendrils (world layer).
        for (i in 0..28) {
            val tx = i * 150f + 50f
            val len = 60f + ((i * 37) % 85)
            EnvArt.tapered(hangingTendrils, tx, -120f, tx + 10f, -120f + len * 0.5f, tx + 5f, -120f + len, 8f, 1f, 6)
        }
    }

    override fun drawBackdrop(canvas: Canvas, paint: Paint, camX: Float, camY: Float, viewW: Float, viewH: Float, t: Float) {
        // FAR: misty stalagmite horizon + drifting glow blooms.
        val sxFar = beginLayer(canvas, 0.12f, camX, camY, viewW)
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = EnvPalette.mossFar
        canvas.drawRect(bounds.left, 620f, bounds.right, 1600f, paint)
        canvas.drawPath(farRidge, paint)
        for (i in 0..6) {
            val ox = 260f + i * 640f
            val oy = 210f + EnvArt.hash01(i * 7 + 3) * 150f + sin(t * 0.7f + i) * 12f
            EnvArt.bloom(canvas, paint, ox, oy, 95f / sxFar, 80f, EnvArt.glowTeal, 40)
        }
        endLayer(canvas)

        // MID: closer cliffs, giant fungi, a second broken bridge far over the lake.
        val sxMid = beginLayer(canvas, 0.30f, camX, camY, viewW)
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.mossMidLayer
        canvas.drawRect(bounds.left, 610f, bounds.right, 1600f, paint)
        canvas.drawPath(midRidge, paint)
        drawFarFungi(canvas, paint)
        // Distant bridge silhouette (deck + towers) behind the real bridge.
        paint.color = EnvPalette.mossMidLayer
        canvas.drawRect(1500f, 300f, 1560f, 560f, paint)
        canvas.drawRect(2420f, 290f, 2480f, 560f, paint)
        canvas.drawRect(1480f, 292f, 2500f, 306f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = EnvPalette.mossMidLayer
        canvas.drawArc(RectF(1560f, 330f, 2420f, 560f), 180f, 180f, false, paint)
        // Shrine dome, huge and hazy, behind the real shrine.
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.mossMidLayer
        canvas.drawOval(3740f, 90f, 4140f, 480f, paint)
        EnvArt.bloom(canvas, paint, 3940f, 300f, 120f / sxMid, 150f, EnvArt.glowTeal, 34)
        endLayer(canvas)

        // NEAR: ceiling teeth + the falls' mist column at the lake's west end.
        val sxNear = beginLayer(canvas, 0.55f, camX, camY, viewW)
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.mossNear
        canvas.drawPath(nearCeiling, paint)
        paint.color = EnvPalette.mossNear
        canvas.drawRect(-100f, -320f, 4400f, -300f, paint)
        EnvArt.bloom(canvas, paint, 1325f, 470f, 70f / sxNear, 130f, EnvArt.glowTeal, 30)
        endLayer(canvas)
        paint.alpha = 255
    }

    /** Stalks + caps of the giant background fungi (mid layer coords). */
    private fun drawFarFungi(canvas: Canvas, paint: Paint) {
        paint.style = Paint.Style.FILL
        for (i in 0..9) {
            val mx = 180f + i * 455f + EnvArt.hash01(i * 13) * 120f
            val my = 380f + EnvArt.hash01(i * 29) * 110f
            val s = 0.8f + EnvArt.hash01(i * 7) * 0.9f
            paint.color = EnvPalette.mossMidLayer
            canvas.drawRoundRect(mx - 6f * s, my, mx + 6f * s, 640f, 6f * s, 6f * s, paint)
            canvas.drawOval(mx - 52f * s, my - 26f * s, mx + 52f * s, my + 14f * s, paint)
            paint.color = EnvPalette.mossFog
            paint.alpha = 46
            canvas.drawOval(mx - 38f * s, my - 22f * s, mx + 38f * s, my + 4f * s, paint)
            paint.alpha = 255
        }
    }

    override fun drawWorld(canvas: Canvas, paint: Paint, t: Float, view: RectF) {
        // 1. Carapace shrine behind the plaza slabs.
        if (spanVisible(view, 3800f, 4060f)) drawShrine(canvas, paint)

        // 2. Seam gate pillars (the beam itself is a slab).
        if (spanVisible(view, 4220f, 4400f)) {
            paint.style = Paint.Style.FILL
            paint.color = EnvPalette.cutStoneBody
            canvas.drawRect(4262f, 348f, 4292f, 460f, paint)
            canvas.drawRect(4330f, 348f, 4360f, 460f, paint)
            paint.color = EnvPalette.cutStoneRim
            canvas.drawRect(4262f, 348f, 4292f, 354f, paint)
            canvas.drawRect(4330f, 348f, 4360f, 354f, paint)
            paint.color = EnvPalette.ivyDeep
            canvas.drawOval(4254f, 360f, 4300f, 410f, paint)
            canvas.drawOval(4326f, 392f, 4368f, 438f, paint)
        }

        // 3. Vantage pillar shaft down into the lake.
        if (spanVisible(view, 2430f, 2530f)) {
            paint.style = Paint.Style.FILL
            paint.color = EnvPalette.cutStoneShadow
            canvas.drawRect(2458f, 312f, 2502f, 575f, paint)
            paint.color = EnvPalette.cutStoneBody
            canvas.drawRect(2466f, 312f, 2476f, 575f, paint)
        }

        // 4. Slabs + animated grass on their crowns.
        drawSlabs(canvas, paint, t, view)
        drawSlabGrass(canvas, paint, t, view)

        // 5. Riverbed stream (shallow, safe).
        if (spanVisible(view, 420f, 960f)) {
            paint.style = Paint.Style.FILL
            paint.color = EnvPalette.waterMid
            paint.alpha = 160
            canvas.drawRect(436f, 551f, 944f, 560f, paint)
            paint.alpha = 255
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.6f
            paint.color = EnvPalette.waterTop
            wavyLine(wavePath, 440f, 940f, 552f, 1.6f, 46f, t * 2.4f)
            canvas.drawPath(wavePath, paint)
            EnvArt.drawCrystalCluster(canvas, paint, 905f, 560f, 26f, EnvPalette.sporeTeal, EnvPalette.waterMid, EnvArt.glowTeal)
        }

        // 6. The falls spilling off the lower shore into the lake.
        if (spanVisible(view, 1240f, 1420f)) {
            EnvArt.drawWaterfall(canvas, paint, 1332f, 452f, 562f, 44f, t)
        }

        // 7. The lake: layered water, waves, bridge pillars & reflections.
        if (spanVisible(view, 1340f, 2550f, 60f)) drawLake(canvas, paint, t)

        // 8. Mushroom tree over the eastern bench.
        if (spanVisible(view, 2660f, 2900f)) drawMushroomTree(canvas, paint, t)

        // 9. Ceiling tendrils.
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.ceilingRoot
        canvas.drawPath(hangingTendrils, paint)
        paint.alpha = 255
    }

    private fun drawShrine(canvas: Canvas, paint: Paint) {
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.midRock
        canvas.drawPath(shrinePath, paint)
        paint.style = Paint.Style.STROKE
        paint.color = EnvPalette.midRim
        paint.strokeWidth = 4f
        val sx = 3920f
        val sy = 460f
        for (r in 1..5) {
            val yOffset = r * 44f
            canvas.drawArc(RectF(sx - 75f + r * 5f, sy - yOffset - 50f, sx + 75f - r * 5f, sy - yOffset + 20f), 190f, 160f, false, paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.windowGlow
        canvas.drawPath(shrineDoor, paint)
        EnvArt.bloom(canvas, paint, sx, sy - 40f, 65f, 65f, EnvArt.glowTeal, 70)
    }

    private fun drawLake(canvas: Canvas, paint: Paint, t: Float) {
        // Bridge pillars first (behind the water sheen).
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.cutStoneShadow
        for (px in bridgePillars) canvas.drawRect(px - 11f, 390f, px + 11f, 580f, paint)
        paint.color = EnvPalette.cutStoneBody
        for (px in bridgePillars) canvas.drawRect(px - 11f, 390f, px - 5f, 580f, paint)

        // Water body: deep base, mid band, animated surface.
        paint.color = EnvPalette.waterDeep
        canvas.drawRect(1340f, 558f, 2550f, 720f, paint)
        paint.color = EnvPalette.waterMid
        paint.alpha = 170
        canvas.drawRect(1340f, 558f, 2550f, 596f, paint)
        paint.alpha = 255

        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 2.2f
        paint.color = EnvPalette.waterTop
        wavyLine(wavePath, 1340f, 2550f, 560f + sin(t * 2.2f) * 1.6f, 2.2f, 52f, t * 2.6f)
        canvas.drawPath(wavePath, paint)
        paint.strokeWidth = 1.4f
        paint.alpha = 120
        wavyLine(wavePath, 1340f, 2550f, 574f, 2.8f, 74f, -t * 1.7f)
        canvas.drawPath(wavePath, paint)
        wavyLine(wavePath, 1340f, 2550f, 590f, 3.4f, 96f, t * 1.2f)
        canvas.drawPath(wavePath, paint)
        paint.alpha = 255
        paint.strokeCap = Paint.Cap.BUTT

        // Drifting sparkles on the surface.
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.waterFoam
        for (i in 0..14) {
            val sxp = 1380f + ((i * 173f + t * (14f + (i % 4) * 6f)) % 1130f)
            val syp = 564f + (i % 5) * 7f + sin(t * 2f + i * 1.4f) * 1.6f
            paint.alpha = (70 + sin(t * 3f + i * 2.1f) * 55).toInt().coerceIn(8, 190)
            canvas.drawOval(sxp - 7f, syp - 1.2f, sxp + 7f, syp + 1.2f, paint)
        }
        paint.alpha = 255

        // Bioluminescent sheen pools.
        EnvArt.bloom(canvas, paint, 1600f, 566f, 230f, 26f, EnvArt.glowTeal, 58)
        EnvArt.bloom(canvas, paint, 2030f, 566f, 240f, 26f, EnvArt.glowTeal, 62)
        EnvArt.bloom(canvas, paint, 2380f, 566f, 200f, 26f, EnvArt.glowTeal, 54)
    }

    private fun drawMushroomTree(canvas: Canvas, paint: Paint, t: Float) {
        val x = 2780f
        val base = 450f
        // Trunk with a gentle lean.
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.midRock
        EnvArt.scratch.reset()
        EnvArt.scratch.moveTo(x - 17f, base)
        EnvArt.scratch.quadTo(x - 10f, base - 90f, x - 4f, base - 168f)
        EnvArt.scratch.lineTo(x + 14f, base - 168f)
        EnvArt.scratch.quadTo(x + 10f, base - 85f, x + 19f, base)
        EnvArt.scratch.close()
        canvas.drawPath(EnvArt.scratch, paint)
        // Cap.
        paint.color = EnvPalette.mushCap
        paint.alpha = 215
        canvas.drawOval(x - 88f, base - 205f, x + 102f, base - 148f, paint)
        paint.color = EnvPalette.sporeTeal
        paint.alpha = 150
        canvas.drawCircle(x - 34f, base - 184f, 6f, paint)
        canvas.drawCircle(x + 22f, base - 192f, 8f, paint)
        canvas.drawCircle(x + 58f, base - 176f, 5f, paint)
        paint.alpha = 255
        EnvArt.bloom(canvas, paint, x + 6f, base - 172f, 120f, 44f, EnvArt.glowTeal, 58)
        // Sapling shelf mushrooms at its foot.
        EnvArt.drawGlowMushroom(canvas, paint, x - 44f, base, 1.5f)
        EnvArt.drawGlowMushroom(canvas, paint, x + 52f, base, 1.1f)
        EnvArt.drawGlowMushroom(canvas, paint, x + 78f, base, 0.8f)
    }

    override fun drawFront(canvas: Canvas, paint: Paint, camX: Float, camY: Float, viewW: Float, viewH: Float, t: Float) {
        // Volumetric shafts + spores in world space.
        canvas.save()
        canvas.translate(-camX, -camY)
        for (shaft in lightShafts) shaft.draw(canvas, paint)
        EnvArt.drawMotes(
            canvas, paint, t,
            areaW = bounds.width(), areaH = 760f, count = 46, seed = 7,
            viewL = camX, viewR = camX + viewW
        )
        canvas.restore()

        // Out-of-focus foreground roots sweeping past at 1.22x.
        beginLayer(canvas, 1.22f, camX, camY, viewW)
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = EnvPalette.foreground
        paint.alpha = 150
        canvas.drawPath(foreground, paint)
        endLayer(canvas)
        paint.alpha = 255
    }
}
