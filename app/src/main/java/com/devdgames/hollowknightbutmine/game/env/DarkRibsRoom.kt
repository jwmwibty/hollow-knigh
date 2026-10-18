package com.devdgames.hollowknightbutmine.game.env

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.devdgames.hollowknightbutmine.game.EnemyType
import kotlin.math.sin

/**
 * Room 2: Extended Ossuary Depths & The Great Fossil Chasm
 * Spans ~4600px wide across three treacherous sections:
 * - Section 1: The Bone Caverns & Spine Steps (x = 0 to 1500)
 * - Section 2: The Giant Segmented Rib Citadel & Spike Trenches (x = 1500 to 3000)
 * - Section 3: The Hanging Catacomb Vaults & Exit Passage (x = 3000 to 4600)
 */
class DarkRibsRoom : Room(
    id = "dark_ribs",
    title = "Ossuary Depths: The Fossil Chasm",
    bounds = RectF(-100f, -250f, 4600f, 750f)
) {
    private val ribArches = Path()
    private val stalactites = Path()

    override fun build() {
        spawnX = 140f
        spawnY = 460f

        // ================= SECTION 1: BONE CAVERNS & SPINE STEPS (0 - 1500) =================
        // Entry ledge
        addSlab(RectF(-100f, 480f, 380f, 620f), seed = 201, mossy = false, body = EnvPalette.stoneBody, rim = EnvPalette.stoneLit)

        // Spine stepping platforms (~70px intervals)
        addSlab(RectF(440f, 420f, 600f, 450f), seed = 202, mossy = false, body = EnvPalette.boneDark, rim = EnvPalette.boneLit)
        addSlab(RectF(660f, 355f, 820f, 385f), seed = 203, mossy = false, body = EnvPalette.boneDark, rim = EnvPalette.boneLit)
        addSlab(RectF(520f, 280f, 680f, 310f), seed = 204, mossy = false, body = EnvPalette.boneDark, rim = EnvPalette.boneLit)

        // Intermediate rest ledge
        addSlab(RectF(880f, 410f, 1140f, 440f), seed = 210, mossy = false, body = EnvPalette.stoneBody, rim = EnvPalette.stoneLit)
        addSlab(RectF(1200f, 340f, 1380f, 370f), seed = 211, mossy = false, body = EnvPalette.boneDark, rim = EnvPalette.boneLit)

        // ================= SECTION 2: THE GREAT SEGMENTED RIB CITADEL (1500 - 3000) =================
        // Approach steps to Rib Citadel
        addSlab(RectF(1460f, 430f, 1620f, 460f), seed = 209, mossy = false, body = EnvPalette.boneDark, rim = EnvPalette.boneLit)

        // Tall Fossil Rib Tower 1
        addSlab(RectF(1680f, 340f, 1880f, 620f), seed = 205, mossy = false, body = EnvPalette.boneMid, rim = EnvPalette.boneLit)

        // High aerial bridge between bone towers
        addSlab(RectF(1940f, 270f, 2180f, 300f), seed = 212, mossy = false, body = EnvPalette.boneLit, rim = EnvPalette.boneLit)

        // Tall Fossil Rib Tower 2
        addSlab(RectF(2240f, 340f, 2440f, 620f), seed = 213, mossy = false, body = EnvPalette.boneMid, rim = EnvPalette.boneLit)

        // Floating descending bone slabs
        addSlab(RectF(2520f, 380f, 2700f, 410f), seed = 206, mossy = false, body = EnvPalette.stoneBody, rim = EnvPalette.stoneLit)
        addSlab(RectF(2760f, 420f, 2940f, 450f), seed = 207, mossy = false, body = EnvPalette.boneDark, rim = EnvPalette.boneLit)

        // ================= SECTION 3: HANGING CATACOMB VAULTS (3000 - 4600) =================
        // Middle plateau
        addSlab(RectF(3000f, 460f, 3350f, 580f), seed = 214, mossy = false, body = EnvPalette.stoneBody, rim = EnvPalette.stoneLit)

        // High vault ledges
        addSlab(RectF(3420f, 390f, 3580f, 420f), seed = 215, mossy = false, body = EnvPalette.boneDark, rim = EnvPalette.boneLit)
        addSlab(RectF(3640f, 320f, 3820f, 350f), seed = 216, mossy = false, body = EnvPalette.boneLit, rim = EnvPalette.boneLit)
        addSlab(RectF(3880f, 250f, 4060f, 280f), seed = 217, mossy = false, body = EnvPalette.boneLit, rim = EnvPalette.boneLit)

        // Lower path stepping stone
        addSlab(RectF(3700f, 450f, 3960f, 480f), seed = 218, mossy = false, body = EnvPalette.stoneBody, rim = EnvPalette.stoneLit)

        // Grand exit plateau
        addSlab(RectF(4120f, 440f, 4550f, 620f), seed = 208, mossy = false, body = EnvPalette.stoneBody, rim = EnvPalette.stoneLit)

        // --- Boundaries & Hazards ---
        solid(-100f, -300f, -20f, 750f)
        solid(4500f, -300f, 4600f, 750f)

        // Spike pit hazards along floor across the vast trenches
        hazard(380f, 570f, 1680f, 620f)
        hazard(1880f, 570f, 2240f, 620f)
        hazard(2440f, 570f, 3000f, 620f)
        hazard(3350f, 570f, 4120f, 620f)

        // Exits
        exit(-60f, 380f, 20f, 480f, "mossy_bridge", 4180f, 440f)
        exit(4440f, 340f, 4520f, 440f, "ruined_sanctuary", 160f, 460f)

        // ================= ENEMIES ACROSS THE 4600px OSSUARY =================
        // Section 1
        enemySpawns.add(EnemySpawn(x = 560f, y = 220f, type = EnemyType.FLYER))
        enemySpawns.add(EnemySpawn(x = 1000f, y = 410f, type = EnemyType.CRAWLER))
        enemySpawns.add(EnemySpawn(x = 1290f, y = 340f, type = EnemyType.SPITTER))

        // Section 2: Citadel defenders
        enemySpawns.add(EnemySpawn(x = 1780f, y = 340f, type = EnemyType.SHIELDER)) // Tower 1 guard
        enemySpawns.add(EnemySpawn(x = 2060f, y = 200f, type = EnemyType.FLYER))   // Bridge squit
        enemySpawns.add(EnemySpawn(x = 2340f, y = 340f, type = EnemyType.SHIELDER)) // Tower 2 guard
        enemySpawns.add(EnemySpawn(x = 2850f, y = 420f, type = EnemyType.SPITTER))

        // Section 3: Catacomb guardians
        enemySpawns.add(EnemySpawn(x = 3180f, y = 460f, type = EnemyType.CRAWLER))
        enemySpawns.add(EnemySpawn(x = 3500f, y = 390f, type = EnemyType.FLYER))
        enemySpawns.add(EnemySpawn(x = 3730f, y = 320f, type = EnemyType.SPITTER))
        enemySpawns.add(EnemySpawn(x = 4250f, y = 440f, type = EnemyType.SHIELDER))

        // Stalactites across ceiling
        for (i in 0..26) {
            val sx = i * 170f + 60f
            val len = 50f + ((i * 43) % 95)
            stalactites.moveTo(sx - 14f, -100f)
            stalactites.lineTo(sx, -100f + len)
            stalactites.lineTo(sx + 14f, -100f)
            stalactites.close()
        }

        // Giant Background Rib Cages
        for (i in 0..14) {
            val rx = i * 280f + 120f
            ribArches.moveTo(rx - 60f, 500f)
            ribArches.cubicTo(rx - 80f, 200f, rx + 80f, 120f, rx + 100f, 280f)
        }
    }

    override fun drawBackdrop(canvas: Canvas, paint: Paint, camX: Float, camY: Float, viewW: Float, viewH: Float, t: Float) {
        // Shared backdrop gradient is painted once by the first biome.

        val paraX = camX * 0.25f
        canvas.save()
        canvas.translate(-paraX, 0f)

        paint.style = Paint.Style.STROKE
        paint.color = EnvPalette.farSilhouette
        paint.strokeWidth = 24f
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawPath(ribArches, paint)

        // Far-off fossil cliffs and broken bone spires.
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(9, 14, 24)
        for (i in 0..18) {
            val bx = i * 255f - 60f
            val top = 190f + ((i * 53) % 145)
            val cliff = Path()
            cliff.moveTo(bx, 620f)
            cliff.lineTo(bx + 35f, top + 80f)
            cliff.lineTo(bx + 62f, top)
            cliff.lineTo(bx + 100f, top + 95f)
            cliff.lineTo(bx + 142f, 620f)
            cliff.close()
            canvas.drawPath(cliff, paint)
        }

        // Hanging chains and tiny soul lamps behind the playable layer.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.rgb(37, 49, 64)
        for (i in 0..12) {
            val cx = i * 365f + 80f
            val drop = 90f + ((i * 29) % 160)
            canvas.drawLine(cx, -80f, cx + 7f, drop, paint)
            canvas.drawOval(cx - 5f, drop - 4f, cx + 12f, drop + 15f, paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(120, 105, 205, 230)
            canvas.drawCircle(cx + 3f, drop + 5f, 3f, paint)
            paint.style = Paint.Style.STROKE
            paint.color = Color.rgb(37, 49, 64)
        }

        // Ancient will-o'-the-wisp blooms across the cavern
        for (i in 0..9) {
            val wx = i * 440f + 160f
            val wy = 260f + sin(t + i) * 20f
            EnvArt.bloom(canvas, paint, wx, wy, 80f, 80f, EnvArt.glowTeal, 40)
        }

        canvas.restore()
    }

    override fun drawWorld(canvas: Canvas, paint: Paint, t: Float) {
        // 1. Draw Slabs
        drawSlabs(canvas, paint, 0f, 0f, 4600f, 800f)

        // 2. Draw Rib Citadel Towers (Tower 1: 1680-1880, Tower 2: 2240-2440)
        drawRibTower(canvas, paint, 1680f, 1880f)
        drawRibTower(canvas, paint, 2240f, 2440f)

        // 3. Stalactites
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.stoneBody
        canvas.drawPath(stalactites, paint)

        // 4. Spike Beds in pit hazards
        paint.color = Color.rgb(180, 190, 200)
        drawSpikeBed(canvas, paint, 380f, 1680f, 570f)
        drawSpikeBed(canvas, paint, 1880f, 2240f, 570f)
        drawSpikeBed(canvas, paint, 2440f, 3000f, 570f)
        drawSpikeBed(canvas, paint, 3350f, 4120f, 570f)

        // Soul crystal lanterns
        drawSoulLantern(canvas, paint, 700f, 330f)
        drawSoulLantern(canvas, paint, 2060f, 250f)
        drawSoulLantern(canvas, paint, 3700f, 300f)
    }

    private fun drawRibTower(canvas: Canvas, paint: Paint, left: Float, right: Float) {
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.boneMid
        for (r in 0..7) {
            val segY = 360f + r * 35f
            canvas.drawRoundRect(left + 8f, segY, right - 8f, segY + 24f, 8f, 8f, paint)

            paint.color = EnvPalette.boneLit
            for (tb in 0..4) {
                val bx = left + 20f + tb * 38f
                canvas.drawCircle(bx, segY + 12f, 3.5f, paint)
            }
            paint.color = EnvPalette.boneMid
        }
    }

    private fun drawSoulLantern(canvas: Canvas, paint: Paint, x: Float, y: Float) {
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.boneLit
        canvas.drawCircle(x, y, 8f, paint)
        EnvArt.bloom(canvas, paint, x, y, 55f, 55f, EnvArt.glowWarm, 85)
    }

    private fun drawSpikeBed(canvas: Canvas, paint: Paint, left: Float, right: Float, top: Float) {
        val spikeWidth = 14f
        var cx = left
        val spikePath = Path()
        while (cx < right) {
            spikePath.moveTo(cx, top + 25f)
            spikePath.lineTo(cx + spikeWidth * 0.5f, top)
            spikePath.lineTo(cx + spikeWidth, top + 25f)
            cx += spikeWidth
        }
        spikePath.close()
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(160, 175, 185)
        canvas.drawPath(spikePath, paint)
    }

    override fun drawFront(canvas: Canvas, paint: Paint, camX: Float, camY: Float, viewW: Float, viewH: Float, t: Float) {
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.foreground
        paint.alpha = 75
        canvas.drawRect(0f, 0f, 35f, viewH, paint)
        canvas.drawRect(viewW - 35f, 0f, viewW, viewH, paint)
        paint.alpha = 255
    }
}
