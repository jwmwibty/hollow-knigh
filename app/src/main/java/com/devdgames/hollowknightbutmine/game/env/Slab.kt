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

/** How a slab is dressed. Collision is always the plain rect. */
enum class SlabStyle {
    /** Raw cavern rock: sagging underside with stone teeth, cracks, strata. */
    ROCK,

    /** Fossil bone: segmented bands, pitted holes, rounded knuckle ends. */
    BONE,

    /** Masonry: block joints, corbelled underside, chiseled top course. */
    CUT_STONE
}

/**
 * One hand-made stone ledge.
 *
 * The collision rect is plain (the engine is rect based), but the drawing is
 * organic: a gently wavy crown so the surface never reads as a rectangle, a
 * moss band, grass blades, roots dripping from the underside and a few
 * bioluminescent specks. Floating ledges get a tapered, stalactite-toothed
 * underside instead of a flat bottom; thick ground blocks get strata lines,
 * cracks and (for masonry) block joints so a 1,600px floor still reads as
 * stone rather than as a filled rectangle.
 *
 * All geometry is generated once at construction with a seeded RNG, so the
 * room looks identical every run and nothing is allocated while running.
 */
class Slab(
    val rect: RectF,
    seed: Int,
    val mossy: Boolean = true,
    private val bodyColor: Int = EnvPalette.rockBody,
    private val bottomColor: Int = EnvPalette.rockShadow,
    private val bandColor: Int = EnvPalette.mossDeep,
    private val tuftColor: Int = EnvPalette.mossLit,
    private val rimColor: Int = EnvPalette.rockRim,
    val style: SlabStyle = SlabStyle.ROCK
) {
    private val rnd = Random(seed)
    private val phA = rnd.nextFloat() * 6.283f
    private val phB = rnd.nextFloat() * 6.283f

    private val body = Path()
    private val crown = Path()
    private val underGlow = Path()
    private val band = Path()
    private val tufts = Path()
    private val drips = Path()
    private val cracks = Path()
    private val strata = Path()
    private val joints = Path()
    private val pits = Path()

    /** x, y, radius, colour index (0 = teal, 1 = warm, 2 = cold) */
    private val specks = ArrayList<Float>()

    /** x, y(base on crown), scale */
    private val mushrooms = ArrayList<Float>()

    /** x, y, rx, ry, shade(0 dark / 1 light) — pebbles set into the face */
    private val pebbles = ArrayList<Float>()

    /** Extra visual depth below rect.bottom (teeth / drips). Used for culling. */
    val visualDrop: Float

    private val shade: LinearGradient = LinearGradient(
        0f, rect.top, 0f, max(rect.top + 10f, rect.bottom),
        bodyColor, bottomColor, Shader.TileMode.CLAMP
    )

    private val xs: FloatArray
    private val jag: FloatArray
    private val toothC: FloatArray
    private val toothW: FloatArray
    private val toothD: FloatArray
    private val thin: Boolean = rect.height() < 48f

    init {
        val n = ((rect.width() / 15f).toInt() + 3).coerceIn(5, 96)
        xs = FloatArray(n + 1) { i -> rect.left + rect.width() * i / n }
        jag = FloatArray(n + 1) { rnd.nextFloat() * 5f - 1f }

        // Stalactite teeth hanging from floating rock/bone ledges.
        val teeth = if (thin && style != SlabStyle.CUT_STONE) {
            (rect.width() / 74f).toInt().coerceIn(1, 7)
        } else 0
        toothC = FloatArray(teeth)
        toothW = FloatArray(teeth)
        toothD = FloatArray(teeth)
        var drop = 14f
        for (i in 0 until teeth) {
            toothC[i] = rect.left + rect.width() * (0.14f + 0.72f * ((i + rnd.nextFloat() * 0.7f) / max(1f, teeth.toFloat())))
            toothW[i] = 7f + rnd.nextFloat() * 9f
            toothD[i] = 13f + rnd.nextFloat() * 26f
            if (toothD[i] + 6f > drop) drop = toothD[i] + 6f
        }
        visualDrop = drop + if (mossy) 22f else 8f
        build()
    }

    /** Height of the drawn surface at [x]; sits on (or a hair above) rect.top. */
    private fun crownY(x: Float): Float {
        val u = (x - rect.left) / max(1f, rect.width())
        val wobble = if (style == SlabStyle.CUT_STONE) 0.45f else 1f
        return rect.top + (sin(u * 5.3f + phA) * 1.6f + sin(u * 14.1f + phB) * 0.8f) * wobble
    }

    /** Drawn underside at sample [i] / position [x]. */
    private fun bottomProfile(x: Float, i: Int): Float {
        if (!thin) return rect.bottom + jag[i.coerceIn(0, jag.size - 1)]
        val u = ((x - rect.left) / max(1f, rect.width())).coerceIn(0f, 1f)
        return when (style) {
            SlabStyle.CUT_STONE -> {
                // Corbelled masonry: shallow steps near the ends, flat soffit.
                val edge = 0.12f
                val step = when {
                    u < edge * 0.5f || u > 1f - edge * 0.5f -> -5f
                    u < edge || u > 1f - edge -> -2f
                    else -> 2f
                }
                rect.bottom + step + sin(u * 22f + phA) * 0.7f
            }
            else -> {
                // Rock/bone: sagging belly + triangular teeth.
                var y = rect.bottom + sin(u * 3.14159f) * (5f + rect.width() * 0.012f).coerceAtMost(15f)
                for (tID in toothC.indices) {
                    val d = x - toothC[tID]
                    val hw = toothW[tID]
                    if (d > -hw && d < hw) {
                        y += toothD[tID] * (1f - (if (d < 0) -d else d) / hw)
                    }
                }
                y
            }
        }
    }

    private fun build() {
        val n = xs.size - 1

        // --- silhouette -------------------------------------------------
        body.moveTo(xs[0], crownY(xs[0]))
        for (i in 1..n) body.lineTo(xs[i], crownY(xs[i]))
        for (i in n downTo 0) {
            body.lineTo(xs[i], bottomProfile(xs[i], i))
        }
        body.close()

        // --- bright rim + soft under-rim along the walkable edge --------
        crown.moveTo(xs[0], crownY(xs[0]))
        underGlow.moveTo(xs[0], crownY(xs[0]) + 3.2f)
        for (i in 1..n) {
            crown.lineTo(xs[i], crownY(xs[i]))
            underGlow.lineTo(xs[i], crownY(xs[i]) + 3.2f)
        }

        buildFaceDetail()
        if (mossy) buildMoss()
    }

    /** Cracks, strata, joints, pits and pebbles that keep big faces alive. */
    private fun buildFaceDetail() {
        val w = rect.width()
        val h = rect.height()
        val faceBottom = if (thin) rect.bottom - 3f else rect.bottom - 6f

        // --- strata: faint wavy sediment lines on tall faces ------------
        if (h >= 40f && style != SlabStyle.BONE) {
            val lines = ((h - 26f) / 30f).toInt().coerceIn(1, 5)
            for (l in 0 until lines) {
                val ly = rect.top + 22f + l * 30f + rnd.nextFloat() * 6f
                if (ly > faceBottom) break
                var x = rect.left + 8f + rnd.nextFloat() * 30f
                strata.moveTo(x, ly)
                val end = rect.right - 8f - rnd.nextFloat() * 40f
                while (x < end) {
                    x += 26f
                    strata.lineTo(x, ly + sin(x * 0.045f + phA + l) * 2.4f)
                }
            }
        }

        // --- masonry joints / bone segment bands -------------------------
        when (style) {
            SlabStyle.CUT_STONE -> {
                var x = rect.left + 30f + rnd.nextFloat() * 26f
                while (x < rect.right - 18f) {
                    joints.moveTo(x + sin(x * 0.2f) * 1.2f, crownY(x) + 5f)
                    joints.lineTo(x + (rnd.nextFloat() - 0.5f) * 3f, if (thin) rect.bottom - 4f else rect.top + 54f)
                    x += 52f + rnd.nextFloat() * 34f
                }
                if (!thin && h > 90f) {
                    // Second course for tall walls, offset half a block.
                    var x2 = rect.left + 58f + rnd.nextFloat() * 20f
                    while (x2 < rect.right - 18f) {
                        joints.moveTo(x2, rect.top + 56f)
                        joints.lineTo(x2 + (rnd.nextFloat() - 0.5f) * 3f, (rect.top + 108f).coerceAtMost(faceBottom))
                        x2 += 52f + rnd.nextFloat() * 34f
                    }
                }
            }
            SlabStyle.BONE -> {
                var x = rect.left + 22f + rnd.nextFloat() * 14f
                while (x < rect.right - 14f) {
                    val top = crownY(x) + 4f
                    val bot = if (thin) bottomProfile(x, 0) - 4f else rect.top + 64f
                    joints.moveTo(x - 3f, top)
                    joints.quadTo(x + 4f, (top + bot) * 0.5f, x - 3f, bot)
                    // Marrow pits between segment bands.
                    if (rnd.nextFloat() < 0.65f) {
                        val px = x + 10f + rnd.nextFloat() * 12f
                        val py = rect.top + 10f + rnd.nextFloat() * (if (thin) 12f else 40f)
                        val pr = 2f + rnd.nextFloat() * 3.4f
                        pits.addCircle(px, py.coerceAtMost(faceBottom - 3f), pr, Path.Direction.CW)
                    }
                    x += 40f + rnd.nextFloat() * 22f
                }
            }
            SlabStyle.ROCK -> Unit
        }

        // --- cracks wandering down from the crown ------------------------
        val crackCount = (w / 260f).toInt().coerceIn(1, 5)
        for (c in 0 until crackCount) {
            var cx = rect.left + 26f + rnd.nextFloat() * max(10f, w - 52f)
            var cy = crownY(cx) + 4f
            cracks.moveTo(cx, cy)
            val len = (16f + rnd.nextFloat() * 44f).coerceAtMost(max(10f, h - 8f))
            val steps = 4
            for (s in 0 until steps) {
                cx += (rnd.nextFloat() - 0.5f) * 12f
                cy += len / steps
                cracks.lineTo(cx, cy)
            }
        }

        // --- pebbles set into the face -------------------------------------
        var px = rect.left + 14f
        while (px < rect.right - 14f) {
            if (rnd.nextFloat() < 0.5f) {
                pebbles.add(px)
                pebbles.add((rect.top + 11f + rnd.nextFloat() * max(6f, (if (thin) h - 16f else 62f))).coerceAtMost(faceBottom - 3f))
                pebbles.add(3f + rnd.nextFloat() * 5f)
                pebbles.add(2f + rnd.nextFloat() * 3.4f)
                pebbles.add(if (rnd.nextFloat() < 0.4f) 1f else 0f)
            }
            px += 34f + rnd.nextFloat() * 58f
        }
    }

    private fun buildMoss() {
        val n = xs.size - 1

        // --- moss band hugging the top ----------------------------------
        band.moveTo(xs[0], crownY(xs[0]) - 0.5f)
        for (i in 1..n) band.lineTo(xs[i], crownY(xs[i]) - 0.5f)
        for (i in n downTo 0) {
            val thick = 4.5f + sin(xs[i] * 0.09f + phB) * 2.2f + if (i % 7 == 3) 2.4f else 0f
            band.lineTo(xs[i], crownY(xs[i]) + thick)
        }
        band.close()

        // --- grass blades ---------------------------------------------------
        var x = rect.left + 2f
        while (x < rect.right - 2f) {
            val y = crownY(x)
            val h = 4f + rnd.nextFloat() * 9f
            val lean = (rnd.nextFloat() - 0.5f) * 8f
            val w = 1.5f + rnd.nextFloat() * 1.5f
            EnvArt.tapered(tufts, x, y + 1f, x + lean * 0.4f, y - h * 0.55f, x + lean, y - h, w, 0.45f, 5)
            x += 5f + rnd.nextFloat() * 10f
        }

        // --- roots hanging under the ledge ----------------------------------
        x = rect.left + 5f
        while (x < rect.right - 5f) {
            if (rnd.nextFloat() < 0.42f) {
                val baseY = bottomProfile(x, 0) - 1f
                val len = 4f + rnd.nextFloat() * 16f
                EnvArt.tapered(
                    drips, x, baseY,
                    x + (rnd.nextFloat() - 0.5f) * 4f, baseY + len * 0.55f,
                    x + (rnd.nextFloat() - 0.5f) * 6f, baseY + len,
                    2.6f, 0.5f, 4
                )
            }
            x += 13f + rnd.nextFloat() * 28f
        }

        // --- glowing specks + the odd glow mushroom in the moss -------------
        x = rect.left + 4f
        while (x < rect.right - 4f) {
            val r = rnd.nextFloat()
            if (r < 0.30f) {
                val u = (x - rect.left) / max(1f, rect.width())
                specks.add(x)
                specks.add(crownY(x) + 1.5f + sin(u * 20f) * 1.5f)
                specks.add(1.1f + rnd.nextFloat() * 1.3f)
                specks.add(if (rnd.nextFloat() < 0.25f) 1f else 0f)
            } else if (r < 0.36f && rect.width() > 120f) {
                mushrooms.add(x)
                mushrooms.add(crownY(x) + 1f)
                mushrooms.add(0.6f + rnd.nextFloat() * 0.7f)
            }
            x += 16f + rnd.nextFloat() * 40f
        }
    }

    fun draw(canvas: Canvas, paint: Paint) = draw(canvas, paint, 0f)

    fun draw(canvas: Canvas, paint: Paint, t: Float) {
        // Body with top-lit gradient.
        paint.shader = shade
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        canvas.drawPath(body, paint)
        paint.shader = null

        // Face detail: strata & joints (dim), cracks (darker), pits, pebbles.
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 1.6f
        paint.color = bottomColor
        paint.alpha = 150
        canvas.drawPath(strata, paint)
        paint.alpha = 185
        canvas.drawPath(joints, paint)
        paint.strokeWidth = 1.3f
        paint.color = Color.rgb(3, 8, 10)
        paint.alpha = 200
        canvas.drawPath(cracks, paint)
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        paint.color = bottomColor
        canvas.drawPath(pits, paint)

        var p = 0
        while (p < pebbles.size) {
            paint.color = if (pebbles[p + 4] > 0.5f) rimColor else bottomColor
            paint.alpha = if (pebbles[p + 4] > 0.5f) 120 else 165
            val px = pebbles[p]
            val py = pebbles[p + 1]
            val prx = pebbles[p + 2]
            val pry = pebbles[p + 3]
            canvas.drawOval(px - prx, py - pry, px + prx, py + pry, paint)
            p += 5
        }
        paint.alpha = 255

        // Walkable edge: soft bioluminescent under-rim, then the crisp rim.
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 4.6f
        paint.color = rimColor
        paint.alpha = 52
        canvas.drawPath(underGlow, paint)
        paint.strokeWidth = 2.4f
        paint.alpha = 255
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
            paint.alpha = (165 + sin(t * 2.2f + sx * 0.07f) * 75).toInt().coerceIn(30, 255)
            canvas.drawCircle(sx, sy, sr, paint)
            i += 4
        }
        paint.alpha = 255

        var m = 0
        while (m < mushrooms.size) {
            EnvArt.drawGlowMushroom(canvas, paint, mushrooms[m], mushrooms[m + 1], mushrooms[m + 2])
            m += 3
        }
        paint.alpha = 255
    }

    /** Cheap cull so off-screen ledges cost nothing. [view] is room-local. */
    fun isVisible(view: RectF): Boolean =
        rect.right > view.left - 30f && rect.left < view.right + 30f &&
            rect.bottom + visualDrop > view.top - 40f && rect.top - 26f < view.bottom + 40f

    /** Legacy cull signature kept for any external callers. */
    fun isVisible(camX: Float, camY: Float, viewW: Float, viewH: Float): Boolean =
        rect.right > camX - 40f && rect.left < camX + viewW + 40f &&
            rect.bottom > camY - 60f && rect.top < camY + viewH + 60f
}
