package com.devdgames.hollowknightbutmine.game.env

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.devdgames.hollowknightbutmine.game.world.Biome
import kotlin.math.sin
import kotlin.random.Random

/**
 * Hand-drawn props. Every function draws one thing at a world position with a handful of
 * canvas calls; the scene culls them before calling, so nothing here worries about that.
 * `x` is the horizontal centre and `y` the ground line the prop stands on.
 */
object Props {

    private val path = Path()

    private fun fill(paint: Paint, color: Int, alpha: Int = 255) {
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = color
        paint.alpha = alpha
    }

    private fun stroke(paint: Paint, color: Int, width: Float, alpha: Int = 255) {
        paint.style = Paint.Style.STROKE
        paint.shader = null
        paint.strokeWidth = width
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = color
        paint.alpha = alpha
    }

    // ------------------------------------------------------------------ bench

    /** A Hallownest-style iron bench: curled armrests, slatted back, soft glow when lit. */
    fun bench(canvas: Canvas, paint: Paint, x: Float, y: Float, lit: Boolean, t: Float) {
        if (lit) {
            val pulse = 0.85f + sin(t * 2.2f) * 0.15f
            EnvArt.bloom(canvas, paint, x, y - 26f, 120f * pulse, 70f * pulse, EnvArt.glowWarm, 70)
        }
        val iron = Color.rgb(26, 28, 36)
        val ironLit = Color.rgb(96, 104, 124)
        // legs
        stroke(paint, iron, 5f)
        canvas.drawLine(x - 34f, y, x - 30f, y - 22f, paint)
        canvas.drawLine(x + 34f, y, x + 30f, y - 22f, paint)
        // seat
        fill(paint, iron)
        canvas.drawRoundRect(x - 44f, y - 28f, x + 44f, y - 20f, 4f, 4f, paint)
        // back rest: curved rail with slats
        stroke(paint, iron, 4f)
        path.reset()
        path.moveTo(x - 42f, y - 26f)
        path.cubicTo(x - 46f, y - 48f, x - 30f, y - 60f, x, y - 60f)
        path.cubicTo(x + 30f, y - 60f, x + 46f, y - 48f, x + 42f, y - 26f)
        canvas.drawPath(path, paint)
        stroke(paint, iron, 3f)
        for (i in -2..2) {
            val sx = x + i * 14f
            canvas.drawLine(sx, y - 28f, sx, y - 52f + kotlin.math.abs(i) * 3f, paint)
        }
        // scroll arm-rests
        stroke(paint, ironLit, 2f)
        canvas.drawArc(RectF(x - 50f, y - 44f, x - 36f, y - 30f), 0f, 300f, false, paint)
        canvas.drawArc(RectF(x + 36f, y - 44f, x + 50f, y - 30f), 240f, 300f, false, paint)
        stroke(paint, ironLit, 1.5f)
        canvas.drawLine(x - 40f, y - 23f, x + 40f, y - 23f, paint)
        paint.strokeCap = Paint.Cap.BUTT
    }

    // ------------------------------------------------------------------ lamps

