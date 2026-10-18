package com.devdgames.hollowknightbutmine.game.env

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.sin

/**
 * Room 2: Deep Chitin Rib Caverns (Inspired by Image 3: segmented ribbed bone pillars & fossil arches).
 * Features:
 * - Ancient carved fossil bones, segmented ribbed worm-like pillars.
 * - Hazardous spike beds and vertical platforming pillars.
 * - Shadowy fossil silhouettes in deep background.
 * - Cool deep indigo/cyan atmosphere with luminous pale blue lanterns.
 */
class DarkRibsRoom : Room(
    id = "dark_ribs",
    title = "Ossuary Depths: The Fossil Chasm",
    bounds = RectF(-100f, -200f, 2200f, 750f)
) {
    private val ribArches = Path()
    private val stalactites = Path()

    override fun build() {
        spawnX = 140f
        spawnY = 460f

        // Entry ledge
        addSlab(RectF(-100f, 480f, 320f, 620f), seed = 201, mossy = false, body = EnvPalette.stoneBody, rim = EnvPalette.stoneLit)

        // Segmented bone stepping platforms
        addSlab(RectF(400f, 420f, 550f, 455f), seed = 202, mossy = false, body = EnvPalette.boneDark, rim = EnvPalette.boneLit)
        addSlab(RectF(650f, 330f, 800f, 365f), seed = 203, mossy = false, body = EnvPalette.boneDark, rim = EnvPalette.boneLit)
        addSlab(RectF(480f, 210f, 630f, 245f), seed = 204, mossy = false, body = EnvPalette.boneDark, rim = EnvPalette.boneLit)

        // Tall Fossil Rib Tower
        addSlab(RectF(940f, 280f, 1140f, 620f), seed = 205, mossy = false, body = EnvPalette.boneMid, rim = EnvPalette.boneLit)

        // Floating bone slab
        addSlab(RectF(1240f, 350f, 1420f, 385f), seed = 206, mossy = false, body = EnvPalette.stoneBody, rim = EnvPalette.stoneLit)
        addSlab(RectF(1500f, 250f, 1720f, 285f), seed = 207, mossy = false, body = EnvPalette.boneDark, rim = EnvPalette.boneLit)

        // Exit ledge on the far right
        addSlab(RectF(1780f, 440f, 2200f, 620f), seed = 208, mossy = false, body = EnvPalette.stoneBody, rim = EnvPalette.stoneLit)

        // Boundaries
        solid(-100f, -300f, -20f, 750f)
        solid(2120f, -300f, 2200f, 750f)

        // Spike pit hazards along floor
        hazard(320f, 570f, 940f, 620f)
        hazard(1140f, 570f, 1780f, 620f)

        // Exits (connects back to mossy_bridge or forward to ruined_sanctuary)
        exit(-60f, 380f, 20f, 480f, "mossy_bridge", 1920f, 440f)
        exit(2080f, 340f, 2160f, 440f, "ruined_sanctuary", 160f, 460f)

        // Spawns
        enemySpawns.add(EnemySpawn(x = 520f, y = 210f, boss = false))   // Flying squit
        enemySpawns.add(EnemySpawn(x = 1040f, y = 280f, boss = false))  // Shielder holding top of bone tower
        enemySpawns.add(EnemySpawn(x = 1330f, y = 350f, boss = false))  // Acid spitter
        enemySpawns.add(EnemySpawn(x = 1960f, y = 440f, boss = false))  // Crawler

        // Ceiling Stalactites
        for (i in 0..12) {
            val sx = i * 170f + 60f
            val len = 50f + ((i * 43) % 90)
            stalactites.moveTo(sx - 14f, -100f)
            stalactites.lineTo(sx, -100f + len)
            stalactites.lineTo(sx + 14f, -100f)
            stalactites.close()
        }

        // Giant Background Rib Cage
        for (i in 0..6) {
            val rx = i * 280f + 120f
            ribArches.moveTo(rx - 60f, 500f)
            ribArches.cubicTo(rx - 80f, 200f, rx + 80f, 120f, rx + 100f, 280f)
        }
    }

    override fun drawBackdrop(canvas: Canvas, paint: Paint, camX: Float, camY: Float, viewW: Float, viewH: Float, t: Float) {
        // Deep obsidian indigo cavern wash
        fillVoid(canvas, paint, viewW, viewH, Color.rgb(4, 6, 12), Color.rgb(10, 16, 26))

        // Parallax background giant fossil ribs
        val paraX = camX * 0.2f
        canvas.save()
        canvas.translate(-paraX, 0f)

        paint.style = Paint.Style.STROKE
        paint.color = EnvPalette.farSilhouette
        paint.strokeWidth = 24f
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawPath(ribArches, paint)

        // Dim ancient will-o'-the-wisp blooms
        for (i in 0..4) {
            val wx = i * 440f + 160f
            val wy = 260f + sin(t + i) * 20f
            EnvArt.bloom(canvas, paint, wx, wy, 80f, 80f, EnvArt.glowTeal, 40)
        }

        canvas.restore()
    }

    override fun drawWorld(canvas: Canvas, paint: Paint, t: Float) {
        // 1. Draw Slabs
        for (s in slabs) {
            s.draw(canvas, paint)
        }

        // 2. Draw Segmented Rib Details on Bone Tower (x = 940 to 1140)
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.boneMid
        val towerLeft = 940f
        val towerRight = 1140f
        for (r in 0..7) {
            val segY = 310f + r * 40f
            canvas.drawRoundRect(towerLeft + 10f, segY, towerRight - 10f, segY + 28f, 10f, 10f, paint)

            // Segment tooth beads (like the segmented centipede pillars from reference image 3)
            paint.color = EnvPalette.boneLit
            for (tb in 0..4) {
                val bx = towerLeft + 25f + tb * 35f
                canvas.drawCircle(bx, segY + 14f, 4f, paint)
            }
            paint.color = EnvPalette.boneMid
        }

        // 3. Draw Stalactites from ceiling
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.stoneBody
        canvas.drawPath(stalactites, paint)

        // 4. Draw Floor Spikes in pits
        paint.color = Color.rgb(180, 190, 200)
        drawSpikeBed(canvas, paint, 320f, 940f, 570f)
        drawSpikeBed(canvas, paint, 1140f, 1780f, 570f)

        // Pale glowing ancient soul crystal lantern
        paint.color = EnvPalette.boneLit
        canvas.drawCircle(650f, 310f, 8f, paint)
        EnvArt.bloom(canvas, paint, 650f, 310f, 55f, 55f, EnvArt.glowWarm, 85)
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
        // Darkness vignette and creeping shadows
        paint.style = Paint.Style.FILL
        paint.color = EnvPalette.foreground
        paint.alpha = 75
        canvas.drawRect(0f, 0f, 35f, viewH, paint)
        canvas.drawRect(viewW - 35f, 0f, viewW, viewH, paint)
        paint.alpha = 255
    }
}
