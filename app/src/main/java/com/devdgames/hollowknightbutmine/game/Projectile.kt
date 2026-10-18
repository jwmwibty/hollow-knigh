package com.devdgames.hollowknightbutmine.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/**
 * Projectile fired by Spitter enemies or hazards.
 * Can be parried by the player's safety pin to deflect it back!
 */
class Projectile(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val radius: Float = 6f,
    val damage: Int = 1,
    var isReflected: Boolean = false
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

    fun reflect(newVx: Float, newVy: Float) {
        isReflected = true
        vx = newVx
        vy = newVy
        life = 3.0f
    }

    fun draw(canvas: Canvas, paint: Paint) {
        if (!isAlive) return

        paint.style = Paint.Style.FILL
        if (isReflected) {
            // Reflected glow (bright cyan / gold player energy)
            paint.color = Color.rgb(100, 240, 255)
            canvas.drawCircle(x, y, radius + 2f, paint)
            paint.color = Color.WHITE
            canvas.drawCircle(x, y, radius - 1f, paint)
        } else {
            // Toxic acid spit / orange infected blob
            paint.color = Color.rgb(255, 140, 20)
            canvas.drawCircle(x, y, radius + 2f, paint)
            paint.color = Color.rgb(255, 230, 80)
            canvas.drawCircle(x, y, radius - 1.5f, paint)
        }
    }
}
