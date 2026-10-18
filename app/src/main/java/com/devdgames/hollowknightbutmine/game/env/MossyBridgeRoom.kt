package com.devdgames.hollowknightbutmine.game.env

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.sin

/**
 * Room 1: Mossy Bridge over Bioluminescent Water (Inspired by Greenpath Lake & Moss Sanctuary).
 * Features:
 * - Hand-crafted stone & moss slabs.
 * - Shimmering bioluminescent water below with floating ripples.
 * - Arching bug shell shrine / ruin with a glowing gothic doorway.
 * - Slanted volumetric God rays / light shafts from cave fissures.
 * - Hanging moss tendrils, overgrown ivy, and floating spores.
 */
class MossyBridgeRoom : Room(
    id = "mossy_bridge",
    title = "Moss Grotto: The Sunken Bridge",
    bounds = RectF(-100f, -200f, 2100f, 750f)
) {
    private val lightShafts = ArrayList<LightShaft>()
    private val hangingTendrils = Path()
    private val backgroundArches = Path()
    private val shrinePath = Path()
    private val waterRipples = Path()

    override fun build() {
        spawnX = 140f
        spawnY = 460f

        // --- Collision Platforms (Slabs) ---
        // Left entry shore (top at 480f)
        addSlab(RectF(-100f, 480f, 320f, 620f), seed = 101, mossy = true)

        // Stepping stones across the water chasm (player height 67px + 10% = 74px clearance)
        addSlab(RectF(380f, 435f, 540f, 465f), seed = 102, mossy = true)
        addSlab(RectF(600f, 385f, 760f, 415f), seed = 103, mossy = true)
        addSlab(RectF(480f, 310f, 640f, 340f), seed = 104, mossy = true)

        // The High Moss Bridge
        addSlab(RectF(840f, 360f, 1340f, 395f), seed = 105, mossy = true)

        // Lower water stepping stones
        addSlab(RectF(1020f, 470f, 1200f, 500f), seed = 106, mossy = true)

        // Shrine Plateau on the right (top at 460f)
        addSlab(RectF(1420f, 460f, 2100f, 620f), seed = 107, mossy = true)

        // Stepping stone up to shrine roof
        addSlab(RectF(1500f, 370f, 1640f, 400f), seed = 109, mossy = true)
        // Upper secret ledge near shrine roof
        addSlab(RectF(1680f, 290f, 1920f, 320f), seed = 108, mossy = true)

        // Cave boundaries
        solid(-100f, -300f, -20f, 750f)
        solid(2020f, -300f, 2100f, 750f)

        // Water hazard in the pit (player respawns / takes damage if falling)
        hazard(320f, 590f, 1420f, 640f)

        // Exits
        exit(1980f, 360f, 2060f, 460f, "dark_ribs", 160f, 460f)

        // Enemy Spawns
        enemySpawns.add(EnemySpawn(x = 520f, y = 440f, boss = false))   // Crawler on step
        enemySpawns.add(EnemySpawn(x = 750f, y = 200f, boss = false))   // Flying squit in air
        enemySpawns.add(EnemySpawn(x = 1130f, y = 340f, boss = false))  // Acid spitter on bridge
        enemySpawns.add(EnemySpawn(x = 1680f, y = 460f, boss = false))  // Shielder defending shrine

        // --- Volumetric Light Shafts ---
        lightShafts.add(LightShaft(x = 450f, top = -180f, bottom = 540f, halfTop = 40f, halfBot = 120f, lean = 80f, color = EnvPalette.windowGlow, alpha = 45))
        lightShafts.add(LightShaft(x = 1100f, top = -180f, bottom = 540f, halfTop = 50f, halfBot = 140f, lean = 90f, color = EnvPalette.windowGlow, alpha = 50))
        lightShafts.add(LightShaft(x = 1750f, top = -180f, bottom = 540f, halfTop = 60f, halfBot = 160f, lean = 70f, color = EnvPalette.windowGlow, alpha = 60))

        // --- Background Organic Shell Shrine ---
        val sx = 1750f
        val sy = 460f
        shrinePath.moveTo(sx - 80f, sy)
        shrinePath.cubicTo(sx - 90f, sy - 180f, sx - 20f, sy - 280f, sx + 20f, sy - 280f)
        shrinePath.cubicTo(sx + 80f, sy - 250f, sx + 95f, sy - 140f, sx + 85f, sy)
        shrinePath.close()

        // --- Ceiling Hanging Tendrils ---
        for (i in 0..14) {
            val tx = i * 150f + 50f
            val len = 60f + ((i * 37) % 80)
            EnvArt.tapered(hangingTendrils, tx, -100f, tx + 10f, -100f + len * 0.5f, tx + 5f, -100f + len, 8f, 1f, 6)
        }
    }

    override fun drawBackdrop(canvas: Canvas, paint: Paint, camX: Float, camY: Float, viewW: Float, viewH: Float, t: Float) {
        // Deep misty teal void
        fillVoid(canvas, paint, viewW, viewH, EnvPalette.voidTop, EnvPalette.voidBottom)

        // Distant background cavern silhouettes (Parallax 0.25x)
        val paraX = camX * 0.25f
        val paraY = camY * 0.25f
        canvas.save()
        canvas.translate(-paraX, -paraY)

        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.farSilhouette
        for (i in 0..7) {
            val archX = i * 320f - 100f
            canvas.drawRoundRect(archX, 80f, archX + 110f, 650f, 24f, 24f, paint)
        }

        // Distant bioluminescent cavern orbs
        for (i in 0..5) {
            val ox = i * 360f + 40f
            val oy = 180f + sin(t * 1.5f + i) * 15f
            EnvArt.bloom(canvas, paint, ox, oy, 70f, 70f, EnvArt.glowTeal, 45)
        }

        canvas.restore()
    }

    override fun drawWorld(canvas: Canvas, paint: Paint, t: Float) {
        // 1. Draw Background Shrine Architecture
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.midRock
        canvas.drawPath(shrinePath, paint)

        // Shell rib ridges on shrine
        paint.style = Paint.Style.STROKE
        paint.color = EnvPalette.midRim
        paint.strokeWidth = 4f
        val sx = 1750f
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

        // 2. Draw Hand-Crafted Stone & Moss Slabs
        for (s in slabs) {
            s.draw(canvas, paint)
        }

        // 3. Hanging Tendrils from ceiling
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.ceilingRoot
        canvas.drawPath(hangingTendrils, paint)

        // 4. Bioluminescent Lake Below Chasm
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.waterDeep
        canvas.drawRect(310f, 560f, 1430f, 700f, paint)

        // Surface water shimmer
        paint.color = EnvPalette.waterTop
        val waterY = 562f + sin(t * 3f) * 2f
        canvas.drawLine(310f, waterY, 1430f, waterY, paint)

        // Water surface bloom
        EnvArt.bloom(canvas, paint, 600f, 565f, 220f, 30f, EnvArt.glowTeal, 60)
        EnvArt.bloom(canvas, paint, 1100f, 565f, 220f, 30f, EnvArt.glowTeal, 60)

        // Bridge pillars dipping into water
        paint.color = EnvPalette.rockShadow
        paint.strokeWidth = 6f
        canvas.drawLine(960f, 380f, 960f, 565f, paint)
        canvas.drawLine(1140f, 380f, 1140f, 565f, paint)
        canvas.drawLine(1300f, 380f, 1300f, 565f, paint)
    }

    override fun drawFront(canvas: Canvas, paint: Paint, camX: Float, camY: Float, viewW: Float, viewH: Float, t: Float) {
        // God rays / light shafts climbing through world space
        canvas.save()
        canvas.translate(-camX, -camY)
        for (shaft in lightShafts) {
            shaft.draw(canvas, paint)
        }

        // Floating spore motes drifting through light shafts
        paint.style = Paint.Style.FILL
        for (i in 0..24) {
            val seedX = (i * 89) % 2000
            val seedY = (i * 47) % 600
            val moteX = seedX + sin(t * 0.8f + i) * 25f
            val moteY = seedY + (t * 18f * (1f + (i % 3) * 0.4f)) % 600f
            paint.color = if (i % 3 == 0) EnvPalette.sporeWarm else EnvPalette.sporeTeal
            paint.alpha = (90 + sin(t * 2f + i) * 60).toInt().coerceIn(0, 255)
            canvas.drawCircle(moteX, moteY, 2f + (i % 3), paint)
        }

        canvas.restore()

        // Vignette framing around edges
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.foreground
        paint.alpha = 50
        canvas.drawRect(0f, 0f, viewW, 20f, paint)
        canvas.drawRect(0f, viewH - 25f, viewW, viewH, paint)
        paint.alpha = 255
    }
}
