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
    val height = 67.2f // 5% taller silhouette

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
    private var landingTimer = 0f
    private var jumpBurstTimer = 0f
    private var dashBurstTimer = 0f
    private var doubleJumpBurstTimer = 0f
    private var airJumpsUsed = 0
    private val maxAirJumps = 1
    /** Distance from the boots down to the nearest floor; -1 when there is none nearby. */
    private var shadowDrop = -1f
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
    var healHeld = false
    val healChargeTime = 1.0f // 1 second focus per mask

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
        if (state == PlayerState.DEAD) return
        if (state == PlayerState.HOLDING_ENEMY || state == PlayerState.HEADBUTTING ||
            state == PlayerState.GRABBING || state == PlayerState.HOLDING_ENEMY) return

        if (isGrounded) {
            vy = jumpSpeed
            isGrounded = false
            airJumpsUsed = 0
            state = PlayerState.JUMPING
            jumpBurstTimer = 0.16f
            landingTimer = 0f
        } else if (airJumpsUsed < maxAirJumps &&
            state != PlayerState.DASHING && state != PlayerState.HEADBUTTING) {
            // Second press in mid-air: punchy double jump with a fresh upward impulse.
            airJumpsUsed++
            // Stronger upward impulse makes the second jump feel like a real mid-air kick.
            vy = jumpSpeed * 1.02f
            state = PlayerState.JUMPING
            stateTimer = 0.12f
            doubleJumpBurstTimer = 0.24f
            landingTimer = 0f
        } else {
            return
        }
    }

    fun dash() {
        if (state == PlayerState.DEAD) return
        if (dashCooldown > 0f) return
        if (!isGrounded && !canDashInAir) return
        if (state == PlayerState.HOLDING_ENEMY || state == PlayerState.HEADBUTTING) return

        state = PlayerState.DASHING
        stateTimer = 0.28f // Slightly longer, punchier dash
        dashBurstTimer = 0.22f
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
        if (state == PlayerState.DEAD) return
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
    fun grab(enemies: List<Enemy> = emptyList()) {
        if (state == PlayerState.DEAD) return
        // While an enemy is being pulled in, don't restart the grab or require another press.
        if (state == PlayerState.HOLDING_ENEMY || state == PlayerState.HEADBUTTING) return
        if (state == PlayerState.DASHING) return

        state = PlayerState.GRABBING
        stateTimer = 0.25f

        val reach = 150f // Long-range hook so the grab has a useful pull distance.
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
        if (state == PlayerState.DEAD) return
        if (state == PlayerState.HOLDING_ENEMY || state == PlayerState.HEADBUTTING) return
        state = PlayerState.PARRYING
        stateTimer = 0.72f // More forgiving parry window
        parrySuccess = false
    }

    /**
     * Focus / Heal using Soul
     */
    fun startHeal() {
        // Focus is a held channel: every full second spends 33 Soul for one mask.
        if (state != PlayerState.DEAD && soul >= 33 && currentMasks < maxMasks &&
            isGrounded && state != PlayerState.HOLDING_ENEMY && state != PlayerState.HEADBUTTING) {
            healHeld = true
            if (state != PlayerState.HEALING) {
                state = PlayerState.HEALING
                healCharge = 0f
                vx = 0f
            }
        }
    }

    fun stopHeal() {
        healHeld = false
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
        healHeld = false
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
        if (landingTimer > 0f) landingTimer = (landingTimer - dt).coerceAtLeast(0f)
        if (jumpBurstTimer > 0f) jumpBurstTimer = (jumpBurstTimer - dt).coerceAtLeast(0f)
        if (dashBurstTimer > 0f) dashBurstTimer = (dashBurstTimer - dt).coerceAtLeast(0f)
        if (doubleJumpBurstTimer > 0f) doubleJumpBurstTimer = (doubleJumpBurstTimer - dt).coerceAtLeast(0f)
        if (invincibilityTimer > 0f) invincibilityTimer -= dt
        if (flashTimer > 0f) flashTimer -= dt
        if (dashCooldown > 0f) dashCooldown -= dt

        // Handle states and action timers
        when (state) {
            PlayerState.DEAD -> {
                // Freeze completely during the brief death pause.
                vx = 0f
                vy = 0f
                activeHitbox = null
                grabbedEnemy?.isGrabbed = false
                grabbedEnemy = null
            }
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

                // Update horizontal movement according to current input
                if (inputX != 0f) {
                    facing = if (inputX > 0) Facing.RIGHT else Facing.LEFT
                    vx = inputX * moveSpeed * 0.75f // Controlled mobile attack
                } else {
                    vx = 0f
                }

                if (stateTimer <= 0f) {
                    activeHitbox = null
                    state = if (isGrounded) PlayerState.IDLE else PlayerState.FALLING
                }
            }
            PlayerState.GRABBING -> {
                stateTimer -= dt
                checkCombatCollisions(enemies)

                if (inputX != 0f) {
                    facing = if (inputX > 0) Facing.RIGHT else Facing.LEFT
                    vx = inputX * moveSpeed * 0.65f
                } else {
                    vx = 0f
                }

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

                    // The enemy is pulled into range; headbutt automatically, no second button press.
                    stateTimer -= dt
                    if (stateTimer <= 0f && grabbedEnemy === target) {
                        executeHeadbutt()
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
                vx = 0f // Locked during parry stand
                checkParryCollision(enemies, onParryTriggered)
                if (stateTimer <= 0f) {
                    state = if (isGrounded) PlayerState.IDLE else PlayerState.FALLING
                }
            }
            PlayerState.HEALING -> {
                healCharge += dt
                vx = 0f
                if (healCharge >= healChargeTime) {
                    if (soul >= 33 && currentMasks < maxMasks) {
                        soul -= 33
                        currentMasks++
                    }
                    healCharge = 0f
                    if (healHeld && soul >= 33 && currentMasks < maxMasks && isGrounded) {
                        // Keep focusing while held, just like Hollow Knight.
                        state = PlayerState.HEALING
                    } else {
                        state = PlayerState.IDLE
                        healHeld = false
                    }
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
        if (state != PlayerState.DASHING && state != PlayerState.HEALING && state != PlayerState.DEAD) {
            vy += gravity * dt
            if (vy > 950f) vy = 950f
        }

        val wasAirborne = !isGrounded
        applyCollision(dt, platforms)
        if (wasAirborne && isGrounded) {
            landingTimer = 0.22f
            jumpBurstTimer = 0f
        }

        if (isGrounded) {
            canDashInAir = true
            airJumpsUsed = 0
        }

        updateShadow(platforms)

        // Touch damage from active enemy if not parrying/invincible
        if (invincibilityTimer <= 0f && state != PlayerState.PARRYING && state != PlayerState.DEAD) {
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

    /** Find the floor under the boots so the contact shadow can sit on it. */
    private fun updateShadow(platforms: List<RectF>) {
        var best = Float.MAX_VALUE
        for (p in platforms) {
            if (x + width * 0.2f < p.left || x - width * 0.2f > p.right) continue
            val d = p.top - y
            if (d >= -2f && d < best) best = d
        }
        shadowDrop = if (best == Float.MAX_VALUE) -1f else best.coerceAtLeast(0f)
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
                        stateTimer = 0.20f // Brief pull-in beat, then automatically headbutt.
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
        // Horizontal move (only collide with thick solid walls/blocks, not thin jump-through slabs)
        x += vx * dt
        var box = getBoundingBox()
        for (plat in platforms) {
            // Jump-through ledges are thin (< 20px tall); don't block horizontal running/jumping through them
            val isThinLedge = plat.height() < 20f
            if (!isThinLedge && RectF.intersects(box, plat)) {
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
        val prevBottom = box.bottom
        y += vy * dt
        box = getBoundingBox()
        isGrounded = false
        for (plat in platforms) {
            val isThinLedge = plat.height() < 20f
            if (RectF.intersects(box, plat)) {
                if (vy > 0 && prevBottom <= plat.top + kotlin.math.max(14f, vy * dt + 2f)) {
                    // Landing on top surface of platform/slab
                    y = plat.top
                    vy = 0f
                    isGrounded = true
                } else if (vy < 0 && !isThinLedge) {
                    // Bonk head on thick solid ceiling only (jump straight through thin floating slabs)
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

        // 0. Grounding: a soft shadow that stays on the floor while jumping.
        drawContactShadow(canvas, paint)

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

    /** Soft blob of shade underneath the knight, projected onto the floor. */
    private fun drawContactShadow(canvas: Canvas, paint: Paint) {
        if (shadowDrop < 0f) return
        val falloff = (1f - shadowDrop / 260f).coerceIn(0.22f, 1f)
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        paint.color = Color.argb((80f * falloff).toInt().coerceIn(0, 255), 0, 0, 0)
        val halfW = 19f + shadowDrop * 0.04f
        canvas.drawOval(x - halfW, y + shadowDrop - 4f, x + halfW, y + shadowDrop + 4f, paint)
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
        val doubleJump = doubleJumpBurstTimer > 0f
        val flutter = sin(animationTime * (if (doubleJump) 22f else if (moving) 13f else 3.5f)) *
            (if (doubleJump) 12f else if (moving) 7f else 2.5f)
        val capeBackDir = -dir
        val boostFlare = if (doubleJump) 18f * (doubleJumpBurstTimer / 0.24f) else 0f
        cape.quadTo(
            x + capeBackDir * (24f + flutter + boostFlare * 0.35f),
            y - height * 0.45f + boostFlare * 0.2f,
            x + capeBackDir * (32f + flutter + boostFlare),
            y - 6f + flutter * 0.35f + boostFlare * 0.25f
        )
        // During the second jump the cape snaps wide, selling a sudden burst of force.
        cape.lineTo(x + capeBackDir * (28f + flutter + boostFlare * 0.8f), y - 14f + boostFlare * 0.25f)
        cape.lineTo(x + capeBackDir * (22f + flutter * 0.6f + boostFlare * 0.5f), y - 2f + flutter * 0.25f)
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
        val doubleJumpTuck = if (doubleJumpBurstTimer > 0f) sin((doubleJumpBurstTimer / 0.24f) * Math.PI).toFloat() * 10f else 0f
        val airborneTuck = when {
            state == PlayerState.DASHING -> 6f
            state == PlayerState.JUMPING && doubleJumpBurstTimer > 0f -> 12f + doubleJumpTuck
            state == PlayerState.JUMPING -> 5f + sin(animationTime * 9f) * 1.5f
            state == PlayerState.FALLING -> 2f
            landingTimer > 0f -> -sin((landingTimer / 0.22f) * Math.PI).toFloat() * 3f
            else -> 0f
        }
        // Legs pivot at the hips. Each boot follows its own leg so the feet never
        // float separately while the running stride swings.
        val leftLegX = x - 7f - stride
        val rightLegX = x + 7f + stride
        val leftFootY = y - 9f + airborneTuck
        val rightFootY = y - 9f - airborneTuck
        canvas.drawRoundRect(leftLegX - 4f, y - 27f + airborneTuck, leftLegX + 4f, leftFootY, 3.5f, 3.5f, paint)
        canvas.drawRoundRect(rightLegX - 4f, y - 27f - airborneTuck, rightLegX + 4f, rightFootY, 3.5f, 3.5f, paint)

        // Chunky boots stay attached to the legs and sit directly on the floor.
        paint.color = if (isFlashing) Color.WHITE else Color.rgb(22, 18, 22)
        val footDir = dir * 3f
        canvas.drawRoundRect(leftLegX - 6f + footDir, leftFootY - 2f, leftLegX + 5f + footDir, y + 1f, 4f, 4f, paint)
        canvas.drawRoundRect(rightLegX - 5f + footDir, rightFootY - 2f, rightLegX + 6f + footDir, y + 1f, 4f, 4f, paint)

        // Boot leather strap cross-ties
        if (!isFlashing) {
            paint.color = Color.rgb(65, 55, 50)
            paint.strokeWidth = 1.8f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(leftLegX - 3f, leftFootY - 8f, leftLegX + 3f, leftFootY - 4f, paint)
            canvas.drawLine(rightLegX - 3f, rightFootY - 8f, rightLegX + 3f, rightFootY - 4f, paint)
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
        val jumpTilt = when {
            state == PlayerState.JUMPING && doubleJumpBurstTimer > 0f -> -7f
            state == PlayerState.JUMPING -> -3.5f
            state == PlayerState.FALLING -> 3f
            state == PlayerState.DASHING -> 2f
            else -> 0f
        }
        val landingDip = if (landingTimer > 0f) sin((landingTimer / 0.22f) * 3.1415927f) * 4f else 0f
        val headCenterX = x + headOffsetX + if (state == PlayerState.DASHING) dir * 3f else 0f
        val headCenterY = y - height * 0.82f + runBob + jumpTilt + landingDip
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
                val lift = if (state == PlayerState.HOLDING_ENEMY) 0f else sin(progress * 3.1415927f) * 7f
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
                // Idle / running: a compact Hollow-Knight-style nail, not a long staff.
                // The blade sits low in the hand with a slight hooked tip and restrained metal glint.
                val gripX = pinOriginX + dir * 8f
                val gripY = pinOriginY + 9f
                val guardX = gripX + dir * 5f
                val guardY = gripY - 1f
                val tipX = gripX + dir * 34f
                val tipY = gripY - 13f

                // Dark wrapped handle.
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = 5f
                paint.color = Color.rgb(48, 43, 37)
                canvas.drawLine(gripX - dir * 9f, gripY + 5f, gripX + dir * 3f, gripY, paint)

                // Small brass crossguard.
                paint.strokeWidth = 3.2f
                paint.color = Color.rgb(173, 133, 69)
                canvas.drawLine(guardX - dir * 5f, guardY + 6f, guardX + dir * 5f, guardY - 4f, paint)

                // Tapered pale-steel nail blade, with a gentle hooked curve.
                val blade = Path()
                blade.moveTo(guardX, guardY - 1f)
                blade.quadTo(gripX + dir * 20f, gripY - 12f, tipX, tipY)
                blade.quadTo(tipX - dir * 4f, tipY + 8f, tipX - dir * 10f, tipY + 8f)
                paint.strokeWidth = 4.2f
                paint.color = Color.rgb(166, 184, 174)
                canvas.drawPath(blade, paint)
                paint.strokeWidth = 1.35f
                paint.color = Color.rgb(230, 239, 219)
                canvas.drawLine(guardX + dir * 2f, guardY - 3f, tipX - dir * 3f, tipY + 1f, paint)

                // Tiny pommel, kept close to the hand so the silhouette reads as a nail.
                paint.style = Paint.Style.FILL
                paint.color = Color.rgb(196, 151, 78)
                canvas.drawCircle(gripX - dir * 10f, gripY + 5f, 3f, paint)
            }
        }
    }

    private fun drawSpecialEffects(canvas: Canvas, paint: Paint) {
        paint.style = Paint.Style.STROKE

        // Nail swipe: a bright crescent that snaps out ahead of the knight.
        if (state == PlayerState.STABBING) {
            val progress = (1f - stateTimer / 0.22f).coerceIn(0f, 1f)
            val fade = (1f - progress).coerceIn(0f, 1f)
            val dir = if (facing == Facing.RIGHT) 1f else -1f
            val cx = x + dir * 20f
            val cy = y - height * 0.52f
            val r = 42f + progress * 16f
            val start = if (dir > 0) -70f else 110f
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = 6f * (1f - progress * 0.55f)
            paint.color = Color.argb((130f * fade).toInt().coerceIn(0, 255), 226, 238, 255)
            canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), start, 140f, false, paint)
            paint.strokeWidth = 2.2f
            paint.color = Color.argb((105f * fade).toInt().coerceIn(0, 255), 255, 236, 178)
            canvas.drawArc(RectF(cx - r + 8f, cy - r + 8f, cx + r - 8f, cy + r - 8f), start + 26f, 62f, false, paint)
            paint.strokeCap = Paint.Cap.BUTT
        }

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

        // Jump take-off burst: small gold flecks kick from the feet.
        if (jumpBurstTimer > 0f) {
            val p = jumpBurstTimer / 0.16f
            paint.style = Paint.Style.STROKE
            paint.color = Color.argb((p * 190f).toInt().coerceIn(0, 190), 235, 195, 105)
            paint.strokeWidth = 2f
            for (i in -1..1) {
                val spread = (1f - p) * (12f + abs(i) * 8f)
                canvas.drawLine(x + i * 5f, y - 2f, x + i * 5f + spread, y + 3f + (1f - p) * 5f, paint)
            }
        }

        // Double-jump propulsion: sharp golden jets shoot DOWN from the boots,
        // so the player looks like they're kicking off the air with extra force.
        if (doubleJumpBurstTimer > 0f) {
            val p = 1f - doubleJumpBurstTimer / 0.24f
            val alpha = ((1f - p) * 225f).toInt().coerceIn(0, 225)
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = Color.argb(alpha, 255, 205, 95)
            paint.strokeWidth = 3.5f
            for (i in -2..2) {
                val startX = x + i * 5f
                val startY = y - 5f
                val spread = i * (3f + p * 3f)
                val length = (1f - p) * (18f + (2 - kotlin.math.abs(i)) * 5f)
                canvas.drawLine(startX, startY, startX + spread, startY + length, paint)
            }
            // A brief compressed shockwave under the character, not a floating halo.
            paint.strokeWidth = 2.5f
            paint.color = Color.argb(alpha, 255, 225, 155)
            canvas.drawOval(x - 15f - p * 9f, y - 4f, x + 15f + p * 9f, y + 2f, paint)
        }

        // Landing squash cue: expanding ground ring and two dust puffs.
        if (landingTimer > 0f) {
            val p = 1f - landingTimer / 0.22f
            paint.style = Paint.Style.STROKE
            paint.color = Color.argb(((1f - p) * 150f).toInt().coerceIn(0, 150), 205, 174, 112)
            paint.strokeWidth = 2.5f
            canvas.drawOval(x - 8f - p * 18f, y - 3f, x + 8f + p * 18f, y + 3f, paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(((1f - p) * 100f).toInt().coerceIn(0, 100), 170, 155, 130)
            canvas.drawCircle(x - 12f - p * 10f, y - 2f - p * 4f, 2f + (1f - p) * 2f, paint)
            canvas.drawCircle(x + 12f + p * 10f, y - 2f - p * 4f, 2f + (1f - p) * 2f, paint)
        }

        // Dash speed lines
        if (state == PlayerState.DASHING || dashBurstTimer > 0f) {
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
