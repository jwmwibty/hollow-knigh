package com.devdgames.hollowknightbutmine.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.devdgames.hollowknightbutmine.game.combat.AttackPhase
import com.devdgames.hollowknightbutmine.game.combat.CombatMove
import com.devdgames.hollowknightbutmine.game.combat.MoveSpec
import com.devdgames.hollowknightbutmine.game.combat.PlayerCombat
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.max

/**
 * Player Controller wielding a sword.
 * Abilities:
 * - Movement: Run, Jump, Dash (invincible i-frames)
 * - Sword Combat: slash, thrust, rising cut, and an aerial downstrike with a hit-confirmed bounce.
 * - Heal: Focus soul / thread to restore 1 mask.
 *
 * The timing, damage and hitbox windows live in [PlayerCombat], which is pure Kotlin; this
 * class turns that model into movement, collision boxes, bounce response and sword animation.
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
    /** Health masks. Starts at 5; each boss victory adds one (see game/boss). */
    var maxMasks = 5
    var currentMasks = 5
    /** Each visible mask contains two damage points. 1 means the current mask is cracked/half spent. */
    var damageInCurrentMask = 0
    private val damagePerMask = 2
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
    var dashCooldown = 0f
    var canDashInAir = true
    var invincibilityTimer = 0f
    var flashTimer = 0f

    // Sword combat: the whole move set and its timings.
    val combat = PlayerCombat()
    var activeHitbox: AttackHitbox? = null

    // Restrained impact feedback where the blade connected.
    private var impactTimer = 0f
    private var impactX = 0f
    private var impactY = 0f

    // Cached drawing objects: the sword is rebuilt every frame but never re-allocated.
    private val bladePath = Path()
    private val arcRect = RectF()

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

        val launched = when {
            isGrounded -> {
                vy = jumpSpeed
                isGrounded = false
                airJumpsUsed = 0
                jumpBurstTimer = 0.16f
                landingTimer = 0f
                true
            }
            airJumpsUsed < maxAirJumps && state != PlayerState.DASHING -> {
                // Second press in mid-air: punchy double jump with a fresh upward impulse.
                airJumpsUsed++
                // Stronger upward impulse makes the second jump feel like a real mid-air kick.
                vy = jumpSpeed * 1.02f
                stateTimer = 0.12f
                doubleJumpBurstTimer = 0.24f
                landingTimer = 0f
                true
            }
            else -> false
        }
        if (!launched) return

        // A jump interrupts a swing instead of leaving it frozen mid-arc.
        combat.cancel()
        activeHitbox = null
        state = PlayerState.JUMPING
    }

    fun dash() {
        if (state == PlayerState.DEAD) return
        if (dashCooldown > 0f) return
        if (!isGrounded && !canDashInAir) return

        combat.cancel()
        activeHitbox = null
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
     * Light attack: a quick sword slash that sweeps a wide, short arc.
     */
    fun slash() {
        beginAttack(CombatMove.SLASH)
    }

    /** A committed forward thrust with extra reach and damage. */
    fun stab() {
        beginAttack(CombatMove.THRUST)
    }

    /** A rising cut whose tall hitbox is designed to catch hovering or jumping enemies. */
    fun risingCut() {
        beginAttack(CombatMove.RISING_CUT)
    }

    /**
     * A downward aerial strike. It sends the knight downward, and a confirmed hit rebounds
     * upward so the player can chain a well-timed strike into another jump.
     */
    fun downstrike() {
        if (isGrounded || state == PlayerState.DEAD || state == PlayerState.DASHING) return
        if (beginAttack(CombatMove.DOWNSTRIKE)) {
            vy = max(vy, 120f)
        }
    }

    /**
     * Starts [move] through [combat]. The request is ignored while the player is dead, dashing
     * or in the middle of a swing's wind-up/strike; the short recovery tail can be cancelled
     * into the next attack, so mashing the button never feels locked out.
     */
    private fun beginAttack(move: CombatMove): Boolean {
        if (state == PlayerState.DEAD || state == PlayerState.DASHING) return false
        if (!combat.begin(move)) return false

        // Swinging breaks focus rather than healing and attacking at the same time.
        if (state == PlayerState.HEALING) {
            healHeld = false
            healCharge = 0f
        }
        activeHitbox = null
        state = if (move == CombatMove.THRUST) PlayerState.STABBING else PlayerState.SLASHING
        stateTimer = combat.currentSpec?.duration ?: 0f
        return true
    }

    /**
     * Builds the live blade hitbox from the attack's spec. It exists only during the ACTIVE
     * window, so the damage window and the drawn blade can never visibly disagree.
     */
    private fun buildHitbox(spec: MoveSpec): AttackHitbox {
        val reach = spec.reach
        val rightFacing = facing == Facing.RIGHT
        val box = when (combat.move) {
            CombatMove.RISING_CUT -> {
                // Front-biased vertical sweep reaches well above the knight's head.
                val left = if (rightFacing) x + 2f else x - reach - 2f
                RectF(left, y - height * 1.38f, left + reach + 4f, y - height * 0.32f)
            }
            CombatMove.DOWNSTRIKE -> {
                // Narrow blade zone under the feet; this move is only enabled in the air.
                RectF(x - reach * 0.46f, y - 8f, x + reach * 0.46f, y + reach)
            }
            else -> {
                val attackX = if (rightFacing) x + 10f else x - 10f - reach
                RectF(
                    attackX,
                    y - height * spec.boxTopFraction,
                    attackX + reach,
                    y - height * spec.boxBottomFraction
                )
            }
        }
        val knockDir = if (rightFacing) spec.knockbackX else -spec.knockbackX
        return AttackHitbox(box, spec.damage, knockDir, spec.knockbackY)
    }

    /**
     * Focus / Heal using Soul
     */
    fun startHeal() {
        // Focus is a held channel: every full second spends 33 Soul for one mask.
        if (state != PlayerState.DEAD && soul >= 33 && currentMasks < maxMasks &&
            isGrounded && state != PlayerState.DASHING) {
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

        var remainingDamage = amount.coerceAtLeast(0)
        while (remainingDamage > 0 && currentMasks > 0) {
            val needed = damagePerMask - damageInCurrentMask
            if (remainingDamage >= needed) {
                remainingDamage -= needed
                currentMasks = (currentMasks - 1).coerceAtLeast(0)
                damageInCurrentMask = 0
            } else {
                damageInCurrentMask += remainingDamage
                remainingDamage = 0
            }
        }
        healHeld = false
        invincibilityTimer = 1.0f
        flashTimer = 0.3f
        state = PlayerState.HURT
        stateTimer = 0.25f
        vy = -200f
        val knockDir = if (facing == Facing.RIGHT) -180f else 180f
        vx = knockDir

        // Being hit interrupts a swing; the blade stops hurting immediately.
        combat.cancel()
        activeHitbox = null
    }

    fun update(
        dt: Float,
        inputX: Float,
        platforms: List<RectF>,
        enemies: List<Enemy>
    ) {
        animationTime += dt
        if (impactTimer > 0f) impactTimer = (impactTimer - dt).coerceAtLeast(0f)
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
                combat.cancel()
            }
            PlayerState.DASHING -> {
                stateTimer -= dt
                if (stateTimer <= 0f) {
                    state = if (isGrounded) PlayerState.IDLE else PlayerState.FALLING
                    vx = 0f
                }
            }
            PlayerState.SLASHING, PlayerState.STABBING -> {
                tickAttack(dt, inputX, enemies)
            }
            PlayerState.HEALING -> {
                healCharge += dt
                vx = 0f
                if (healCharge >= healChargeTime) {
                    if (soul >= 33 && currentMasks < maxMasks) {
                        soul -= 33
                        currentMasks++
                        damageInCurrentMask = 0
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

        // Touch damage from an active enemy, unless the player is invincible or dead.
        if (invincibilityTimer <= 0f && state != PlayerState.DEAD) {
            val playerBox = getBoundingBox()
            for (enemy in enemies) {
                if (enemy.isAlive && !enemy.isStunned) {
                    if (enemy.hurtsPlayer(playerBox)) {
                        takeDamage(enemy.touchDamage)
                        break
                    }
                }
            }
        }
    }

    /**
     * Advances the attack in progress. The model owns the timeline; this only keeps the body,
     * the state and the live hitbox in step with it, so the drawn blade and the damage window
     * always agree.
     */
    private fun tickAttack(dt: Float, inputX: Float, enemies: List<Enemy>) {
        combat.update(dt)
        stateTimer = (combat.currentSpec?.duration ?: 0f) - combat.elapsed

        val spec = combat.currentSpec
        if (spec == null || combat.phase == AttackPhase.IDLE) {
            // The swing finished (or was cancelled): drop the blade and return to neutral.
            activeHitbox = null
            state = if (isGrounded) PlayerState.IDLE else PlayerState.FALLING
            return
        }

        // Controlled travel while the swing plays out: the attack never roots the player.
        if (inputX != 0f) {
            facing = if (inputX > 0) Facing.RIGHT else Facing.LEFT
            vx = inputX * moveSpeed * spec.travelScale
        } else {
            vx = 0f
        }

        // The blade only exists while it is actually travelling, and hits at most once.
        if (combat.isActive && !combat.connected) {
            // Rebuild each active frame so the attack follows the knight and meets fast chargers.
            activeHitbox = buildHitbox(spec)
            checkCombatCollisions(enemies)
        } else {
            activeHitbox = null
        }
    }

    private fun checkCombatCollisions(enemies: List<Enemy>) {
        val attack = activeHitbox ?: return
        val move = combat.move
        for (enemy in enemies) {
            if (!enemy.isAlive) continue
            if (!RectF.intersects(attack.rect, enemy.getBoundingBox())) continue

            val before = enemy.hp
            enemy.takeDamage(attack.damage, attack.knockbackX, attack.knockbackY)

            // An enemy may reject this hit during brief invulnerability or while guarding.
            // Don't spend the player's swing unless health actually decreases.
            if (enemy.hp >= before) continue

            combat.onHit()
            activeHitbox = null

            if (move == CombatMove.DOWNSTRIKE && !isGrounded) {
                vy = -430f
                isGrounded = false
                airJumpsUsed = 0
                canDashInAir = true
                doubleJumpBurstTimer = 0.14f
            }

            // Soul and impact feedback are reserved for confirmed damage, never blocked hits.
            val spec = combat.currentSpec ?: return
            soul = (soul + spec.soulGain).coerceAtMost(maxSoul)
            impactTimer = 0.12f
            impactX = attack.rect.centerX()
            impactY = enemy.y - enemy.height * 0.55f
            return
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
     * - A steel sword with a brass crossguard, held in the near hand
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

        // 4. Sword (grip, crossguard, blade) and its swing arc
        drawSword(canvas, paint, dir)

        // 5. Folded Yellow Scarf / Cowl around neck
        drawScarf(canvas, paint, dir, isFlashing)

        // 6. Round Ivory Skull / Head & Void Eyes
        drawRoundHead(canvas, paint, dir, isFlashing)

        // 7. Special Combat FX (impact sparks, heal aura, dash trail)
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
        // Each leg is a small two-joint rig: thigh angle, knee bend, ankle position,
        // then a foot that follows the ankle. This is deliberately not one rigid rotating stick.
        val phase = animationTime * 10f
        val hipY = y - 27f

        fun drawLeg(side: Int, nearSide: Boolean) {
            val legPhase = phase + if (side > 0) Math.PI.toFloat() else 0f
            val swing = sin(legPhase)
            val kneeFold = max(0f, cos(legPhase))
            var upperAngle: Float
            var lowerAngle: Float
            var footLift = 0f

            when (state) {
                PlayerState.RUNNING -> {
                    // Thigh drives the stride; the shin folds behind it during lift-off.
                    upperAngle = dir * swing * 0.66f
                    lowerAngle = upperAngle - dir * kneeFold * 0.82f
                    footLift = kneeFold * 4.5f
                }
                PlayerState.JUMPING -> {
                    // Tuck both knees, with a little asymmetry so the pose feels alive.
                    upperAngle = dir * (side * 0.24f + 0.12f)
                    val tuck = if (doubleJumpBurstTimer > 0f) 0.98f else 0.62f
                    lowerAngle = upperAngle - dir * (tuck + if (side < 0) 0.12f else 0f)
                    footLift = if (doubleJumpBurstTimer > 0f) 3.5f else 1.5f
                }
                PlayerState.FALLING -> {
                    // Legs extend down, then relax slightly at the knees.
                    upperAngle = dir * side * 0.17f
                    lowerAngle = upperAngle - dir * 0.24f
                }
                PlayerState.DASHING -> {
                    // Both legs stream behind the horizontal body, with bent knees.
                    upperAngle = -dir * (0.82f + side * 0.05f)
                    lowerAngle = upperAngle - dir * 0.48f
                }
                PlayerState.SLASHING, PlayerState.STABBING -> {
                    // A planted, slightly staggered stance sells the weight behind sword attacks.
                    upperAngle = dir * (if (side.toFloat() * dir > 0f) 0.24f else -0.18f)
                    lowerAngle = upperAngle - dir * 0.2f
                }
                PlayerState.HURT -> {
                    upperAngle = -dir * 0.24f + side * 0.08f
                    lowerAngle = upperAngle + dir * 0.36f
                }
                else -> {
                    // Idle / healing: relaxed feet, tiny outward stance, soft knee bend.
                    upperAngle = dir * side * 0.09f
                    lowerAngle = upperAngle - dir * 0.12f
                }
            }

            val hipX = x + side * 4.5f
            val upperLength = 10.5f
            val lowerLength = 9.5f
            val kneeX = hipX + sin(upperAngle) * upperLength
            val kneeY = hipY + cos(upperAngle) * upperLength
            val ankleX = kneeX + sin(lowerAngle) * lowerLength
            val ankleY = kneeY + cos(lowerAngle) * lowerLength - footLift

            // Far leg sits behind the near leg. Slight value separation gives the knee joint
            // a readable shape without adding an outline-heavy, robotic hinge.
            val legColor = when {
                isFlashing -> Color.WHITE
                nearSide -> Color.rgb(36, 33, 41)
                else -> Color.rgb(27, 25, 32)
            }
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = legColor
            paint.strokeWidth = if (nearSide) 7.2f else 6.4f
            canvas.drawLine(hipX, hipY, kneeX, kneeY, paint)
            paint.strokeWidth = if (nearSide) 6.3f else 5.7f
            canvas.drawLine(kneeX, kneeY, ankleX, ankleY, paint)

            // Small knee joint blends the two segments while keeping the bend visible.
            paint.style = Paint.Style.FILL
            paint.color = legColor
            canvas.drawCircle(kneeX, kneeY, if (nearSide) 3.1f else 2.7f, paint)

            // Draw the boot in ankle-local coordinates. Mirroring the local foot means its toe
            // always points the way the knight faces, while the ankle tilt stays subtle.
            canvas.save()
            canvas.translate(ankleX, ankleY)
            canvas.scale(dir, 1f)
            canvas.rotate(Math.toDegrees((lowerAngle * 0.22f).toDouble()).toFloat())
            paint.style = Paint.Style.FILL
            paint.color = if (isFlashing) Color.WHITE else Color.rgb(19, 17, 21)
            val boot = RectF(-4.5f, -2.4f, 7.5f, 4.2f)
            canvas.drawRoundRect(boot, 2.8f, 2.8f, paint)
            if (!isFlashing) {
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = 1.2f
                paint.color = if (nearSide) Color.rgb(91, 72, 57) else Color.rgb(65, 54, 47)
                canvas.drawLine(-1f, 1.2f, 5f, 1.2f, paint)
            }
            canvas.restore()
        }

        // Rear foot first, front foot second, so the steps overlap naturally.
        drawLeg(side = -1, nearSide = false)
        drawLeg(side = 1, nearSide = true)
        paint.style = Paint.Style.FILL
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
        // The head leans into the swing while the blade is actually travelling.
        val headOffsetX = dir * (if (combat.isActive) 3.5f else 0f)
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
     * The player's sword: a short wrapped grip with a brass pommel, a crossguard, and a
     * straight double-edged pale-steel blade that tapers to a point.
     *
     * Every pose is one angle plus one extension around the hand, so the same drawing code
     * serves the held pose, the slash arc and the thrust. The frame is mirrored for facing,
     * and the blade is built into a cached [Path]: nothing is allocated per frame.
     */
    private fun drawSword(canvas: Canvas, paint: Paint, dir: Float) {
        val handX = x + dir * 9f
        val handY = y - height * 0.46f

        val ready = -30f
        var angle: Float
        var extend = 0f
        var arcFrom = 0f
        var arcSweep = 0f

        when (state) {
            PlayerState.SLASHING -> {
                when (combat.move) {
                    CombatMove.RISING_CUT -> {
                        when (combat.phase) {
                            AttackPhase.WINDUP -> {
                                val t = smooth(combat.phaseProgress)
                                angle = lerp(ready, 58f, t)
                                extend = -4f * t
                            }
                            AttackPhase.ACTIVE -> {
                                val t = easeOut(combat.phaseProgress)
                                angle = lerp(58f, -104f, t)
                                arcFrom = 58f
                                arcSweep = angle - 58f
                            }
                            else -> angle = lerp(-104f, ready, smooth(combat.phaseProgress))
                        }
                    }
                    CombatMove.DOWNSTRIKE -> {
                        when (combat.phase) {
                            AttackPhase.WINDUP -> {
                                val t = smooth(combat.phaseProgress)
                                angle = lerp(ready, 54f, t)
                                extend = 2f * t
                            }
                            AttackPhase.ACTIVE -> {
                                angle = lerp(54f, 78f, easeOut(combat.phaseProgress))
                                extend = lerp(2f, 12f, easeOut(combat.phaseProgress))
                            }
                            else -> {
                                val t = smooth(combat.phaseProgress)
                                angle = lerp(78f, ready, t)
                                extend = lerp(12f, 0f, t)
                            }
                        }
                    }
                    else -> when (combat.phase) {
                        AttackPhase.WINDUP -> {
                            val t = smooth(combat.phaseProgress)
                            angle = lerp(ready, SLASH_START, t)
                            extend = -3f * t
                        }
                        AttackPhase.ACTIVE -> {
                            val t = easeOut(combat.phaseProgress)
                            angle = lerp(SLASH_START, SLASH_END, t)
                            arcFrom = SLASH_START
                            arcSweep = angle - SLASH_START
                        }
                        else -> angle = lerp(SLASH_END, ready, smooth(combat.phaseProgress))
                    }
                }
            }
            PlayerState.STABBING -> {
                when (combat.phase) {
                    AttackPhase.WINDUP -> {
                        // Align the point at the target and draw the sword back.
                        val t = smooth(combat.phaseProgress)
                        angle = lerp(ready, THRUST_ANGLE, t)
                        extend = -11f * t
                    }
                    AttackPhase.ACTIVE -> {
                        // Drive the point straight forward.
                        angle = THRUST_ANGLE
                        extend = lerp(-11f, 21f, easeOut(combat.phaseProgress))
                    }
                    else -> {
                        val t = smooth(combat.phaseProgress)
                        angle = lerp(THRUST_ANGLE, ready, t)
                        extend = lerp(21f, 0f, t)
                    }
                }
            }
            else -> {
                // Held pose: low and ready, with a little life in it.
                val tilt = when (state) {
                    PlayerState.RUNNING -> sin(animationTime * 17f) * 5f
                    PlayerState.JUMPING -> -7f
                    PlayerState.FALLING -> 7f
                    PlayerState.DASHING -> 20f   // the blade streams back behind the dash
                    PlayerState.HEALING -> -52f  // held up out of the way while focusing
                    else -> sin(animationTime * 2.6f) * 2.5f
                }
                angle = ready + tilt
            }
        }

        canvas.save()
        canvas.translate(handX, handY)
        canvas.scale(dir, 1f)

        // Restrained motion trail, drawn only while the blade is actually sweeping.
        if (arcSweep != 0f) {
            arcRect.set(-ARC_RADIUS, -ARC_RADIUS, ARC_RADIUS, ARC_RADIUS)
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = 5.5f
            paint.color = Color.argb(115, 224, 238, 246)
            canvas.drawArc(arcRect, arcFrom, arcSweep, false, paint)
        }

        canvas.rotate(angle)
        canvas.translate(extend, 0f)

        // Wrapped grip.
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.BUTT
        paint.strokeWidth = 5.5f
        paint.color = Color.rgb(46, 40, 36)
        canvas.drawLine(-14f, 0f, 3f, 0f, paint)

        // Pommel.
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(196, 151, 78)
        canvas.drawCircle(-15.5f, 0f, 3.6f, paint)

        // Crossguard: the bar that makes the silhouette read as a sword at gameplay scale.
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 4.2f
        paint.color = Color.rgb(178, 138, 72)
        canvas.drawLine(5f, -9.5f, 5f, 9.5f, paint)

        // Straight double-edged blade tapering to a point.
        bladePath.reset()
        bladePath.moveTo(7f, -4.1f)
        bladePath.lineTo(36f, -3f)
        bladePath.lineTo(49f, 0f)
        bladePath.lineTo(36f, 3f)
        bladePath.lineTo(7f, 4.1f)
        bladePath.close()
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(176, 190, 198)
        canvas.drawPath(bladePath, paint)

        // Lit edge and fuller groove, so the blade has a spine instead of reading as a stick.
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 1.4f
        paint.color = Color.rgb(238, 245, 248)
        canvas.drawLine(10f, -2.3f, 45f, -0.4f, paint)
        paint.strokeWidth = 1f
        paint.color = Color.rgb(122, 136, 148)
        canvas.drawLine(11f, 1.6f, 40f, 0.7f, paint)

        canvas.restore()
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    /** Smooth in-and-out, used for anticipation and recovery. */
    private fun smooth(t: Float): Float = t * t * (3f - 2f * t)

    /** Fast out of the gate, used for the strike itself. */
    private fun easeOut(t: Float): Float = 1f - (1f - t) * (1f - t)

    private fun drawSpecialEffects(canvas: Canvas, paint: Paint) {
        paint.style = Paint.Style.STROKE

        // Impact spark where the blade connected: two short flecks, gone in a few frames.
        if (impactTimer > 0f) {
            val p = impactTimer / 0.12f
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = Color.argb((p * 210f).toInt().coerceIn(0, 210), 240, 248, 255)
            paint.strokeWidth = 2.4f
            val spread = (1f - p) * 9f
            canvas.drawLine(impactX - spread, impactY - spread, impactX - spread - 4f, impactY - spread - 4f, paint)
            canvas.drawLine(impactX + spread, impactY - spread * 0.5f, impactX + spread + 5f, impactY - spread * 0.5f - 2f, paint)
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

    companion object {
        /** Blade angles in degrees around the hand; negative is up, matching Canvas.rotate. */
        private const val SLASH_START = -122f  // raised back over the shoulder
        private const val SLASH_END = 54f      // finished low and forward
        private const val THRUST_ANGLE = -6f   // point level with the target
        private const val ARC_RADIUS = 47f     // matches the blade length so the trail hugs it
    }
}
