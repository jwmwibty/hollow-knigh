package com.devdgames.hollowknightbutmine.game.boss

import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/**
 * The fight logic of one boss, with no Android types: it owns the boss's body, hit points,
 * attack timeline, hazards and shots, and is driven one frame at a time by [update].
 *
 * Every attack follows the same readable rhythm:
 *
 *   MOVE -> TELEGRAPH (visible wind-up) -> STRIKE (the only time the attack hurts)
 *        -> RECOVER (exposed: this is the punish window) -> MOVE
 *
 * Outside the recovery window the boss guards and takes only a fraction of damage, so the
 * fight is won by learning attacks and striking after them, not by trading blows. A well
 * timed parry during a wind-up or strike staggers the boss; a stagger cannot be chained
 * (poise), so a boss can not be locked down.
 */
abstract class BossBrain(val spec: BossSpec, val arena: BossArena, seed: Int = 0x5EED) {

    enum class Mode { SLEEP, AWAKEN, MOVE, TELEGRAPH, STRIKE, RECOVER, STAGGER, TRANSITION, DYING, DEAD }

    protected val rng = Random(seed)

    // ---- body -----------------------------------------------------------------------
    /** Position of the feet (world px). */
    var x: Float = arena.spawnX
    var y: Float = arena.floorY

    /** +1 faces right, -1 faces left. */
    var facing: Int = -1
    open val bodyW: Float get() = 80f
    open val bodyH: Float get() = 100f

    /** The boss is out of sight and can neither hurt nor be hurt (for example while it teleports). */
    open val vanished: Boolean get() = false

    // ---- state ----------------------------------------------------------------------
    var hp: Int = spec.maxHp
        private set
    val maxHp: Int get() = spec.maxHp
    var phase: Int = 1
        private set
    var mode: Mode = Mode.SLEEP
        private set
    var modeTime: Float = 0f
        private set

    /** Seconds since the encounter began; drives idle animation. */
    var time: Float = 0f
        private set
    var attack: Attack? = null
        private set
    var hitFlash: Float = 0f
        private set

    /** Masks the most recent harmful contact should take. */
    var lastDamage: Int = 1
        private set

    private var invuln = 0f
    private var poise = 0f
    private var staggerLen = 0f
    private var moveDuration = 1f
    private var lastAttackId = -1
    private var repeatCount = 0

    /** Set by a subclass inside tickStrike to end the strike early. */
    protected var strikeDone = false

    // ---- output ---------------------------------------------------------------------
    val hazards = ArrayList<Hazard>()
    val shots = ArrayList<Shot>()
    val signals = ArrayList<BossSignal>()

    /** Harmful boxes of the boss's own weapon this frame (rebuilt every update). */
    val strikeBoxes = ArrayList<Box>()
    private val scratch = Box()

    // ---- queries --------------------------------------------------------------------
    val isDefeated: Boolean get() = mode == Mode.DEAD
    val isDying: Boolean get() = mode == Mode.DYING
    val isAwake: Boolean get() = mode != Mode.SLEEP && mode != Mode.AWAKEN
    val isStaggered: Boolean get() = mode == Mode.STAGGER

    /** The punish window: full damage is taken now. */
    val isExposed: Boolean get() = mode == Mode.RECOVER || mode == Mode.STAGGER

    /** A parry right now would interrupt the boss. */
    val parryOpen: Boolean
        get() = (mode == Mode.TELEGRAPH || mode == Mode.STRIKE) && attack?.parryable == true

    val isHittable: Boolean
        get() = !vanished && (mode == Mode.MOVE || mode == Mode.TELEGRAPH || mode == Mode.STRIKE ||
            mode == Mode.RECOVER || mode == Mode.STAGGER)

    val bodyHurtsNow: Boolean
        get() = !vanished && (mode == Mode.MOVE || mode == Mode.TELEGRAPH || mode == Mode.STRIKE)

    val telegraphProgress: Float
        get() {
            val a = attack
            return if (mode == Mode.TELEGRAPH && a != null) (modeTime / telegraphTime(a)).coerceIn(0f, 1f) else 0f
        }

