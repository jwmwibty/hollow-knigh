package com.devdgames.hollowknightbutmine.game.env

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader

/**
 * A tapered volumetric light shaft, built once.
 *
 * The polygon and its gradient are static in world space, so nothing here is
 * allocated at draw time. It is composited additively, which lets it climb over
 * the actor layer without hiding anything.
 */
class LightShaft(
    x: Float,
    top: Float,
    bottom: Float,
    halfTop: Float,
    halfBot: Float,
    lean: Float,
    color: Int,
    alpha: Int
) {
    private val path = Path()
    private val shader: Shader

    init {
        path.moveTo(x - halfTop, top)
        path.lineTo(x + halfTop, top)
        path.lineTo(x + lean + halfBot, bottom)
        path.lineTo(x + lean - halfBot, bottom)
        path.close()

        val r = android.graphics.Color.red(color)
        val g = android.graphics.Color.green(color)
        val b = android.graphics.Color.blue(color)
        shader = LinearGradient(
            x, top, x + lean, bottom,
            intArrayOf(
                android.graphics.Color.argb((alpha * 0.95f).toInt().coerceIn(0, 255), r, g, b),
                android.graphics.Color.argb((alpha * 0.45f).toInt().coerceIn(0, 255), r, g, b),
                android.graphics.Color.argb(0, r, g, b)
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    fun draw(canvas: Canvas, paint: Paint) {
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        paint.shader = shader
        paint.xfermode = ADD
        canvas.drawPath(path, paint)
        paint.xfermode = null
        paint.shader = null
    }

    private companion object {
        val ADD = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }
}
