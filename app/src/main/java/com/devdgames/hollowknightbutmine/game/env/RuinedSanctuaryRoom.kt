package com.devdgames.hollowknightbutmine.game.env

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.sin

/**
 * Room 3: Two-Story Ruined Overgrown Hall (Inspired by Image 4: grand gothic moss-covered cathedral arches,
 * balcony pillars, hanging foliage, and light streaming into ancient ruins).
 * Features:
 * - Upper grand balcony and lower colonnade floor.
 * - Magnificent background gothic arches overgrown with lush moss.
 * - Boss arena hosting the heavy Moss Charger.
 * - Towering light shafts slicing down through ruined ceiling skylights.
 */
class RuinedSanctuaryRoom : Room(
    id = "ruined_sanctuary",
    title = "The Overgrown Colonnade",
    bounds = RectF(-100f, -250f, 2400f, 750f)
) {
    private val lightShafts = ArrayList<LightShaft>()
    private val grandCathedral = Path()
    private val lushVines = Path()

    override fun build() {
        spawnX = 140f
        spawnY = 460f

        // --- Ground Colonnade Floor ---
        addSlab(RectF(-100f, 480f, 2300f, 620f), seed = 301, mossy = true)

        // --- Upper Balcony (Second Story) ---
        addSlab(RectF(350f, 320f, 850f, 355f), seed = 302, mossy = true)
        addSlab(RectF(1100f, 320f, 1600f, 355f), seed = 303, mossy = true)

        // Mid-air stepping ledges
        addSlab(RectF(920f, 230f, 1030f, 260f), seed = 304, mossy = true)
        addSlab(RectF(1750f, 260f, 1920f, 290f), seed = 305, mossy = true)

        // Boundaries
        solid(-100f, -350f, -20f, 750f)
        solid(2250f, -350f, 2350f, 750f)

        // Exits (connects back to dark_ribs)
        exit(-60f, 380f, 20f, 480f, "dark_ribs", 1980f, 440f)

        // Boss arena spawn: Moss Charger Mini-Boss + Spitter support
        enemySpawns.add(EnemySpawn(x = 600f, y = 320f, boss = false))   // Spitter sniper on balcony
        enemySpawns.add(EnemySpawn(x = 1350f, y = 320f, boss = false))  // Shielder on balcony
        enemySpawns.add(EnemySpawn(x = 980f, y = 140f, boss = false))   // Flying squit hovering high
        enemySpawns.add(EnemySpawn(x = 1650f, y = 480f, boss = true))   // MOSS CHARGER BOSS on floor!

        // Grand God Rays piercing through the high ceiling
        lightShafts.add(LightShaft(x = 600f, top = -200f, bottom = 540f, halfTop = 45f, halfBot = 130f, lean = 60f, color = EnvPalette.windowGlow, alpha = 55))
        lightShafts.add(LightShaft(x = 1350f, top = -200f, bottom = 540f, halfTop = 50f, halfBot = 150f, lean = -60f, color = EnvPalette.windowGlow, alpha = 55))

        // Grand Background Gothic Arches
        for (i in 0..7) {
            val ax = i * 280f + 60f
            grandCathedral.moveTo(ax - 55f, 480f)
            grandCathedral.lineTo(ax - 55f, 200f)
            grandCathedral.quadTo(ax, 120f, ax + 55f, 200f)
            grandCathedral.lineTo(ax + 55f, 480f)
        }

        // Hanging lush overgrown foliage
        for (i in 0..16) {
            val vx = i * 140f + 40f
            val len = 70f + ((i * 51) % 110)
            EnvArt.tapered(lushVines, vx, -120f, vx + 12f, -120f + len * 0.5f, vx + 6f, -120f + len, 10f, 1.2f, 6)
        }
    }

    override fun drawBackdrop(canvas: Canvas, paint: Paint, camX: Float, camY: Float, viewW: Float, viewH: Float, t: Float) {
        fillVoid(canvas, paint, viewW, viewH, Color.rgb(6, 12, 18), Color.rgb(10, 24, 28))

        // Parallax Gothic arcade
        val paraX = camX * 0.22f
        canvas.save()
        canvas.translate(-paraX, 0f)

        paint.style = Paint.Style.STROKE
        paint.color = EnvPalette.farSilhouette
        paint.strokeWidth = 18f
        canvas.drawPath(grandCathedral, paint)

        // Upper Gothic Windows glow
        for (i in 0..7) {
            val wx = i * 280f + 60f
            EnvArt.bloom(canvas, paint, wx, 220f, 45f, 85f, EnvArt.glowTeal, 45)
        }

        canvas.restore()
    }

    override fun drawWorld(canvas: Canvas, paint: Paint, t: Float) {
        // 1. Draw Balcony Pillars supporting upper story
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.rockShadow
        val pillarX = floatArrayOf(450f, 750f, 1200f, 1500f)
        for (px in pillarX) {
            canvas.drawRect(px - 14f, 355f, px + 14f, 480f, paint)
            // Pillar capital / carving
            paint.color = EnvPalette.mossDeep
            canvas.drawRoundRect(px - 20f, 350f, px + 20f, 370f, 6f, 6f, paint)
            paint.color = EnvPalette.rockShadow
        }

        // 2. Draw Slabs
        for (s in slabs) {
            s.draw(canvas, paint)
        }

        // 3. Hanging Overgrown Vines
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.ivyDeep
        canvas.drawPath(lushVines, paint)

        // 4. Overgrown Flower Clusters & Moss Cushions on Balcony
        paint.color = EnvPalette.mossLit
        for (i in 0..18) {
            val fx = 360f + i * 26f
            val fy = 318f + sin(fx * 0.1f) * 3f
            canvas.drawCircle(fx, fy, 4f, paint)
        }
        for (i in 0..18) {
            val fx = 1110f + i * 26f
            val fy = 318f + sin(fx * 0.1f) * 3f
            canvas.drawCircle(fx, fy, 4f, paint)
        }
    }

    override fun drawFront(canvas: Canvas, paint: Paint, camX: Float, camY: Float, viewW: Float, viewH: Float, t: Float) {
        // Light shafts
        canvas.save()
        canvas.translate(-camX, -camY)
        for (shaft in lightShafts) {
            shaft.draw(canvas, paint)
        }

        // Spores
        paint.style = Paint.Style.FILL
        for (i in 0..28) {
            val seedX = (i * 97) % 2200
            val seedY = (i * 53) % 600
            val moteX = seedX + sin(t * 0.7f + i) * 20f
            val moteY = seedY + (t * 15f * (1f + (i % 3) * 0.3f)) % 600f
            paint.color = if (i % 2 == 0) EnvPalette.sporeTeal else EnvPalette.sporeWarm
            paint.alpha = (80 + sin(t * 2f + i) * 50).toInt().coerceIn(0, 255)
            canvas.drawCircle(moteX, moteY, 2.5f, paint)
        }
        canvas.restore()
    }
}
