package com.devdgames.hollowknightbutmine.game.combat

/**
 * The player's whole combat move set, with no Android types, so the timings, damage and
 * hitbox windows can be unit-tested on the JVM exactly as the game runs them.
 *
 * The four-move sword kit is a quick slash, a committed thrust, a rising cut for aerial
 * targets, and an aerial downstrike that can bounce the knight off a successful hit.
 */
enum class CombatMove {
    /** Fast horizontal cut; the dependable all-purpose attack. */
    SLASH,

    /** A committed, long-reaching point-first thrust. */
    THRUST,

    /** A rising cut for enemies above or close to the knight. */
    RISING_CUT,

    /** Air-only downward strike; landing a hit rebounds the knight upward. */
    DOWNSTRIKE
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
    private val thrustSpec: MoveSpec = DEFAULT_THRUST,
    private val risingCutSpec: MoveSpec = DEFAULT_RISING_CUT,
    private val downstrikeSpec: MoveSpec = DEFAULT_DOWNSTRIKE
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

    fun specFor(move: CombatMove): MoveSpec = when (move) {
        CombatMove.SLASH -> slashSpec
        CombatMove.THRUST -> thrustSpec
        CombatMove.RISING_CUT -> risingCutSpec
        CombatMove.DOWNSTRIKE -> downstrikeSpec
    }

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
            active = 0.12f,
            recovery = 0.16f,
            damage = 20,
            reach = 84f,
            boxTopFraction = 0.75f,
            boxBottomFraction = 0.20f,
            knockbackX = 180f,
            knockbackY = -90f,
            soulGain = 11,
            travelScale = 0.72f
        )

        /** A vertical attack that trades horizontal mobility for reliable anti-air coverage. */
        val DEFAULT_RISING_CUT = MoveSpec(
            move = CombatMove.RISING_CUT,
            windup = 0.08f,
            active = 0.14f,
            recovery = 0.17f,
            damage = 17,
            reach = 62f,
            boxTopFraction = 0.96f,
            boxBottomFraction = 0.38f,
            knockbackX = 90f,
            knockbackY = -250f,
            soulGain = 10,
            travelScale = 0.50f
        )

        /** A committed aerial strike; the reward is a bounce only when the hit really lands. */
        val DEFAULT_DOWNSTRIKE = MoveSpec(
            move = CombatMove.DOWNSTRIKE,
            windup = 0.04f,
            active = 0.16f,
            recovery = 0.18f,
            damage = 20,
            reach = 58f,
            boxTopFraction = 0.92f,
            boxBottomFraction = 0.18f,
            knockbackX = 90f,
            knockbackY = 240f,
            soulGain = 12,
            travelScale = 0.35f
        )
    }
}