    val awakenProgress: Float get() = if (mode == Mode.AWAKEN) (modeTime / spec.introTime).coerceIn(0f, 1f) else if (mode == Mode.SLEEP) 0f else 1f
    val deathProgress: Float get() = if (mode == Mode.DYING) (modeTime / spec.deathTime).coerceIn(0f, 1f) else if (mode == Mode.DEAD) 1f else 0f
    val healthFraction: Float get() = hp.toFloat() / spec.maxHp

    fun bodyBox(out: Box) = out.set(x - bodyW / 2f, y - bodyH, x + bodyW / 2f, y)

    // ---- hooks for the concrete boss ------------------------------------------------
    abstract fun pickAttack(px: Float, py: Float): Attack
    protected open fun tickSleep(dt: Float) {}
    protected open fun tickAwaken(dt: Float, progress: Float) {}
    protected abstract fun tickMove(dt: Float, px: Float, py: Float)
    protected open fun onTelegraph(a: Attack, px: Float, py: Float) {}
    protected open fun tickTelegraph(a: Attack, t: Float, dt: Float, px: Float, py: Float) {}
    protected open fun onStrike(a: Attack, px: Float, py: Float) {}
    protected open fun tickStrike(a: Attack, t: Float, dt: Float, px: Float, py: Float) {}
    protected open fun onRecover(a: Attack) {}
    protected open fun tickRecover(a: Attack, t: Float, dt: Float) {}
    protected open fun onTransition() {}
    protected open fun onPhase2() {}
    protected open fun onCancel() {}
    protected open fun onReset() {}
    protected open fun onHazardArmed(h: Hazard) {}
    /** Called at the end of every update, for per-frame body animation (hover, bob). */
    protected open fun afterUpdate(dt: Float) {}
    protected open fun restY(): Float = arena.floorY
    protected open val moveMin: Float get() = 0.7f
    protected open val moveMax: Float get() = 1.4f
    protected open fun activeTime(a: Attack): Float = a.active
    protected open fun recoveryTime(a: Attack): Float = a.recovery * pace
    protected open fun telegraphTime(a: Attack): Float = a.telegraph * pace

    /** The id of the attack before the current one, or -1. */
    protected val previousAttackId: Int get() = lastAttackId

    /** Phase 2 runs everything slightly faster. */
    protected val pace: Float get() = if (phase >= 2) spec.phase2Pace else 1f

    // ---- helpers for subclasses ----------------------------------------------------
    protected fun face(px: Float) {
        val dx = px - x
        if (abs(dx) > 12f) facing = if (dx > 0f) 1 else -1
    }

    protected fun signal(type: SignalType, sx: Float, sy: Float, value: Float = 0f) {
        if (signals.size < MAX_SIGNALS) signals.add(BossSignal(type, sx, sy, value))
    }

    protected fun addHazard(h: Hazard) {
        if (hazards.size < MAX_HAZARDS) hazards.add(h)
    }

    protected fun fire(sx: Float, sy: Float, vx: Float, vy: Float, radius: Float) {
        if (shots.size < MAX_SHOTS) shots.add(Shot(sx, sy, vx, vy, radius))
        signal(SignalType.SHOT, sx, sy)
    }

    /** Picks one of [options] by weight, but never the same attack three times running. */
    protected fun weighted(options: List<Attack>, weights: FloatArray): Attack {
        var total = 0f
        for (i in options.indices) {
            val a = options[i]
            total += if (a.id == lastAttackId && repeatCount >= 2) 0f else weights[i]
        }
        if (total <= 0f) return options[0]
        var r = rng.nextFloat() * total
        for (i in options.indices) {
            val a = options[i]
            val w = if (a.id == lastAttackId && repeatCount >= 2) 0f else weights[i]
            r -= w
            if (r <= 0f && w > 0f) return a
        }
        return options.last()
    }

    protected fun finishStrike() {
        strikeDone = true
    }

    // ---- lifecycle ------------------------------------------------------------------
    /** The player has entered the arena: wake up. */
    fun awaken() {
        if (mode != Mode.SLEEP) return
        enter(Mode.AWAKEN)
        signal(SignalType.AWAKEN, x, y - bodyH * 0.5f)
    }

