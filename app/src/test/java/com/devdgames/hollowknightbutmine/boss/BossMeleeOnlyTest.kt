package com.devdgames.hollowknightbutmine.boss

import com.devdgames.hollowknightbutmine.boss.BossTestKit.DT
import com.devdgames.hollowknightbutmine.game.boss.BossBrain
import com.devdgames.hollowknightbutmine.game.combat.CombatMove
import com.devdgames.hollowknightbutmine.game.combat.PlayerCombat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The player's move set is now only a light slash and a thrust (see combat/PlayerCombat.kt).
 * The grab and the parry that used to stagger a boss are gone, so nothing forces a fight open
 * any more: every hit has to land in a window the boss leaves on its own.
 *
 * These tests drive the real [BossBrain] exactly the way GameWorld does — one
 * `brain.damage()` per landed swing, at a human-plausible swing rate, and only while the boss
 * is exposed — and assert both bosses still die. If a future boss ever needs a removed move,
 * this fails before it ships.
 */
class BossMeleeOnlyTest {
    private val slash = PlayerCombat.DEFAULT_SLASH
    private val thrust = PlayerCombat.DEFAULT_THRUST

    /** A swing, a beat of reaction, and the next swing: roughly how fast a player can cycle. */
    private val swingEvery = slash.duration + 0.12f

    /** Generous: a boss that needs a parry would simply never die inside this. */
    private val limit = 240f

    private val makers: List<Pair<String, (Int) -> BossBrain>> = listOf(
        "keener" to { s -> BossTestKit.keener(s) },
        "shardback" to { s -> BossTestKit.shardback(s) }
    )

    /**
     * Fights [make] using only the two attacks the player still has. Returns a pair of
     * (seconds to defeat, hits landed); seconds is -1f if the boss survived.
     */
    private fun fightWithMeleeOnly(make: (Int) -> BossBrain, seed: Int, damagePerHit: Int): Pair<Float, Int> {
        val b = make(seed)
        val px = BossTestKit.insideX(b.arena)
        val py = b.arena.floorY - 1f
        BossTestKit.wake(b, px)

        var t = 0f
        var cooldown = 0f
        var hits = 0
        while (!b.isDefeated && t < limit) {
            if (cooldown <= 0f && b.isExposed && b.isHittable) {
                if (b.damage(damagePerHit) > 0) hits++
                cooldown = swingEvery
            }
            b.update(DT, px, py)
            t += DT
            cooldown -= DT
        }
        return (if (b.isDefeated) t else -1f) to hits
    }

    @Test
    fun `both bosses die to the light attack alone`() {
        assertEquals(CombatMove.SLASH, slash.move)
        for ((name, make) in makers) {
            for (seed in listOf(1, 7, 23)) {
                val (seconds, hits) = fightWithMeleeOnly(make, seed, slash.damage)
                assertTrue(
                    "$name (seed $seed) survived $limit s of light attacks alone: $hits hits landed",
                    seconds >= 0f
                )
                assertTrue("$name should have been hit more than a handful of times", hits > 5)
            }
        }
    }

    @Test
    fun `both bosses die to the stab alone`() {
        assertEquals(CombatMove.THRUST, thrust.move)
        for ((name, make) in makers) {
            val (seconds, hits) = fightWithMeleeOnly(make, 7, thrust.damage)
            assertTrue("$name survived $limit s of thrusts alone: $hits hits landed", seconds >= 0f)
        }
    }

    @Test
    fun `the exposed windows come from the boss itself, not from a removed player move`() {
        for ((name, make) in makers) {
            val b = make(5)
            val px = BossTestKit.insideX(b.arena)
            val py = b.arena.floorY - 1f
            BossTestKit.wake(b, px)

            // Only ever advance time: no stagger, no reflect, no grab is ever applied.
            var t = 0f
            var exposed = 0f
            while (t < 20f) {
                if (b.isExposed) exposed += DT
                b.update(DT, px, py)
                t += DT
            }
            assertTrue(
                "$name left only ${exposed}s of punish window in 20s of untouched fighting",
                exposed > 3f
            )
        }
    }
}
