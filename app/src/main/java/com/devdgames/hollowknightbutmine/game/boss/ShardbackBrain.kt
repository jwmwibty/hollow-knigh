package com.devdgames.hollowknightbutmine.game.boss

import kotlin.math.abs
import kotlin.math.sin

/**
 * SHARDBACK, the geode beast of Glimmer Peak.
 *
 * A heavy crystal-backed beast that fights on the ground, with a shell that turns most blows
 * aside. Its core only opens when it is spent:
 *
 *  - ROCK CHARGE: it paws the ground, then charges the way it faces. Jump or dash through it
 *    and it ploughs into the wall, shaking loose a few ceiling shards (each shows a marker on
 *    the floor first) and standing stunned for a long punish window.
 *  - FACET SLAM: it rears and brings both forelegs down, with a short wave along the floor.
 *    A parry at the right moment staggers it.
 *  - SHARD RAIN: a roar loosens crystals above; markers show exactly where each lands.
 *  - GEODE ROLL (phase 2): it curls into a ball and ricochets off the walls twice.
 *
 * Attacks that end in exhaustion leave it exposed; everything else bounces off the shell.
 */
class ShardbackBrain(spec: BossSpec, arena: BossArena, seed: Int = 0x5AD0) : BossBrain(spec, arena, seed) {

    private val charge = Attack(CHARGE, "rock charge", telegraph = 0.95f, active = 3.2f, recovery = 1.7f)
    private val slam = Attack(SLAM, "facet slam", telegraph = 0.8f, active = 0.3f, recovery = 1.05f)
    private val rain = Attack(RAIN, "shard rain", telegraph = 0.8f, active = 0.3f, recovery = 0.9f, parryable = false)
    private val roll = Attack(ROLL, "geode roll", telegraph = 0.75f, active = 5.0f, recovery = 1.5f)
    val attacks: List<Attack> get() = listOf(charge, slam, rain, roll)

    var rolling = false
        private set

    /** Height of the ball above the floor while rolling. */
    var hop = 0f
        private set
    var crashed = false
        private set
    private var speedNow = 0f
    private var chargeDir = 1
    private var rollDir = 1
    private var bounces = 0

    /** Head down in a charge: the body is lower, so a jump clears it with room to spare. */
    private val charging: Boolean get() = mode == Mode.STRIKE && attack?.id == CHARGE

    override val bodyW: Float get() = if (rolling) 96f else 128f
    override val bodyH: Float get() = if (rolling) 96f else if (charging) 74f else 82f

    // ---- choosing --------------------------------------------------------------------
    override fun pickAttack(px: Float, py: Float): Attack {
        val near = abs(px - x) < 300f
        return if (phase >= 2) {
            val w = if (near) floatArrayOf(0.4f, 0.2f, 0.15f, 0.25f) else floatArrayOf(0.05f, 0.35f, 0.25f, 0.35f)
            weighted(listOf(slam, charge, rain, roll), w)
        } else {
            val w = if (near) floatArrayOf(0.5f, 0.3f, 0.2f) else floatArrayOf(0.1f, 0.5f, 0.4f)
            weighted(listOf(slam, charge, rain), w)
        }
    }

    // ---- moving ----------------------------------------------------------------------
    override fun tickMove(dt: Float, px: Float, py: Float) {
        val dx = px - x
        val speed = if (phase >= 2) 150f else 110f
        val sign = if (dx >= 0f) 1f else -1f
        if (abs(dx) > 340f) x += sign * speed * dt
        else if (abs(dx) < 220f) x -= sign * speed * 0.6f * dt
    }

    // ---- attacks ---------------------------------------------------------------------
    override fun tickTelegraph(a: Attack, t: Float, dt: Float, px: Float, py: Float) {
        // it keeps tracking the player until the very end of the wind-up, then commits
        face(px)
    }

    override fun onStrike(a: Attack, px: Float, py: Float) {
        when (a.id) {
            CHARGE -> {
                chargeDir = if (px >= x) 1 else -1
                facing = chargeDir
                speedNow = 0f
                crashed = false
            }
            SLAM -> {
                addHazard(
                    Hazard(Hazard.WAVE, x + facing * (bodyW / 2f + 10f), arena.floorY, 56f, 40f, vx = facing * 380f, life = 0.75f)
                )
            }
            RAIN -> rainShards(px)
            ROLL -> {
                rolling = true
                rollDir = if (px >= x) 1 else -1
                facing = rollDir
                bounces = 0
                crashed = false
            }
        }
    }