    /** Back to sleep at full health, as if the fight had never started. */
    fun reset() {
        hp = spec.maxHp
        phase = 1
        mode = Mode.SLEEP
        modeTime = 0f
        time = 0f
        attack = null
        hitFlash = 0f
        invuln = 0f
        poise = 0f
        staggerLen = 0f
        strikeDone = false
        lastAttackId = -1
        repeatCount = 0
        moveDuration = 1f
        hazards.clear()
        shots.clear()
        signals.clear()
        strikeBoxes.clear()
        x = arena.spawnX
        y = restY()
        facing = -1
        onReset()
    }

    private fun enter(m: Mode) {
        mode = m
        modeTime = 0f
    }

    private fun enterMove() {
        enter(Mode.MOVE)
        moveDuration = (moveMin + rng.nextFloat() * (moveMax - moveMin)) * pace
        attack = null
    }

    fun update(dt: Float, px: Float, py: Float) {
        if (mode == Mode.DEAD) return
        time += dt
        modeTime += dt
        if (hitFlash > 0f) hitFlash -= dt
        if (invuln > 0f) invuln -= dt
        if (poise > 0f) poise -= dt
        updateHazards(dt)
        strikeBoxes.clear()

        when (mode) {
            Mode.SLEEP -> tickSleep(dt)
            Mode.AWAKEN -> {
                tickAwaken(dt, (modeTime / spec.introTime).coerceIn(0f, 1f))
                if (modeTime >= spec.introTime) enterMove()
            }
            Mode.MOVE -> {
                face(px)
                tickMove(dt, px, py)
                if (modeTime >= moveDuration) beginAttack(pickAttack(px, py), px, py)
            }
            Mode.TELEGRAPH -> {
                val a = attack!!
                tickTelegraph(a, modeTime, dt, px, py)
                if (modeTime >= telegraphTime(a)) {
                    enter(Mode.STRIKE)
                    strikeDone = false
                    signal(SignalType.STRIKE, x, y - bodyH * 0.5f, a.id.toFloat())
                    onStrike(a, px, py)
                }
            }
            Mode.STRIKE -> {
                val a = attack!!
                tickStrike(a, modeTime, dt, px, py)
                if (mode == Mode.STRIKE && (strikeDone || modeTime >= activeTime(a))) {
                    enter(Mode.RECOVER)
                    onRecover(a)
                }
            }
            Mode.RECOVER -> {
                val a = attack!!
                tickRecover(a, modeTime, dt)
                if (modeTime >= recoveryTime(a)) enterMove()
            }
            Mode.STAGGER -> {
                if (modeTime >= staggerLen) {
                    poise = spec.poiseTime
                    enterMove()
                }
            }
            Mode.TRANSITION -> {
                if (modeTime >= spec.transitionTime) {
                    phase = 2
                    onPhase2()
                    invuln = 0.4f
                    enterMove()
                }
            }
            Mode.DYING -> {
                if (modeTime >= spec.deathTime) {
                    mode = Mode.DEAD
                    signal(SignalType.DEFEATED, x, y - bodyH * 0.5f)
                }
            }
            Mode.DEAD -> {}
        }
        afterUpdate(dt)
        if (mode != Mode.DEAD) clampToArena()
    }

    private fun beginAttack(a: Attack, px: Float, py: Float) {
        attack = a
        if (a.id == lastAttackId) repeatCount++ else {
            lastAttackId = a.id
            repeatCount = 1
        }
        face(px)
        enter(Mode.TELEGRAPH)
        signal(SignalType.TELEGRAPH, x, y - bodyH * 0.5f, a.id.toFloat())
        onTelegraph(a, px, py)
    }

    private fun clampToArena() {
        val half = bodyW / 2f + 6f
        if (x < arena.left + half) x = arena.left + half
        if (x > arena.right - half) x = arena.right - half
    }

