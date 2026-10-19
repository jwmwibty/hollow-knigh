package com.devdgames.hollowknightbutmine.game.boss

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * THE KEENER, mourner of the Vigil Hall.
 *
 * A veiled, weeping figure that hovers a hand's breadth above the floor and keeps its
 * distance. She fights with grief rather than blades:
 *
 *  - WAIL: two or three slow shots at chest height. They can be PARRIED BACK: a reflected
 *    shot ignores her guard, hurts badly and staggers her - the fight's central trick.
 *  - GALE: a low wall of wind sweeps across the hall; jump or dash through it.
 *  - VEIL DROP: she fades out, a marker appears under the player and locks, and she crashes
 *    down on it, throwing a small shockwave either way. Standing beside her is punished
 *    this way, so she can never be pinned in a corner.
 *  - MOURNING RAIN (phase 2): slow tears fall from the ceiling in a spread around the player;
 *    slip through a gap or reflect one back.
 *
 * Her recovery after each attack is the punish window.
 */
class KeenerBrain(spec: BossSpec, arena: BossArena, seed: Int = 0x4B33) : BossBrain(spec, arena, seed) {

    private val wail = Attack(WAIL, "wail", telegraph = 0.85f, active = 1.0f, recovery = 1.0f)
    private val gale = Attack(GALE, "gale", telegraph = 0.95f, active = 0.4f, recovery = 1.15f)
    private val drop = Attack(DROP, "veil drop", telegraph = 0.95f, active = 0.75f, recovery = 1.3f, parryable = false)
    private val ring = Attack(RING, "mourning rain", telegraph = 1.05f, active = 1.0f, recovery = 1.2f)
    val attacks: List<Attack> get() = listOf(wail, gale, drop, ring)

    override val bodyW: Float get() = 56f
    override val bodyH: Float get() = 112f

    /** Extra height above the resting hover; negative when she sinks (kneeling, asleep). */
    var lift = 0f
        private set

    /** 0 = fully visible, 1 = gone. */
    var fade = 0f
        private set
    private var hidden = false
    private var falling = false
    private var dropX = 0f
    private var marker: Hazard? = null
    private var shotIndex = 0
    private var secondGale = false
    private var rainCentre = 0f

    override val vanished: Boolean get() = hidden

    init {
        y = restY()
    }

    override fun restY(): Float = arena.floorY - HOVER

    // ---- choosing --------------------------------------------------------------------
    override fun pickAttack(px: Float, py: Float): Attack {
        // Right beside her: she slips away rather than being cornered.
        if (abs(px - x) < 190f && previousAttackId != DROP && rng.nextFloat() < 0.7f) return drop
        return if (phase >= 2) {
            weighted(listOf(wail, gale, drop, ring), floatArrayOf(0.25f, 0.25f, 0.2f, 0.3f))
        } else {
            weighted(listOf(wail, gale, drop), floatArrayOf(0.4f, 0.35f, 0.25f))
        }
    }

    // ---- moving ----------------------------------------------------------------------
    override fun tickMove(dt: Float, px: Float, py: Float) {
        // She stays on whichever side of the player she is on; having just landed on top of them
        // (or being level with them) she picks the side with more room, so she is never left
        // pinned between the player and a wall.
        val roomLeft = px - (arena.left + 90f)
        val roomRight = (arena.right - 90f) - px
        val side = if (abs(x - px) < 100f) (if (roomRight >= roomLeft) 1f else -1f) else (if (x > px) 1f else -1f)
        val prefer = if (phase >= 2) 300f else 340f
        val target = (px + side * prefer).coerceIn(arena.left + 90f, arena.right - 90f)
        val speed = if (phase >= 2) 175f else 135f
        x += (target - x).coerceIn(-speed * dt, speed * dt)
    }

    // ---- attacks ---------------------------------------------------------------------
    override fun onTelegraph(a: Attack, px: Float, py: Float) {
        if (a.id == DROP) {
            dropX = px.coerceIn(arena.left + 50f, arena.right - 50f)
            val m = Hazard(Hazard.MARKER, dropX, arena.floorY, 74f, 8f, life = telegraphTime(a) + 0.05f)
            marker = m
            addHazard(m)
        }
    }

    override fun tickTelegraph(a: Attack, t: Float, dt: Float, px: Float, py: Float) {
        if (a.id == DROP) {
            // the marker follows the player, then locks so there is time to step away
            if (t < telegraphTime(a) * 0.6f) {
                dropX = px.coerceIn(arena.left + 50f, arena.right - 50f)
                marker?.x = dropX
            }
        } else {
            face(px)
        }
    }

    override fun onStrike(a: Attack, px: Float, py: Float) {
        when (a.id) {
            WAIL -> shotIndex = 0
            GALE -> {
                secondGale = false
                val dir = facing.toFloat()
                addHazard(Hazard(Hazard.WAVE, x + dir * 40f, arena.floorY, 56f, 74f, vx = dir * galeSpeed(), life = 6f))
            }
            DROP -> {
                x = dropX
                y = arena.ceilingY - 40f
                hidden = false
                fade = 0f
                falling = true
            }
            RING -> {
                shotIndex = 0
                rainCentre = px
            }
        }
    }

