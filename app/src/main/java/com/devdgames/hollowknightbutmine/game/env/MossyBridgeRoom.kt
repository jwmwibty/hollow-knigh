package com.devdgames.hollowknightbutmine.game.env

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.devdgames.hollowknightbutmine.game.EnemyType
import kotlin.math.sin

/**
 * Room 1: Extended Moss Grotto & The Sunken Bridge
 * Spans ~4400px wide across three epic sections:
 * - Section 1: Verdant Shore & Moss Terraces (x = 0 to 1400)
 * - Section 2: The Great Sunken Bridge & Lake Abyss (x = 1400 to 2800)
 * - Section 3: Carapace Shrine Sanctuary & Overgrown Caverns (x = 2800 to 4400)
 */
class MossyBridgeRoom : Room(
    id = "mossy_bridge",
    title = "Moss Grotto: The Sunken Bridge",
    bounds = RectF(-100f, -250f, 4400f, 750f)
) {
    private val lightShafts = ArrayList<LightShaft>()
    private val hangingTendrils = Path()
    private val shrinePath = Path()
    private val distantCliffs = Path()

    override fun build() {
        spawnX = 140f
        spawnY = 460f

        // ================= SECTION 1: VERDANT SHORE & MOSS TERRACES (0 - 1400) =================
        // Entry shore
        addSlab(RectF(-100f, 480f, 420f, 620f), seed = 101, mossy = true)

        // Stepping terraces (clearance ~74px)
        addSlab(RectF(480f, 435f, 640f, 465f), seed = 102, mossy = true)
        addSlab(RectF(700f, 385f, 880f, 415f), seed = 103, mossy = true)
        addSlab(RectF(580f, 310f, 740f, 340f), seed = 104, mossy = true)

        // High canopy ledge
        addSlab(RectF(820f, 260f, 1060f, 290f), seed = 110, mossy = true)

        // Lower shore before water
        addSlab(RectF(960f, 450f, 1340f, 500f), seed = 111, mossy = true)

        // ================= SECTION 2: THE GREAT SUNKEN BRIDGE & LAKE ABYSS (1400 - 2800) =================
        // Stepping stone at water entry
        addSlab(RectF(1420f, 420f, 1580f, 450f), seed = 112, mossy = true)

        // The High Grand Moss Bridge (spanning across the lake)
        addSlab(RectF(1660f, 360f, 2400f, 395f), seed = 105, mossy = true)

        // Low water stepping stones across the abyss
        addSlab(RectF(1800f, 470f, 1980f, 500f), seed = 106, mossy = true)
        addSlab(RectF(2120f, 470f, 2300f, 500f), seed = 113, mossy = true)

        // Mid-lake high vantage pillar
        addSlab(RectF(2480f, 300f, 2640f, 330f), seed = 114, mossy = true)

        // Eastern lake shore plateau
        addSlab(RectF(2550f, 450f, 2950f, 520f), seed = 115, mossy = true)

        // ================= SECTION 3: CARAPACE SHRINE SANCTUARY & CAVERNS (2800 - 4400) =================
        // Ascent steps to Sanctuary
        addSlab(RectF(3020f, 400f, 3180f, 430f), seed = 116, mossy = true)
        addSlab(RectF(3240f, 340f, 3420f, 370f), seed = 117, mossy = true)

        // Main Carapace Shrine Plaza
        addSlab(RectF(3480f, 460f, 4300f, 620f), seed = 107, mossy = true)

        // Shrine roof and secret high sanctum
        addSlab(RectF(3680f, 370f, 3840f, 400f), seed = 109, mossy = true)
        addSlab(RectF(3880f, 280f, 4120f, 310f), seed = 108, mossy = true)

        // --- Boundaries & Hazards ---
        solid(-100f, -300f, -20f, 750f)
        solid(4300f, -300f, 4400f, 750f)

        // Lake Abyss Hazard (extended across the vast water section)
        hazard(1340f, 590f, 2550f, 650f)

        // Exit to Chapter 2: Dark Ribs
        exit(4240f, 360f, 4340f, 460f, "dark_ribs", 160f, 460f)

        // ================= ENEMIES ACROSS THE EXTENDED GROTTO =================
        // Section 1: Patrol beetles & air squits
        enemySpawns.add(EnemySpawn(x = 600f, y = 435f, type = EnemyType.CRAWLER))
        enemySpawns.add(EnemySpawn(x = 800f, y = 200f, type = EnemyType.FLYER))
        enemySpawns.add(EnemySpawn(x = 1150f, y = 450f, type = EnemyType.CRAWLER))

        // Section 2: Bridge & Water defenders
        enemySpawns.add(EnemySpawn(x = 1500f, y = 420f, type = EnemyType.FLYER))
        enemySpawns.add(EnemySpawn(x = 1950f, y = 360f, type = EnemyType.SPITTER)) // Acid sniper on bridge
        enemySpawns.add(EnemySpawn(x = 2250f, y = 360f, type = EnemyType.SHIELDER)) // Guardian on bridge
        enemySpawns.add(EnemySpawn(x = 2560f, y = 250f, type = EnemyType.FLYER))

        // Section 3: Shrine sanctum guards
        enemySpawns.add(EnemySpawn(x = 3100f, y = 400f, type = EnemyType.CRAWLER))
        enemySpawns.add(EnemySpawn(x = 3330f, y = 340f, type = EnemyType.SPITTER))
        enemySpawns.add(EnemySpawn(x = 3750f, y = 460f, type = EnemyType.SHIELDER))
        enemySpawns.add(EnemySpawn(x = 4100f, y = 460f, type = EnemyType.CRAWLER))
        enemySpawns.add(EnemySpawn(x = 4200f, y = 460f, boss = true, type = EnemyType.BROODMOTHER)) // Boss spawn

        // ================= ATMOSPHERIC SHAFTS ACROSS 4400px =================
        lightShafts.add(LightShaft(x = 550f, top = -180f, bottom = 540f, halfTop = 40f, halfBot = 120f, lean = 70f, color = EnvPalette.windowGlow, alpha = 45))
        lightShafts.add(LightShaft(x = 1200f, top = -180f, bottom = 540f, halfTop = 45f, halfBot = 130f, lean = -60f, color = EnvPalette.windowGlow, alpha = 40))
        lightShafts.add(LightShaft(x = 2000f, top = -180f, bottom = 540f, halfTop = 55f, halfBot = 150f, lean = 80f, color = EnvPalette.windowGlow, alpha = 50))
        lightShafts.add(LightShaft(x = 2700f, top = -180f, bottom = 540f, halfTop = 45f, halfBot = 130f, lean = -70f, color = EnvPalette.windowGlow, alpha = 45))
        lightShafts.add(LightShaft(x = 3900f, top = -180f, bottom = 540f, halfTop = 60f, halfBot = 170f, lean = 60f, color = EnvPalette.windowGlow, alpha = 60))

        // Shrine Carapace Silhouette (relocated to Section 3)
        val sx = 3920f
        val sy = 460f
        shrinePath.moveTo(sx - 80f, sy)
        shrinePath.cubicTo(sx - 90f, sy - 180f, sx - 20f, sy - 280f, sx + 20f, sy - 280f)
        shrinePath.cubicTo(sx + 80f, sy - 250f, sx + 95f, sy - 140f, sx + 85f, sy)
        shrinePath.close()

        // Prebuild distant cavern silhouettes once to avoid per-frame allocations.
        for (i in 0..22) {
            val sx = i * 205f - 80f
            val peak = 220f + ((i * 47) % 170)
            distantCliffs.moveTo(sx, 620f)
            distantCliffs.lineTo(sx + 42f, peak + 70f)
            distantCliffs.lineTo(sx + 82f, peak)
            distantCliffs.lineTo(sx + 126f, peak + 95f)
            distantCliffs.lineTo(sx + 168f, 620f)
            distantCliffs.close()
        }

        // Ceiling Tendrils across the entire expanse
        for (i in 0..28) {
            val tx = i * 150f + 50f
            val len = 60f + ((i * 37) % 85)
            EnvArt.tapered(hangingTendrils, tx, -100f, tx + 10f, -100f + len * 0.5f, tx + 5f, -100f + len, 8f, 1f, 6)
        }
    }

    override fun drawBackdrop(canvas: Canvas, paint: Paint, camX: Float, camY: Float, viewW: Float, viewH: Float, t: Float) {
        fillVoid(canvas, paint, viewW, viewH, EnvPalette.voidTop, EnvPalette.voidBottom)

        // Parallax background cavern pillars across 4400px (0.25x parallax)
        val paraX = camX * 0.25f
        val paraY = camY * 0.25f
        canvas.save()
        canvas.translate(-paraX, -paraY)

        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.farSilhouette
        for (i in 0..16) {
            val archX = i * 320f - 100f
            canvas.drawRoundRect(archX, 80f, archX + 110f, 650f, 24f, 24f, paint)
        }

        // Distant stalagmite skyline: layered shapes make the cavern feel deep.
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(5, 28, 27)
        canvas.drawPath(distantCliffs, paint)

        // Giant fungal caps and glowing stems in the far distance.
        for (i in 0..10) {
            val mx = i * 405f + 115f
            val my = 370f + ((i * 31) % 100)
            paint.color = Color.rgb(8, 43, 39)
            canvas.drawRoundRect(mx - 5f, my, mx + 5f, 600f, 5f, 5f, paint)
            canvas.drawOval(mx - 42f, my - 20f, mx + 42f, my + 12f, paint)
            paint.color = Color.argb(42, 73, 238, 203)
            canvas.drawOval(mx - 30f, my - 17f, mx + 30f, my + 3f, paint)
        }

        // Bioluminescent background cavern blooms
        for (i in 0..12) {
            val ox = i * 360f + 40f
            val oy = 180f + sin(t * 1.5f + i) * 15f
            EnvArt.bloom(canvas, paint, ox, oy, 75f, 75f, EnvArt.glowTeal, 45)
        }

        // Extra distant rock spires
paint.style = Paint.Style.FILL
paint.color = EnvPalette.farSilhouette
for (i in 0..8) {
    val spireX = i * 500f + 250f
    canvas.drawRoundRect(spireX, 30f, spireX + 80f, 200f, 12f, 12f, paint)
}
canvas.restore()
    }

    override fun drawWorld(canvas: Canvas, paint: Paint, t: Float) {
        // 1. Draw Shrine Architecture
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.midRock
        canvas.drawPath(shrinePath, paint)

        // Shell rib ridges on shrine
        paint.style = Paint.Style.STROKE
        paint.color = EnvPalette.midRim
        paint.strokeWidth = 4f
        val sx = 3920f
        val sy = 460f
        for (r in 1..5) {
            val yOffset = r * 44f
            canvas.drawArc(RectF(sx - 75f + r * 5f, sy - yOffset - 50f, sx + 75f - r * 5f, sy - yOffset + 20f), 190f, 160f, false, paint)
        }

        // Glowing Gothic Shrine Doorway
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.windowGlow
        val door = Path()
        door.moveTo(sx - 18f, sy)
        door.lineTo(sx - 18f, sy - 60f)
        door.quadTo(sx, sy - 85f, sx + 18f, sy - 60f)
        door.lineTo(sx + 18f, sy)
        door.close()
        canvas.drawPath(door, paint)
        EnvArt.bloom(canvas, paint, sx, sy - 40f, 65f, 65f, EnvArt.glowTeal, 70)

        // 2. Draw Slabs
        drawSlabs(canvas, paint, 0f, 0f, 4400f, 800f)

        // 3. Hanging Tendrils from ceiling
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.ceilingRoot
        canvas.drawPath(hangingTendrils, paint)

        // 4. Bioluminescent Lake Below Chasm (spanning 1340 to 2550)
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.waterDeep
        canvas.drawRect(1340f, 560f, 2550f, 700f, paint)

        // Surface water shimmer
        paint.color = EnvPalette.waterTop
        val waterY = 562f + sin(t * 3f) * 2f
        canvas.drawLine(1340f, waterY, 2550f, waterY, paint)

        // Water surface bloom
        EnvArt.bloom(canvas, paint, 1600f, 565f, 220f, 30f, EnvArt.glowTeal, 60)
        EnvArt.bloom(canvas, paint, 2000f, 565f, 220f, 30f, EnvArt.glowTeal, 60)
        EnvArt.bloom(canvas, paint, 2350f, 565f, 220f, 30f, EnvArt.glowTeal, 60)

        // Bridge pillars dipping into water
        paint.color = EnvPalette.rockShadow
        paint.strokeWidth = 6f
        canvas.drawLine(1750f, 395f, 1750f, 565f, paint)
        canvas.drawLine(1950f, 395f, 1950f, 565f, paint)
        canvas.drawLine(2150f, 395f, 2150f, 565f, paint)
        canvas.drawLine(2350f, 395f, 2350f, 565f, paint)
    }

    override fun drawFront(canvas: Canvas, paint: Paint, camX: Float, camY: Float, viewW: Float, viewH: Float, t: Float) {
        canvas.save()
        canvas.translate(-camX, -camY)
        for (shaft in lightShafts) {
            shaft.draw(canvas, paint)
        }

        // Floating spores across the expansive grotto
        paint.style = Paint.Style.FILL
        for (i in 0..38) {
            val seedX = (i * 113) % 4300
            val seedY = (i * 47) % 600
            val moteX = seedX + sin(t * 0.8f + i) * 25f
            val moteY = seedY + (t * 18f * (1f + (i % 3) * 0.4f)) % 600f
            paint.color = if (i % 3 == 0) EnvPalette.sporeWarm else EnvPalette.sporeTeal
            paint.alpha = (90 + sin(t * 2f + i) * 60).toInt().coerceIn(0, 255)
            canvas.drawCircle(moteX, moteY, 2f + (i % 3), paint)
        }
        canvas.restore()

        // Vignette
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.foreground
        paint.alpha = 50
        canvas.drawRect(0f, 0f, viewW, 20f, paint)
        canvas.drawRect(0f, viewH - 25f, viewW, viewH, paint)
        paint.alpha = 255
    }
}