    fun lamp(canvas: Canvas, paint: Paint, biome: Biome, x: Float, y: Float, hangY: Float, ceilY: Float, glow: Int, t: Float, seed: Int) {
        val flicker = 0.9f + sin(t * 5.3f + seed) * 0.06f + sin(t * 13.1f + seed * 2) * 0.04f
        when (biome) {
            Biome.TOWN, Biome.CITY -> {
                // Lamp post with a hanging, glowing head.
                val iron = Color.rgb(24, 26, 34)
                stroke(paint, iron, 5f)
                canvas.drawLine(x, y, x, y - 108f, paint)
                stroke(paint, iron, 4f)
                path.reset(); path.moveTo(x, y - 108f); path.quadTo(x + 4f, y - 126f, x + 22f, y - 122f)
                canvas.drawPath(path, paint)
                fill(paint, iron)
                canvas.drawRoundRect(x + 14f, y - 124f, x + 30f, y - 96f, 3f, 3f, paint)
                EnvArt.bloom(canvas, paint, x + 22f, y - 110f, 90f * flicker, 90f * flicker, EnvArt.glowWarm, 120)
                fill(paint, glow)
                canvas.drawRoundRect(x + 17f, y - 120f, x + 27f, y - 100f, 2f, 2f, paint)
                fill(paint, iron)
                canvas.drawRoundRect(x - 10f, y - 8f, x + 10f, y, 3f, 3f, paint)
            }
            Biome.CROSSROADS, Biome.CLIFFS, Biome.RESTING, Biome.BASIN, Biome.WATERWAYS -> {
                // Lantern hanging on a chain from the ceiling above its marker tile.
                val top = hangY - 6f
                stroke(paint, Color.rgb(50, 56, 70), 2.5f)
                val chainTop = if (ceilY.isNaN() || top - ceilY > 320f) top - 60f else ceilY
                canvas.drawLine(x, chainTop, x, top - 6f, paint)
                fill(paint, Color.rgb(32, 36, 48))
                canvas.drawRoundRect(x - 9f, top - 8f, x + 9f, top + 18f, 4f, 4f, paint)
                EnvArt.bloom(canvas, paint, x, top + 5f, 80f * flicker, 80f * flicker, if (biome == Biome.BASIN) EnvArt.glowPale else EnvArt.glowWarm, 110)
                fill(paint, glow)
                canvas.drawRoundRect(x - 5f, top - 3f, x + 5f, top + 13f, 2f, 2f, paint)
            }
            Biome.GREEN, Biome.FUNGAL -> {
                // Cluster of tall bioluminescent mushrooms.
                for (i in 0..2) {
                    val mx = x + (i - 1) * 12f
                    val h = 22f + (i * 7 + seed) % 14
                    fill(paint, Color.rgb(196, 216, 200))
                    canvas.drawRoundRect(mx - 2.5f, y - h, mx + 2.5f, y, 2f, 2f, paint)
                    fill(paint, glow)
                    canvas.drawOval(mx - 11f, y - h - 7f, mx + 11f, y - h + 4f, paint)
                }
                EnvArt.bloom(canvas, paint, x, y - 24f, 70f * flicker, 50f * flicker, EnvArt.glowTeal, 100)
            }
            Biome.CRYSTAL -> {
                crystalCluster(canvas, paint, x, y, 1.3f, glow, seed)
                EnvArt.bloom(canvas, paint, x, y - 24f, 90f * flicker, 70f * flicker, EnvArt.glowRose, 60)
            }
            Biome.EDGE -> {
                // Stone brazier with a pale flame.
                fill(paint, Color.rgb(40, 40, 46))
                canvas.drawRect(x - 6f, y - 30f, x + 6f, y, paint)
                canvas.drawRoundRect(x - 18f, y - 44f, x + 18f, y - 28f, 6f, 6f, paint)
                EnvArt.bloom(canvas, paint, x, y - 52f, 80f * flicker, 70f * flicker, EnvArt.glowWarm, 110)
                fill(paint, glow)
                path.reset()
                path.moveTo(x - 10f, y - 42f)
                path.quadTo(x - 12f, y - 62f, x, y - 74f - sin(t * 9f) * 4f)
                path.quadTo(x + 12f, y - 62f, x + 10f, y - 42f)
                path.close()
                canvas.drawPath(path, paint)
            }
            Biome.DEEPNEST -> {
                // A guttering candle in the dark.
                fill(paint, Color.rgb(200, 180, 150))
                canvas.drawRect(x - 4f, y - 16f, x + 4f, y, paint)
                EnvArt.bloom(canvas, paint, x, y - 20f, 60f * flicker, 60f * flicker, EnvArt.glowWarm, 140)
                fill(paint, glow)
                canvas.drawOval(x - 3f, y - 26f - sin(t * 11f) * 2f, x + 3f, y - 16f, paint)
            }
            Biome.ABYSS -> {
                EnvArt.bloom(canvas, paint, x, y - 20f, 60f * flicker, 60f * flicker, EnvArt.glowVoid, 120)
            }
        }
    }

    // ------------------------------------------------------------------ landmarks ('*')

    fun landmark(canvas: Canvas, paint: Paint, biome: Biome, x: Float, y: Float, glow: Int, t: Float, seed: Int) {
        when (biome) {
            Biome.TOWN -> house(canvas, paint, x, y, seed)
            Biome.RESTING -> grave(canvas, paint, x, y, seed)
            Biome.ABYSS -> shadeStatue(canvas, paint, x, y, t)
            Biome.FUNGAL -> giantMushroom(canvas, paint, x, y, glow, seed)
            Biome.CRYSTAL -> crystalCluster(canvas, paint, x, y, 2.4f, glow, seed)
            else -> statue(canvas, paint, x, y, seed)
        }
    }