    override fun tickStrike(a: Attack, t: Float, dt: Float, px: Float, py: Float) {
        when (a.id) {
            WAIL -> {
                val pattern = shotHeights()
                while (shotIndex < pattern.size && t >= shotIndex * shotGap()) {
                    val h = pattern[shotIndex]
                    val dir = facing.toFloat()
                    fire(x + dir * 34f, arena.floorY - h, dir * shotSpeed(), 0f, 9f)
                    shotIndex++
                }
            }
            RING -> {
                // tears fall one after another, in columns spread around where the player stood
                while (shotIndex < RAIN_COLUMNS && t >= shotIndex * RAIN_GAP) {
                    val spread = (shotIndex - (RAIN_COLUMNS - 1) / 2f) * RAIN_SPACING + (rng.nextFloat() - 0.5f) * 40f
                    val cx = (rainCentre + spread).coerceIn(arena.left + 30f, arena.right - 30f)
                    fire(cx, arena.ceilingY + 8f, 0f, RAIN_SPEED, 8f)
                    shotIndex++
                }
            }
            GALE -> {
                if (phase >= 2 && !secondGale && t >= 0.55f) {
                    secondGale = true
                    // a second wall of wind returns from the far side
                    val dir = -facing.toFloat()
                    val startX = if (dir > 0f) arena.left + 20f else arena.right - 20f
                    addHazard(Hazard(Hazard.WAVE, startX, arena.floorY, 56f, 74f, vx = dir * galeSpeed(), life = 6f))
                }
            }
            DROP -> if (falling) {
                y += 1500f * dt
                if (y >= arena.floorY - HOVER) {
                    y = arena.floorY - HOVER
                    falling = false
                    addHazard(Hazard(Hazard.WAVE, x, arena.floorY, 46f, 38f, vx = -380f, life = 0.55f))
                    addHazard(Hazard(Hazard.WAVE, x, arena.floorY, 46f, 38f, vx = 380f, life = 0.55f))
                    signal(SignalType.LAND, x, arena.floorY)
                }
            }
        }
    }

    override fun activeTime(a: Attack): Float = when (a.id) {
        WAIL -> (shotHeights().size - 1) * shotGap() + 0.3f
        GALE -> if (phase >= 2) 0.8f else 0.4f
        RING -> (RAIN_COLUMNS - 1) * RAIN_GAP + 0.25f
        else -> a.active
    }

    private fun shotHeights(): FloatArray = if (phase >= 2) floatArrayOf(LOW, HIGH, MID) else floatArrayOf(LOW, MID)
    private fun shotGap(): Float = if (phase >= 2) 0.42f else 0.55f
    private fun shotSpeed(): Float = if (phase >= 2) 340f else 300f
    private fun galeSpeed(): Float = if (phase >= 2) 480f else 400f

    override fun onRecover(a: Attack) {
        falling = false
    }

    override fun onCancel() {
        // a stagger or phase change mid-attack: she reappears where she stands
        marker?.life = 0f
        marker = null
        if (falling || hidden || fade > 0f) {
            falling = false
            hidden = false
            fade = 0f
            y = restY()
        }
    }

    override fun onReset() {
        lift = -(HOVER - 2f)
        fade = 0f
        hidden = false
        falling = false
        marker = null
        shotIndex = 0
        secondGale = false
        y = restY() - lift
    }

    // ---- body animation --------------------------------------------------------------
    override fun afterUpdate(dt: Float) {
        val a = attack
        var targetLift = 0f
        when (mode) {
            Mode.SLEEP -> targetLift = -(HOVER - 2f)
            Mode.AWAKEN -> targetLift = -(HOVER - 2f) * (1f - awakenProgress)
            Mode.TELEGRAPH -> {
                if (a?.id == DROP) {
                    fade = (telegraphProgress / 0.3f).coerceIn(0f, 1f)
                    hidden = fade >= 0.9f
                } else {
                    targetLift = 16f * telegraphProgress
                }
            }
            Mode.STRIKE -> if (a?.id != DROP) targetLift = 16f
            Mode.RECOVER -> targetLift = -14f
            Mode.STAGGER -> targetLift = -16f
            Mode.TRANSITION -> targetLift = 30f * sin(Math.PI.toFloat() * (modeTime / spec.transitionTime).coerceIn(0f, 1f))
            Mode.DYING -> targetLift = -(HOVER - 2f)
            else -> {}
        }
        lift += (targetLift - lift) * (1f - exp(-8f * dt))
        if (!falling) {
            val bob = if (mode == Mode.SLEEP || mode == Mode.DYING || mode == Mode.DEAD) 0f else 5f * sin(time * 2.2f)
            y = arena.floorY - HOVER - lift - bob
        }
    }

    companion object {
        const val WAIL = 0
        const val GALE = 1
        const val DROP = 2
        const val RING = 3

        /** Resting gap between her hem and the floor. */
        const val HOVER = 22f

        /**
         * Heights of her shots above the floor. A standing player is hit by LOW and MID, a jumper by
         * HIGH. All three sit inside her body's height, so a parried shot, which returns along the
         * line it came, always reaches her.
         */
        const val LOW = 42f
        const val MID = 62f
        const val HIGH = 104f

        private const val RAIN_COLUMNS = 8
        private const val RAIN_GAP = 0.13f
        private const val RAIN_SPACING = 98f
        private const val RAIN_SPEED = 235f
    }
}
