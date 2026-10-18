package com.devdgames.hollowknightbutmine.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.sin

/**
 * Player Controller wielding the Safety Pin!
 * Abilities:
 * - Movement: Run, Jump, Dash (invincible i-frames)
 * - Safety Pin Combat:
 *    * Stab: Fast basic thrust with the needle point.
 *    * Grab: Hook an enemy with the safety pin clasp and latch on.
 *    * Headbutt: Smash grabbed enemy with your hard shell mask for heavy bonus damage.
 *    * Parry: Timed block with the pin's clasp; negates hit & stuns the enemy!
 * - Heal: Focus soul / thread to restore 1 mask.
 */
class Player(
    var x: Float,
    var y: Float
) {
    val width = 44f
    val height = 64f

    var vx = 0f
    var vy = 0f
    var facing = Facing.RIGHT
    var state = PlayerState.IDLE
    var isGrounded = false

    // Stats
    val maxMasks = 5
    var currentMasks = 5
    var soul = 33 // 0 to 100
    val maxSoul = 100

    // Cooldowns and action timers
    var stateTimer = 0f
    // Continuous clock for lightweight procedural character animation.
    private var animationTime = 0f
    var dashCooldown = 0f
    var canDashInAir = true
    var invincibilityTimer = 0f
    var flashTimer = 0f

    // Pin Combat mechanics
    var activeHitbox: AttackHitbox? = null
    var grabbedEnemy: Enemy? = null
    var parrySuccess = false

    // Healing mechanics
    var healCharge = 0f
    val healChargeTime = 1.0f // 1 second focus

    // Physics constants
    private val moveSpeed = 260f
    private val jumpSpeed = -540f
    private val dashSpeed = 680f
    private val gravity = 1250f

    val hitbox = RectF()

    fun getBoundingBox(): RectF {
        hitbox.set(x - width / 2, y - height, x + width / 2, y)
        return hitbox
    }

    fun jump() {
        if (state == PlayerState.HOLDING_ENEMY || state == PlayerState.HEADBUTTING) return
        if (isGrounded) {
            vy = jumpSpeed
            isGrounded = false
            state = PlayerState.JUMPING
        }
    }

    fun dash() {
        if (dashCooldown > 0f) return
        if (!isGrounded && !canDashInAir) return
        if (state == PlayerState.HOLDING_ENEMY || state == PlayerState.HEADBUTTING) return

        state = PlayerState.DASHING
        stateTimer = 0.22f // Dash duration
        dashCooldown = 0.65f
        invincibilityTimer = 0.24f // Dash i-frames!
        if (!isGrounded) canDashInAir = false

        val dir = if (facing == Facing.RIGHT) 1f else -1f
        vx = dir * dashSpeed
        vy = 0f // Suspended vertically during dash
    }

    /**
     * Safety Pin Quick Stab (Basic Attack)
     */
    fun stab() {
        if (state == PlayerState.DASHING || state == PlayerState.HOLDING_ENEMY || state == PlayerState.HEADBUTTING) return
        state = PlayerState.STABBING
        stateTimer = 0.22f

        val reach = 72f
        val attackX = if (facing == Facing.RIGHT) x + 10f else x - 10f - reach
        val attackBox = RectF(attackX, y - height * 0.75f, attackX + reach, y - height * 0.2f)
        val knockDir = if (facing == Facing.RIGHT) 160f else -160f
        activeHitbox = AttackHitbox(rect = attackBox, damage = 18, isGrab = false, knockbackX = knockDir, knockbackY = -80f)
    }

    /**
     * Safety Pin Grab (Hooking enemy with the clasp)
     */
    fun grab() {
        if (state == PlayerState.HOLDING_ENEMY) {
            // Already holding enemy -> execute Headbutt!
            executeHeadbutt()
            return
        }
        if (state == PlayerState.DASHING || state == PlayerState.HEADBUTTING) return

        state = PlayerState.GRABBING
        stateTimer = 0.25f

        val reach = 74f
        val attackX = if (facing == Facing.RIGHT) x + 10f else x - 10f - reach
        val attackBox = RectF(attackX, y - height * 0.75f, attackX + reach, y - height * 0.2f)
        activeHitbox = AttackHitbox(rect = attackBox, damage = 5, isGrab = true)
    }

    /**
     * Headbutt: Smash grabbed enemy with hard round mask for massive damage!
     */
    private fun executeHeadbutt() {
        val target = grabbedEnemy ?: return
        state = PlayerState.HEADBUTTING
        stateTimer = 0.3f

        // Headbutt deals high damage, bosses take balanced damage
        val damage = if (target.isBoss) 25 else 48
        val knockDir = if (facing == Facing.RIGHT) 360f else -360f
        target.isGrabbed = false
        target.takeDamage(damage, knockbackX = knockDir, knockbackY = -180f)
        target.stun(0.85f)

        // Player gains soul from headbutt combo
        soul = (soul + 25).coerceAtMost(maxSoul)

        grabbedEnemy = null
    }

    /**
     * Safety Pin Parry: Block incoming enemy hits with the pin's clasp
     */
    fun startParry() {
        if (state == PlayerState.HOLDING_ENEMY || state == PlayerState.HEADBUTTING) return
        state = PlayerState.PARRYING
        stateTimer = 0.28f // Tight parry window
        parrySuccess = false
    }

    /**
     * Focus / Heal using Soul
     */
    fun startHeal() {
        if (soul >= 33 && currentMasks < maxMasks && isGrounded && state != PlayerState.HOLDING_ENEMY) {
            state = PlayerState.HEALING
            healCharge = 0f
            vx = 0f
        }
    }

    fun stopHeal() {
        if (state == PlayerState.HEALING) {
            state = PlayerState.IDLE
            healCharge = 0f
        }
    }

    fun takeDamage(amount: Int = 1) {
        if (invincibilityTimer > 0f) return

        if (state == PlayerState.PARRYING) {
            // Successful PARRY!
            parrySuccess = true
            invincibilityTimer = 0.4f
            soul = (soul + 20).coerceAtMost(maxSoul)
            return
        }

        currentMasks = (currentMasks - amount).coerceAtLeast(0)
        invincibilityTimer = 1.0f
        flashTimer = 0.3f
        state = PlayerState.HURT
        stateTimer = 0.25f
        vy = -200f
        val knockDir = if (facing == Facing.RIGHT) -180f else 180f
        vx = knockDir

        // If holding enemy, release on hit
        grabbedEnemy?.isGrabbed = false
        grabbedEnemy = null
    }

    fun update(
        dt: Float,
        inputX: Float,
        platforms: List<RectF>,
        enemies: List<Enemy>,
        onParryTriggered: (() -> Unit)? = null
    ) {
        animationTime += dt
        if (invincibilityTimer > 0f) invincibilityTimer -= dt
        if (flashTimer > 0f) flashTimer -= dt
        if (dashCooldown > 0f) dashCooldown -= dt

        // Handle states and action timers
        when (state) {
            PlayerState.DASHING -> {
                stateTimer -= dt
                if (stateTimer <= 0f) {
                    state = if (isGrounded) PlayerState.IDLE else PlayerState.FALLING
                    vx = 0f
                }
            }
            PlayerState.STABBING -> {
                stateTimer -= dt
                checkCombatCollisions(enemies)
                if (stateTimer <= 0f) {
                    activeHitbox = null
                    state = if (isGrounded) PlayerState.IDLE else PlayerState.FALLING
                }
            }
            PlayerState.GRABBING -> {
                stateTimer -= dt
                checkCombatCollisions(enemies)
                if (stateTimer <= 0f && state == PlayerState.GRABBING) {
                    activeHitbox = null
                    state = if (isGrounded) PlayerState.IDLE else PlayerState.FALLING
                }
            }
            PlayerState.HOLDING_ENEMY -> {
                val target = grabbedEnemy
                if (target == null || !target.isAlive) {
                    grabbedEnemy = null
                    state = PlayerState.IDLE
                } else {
                    val pinOffset = if (facing == Facing.RIGHT) 42f else -42f
                    target.x = x + pinOffset
                    target.y = y - 5f
                    target.vx = 0f
                    target.vy = 0f

                    stateTimer -= dt
                    if (stateTimer <= 0f) {
                        target.isGrabbed = false
                        grabbedEnemy = null
                        state = PlayerState.IDLE
                    }
                }
            }
            PlayerState.HEADBUTTING -> {
                stateTimer -= dt
                if (stateTimer <= 0f) {
                    state = if (isGrounded) PlayerState.IDLE else PlayerState.FALLING
                }
            }
            PlayerState.PARRYING -> {
                stateTimer -= dt
                checkParryCollision(enemies, onParryTriggered)
                if (stateTimer <= 0f) {
                    state = if (isGrounded) PlayerState.IDLE else PlayerState.FALLING
                }
            }
            PlayerState.HEALING -> {
                healCharge += dt
                if (healCharge >= healChargeTime) {
                    if (soul >= 33 && currentMasks < maxMasks) {
                        soul -= 33
                        currentMasks++
                    }
                    healCharge = 0f
                    state = PlayerState.IDLE
                }
            }
            PlayerState.HURT -> {
                stateTimer -= dt
                if (stateTimer <= 0f) {
                    state = if (isGrounded) PlayerState.IDLE else PlayerState.FALLING
                }
            }
            else -> {
                // Idle, Running, Jumping, Falling
                if (inputX != 0f) {
                    facing = if (inputX > 0) Facing.RIGHT else Facing.LEFT
                    vx = inputX * moveSpeed
                    if (isGrounded) state = PlayerState.RUNNING
                } else {
                    vx = 0f
                    if (isGrounded) state = PlayerState.IDLE
                }

                if (!isGrounded) {
                    state = if (vy < 0) PlayerState.JUMPING else PlayerState.FALLING
                }
            }
        }

        // Apply movement physics (unless dashing or healing)
        if (state != PlayerState.DASHING && state != PlayerState.HEALING) {
            vy += gravity * dt
            if (vy > 950f) vy = 950f
        }

        applyCollision(dt, platforms)

        if (isGrounded) {
            canDashInAir = true
        }

        // Touch damage from active enemy if not parrying/invincible
        if (invincibilityTimer <= 0f && state != PlayerState.PARRYING) {
            val playerBox = getBoundingBox()
            for (enemy in enemies) {
                if (enemy.isAlive && !enemy.isGrabbed && !enemy.isStunned) {
                    if (RectF.intersects(playerBox, enemy.getBoundingBox())) {
                        takeDamage(1)
                        break
                    }
                }
            }
        }
    }

    private fun checkCombatCollisions(enemies: List<Enemy>) {
        val attack = activeHitbox ?: return
        for (enemy in enemies) {
            if (enemy.isAlive && !enemy.isGrabbed) {
                if (RectF.intersects(attack.rect, enemy.getBoundingBox())) {
                    if (attack.isGrab) {
                        enemy.isGrabbed = true
                        enemy.takeDamage(attack.damage)
                        grabbedEnemy = enemy
                        state = PlayerState.HOLDING_ENEMY
                        stateTimer = 2.5f
                        activeHitbox = null
                        break
                    } else {
                        enemy.takeDamage(attack.damage, attack.knockbackX, attack.knockbackY)
                        soul = (soul + 11).coerceAtMost(maxSoul)
                        activeHitbox = null
                        break
                    }
                }
            }
        }
    }

    private fun checkParryCollision(enemies: List<Enemy>, onParryTriggered: (() -> Unit)?) {
        val parryBox = RectF(
            if (facing == Facing.RIGHT) x else x - 55f,
            y - height,
            if (facing == Facing.RIGHT) x + 55f else x,
            y
        )
        for (enemy in enemies) {
            if (enemy.isAlive && RectF.intersects(parryBox, enemy.getBoundingBox())) {
                if (enemy.isAttacking || abs(enemy.vx) > 40f) {
                    enemy.stun(1.8f)
                    val knockDir = if (facing == Facing.RIGHT) 280f else -280f
                    enemy.vx = knockDir
                    parrySuccess = true
                    invincibilityTimer = 0.5f
                    soul = (soul + 25).coerceAtMost(maxSoul)
                    onParryTriggered?.invoke()
                    break
                }
            }
        }
    }

    private fun applyCollision(dt: Float, platforms: List<RectF>) {
        // Horizontal move
        x += vx * dt
        var box = getBoundingBox()
        for (plat in platforms) {
            if (RectF.intersects(box, plat)) {
                if (vx > 0) {
                    x = plat.left - width / 2
                } else if (vx < 0) {
                    x = plat.right + width / 2
                }
                vx = 0f
                break
            }
        }

        // Vertical move
        y += vy * dt
        box = getBoundingBox()
        isGrounded = false
        for (plat in platforms) {
            if (RectF.intersects(box, plat)) {
                if (vy > 0 && box.bottom - (vy * dt) <= plat.top + 12f) {
                    y = plat.top
                    vy = 0f
                    isGrounded = true
                } else if (vy < 0) {
                    y = plat.bottom + height
                    vy = 0f
                }
            }
        }
    }

    /**
     * Renders character inspired directly by the reference art:
     * - Perfectly round spherical ivory/bone head with two solid oval black eyes
     * - High folded yellow scarf/cowl wrapped around neck
     * - Tattered, jagged yellow cloak trailing behind
     * - Dark charcoal ninja tunic, trousers, and strapped leather boots
     * - Giant bronze/brass safety pin polearm/greatsword
     */
    fun draw(canvas: Canvas, paint: Paint) {
        val dir = if (facing == Facing.RIGHT) 1f else -1f
        val isFlashing = flashTimer > 0f

        // 1. Tattered Yellow Cape (Back layer)
        drawCape(canvas, paint, dir, isFlashing)

        // 2. Dark Pants & Strapped Boots (Legs)
        drawLegsAndBoots(canvas, paint, dir, isFlashing)

        // 3. Dark Charcoal Tunic / Torso with diagonal belt harness
        drawTorso(canvas, paint, dir, isFlashing)

        // 4. Giant Brass/Bronze Safety Pin Weapon
        drawGiantSafetyPin(canvas, paint, dir)

        // 5. Folded Yellow Scarf / Cowl around neck
        drawScarf(canvas, paint, dir, isFlashing)

        // 6. Round Ivory Skull / Head & Void Eyes
        drawRoundHead(canvas, paint, dir, isFlashing)

        // 7. Special Combat FX (parry sparks, heal aura, dash trail)
        drawSpecialEffects(canvas, paint)
    }

    private fun drawCape(canvas: Canvas, paint: Paint, dir: Float, isFlashing: Boolean) {
        paint.style = Paint.Style.FILL
        paint.color = if (isFlashing) Color.WHITE else Color.rgb(208, 158, 48) // Warm mustard yellow

        val cape = Path()
        val capeTopX = x - dir * 4f
        val capeTopY = y - height * 0.65f
        cape.moveTo(capeTopX, capeTopY)

        // Cape responds to movement and action, rather than staying rigid.
        val moving = abs(vx) > 20f || state == PlayerState.DASHING || state == PlayerState.JUMPING || state == PlayerState.FALLING
        val flutter = sin(animationTime * (if (moving) 13f else 3.5f)) * (if (moving) 7f else 2.5f)
        val capeBackDir = -dir
        cape.quadTo(x + capeBackDir * (24f + flutter), y - height * 0.45f, x + capeBackDir * (32f + flutter), y - 6f + flutter * 0.35f)
        // Ragged hem flexes as the character moves.
        cape.lineTo(x + capeBackDir * (28f + flutter), y - 14f)
        cape.lineTo(x + capeBackDir * (22f + flutter * 0.6f), y - 2f + flutter * 0.25f)
        cape.lineTo(x + capeBackDir * 16f, y - 10f)
        cape.lineTo(x + capeBackDir * 10f, y - 2f)
        cape.lineTo(x, y - height * 0.25f)
        cape.close()
        canvas.drawPath(cape, paint)

        // Cape shadow / fold detail
        if (!isFlashing) {
            paint.color = Color.rgb(160, 115, 30) // Darker yellow shadow
            paint.strokeWidth = 2f
            paint.style = Paint.Style.STROKE
            canvas.drawPath(cape, paint)
        }
    }

    private fun drawLegsAndBoots(canvas: Canvas, paint: Paint, dir: Float, isFlashing: Boolean) {
        paint.style = Paint.Style.FILL

        // Dark baggy charcoal ninja pants
        paint.color = if (isFlashing) Color.WHITE else Color.rgb(32, 30, 36)
        // Two-frame procedural stride: legs swing opposite each other while running.
        val stride = if (state == PlayerState.RUNNING) sin(animationTime * 17f) * 6f else 0f
        val airborneTuck = if (state == PlayerState.JUMPING || state == PlayerState.FALLING || state == PlayerState.DASHING) 3f else 0f
        // Left leg
        canvas.drawRoundRect(x - 11f - stride, y - 24f + airborneTuck, x - 2f - stride, y - 8f + airborneTuck, 3f, 3f, paint)
        // Right leg
        canvas.drawRoundRect(x + 2f + stride, y - 24f - airborneTuck, x + 11f + stride, y - 8f - airborneTuck, 3f, 3f, paint)

        // Strapped leather boots
        paint.color = if (isFlashing) Color.WHITE else Color.rgb(22, 18, 22)
        // Boot feet pointing forward
        val footDir = dir * 3f
        canvas.drawRoundRect(x - 12f + footDir, y - 10f, x - 1f + footDir, y, 4f, 4f, paint)
        canvas.drawRoundRect(x + 1f + footDir, y - 10f, x + 12f + footDir, y, 4f, 4f, paint)

        // Boot leather strap cross-ties
        if (!isFlashing) {
            paint.color = Color.rgb(65, 55, 50)
            paint.strokeWidth = 1.8f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(x - 10f, y - 16f, x - 3f, y - 12f, paint)
            canvas.drawLine(x + 3f, y - 16f, x + 10f, y - 12f, paint)
        }
    }

    private fun drawTorso(canvas: Canvas, paint: Paint, dir: Float, isFlashing: Boolean) {
        paint.style = Paint.Style.FILL
        paint.color = if (isFlashing) Color.WHITE else Color.rgb(34, 32, 40)
        val torso = RectF(x - 9f, y - height * 0.65f, x + 9f, y - 20f)
        canvas.drawRoundRect(torso, 4f, 4f, paint)

        // Diagonal leather strap / belt harness across chest
        if (!isFlashing) {
            paint.color = Color.rgb(68, 52, 42)
            paint.strokeWidth = 3f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(x - dir * 7f, y - height * 0.62f, x + dir * 6f, y - 24f, paint)

            // Leather pouch on hip
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(90, 70, 52)
            canvas.drawRoundRect(x - dir * 8f - 4f, y - 32f, x - dir * 8f + 4f, y - 22f, 2f, 2f, paint)
        }
    }

    private fun drawScarf(canvas: Canvas, paint: Paint, dir: Float, isFlashing: Boolean) {
        paint.style = Paint.Style.FILL
        paint.color = if (isFlashing) Color.WHITE else Color.rgb(228, 175, 52) // Bright golden cowl

        val cowlY = y - height * 0.68f
        // Bulky folded ring around neck
        val cowlRect = RectF(x - 15f, cowlY - 8f, x + 15f, cowlY + 7f)
        canvas.drawRoundRect(cowlRect, 8f, 8f, paint)

        // Cowl fold outline
        if (!isFlashing) {
            paint.color = Color.rgb(170, 125, 32)
            paint.strokeWidth = 2f
            paint.style = Paint.Style.STROKE
            canvas.drawRoundRect(cowlRect, 8f, 8f, paint)
            canvas.drawLine(x - 12f, cowlY, x + 12f, cowlY, paint)
        }
    }

    private fun drawRoundHead(canvas: Canvas, paint: Paint, dir: Float, isFlashing: Boolean) {
        // Head moves forward slightly during Headbutt attack
        val headOffsetX = dir * (if (state == PlayerState.HEADBUTTING) 16f else 0f)
        val runBob = if (state == PlayerState.RUNNING) abs(sin(animationTime * 17f)) * 2.5f else 0f
        val jumpTilt = if (state == PlayerState.JUMPING) -2f else if (state == PlayerState.FALLING) 2f else 0f
        val headCenterX = x + headOffsetX
        val headCenterY = y - height * 0.82f + runBob + jumpTilt
        val headRadius = 16f

        // Perfectly round ivory/bone skull
        paint.style = Paint.Style.FILL
        paint.color = if (isFlashing) Color.WHITE else Color.rgb(244, 240, 232)
        canvas.drawCircle(headCenterX, headCenterY, headRadius, paint)

        // Subtle shaded contour around skull
        if (!isFlashing) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.2f
            paint.color = Color.rgb(195, 185, 172)
            canvas.drawCircle(headCenterX, headCenterY, headRadius, paint)
        }

        // Deep hollow black oval eyes (from reference: vertical dark ovals)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(16, 14, 20)

        val eyeSpacing = 6.5f
        val eyeWidth = 4.2f
        val eyeHeight = 7.5f
        val eyeCenterShift = dir * 2.5f

        // Near eye (larger, direct gaze)
        canvas.drawOval(
            headCenterX + eyeCenterShift - eyeSpacing - eyeWidth / 2,
            headCenterY - eyeHeight / 2,
            headCenterX + eyeCenterShift - eyeSpacing + eyeWidth / 2,
            headCenterY + eyeHeight / 2,
            paint
        )

        // Far eye
        canvas.drawOval(
            headCenterX + eyeCenterShift + eyeSpacing - eyeWidth / 2,
            headCenterY - eyeHeight / 2,
            headCenterX + eyeCenterShift + eyeSpacing + eyeWidth / 2,
            headCenterY + eyeHeight / 2,
            paint
        )
    }

    /**
     * Draws the Giant Safety Pin (Polearm / Greatsword sized):
     * - Coiled circular spring at back
     * - Bronze/brass weathered metal body
     * - Sharp needle tip & openable clasp
     */
    private fun drawGiantSafetyPin(canvas: Canvas, paint: Paint, dir: Float) {
        val pinOriginX = x + dir * 6f
        val pinOriginY = y - height * 0.5f

        paint.strokeCap = Paint.Cap.ROUND

        when (state) {
            PlayerState.STABBING -> {
                // Three-beat thrust: draw back, snap forward, then recoil.
                val progress = (1f - stateTimer / 0.22f).coerceIn(0f, 1f)
                val thrust = when {
                    progress < 0.22f -> -10f * (progress / 0.22f)
                    progress < 0.58f -> -10f + 78f * ((progress - 0.22f) / 0.36f)
                    else -> 68f * (1f - (progress - 0.58f) / 0.42f)
                }
                val tipX = pinOriginX + dir * (26f + thrust)
                val tipY = pinOriginY - 2f
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 5f
                paint.color = Color.rgb(205, 160, 90)
                canvas.drawLine(pinOriginX - dir * 14f, pinOriginY + 3f, tipX, tipY, paint)
                paint.style = Paint.Style.FILL
                paint.color = Color.rgb(160, 120, 60)
                canvas.drawRoundRect(pinOriginX - dir * 22f, pinOriginY - 5f, pinOriginX - dir * 10f, pinOriginY + 11f, 4f, 4f, paint)
                // Brief impact glint only near the end of the thrust.
                if (progress in 0.42f..0.66f) {
                    paint.style = Paint.Style.STROKE
                    paint.color = Color.rgb(255, 235, 175)
                    paint.strokeWidth = 2.5f
                    canvas.drawLine(tipX, tipY - 7f, tipX + dir * 9f, tipY + 7f, paint)
                    canvas.drawLine(tipX + dir * 2f, tipY - 9f, tipX + dir * 2f, tipY + 9f, paint)
                }
            }
            PlayerState.GRABBING, PlayerState.HOLDING_ENEMY -> {
                // The hook reaches out in a quick arc instead of popping into place.
                val progress = if (state == PlayerState.GRABBING)
                    (1f - stateTimer / 0.25f).coerceIn(0f, 1f) else 1f
                val reach = if (state == PlayerState.HOLDING_ENEMY) 50f else 18f + 38f * (1f - (1f - progress) * (1f - progress))
                val lift = if (state == PlayerState.HOLDING_ENEMY) 0f else sin(progress * Math.PI).toFloat() * 7f
                val tipX = pinOriginX + dir * reach
                val hook = Path()
                hook.moveTo(pinOriginX - dir * 10f, pinOriginY + 4f)
                hook.quadTo(pinOriginX + dir * (reach * 0.55f), pinOriginY - 15f - lift, tipX, pinOriginY + 9f)
                hook.quadTo(tipX - dir * 13f, pinOriginY + 22f, tipX - dir * 20f, pinOriginY + 12f)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 5f
                paint.color = Color.rgb(215, 175, 100)
                canvas.drawPath(hook, paint)
                // A small warm glint sells the clasp snapping shut on contact.
                if (state == PlayerState.HOLDING_ENEMY) {
                    paint.strokeWidth = 2f
                    paint.color = Color.rgb(255, 225, 150)
                    canvas.drawLine(tipX - dir * 4f, pinOriginY + 3f, tipX + dir * 4f, pinOriginY + 11f, paint)
                }
                paint.style = Paint.Style.FILL
                paint.color = Color.rgb(150, 110, 55)
                canvas.drawCircle(pinOriginX - dir * 12f, pinOriginY, 8f, paint)
            }
            PlayerState.PARRYING -> {
                // Pin held upright defensively as giant guard
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 6.5f
                paint.color = if (parrySuccess) Color.rgb(255, 235, 90) else Color.rgb(210, 170, 95)

                val guardX = pinOriginX + dir * 18f
                canvas.drawLine(guardX, pinOriginY - 32f, guardX, pinOriginY + 28f, paint)

                // Large clasp housing
                paint.style = Paint.Style.FILL
                paint.color = Color.rgb(170, 125, 65)
                canvas.drawRoundRect(guardX - 6f, pinOriginY + 16f, guardX + 6f, pinOriginY + 34f, 4f, 4f, paint)
                // Coil ring at top
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4f
                canvas.drawCircle(guardX, pinOriginY - 32f, 7f, paint)
            }
            else -> {
                // Idle / Running: Wielded over shoulder / diagonally strapped across back
                val angleDir = dir
                val backX = pinOriginX - angleDir * 20f
                val backY = pinOriginY - 26f
                val frontX = pinOriginX + angleDir * 24f
                val frontY = pinOriginY + 20f

                // Main bronze shaft
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4.5f
                paint.color = Color.rgb(195, 150, 80) // Weathered bronze
                canvas.drawLine(backX, backY, frontX, frontY, paint)

                // Large clasp at bottom front (like reference art)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                paint.color = Color.rgb(160, 120, 60)
                val claspRect = RectF(frontX - 4f, frontY - 3f, frontX + angleDir * 18f, frontY + 18f)
                canvas.drawRoundRect(claspRect, 6f, 6f, paint)
                // Inner hollow opening of safety pin clasp
                canvas.drawCircle(frontX + angleDir * 8f, frontY + 7f, 3.5f, paint)

                // Large spring coil loop at the top back
                paint.strokeWidth = 3.5f
                canvas.drawCircle(backX, backY, 7.5f, paint)
                canvas.drawPoint(backX, backY, paint)
            }
        }
    }

    private fun drawSpecialEffects(canvas: Canvas, paint: Paint) {
        paint.style = Paint.Style.STROKE

        // Parry golden flash spark
        if (state == PlayerState.PARRYING && parrySuccess) {
            paint.color = Color.rgb(255, 235, 80)
            paint.strokeWidth = 4f
            val px = if (facing == Facing.RIGHT) x + 30f else x - 30f
            canvas.drawCircle(px, y - height / 2, 22f, paint)
            canvas.drawCircle(px, y - height / 2, 34f, paint)
        }

        // Heal Focus Aura
        if (state == PlayerState.HEALING) {
            paint.color = Color.rgb(220, 240, 255)
            paint.strokeWidth = 3f
            val pulse = (sin(healCharge * 15f) * 6f)
            canvas.drawCircle(x, y - height / 2, 30f + pulse, paint)

            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(180, 225, 255)
            canvas.drawCircle(x - 12f, y - height * (healCharge / healChargeTime), 4f, paint)
            canvas.drawCircle(x + 10f, y - height * ((healCharge * 1.3f) % 1f), 3.5f, paint)
        }

        // Dash speed lines
        if (state == PlayerState.DASHING) {
            paint.color = Color.argb(160, 230, 185, 80)
            paint.strokeWidth = 2.5f
            val dir = if (facing == Facing.RIGHT) 1f else -1f
            for (i in 0..3) {
                val trailX = x - dir * (20f + i * 14f)
                val trailY = y - height * (0.2f + i * 0.2f)
                canvas.drawLine(trailX, trailY, trailX - dir * 18f, trailY, paint)
            }
        }
    }
}
