package com.devdgames.hollowknightbutmine.game.env

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.sqrt
import kotlin.math.cos
import kotlin.math.sin

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

    /** Draws three slender, wind-swayed blades on a terrain edge. */
    fun drawGrassPatch(canvas: Canvas, paint: Paint, x: Float, y: Float, wind: Float) {
        val bladeWidth = 2f
        val bladeHeight = 18f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = bladeWidth
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.rgb(31, 71, 30)
        repeat(3) { i ->
            val offset = i * 4f - 4f
            val angle = (wind + i * 0.5f) * 0.15f
            val tipX = x + offset + cos(angle) * 6f
            val tipY = y - bladeHeight + sin(angle) * 4f
            canvas.drawLine(x + offset, y, tipX, tipY, paint)
        }
        paint.style = Paint.Style.FILL
        paint.strokeCap = Paint.Cap.BUTT
    }
}
