package com.devdgames.hollowknightbutmine.game.env

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.devdgames.hollowknightbutmine.game.world.Blade
import com.devdgames.hollowknightbutmine.game.world.FloraHash
import com.devdgames.hollowknightbutmine.game.world.FloraPlan
import com.devdgames.hollowknightbutmine.game.world.FloraProfile
import com.devdgames.hollowknightbutmine.game.world.Speck
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * One ~96 px stretch of vegetation. Built once at scene construction; drawing it is a handful
 * of `drawPath` calls with a small skew for wind, and allocates nothing.
 *
 * The three blade layers are separate paths (dark back, mid, light front) so a clump reads as
 * overlapping leaves with depth rather than a single flat colour.
 */
internal class FloraChunk(
    val bounds: RectF,
    val pivotX: Float,
    val pivotY: Float,
    val phase: Float,
    val speed: Float,
    val amp: Float
) {
    var cushions: Path? = null
    var roots: Path? = null
    var rootWidth = 2.4f
    var back: Path? = null
    var mid: Path? = null
    var front: Path? = null
    var pods: Path? = null
    var glow: Path? = null
    var warm: Path? = null

    var cDeep = 0
    var cMid = 0
    var cLight = 0
    var cCushion = 0
    var cRoot = 0
    var cPod = 0
    var cGlow = 0
    var cWarm = 0
}

internal object FloraArt {
    private const val CHUNK = 48f
    private const val TWO_PI = 6.2831855f
    private val tmp = RectF()

