package com.devdgames.hollowknightbutmine.game.env

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * One hand-made stone ledge.
 *
 * The collision rect is plain (the engine is rect based), but the drawing is
 * organic: a gently wavy crown so the surface never reads as a rectangle, a
 * moss band, grass blades, roots dripping from the underside and a few
 * bioluminescent specks. All of the geometry is generated once at construction
 * with a seeded RNG, so the room looks identical every run and nothing is
 * allocated while the game is running.
 */
class Slab(
    val rect: RectF,
    seed: Int,
    private val mossy: Boolean = true,
    private val bodyColor: Int = EnvPalette.rockBody,
    private val bottomColor: Int = EnvPalette.rockShadow,
    private val bandColor: Int = EnvPalette.mossDeep,
    private val tuftColor: Int = EnvPalette.mossLit,
    private val rimColor: Int = EnvPalette.rockRim
) {
    private val rnd = Random(seed)
    private val phA = rnd.nextFloat() * 6.283f
    private val phB = rnd.nextFloat() * 6.283f

    private val body = Path()
    private val crown = Path()
    private val band = Path()
    private val tufts = Path()
    private val drips = Path()

    /** x, y, radius, colour index (0 = teal, 1 = warm, 2 = dim) */
    private val specks = ArrayList<Float>()

    private val shade: LinearGradient = LinearGradient(
        0f, rect.top, 0f, max(rect.top + 10f, rect.bottom),
        bodyColor, bottomColor, Shader.TileMode.CLAMP
    )

    private val xs: FloatArray
    private val jag: FloatArray

    init {
        val n = ((rect.width() / 15f).toInt() + 3).coerceIn(5, 96)
        xs = FloatArray(n + 1) { i -> rect.left + rect.width() * i / n }
        jag = FloatArray(n + 1) { rnd.nextFloat() * 5f - 1f }
        build()
    }

    /** Height of the drawn surface at [x]; sits on (or a hair above) rect.top. */
    private fun crownY(x: Float): Float {
        val u = (x - rect.left) / max(1f, rect.width())
        return rect.top + sin(u * 5.3f + phA) * 1.6f + sin(u * 14.1f + phB) * 0.8f
    }

    private fun bottomY(i: Int): Float = rect.bottom + jag[i]

    private fun build() {
        val n = xs.size - 1

        // --- silhouette -------------------------------------------------
        body.moveTo(xs[0], crownY(xs[0]))
        for (i in 1..n) body.lineTo(xs[i], crownY(xs[i]))
        for (i in n downTo 0) body.lineTo(xs[i], bottomY(i))
        body.close()

        // --- bright rim along the walkable edge -------------------------
        crown.moveTo(xs[0], crownY(xs[0]))
        for (i in 1..n) crown.lineTo(xs[i], crownY(xs[i]))

        if (!mossy) return

        // --- moss band hugging the top ----------------------------------
        band.moveTo(xs[0], crownY(xs[0]) - 0.5f)
        for (i in 1..n) band.lineTo(xs[i], crownY(xs[i]) - 0.5f)
        for (i in n downTo 0) {
            val thick = 4.5f + sin(xs[i] * 0.09f + phB) * 2.2f
            band.lineTo(xs[i], crownY(xs[i]) + thick)
        }
        band.close()

        // --- grass blades -----------------------------------------------
        var x = rect.left + 2f
        while (x < rect.right - 2f) {
            val y = crownY(x)
            val h = 4f + rnd.nextFloat() * 9f
            val lean = (rnd.nextFloat() - 0.5f) * 8f
            val w = 1.5f + rnd.nextFloat() * 1.5f
            EnvArt.tapered(tufts, x, y + 1f, x + lean * 0.4f, y - h * 0.55f, x + lean, y - h, w, 0.45f, 5)
            x += 5f + rnd.nextFloat() * 10f
        }

        // --- roots hanging under the ledge ------------------------------
        x = rect.left + 5f
        while (x < rect.right - 5f) {
            if (rnd.nextFloat() < 0.42f) {
                val len = 4f + rnd.nextFloat() * 17f
                EnvArt.tapered(
                    drips, x, rect.bottom - 1f,
                    x + (rnd.nextFloat() - 0.5f) * 4f, rect.bottom + len * 0.55f,
                    x + (rnd.nextFloat() - 0.5f) * 6f, rect.bottom + len,
                    2.6f, 0.5f, 4
                )
            }
            x += 13f + rnd.nextFloat() * 28f
        }

        // --- glowing specks in the moss ---------------------------------
        x = rect.left + 4f
        while (x < rect.right - 4f) {
            if (rnd.nextFloat() < 0.34f) {
                val u = (x - rect.left) / max(1f, rect.width())
                specks.add(x)
                specks.add(crownY(x) + 1.5f + sin(u * 20f) * 1.5f)
                specks.add(1.1f + rnd.nextFloat() * 1.3f)
                specks.add(if (rnd.nextFloat() < 0.25f) 1f else 0f)
            }
            x += 16f + rnd.nextFloat() * 40f
        }
    }

    fun draw(canvas: Canvas, paint: Paint) {
        paint.shader = shade
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        canvas.drawPath(body, paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.4f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = rimColor
        canvas.drawPath(crown, paint)
        paint.strokeCap = Paint.Cap.BUTT

        if (!mossy) return

        paint.style = Paint.Style.FILL
        paint.color = bandColor
        canvas.drawPath(band, paint)

        paint.color = tuftColor
        canvas.drawPath(tufts, paint)

        paint.color = bandColor
        canvas.drawPath(drips, paint)

        var i = 0
        while (i < specks.size) {
            val sx = specks[i]
            val sy = specks[i + 1]
            val sr = specks[i + 2]
            paint.color = if (specks[i + 3] > 0.5f) EnvPalette.sporeWarm else EnvPalette.sporeTeal
            canvas.drawCircle(sx, sy, sr, paint)
            i += 4
        }
    }

    /** Cheap cull so off-screen ledges cost nothing. */
    fun isVisible(camX: Float, camY: Float, viewW: Float, viewH: Float): Boolean =
        rect.right > camX - 40f && rect.left < camX + viewW + 40f &&
            rect.bottom > camY - 60f && rect.top < camY + viewH + 60f
}
