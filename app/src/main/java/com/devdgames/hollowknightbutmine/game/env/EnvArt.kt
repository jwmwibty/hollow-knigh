package com.devdgames.hollowknightbutmine.game.env

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** A pre-built path plus a generous bounding box used for culling. */
internal class EnvShape(val path: Path, val bounds: RectF)

/**
 * Shared drawing primitives for the environment.
 *
 * Everything here is deliberately allocation-light once construction is done:
 * paths and gradient shaders are built a single time and re-used every frame,
 * because this runs inside a software Canvas game loop at 60fps.
 */
internal object EnvArt {

    /** Cold bioluminescent bloom — spores, window light, mushrooms. */
    val glowTeal: Shader = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(
            Color.argb(150, 132, 234, 202),
            Color.argb(64, 70, 190, 168),
            Color.argb(0, 30, 110, 100)
        ),
        floatArrayOf(0f, 0.42f, 1f),
        Shader.TileMode.CLAMP
    )

    /** Warm lantern bloom. */
    val glowWarm: Shader = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(
            Color.argb(180, 255, 224, 158),
            Color.argb(74, 244, 186, 96),
            Color.argb(0, 200, 120, 40)
        ),
        floatArrayOf(0f, 0.40f, 1f),
        Shader.TileMode.CLAMP
    )

    /** Indigo soul-flame bloom used in the Ossuary. */
    val glowCold: Shader = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(
            Color.argb(160, 158, 196, 255),
            Color.argb(66, 92, 200, 255),
            Color.argb(0, 40, 70, 200)
        ),
        floatArrayOf(0f, 0.42f, 1f),
        Shader.TileMode.CLAMP
    )

    /** Pale gold window bloom used in the Sanctuary. */
    val glowGold: Shader = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(
            Color.argb(165, 255, 232, 176),
            Color.argb(66, 230, 196, 126),
            Color.argb(0, 170, 130, 60)
        ),
        floatArrayOf(0f, 0.42f, 1f),
        Shader.TileMode.CLAMP
    )

    /** Very dim haze used for the cavern floor and depth separation. */
    val glowVoid: Shader = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(
            Color.argb(70, 58, 158, 148),
            Color.argb(22, 34, 108, 108),
            Color.argb(0, 16, 52, 60)
        ),
        floatArrayOf(0f, 0.50f, 1f),
        Shader.TileMode.CLAMP
    )

    /** One reusable scratch path. The game loop is single-threaded, so this is safe. */
    val scratch = Path()
    val scratchRect = RectF()

    /** Deterministic 0..1 value from an integer seed (stable across frames). */
    fun hash01(seed: Int): Float {
        var h = seed * -1640531527 // 0x9E3779B9 as signed int
        h = h xor (h ushr 13)
        h *= -2048144777 // 0x85EBCA6B
        h = h xor (h ushr 16)
        return ((h and 0xFFFF).toFloat() / 65535f)
    }

    /** Linear blend of two ARGB colours. */
    fun lerpColor(a: Int, b: Int, t: Float): Int {
        val tt = t.coerceIn(0f, 1f)
        val ia = 1f - tt
        return Color.argb(
            (Color.alpha(a) * ia + Color.alpha(b) * tt).toInt().coerceIn(0, 255),
            (Color.red(a) * ia + Color.red(b) * tt).toInt().coerceIn(0, 255),
            (Color.green(a) * ia + Color.green(b) * tt).toInt().coerceIn(0, 255),
            (Color.blue(a) * ia + Color.blue(b) * tt).toInt().coerceIn(0, 255)
        )
    }

    /** Smoothstep used for biome cross-fades at the seams. */
    fun smooth01(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    /**
     * Draws one of the unit-radius glow shaders as an ellipse at (x, y).
     * The gradient is defined on a radius of 1 and scaled by the canvas matrix,
     * so a single cached shader serves every glow size on screen.
     */
    fun bloom(
        canvas: Canvas,
        paint: Paint,
        x: Float,
        y: Float,
        rx: Float,
        ry: Float,
        shader: Shader,
        alpha: Int
    ) {
        if (rx <= 0.5f || ry <= 0.5f || alpha <= 0) return
        paint.style = Paint.Style.FILL
        paint.shader = shader
        paint.alpha = alpha.coerceIn(0, 255)
        paint.xfermode = null
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(rx, ry)
        canvas.drawCircle(0f, 0f, 1f, paint)
        canvas.restore()
        paint.alpha = 255
        paint.shader = null
    }

    /**
     * Appends a tapered, slightly curved organic limb (root, vine, blade of grass)
     * to [path] as a filled contour. Start end is rounded-ish, end tip tapers to a point.
     */
    fun tapered(
        path: Path,
        x0: Float,
        y0: Float,
        cx: Float,
        cy: Float,
        x1: Float,
        y1: Float,
        w0: Float,
        w1: Float,
        samples: Int = 12
    ) {
        val n = samples.coerceAtLeast(2)
        val lx = FloatArray(n + 1)
        val ly = FloatArray(n + 1)
        val rx = FloatArray(n + 1)
        val ry = FloatArray(n + 1)

        for (i in 0..n) {
            val t = i / n.toFloat()
            val mt = 1f - t
            val px = mt * mt * x0 + 2f * mt * t * cx + t * t * x1
            val py = mt * mt * y0 + 2f * mt * t * cy + t * t * y1
            // Tangent of the quadratic -> normal for the thickness.
            var nx = -(2f * mt * (cy - y0) + 2f * t * (y1 - cy))
            var ny = 2f * mt * (cx - x0) + 2f * t * (x1 - cx)
            val len = sqrt(nx * nx + ny * ny)
            if (len > 0.0001f) {
                nx /= len
                ny /= len
            } else {
                nx = 1f
                ny = 0f
            }
            val half = (w0 + (w1 - w0) * t) * 0.5f
            lx[i] = px + nx * half
            ly[i] = py + ny * half
            rx[i] = px - nx * half
            ry[i] = py - ny * half
        }

        path.moveTo(lx[0], ly[0])
        for (i in 1..n) path.lineTo(lx[i], ly[i])
        if (w1 > 2.5f) {
            path.lineTo(x1, y1)
        }
        for (i in n downTo 0) path.lineTo(rx[i], ry[i])
        path.close()
    }

    // ---------------------------------------------------------- silhouette builders

    /**
     * Appends a jagged cavern ridge / stalagmite skyline to [path], standing on
     * [baseY]. Peaks vary pseudo-randomly between [minPeak] and [maxPeak]
     * (measured *upwards* from the base) so the skyline never reads as tiled.
     */
    fun ridge(
        path: Path,
        left: Float,
        right: Float,
        baseY: Float,
        minPeak: Float,
        maxPeak: Float,
        seed: Int,
        step: Float = 92f
    ) {
        val rnd = Random(seed)
        var x = left - step
        var peak = minPeak + rnd.nextFloat() * (maxPeak - minPeak)
        path.moveTo(left - step, baseY)
        while (x < right + step) {
            val shoulder = baseY - peak * (0.35f + rnd.nextFloat() * 0.3f)
            path.lineTo(x + step * 0.42f, shoulder)
            path.lineTo(x + step * 0.58f, baseY - peak)
            path.lineTo(x + step, baseY - peak * (0.25f + rnd.nextFloat() * 0.35f))
            peak = minPeak + rnd.nextFloat() * (maxPeak - minPeak)
            x += step * (0.75f + rnd.nextFloat() * 0.55f)
        }
        path.lineTo(right + step, baseY)
        path.close()
    }

    /**
     * Appends a hanging stalactite ceiling band to [path]: a slab along
     * [topY] with irregular teeth reaching down to [maxLen].
     */
    fun stalactiteCeiling(
        path: Path,
        left: Float,
        right: Float,
        topY: Float,
        minLen: Float,
        maxLen: Float,
        seed: Int,
        step: Float = 74f
    ) {
        val rnd = Random(seed)
        path.moveTo(left - step, topY)
        var x = left - step
        while (x < right + step) {
            val len = minLen + rnd.nextFloat() * (maxLen - minLen)
            val half = step * (0.16f + rnd.nextFloat() * 0.16f)
            val cx = x + step * 0.5f
            path.lineTo(cx - half, topY + len * 0.22f)
            path.lineTo(cx + (rnd.nextFloat() - 0.5f) * 6f, topY + len)
            path.lineTo(cx + half, topY + len * 0.22f)
            x += step * (0.7f + rnd.nextFloat() * 0.6f)
            path.lineTo(x, topY + 6f + rnd.nextFloat() * 10f)
        }
        path.lineTo(right + step, topY)
        path.close()
    }

    /**
     * Appends out-of-focus foreground framing to [path] across [spanWidth]
     * of room-local space: thick roots dropping from above, the odd looming
     * pillar edge. Drawn at >1 parallax so it sweeps past the camera.
     */
    fun buildForeground(path: Path, left: Float, spanWidth: Float, seed: Int) {
        val rnd = Random(seed)
        var x = left + 120f + rnd.nextFloat() * 260f
        val end = left + spanWidth - 60f
        var i = 0
        while (x < end) {
            if (i % 3 == 2) {
                // Looming pillar edge (top and bottom stubs, gap in the middle).
                val w = 34f + rnd.nextFloat() * 46f
                val topLen = 90f + rnd.nextFloat() * 150f
                val botLen = 70f + rnd.nextFloat() * 130f
                path.addRect(x, -320f, x + w, -320f + topLen, Path.Direction.CW)
                path.addRect(x - 6f, 780f - botLen, x + w + 6f, 780f, Path.Direction.CW)
            } else {
                // Heavy root sweeping down from the ceiling.
                val len = 150f + rnd.nextFloat() * 210f
                val sway = (rnd.nextFloat() - 0.5f) * 90f
                tapered(path, x, -320f, x + sway * 0.4f, -320f + len * 0.55f, x + sway, -320f + len, 30f + rnd.nextFloat() * 22f, 4f, 8)
            }
            x += 620f + rnd.nextFloat() * 560f
            i++
        }
    }

    // ---------------------------------------------------------- animated atmospherics

    /**
     * Drifting ambient motes (spores / soul-dust) inside a room-local area.
     * Only motes inside [viewL]..[viewR] are actually drawn.
     */
    fun drawMotes(
        canvas: Canvas,
        paint: Paint,
        t: Float,
        areaW: Float,
        areaH: Float,
        count: Int,
        seed: Int,
        viewL: Float,
        viewR: Float,
        warmEvery: Int = 3,
        cold: Boolean = false
    ) {
        if (areaW <= 10f || areaH <= 10f) return
        paint.style = Paint.Style.FILL
        paint.shader = null
        for (i in 0 until count) {
            val h1 = hash01(seed + i * 131)
            val h2 = hash01(seed * 3 + i * 57)
            val speed = 10f + h2 * 22f
            var mx = (h1 * areaW + sin(t * (0.5f + h2 * 0.6f) + i * 1.7f) * 26f) % areaW
            if (mx < 0f) mx += areaW
            val drawX = mx - 100f // area is authored 0..areaW, rooms live at x >= -100
            if (drawX < viewL - 30f || drawX > viewR + 30f) continue
            val my = (h2 * areaH + t * speed + sin(t * 0.9f + i) * 10f) % areaH
            paint.color = when {
                cold && i % 2 == 0 -> EnvPalette.sporeCold
                i % warmEvery == 0 -> EnvPalette.sporeWarm
                else -> EnvPalette.sporeTeal
            }
            paint.alpha = (95 + sin(t * 2.1f + i * 1.3f) * 65).toInt().coerceIn(12, 230)
            canvas.drawCircle(drawX, my - 60f, 1.5f + h1 * 2.2f, paint)
        }
        paint.alpha = 255
    }

    /**
     * Slow horizontal fog bank in *screen* space: a row of wide, dim blooms
     * sliding gently sideways. Cheap depth separator between backdrop and world.
     */
    fun drawFogBank(
        canvas: Canvas,
        paint: Paint,
        t: Float,
        viewW: Float,
        centreY: Float,
        thickness: Float,
        driftSpeed: Float,
        alpha: Int,
        shader: Shader = glowVoid
    ) {
        if (viewW <= 10f) return
        val spacing = 380f
        val span = viewW + spacing * 2f
        var base = (t * driftSpeed) % span
        if (base < 0f) base += span
        var i = -1
        var x = base - span
        while (x < viewW + spacing) {
            val wobble = sin(t * 0.35f + i * 1.9f) * thickness * 0.22f
            bloom(canvas, paint, x + spacing * 0.5f, centreY + wobble, spacing * 0.72f, thickness, shader, alpha)
            x += spacing
            i++
        }
    }

    /**
     * Animated waterfall: layered translucent falling streaks + foam bloom.
     * Drawn in world space, so [x] is the centre of the fall.
     */
    fun drawWaterfall(
        canvas: Canvas,
        paint: Paint,
        x: Float,
        top: Float,
        bottom: Float,
        width: Float,
        t: Float
    ) {
        val h = bottom - top
        if (h <= 10f) return
        // Body wash.
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = EnvPalette.waterMid
        paint.alpha = 110
        canvas.drawRect(x - width * 0.5f, top, x + width * 0.5f, bottom, paint)
        // Falling streaks (looped by time, offset per column).
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        val cols = 5
        for (c in 0 until cols) {
            val lane = -0.38f + c * (0.76f / (cols - 1))
            val lx = x + lane * width + sin(t * 1.7f + c * 2.1f) * 2.5f
            val speed = 150f + (c % 3) * 55f
            paint.strokeWidth = if (c % 2 == 0) 3.2f else 2f
            paint.color = if (c % 2 == 0) EnvPalette.waterTop else EnvPalette.waterFoam
            paint.alpha = if (c % 2 == 0) 120 else 90
            var sy = top + ((t * speed + c * 47f) % (h + 48f)) - 48f
            while (sy < bottom) {
                val y0 = if (sy < top) top else sy
                val y1 = (sy + 30f).coerceAtMost(bottom)
                if (y1 > y0) canvas.drawLine(lx, y0, lx, y1, paint)
                sy += 78f
            }
        }
        paint.strokeCap = Paint.Cap.BUTT
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        // Foam where the fall lands.
        bloom(canvas, paint, x, bottom, width * 0.95f, 15f + sin(t * 3.2f) * 3f, glowTeal, 95)
        bloom(canvas, paint, x, (top + bottom) * 0.5f, width * 0.62f, h * 0.42f, glowTeal, 36)
    }

    /** Small cluster of glowing crystal shards rooted at (x, y) base. */
    fun drawCrystalCluster(
        canvas: Canvas,
        paint: Paint,
        x: Float,
        y: Float,
        size: Float,
        lit: Int,
        dark: Int,
        glow: Shader
    ) {
        scratch.reset()
        // Centre shard.
        scratch.moveTo(x - size * 0.22f, y)
        scratch.lineTo(x, y - size)
        scratch.lineTo(x + size * 0.22f, y)
        scratch.close()
        // Left shard.
        scratch.moveTo(x - size * 0.52f, y)
        scratch.lineTo(x - size * 0.34f, y - size * 0.58f)
        scratch.lineTo(x - size * 0.08f, y)
        scratch.close()
        // Right shard.
        scratch.moveTo(x + size * 0.1f, y)
        scratch.lineTo(x + size * 0.38f, y - size * 0.44f)
        scratch.lineTo(x + size * 0.54f, y)
        scratch.close()
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.alpha = 235
        paint.color = dark
        canvas.drawPath(scratch, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.4f
        paint.color = lit
        paint.alpha = 190
        canvas.drawLine(x, y - size, x, y - size * 0.18f, paint)
        canvas.drawLine(x - size * 0.32f, y - size * 0.52f, x - size * 0.26f, y - size * 0.1f, paint)
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        bloom(canvas, paint, x, y - size * 0.4f, size * 0.95f, size * 0.8f, glow, 52)
    }

    /** One squat glow-cap mushroom rooted at (x, y) base. */
    fun drawGlowMushroom(canvas: Canvas, paint: Paint, x: Float, y: Float, scale: Float) {
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.alpha = 255
        paint.color = EnvPalette.mushStem
        canvas.drawRoundRect(x - 2.2f * scale, y - 8f * scale, x + 2.2f * scale, y, 2f * scale, 2f * scale, paint)
        paint.color = EnvPalette.mushCap
        canvas.drawOval(x - 7f * scale, y - 13f * scale, x + 7f * scale, y - 5f * scale, paint)
        paint.color = EnvPalette.sporeTeal
        paint.alpha = 170
        canvas.drawCircle(x - 2.5f * scale, y - 10f * scale, 1.3f * scale, paint)
        canvas.drawCircle(x + 2.6f * scale, y - 9f * scale, 1.1f * scale, paint)
        paint.alpha = 255
    }

    /**
     * Draws a small cluster of wind-swayed grass blades rooted at (x, y).
     * Safe to call each frame: uses Canvas primitives without allocating paths.
     */
    fun drawGrassBlade(
        canvas: Canvas,
        paint: Paint,
        x: Float,
        baseY: Float,
        height: Float,
        sway: Float
    ) {
        val oldColor = paint.color
        val oldStyle = paint.style
        val oldStrokeWidth = paint.strokeWidth
        val oldAlpha = paint.alpha
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 2.2f
        paint.alpha = 220
        paint.color = Color.rgb(35, 112, 79)
        canvas.drawLine(x, baseY, x - 3f + sway * 0.45f, baseY - height * 0.65f, paint)
        paint.color = Color.rgb(63, 153, 96)
        canvas.drawLine(x, baseY, x + sway, baseY - height, paint)
        paint.color = Color.rgb(24, 83, 65)
        canvas.drawLine(x + 1f, baseY, x + 5f + sway * 0.7f, baseY - height * 0.78f, paint)
        paint.color = oldColor
        paint.style = oldStyle
        paint.strokeWidth = oldStrokeWidth
        paint.alpha = oldAlpha
        paint.strokeCap = Paint.Cap.BUTT
        paint.shader = null
    }

    /** Draws three curved, wind-swayed grass blades on a ledge. */
    fun drawGrassPatch(canvas: Canvas, paint: Paint, x: Float, y: Float, wind: Float) {
        drawGrassBlade(canvas, paint, x - 4f, y, 15f, wind * 0.55f - 2f)
        drawGrassBlade(canvas, paint, x, y, 23f, wind)
        drawGrassBlade(canvas, paint, x + 5f, y, 18f, wind * 0.75f + 2f)
    }

    /** Distance helper used by culling checks. */
    fun near(x: Float, l: Float, r: Float, pad: Float = 260f): Boolean =
        x > l - pad && x < r + pad

    /** Absolute value re-export to keep room files import-light. */
    fun absf(v: Float): Float = abs(v)
}
