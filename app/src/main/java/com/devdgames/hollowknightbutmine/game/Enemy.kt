package com.devdgames.hollowknightbutmine.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.sin

/**
 * Enemy entity. Can be stabbed, grabbed, headbutted, or parried.
 */
class Enemy(
    var x: Float,
    var y: Float,
    val width: Float = 46f,
    val height: Float = 42f,
    val type: EnemyType = EnemyType.CRAWLER,
    val isBoss: Boolean = false
) {
    var vx = 0f
    var vy = 0f
    var maxHp = if (isBoss) 300 else 60
    var hp = maxHp
    var isAlive = true

    var facing = Facing.LEFT
    var patrolSpeed = 70f
    var patrolTimer = 0f
    var isGrounded = false

    // Combat state
    var isStunned = false
    var stunTimer = 0f
    var isGrabbed = false // Held by the player's safety pin
    var invincibilityTimer = 0f
    var flashTimer = 0f

    // Attack cooldown for enemy
    var attackCooldown = 1.2f
    var isAttacking = false
    var attackWindup = 0f

    val hitbox = RectF()

    fun getBoundingBox(): RectF {
        hitbox.set(x - width / 2, y - height, x + width / 2, y)
        return hitbox
    }

    fun takeDamage(damage: Int, knockbackX: Float = 0f, knockbackY: Float = 0f, playEffect: (() -> Unit)? = null) {
        if (!isAlive || invincibilityTimer > 0f) return
        hp -= damage
        flashTimer = 0.15f
        invincibilityTimer = 0.2f
        vx = knockbackX
        vy = knockbackY
        playEffect?.invoke()
        if (hp <= 0) {
            hp = 0
            isAlive = false
            isGrabbed = false
        }
    }

    fun stun(duration: Float) {
        isStunned = true
        stunTimer = duration
        vx = 0f
    }

    fun update(dt: Float, playerX: Float, playerY: Float, platforms: List<RectF>) {
        if (!isAlive) return

        if (invincibilityTimer > 0f) invincibilityTimer -= dt
        if (flashTimer > 0f) flashTimer -= dt

        if (isGrabbed) {
            // Velocity controlled by the player holding it
            return
        }

        if (isStunned) {
            stunTimer -= dt
            if (stunTimer <= 0f) {
                isStunned = false
            }
            applyPhysics(dt, platforms)
            return
        }

        // Basic AI: Patrol or aggressively move toward player if close
        val dx = playerX - x
        val dist = abs(dx)

        if (dist < 320f) {
            // Aggro
            facing = if (dx > 0) Facing.RIGHT else Facing.LEFT
            val targetVx = if (facing == Facing.RIGHT) patrolSpeed * 1.6f else -patrolSpeed * 1.6f
            vx += (targetVx - vx) * 8f * dt

            // Wind up an attack if in close range
            if (dist < 70f) {
                attackWindup += dt
                if (attackWindup >= 0.6f && attackCooldown <= 0f) {
                    isAttacking = true
                    attackCooldown = 1.5f
                    attackWindup = 0f
                }
            } else {
                attackWindup = 0f
            }
        } else {
            // Chill patrol
            patrolTimer += dt
            if (patrolTimer > 3f) {
                patrolTimer = 0f
                facing = if (facing == Facing.LEFT) Facing.RIGHT else Facing.LEFT
            }
            val targetVx = if (facing == Facing.RIGHT) patrolSpeed else -patrolSpeed
            vx += (targetVx - vx) * 4f * dt
        }

        if (attackCooldown > 0f) {
            attackCooldown -= dt
            if (attackCooldown < 1.0f) {
                isAttacking = false
            }
        }

        applyPhysics(dt, platforms)
    }

    private fun applyPhysics(dt: Float, platforms: List<RectF>) {
        // Gravity
        vy += 1200f * dt
        if (vy > 900f) vy = 900f

        // Horizontal move
        x += vx * dt

        // Horizontal platform collision
        val currentBox = getBoundingBox()
        for (plat in platforms) {
            if (RectF.intersects(currentBox, plat)) {
                if (vx > 0) {
                    x = plat.left - width / 2
                    facing = Facing.LEFT
                } else if (vx < 0) {
                    x = plat.right + width / 2
                    facing = Facing.RIGHT
                }
                vx = 0f
                break
            }
        }

        // Vertical move
        y += vy * dt
        val vertBox = getBoundingBox()
        isGrounded = false
        for (plat in platforms) {
            if (RectF.intersects(vertBox, plat)) {
                if (vy > 0 && vertBox.bottom - (vy * dt) <= plat.top + 10f) {
                    y = plat.top
                    vy = 0f
                    isGrounded = true
                } else if (vy < 0) {
                    y = plat.bottom + height
                    vy = 0f
                }
            }
        }

        // Ground friction
        if (isGrounded) {
            vx *= 0.85f
        }
    }

    fun draw(canvas: Canvas, paint: Paint) {
        if (!isAlive) return

        val box = getBoundingBox()

        // Flash white on hit
        val bodyColor = when {
            flashTimer > 0f -> Color.WHITE
            isStunned -> Color.rgb(255, 235, 59) // Stunned golden glow
            isAttacking -> Color.rgb(220, 50, 50) // Telegraphing attack
            else -> Color.rgb(45, 30, 55) // Dark Hollow Knight beetle style
        }

        paint.style = Paint.Style.FILL
        paint.color = bodyColor

        // Draw shell body (curved beetle shape)
        canvas.drawRoundRect(box, 14f, 14f, paint)

        // Draw glowing bug eyes (orange infection glow / red eyes)
        paint.color = if (isStunned) Color.rgb(100, 200, 255) else Color.rgb(255, 120, 20)
        val eyeRadius = 5f
        val eyeOffset = if (facing == Facing.RIGHT) 10f else -10f
        canvas.drawCircle(x + eyeOffset, y - height * 0.65f, eyeRadius, paint)

        // Draw bug legs or carapace lines
        paint.color = Color.BLACK
        paint.strokeWidth = 3f
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(box, 14f, 14f, paint)

        // Telegraph attack indicator (spikes or aura)
        if (isAttacking) {
            paint.color = Color.rgb(255, 60, 60)
            paint.strokeWidth = 4f
            val spikeDir = if (facing == Facing.RIGHT) 1f else -1f
            canvas.drawLine(x, y - height / 2, x + spikeDir * 35f, y - height / 2, paint)
        }

        // Draw HP bar above enemy
        val barWidth = width * 1.2f
        val barHeight = 6f
        val barLeft = x - barWidth / 2
        val barTop = y - height - 14f

        paint.style = Paint.Style.FILL
        paint.color = Color.argb(180, 20, 20, 20)
        canvas.drawRect(barLeft, barTop, barLeft + barWidth, barTop + barHeight, paint)

        val hpPct = (hp.toFloat() / maxHp).coerceIn(0f, 1f)
        paint.color = if (isBoss) Color.rgb(230, 80, 80) else Color.rgb(180, 70, 70)
        canvas.drawRect(barLeft, barTop, barLeft + barWidth * hpPct, barTop + barHeight, paint)

        if (isStunned) {
            // Draw "STUNNED" dizzy stars
            paint.color = Color.YELLOW
            val starY = barTop - 8f
            canvas.drawCircle(x - 8f, starY + sin(stunTimer * 10f) * 4f, 3f, paint)
            canvas.drawCircle(x + 8f, starY - sin(stunTimer * 10f) * 4f, 3f, paint)
        }
    }
}
