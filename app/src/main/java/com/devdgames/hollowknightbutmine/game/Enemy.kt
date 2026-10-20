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
 * Enemy entity with regular variations and UNIQUE MAIN BOSSES:
 * - Regular: CRAWLER, FLYER, SPITTER, SHIELDER, MOSS_CHARGER
 * - Chapter 1 Boss: BROODMOTHER (Giant aerial queen; 3-way acid barrage, dive bombs, airborne spore bursts)
 * - Chapter 2 Boss: OSSUARY_TITAN (Gigantic stone/bone monolith; seismic earth quakes, ground slam shockwaves, bone armor)
 * - Chapter 3 Boss: COLONNADE_KNIGHT (Royal duelist; safety pin lance thrust, teleport dash strike, parry stance)
 */
open class Enemy(
    var x: Float,
    var y: Float,
    var width: Float = 46f,
    var height: Float = 42f,
    val type: EnemyType = EnemyType.CRAWLER,
    val isBoss: Boolean = false
) {
    var vx = 0f
    var vy = 0f

    // HP based on enemy / boss type
    var maxHp = when (type) {
        EnemyType.BROODMOTHER -> 360
        EnemyType.OSSUARY_TITAN -> 480
        EnemyType.COLONNADE_KNIGHT -> 420
        EnemyType.MOSS_CHARGER -> if (isBoss) 300 else 100
        EnemyType.SHIELDER -> 95
        EnemyType.RUNEWEAVER -> 65
        EnemyType.LURKER -> 55
        EnemyType.SPITTER -> 45
        EnemyType.SWOOPER -> 40
        EnemyType.FLYER -> 35
        else -> 60
    }
    var hp = maxHp
    var isAlive = true

    var facing = Facing.LEFT
    var patrolSpeed = when (type) {
        EnemyType.BROODMOTHER -> 140f
        EnemyType.OSSUARY_TITAN -> 60f
        EnemyType.COLONNADE_KNIGHT -> 180f
        EnemyType.FLYER -> 110f
        EnemyType.SWOOPER -> 90f
        EnemyType.SHIELDER -> 45f
        EnemyType.RUNEWEAVER -> 50f
        EnemyType.SPITTER -> 35f
        EnemyType.MOSS_CHARGER -> 130f
        EnemyType.LURKER -> 0f
        else -> 70f
    }
    var patrolTimer = 0f
    var isGrounded = false

    // Combat state. `isStunned` is driven by a boss fight (see game/boss); ordinary enemies
    // are never stunned, because the player's only attacks are the slash and the stab.
    var isStunned = false
    /** Drives the stagger-star animation while [isStunned] is true. */
    var stunTimer = 0f
    var invincibilityTimer = 0f
    var flashTimer = 0f

    // Attack cooldown and telegraphing
    var attackCooldown = 1.2f
    var isAttacking = false
    var attackWindup = 0f

    // Boss & Flyer flight/phase variables
    private var hoverTimer = 0f
    private var isDiving = false
    var bossPhase = 1
    var bossSpecialTimer = 0f

    // Lurker ambush state
    var isHiding = (type == EnemyType.LURKER)
    var lurkerTimer = 0f

    // Swooper ceiling dive state
    var ceilingY = 0f
    var swooperState = 0 // 0 = hanging/tracking, 1 = diving, 2 = ground pause, 3 = ascending
    var swooperTimer = 0f

    // Spitter / Boss projectile callback (x, y, vx, vy)
    var onShootProjectile: ((x: Float, y: Float, vx: Float, vy: Float) -> Unit)? = null

    // Boss slam ground-shockwave callback (originX, originY, width, height)
    var onGroundSlam: ((x: Float, y: Float) -> Unit)? = null

    val hitbox = RectF()

    init {
        when (type) {
            EnemyType.BROODMOTHER -> {
                width = 82f
                height = 78f
            }
            EnemyType.OSSUARY_TITAN -> {
                width = 96f
                height = 104f
            }
            EnemyType.COLONNADE_KNIGHT -> {
                width = 54f
                height = 76f
            }
            EnemyType.MOSS_CHARGER -> {
                width = 68f
                height = 60f
            }
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
            EnemyType.LURKER -> {
                width = 48f
                height = 36f
            }
            EnemyType.SWOOPER -> {
                width = 36f
                height = 36f
            }
            EnemyType.RUNEWEAVER -> {
                width = 46f
                height = 58f
            }
            else -> {}
        }
    }

    open fun getBoundingBox(): RectF {
        hitbox.set(x - width / 2, y - height, x + width / 2, y)
        return hitbox
    }

    /**
     * True when this enemy hurts a player occupying [playerBox] right now. Ordinary enemies hurt
     * on any body contact; bosses add weapon boxes and hazards (see game/boss/BossEnemy).
     */
    open fun hurtsPlayer(playerBox: RectF): Boolean =
        if (isHiding) false else RectF.intersects(playerBox, getBoundingBox())

    /** Masks taken by the contact that [hurtsPlayer] just reported. */
    open val touchDamage: Int get() = 1

    open fun takeDamage(damage: Int, knockbackX: Float = 0f, knockbackY: Float = 0f, playEffect: (() -> Unit)? = null) {
        if (!isAlive || invincibilityTimer > 0f || isHiding) return

        // Shielder blocks frontal attacks; the counter is to strike it from behind.
        if (type == EnemyType.SHIELDER) {
            val attackFromFront = if (facing == Facing.RIGHT) knockbackX < 0 else knockbackX > 0
            if (attackFromFront && knockbackX != 0f) {
                flashTimer = 0.08f
                vx = if (facing == Facing.RIGHT) -40f else 40f
                return
            }
        }

        // Bosses take reduced knockback
        val kbFactor = if (isBoss || type == EnemyType.BROODMOTHER || type == EnemyType.OSSUARY_TITAN || type == EnemyType.COLONNADE_KNIGHT) 0.25f else 1.0f

        hp -= damage
        flashTimer = 0.15f
        invincibilityTimer = 0.10f // Keep hits distinct without swallowing fast follow-up attacks
        vx = knockbackX * kbFactor
        vy = knockbackY * kbFactor
        playEffect?.invoke()
        if (hp <= 0) {
            hp = 0
            isAlive = false
        }
    }

    open fun update(dt: Float, playerX: Float, playerY: Float, platforms: List<RectF>) {
        if (!isAlive) return

        if (invincibilityTimer > 0f) invincibilityTimer -= dt
        if (flashTimer > 0f) flashTimer -= dt

        // Stagger stars keep turning while a boss fight holds this enemy down.
        if (isStunned && stunTimer > 0f) stunTimer -= dt

        when (type) {
            EnemyType.BROODMOTHER -> updateBroodmotherAI(dt, playerX, playerY)
            EnemyType.OSSUARY_TITAN -> updateTitanAI(dt, playerX, playerY, platforms)
            EnemyType.COLONNADE_KNIGHT -> updateColonnadeKnightAI(dt, playerX, playerY, platforms)
            EnemyType.FLYER -> updateFlyerAI(dt, playerX, playerY)
            EnemyType.SPITTER -> updateSpitterAI(dt, playerX, playerY, platforms)
            EnemyType.SHIELDER -> updateShielderAI(dt, playerX, playerY, platforms)
            EnemyType.MOSS_CHARGER -> updateChargerAI(dt, playerX, playerY, platforms)
            EnemyType.LURKER -> updateLurkerAI(dt, playerX, playerY, platforms)
            EnemyType.SWOOPER -> updateSwooperAI(dt, playerX, playerY)
            EnemyType.RUNEWEAVER -> updateRuneweaverAI(dt, playerX, playerY, platforms)
            else -> updateCrawlerAI(dt, playerX, playerY, platforms)
        }
    }

    // =========================================================================
    // CHAPTER 1 BOSS: BROODMOTHER (Giant Aerial Queen)
    // Abilities:
    // 1. Triple Acid Volley (spread barrage)
    // 2. High-speed diagonal swoop dive
    // 3. Spore Cloud pulse
    // =========================================================================
    private fun updateBroodmotherAI(dt: Float, playerX: Float, playerY: Float) {
        hoverTimer += dt * 2.5f
        bossSpecialTimer += dt
        val dx = playerX - x
        val dy = playerY - y
        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
        facing = if (dx > 0) Facing.RIGHT else Facing.LEFT

        if (!isDiving) {
            // Hover menacingly in upper air above player
            val targetHoverY = playerY - 170f + sin(hoverTimer) * 35f
            val targetHoverX = playerX + (if (facing == Facing.RIGHT) -100f else 100f)

            vx += (targetHoverX - x) * 2.0f * dt
            vy += (targetHoverY - y) * 2.0f * dt

            attackCooldown -= dt
            if (attackCooldown <= 0f) {
                attackWindup += dt
                isAttacking = true

                if (attackWindup >= 0.7f) {
                    if (bossSpecialTimer > 4.5f) {
                        // Ability 1: Triple Acid Barrage!
                        bossSpecialTimer = 0f
                        val shootDir = if (facing == Facing.RIGHT) 1f else -1f
                        onShootProjectile?.invoke(x, y - height * 0.4f, shootDir * 340f, -40f)
                        onShootProjectile?.invoke(x, y - height * 0.4f, shootDir * 280f, 60f)
                        onShootProjectile?.invoke(x, y - height * 0.4f, shootDir * 320f, -120f)
                        attackCooldown = 2.4f
                        isAttacking = false
                        attackWindup = 0f
                    } else {
                        // Ability 2: Swoop Dive Bomb!
                        isDiving = true
                        val angle = kotlin.math.atan2(dy, dx)
                        vx = cos(angle) * 440f
                        vy = sin(angle) * 440f
                        attackCooldown = 2.2f
                    }
                }
            }
        } else {
            // In dive bomb
            attackCooldown -= dt
            if (attackCooldown < 1.2f) {
                isDiving = false
                isAttacking = false
                attackWindup = 0f
            }
        }

        x += vx * dt
        y += vy * dt
    }

    // =========================================================================
    // CHAPTER 2 BOSS: OSSUARY TITAN (Giant Bone Monolith)
    // Abilities:
    // 1. Heavy Monolith Ground Slam (screen shake & dual ground shockwaves)
    // 2. Chitin Shocker Rushes
    // 3. Bone Boulder Ejection
    // =========================================================================
    private fun updateTitanAI(dt: Float, playerX: Float, playerY: Float, platforms: List<RectF>) {
        val dx = playerX - x
        val dist = abs(dx)
        facing = if (dx > 0) Facing.RIGHT else Facing.LEFT
        bossSpecialTimer += dt

        if (dist < 550f) {
            attackCooldown -= dt

            if (attackCooldown <= 0f) {
                attackWindup += dt
                isAttacking = true

                if (attackWindup >= 1.0f) {
                    if (bossSpecialTimer > 4.0f) {
                        // Ability 1: EARTHQUAKE GROUND SLAM!
                        bossSpecialTimer = 0f
                        vy = -260f // Leap up, then slam down
                        onGroundSlam?.invoke(x, y)
                        // Eject two bone boulders horizontally
                        onShootProjectile?.invoke(x - 30f, y - 20f, -320f, -80f)
                        onShootProjectile?.invoke(x + 30f, y - 20f, 320f, -80f)
                    } else {
                        // Ability 2: Forward bone hammer strike
                        val chargeDir = if (facing == Facing.RIGHT) 1f else -1f
                        vx = chargeDir * 240f
                    }
                    attackCooldown = 2.5f
                    attackWindup = 0f
                    isAttacking = false
                }
            } else {
                // Relentless heavy march
                val targetVx = if (facing == Facing.RIGHT) patrolSpeed else -patrolSpeed
                vx += (targetVx - vx) * 3f * dt
            }
        } else {
            vx *= 0.85f
            isAttacking = false
        }

        applyPhysics(dt, platforms)
    }

    // =========================================================================
    // CHAPTER 3 BOSS: COLONNADE KNIGHT (Royal Lance Duelist)
    // Abilities:
    // 1. Lance Thrust (extended piercing dash)
    // 2. Phantom Teleport / Shadow Shift behind player
    // =========================================================================
    private fun updateColonnadeKnightAI(dt: Float, playerX: Float, playerY: Float, platforms: List<RectF>) {
        val dx = playerX - x
        val dist = abs(dx)
        facing = if (dx > 0) Facing.RIGHT else Facing.LEFT
        bossSpecialTimer += dt

        if (dist < 600f) {
            attackCooldown -= dt

            if (attackCooldown <= 0f) {
                attackWindup += dt
                isAttacking = true

                if (attackWindup >= 0.55f) {
                    if (bossSpecialTimer > 4.5f) {
                        // Ability 1: Phantom Shadow Shift (Teleport behind player!)
                        bossSpecialTimer = 0f
                        val shiftOffset = if (playerX > x) 120f else -120f
                        x = playerX + shiftOffset
                        facing = if (shiftOffset > 0) Facing.LEFT else Facing.RIGHT
                        // Instant follow-up thrust
                        val thrustDir = if (facing == Facing.RIGHT) 1f else -1f
                        vx = thrustDir * 420f
                    } else {
                        // Ability 2: Piercing Lance Lunge
                        val lungeDir = if (facing == Facing.RIGHT) 1f else -1f
                        vx = lungeDir * 380f
                    }
                    attackCooldown = 1.9f
                    attackWindup = 0f
                    isAttacking = false
                }
            } else {
                // Agile combat spacing
                val targetVx = if (facing == Facing.RIGHT) patrolSpeed * 1.4f else -patrolSpeed * 1.4f
                vx += (targetVx - vx) * 6f * dt
            }
        } else {
            vx *= 0.8f
        }

        applyPhysics(dt, platforms)
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
            if (!isDiving) {
                val targetHoverY = playerY - 140f
                val targetHoverX = playerX + (if (facing == Facing.RIGHT) -60f else 60f)
                vx += (targetHoverX - x) * 2.5f * dt
                vy += (targetHoverY - y) * 2.5f * dt

                attackCooldown -= dt
                if (attackCooldown <= 0f && dist < 240f) {
                    isDiving = true
                    isAttacking = true
                    val angle = kotlin.math.atan2(dy, dx)
                    vx = cos(angle) * 360f
                    vy = sin(angle) * 360f
                    attackCooldown = 2.2f
                }
            } else {
                attackCooldown -= dt
                if (attackCooldown < 1.4f) {
                    isDiving = false
                    isAttacking = false
                }
            }
        } else {
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
            if (dist < 180f) {
                val fleeVx = if (facing == Facing.RIGHT) -patrolSpeed else patrolSpeed
                vx += (fleeVx - vx) * 6f * dt
            } else {
                vx *= 0.8f
            }

            attackCooldown -= dt
            if (attackCooldown <= 0f) {
                attackWindup += dt
                isAttacking = true
                if (attackWindup >= 0.65f) {
                    val shootDir = if (facing == Facing.RIGHT) 1f else -1f
                    onShootProjectile?.invoke(x + shootDir * 20f, y - height * 0.6f, shootDir * 320f, -70f)
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
                isAttacking = true
                val chargeDir = if (facing == Facing.RIGHT) 1f else -1f
                vx = chargeDir * patrolSpeed * 2.2f

                if (attackWindup > 1.8f) {
                    attackWindup = 0f
                    isAttacking = false
                }
            } else {
                vx *= 0.85f
            }
        } else {
            attackWindup = 0f
            isAttacking = false
            vx *= 0.8f
        }

        applyPhysics(dt, platforms)
    }

    private fun updateLurkerAI(dt: Float, playerX: Float, playerY: Float, platforms: List<RectF>) {
        val dx = playerX - x
        val distH = abs(dx)
        val distV = abs(playerY - y)
        facing = if (dx > 0) Facing.RIGHT else Facing.LEFT

        if (isHiding) {
            vx = 0f
            vy = 0f
            if (distH < 120f && distV < 140f) {
                // Player stepped close: start unburrowing telegraph
                lurkerTimer += dt
                if (lurkerTimer >= 0.6f) {
                    // Emerge with biting burst!
                    isHiding = false
                    isAttacking = true
                    lurkerTimer = 0f
                    vy = -180f
                }
            } else {
                lurkerTimer = 0f
            }
        } else {
            lurkerTimer += dt
            if (isAttacking) {
                val lungeDir = if (facing == Facing.RIGHT) 1f else -1f
                vx = lungeDir * 120f
                if (lurkerTimer > 0.8f) {
                    isAttacking = false
                }
            } else {
                vx *= 0.8f
            }

            // After 2.5s emerged, burrow back into ground
            if (lurkerTimer >= 2.5f) {
                isHiding = true
                isAttacking = false
                lurkerTimer = 0f
                vx = 0f
                vy = 0f
            }
            applyPhysics(dt, platforms)
        }
    }

    private fun updateSwooperAI(dt: Float, playerX: Float, playerY: Float) {
        if (ceilingY == 0f) {
            ceilingY = y - height
        }
        val dx = playerX - x
        facing = if (dx > 0) Facing.RIGHT else Facing.LEFT

        when (swooperState) {
            0 -> {
                // Ceiling cling / track player
                isAttacking = false
                y = ceilingY + height
                val targetVx = if (abs(dx) > 20f) (if (dx > 0) patrolSpeed else -patrolSpeed) else 0f
                vx += (targetVx - vx) * 3f * dt
                x += vx * dt

                // Dive trigger: player directly underneath within 80px horizontal
                if (abs(dx) < 80f && playerY > y && (playerY - y) in 40f..380f && attackCooldown <= 0f) {
                    swooperState = 1
                    isAttacking = true
                    vy = 480f
                    vx = 0f
                }
                if (attackCooldown > 0f) attackCooldown -= dt
            }
            1 -> {
                // Diving downwards rapidly
                vy += 600f * dt
                if (vy > 560f) vy = 560f
                y += vy * dt
                if (y >= playerY - 4f) {
                    y = playerY
                    vy = 0f
                    swooperState = 2
                    swooperTimer = 0.5f // Pause on ground
                    isAttacking = false
                }
            }
            2 -> {
                vx = 0f
                swooperTimer -= dt
                if (swooperTimer <= 0f) {
                    swooperState = 3
                    vy = -200f
                }
            }
            3 -> {
                // Ascending back to ceiling
                vy = -200f
                y += vy * dt
                if (y <= ceilingY + height) {
                    y = ceilingY + height
                    vy = 0f
                    swooperState = 0
                    attackCooldown = 2.0f
                }
            }
        }
    }

    private fun updateRuneweaverAI(dt: Float, playerX: Float, playerY: Float, platforms: List<RectF>) {
        val dx = playerX - x
        val dist = abs(dx)
        facing = if (dx > 0) Facing.RIGHT else Facing.LEFT

        if (dist < 500f && abs(playerY - y) < 220f) {
            if (dist < 200f) {
                // Tactical retreat
                val fleeVx = if (facing == Facing.RIGHT) -patrolSpeed * 1.5f else patrolSpeed * 1.5f
                vx += (fleeVx - vx) * 6f * dt
            } else {
                vx *= 0.8f
            }

            attackCooldown -= dt
            if (attackCooldown <= 0f) {
                attackWindup += dt
                isAttacking = true
                if (attackWindup >= 0.7f) {
                    val shootDir = if (facing == Facing.RIGHT) 1f else -1f
                    // 3-shot arc pattern: angled up, straight, angled down
                    onShootProjectile?.invoke(x + shootDir * 20f, y - height * 0.6f, shootDir * 290f, -100f)
                    onShootProjectile?.invoke(x + shootDir * 20f, y - height * 0.6f, shootDir * 330f, -20f)
                    onShootProjectile?.invoke(x + shootDir * 20f, y - height * 0.6f, shootDir * 270f, 60f)

                    vx = -shootDir * 180f
                    vy = -120f
                    attackCooldown = 3.5f
                    attackWindup = 0f
                    isAttacking = false
                }
            }
        } else {
            isAttacking = false
            attackWindup = 0f
            vx *= 0.8f
        }

        applyPhysics(dt, platforms)
    }

    private fun applyPhysics(dt: Float, platforms: List<RectF>) {
        vy += 1200f * dt
        if (vy > 900f) vy = 900f

        // Ground walkers turn around at ledges instead of marching off into pits.
        if (isGrounded && abs(vx) > 1f) {
            val aheadX = x + (if (vx > 0) 1f else -1f) * (width / 2f + 6f)
            var groundAhead = false
            for (plat in platforms) {
                if (aheadX >= plat.left && aheadX <= plat.right && y + 6f >= plat.top && y - 6f <= plat.bottom) {
                    groundAhead = true
                    break
                }
            }
            if (!groundAhead) {
                vx = -vx
                facing = if (vx > 0) Facing.RIGHT else Facing.LEFT
                patrolTimer = 0f
            }
        }

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
                if (vy > 0 && vertBox.bottom - (vy * dt) <= plat.top + kotlin.math.max(10f, vy * dt + 2f)) {
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

    open fun draw(canvas: Canvas, paint: Paint) {
        if (!isAlive) return

        val dir = if (facing == Facing.RIGHT) 1f else -1f

        when (type) {
            EnemyType.BROODMOTHER -> drawBroodmother(canvas, paint, dir)
            EnemyType.OSSUARY_TITAN -> drawTitan(canvas, paint, dir)
            EnemyType.COLONNADE_KNIGHT -> drawColonnadeKnight(canvas, paint, dir)
            EnemyType.FLYER -> drawFlyer(canvas, paint, dir)
            EnemyType.SPITTER -> drawSpitter(canvas, paint, dir)
            EnemyType.SHIELDER -> drawShielder(canvas, paint, dir)
            EnemyType.MOSS_CHARGER -> drawCharger(canvas, paint, dir)
            EnemyType.LURKER -> drawLurker(canvas, paint, dir)
            EnemyType.SWOOPER -> drawSwooper(canvas, paint, dir)
            EnemyType.RUNEWEAVER -> drawRuneweaver(canvas, paint, dir)
            else -> drawCrawler(canvas, paint, dir)
        }

        drawHPBar(canvas, paint)
    }

    // =========================================================================
    // BOSS 1 DRAW: BROODMOTHER (Large Royal Hornet/Moth Matriarch)
    // =========================================================================
    private fun drawBroodmother(canvas: Canvas, paint: Paint, dir: Float) {
        val bodyColor = when {
            flashTimer > 0f -> Color.WHITE
            isStunned -> Color.rgb(255, 235, 59)
            isAttacking -> Color.rgb(255, 90, 90)
            else -> Color.rgb(42, 68, 56) // Royal toxic emerald
        }

        paint.style = Paint.Style.FILL
        paint.color = bodyColor

        // Huge bulbous acid abdomen
        val abY = y - height * 0.45f
        canvas.drawOval(x - 30f, abY - 26f, x + 30f, abY + 28f, paint)

        // Abdomen glowing segments
        paint.color = if (isAttacking) Color.rgb(255, 180, 50) else Color.rgb(96, 215, 160)
        canvas.drawCircle(x, abY, 14f, paint)
        canvas.drawCircle(x - dir * 16f, abY - 4f, 9f, paint)

        // Thorax & Crowned Mask
        paint.color = if (flashTimer > 0f) Color.WHITE else Color.rgb(238, 232, 222)
        canvas.drawCircle(x + dir * 18f, y - height * 0.72f, 18f, paint)

        // Regal Crown Horns
        val crown = Path()
        val cx = x + dir * 18f
        val cy = y - height * 0.88f
        crown.moveTo(cx - 16f, cy + 12f)
        crown.lineTo(cx - 24f, cy - 20f)
        crown.lineTo(cx - 6f, cy - 6f)
        crown.lineTo(cx, cy - 26f)
        crown.lineTo(cx + 6f, cy - 6f)
        crown.lineTo(cx + 24f, cy - 20f)
        crown.lineTo(cx + 16f, cy + 12f)
        crown.close()
        canvas.drawPath(crown, paint)

        // Multiple glowing orange queen eyes
        paint.color = Color.rgb(255, 110, 20)
        canvas.drawOval(cx + dir * 6f - 4f, cy + 14f, cx + dir * 6f + 4f, cy + 24f, paint)
        canvas.drawCircle(cx + dir * 12f, cy + 19f, 3.5f, paint)

        // Massive Buzzing Gossamer Wings
        paint.color = Color.argb(195, 210, 245, 235)
        val wingFlap = sin(hoverTimer * 14f) * 16f
        val wing1 = Path()
        wing1.moveTo(x, y - height * 0.75f)
        wing1.quadTo(x - dir * 55f, y - height - 35f + wingFlap, x - dir * 60f, y - height * 0.4f)
        wing1.close()
        canvas.drawPath(wing1, paint)
    }

    // =========================================================================
    // BOSS 2 DRAW: OSSUARY TITAN (Giant Stone & Ribcage Golem)
    // =========================================================================
    private fun drawTitan(canvas: Canvas, paint: Paint, dir: Float) {
        val bodyColor = when {
            flashTimer > 0f -> Color.WHITE
            isStunned -> Color.rgb(255, 235, 59)
            isAttacking -> Color.rgb(235, 80, 80)
            else -> Color.rgb(24, 30, 40) // Obsidian stone body
        }

        val box = getBoundingBox()
        paint.style = Paint.Style.FILL
        paint.color = bodyColor
        canvas.drawRoundRect(box, 16f, 16f, paint)

        // Huge Ribcage Carved Plate
        paint.color = if (flashTimer > 0f) Color.WHITE else Color.rgb(158, 186, 172)
        val ribY = y - height * 0.65f
        for (i in 0..4) {
            val ry = ribY + i * 15f
            canvas.drawRoundRect(x - 36f, ry, x + 36f, ry + 9f, 4f, 4f, paint)
        }

        // Titan Stone Skull
        val headY = y - height * 0.86f
        canvas.drawRoundRect(x - 22f, headY - 14f, x + 22f, headY + 14f, 8f, 8f, paint)

        // Single Giant Cyclopean Core Eye (glowing blue soul eye)
        paint.color = if (isAttacking) Color.rgb(255, 60, 60) else Color.rgb(80, 220, 255)
        canvas.drawCircle(x + dir * 6f, headY, 9f, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(x + dir * 6f, headY, 4f, paint)

        // Giant Bone Club / Hammer in hand
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 9f
        paint.color = Color.rgb(180, 165, 145)
        val clubX = x + dir * 42f
        val clubY = y - height * 0.45f
        canvas.drawLine(clubX - dir * 14f, clubY + 25f, clubX + dir * 28f, clubY - 35f, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(130, 115, 95)
        canvas.drawRoundRect(clubX + dir * 20f - 14f, clubY - 45f, clubX + dir * 20f + 14f, clubY - 15f, 6f, 6f, paint)
    }

    // =========================================================================
    // BOSS 3 DRAW: COLONNADE KNIGHT (Royal Lance Duelist)
    // =========================================================================
    private fun drawColonnadeKnight(canvas: Canvas, paint: Paint, dir: Float) {
        val bodyColor = when {
            flashTimer > 0f -> Color.WHITE
            isStunned -> Color.rgb(255, 235, 59)
            isAttacking -> Color.rgb(245, 210, 80)
            else -> Color.rgb(32, 28, 44) // Royal dark velvet
        }

        val box = getBoundingBox()
        paint.style = Paint.Style.FILL
        paint.color = bodyColor

        // Royal Flowing Cape
        val cape = Path()
        cape.moveTo(x - dir * 12f, y - height * 0.75f)
        cape.lineTo(x - dir * 38f, y - 4f)
        cape.lineTo(x, y - 2f)
        cape.close()
        paint.color = Color.rgb(75, 45, 95) // Purple mantle
        canvas.drawPath(cape, paint)

        // Slender Knight Body
        paint.color = bodyColor
        canvas.drawRoundRect(x - 14f, y - height * 0.72f, x + 14f, y - 8f, 6f, 6f, paint)

        // Golden Royal Chestpiece & Epaulets
        paint.color = Color.rgb(215, 175, 75)
        canvas.drawRoundRect(x - 16f, y - height * 0.72f, x + 16f, y - height * 0.5f, 4f, 4f, paint)

        // Elegant Mask with Feather Crest
        paint.color = if (flashTimer > 0f) Color.WHITE else Color.rgb(240, 238, 230)
        val headY = y - height * 0.85f
        canvas.drawOval(x - 13f, headY - 14f, x + 13f, headY + 14f, paint)

        // Dual Sharp Visor Eyes
        paint.color = Color.rgb(255, 190, 40)
        canvas.drawOval(x + dir * 4f - 6f, headY - 2f, x + dir * 4f, headY + 5f, paint)

        // Royal Piercing Greatlance (slender golden safety pin lance)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4.5f
        paint.color = Color.rgb(240, 220, 120)
        val lanceTipX = x + dir * (if (isAttacking) 85f else 48f)
        val lanceTipY = y - height * 0.5f
        canvas.drawLine(x, y - height * 0.45f, lanceTipX, lanceTipY, paint)

        // Ornate Lance Guard
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(195, 150, 50)
        canvas.drawCircle(x + dir * 20f, y - height * 0.46f, 7f, paint)
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

        paint.color = if (isStunned) Color.rgb(100, 200, 255) else Color.rgb(255, 120, 20)
        canvas.drawCircle(x + dir * 10f, y - height * 0.65f, 5f, paint)

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
            else -> Color.rgb(65, 45, 80)
        }

        paint.style = Paint.Style.FILL
        paint.color = bodyColor
        canvas.drawOval(x - 14f, y - height * 0.8f, x + 14f, y - 6f, paint)

        paint.color = Color.argb(190, 200, 225, 255)
        val wingFlap = sin(hoverTimer * 12f) * 8f
        val wing = Path()
        wing.moveTo(x, y - height * 0.7f)
        wing.quadTo(x - dir * 26f, y - height - 12f + wingFlap, x - dir * 28f, y - height * 0.6f)
        wing.close()
        canvas.drawPath(wing, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = if (isAttacking) Color.rgb(255, 80, 80) else Color.rgb(210, 200, 180)
        canvas.drawLine(x, y - height * 0.4f, x + dir * 22f, y - height * 0.2f, paint)

        paint.style = Paint.Style.FILL
        paint.color = if (isStunned) Color.rgb(100, 200, 255) else Color.rgb(255, 140, 20)
        canvas.drawCircle(x + dir * 6f, y - height * 0.65f, 4.5f, paint)
    }

    private fun drawSpitter(canvas: Canvas, paint: Paint, dir: Float) {
        val bodyColor = when {
            flashTimer > 0f -> Color.WHITE
            isStunned -> Color.rgb(255, 235, 59)
            isAttacking -> Color.rgb(255, 160, 40)
            else -> Color.rgb(38, 55, 40)
        }

        paint.style = Paint.Style.FILL
        paint.color = bodyColor
        val box = getBoundingBox()
        canvas.drawRoundRect(box, 16f, 16f, paint)

        val acidColor = if (isAttacking) Color.rgb(255, 220, 40) else Color.rgb(120, 210, 50)
        paint.color = acidColor
        canvas.drawCircle(x - dir * 6f, y - height * 0.5f, 10f, paint)

        paint.color = Color.rgb(25, 35, 25)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        canvas.drawLine(x, y - height * 0.6f, x + dir * 24f, y - height * 0.65f, paint)

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
        canvas.drawRoundRect(box.left + 8f, box.top, box.right - 8f, box.bottom, 10f, 10f, paint)

        val shieldX = if (facing == Facing.RIGHT) x + 10f else x - 24f
        paint.color = if (isStunned) Color.rgb(120, 110, 100) else Color.rgb(190, 180, 165)
        val shieldRect = RectF(shieldX, y - height - 4f, shieldX + 14f, y + 2f)
        canvas.drawRoundRect(shieldRect, 5f, 5f, paint)

        paint.color = Color.rgb(70, 60, 50)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        canvas.drawRoundRect(shieldRect, 5f, 5f, paint)
        canvas.drawLine(shieldRect.centerX(), shieldRect.top + 6f, shieldRect.centerX(), shieldRect.bottom - 6f, paint)

        paint.style = Paint.Style.FILL
        paint.color = if (isStunned) Color.rgb(100, 200, 255) else Color.rgb(255, 50, 50)
        canvas.drawRect(x - dir * 2f, y - height * 0.75f, x + dir * 6f, y - height * 0.70f, paint)
    }

    private fun drawCharger(canvas: Canvas, paint: Paint, dir: Float) {
        val bodyColor = when {
            flashTimer > 0f -> Color.WHITE
            isStunned -> Color.rgb(255, 235, 59)
            isAttacking -> Color.rgb(240, 70, 70)
            else -> Color.rgb(65, 80, 50)
        }

        paint.style = Paint.Style.FILL
        paint.color = bodyColor
        val box = getBoundingBox()
        canvas.drawRoundRect(box, 20f, 20f, paint)

        paint.color = Color.rgb(230, 225, 210)
        val tusk = Path()
        tusk.moveTo(x + dir * 18f, y - height * 0.4f)
        tusk.lineTo(x + dir * 42f, y - height * 0.65f)
        tusk.lineTo(x + dir * 20f, y - height * 0.2f)
        tusk.close()
        canvas.drawPath(tusk, paint)

        paint.color = Color.rgb(255, 120, 20)
        canvas.drawCircle(x + dir * 14f, y - height * 0.65f, 5f, paint)
        canvas.drawCircle(x + dir * 22f, y - height * 0.58f, 3.5f, paint)
    }

    private fun drawLurker(canvas: Canvas, paint: Paint, dir: Float) {
        if (isHiding) {
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(75, 55, 40)
            val mound = Path()
            mound.moveTo(x - 22f, y)
            val shake = if (lurkerTimer > 0f) (sin(lurkerTimer * 30f) * 2f) else 0f
            mound.quadTo(x + shake, y - 14f, x + 22f, y)
            mound.close()
            canvas.drawPath(mound, paint)

            paint.color = Color.rgb(105, 80, 60)
            canvas.drawCircle(x - 10f, y - 4f, 3f, paint)
            canvas.drawCircle(x + 12f, y - 3f, 2.5f, paint)

            if (lurkerTimer > 0.15f) {
                paint.color = Color.rgb(255, 60, 40)
                canvas.drawCircle(x - 5f + dir * 3f, y - 7f, 2.5f, paint)
                canvas.drawCircle(x + 5f + dir * 3f, y - 7f, 2.5f, paint)
            }
            return
        }

        val bodyColor = when {
            flashTimer > 0f -> Color.WHITE
            isStunned -> Color.rgb(255, 235, 59)
            isAttacking -> Color.rgb(230, 80, 50)
            else -> Color.rgb(90, 60, 45)
        }

        paint.style = Paint.Style.FILL
        paint.color = bodyColor
        val box = getBoundingBox()
        canvas.drawRoundRect(box, 14f, 14f, paint)

        paint.color = Color.rgb(60, 40, 30)
        for (i in 0..2) {
            val sx = x - 14f + i * 14f
            val sy = y - height * 0.85f
            val spike = Path()
            spike.moveTo(sx - 4f, sy + 6f)
            spike.lineTo(sx, sy - 8f)
            spike.lineTo(sx + 4f, sy + 6f)
            spike.close()
            canvas.drawPath(spike, paint)
        }

        paint.color = if (isAttacking) Color.rgb(255, 50, 30) else Color.rgb(255, 170, 40)
        canvas.drawCircle(x + dir * 12f, y - height * 0.65f, 4.5f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3.5f
        paint.color = Color.rgb(210, 190, 160)
        val jawOpen = if (isAttacking) 10f else 4f
        canvas.drawLine(x + dir * 16f, y - height * 0.45f - jawOpen, x + dir * 28f, y - height * 0.45f - 2f, paint)
        canvas.drawLine(x + dir * 16f, y - height * 0.45f + jawOpen, x + dir * 28f, y - height * 0.45f + 2f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawSwooper(canvas: Canvas, paint: Paint, dir: Float) {
        val bodyColor = when {
            flashTimer > 0f -> Color.WHITE
            isStunned -> Color.rgb(255, 235, 59)
            isAttacking -> Color.rgb(240, 70, 70)
            else -> Color.rgb(55, 40, 65)
        }

        paint.style = Paint.Style.FILL
        paint.color = bodyColor

        val cy = y - height * 0.5f
        canvas.drawOval(x - 12f, cy - 14f, x + 12f, cy + 14f, paint)

        val flared = swooperState == 1 || swooperState == 3
        paint.color = Color.rgb(75, 55, 85)
        val wing = Path()
        if (flared) {
            wing.moveTo(x, cy - 6f)
            wing.lineTo(x - 26f, cy - 22f)
            wing.lineTo(x - 14f, cy + 4f)
            wing.lineTo(x - 28f, cy + 8f)
            wing.lineTo(x - 6f, cy + 10f)
            wing.close()
            wing.moveTo(x, cy - 6f)
            wing.lineTo(x + 26f, cy - 22f)
            wing.lineTo(x + 14f, cy + 4f)
            wing.lineTo(x + 28f, cy + 8f)
            wing.lineTo(x + 6f, cy + 10f)
            wing.close()
        } else {
            wing.moveTo(x - 10f, cy - 10f)
            wing.lineTo(x - 18f, cy + 16f)
            wing.lineTo(x - 4f, cy + 12f)
            wing.close()
            wing.moveTo(x + 10f, cy - 10f)
            wing.lineTo(x + 18f, cy + 16f)
            wing.lineTo(x + 4f, cy + 12f)
            wing.close()
        }
        canvas.drawPath(wing, paint)

        paint.color = if (isAttacking) Color.rgb(255, 40, 40) else Color.rgb(255, 140, 60)
        canvas.drawCircle(x - 4f + dir * 2f, cy - 4f, 3f, paint)
        canvas.drawCircle(x + 4f + dir * 2f, cy - 4f, 3f, paint)

        if (swooperState == 1) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.5f
            paint.color = Color.rgb(220, 200, 160)
            canvas.drawLine(x - 6f, y - 4f, x - 10f, y + 4f, paint)
            canvas.drawLine(x + 6f, y - 4f, x + 10f, y + 4f, paint)
            paint.style = Paint.Style.FILL
        }
    }

    private fun drawRuneweaver(canvas: Canvas, paint: Paint, dir: Float) {
        val bodyColor = when {
            flashTimer > 0f -> Color.WHITE
            isStunned -> Color.rgb(255, 235, 59)
            isAttacking -> Color.rgb(120, 190, 255)
            else -> Color.rgb(36, 44, 66)
        }

        paint.style = Paint.Style.FILL
        paint.color = bodyColor
        val box = getBoundingBox()
        val robe = Path()
        robe.moveTo(x, box.top + 16f)
        robe.lineTo(box.right, box.bottom)
        robe.lineTo(box.left, box.bottom)
        robe.close()
        canvas.drawPath(robe, paint)

        paint.color = Color.rgb(48, 58, 86)
        canvas.drawCircle(x, box.top + 18f, 15f, paint)

        paint.color = Color.rgb(100, 220, 255)
        canvas.drawOval(x + dir * 4f - 5f, box.top + 16f, x + dir * 4f, box.top + 21f, paint)

        if (isAttacking || attackWindup > 0f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = Color.rgb(120, 220, 255)
            val glyphY = box.top - 12f
            canvas.drawCircle(x, glyphY, 8f, paint)
            canvas.drawLine(x - 6f, glyphY, x + 6f, glyphY, paint)
            canvas.drawLine(x, glyphY - 6f, x, glyphY + 6f, paint)
            paint.style = Paint.Style.FILL
        }

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.rgb(180, 160, 140)
        val staffX = x + dir * 18f
        canvas.drawLine(staffX, box.top + 8f, staffX, box.bottom, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(80, 200, 255)
        canvas.drawCircle(staffX, box.top + 6f, 5f, paint)
    }

    private fun drawHPBar(canvas: Canvas, paint: Paint) {
        if (isHiding) return
        val isMainBoss = isBoss || type == EnemyType.BROODMOTHER || type == EnemyType.OSSUARY_TITAN || type == EnemyType.COLONNADE_KNIGHT
        val barWidth = if (isMainBoss) width * 1.4f else width * 1.25f
        val barHeight = if (isMainBoss) 9f else 6f
        val barLeft = x - barWidth / 2
        val barTop = y - height - 16f

        paint.style = Paint.Style.FILL
        paint.color = Color.argb(180, 20, 20, 20)
        canvas.drawRect(barLeft, barTop, barLeft + barWidth, barTop + barHeight, paint)

        val hpPct = (hp.toFloat() / maxHp).coerceIn(0f, 1f)
        paint.color = when (type) {
            EnemyType.BROODMOTHER -> Color.rgb(80, 220, 160)
            EnemyType.OSSUARY_TITAN -> Color.rgb(220, 110, 80)
            EnemyType.COLONNADE_KNIGHT -> Color.rgb(255, 215, 60)
            EnemyType.MOSS_CHARGER -> Color.rgb(235, 70, 70)
            EnemyType.SHIELDER -> Color.rgb(200, 160, 60)
            EnemyType.SPITTER -> Color.rgb(100, 210, 60)
            EnemyType.FLYER -> Color.rgb(140, 120, 220)
            EnemyType.LURKER -> Color.rgb(160, 100, 60)
            EnemyType.SWOOPER -> Color.rgb(180, 80, 180)
            EnemyType.RUNEWEAVER -> Color.rgb(100, 160, 240)
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
