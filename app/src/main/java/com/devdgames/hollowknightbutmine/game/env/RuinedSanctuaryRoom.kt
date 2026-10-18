package com.devdgames.hollowknightbutmine.game.env

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.devdgames.hollowknightbutmine.game.EnemyType
import kotlin.math.sin

/**
 * Room 3: Extended Ruined Sanctuary & The Grand Colonnade
 * Spans ~4800px wide across three majestic tiers:
 * - Section 1: The Outer Ruined Portico & Courtyard (x = 0 to 1500)
 * - Section 2: The Two-Story Grand Balconies & Colonnades (x = 1500 to 3200)
 * - Section 3: The High Throne Sanctum & Boss Arena (x = 3200 to 4800)
 */
class RuinedSanctuaryRoom : Room(
    id = "ruined_sanctuary",
    title = "The Overgrown Colonnade",
    bounds = RectF(-100f, -250f, 4800f, 750f)
) {
    private val lightShafts = ArrayList<LightShaft>()
    private val grandCathedral = Path()
    private val lushVines = Path()
    private val distantTowers = Path()
    private val distantBanners = Path()

    override fun build() {
        spawnX = 140f
        spawnY = 460f

        // ================= SECTION 1: OUTER PORTICO & COURTYARD (0 - 1500) =================
        // Ground floor
        addSlab(RectF(-100f, 480f, 1500f, 620f), seed = 301, mossy = true)

        // Courtyard stepping terraces
        addSlab(RectF(320f, 410f, 480f, 440f), seed = 309, mossy = true)
        addSlab(RectF(540f, 340f, 720f, 370f), seed = 310, mossy = true)
        addSlab(RectF(780f, 270f, 960f, 300f), seed = 311, mossy = true)
        addSlab(RectF(1020f, 340f, 1200f, 370f), seed = 312, mossy = true)
        addSlab(RectF(1260f, 410f, 1420f, 440f), seed = 313, mossy = true)

        // ================= SECTION 2: GRAND TWO-STORY BALCONIES (1500 - 3200) =================
        // Continuous lower colonnade floor
        addSlab(RectF(1500f, 480f, 3200f, 620f), seed = 314, mossy = true)

        // Balcony ascent step
        addSlab(RectF(1560f, 410f, 1720f, 440f), seed = 306, mossy = true)

        // First Upper Balcony (340f)
        addSlab(RectF(1780f, 340f, 2280f, 375f), seed = 302, mossy = true)

        // Central sky bridge between balconies (~70px interval)
        addSlab(RectF(2340f, 355f, 2480f, 385f), seed = 307, mossy = true)
        addSlab(RectF(2360f, 275f, 2500f, 305f), seed = 304, mossy = true)

        // Second Upper Balcony (340f)
        addSlab(RectF(2560f, 340f, 3060f, 375f), seed = 303, mossy = true)

        // High vantage chandeliers / secret ledges
        addSlab(RectF(2000f, 260f, 2180f, 290f), seed = 315, mossy = true)
        addSlab(RectF(2700f, 260f, 2880f, 290f), seed = 316, mossy = true)

        // ================= SECTION 3: THE THRONE ARENA (3200 - 4800) =================
        // Colonnade extends into boss chamber floor
        addSlab(RectF(3200f, 480f, 4700f, 620f), seed = 317, mossy = true)

        // Tiered audience balconies overlooking the arena
        addSlab(RectF(3300f, 410f, 3480f, 440f), seed = 318, mossy = true)
        addSlab(RectF(3420f, 335f, 3620f, 365f), seed = 319, mossy = true)

        // Overhang royal box high on the right
        addSlab(RectF(4350f, 340f, 4650f, 370f), seed = 320, mossy = true)
        addSlab(RectF(4450f, 265f, 4650f, 295f), seed = 321, mossy = true)

        // Boundaries
        solid(-100f, -350f, -20f, 750f)
        solid(4680f, -350f, 4800f, 750f)

        // Exits
        exit(-60f, 380f, 20f, 480f, "dark_ribs", 4380f, 440f)

        // ================= ENEMIES ACROSS THE 4800px SANCTUARY =================
        // Section 1: Courtyard guards
        enemySpawns.add(EnemySpawn(x = 620f, y = 340f, type = EnemyType.CRAWLER))
        enemySpawns.add(EnemySpawn(x = 880f, y = 200f, type = EnemyType.FLYER))
        enemySpawns.add(EnemySpawn(x = 1110f, y = 340f, type = EnemyType.SPITTER))
        enemySpawns.add(EnemySpawn(x = 1350f, y = 480f, type = EnemyType.CRAWLER))

        // Section 2: Balcony archers & shielders
        enemySpawns.add(EnemySpawn(x = 1950f, y = 340f, type = EnemyType.SPITTER))
        enemySpawns.add(EnemySpawn(x = 2150f, y = 340f, type = EnemyType.SHIELDER))
        enemySpawns.add(EnemySpawn(x = 2420f, y = 200f, type = EnemyType.FLYER))
        enemySpawns.add(EnemySpawn(x = 2750f, y = 340f, type = EnemyType.SHIELDER))
        enemySpawns.add(EnemySpawn(x = 2950f, y = 340f, type = EnemyType.SPITTER))

        // Section 3: Boss Chamber
        enemySpawns.add(EnemySpawn(x = 3520f, y = 335f, type = EnemyType.SHIELDER))
        enemySpawns.add(EnemySpawn(x = 4500f, y = 265f, type = EnemyType.FLYER))
        enemySpawns.add(EnemySpawn(x = 4050f, y = 480f, boss = true, type = EnemyType.COLONNADE_KNIGHT)) // BOSS

        // Grand God Rays across 4800px
        lightShafts.add(LightShaft(x = 850f, top = -200f, bottom = 540f, halfTop = 45f, halfBot = 130f, lean = 60f, color = EnvPalette.windowGlow, alpha = 50))
        lightShafts.add(LightShaft(x = 2000f, top = -200f, bottom = 540f, halfTop = 50f, halfBot = 150f, lean = 70f, color = EnvPalette.windowGlow, alpha = 55))
        lightShafts.add(LightShaft(x = 2800f, top = -200f, bottom = 540f, halfTop = 50f, halfBot = 150f, lean = -60f, color = EnvPalette.windowGlow, alpha = 55))
        lightShafts.add(LightShaft(x = 4100f, top = -200f, bottom = 540f, halfTop = 65f, halfBot = 180f, lean = 80f, color = EnvPalette.windowGlow, alpha = 65))

        // Grand Background Gothic Arches across 4800px
        for (i in 0..16) {
            val ax = i * 280f + 60f
            grandCathedral.moveTo(ax - 55f, 480f)
            grandCathedral.lineTo(ax - 55f, 200f)
            grandCathedral.quadTo(ax, 120f, ax + 55f, 200f)
            grandCathedral.lineTo(ax + 55f, 480f)
        }

        // Prebuild distant tower roofs and hanging banners once.
        for (i in 0..15) {
            val tx = i * 300f + 20f
            val towerH = 150f + ((i * 41) % 180)
            distantTowers.moveTo(tx - 8f, 480f - towerH)
            distantTowers.lineTo(tx + 39f, 450f - towerH)
            distantTowers.lineTo(tx + 86f, 480f - towerH)
            distantTowers.close()
        }
        for (i in 0..7) {
            val bx = i * 560f + 160f
            distantBanners.moveTo(bx, 105f)
            distantBanners.lineTo(bx + 27f, 113f)
            distantBanners.lineTo(bx + 21f, 158f)
            distantBanners.lineTo(bx + 11f, 147f)
            distantBanners.lineTo(bx, 160f)
            distantBanners.close()
        }

        // Hanging lush vines across ceiling
        for (i in 0..32) {
            val vx = i * 140f + 40f
            val len = 70f + ((i * 51) % 115)
            EnvArt.tapered(lushVines, vx, -120f, vx + 12f, -120f + len * 0.5f, vx + 6f, -120f + len, 10f, 1.2f, 6)
        }
    }

    override fun drawBackdrop(canvas: Canvas, paint: Paint, camX: Float, camY: Float, viewW: Float, viewH: Float, t: Float) {
        // Shared backdrop gradient is painted once by the first biome.

        val paraX = camX * 0.25f
        canvas.save()
        canvas.translate(-paraX, 0f)

        paint.style = Paint.Style.STROKE
        paint.color = EnvPalette.farSilhouette
        paint.strokeWidth = 18f
        canvas.drawPath(grandCathedral, paint)

        // Layered ruined towers behind the cathedral arches.
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(8, 24, 29)
        for (i in 0..15) {
            val tx = i * 300f + 20f
            val towerH = 150f + ((i * 41) % 180)
            canvas.drawRect(tx, 480f - towerH, tx + 78f, 520f, paint)
            // Roof silhouettes are drawn together below.

            // Narrow, faint windows in each distant tower.
            paint.color = Color.argb(70, 83, 211, 194)
            for (w in 0..2) {
                val wx = tx + 13f + w * 22f
                canvas.drawRoundRect(wx, 500f - towerH, wx + 7f, 520f - towerH, 3f, 3f, paint)
            }
            paint.color = Color.rgb(8, 24, 29)
        }

        paint.color = Color.rgb(8, 24, 29)
        canvas.drawPath(distantTowers, paint)

        // A few distant hanging banners add scale to the old royal hall.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.rgb(24, 55, 56)
        for (i in 0..7) {
            val bx = i * 560f + 160f
            canvas.drawLine(bx, 90f, bx, 190f, paint)
            paint.style = Paint.Style.FILL
            paint.style = Paint.Style.STROKE
        }

        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(24, 55, 56)
        canvas.drawPath(distantBanners, paint)

        // Gothic Windows glow
        paint.style = Paint.Style.FILL
        for (i in 0..16) {
            val wx = i * 280f + 60f
            EnvArt.bloom(canvas, paint, wx, 220f, 45f, 85f, EnvArt.glowTeal, 45)
        }

        // Extra distant gothic spires
paint.style = Paint.Style.FILL
paint.color = EnvPalette.farSilhouette
for (i in 0..6) {
    val spireX = i * 500f + 150f
    canvas.drawRoundRect(spireX, 40f, spireX + 90f, 210f, 14f, 14f, paint)
}
canvas.restore()
    }

    override fun drawWorld(canvas: Canvas, paint: Paint, t: Float) {
        // 1. Draw Balcony Pillars (Section 2: 1780-3060, Section 3: 4350-4650)
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.rockShadow
        val pillarX = floatArrayOf(1880f, 2180f, 2660f, 2960f, 4400f, 4600f)
        for (px in pillarX) {
            canvas.drawRect(px - 14f, 355f, px + 14f, 480f, paint)
            paint.color = EnvPalette.mossDeep
            canvas.drawRoundRect(px - 20f, 350f, px + 20f, 370f, 6f, 6f, paint)
            paint.color = EnvPalette.rockShadow
        }

        // 2. Draw Slabs
        drawSlabs(canvas, paint, 0f, 0f, 4800f, 800f)

        // 3. Hanging Overgrown Vines
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.ivyDeep
        canvas.drawPath(lushVines, paint)

        // 4. Overgrown Flower Clusters & Moss Cushions on Balconies
        paint.color = EnvPalette.mossLit
        for (i in 0..18) {
            val fx = 1790f + i * 26f
            val fy = 338f + sin(fx * 0.1f) * 3f
            canvas.drawCircle(fx, fy, 4f, paint)
        }
        for (i in 0..18) {
            val fx = 2570f + i * 26f
            val fy = 338f + sin(fx * 0.1f) * 3f
            canvas.drawCircle(fx, fy, 4f, paint)
        }
    }

    override fun drawFront(canvas: Canvas, paint: Paint, camX: Float, camY: Float, viewW: Float, viewH: Float, t: Float) {
        canvas.save()
        canvas.translate(-camX, -camY)
        for (shaft in lightShafts) {
            shaft.draw(canvas, paint)
        }

        paint.style = Paint.Style.FILL
        for (i in 0..36) {
            val seedX = (i * 97) % 4700
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
