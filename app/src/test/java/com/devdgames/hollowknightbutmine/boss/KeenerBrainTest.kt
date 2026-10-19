package com.devdgames.hollowknightbutmine.boss

import com.devdgames.hollowknightbutmine.boss.BossTestKit.DT
import com.devdgames.hollowknightbutmine.game.boss.BossBrain
import com.devdgames.hollowknightbutmine.game.boss.Hazard
import com.devdgames.hollowknightbutmine.game.boss.KeenerBrain
import com.devdgames.hollowknightbutmine.game.boss.Shot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The Keener's moves: timing, geometry and the guarantees that make them fair. */
class KeenerBrainTest {
    private fun fresh(seed: Int): KeenerBrain {
        val b = BossTestKit.keener(seed)
        BossTestKit.wake(b, BossTestKit.insideX(b.arena))
        return b
    }

    private fun px(b: KeenerBrain) = BossTestKit.insideX(b.arena)
    private fun floor(b: KeenerBrain) = b.arena.floorY - 1f

    /** Runs until the boss is in STRIKE of the attack called [name]. */
    private fun toStrike(b: KeenerBrain, name: String, x: Float = px(b), limit: Float = 240f) {
        val t = BossTestKit.runUntil(b, limit, x) { b.mode == BossBrain.Mode.STRIKE && b.attack!!.name == name }
        assertTrue("never struck with $name", t >= 0f)
    }

    private fun toPhaseTwo(b: KeenerBrain) {
        var guard = 0
        while (b.phase == 1 && guard++ < 500) {
            b.damageFromReflect()
            BossTestKit.run(b, 0.12f, px(b))
        }
        BossTestKit.run(b, b.spec.transitionTime + 0.2f, px(b))
        assertEquals(2, b.phase)
    }

    /** Collects every shot fired until the strike ends, with the time each was fired. */
    private fun collectShots(b: KeenerBrain, x: Float = px(b)): Pair<List<Shot>, List<Float>> {
        val shots = ArrayList<Shot>()
        val times = ArrayList<Float>()
        var clock = 0f
        // shots left over from earlier attacks are not part of this one
        b.shots.clear()
        while (b.mode == BossBrain.Mode.STRIKE) {
            b.update(DT, x, floor(b))
            for (s in b.shots) { shots.add(s); times.add(clock) }
            b.shots.clear()
            clock += DT
        }
        return shots to times
    }

    @Test
    fun `wail fires two slow shots at standing height toward the player`() {
        val b = fresh(61)
        toStrike(b, "wail")
        val dir = b.facing
        val (shots, times) = collectShots(b)
        assertEquals(2, shots.size)
        assertEquals((b.arena.floorY - KeenerBrain.LOW).toInt(), shots[0].y.toInt())
        assertEquals((b.arena.floorY - KeenerBrain.MID).toInt(), shots[1].y.toInt())
        for (s in shots) {
            assertEquals("shot direction", dir.toFloat(), Math.signum(s.vx), 0f)
            assertEquals(0f, s.vy, 0f)
            assertTrue("shots must be slow enough to read: ${s.vx}", abs(s.vx) <= 320f)
            assertEquals(9f, s.radius, 0f)
        }
        assertEquals("gap between shots", 0.55f, times[1] - times[0], 2.5f * DT)
    }

    @Test
    fun `every wail shot hits a standing player and reflects back into her body`() {
        for (phase2 in listOf(false, true)) {
            val b = fresh(if (phase2) 62 else 63)
            if (phase2) toPhaseTwo(b)
            toStrike(b, "wail")
            val (shots, _) = collectShots(b)
            assertTrue(shots.isNotEmpty())
            val playerTop = b.arena.floorY - 67.2f
            for (s in shots) {
                val top = s.y - s.radius
                val bottom = s.y + s.radius
                if (s.y > b.arena.floorY - KeenerBrain.HIGH + 1f) {
                    // low and mid shots must overlap a standing player's body
                    val overlap = minOf(bottom, b.arena.floorY) - maxOf(top, playerTop)
                    assertTrue("shot at ${b.arena.floorY - s.y} misses a standing player", overlap > 8f)
                }
                // a shot sent back along its line must cross the boss's body (checked where she stood)
                val bodyTop = b.y - b.bodyH
                val bodyBottom = b.y
                val cross = minOf(bottom, bodyBottom) - maxOf(top, bodyTop)
                assertTrue("shot at ${b.arena.floorY - s.y} would miss her on reflection (overlap $cross)", cross >= 8f)
            }
        }
    }

