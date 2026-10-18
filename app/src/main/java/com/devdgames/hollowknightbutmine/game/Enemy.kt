package com.devdgames.hollowknightbutmine.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Enemy entity with variations:
 * - CRAWLER: Patrols slabs, aggros and charges when close.
 * - FLYER: Aerial bat / squit that hovers and swoops down at the player.
 * - SPITTER: Ranged artillery acid beetle that fires projectiles.
 * - SHIELDER: Armored bug with a heavy chitin shield (guards frontal attacks, weak to parry/grab).
 * - MOSS_CHARGER: Heavy boss/mini-boss with ground-shaking rushes.
 */
class Enemy(
    var x: Float,
    var y: Float,
    var width: Float = 46f,
    var height: Float = 42f,
    val type: EnemyType = EnemyType.CRAWLER,
    val isBoss: Boolean = false
) {
    var vx = 0f
    var vy = 0f

    // HP based on enemy type
    var maxHp = when {
        isBoss -> 300
        type == EnemyType.SHIELDER -> 95
        type == EnemyType.SPITTER -> 45
        type == EnemyType.FLYER -> 35
        else -> 60
    }
    var hp = maxHp
    var isAlive = true

    var facing = Facing.LEFT
    var patrolSpeed = when (type) {
        EnemyType.FLYER -> 110f
        EnemyType.SHIELDER -> 45f
        EnemyType.SPITTER -> 35f
        EnemyType.MOSS_CHARGER -> 130f
        else -> 70f
    }
    var patrolTimer = 0f
    var isGrounded = false

    // Combat state
    var isStunned = false
    var stunTimer = 0f
    var isGrabbed = false // Held by player's safety pin
    var invincibilityTimer = 0f
    var flashTimer = 0f

    // Attack cooldown and telegraphing
    var attackCooldown = 1.2f
    var isAttacking = false
    var attackWindup = 0f

    // Flyer hovering & dive-bombing mechanics
    private var hoverTimer = 0f
    private var flyStartY = y
    private var isDiving = false

    // Spitter firing queue callback
    var onShootProjectile: ((x: Float, y: Float, vx: Float, vy: Float) -> Unit)? = null

    // Shielder defense state
    var isShieldRaised = (type == EnemyType.SHIELDER)

    val hitbox = RectF()

    init {
        // Adjust hitbox size based on type
        when (type) {
            EnemyType.FLYER -> {
                width = 38f
                height = 36f
            }
            EnemyType.SPITTER -> {
                width = 44f
                height = 38f
            }
            EnemyType.SHIELDER -> {
                width = 50f
                height = 52f
            }
            EnemyType.MOSS_CHARGER -> {
                width = 68f
                height = 60f
            }
            else -> {}
        }
    }

    fun getBoundingBox(): RectF {
        hitbox.set(x - width / 2, y - height, x + width / 2, y)
        return hitbox
    }

    fun takeDamage(damage: Int, knockbackX: Float = 0f, knockbackY: Float = 0f, playEffect: (() -> Unit)? = null) {
        if (!isAlive || invincibilityTimer > 0f) return

        // Shielder blocks frontal attacks unless stunned or grabbed!
        if (type == EnemyType.SHIELDER && !isStunned && !isGrabbed) {
            val attackFromFront = if (facing == Facing.RIGHT) knockbackX < 0 else knockbackX > 0
            if (attackFromFront && knockbackX != 0f) {
                // Shield deflected the hit!
                flashTimer = 0.08f
                vx = if (facing == Facing.RIGHT) -40f else 40f
                return
            }
        }

        hp -= damage
        flashTimer = 0.15f
        invincibilityTimer = 0.18f
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
        isAttacking = false
        attackWindup = 0f
    }

    fun update(dt: Float, playerX: Float, playerY: Float, platforms: List<RectF>) {
        if (!isAlive) return

        if (invincibilityTimer > 0f) invincibilityTimer -= dt
        if (flashTimer > 0f) flashTimer -= dt

        if (isGrabbed) return

        if (isStunned) {
            stunTimer -= dt
            if (stunTimer <= 0f) {
                isStunned = false
            }
            if (type != EnemyType.FLYER) {
                applyPhysics(dt, platforms)
            }
            return
        }

        when (type) {
            EnemyType.FLYER -> updateFlyerAI(dt, playerX, playerY)
            EnemyType.SPITTER -> updateSpitterAI(dt, playerX, playerY, platforms)
            EnemyType.SHIELDER -> updateShielderAI(dt, playerX, playerY, platforms)
            EnemyType.MOSS_CHARGER -> updateChargerAI(dt, playerX, playerY, platforms)
            else -> updateCrawlerAI(dt, playerX, playerY, platforms)
        }
    }

    private fun updateCrawlerAI(dt: Float, playerX: Float, playerY: Float, platforms: List<RectF>) {
        val dx = playerX - x
        val dist = abs(dx)

        if (dist < 320f && abs(playerY - y) < 180f) {
            facing = if (dx > 0) Facing.RIGHT else Facing.LEFT
            val targetVx = if (facing == Facing.RIGHT) patrolSpeed * 1.6f else -patrolSpeed * 1.6f
            vx += (targetVx - vx) * 8f * dt

            if (dist < 70f) {
                attackWindup += dt
                if (attackWindup >= 0.5f && attackCooldown <= 0f) {
                    isAttacking = true
                    attackCooldown = 1.5f
                    attackWindup = 0f
                }
            } else {
                attackWindup = 0f
            }
        } else {
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
            if (attackCooldown < 1.0f) isAttacking = false
        }

        applyPhysics(dt, platforms)
    }

    private fun updateFlyerAI(dt: Float, playerX: Float, playerY: Float) {
        hoverTimer += dt * 3.5f
        val dx = playerX - x
        val dy = playerY - y
        val dist = kotlin.math.sqrt(dx * dx + dy * dy)

        facing = if (dx > 0) Facing.RIGHT else Facing.LEFT

        if (dist < 380f) {
            // Aggro flyer behavior: hover above player, then swoop down!
            if (!isDiving) {
                val targetHoverY = playerY - 140f
                val targetHoverX = playerX + (if (facing == Facing.RIGHT) -60f else 60f)

                vx += (targetHoverX - x) * 2.5f * dt
                vy += (targetHoverY - y) * 2.5f * dt

                attackCooldown -= dt
                if (attackCooldown <= 0f && dist < 240f) {
                    // Launch swoop dive!
                    isDiving = true
                    isAttacking = true
                    val angle = kotlin.math.atan2(dy, dx)
                    vx = cos(angle) * 360f
                    vy = sin(angle) * 360f
                    attackCooldown = 2.2f
                }
            } else {
                // In dive attack
                attackCooldown -= dt
                if (attackCooldown < 1.4f) {
                    isDiving = false
                    isAttacking = false
                }
            }
        } else {
            // Idle gentle aerial hovering
            vx *= 0.92f
            vy = sin(hoverTimer) * 45f
            isDiving = false
            isAttacking = false
        }

        x += vx * dt
        y += vy * dt
    }

    private fun updateSpitterAI(dt: Float, playerX: Float, playerY: Float, platforms: List<RectF>) {
        val dx = playerX - x
        val dist = abs(dx)
        facing = if (dx > 0) Facing.RIGHT else Facing.LEFT

        if (dist < 460f && abs(playerY - y) < 220f) {
            // Keep moderate distance
            if (dist < 180f) {
                // Back away
                val fleeVx = if (facing == Facing.RIGHT) -patrolSpeed else patrolSpeed
                vx += (fleeVx - vx) * 6f * dt
            } else {
                vx *= 0.8f
            }

            // Wind up spitter shot
            attackCooldown -= dt
            if (attackCooldown <= 0f) {
                attackWindup += dt
                isAttacking = true
                if (attackWindup >= 0.65f) {
                    // Fire projectile toward player!
                    val shootDir = if (facing == Facing.RIGHT) 1f else -1f
                    val projVx = shootDir * 320f
                    val projVy = -70f // slight upward arc
                    onShootProjectile?.invoke(x + shootDir * 20f, y - height * 0.6f, projVx, projVy)

                    attackCooldown = 2.5f
                    attackWindup = 0f
                    isAttacking = false
                }
            }
        } else {
            isAttacking = false
            attackWindup = 0f
            vx = 0f
        }

        applyPhysics(dt, platforms)
    }

    private fun updateShielderAI(dt: Float, playerX: Float, playerY: Float, platforms: List<RectF>) {
        val dx = playerX - x
        val dist = abs(dx)
        facing = if (dx > 0) Facing.RIGHT else Facing.LEFT

        // Steady disciplined march with heavy shield raised
        if (dist < 340f) {
            val targetVx = if (facing == Facing.RIGHT) patrolSpeed else -patrolSpeed
            vx += (targetVx - vx) * 5f * dt

            if (dist < 75f) {
                attackWindup += dt
                if (attackWindup >= 0.7f && attackCooldown <= 0f) {
                    isAttacking = true
                    attackCooldown = 1.8f
                    attackWindup = 0f
                }
            } else {
                attackWindup = 0f
            }
        } else {
            vx *= 0.85f
        }

        if (attackCooldown > 0f) {
            attackCooldown -= dt
            if (attackCooldown < 1.2f) isAttacking = false
        }

        applyPhysics(dt, platforms)
    }

    private fun updateChargerAI(dt: Float, playerX: Float, playerY: Float, platforms: List<RectF>) {
        val dx = playerX - x
        val dist = abs(dx)
        facing = if (dx > 0) Facing.RIGHT else Facing.LEFT

        if (dist < 420f) {
            attackWindup += dt
            if (attackWindup > 0.8f) {
                // Bull charge!
                isAttacking = true
                val chargeDir = if (facing == Facing.RIGHT) 1f else -1f
                vx = chargeDir * patrolSpeed * 2.2f

                if (attackWindup > 1.8f) {
                    attackWindup = 0f
                    isAttacking = false
                }
            } else {
                // Shuffling windup
                vx *= 0.85f
            }
        } else {
            attackWindup = 0f
            isAttacking = false
            vx *= 0.8f
        }

        applyPhysics(dt, platforms)
    }

    private fun applyPhysics(dt: Float, platforms: List<RectF>) {
        vy += 1200f * dt
        if (vy > 900f) vy = 900f

        x += vx * dt
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

        if (isGrounded) {
            vx *= 0.85f
        }
    }

    fun draw(canvas: Canvas, paint: Paint) {
        if (!isAlive) return

        val box = getBoundingBox()
        val dir = if (facing == Facing.RIGHT) 1f else -1f

        when (type) {
            EnemyType.FLYER -> drawFlyer(canvas, paint, dir)
            EnemyType.SPITTER -> drawSpitter(canvas, paint, dir)
            EnemyType.SHIELDER -> drawShielder(canvas, paint, dir)
            EnemyType.MOSS_CHARGER -> drawCharger(canvas, paint, dir)
            else -> drawCrawler(canvas, paint, dir)
        }

        // Draw Health Bar
        drawHPBar(canvas, paint)
    }

    private fun drawCrawler(canvas: Canvas, paint: Paint, dir: Float) {
        val box = getBoundingBox()
        val bodyColor = when {
            flashTimer > 0f -> Color.WHITE
            isStunned -> Color.rgb(255, 235, 59)
            isAttacking -> Color.rgb(220, 50, 50)
            else -> Color.rgb(45, 30, 55)
        }

        paint.style = Paint.Style.FILL
        paint.color = bodyColor
        canvas.drawRoundRect(box, 14f, 14f, paint)

        // Eyes
        paint.color = if (isStunned) Color.rgb(100, 200, 255) else Color.rgb(255, 120, 20)
        canvas.drawCircle(x + dir * 10f, y - height * 0.65f, 5f, paint)

        // Carapace line
        paint.color = Color.BLACK
        paint.strokeWidth = 3f
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(box, 14f, 14f, paint)

        if (isAttacking) {
            paint.color = Color.rgb(255, 60, 60)
            paint.strokeWidth = 4f
            canvas.drawLine(x, y - height / 2, x + dir * 35f, y - height / 2, paint)
        }
    }

    private fun drawFlyer(canvas: Canvas, paint: Paint, dir: Float) {
        val bodyColor = when {
            flashTimer > 0f -> Color.WHITE
            isStunned -> Color.rgb(255, 235, 59)
            isAttacking -> Color.rgb(255, 70, 70)
            else -> Color.rgb(65, 45, 80) // Winged purple husk
        }

        paint.style = Paint.Style.FILL
        paint.color = bodyColor

        // Teardrop bug thorax
        canvas.drawOval(x - 14f, y - height * 0.8f, x + 14f, y - 6f, paint)

        // Translucent insect wings buzzing
        paint.color = Color.argb(190, 200, 225, 255)
        val wingFlap = sin(hoverTimer * 12f) * 8f
        val wing = Path()
        wing.moveTo(x, y - height * 0.7f)
        wing.quadTo(x - dir * 26f, y - height - 12f + wingFlap, x - dir * 28f, y - height * 0.6f)
        wing.close()
        canvas.drawPath(wing, paint)

        // Glowing needle proboscis
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = if (isAttacking) Color.rgb(255, 80, 80) else Color.rgb(210, 200, 180)
        canvas.drawLine(x, y - height * 0.4f, x + dir * 22f, y - height * 0.2f, paint)

        // Orange glowing flyer eye
        paint.style = Paint.Style.FILL
        paint.color = if (isStunned) Color.rgb(100, 200, 255) else Color.rgb(255, 140, 20)
        canvas.drawCircle(x + dir * 6f, y - height * 0.65f, 4.5f, paint)
    }

    private fun drawSpitter(canvas: Canvas, paint: Paint, dir: Float) {
        val bodyColor = when {
            flashTimer > 0f -> Color.WHITE
            isStunned -> Color.rgb(255, 235, 59)
            isAttacking -> Color.rgb(255, 160, 40) // Acid sac pulsing!
            else -> Color.rgb(38, 55, 40) // Toxic moss green shell
        }

        paint.style = Paint.Style.FILL
        paint.color = bodyColor
        val box = getBoundingBox()
        canvas.drawRoundRect(box, 16f, 16f, paint)

        // Glowing toxic acid belly
        val acidColor = if (isAttacking) Color.rgb(255, 220, 40) else Color.rgb(120, 210, 50)
        paint.color = acidColor
        canvas.drawCircle(x - dir * 6f, y - height * 0.5f, 10f, paint)

        // Cannon mouth / nozzle
        paint.color = Color.rgb(25, 35, 25)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        canvas.drawLine(x, y - height * 0.6f, x + dir * 24f, y - height * 0.65f, paint)

        // Bug eye
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(255, 110, 20)
        canvas.drawCircle(x + dir * 8f, y - height * 0.72f, 4f, paint)
    }

    private fun drawShielder(canvas: Canvas, paint: Paint, dir: Float) {
        val bodyColor = when {
            flashTimer > 0f -> Color.WHITE
            isStunned -> Color.rgb(255, 235, 59)
            isAttacking -> Color.rgb(230, 60, 60)
            else -> Color.rgb(50, 45, 60)
        }

        paint.style = Paint.Style.FILL
        paint.color = bodyColor
        val box = getBoundingBox()
        // Husk body
        canvas.drawRoundRect(box.left + 8f, box.top, box.right - 8f, box.bottom, 10f, 10f, paint)

        // Great Chitin Tower Shield (Facing player!)
        val shieldX = if (facing == Facing.RIGHT) x + 10f else x - 24f
        paint.color = if (isStunned) Color.rgb(120, 110, 100) else Color.rgb(190, 180, 165)
        val shieldRect = RectF(shieldX, y - height - 4f, shieldX + 14f, y + 2f)
        canvas.drawRoundRect(shieldRect, 5f, 5f, paint)

        // Shield rune / reinforce plate
        paint.color = Color.rgb(70, 60, 50)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        canvas.drawRoundRect(shieldRect, 5f, 5f, paint)
        canvas.drawLine(shieldRect.centerX(), shieldRect.top + 6f, shieldRect.centerX(), shieldRect.bottom - 6f, paint)

        // Red menacing visor slit behind shield
        paint.style = Paint.Style.FILL
        paint.color = if (isStunned) Color.rgb(100, 200, 255) else Color.rgb(255, 50, 50)
        canvas.drawRect(x - dir * 2f, y - height * 0.75f, x + dir * 6f, y - height * 0.70f, paint)
    }

    private fun drawCharger(canvas: Canvas, paint: Paint, dir: Float) {
        val bodyColor = when {
            flashTimer > 0f -> Color.WHITE
            isStunned -> Color.rgb(255, 235, 59)
            isAttacking -> Color.rgb(240, 70, 70)
            else -> Color.rgb(65, 80, 50) // Moss overgrown beast
        }

        paint.style = Paint.Style.FILL
        paint.color = bodyColor
        val box = getBoundingBox()
        canvas.drawRoundRect(box, 20f, 20f, paint)

        // Heavy front bone horns / tusks
        paint.color = Color.rgb(230, 225, 210)
        val tusk = Path()
        tusk.moveTo(x + dir * 18f, y - height * 0.4f)
        tusk.lineTo(x + dir * 42f, y - height * 0.65f)
        tusk.lineTo(x + dir * 20f, y - height * 0.2f)
        tusk.close()
        canvas.drawPath(tusk, paint)

        // Glowing infection eyes (multiple beast eyes)
        paint.color = Color.rgb(255, 120, 20)
        canvas.drawCircle(x + dir * 14f, y - height * 0.65f, 5f, paint)
        canvas.drawCircle(x + dir * 22f, y - height * 0.58f, 3.5f, paint)
    }

    private fun drawHPBar(canvas: Canvas, paint: Paint) {
        val barWidth = width * 1.25f
        val barHeight = if (isBoss) 8f else 6f
        val barLeft = x - barWidth / 2
        val barTop = y - height - 16f

        paint.style = Paint.Style.FILL
        paint.color = Color.argb(180, 20, 20, 20)
        canvas.drawRect(barLeft, barTop, barLeft + barWidth, barTop + barHeight, paint)

        val hpPct = (hp.toFloat() / maxHp).coerceIn(0f, 1f)
        paint.color = when {
            isBoss -> Color.rgb(235, 70, 70)
            type == EnemyType.SHIELDER -> Color.rgb(200, 160, 60)
            type == EnemyType.SPITTER -> Color.rgb(100, 210, 60)
            type == EnemyType.FLYER -> Color.rgb(140, 120, 220)
            else -> Color.rgb(180, 70, 70)
        }
        canvas.drawRect(barLeft, barTop, barLeft + barWidth * hpPct, barTop + barHeight, paint)

        if (isStunned) {
            paint.color = Color.YELLOW
            val starY = barTop - 8f
            canvas.drawCircle(x - 8f, starY + sin(stunTimer * 10f) * 4f, 3f, paint)
            canvas.drawCircle(x + 8f, starY - sin(stunTimer * 10f) * 4f, 3f, paint)
        }
    }
}
