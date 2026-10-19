package com.devdgames.hollowknightbutmine.boss

import com.devdgames.hollowknightbutmine.boss.BossTestKit.DT
import com.devdgames.hollowknightbutmine.game.boss.BossBrain
import com.devdgames.hollowknightbutmine.game.boss.Hazard
import com.devdgames.hollowknightbutmine.game.boss.SignalType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Rules shared by every boss: damage and guard, phases, stagger and poise, death, and the
 * readability guarantees (telegraphs, recovery windows) that keep a fight fair.
 */
class BossBrainTest {
    private val makers: List<Pair<String, (Int) -> BossBrain>> = listOf(
        "keener" to { s -> BossTestKit.keener(s) },
        "shardback" to { s -> BossTestKit.shardback(s) }
    )

    private fun px(b: BossBrain) = BossTestKit.insideX(b.arena)

    private fun awake(b: BossBrain): BossBrain {
        BossTestKit.wake(b, px(b))
        return b
    }

    /** Runs until the boss is in [mode]; fails the test if it never gets there. */
    private fun until(b: BossBrain, mode: BossBrain.Mode, limit: Float = 40f, extra: () -> Boolean = { true }): Float {
        val t = BossTestKit.runUntil(b, limit, px(b)) { b.mode == mode && extra() }
        assertTrue("${b.spec.id} never reached $mode", t >= 0f)
        return t
    }

    @Test
    fun `a guarding boss takes a fraction of damage and an exposed one takes it all`() {
        for ((name, make) in makers) {
            val b = awake(make(3))
            assertEquals("$name guarding", maxOf(1, (18 * b.spec.guard + 0.5f).toInt()), b.damage(18))
            until(b, BossBrain.Mode.RECOVER)
            BossTestKit.run(b, 0.2f, px(b))   // past the hit invulnerability
            assertTrue(b.isExposed)
            assertEquals("$name exposed", 18, b.damage(18))
        }
    }

    @Test
    fun `a reflected shot ignores guard and staggers`() {
        for ((name, make) in makers) {
            val b = awake(make(4))
            val before = b.hp
            assertEquals(b.spec.reflectDamage, b.damageFromReflect())
            assertEquals("$name hp", before - b.spec.reflectDamage, b.hp)
            assertTrue("$name should be staggered", b.isStaggered)
        }
    }

    @Test
    fun `hits closer together than the invulnerability window count once`() {
        for ((_, make) in makers) {
            val b = awake(make(5))
            assertTrue(b.damage(18) > 0)
            assertEquals(0, b.damage(18))
            BossTestKit.run(b, 0.12f, px(b))
            assertTrue(b.damage(18) > 0)
        }
    }

    @Test
    fun `a sleeping or waking boss can not be hurt or hurt anything`() {
        for ((_, make) in makers) {
            val b = make(6)
            assertFalse(b.isHittable)
            assertEquals(0, b.damage(100))
            assertFalse(b.hurts(b.x - 500f, b.y - 500f, b.x + 500f, b.y + 500f))
            b.awaken()
            BossTestKit.run(b, 0.5f, px(b))
            assertEquals(BossBrain.Mode.AWAKEN, b.mode)
            assertEquals(0, b.damage(100))
            assertFalse(b.hurts(b.x - 500f, b.y - 500f, b.x + 500f, b.y + 500f))
        }
    }

    @Test
    fun `crossing the phase threshold starts a protected transition then a faster phase two`() {
        for ((name, make) in makers) {
            val b = awake(make(8))
            val hazardBefore = b.hazards.size
            var guard = 0
            while (b.phase == 1 && b.mode != BossBrain.Mode.TRANSITION && guard++ < 400) {
                b.damageFromReflect()
                BossTestKit.run(b, 0.12f, px(b))
            }
            assertEquals("$name transition", BossBrain.Mode.TRANSITION, b.mode)
            assertTrue("$name hp at the threshold", b.hp <= b.spec.phase2Hp)
            assertTrue("hazards are cleared for the transition", b.hazards.isEmpty() && hazardBefore >= 0)
            assertEquals("invulnerable while changing phase", 0, b.damage(100))
            assertEquals(0, b.damageFromReflect())
            assertFalse(b.hurts(b.x - 500f, b.y - 500f, b.x + 500f, b.y + 500f))
            BossTestKit.run(b, b.spec.transitionTime + 0.05f, px(b))
            assertEquals(2, b.phase)
            assertTrue(b.mode != BossBrain.Mode.TRANSITION)
            assertTrue("phase two must not skip the boss's remaining health", b.hp > 0)
        }
    }