    private fun updateHazards(dt: Float) {
        var i = 0
        while (i < hazards.size) {
            val h = hazards[i]
            if (h.warn > 0f) {
                h.warn -= dt
                if (h.warn <= 0f) onHazardArmed(h)
            } else if (!h.landed) {
                h.x += h.vx * dt
                h.y += h.vy * dt
                h.life -= dt
                if (h.kind == Hazard.SHARD && h.y >= arena.floorY) {
                    h.y = arena.floorY
                    h.vy = 0f
                    h.landed = true
                    h.life = 0.35f
                    signal(SignalType.LAND, h.x, h.y)
                }
                if (h.kind == Hazard.WAVE && (h.x < arena.left - h.w || h.x > arena.right + h.w)) h.life = 0f
            } else {
                h.life -= dt
            }
            if (h.life <= 0f && h.warn <= 0f) hazards.removeAt(i) else i++
        }
    }

    // ---- damage ---------------------------------------------------------------------
    /** A hit from the player. Returns the damage actually dealt (0 when the boss can not be hurt now). */
    fun damage(amount: Int): Int {
        if (!isHittable || invuln > 0f) return 0
        val guarded = !isExposed
        val mult = if (guarded) spec.guard else 1f
        val dealt = max(1, (amount * mult + 0.5f).toInt())
        applyDamage(dealt, guarded)
        return dealt
    }

    /** A shot the player parried back at the boss: ignores guard and staggers. */
    fun damageFromReflect(): Int {
        if (!isHittable || invuln > 0f) return 0
        applyDamage(spec.reflectDamage, false)
        if (mode != Mode.DYING && mode != Mode.DEAD && mode != Mode.TRANSITION) stagger(byParry = false)
        return spec.reflectDamage
    }

    private fun applyDamage(dealt: Int, guarded: Boolean) {
        hp -= dealt
        invuln = 0.1f
        hitFlash = if (guarded) 0.06f else 0.14f
        signal(if (guarded) SignalType.GUARD else SignalType.HIT, x, y - bodyH * 0.5f, dealt.toFloat())
        if (hp <= 0) {
            hp = 0
            hazards.clear()
            strikeBoxes.clear()
            enter(Mode.DYING)
            signal(SignalType.DYING, x, y - bodyH * 0.5f)
        } else if (phase == 1 && hp <= spec.phase2Hp) {
            hazards.clear()
            strikeBoxes.clear()
            onCancel()
            enter(Mode.TRANSITION)
            signal(SignalType.PHASE, x, y - bodyH * 0.5f)
            onTransition()
        }
    }

    /**
     * Knocks the boss out of whatever it is doing. A parry only works on a parryable attack in
     * its wind-up or strike; a grab can stagger at any time. Neither works while the boss still
     * has poise from a recent stagger.
     */
    fun stagger(byParry: Boolean): Boolean {
        if (poise > 0f) return false
        val inAction = mode == Mode.TELEGRAPH || mode == Mode.STRIKE
        if (byParry) {
            if (!inAction || attack?.parryable != true) return false
        } else if (!(inAction || mode == Mode.MOVE || mode == Mode.RECOVER)) {
            return false
        }
        staggerLen = if (byParry) spec.parryStagger else spec.grabStagger
        onCancel()
        strikeBoxes.clear()
        enter(Mode.STAGGER)
        signal(SignalType.STAGGER, x, y - bodyH * 0.5f, if (byParry) 1f else 0f)
        return true
    }

    /** Does the boss hurt something occupying the given box right now? Sets [lastDamage]. */
    fun hurts(l: Float, t: Float, r: Float, b: Float): Boolean {
        var dmg = 0
        val a = attack
        if (bodyHurtsNow) {
            bodyBox(scratch)
            if (scratch.intersects(l, t, r, b)) dmg = max(dmg, if (mode == Mode.STRIKE && a != null) a.damage else 1)
        }
        if (mode == Mode.STRIKE && a != null) {
            for (box in strikeBoxes) if (box.intersects(l, t, r, b)) dmg = max(dmg, a.damage)
        }
        for (h in hazards) {
            if (!h.harmful) continue
            h.box(scratch)
            if (scratch.intersects(l, t, r, b)) dmg = max(dmg, h.damage)
        }
        if (dmg > 0) lastDamage = dmg
        return dmg > 0
    }

    companion object {
        const val MAX_HAZARDS = 48
        const val MAX_SHOTS = 48
        const val MAX_SIGNALS = 128
    }
}
