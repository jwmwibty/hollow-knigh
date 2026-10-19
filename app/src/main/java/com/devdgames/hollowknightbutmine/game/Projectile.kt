package com.devdgames.hollowknightbutmine.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/**
 * Projectile fired by Spitter enemies or hazards.
 */
class Projectile(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val radius: Float = 6f,
    val damage: Int = 1,
    /** Outer glow and bright core of an enemy shot; the defaults are the spitter's acid orange. */
    val glowColor: Int = Color.rgb(255, 140, 20),
    val coreColor: Int = Color.rgb(255, 230, 80)
) {
    var isAlive = true
    var life = 4.0f // Expiry timer

    fun update(dt: Float, platforms: List<RectF>) {
        if (!isAlive) return
        life -= dt
        if (life <= 0f) {
            isAlive = false
            return
        }

        x += vx * dt
        y += vy * dt

        // Check platform collision
        for (plat in platforms) {
            if (plat.contains(x, y)) {
                isAlive = false
                break
            }
        }
    }

    fun getBoundingBox(): RectF =
        RectF(x - radius, y - radius, x + radius, y + radius)

    fun draw(canvas: Canvas, paint: Paint) {
        if (!isAlive) return

        paint.style = Paint.Style.FILL
        // Toxic acid spit / orange infected blob
        paint.color = glowColor
        canvas.drawCircle(x, y, radius + 2f, paint)
        paint.color = coreColor
        canvas.drawCircle(x, y, radius - 1.5f, paint)
    }
}