    /** Dustmere house silhouette: steep roof, chimney, one warm window. */
    fun house(canvas: Canvas, paint: Paint, x: Float, y: Float, seed: Int) {
        val rnd = Random(seed)
        val w = 110f + rnd.nextInt(50)
        val h = 70f + rnd.nextInt(30)
        val wall = Color.rgb(22, 23, 32)
        val roof = Color.rgb(14, 15, 22)
        fill(paint, wall)
        canvas.drawRect(x - w / 2, y - h, x + w / 2, y, paint)
        fill(paint, roof)
        path.reset()
        path.moveTo(x - w / 2 - 12f, y - h + 4f)
        path.lineTo(x - 6f + rnd.nextInt(12), y - h - 58f - rnd.nextInt(20))
        path.lineTo(x + w / 2 + 12f, y - h + 4f)
        path.close()
        canvas.drawPath(path, paint)
        canvas.drawRect(x + w / 4, y - h - 40f, x + w / 4 + 12f, y - h, paint)
        // window
        val wx = x - w / 4 + rnd.nextInt(10)
        EnvArt.bloom(canvas, paint, wx, y - h * 0.55f, 46f, 46f, EnvArt.glowWarm, 60)
        fill(paint, Color.rgb(255, 196, 110))
        canvas.drawRoundRect(wx - 8f, y - h * 0.55f - 10f, wx + 8f, y - h * 0.55f + 8f, 2f, 2f, paint)
        stroke(paint, wall, 2f)
        canvas.drawLine(wx, y - h * 0.55f - 10f, wx, y - h * 0.55f + 8f, paint)
        // door
        fill(paint, Color.rgb(10, 10, 16))
        canvas.drawRoundRect(x + 8f, y - 34f, x + 26f, y, 6f, 6f, paint)
    }

    fun grave(canvas: Canvas, paint: Paint, x: Float, y: Float, seed: Int) {
        val rnd = Random(seed)
        val h = 34f + rnd.nextInt(26)
        val stone = Color.rgb(44, 40, 60)
        fill(paint, stone)
        if (rnd.nextBoolean()) {
            canvas.drawRoundRect(x - 13f, y - h, x + 13f, y, 12f, 12f, paint)
        } else {
            canvas.drawRect(x - 11f, y - h, x + 11f, y, paint)
            canvas.drawRect(x - 20f, y - h + 10f, x + 20f, y - h + 18f, paint)
        }
        canvas.drawRect(x - 18f, y - 5f, x + 18f, y, paint)
        stroke(paint, Color.rgb(90, 84, 116), 1.5f)
        canvas.drawLine(x - 6f, y - h * 0.6f, x + 6f, y - h * 0.6f, paint)
        canvas.drawLine(x, y - h * 0.75f, x, y - h * 0.45f, paint)
    }

    fun statue(canvas: Canvas, paint: Paint, x: Float, y: Float, seed: Int) {
        val stone = Color.rgb(36, 42, 58)
        val lit = Color.rgb(96, 108, 136)
        fill(paint, stone)
        canvas.drawRect(x - 26f, y - 14f, x + 26f, y, paint)
        canvas.drawRect(x - 18f, y - 24f, x + 18f, y - 14f, paint)
        // robed figure
        path.reset()
        path.moveTo(x - 16f, y - 24f)
        path.quadTo(x - 14f, y - 70f, x, y - 92f)
        path.quadTo(x + 14f, y - 70f, x + 16f, y - 24f)
        path.close()
        canvas.drawPath(path, paint)
        canvas.drawCircle(x, y - 96f, 11f, paint)
        // horns
        stroke(paint, stone, 4f)
        canvas.drawLine(x - 7f, y - 102f, x - 16f, y - 122f + (seed % 5), paint)
        canvas.drawLine(x + 7f, y - 102f, x + 16f, y - 122f + (seed % 7), paint)
        stroke(paint, lit, 1.5f)
        canvas.drawLine(x - 26f, y - 14f, x + 26f, y - 14f, paint)
        canvas.drawArc(RectF(x - 11f, y - 107f, x + 11f, y - 85f), 200f, 140f, false, paint)
    }

