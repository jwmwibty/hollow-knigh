package com.devdgames.hollowknightbutmine.game.story

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.sin

/**
 * The things lying about the kingdom that can be picked up, drawn.
 *
 * A relic hangs at knee height, turns slowly, and throws a thin pillar of light up to the
 * ceiling so it can be spotted from across a room — which matters, because the only way to
 * find one is to see it out of the corner of an eye.
 */
object RelicArt {

    private val scratch = Path()

    fun draw(canvas: Canvas, paint: Paint, relic: PlacedRelic, time: Float) {
        val x = relic.x
        val ground = relic.floorY
        val accent = relic.def.accent

        if (relic.taken) {
            // The last of the light, going up and away.
            val w = (relic.takeAnim / 1.1f).coerceIn(0f, 1f)
            if (w <= 0f) return
            paint.style = Paint.Style.FILL
            paint.color = Color.argb((150 * w).toInt(), Color.red(accent), Color.green(accent), Color.blue(accent))
            canvas.drawCircle(x, ground - 34f - (1f - w) * 40f, 5f * w + 1f, paint)
            return
        }

        val bob = sin(time * 2.2f + x * 0.01f) * 4f
        val cy = ground - 34f + bob
        val turn = sin(time * 1.3f)

        // A pillar of light, and the pool of it on the floor.
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(26, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawRect(x - 7f, cy - 150f, x + 7f, ground, paint)
        paint.color = Color.argb(52, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawOval(x - 16f, ground - 5f, x + 16f, ground + 4f, paint)
        paint.color = Color.argb(44, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawCircle(x, cy, 17f + sin(time * 3.4f) * 1.6f, paint)

        // The shard: a splinter of a lantern glass, turning on its own slow axis.
        val w = 6.5f * (0.45f + 0.55f * kotlin.math.abs(turn))
        paint.color = Color.argb(245, Color.red(accent), Color.green(accent), Color.blue(accent))
        scratch.reset()
        scratch.moveTo(x, cy - 13f)
        scratch.lineTo(x + w, cy - 2f)
        scratch.lineTo(x + w * 0.5f, cy + 12f)
        scratch.lineTo(x - w * 0.5f, cy + 12f)
        scratch.lineTo(x - w, cy - 2f)
        scratch.close()
        canvas.drawPath(scratch, paint)
        paint.color = Color.argb(235, 255, 250, 230)
        scratch.reset()
        scratch.moveTo(x, cy - 9f)
        scratch.lineTo(x + w * 0.42f, cy - 1f)
        scratch.lineTo(x, cy + 7f)
        scratch.lineTo(x - w * 0.42f, cy - 1f)
        scratch.close()
        canvas.drawPath(scratch, paint)

        // Two sparks orbiting it, so it reads as a thing of the story and not a pickup.
        for (i in 0..1) {
            val a = time * 1.9f + i * 3.14159f
            paint.color = Color.argb(150, 255, 244, 214)
            canvas.drawCircle(x + kotlin.math.cos(a) * 15f, cy + kotlin.math.sin(a) * 7f, 1.7f, paint)
        }
    }
}