    private fun mix(a: Int, b: Int, k: Float): Int {
        val t = k.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
        )
    }

    /** All moss patches of one surface as a single fill, thickest in the middle, with drips. */
    fun mossPath(plan: FloraPlan, seed: Int, baseline: (Float) -> Float): Path? {
        if (plan.moss.isEmpty()) return null
        val path = Path()
        for (m in plan.moss) {
            val n = max(2, ((m.x1 - m.x0) / 6f).toInt())
            val step = (m.x1 - m.x0) / n
            path.moveTo(m.x0, baseline(m.x0) - 0.5f)
            for (i in 1..n) {
                val x = m.x0 + step * i
                path.lineTo(x, baseline(x) - 0.5f)
            }
            for (i in n downTo 0) {
                val x = m.x0 + step * i
                val u = i / n.toFloat()
                val prof = sin(Math.PI.toFloat() * u).pow(0.6f)
                val wobble = 0.85f + 0.3f * FloraHash.noise(seed, x * 0.09f)
                path.lineTo(x, baseline(x) + 0.8f + m.thick * prof * wobble)
            }
            path.close()
            for (d in m.drips) {
                val yb = baseline(d.x) + m.thick * 0.8f
                val half = d.w * 0.5f
                path.moveTo(d.x + half, yb - 1f)
                path.quadTo(d.x + half, yb + d.len, d.x, yb + d.len)
                path.quadTo(d.x - half, yb + d.len, d.x - half, yb - 1f)
                path.close()
            }
        }
        return path
    }

    private fun addBlade(path: Path, x: Float, y: Float, b: Blade) {
        val hw = b.w * 0.5f
        val tipX = x + b.lean
        val tipY = y - b.h
        val cx = x + b.bend
        val cy = y - b.h * 0.55f
        path.moveTo(x - hw, y + 0.6f)
        path.quadTo(cx - hw * 0.8f, cy, tipX, tipY)
        path.quadTo(cx + hw * 0.8f, cy, x + hw, y + 0.6f)
        path.close()
    }

    /**
     * Turns [plan] into drawable chunks. [baseline] gives the surface height at an x, so blades
     * follow the slightly wavy rim of the rock. Returns an empty list for an empty plan.
     */
    fun build(
        plan: FloraPlan, p: FloraProfile, pal: BiomePalette, seed: Int,
        x0: Float, x1: Float, baseline: (Float) -> Float
    ): List<FloraChunk> {
        if (plan.isEmpty || plan.blades.isEmpty() && plan.cushions.isEmpty() && plan.roots.isEmpty() && plan.specks.isEmpty()) {
            return emptyList()
        }
        val count = max(1, ceil((x1 - x0) / CHUNK).toInt())
        val chunks = arrayOfNulls<FloraChunk>(count)

        fun chunkAt(x: Float): FloraChunk {
            val ci = ((x - x0) / CHUNK).toInt().coerceIn(0, count - 1)
            var c = chunks[ci]
            if (c == null) {
                val cx0 = x0 + ci * CHUNK
                val cx1 = min(x1, cx0 + CHUNK)
                val midX = (cx0 + cx1) * 0.5f
                val by = baseline(midX)
                val top = by - (p.hMax * p.tallScale * 1.35f + 10f)
                val u = FloraHash.unit(seed, 9000 + ci)
                val made = FloraChunk(
                    RectF(cx0 - 18f, top, cx1 + 18f, by + 12f),
                    midX, by,
                    phase = u * TWO_PI,
                    speed = 0.9f + 0.6f * FloraHash.unit(seed, 9100 + ci),
                    amp = p.sway * (0.75f + 0.5f * FloraHash.unit(seed, 9200 + ci))
                )
                val tone = (FloraHash.unit(seed, 7000 + ci) - 0.5f) * 0.2f
                made.cDeep = mix(pal.rockDeep, pal.moss, 0.72f + tone)
                made.cMid = mix(pal.moss, pal.mossLit, 0.24f + tone)
                made.cLight = mix(pal.moss, pal.mossLit, 0.62f + tone)
                made.cCushion = mix(pal.moss, pal.mossLit, 0.16f + tone * 0.5f)
                made.cRoot = mix(pal.rockDeep, pal.moss, 0.5f)
                made.cPod = mix(pal.rockDeep, pal.moss, 0.35f)
                made.cGlow = pal.glow
                made.cWarm = EnvPalette.sporeWarm
                chunks[ci] = made
                c = made
            }
            return c
        }

        for (b in plan.blades) {
            val c = chunkAt(b.x)
            val path = when (b.layer) {
                0 -> c.back ?: Path().also { c.back = it }
                2 -> c.front ?: Path().also { c.front = it }
                else -> c.mid ?: Path().also { c.mid = it }
            }
            addBlade(path, b.x, baseline(b.x) - b.base, b)
        }
        for (cu in plan.cushions) {
            val c = chunkAt(cu.x)
            val path = c.cushions ?: Path().also { c.cushions = it }
            val y = baseline(cu.x)
            val half = cu.w * 0.5f
            path.moveTo(cu.x - half, y + 1f)
            path.quadTo(cu.x - half, y - cu.h, cu.x, y - cu.h)
            path.quadTo(cu.x + half, y - cu.h, cu.x + half, y + 1f)
            path.close()
        }
        val widthSum = FloatArray(count)
        val widthCnt = IntArray(count)
        for (r in plan.roots) {
            val c = chunkAt(r.x)
            val ci = ((r.x - x0) / CHUNK).toInt().coerceIn(0, count - 1)
            val path = c.roots ?: Path().also { c.roots = it }
            val y = baseline(r.x) + 1.5f
            val y2 = baseline(r.x + r.len) + 1.5f
            path.moveTo(r.x, y)
            path.quadTo(r.x + r.len * 0.5f, min(y, y2) - r.arch * 2f, r.x + r.len, y2)
            widthSum[ci] += r.width
            widthCnt[ci]++
        }
        for (i in 0 until count) {
            val c = chunks[i] ?: continue
            if (widthCnt[i] > 0) c.rootWidth = widthSum[i] / widthCnt[i]
        }
        for (s in plan.specks) {
            val c = chunkAt(s.x)
            val y = baseline(s.x) - s.y
            when (s.kind) {
                Speck.POD -> {
                    val path = c.pods ?: Path().also { c.pods = it }
                    tmp.set(s.x - s.rx, y - s.ry, s.x + s.rx, y + s.ry)
                    path.addOval(tmp, Path.Direction.CW)
                }
                Speck.WARM -> (c.warm ?: Path().also { c.warm = it }).addCircle(s.x, y, s.rx, Path.Direction.CW)
                else -> (c.glow ?: Path().also { c.glow = it }).addCircle(s.x, y, s.rx, Path.Direction.CW)
            }
        }
        val out = ArrayList<FloraChunk>(count)
        for (c in chunks) if (c != null) out.add(c)
        return out
    }

    fun draw(canvas: Canvas, paint: Paint, c: FloraChunk, t: Float) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        val cushions = c.cushions
        if (cushions != null) {
            paint.color = c.cCushion
            canvas.drawPath(cushions, paint)
        }
        val roots = c.roots
        if (roots != null) {
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = c.rootWidth
            paint.color = c.cRoot
            canvas.drawPath(roots, paint)
            paint.style = Paint.Style.FILL
            paint.strokeCap = Paint.Cap.BUTT
        }
        // Leaves sway together: a skew anchored on the surface line.
        val k = c.amp * (sin(t * c.speed + c.phase) + 0.35f * sin(t * c.speed * 2.3f + c.phase * 1.7f))
        canvas.save()
        canvas.translate(c.pivotX, c.pivotY)
        canvas.skew(k, 0f)
        canvas.translate(-c.pivotX, -c.pivotY)
        val back = c.back
        if (back != null) { paint.color = c.cDeep; canvas.drawPath(back, paint) }
        val mid = c.mid
        if (mid != null) { paint.color = c.cMid; canvas.drawPath(mid, paint) }
        val front = c.front
        if (front != null) { paint.color = c.cLight; canvas.drawPath(front, paint) }
        val pods = c.pods
        if (pods != null) { paint.color = c.cPod; canvas.drawPath(pods, paint) }
        val glow = c.glow
        if (glow != null) { paint.color = c.cGlow; canvas.drawPath(glow, paint) }
        val warm = c.warm
        if (warm != null) { paint.color = c.cWarm; canvas.drawPath(warm, paint) }
        canvas.restore()
    }

    /**
     * Same as [draw] but skips blade layers (back/mid/front) when [chunkKey] is present in
     * [cutKeys], giving the appearance of cut grass. Cushions, roots, pods and specks remain
     * visible so the ground surface still reads as vegetated.
     *
     * This is PURELY COSMETIC: no collision or damage code is affected. No per-frame
     * allocation — [cutKeys] is the live key-set from the game's GrassCutState.
     */
    fun drawWithCuts(canvas: Canvas, paint: Paint, c: FloraChunk, t: Float, chunkKey: Long, cutKeys: Set<Long>) {
        val bladesCut = chunkKey in cutKeys
        paint.shader = null
        paint.style = Paint.Style.FILL
        val cushions = c.cushions
        if (cushions != null) {
            paint.color = c.cCushion
            canvas.drawPath(cushions, paint)
        }
        val roots = c.roots
        if (roots != null) {
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = c.rootWidth
            paint.color = c.cRoot
            canvas.drawPath(roots, paint)
            paint.style = Paint.Style.FILL
            paint.strokeCap = Paint.Cap.BUTT
        }
        val k = c.amp * (sin(t * c.speed + c.phase) + 0.35f * sin(t * c.speed * 2.3f + c.phase * 1.7f))
        canvas.save()
        canvas.translate(c.pivotX, c.pivotY)
        canvas.skew(k, 0f)
        canvas.translate(-c.pivotX, -c.pivotY)
        if (!bladesCut) {
            val back = c.back
            if (back != null) { paint.color = c.cDeep; canvas.drawPath(back, paint) }
            val mid = c.mid
            if (mid != null) { paint.color = c.cMid; canvas.drawPath(mid, paint) }
            val front = c.front
            if (front != null) { paint.color = c.cLight; canvas.drawPath(front, paint) }
        }
        val pods = c.pods
        if (pods != null) { paint.color = c.cPod; canvas.drawPath(pods, paint) }
        val glow = c.glow
        if (glow != null) { paint.color = c.cGlow; canvas.drawPath(glow, paint) }
        val warm = c.warm
        if (warm != null) { paint.color = c.cWarm; canvas.drawPath(warm, paint) }
        canvas.restore()
    }
}