    /** The hollow figure waiting at the bottom of the abyss. */
    fun shadeStatue(canvas: Canvas, paint: Paint, x: Float, y: Float, t: Float) {
        EnvArt.bloom(canvas, paint, x, y - 70f, 150f, 150f, EnvArt.glowVoid, 90)
        val void = Color.rgb(4, 4, 8)
        fill(paint, void)
        path.reset()
        path.moveTo(x - 30f, y)
        path.quadTo(x - 34f, y - 90f, x, y - 140f)
        path.quadTo(x + 34f, y - 90f, x + 30f, y)
        path.close()
        canvas.drawPath(path, paint)
        // wisps
        stroke(paint, void, 3f)
        for (i in 0..4) {
            val wx = x - 24f + i * 12f
            val sway = sin(t * 1.6f + i) * 6f
            canvas.drawLine(wx, y - 4f, wx + sway, y + 14f, paint)
        }
        // mask
        fill(paint, Color.rgb(228, 232, 240))
        canvas.drawOval(x - 16f, y - 150f, x + 16f, y - 112f, paint)
        stroke(paint, Color.rgb(228, 232, 240), 5f)
        canvas.drawLine(x - 10f, y - 144f, x - 22f, y - 172f, paint)
        canvas.drawLine(x + 10f, y - 144f, x + 22f, y - 172f, paint)
        fill(paint, void)
        canvas.drawOval(x - 9f, y - 136f, x - 3f, y - 122f, paint)
        canvas.drawOval(x + 3f, y - 136f, x + 9f, y - 122f, paint)
        paint.strokeCap = Paint.Cap.BUTT
    }

    fun giantMushroom(canvas: Canvas, paint: Paint, x: Float, y: Float, glow: Int, seed: Int) {
        val rnd = Random(seed)
        val h = 120f + rnd.nextInt(60)
        val capW = 90f + rnd.nextInt(50)
        fill(paint, Color.rgb(150, 128, 118))
        path.reset()
        path.moveTo(x - 14f, y)
        path.quadTo(x - 10f, y - h * 0.6f, x - 9f, y - h)
        path.lineTo(x + 9f, y - h)
        path.quadTo(x + 10f, y - h * 0.6f, x + 14f, y)
        path.close()
        canvas.drawPath(path, paint)
        EnvArt.bloom(canvas, paint, x, y - h + 10f, capW * 0.9f, 50f, EnvArt.glowWarm, 60)
        fill(paint, Color.rgb(176, 84, 72))
        canvas.drawOval(x - capW / 2, y - h - 36f, x + capW / 2, y - h + 14f, paint)
        fill(paint, Color.rgb(96, 36, 44))
        canvas.drawRect(x - capW / 2, y - h - 10f, x + capW / 2, y - h + 14f, paint)
        fill(paint, Color.rgb(176, 84, 72))
        canvas.drawOval(x - capW / 2, y - h - 36f, x + capW / 2, y - h + 10f, paint)
        fill(paint, glow, 200)
        for (i in 0..3) {
            val sx = x - capW / 2 + 14f + rnd.nextInt((capW - 28f).toInt().coerceAtLeast(1))
            canvas.drawCircle(sx, y - h - 18f + rnd.nextInt(14), 3f + rnd.nextInt(3), paint)
        }
    }

    fun crystalCluster(canvas: Canvas, paint: Paint, x: Float, y: Float, scale: Float, glow: Int, seed: Int) {
        val rnd = Random(seed)
        val n = 3 + rnd.nextInt(3)
        for (i in 0 until n) {
            val cx = x + (rnd.nextFloat() - 0.5f) * 36f * scale
            val h = (18f + rnd.nextFloat() * 26f) * scale
            val w = (5f + rnd.nextFloat() * 6f) * scale
            val lean = (rnd.nextFloat() - 0.5f) * 14f * scale
            path.reset()
            path.moveTo(cx - w, y)
            path.lineTo(cx + lean, y - h)
            path.lineTo(cx + w, y)
            path.close()
            fill(paint, glow, 200)
            canvas.drawPath(path, paint)
            path.reset()
            path.moveTo(cx - w * 0.3f, y)
            path.lineTo(cx + lean * 0.9f, y - h * 0.92f)
            path.lineTo(cx + w * 0.3f, y)
            path.close()
            fill(paint, Color.WHITE, 110)
            canvas.drawPath(path, paint)
        }
        EnvArt.bloom(canvas, paint, x, y - 14f * scale, 50f * scale, 36f * scale, EnvArt.glowRose, 50)
    }

