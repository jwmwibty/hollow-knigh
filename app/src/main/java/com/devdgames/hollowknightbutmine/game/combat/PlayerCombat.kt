package com.devdgames.hollowknightbutmine.game.combat

/**
 * The player's whole combat move set, with no Android types, so the timings, damage and
 * hitbox windows can be unit-tested on the JVM exactly as the game runs them.
 *
 * There are deliberately only two attacks: a light slash and a committed thrust. There is
 * no grab, no headbutt, no parry and no block anywhere in this model, and [CombatMove] is
 * the single list the input layer is allowed to trigger.
 */
enum class CombatMove {
    /** Light attack: a quick sword slash that sweeps a wide, short arc. */
    SLASH,

    /** The other core move: a straight forward thrust with the point of the sword. */
    THRUST
}

/** Where an attack is in its own timeline. The blade only hurts during [ACTIVE]. */
enum class AttackPhase { IDLE, WINDUP, ACTIVE, RECOVERY }

/**
 * One attack's timings and the damage it applies. `boxTopFraction` / `boxBottomFraction`
 * are fractions of the player's height measured up from the feet, so the hitbox follows the
 * player's size instead of hard-coded pixels.
 */
data class MoveSpec(
    val move: CombatMove,
    val windup: Float,
    val active: Float,
    val recovery: Float,
    val damage: Int,
    val reach: Float,
    val boxTopFraction: Float,
    val boxBottomFraction: Float,
    val knockbackX: Float,
    val knockbackY: Float,
    val soulGain: Int,
    /** Fraction of the run speed the player keeps while the attack plays out. */
    val travelScale: Float
) {
    /** Total time the attack occupies, anticipation through recovery. */
    val duration: Float get() = windup + active + recovery
}

/**
 * Drives the two attacks one frame at a time.
 *
 * An attack always plays WINDUP -> ACTIVE -> RECOVER. The damage window is exactly the
 * ACTIVE slice, so the sword is only dangerous while the blade is visibly travelling, and
 * the recovery tail can be cancelled into the next attack so the controls never feel locked.
 */
class PlayerCombat(
    private val slashSpec: MoveSpec = DEFAULT_SLASH,
    private val thrustSpec: MoveSpec = DEFAULT_THRUST
) {

    /** The attack currently playing, or null while the player is free. */
    var move: CombatMove? = null
        private set

    var phase: AttackPhase = AttackPhase.IDLE
        private set

    /** Seconds since the current attack started. */
    var elapsed = 0f
        private set

    /** Seconds since the current phase started. */
    var phaseTime = 0f
        private set

    /**
     * True once the current swing has connected. A swing hits at most one enemy, and this
     * stops the hitbox from being rebuilt for the rest of the ACTIVE window.
     */
    var connected = false
        private set

    /** Counts every attack started, so callers can tell one swing from the next. */
    var swings = 0
        private set

    private var spec: MoveSpec? = null

    /** The spec of the attack in progress, or null while idle. */
    val currentSpec: MoveSpec? get() = spec

    fun specFor(move: CombatMove): MoveSpec = if (move == CombatMove.SLASH) slashSpec else thrustSpec

    /**
     * An attack may be started from rest, or by cancelling the recovery tail of the previous
     * one. Wind-up and the strike itself cannot be interrupted, so an attack always reads
     * clearly, but recovery is short enough that the controls stay responsive.
     */
    val canAttack: Boolean get() = phase == AttackPhase.IDLE || phase == AttackPhase.RECOVERY

    /** The blade is live right now and has not hit anything yet. */
    val isActive: Boolean get() = phase == AttackPhase.ACTIVE

    /** 0..1 through the whole current attack. */
    val progress: Float
        get() {
            val s = spec ?: return 0f
            return (elapsed / s.duration).coerceIn(0f, 1f)
        }

    /** 0..1 through the current phase; drives the animation of that phase. */
    val phaseProgress: Float
        get() {
            val s = spec ?: return 0f
            val len = when (phase) {
                AttackPhase.WINDUP -> s.windup
                AttackPhase.ACTIVE -> s.active
                AttackPhase.RECOVERY -> s.recovery
                AttackPhase.IDLE -> return 0f
            }
            if (len <= 0f) return 1f
            return (phaseTime / len).coerceIn(0f, 1f)
        }

    /**
     * Starts [requested] if the player is free to attack. Returns false when the request is
     * ignored (mid wind-up or mid strike), so the caller can leave the player's state alone.
     */
    fun begin(requested: CombatMove): Boolean {
        if (!canAttack) return false
        val s = specFor(requested)
        move = requested
        spec = s
        phase = AttackPhase.WINDUP
        elapsed = 0f
        phaseTime = 0f
        connected = false
        swings++
        return true
    }

    /** Records that the live blade hit something, ending this swing's damage window. */
    fun onHit() {
        connected = true
    }

    /** Ends the current attack immediately (used when the player is hurt, dies or respawns). */
    fun cancel() {
        move = null
        spec = null
        phase = AttackPhase.IDLE
        elapsed = 0f
        phaseTime = 0f
        connected = false
    }

    fun update(dt: Float) {
        val s = spec
        if (s == null || phase == AttackPhase.IDLE) return
        elapsed += dt
        phaseTime += dt

        val limit = when (phase) {
            AttackPhase.WINDUP -> s.windup
            AttackPhase.ACTIVE -> s.active
            AttackPhase.RECOVERY -> s.recovery
            AttackPhase.IDLE -> return
        }
        if (phaseTime < limit) return

        // Advance to the next phase, carrying the overshoot across so timing stays exact.
        val carry = phaseTime - limit
        phaseTime = carry
        phase = when (phase) {
            AttackPhase.WINDUP -> AttackPhase.ACTIVE
            AttackPhase.ACTIVE -> AttackPhase.RECOVERY
            else -> {
                cancel()
                return
            }
        }
    }

    companion object {
        /**
         * Light attack. Fast and wide: a short anticipation, a brief but generous arc that
         * covers high and low, and a short recovery that can be cancelled into another swing.
         */
        val DEFAULT_SLASH = MoveSpec(
            move = CombatMove.SLASH,
            windup = 0.06f,
            active = 0.09f,
            recovery = 0.12f,
            damage = 14,
            reach = 66f,
            boxTopFraction = 0.86f,
            boxBottomFraction = 0.12f,
            knockbackX = 120f,
            knockbackY = -60f,
            soulGain = 9,
            travelScale = 0.85f
        )

        /**
         * The thrust. Keeps the damage, reach, hitbox height and knockback the stab has always
         * had; only the timing changed, so the hitbox now exists just while the point is
         * actually driving forward instead of for the whole swing.
         */
        val DEFAULT_THRUST = MoveSpec(
            move = CombatMove.THRUST,
            windup = 0.08f,
            active = 0.10f,
            recovery = 0.13f,
            damage = 18,
            reach = 72f,
            boxTopFraction = 0.75f,
            boxBottomFraction = 0.20f,
            knockbackX = 160f,
            knockbackY = -80f,
            soulGain = 11,
            travelScale = 0.75f
        )
    }
}