    override fun tickStrike(a: Attack, t: Float, dt: Float, px: Float, py: Float) {
        when (a.id) {
            CHARGE -> {
                speedNow = minOf(chargeTopSpeed(), speedNow + 2600f * dt)
                x += chargeDir * speedNow * dt
                val limit = bodyW / 2f + 6f
                if (x <= arena.left + limit || x >= arena.right - limit) {
                    x = x.coerceIn(arena.left + limit, arena.right - limit)
                    crash(if (phase >= 2) 5 else 3, px)
                    finishStrike()
                }
            }
            SLAM -> {
                if (t < 0.22f) {
                    val front = bodyW / 2f
                    val l = if (facing > 0) x + front - 20f else x - front - 170f
                    val r = if (facing > 0) x + front + 170f else x - front + 20f
                    strikeBoxes.add(Box(l, y - 78f, r, y))
                }
            }
            ROLL -> {
                x += rollDir * ROLL_SPEED * dt
                hop = abs(sin(t * 4.4f)) * 62f
                val limit = bodyW / 2f + 6f
                if (x <= arena.left + limit || x >= arena.right - limit) {
                    x = x.coerceIn(arena.left + limit, arena.right - limit)
                    rollDir = -rollDir
                    facing = rollDir
                    bounces++
                    signal(SignalType.WALL_CRASH, x, arena.floorY - bodyH / 2f)
                    dropShard(px + (rng.nextFloat() - 0.5f) * 260f, 0.9f)
                    if (bounces >= ROLL_BOUNCES) {
                        crashed = true
                        finishStrike()
                    }
                }
            }
        }
    }

    override fun onRecover(a: Attack) {
        rolling = false
        hop = 0f
        speedNow = 0f
    }

    override fun recoveryTime(a: Attack): Float =
        if (a.id == CHARGE && !crashed) 0.9f * pace else a.recovery * pace

    private fun chargeTopSpeed(): Float = if (phase >= 2) 610f else 520f

    private fun crash(shards: Int, px: Float) {
        crashed = true
        signal(SignalType.WALL_CRASH, x, arena.floorY - bodyH / 2f)
        val toward = if (chargeDir > 0) -1f else 1f
        for (i in 0 until shards) {
            val cx = x + toward * (70f + i * 95f + rng.nextFloat() * 50f)
            dropShard(cx, 0.85f + i * 0.14f)
        }
    }

    private fun rainShards(px: Float) {
        val n = if (phase >= 2) 8 else 5
        val columns = ArrayList<Float>()
        columns.add(px)
        var tries = 0
        while (columns.size < n && tries < 60) {
            tries++
            val sign = if (rng.nextBoolean()) 1f else -1f
            val c = (px + sign * (80f + rng.nextFloat() * 400f)).coerceIn(arena.left + 50f, arena.right - 50f)
            var ok = true
            for (e in columns) if (abs(e - c) < 80f) ok = false
            if (ok) columns.add(c)
        }
        for ((i, c) in columns.withIndex()) dropShard(c, 0.9f + i * 0.12f + rng.nextFloat() * 0.15f)
    }

    private fun dropShard(cx: Float, delay: Float) {
        val c = cx.coerceIn(arena.left + 50f, arena.right - 50f)
        addHazard(Hazard(Hazard.MARKER, c, arena.floorY, 44f, 6f, life = delay + 0.02f))
        addHazard(Hazard(Hazard.SHARD, c, arena.ceilingY + 56f, 30f, 56f, vy = 760f, warn = delay, life = 3f))
    }

    override fun onCancel() {
        rolling = false
        hop = 0f
        speedNow = 0f
    }

    override fun onReset() {
        rolling = false
        hop = 0f
        crashed = false
        speedNow = 0f
        bounces = 0
    }

    override fun afterUpdate(dt: Float) {
        y = arena.floorY - hop
    }

    companion object {
        const val CHARGE = 0
        const val SLAM = 1
        const val RAIN = 2
        const val ROLL = 3

        /** The ball rolls this fast and ends its attack after this many wall hits (about 4 s in all). */
        private const val ROLL_SPEED = 600f
        private const val ROLL_BOUNCES = 2
    }
}