    /** Stone well ring drawn at the top of a surface shaft. */
    fun well(canvas: Canvas, paint: Paint, left: Float, right: Float, y: Float) {
        val stone = Color.rgb(36, 38, 48)
        val lit = Color.rgb(80, 84, 100)
        fill(paint, stone)
        canvas.drawRoundRect(left - 14f, y - 26f, left + 2f, y, 4f, 4f, paint)
        canvas.drawRoundRect(right - 2f, y - 26f, right + 14f, y, 4f, 4f, paint)
        stroke(paint, stone, 6f)
        canvas.drawLine(left - 6f, y - 26f, left - 6f, y - 96f, paint)
        canvas.drawLine(right + 6f, y - 26f, right + 6f, y - 96f, paint)
        fill(paint, stone)
        path.reset()
        path.moveTo(left - 22f, y - 92f)
        path.lineTo((left + right) / 2f, y - 122f)
        path.lineTo(right + 22f, y - 92f)
        path.close()
        canvas.drawPath(path, paint)
        stroke(paint, lit, 2f)
        canvas.drawLine(left - 14f, y - 26f, left + 2f, y - 26f, paint)
        canvas.drawLine(right - 2f, y - 26f, right + 14f, y - 26f, paint)
        // rope and bucket
        stroke(paint, Color.rgb(110, 92, 66), 2f)
        canvas.drawLine((left + right) / 2f, y - 92f, (left + right) / 2f, y - 40f, paint)
        fill(paint, stone)
        canvas.drawRoundRect((left + right) / 2f - 8f, y - 42f, (left + right) / 2f + 8f, y - 28f, 3f, 3f, paint)
    }

    // ------------------------------------------------------------------ hazards

    private val spikePath = Path()

    fun spikeBed(canvas: Canvas, paint: Paint, left: Float, right: Float, top: Float, bottom: Float, body: Int, lit: Int) {
        spikePath.reset()
        var cx = left
        val w = 16f
        while (cx + w <= right + 0.5f) {
            spikePath.moveTo(cx, bottom)
            spikePath.lineTo(cx + w * 0.5f, top + 4f)
            spikePath.lineTo(cx + w, bottom)
            spikePath.close()
            cx += w
        }
        fill(paint, body)
        canvas.drawPath(spikePath, paint)
        stroke(paint, lit, 1.4f)
        canvas.drawPath(spikePath, paint)
    }

    fun acidPool(canvas: Canvas, paint: Paint, r: RectF, body: Int, lit: Int, t: Float) {
        fill(paint, body, 200)
        canvas.drawRect(r.left, r.top + 6f, r.right, r.bottom, paint)
        val wave = sin(t * 2.6f + r.left * 0.01f) * 2f
        stroke(paint, lit, 2.5f, 220)
        path.reset()
        var x = r.left
        path.moveTo(x, r.top + 6f + wave)
        while (x < r.right) {
            x += 18f
            path.lineTo(x.coerceAtMost(r.right), r.top + 6f + sin(x * 0.11f + t * 3f) * 2.5f)
        }
        canvas.drawPath(path, paint)
        var bx = r.left + 30f
        while (bx < r.right - 10f) {
            EnvArt.bloom(canvas, paint, bx, r.top + 8f, 60f, 18f, EnvArt.glowTeal, 70)
            bx += 120f
        }
        // Rising bubbles
        fill(paint, lit, 150)
        var k = 0
        bx = r.left + 14f
        while (bx < r.right - 8f) {
            val phase = (t * 0.5f + k * 0.37f) % 1f
            val by = r.bottom - (r.height() - 8f) * phase
            if (by > r.top + 8f) canvas.drawCircle(bx + sin(t * 2f + k) * 3f, by, 2f + (k % 3), paint)
            bx += 46f; k++
        }
    }
}