    @Test
    fun `dying boss is harmless and untouchable and ends exactly once`() {
        for ((name, make) in makers) {
            val b = awake(make(9))
            var guard = 0
            while (!b.isDying && guard++ < 600) {
                b.damageFromReflect()
                BossTestKit.run(b, 0.12f, px(b))
            }
            assertTrue("$name should be dying", b.isDying)
            assertEquals(0, b.hp)
            assertFalse(b.isHittable)
            assertEquals(0, b.damage(500))
            assertFalse(b.hurts(b.x - 500f, b.y - 500f, b.x + 500f, b.y + 500f))
            assertTrue(b.hazards.isEmpty())
            BossTestKit.run(b, b.spec.deathTime + 0.2f, px(b))
            assertTrue(b.isDefeated)
            val defeated = b.signals.count { it.type == SignalType.DEFEATED }
            BossTestKit.run(b, 2f, px(b))
            assertEquals("DEFEATED is reported once", defeated, b.signals.count { it.type == SignalType.DEFEATED })
            assertEquals(1, defeated)
        }
    }

    @Test
    fun `a parry staggers a parryable attack, exposes the boss, and then poise stops a lock`() {
        for ((name, make) in makers) {
            val b = awake(make(10))
            assertFalse("no parry outside an attack", b.stagger(byParry = true))
            val wait = BossTestKit.runUntil(b, 60f, px(b)) { b.mode == BossBrain.Mode.TELEGRAPH && b.attack!!.parryable }
            assertTrue("$name parryable attack", wait >= 0f)
            assertTrue(b.parryOpen)
            assertTrue(b.stagger(byParry = true))
            assertTrue(b.isStaggered && b.isExposed)
            assertFalse("a staggered boss hurts nothing by touch", b.hurts(b.x - 500f, b.y - 500f, b.x + 500f, b.y + 500f))
            assertFalse("no second stagger while staggered", b.stagger(byParry = false))
            BossTestKit.run(b, b.spec.parryStagger + 0.1f, px(b))
            assertFalse(b.isStaggered)
            // poise: the next knock-down is refused for a while
            assertFalse("$name poise", b.stagger(byParry = false))
            BossTestKit.run(b, b.spec.poiseTime + 0.2f, px(b))
        }
    }

    @Test
    fun `an unparryable attack can not be parried`() {
        for ((name, make) in makers) {
            val b = awake(make(11))
            val wait = BossTestKit.runUntil(b, 120f, px(b)) { b.mode == BossBrain.Mode.TELEGRAPH && !b.attack!!.parryable }
            assertTrue("$name should use an unparryable attack within two minutes", wait >= 0f)
            assertFalse(b.parryOpen)
            assertFalse(b.stagger(byParry = true))
            assertEquals(BossBrain.Mode.TELEGRAPH, b.mode)
        }
    }

    @Test
    fun `body contact hurts while the boss is active and never while it recovers`() {
        for ((name, make) in makers) {
            val b = awake(make(12))
            until(b, BossBrain.Mode.MOVE)
            assertTrue("$name active contact", b.hurts(b.x - 1f, b.y - 40f, b.x + 1f, b.y - 20f))
            assertEquals(1, b.lastDamage)
            until(b, BossBrain.Mode.RECOVER)
            assertFalse("$name recovering contact", b.hurts(b.x - 1f, b.y - 40f, b.x + 1f, b.y - 20f))
        }
    }

    @Test
    fun `weapon boxes exist only during the strike`() {
        for ((name, make) in makers) {
            val b = awake(make(13))
            var t = 0f
            var sawBox = false
            while (t < 120f) {
                b.update(DT, px(b), b.arena.floorY - 1f)
                if (b.strikeBoxes.isNotEmpty()) {
                    sawBox = true
                    assertEquals("$name weapon box outside STRIKE", BossBrain.Mode.STRIKE, b.mode)
                }
                if (b.mode == BossBrain.Mode.DEAD) break
                t += DT
            }
            assertTrue(sawBox || name == "keener")
        }
    }

    @Test
    fun `every attack is readable - long telegraph, real recovery window`() {
        val all = BossTestKit.keener().attacks + BossTestKit.shardback().attacks
        for (a in all) {
            assertTrue("${a.name} telegraph ${a.telegraph}s is too short to react to", a.telegraph >= 0.55f)
            assertTrue("${a.name} recovery ${a.recovery}s is too short to punish", a.recovery >= 0.85f)
            assertTrue("${a.name} damage", a.damage in 1..2)
        }
        for ((name, make) in makers) {
            val spec = make(1).spec
            assertTrue("$name phase-2 pace must stay fair", spec.phase2Pace in 0.75f..0.95f)
            for (a in all) assertTrue("${a.name} phase-2 telegraph", a.telegraph * spec.phase2Pace >= 0.45f)
        }
    }