    @Test
    fun `phase two wail adds a third shot and speeds up`() {
        val b = fresh(64)
        toPhaseTwo(b)
        toStrike(b, "wail")
        val (shots, times) = collectShots(b)
        assertEquals(3, shots.size)
        assertEquals("gap", 0.42f, times[1] - times[0], 2.5f * DT)
        assertTrue(abs(shots[0].vx) > 300f && abs(shots[0].vx) <= 360f)
    }

    @Test
    fun `gale is a low wave that one jump clears and that is not too fast`() {
        val b = fresh(65)
        toStrike(b, "gale")
        val waves = b.hazards.filter { it.kind == Hazard.WAVE }
        assertEquals(1, waves.size)
        val w = waves[0]
        assertEquals("sits on the floor", b.arena.floorY, w.y, 0.01f)
        assertTrue("a single jump (apex 116px) must clear it: height ${w.h}", w.h in 60f..85f)
        assertTrue("wave speed ${w.vx}", abs(w.vx) in 350f..450f)
        assertEquals("travels the way she faces", b.facing.toFloat(), Math.signum(w.vx), 0f)
    }

    @Test
    fun `phase two gale sends a second wave back from the far wall`() {
        val b = fresh(66)
        toPhaseTwo(b)
        toStrike(b, "gale")
        BossTestKit.run(b, 0.7f, px(b))
        val waves = b.hazards.filter { it.kind == Hazard.WAVE && it.armed }
        assertEquals(2, waves.size)
        assertTrue("the two waves must come from opposite directions", waves[0].vx * waves[1].vx < 0f)
        assertTrue(waves.all { abs(it.vx) <= 520f })
    }

    @Test
    fun `veil drop marker follows the player, locks, and she lands on it`() {
        val b = fresh(67)
        val wait = BossTestKit.runUntil(b, 240f, px(b)) { b.mode == BossBrain.Mode.TELEGRAPH && b.attack!!.name == "veil drop" }
        assertTrue(wait >= 0f)
        val tele = b.attack!!.telegraph * (if (b.phase >= 2) b.spec.phase2Pace else 1f)
        val x1 = b.arena.left + 500f
        val x2 = b.arena.right - 400f
        // the marker is created immediately and tracks the player for the first 60% of the wind-up
        val marker = b.hazards.first { it.kind == Hazard.MARKER }
        BossTestKit.run(b, tele * 0.45f, x1)
        assertEquals("marker follows", x1, marker.x, 1f)
        BossTestKit.run(b, tele * 0.2f, x1)   // 65%: now locked on x1
        // after the lock the player moves away: the marker must not follow
        BossTestKit.run(b, tele * 0.1f, x2)
        val lockedAt = marker.x
        assertEquals("marker must stay where it locked", x1, lockedAt, 1f)
        // locked for at least 0.3s before she arrives
        assertTrue("lock time too short to step away: ${tele * 0.4f}", tele * 0.4f >= 0.3f)
        // she is gone and harmless while the marker waits
        BossTestKit.run(b, tele * 0.1f, x2)
        if (b.mode == BossBrain.Mode.TELEGRAPH) {
            assertFalse(b.isHittable)
            assertFalse("an invisible boss must not hurt", b.bodyHurtsNow)
            assertFalse(b.hurts(b.x - 500f, b.y - 500f, b.x + 500f, b.y + 500f))
        }
        val t = BossTestKit.runUntil(b, 3f, x2) { b.mode == BossBrain.Mode.STRIKE }
        assertTrue(t >= 0f)
        BossTestKit.run(b, 0.6f, x2)
        assertEquals("she lands on the locked marker", lockedAt, b.x, 2f)
        val waves = b.hazards.filter { it.kind == Hazard.WAVE }
        assertTrue("the landing sends a wave each way", waves.size >= 2 && waves.any { it.vx > 0 } && waves.any { it.vx < 0 })
        assertTrue(waves.all { it.h <= 45f })
    }

