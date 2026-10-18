package com.devdgames.hollowknightbutmine.game.env

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.devdgames.hollowknightbutmine.game.EnemyType
import kotlin.math.sin

/**
 * Biome 2: Ossuary Depths & The Great Fossil Chasm (~4,600px).
 *
 * - Bone Caverns (0–1500): spine-step climb to a high loft (cache), with a
 *   chain of bone rafts low over the spike trench as a second, safer route —
 *   falling off the spine no longer means restarting the biome.
 * - Rib Citadel (1500–3000): two fossil towers joined by a bridge, a crown
 *   beam above it (cache), buttress stairs so the trenches can be climbed.
 * - Catacomb Vaults (3000–4600): high vault ledges (cache at the crown),
 *   and below them a sunken crypt with its own bench and guarded cache.
 *   Landmark: the giant skull watching the catacombs.
 */
class DarkRibsRoom : Room(
    id = "dark_ribs",
    title = "Ossuary Depths",
    bounds = RectF(-100f, -250f, 4600f, 750f),
    subtitle = "The Fossil Chasm"
) {
    override val skyTop: Int = EnvPalette.boneSkyTop
    override val skyMid: Int = EnvPalette.boneSkyMid
    override val skyBottom: Int = EnvPalette.boneSkyBottom
    override val fogColor: Int = EnvPalette.boneFog

    private val lightShafts = ArrayList<LightShaft>()
    private val ribArchesFar = Path()
    private val ribArchesMid = Path()
    private val stalactites = Path()
    private val farRidge = Path()
    private val midRidge = Path()
    private val nearCeiling = Path()
    private val foreground = Path()
    private val spikesDark = Path()
    private val spikesLit = Path()

    override fun build() {
        spawnX = 140f
        spawnY = 450f

        // ============ SECTION 1: BONE CAVERNS & SPINE STEPS (0–1500) ============
        addSlab(RectF(-100f, 460f, 380f, 620f), seed = 201, mossy = false, body = EnvPalette.stoneBody, rim = EnvPalette.stoneLit) // entry (flush with Mossy plaza)
        addBone(RectF(440f, 420f, 600f, 450f), seed = 202)   // spine 1
        addBone(RectF(660f, 355f, 820f, 385f), seed = 203)   // spine 2
        addBone(RectF(520f, 280f, 680f, 310f), seed = 204)   // spine 3
        addBone(RectF(700f, 205f, 860f, 235f), seed = 220)   // loft 1
        addBone(RectF(520f, 130f, 700f, 160f), seed = 221)   // loft 2 (cache)
        addSlab(RectF(880f, 410f, 1140f, 440f), seed = 210, mossy = false, body = EnvPalette.stoneBody, rim = EnvPalette.stoneLit) // rest ledge
        addBone(RectF(1200f, 340f, 1380f, 370f), seed = 211) // high step
        // Bone rafts: low route / recovery chain across trench 1.
        addBone(RectF(500f, 530f, 640f, 560f), seed = 222)
        addBone(RectF(740f, 525f, 880f, 555f), seed = 223)
        addBone(RectF(980f, 530f, 1120f, 560f), seed = 224)
        addBone(RectF(1220f, 525f, 1360f, 555f), seed = 225)
        addBone(RectF(1460f, 530f, 1600f, 560f), seed = 226)
        addBone(RectF(1520f, 475f, 1620f, 505f), seed = 227) // climb-out step
        addBone(RectF(1460f, 430f, 1620f, 460f), seed = 209) // citadel approach

        // ============ SECTION 2: THE RIB CITADEL (1500–3000) ============
        addBone(RectF(1680f, 340f, 1880f, 620f), seed = 205) // tower 1
        addBone(RectF(1940f, 270f, 2180f, 300f), seed = 212) // tower bridge
        addBone(RectF(2240f, 340f, 2440f, 620f), seed = 213) // tower 2
        addBone(RectF(1720f, 255f, 1840f, 285f), seed = 228) // crown 1
        addBone(RectF(1900f, 190f, 2220f, 220f), seed = 229) // crown beam (cache)
        addBone(RectF(2280f, 255f, 2400f, 285f), seed = 230) // crown 2
        addBone(RectF(2520f, 380f, 2700f, 410f), seed = 206) // descent 1
        addBone(RectF(2760f, 420f, 2940f, 450f), seed = 207) // descent 2
        // Trench rafts + buttress stairs back up.
        addBone(RectF(1980f, 520f, 2135f, 550f), seed = 231)
        addBone(RectF(2130f, 468f, 2195f, 620f), seed = 232)
        addBone(RectF(2195f, 402f, 2245f, 620f), seed = 233)
        addBone(RectF(2560f, 530f, 2700f, 560f), seed = 234)
        addBone(RectF(2600f, 458f, 2700f, 488f), seed = 235)
        addBone(RectF(2800f, 525f, 2940f, 555f), seed = 236)
        addBone(RectF(2840f, 458f, 2940f, 488f), seed = 237)

        // ============ SECTION 3: CATACOMB VAULTS (3000–4600) ============
        addSlab(RectF(3000f, 460f, 3350f, 580f), seed = 214, mossy = false, body = EnvPalette.stoneBody, rim = EnvPalette.stoneLit) // plateau
        addBone(RectF(3420f, 390f, 3580f, 420f), seed = 215) // vault 1
        addBone(RectF(3640f, 320f, 3820f, 350f), seed = 216) // vault 2
        addBone(RectF(3880f, 250f, 4060f, 280f), seed = 217) // vault 3
        addBone(RectF(3920f, 165f, 4060f, 195f), seed = 238) // vault crown (cache)
        addSlab(RectF(3700f, 450f, 3960f, 480f), seed = 218, mossy = false, body = EnvPalette.stoneBody, rim = EnvPalette.stoneLit) // lower stone
        addSlab(RectF(4120f, 480f, 4550f, 620f), seed = 208, mossy = false, body = EnvPalette.stoneBody, rim = EnvPalette.stoneLit) // exit plateau (flush with Sanctuary floor)
        // The sunken crypt beneath the vaults.
        addSlab(RectF(3310f, 580f, 3350f, 750f), seed = 239, mossy = false, body = EnvPalette.stoneBody, rim = EnvPalette.stoneLit) // crypt west wall
        addCutStone(RectF(3350f, 690f, 3720f, 735f), seed = 240, mossy = false) // crypt floor W
        addBone(RectF(3740f, 658f, 3802f, 686f), seed = 241)                     // hop stone over crypt spikes
        addCutStone(RectF(3820f, 690f, 4120f, 735f), seed = 242, mossy = false) // crypt floor E
        addBone(RectF(3980f, 612f, 4080f, 642f), seed = 243)                     // crypt stair 1
        addBone(RectF(4030f, 534f, 4122f, 564f), seed = 244)                     // crypt stair 2

        // --- Boundaries & hazards ---
        solid(-100f, -300f, -20f, 750f)
        solid(4500f, -300f, 4600f, 750f)
        // Spike trenches only where the rafts/buttresses don't cover.
        hazard(380f, 585f, 500f, 625f)
        hazard(640f, 585f, 740f, 625f)
        hazard(880f, 585f, 980f, 625f)
        hazard(1120f, 585f, 1220f, 625f)
        hazard(1360f, 585f, 1460f, 625f)
        hazard(1600f, 585f, 1680f, 625f)
        hazard(1880f, 585f, 1980f, 625f)
        hazard(2440f, 585f, 2560f, 625f)
        hazard(2700f, 585f, 2800f, 625f)
        hazard(2940f, 585f, 3000f, 625f)
        hazard(3720f, 702f, 3820f, 735f) // crypt spike strip

        exit(-60f, 380f, 20f, 480f, "mossy_bridge", 4180f, 440f)
        exit(4440f, 340f, 4520f, 440f, "ruined_sanctuary", 160f, 460f)

        // --- Benches & soul caches ---
        bench(200f, 460f)    // entry
        bench(3120f, 460f)   // catacomb plateau
        bench(3480f, 690f)   // inside the crypt
        bench(4210f, 480f)   // exit plateau, before the Titan
        cache(600f, 130f)    // spine loft
        cache(2060f, 190f)   // crown beam
        cache(3990f, 165f)   // vault crown
        cache(3860f, 690f)   // crypt, guarded

        // --- Enemies ---
        enemySpawns.add(EnemySpawn(x = 560f, y = 225f, type = EnemyType.FLYER))
        enemySpawns.add(EnemySpawn(x = 600f, y = 88f, type = EnemyType.FLYER))
        enemySpawns.add(EnemySpawn(x = 810f, y = 525f, type = EnemyType.CRAWLER))
        enemySpawns.add(EnemySpawn(x = 1000f, y = 410f, type = EnemyType.CRAWLER))
        enemySpawns.add(EnemySpawn(x = 1290f, y = 340f, type = EnemyType.SPITTER))
        enemySpawns.add(EnemySpawn(x = 1780f, y = 340f, type = EnemyType.SHIELDER))
        enemySpawns.add(EnemySpawn(x = 2060f, y = 125f, type = EnemyType.FLYER))
        enemySpawns.add(EnemySpawn(x = 2060f, y = 520f, type = EnemyType.CRAWLER))
        enemySpawns.add(EnemySpawn(x = 2340f, y = 340f, type = EnemyType.SHIELDER))
        enemySpawns.add(EnemySpawn(x = 2850f, y = 420f, type = EnemyType.SPITTER))
        enemySpawns.add(EnemySpawn(x = 3180f, y = 460f, type = EnemyType.CRAWLER))
        enemySpawns.add(EnemySpawn(x = 3500f, y = 332f, type = EnemyType.FLYER))
        enemySpawns.add(EnemySpawn(x = 3730f, y = 320f, type = EnemyType.SPITTER))
        enemySpawns.add(EnemySpawn(x = 3550f, y = 690f, type = EnemyType.CRAWLER))
        enemySpawns.add(EnemySpawn(x = 3960f, y = 690f, type = EnemyType.SHIELDER))
        enemySpawns.add(EnemySpawn(x = 4250f, y = 480f, type = EnemyType.SHIELDER))
        enemySpawns.add(EnemySpawn(x = 4400f, y = 480f, boss = true, type = EnemyType.OSSUARY_TITAN))

        // Dim indigo shafts over the citadel.
        lightShafts.add(LightShaft(x = 2060f, top = -200f, bottom = 520f, halfTop = 60f, halfBot = 170f, lean = 40f, color = EnvPalette.boneFog, alpha = 30))
        lightShafts.add(LightShaft(x = 3920f, top = -200f, bottom = 560f, halfTop = 50f, halfBot = 150f, lean = -50f, color = EnvPalette.boneFog, alpha = 26))

        // --- Backdrop silhouettes ---
        EnvArt.ridge(farRidge, bounds.left, bounds.right, 660f, 200f, 420f, seed = 53, step = 132f)
        EnvArt.ridge(midRidge, bounds.left, bounds.right, 640f, 110f, 260f, seed = 67, step = 96f)
        EnvArt.stalactiteCeiling(nearCeiling, bounds.left, bounds.right, -300f, 130f, 320f, seed = 71, step = 82f)
        EnvArt.buildForeground(foreground, bounds.left, bounds.width(), seed = 83)

        // Giant rib arcs, two depths.
        for (i in 0..13) {
            val rx = i * 335f + 60f
            ribArchesFar.moveTo(rx - 70f, 560f)
            ribArchesFar.cubicTo(rx - 95f, 230f, rx + 80f, 130f, rx + 115f, 320f)
        }
        for (i in 0..8) {
            val rx = i * 540f + 240f
            ribArchesMid.moveTo(rx - 90f, 600f)
            ribArchesMid.cubicTo(rx - 120f, 220f, rx + 110f, 110f, rx + 150f, 330f)
        }

        // World-layer ceiling stalactites.
        for (i in 0..26) {
            val sx = i * 170f + 60f
            val len = 50f + ((i * 43) % 95)
            stalactites.moveTo(sx - 14f, -120f)
            stalactites.lineTo(sx, -120f + len)
            stalactites.lineTo(sx + 14f, -120f)
            stalactites.close()
        }

        // Spike beds are generated from the actual hazard rects, once.
        for (haz in hazards) {
            val w = 16f
            var cx = haz.left
            while (cx < haz.right - 2f) {
                val tipX = cx + w * 0.5f
                spikesDark.moveTo(cx, haz.bottom - 2f)
                spikesDark.lineTo(tipX, haz.top - 2f)
                spikesDark.lineTo(cx + w, haz.bottom - 2f)
                spikesDark.close()
                spikesLit.moveTo(cx + w * 0.3f, haz.bottom - 2f)
                spikesLit.lineTo(tipX, haz.top - 2f)
                spikesLit.lineTo(cx + w * 0.62f, haz.bottom - 2f)
                spikesLit.close()
                cx += w
            }
        }
    }

    override fun drawBackdrop(canvas: Canvas, paint: Paint, camX: Float, camY: Float, viewW: Float, viewH: Float, t: Float) {
        // FAR: fossil cliff horizon + the great rib cage + wisps.
        val sxFar = beginLayer(canvas, 0.12f, camX, camY, viewW)
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = EnvPalette.boneFar
        canvas.drawRect(bounds.left, 620f, bounds.right, 1600f, paint)
        canvas.drawPath(farRidge, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 26f
        paint.color = EnvPalette.boneFar
        paint.alpha = 200
        canvas.drawPath(ribArchesFar, paint)
        paint.alpha = 255
        for (i in 0..6) {
            val wx = 300f + i * 660f + sin(t * 0.5f + i * 1.8f) * 26f
            val wy = 250f + EnvArt.hash01(i * 17) * 160f + sin(t * 0.8f + i) * 16f
            EnvArt.bloom(canvas, paint, wx, wy, 66f / sxFar, 66f, EnvArt.glowCold, 44)
        }
        endLayer(canvas)

        // MID: closer ridge, nearer ribs, hanging soul-lamp chains, the skull.
        beginLayer(canvas, 0.30f, camX, camY, viewW)
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.boneMidLayer
        canvas.drawRect(bounds.left, 610f, bounds.right, 1600f, paint)
        canvas.drawPath(midRidge, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 20f
        paint.color = EnvPalette.boneMidLayer
        canvas.drawPath(ribArchesMid, paint)
        drawHangingLamps(canvas, paint, t)
        drawGiantSkull(canvas, paint, t)
        endLayer(canvas)

        // NEAR: bone-dark ceiling teeth.
        beginLayer(canvas, 0.55f, camX, camY, viewW)
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.boneNear
        canvas.drawPath(nearCeiling, paint)
        canvas.drawRect(-100f, -320f, 4600f, -300f, paint)
        endLayer(canvas)
        paint.alpha = 255
        paint.strokeCap = Paint.Cap.BUTT
    }

    /** Chains with tiny caged soul-flames (mid backdrop layer coords). */
    private fun drawHangingLamps(canvas: Canvas, paint: Paint, t: Float) {
        for (i in 0..11) {
            val cx = 140f + i * 395f + EnvArt.hash01(i * 31) * 90f
            val drop = 60f + EnvArt.hash01(i * 13) * 180f
            val sway = sin(t * 0.9f + i * 1.6f) * 6f
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.6f
            paint.color = EnvPalette.boneNear
            canvas.drawLine(cx, -140f, cx + sway, drop, paint)
            canvas.drawOval(cx + sway - 8f, drop - 2f, cx + sway + 8f, drop + 18f, paint)
            paint.style = Paint.Style.FILL
            val flicker = (120 + sin(t * 5.1f + i * 2.7f) * 45).toInt().coerceIn(30, 220)
            paint.color = EnvPalette.sporeCold
            paint.alpha = flicker
            canvas.drawCircle(cx + sway, drop + 8f, 3.4f, paint)
            paint.alpha = 255
            EnvArt.bloom(canvas, paint, cx + sway, drop + 8f, 26f, 26f, EnvArt.glowCold, flicker / 3)
        }
    }

    /** The colossal fossil skull overlooking the catacombs (mid layer). */
    private fun drawGiantSkull(canvas: Canvas, paint: Paint, t: Float) {
        val x = 3760f
        val y = 330f
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.boneMidLayer
        // Cranium + snout + jaw.
        canvas.drawOval(x - 150f, y - 170f, x + 150f, y + 60f, paint)
        canvas.drawRect(x - 105f, y + 30f, x + 105f, y + 120f, paint)
        canvas.drawRect(x - 80f, y + 132f, x + 80f, y + 158f, paint)
        // Teeth.
        paint.color = EnvPalette.boneSkyMid
        var tx = x - 68f
        while (tx < x + 66f) {
            canvas.drawRect(tx, y + 104f, tx + 9f, y + 134f, paint)
            tx += 26f
        }
        // Eye sockets with faint, breathing soul-light.
        paint.color = EnvPalette.boneSkyTop
        canvas.drawOval(x - 78f, y - 60f, x - 18f, y + 22f, paint)
        canvas.drawOval(x + 18f, y - 60f, x + 78f, y + 22f, paint)
        canvas.drawOval(x - 14f, y + 36f, x + 14f, y + 92f, paint)
        val breathe = (52 + sin(t * 0.8f) * 22).toInt().coerceIn(10, 110)
        EnvArt.bloom(canvas, paint, x - 48f, y - 18f, 34f, 40f, EnvArt.glowCold, breathe)
        EnvArt.bloom(canvas, paint, x + 48f, y - 18f, 34f, 40f, EnvArt.glowCold, breathe)
    }

    override fun drawWorld(canvas: Canvas, paint: Paint, t: Float, view: RectF) {
        // 1. Crossing rib arcs framing the Titan's plateau.
        if (spanVisible(view, 4080f, 4600f)) {
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = 17f
            paint.color = EnvPalette.boneDark
            EnvArt.scratch.reset()
            EnvArt.scratch.moveTo(4160f, 480f)
            EnvArt.scratch.cubicTo(4180f, 250f, 4380f, 170f, 4470f, 300f)
            EnvArt.scratch.moveTo(4520f, 480f)
            EnvArt.scratch.cubicTo(4500f, 260f, 4320f, 180f, 4230f, 310f)
            canvas.drawPath(EnvArt.scratch, paint)
            paint.strokeWidth = 6f
            paint.color = EnvPalette.boneMid
            canvas.drawPath(EnvArt.scratch, paint)
            paint.strokeCap = Paint.Cap.BUTT
        }

        // 2. Slabs.
        drawSlabs(canvas, paint, t, view)

        // 3. Rib-citadel tower banding.
        if (spanVisible(view, 1680f, 1880f)) drawRibTower(canvas, paint, 1680f, 1880f)
        if (spanVisible(view, 2240f, 2440f)) drawRibTower(canvas, paint, 2240f, 2440f)

        // 4. Spikes: dark bed + lit edges, from the pre-built paths.
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.boneShadow
        for (haz in hazards) {
            if (haz.right > view.left - 40f && haz.left < view.right + 40f) {
                canvas.drawRect(haz.left - 3f, haz.bottom - 12f, haz.right + 3f, haz.bottom + 4f, paint)
            }
        }
        paint.color = EnvPalette.spikeDark
        canvas.drawPath(spikesDark, paint)
        paint.color = EnvPalette.spikeLit
        paint.alpha = 190
        canvas.drawPath(spikesLit, paint)
        paint.alpha = 255

        // 5. World stalactites.
        paint.color = EnvPalette.stoneBody
        canvas.drawPath(stalactites, paint)

        // 6. Soul-crystal clusters on the raft route.
        if (spanVisible(view, 860f, 1120f)) {
            EnvArt.drawCrystalCluster(canvas, paint, 1056f, 530f, 24f, EnvPalette.sporeCold, EnvPalette.boneDark, EnvArt.glowCold)
        }
        if (spanVisible(view, 3300f, 3560f)) {
            EnvArt.drawCrystalCluster(canvas, paint, 3420f, 690f, 20f, EnvPalette.sporeCold, EnvPalette.boneDark, EnvArt.glowCold)
        }

        // 7. Hanging lanterns over rest spots.
        drawSoulLantern(canvas, paint, 200f, 320f, view)
        drawSoulLantern(canvas, paint, 2060f, 150f, view)
        drawSoulLantern(canvas, paint, 3480f, 590f, view)
        drawSoulLantern(canvas, paint, 4210f, 360f, view)
        paint.alpha = 255
    }

    private fun drawRibTower(canvas: Canvas, paint: Paint, left: Float, right: Float) {
        paint.style = Paint.Style.FILL
        for (r in 0..7) {
            val segY = 362f + r * 34f
            if (segY > 600f) break
            paint.color = EnvPalette.boneMid
            paint.alpha = 165
            canvas.drawRoundRect(left + 9f, segY, right - 9f, segY + 22f, 9f, 9f, paint)
            paint.color = EnvPalette.boneLit
            paint.alpha = 150
            for (tb in 0..4) {
                canvas.drawCircle(left + 24f + tb * 38f, segY + 11f, 3.2f, paint)
            }
        }
        paint.alpha = 255
    }

    private fun drawSoulLantern(canvas: Canvas, paint: Paint, x: Float, y: Float, view: RectF) {
        if (!nearView(view, x, 200f)) return
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.2f
        paint.color = EnvPalette.boneDark
        canvas.drawLine(x, y - 90f, x, y - 8f, paint)
        canvas.drawOval(x - 7f, y - 9f, x + 7f, y + 9f, paint)
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.sporeCold
        canvas.drawCircle(x, y, 4f, paint)
        EnvArt.bloom(canvas, paint, x, y, 46f, 46f, EnvArt.glowCold, 80)
    }

    override fun drawFront(canvas: Canvas, paint: Paint, camX: Float, camY: Float, viewW: Float, viewH: Float, t: Float) {
        canvas.save()
        canvas.translate(-camX, -camY)
        for (shaft in lightShafts) shaft.draw(canvas, paint)
        EnvArt.drawMotes(
            canvas, paint, t,
            areaW = bounds.width(), areaH = 780f, count = 42, seed = 19,
            viewL = camX, viewR = camX + viewW, cold = true
        )
        canvas.restore()

        beginLayer(canvas, 1.22f, camX, camY, viewW)
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = EnvPalette.foreground
        paint.alpha = 135
        canvas.drawPath(foreground, paint)
        endLayer(canvas)
        paint.alpha = 255
    }
}