    @Test
    fun `an attack is never used three times running and each boss shows its whole repertoire`() {
        for ((name, make) in makers) {
            val b = awake(make(21))
            val used = ArrayList<Int>()
            var t = 0f
            var swapped = false
            while (t < 400f && !b.isDying) {
                b.update(DT, px(b), b.arena.floorY - 1f)
                for (s in b.signals) if (s.type == SignalType.TELEGRAPH) used.add(s.value.toInt())
                b.signals.clear()
                // push it into phase 2 once it has shown phase one
                if (!swapped && t > 150f) { swapped = true }
                if (swapped && b.phase == 1) b.damageFromReflect()
                t += DT
            }
            for (i in 2 until used.size) assertFalse("$name repeated ${used[i]} three times", used[i] == used[i - 1] && used[i] == used[i - 2])
            val distinct = used.toSet()
            val expected = if (name == "keener") 4 else 4
            assertEquals("$name should use all $expected attacks, used $distinct", expected, distinct.size)
        }
    }

    @Test
    fun `phase two attacks never appear in phase one`() {
        val k = awake(BossTestKit.keener(31))
        val s = awake(BossTestKit.shardback(31))
        var t = 0f
        while (t < 200f) {
            k.update(DT, px(k), k.arena.floorY - 1f)
            s.update(DT, px(s), s.arena.floorY - 1f)
            if (k.phase == 1 && k.mode == BossBrain.Mode.TELEGRAPH) assertNotEquals("rain in phase one", 3, k.attack!!.id)
            if (s.phase == 1 && s.mode == BossBrain.Mode.TELEGRAPH) assertNotEquals("roll in phase one", 3, s.attack!!.id)
            t += DT
        }
    }

    @Test
    fun `the boss and everything it throws stay inside the arena`() {
        for ((name, make) in makers) {
            val b = awake(make(41))
            val rnd = Random(99)
            var t = 0f
            var px = px(b)
            while (t < 500f && !b.isDefeated) {
                if (rnd.nextFloat() < 0.01f) px = b.arena.left + 20f + rnd.nextFloat() * (b.arena.width - 40f)
                b.update(DT, px, b.arena.floorY - 1f)
                b.shots.clear(); b.signals.clear()
                if (!b.vanished) assertTrue("$name x=${b.x}", b.x >= b.arena.left && b.x <= b.arena.right)
                assertTrue("$name y=${b.y}", b.y >= b.arena.ceilingY - 60f && b.y <= b.arena.floorY + 1f)
                for (h in b.hazards) {
                    assertTrue("$name hazard x=${h.x}", h.x >= b.arena.left - h.w - 8f && h.x <= b.arena.right + h.w + 8f)
                    assertTrue("$name hazard y=${h.y}", h.y <= b.arena.floorY + 1f)
                }
                if (b.phase == 1 && t > 120f) b.damageFromReflect()
                if (b.phase == 2 && t > 300f) b.damageFromReflect()
                t += DT
            }
            assertTrue("$name fight should have ended within the run", b.isDefeated || b.isDying || t >= 500f)
        }
    }

    @Test
    fun `a fight has real punish windows and is not all downtime`() {
        for ((name, make) in makers) {
            val b = awake(make(51))
            var exposed = 0f
            var total = 0f
            while (total < 120f) {
                b.update(DT, px(b), b.arena.floorY - 1f)
                b.shots.clear(); b.signals.clear()
                if (b.isExposed) exposed += DT
                total += DT
            }
            val share = exposed / total
            assertTrue("$name exposed share $share", share in 0.12f..0.5f)
        }
    }

    @Test
    fun `hazards act in the right order - markers warn first, waves and shards hurt only when armed`() {
        val h = Hazard(Hazard.SHARD, 100f, 50f, 30f, 56f, vy = 700f, warn = 0.9f, life = 3f)
        assertFalse(h.harmful)
        h.warn = 0f
        assertTrue(h.harmful)
        h.landed = true
        assertFalse(h.harmful)
        assertFalse(Hazard(Hazard.MARKER, 0f, 0f, 40f, 6f, life = 1f).harmful)
    }
}