    @Test
    fun `a staggered drop brings her back into view`() {
        val b = fresh(68)
        // the drop is the only attack that is not parryable, so interrupt it with a grab
        val wait = BossTestKit.runUntil(b, 240f, px(b)) { b.mode == BossBrain.Mode.TELEGRAPH && b.attack!!.name == "veil drop" && b.telegraphProgress > 0.5f }
        assertTrue(wait >= 0f)
        assertFalse(b.isHittable)
        assertTrue(b.stagger(byParry = false))
        assertTrue("she reappears when staggered", !b.vanished)
        assertTrue(b.isExposed)
    }

    @Test
    fun `standing right beside her gets you dropped on, so she can never be cornered`() {
        var drops = 0
        val trials = 300
        for (seed in 0 until trials) {
            val b = BossTestKit.keener(seed)
            if (b.pickAttack(b.x + 100f, b.arena.floorY).name == "veil drop") drops++
        }
        assertTrue("only $drops of $trials", drops > trials * 0.55f)
        // and far away she does not
        var farDrops = 0
        for (seed in 0 until trials) {
            val b = BossTestKit.keener(seed)
            if (b.pickAttack(b.x - 600f, b.arena.floorY).name == "veil drop") farDrops++
        }
        assertTrue("far drops $farDrops", farDrops < trials * 0.4f)
    }

    @Test
    fun `mourning rain falls from the ceiling in a spread and only in phase two`() {
        val b = fresh(69)
        toPhaseTwo(b)
        toStrike(b, "mourning rain")
        val (shots, times) = collectShots(b)
        assertEquals(8, shots.size)
        val xs = shots.map { it.x }
        for (s in shots) {
            assertTrue("falls straight down", s.vx == 0f && s.vy > 0f && s.vy <= 260f)
            assertTrue("starts at the ceiling", abs(s.y - (b.arena.ceilingY + 8f)) < 1f)
            assertTrue("inside the hall", s.x >= b.arena.left && s.x <= b.arena.right)
        }
        assertTrue("tears should spread across the hall", xs.max() - xs.min() > 500f)
        assertTrue("tears arrive one after another", times.last() - times.first() > 0.7f)
    }

    @Test
    fun `her recovery after every attack leaves her exposed`() {
        val b = fresh(70)
        var t = 0f
        var exposedAfter = 0
        var attacks = 0
        var last = BossBrain.Mode.MOVE
        while (t < 200f) {
            b.update(DT, px(b), floor(b))
            b.shots.clear(); b.signals.clear()
            if (last == BossBrain.Mode.STRIKE && b.mode == BossBrain.Mode.RECOVER) {
                attacks++
                if (b.isExposed) exposedAfter++
            }
            last = b.mode
            t += DT
        }
        assertTrue(attacks > 10)
        assertEquals(attacks, exposedAfter)
    }

    @Test
    fun `she keeps her distance instead of crowding the player`() {
        val b = fresh(71)
        var sum = 0f
        var n = 0
        var t = 0f
        while (t < 120f) {
            b.update(DT, px(b), floor(b))
            b.shots.clear(); b.signals.clear()
            if (b.mode == BossBrain.Mode.MOVE) { sum += abs(b.x - px(b)); n++ }
            t += DT
        }
        assertTrue("average distance while moving: ${sum / n}", sum / n > 220f)
    }
}
