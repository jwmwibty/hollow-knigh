package com.devdgames.hollowknightbutmine.boss

import com.devdgames.hollowknightbutmine.boss.BossTestKit.DT
import com.devdgames.hollowknightbutmine.game.boss.BossBrain
import com.devdgames.hollowknightbutmine.game.boss.Hazard
import com.devdgames.hollowknightbutmine.game.boss.ShardbackBrain
import com.devdgames.hollowknightbutmine.game.boss.SignalType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Shardback's moves: the charge-and-crash loop, the slam window, shard rain and the geode roll. */
class ShardbackBrainTest {
    private fun fresh(seed: Int): ShardbackBrain {
        val b = BossTestKit.shardback(seed)
        BossTestKit.wake(b, BossTestKit.insideX(b.arena))
        return b
    }

    private fun floor(b: ShardbackBrain) = b.arena.floorY - 1f

    private fun toStrike(b: ShardbackBrain, name: String, x: Float, limit: Float = 300f) {
        val t = BossTestKit.runUntil(b, limit, x) { b.mode == BossBrain.Mode.STRIKE && b.attack!!.name == name }
        assertTrue("never struck with $name", t >= 0f)
    }

    private fun toPhaseTwo(b: ShardbackBrain) {
        var guard = 0
        while (b.phase == 1 && guard++ < 500) {
            b.damageFromReflect()
            BossTestKit.run(b, 0.12f, BossTestKit.insideX(b.arena))
        }
        BossTestKit.run(b, b.spec.transitionTime + 0.2f, BossTestKit.insideX(b.arena))
        assertEquals(2, b.phase)
    }

    @Test
    fun `charge commits toward the player's side, runs to the wall and crashes`() {
        for (leftSide in listOf(true, false)) {
            val b = fresh(if (leftSide) 81 else 82)
            val px = if (leftSide) b.arena.left + 300f else b.arena.right - 120f
            // make sure the boss is on the other side of the player
            b.x = if (leftSide) b.arena.right - 400f else b.arena.left + 400f
            toStrike(b, "rock charge", px)
            assertEquals(if (leftSide) -1 else 1, b.facing)
            var last = b.x
            var maxStep = 0f
            while (b.mode == BossBrain.Mode.STRIKE) {
                b.update(DT, px, floor(b))
                val step = (b.x - last) / DT
                assertTrue("charge must not reverse", if (leftSide) step <= 1f else step >= -1f)
                maxStep = maxOf(maxStep, abs(step))
                last = b.x
            }
            assertTrue("it must have crashed into the wall", b.crashed)
            val wallX = if (leftSide) b.arena.left + b.bodyW / 2f + 6f else b.arena.right - b.bodyW / 2f - 6f
            assertEquals(wallX, b.x, 2f)
            assertTrue("top speed $maxStep must stay dodgeable", maxStep <= 560f)
            assertTrue(b.isExposed)
            assertTrue(b.signals.any { it.type == SignalType.WALL_CRASH })
        }
    }

    @Test
    fun `a crash drops three marked shards toward the middle of the hall`() {
        val b = fresh(83)
        val px = b.arena.left + 300f
        b.x = b.arena.right - 400f
        toStrike(b, "rock charge", px)
        BossTestKit.runUntil(b, 10f, px) { b.mode == BossBrain.Mode.RECOVER }
        val shards = b.hazards.filter { it.kind == Hazard.SHARD }
        val markers = b.hazards.filter { it.kind == Hazard.MARKER }
        assertEquals(3, shards.size)
        for (s in shards) {
            assertTrue("shard inside the hall", s.x > b.arena.left && s.x < b.arena.right)
            assertTrue("shards fall toward the interior, away from the crash wall", s.x > b.x + 60f)
            assertTrue("a shard needs at least 0.8s of warning but had ${s.warn}", s.warn >= 0.8f)
            val m = markers.firstOrNull { abs(it.x - s.x) < 0.5f }
            assertTrue("every shard has a marker under it", m != null)
            assertTrue("the marker outlasts the warning", m!!.life >= s.warn)
        }
    }

    @Test
    fun `the crash leaves it stunned for a long punish window`() {
        val b = fresh(84)
        val px = b.arena.left + 300f
        b.x = b.arena.right - 400f
        toStrike(b, "rock charge", px)
        BossTestKit.runUntil(b, 10f, px) { b.mode == BossBrain.Mode.RECOVER }
        val t = BossTestKit.runUntil(b, 5f, px) { b.mode == BossBrain.Mode.MOVE }
        assertEquals("recovery length", 1.7f, t, 0.1f)
        assertTrue(b.crashed)
    }

    @Test
    fun `the charge is a fair dodge - long wind-up, low body, a jump clears it`() {
        val b = fresh(85)
        val charge = b.attacks.first { it.name == "rock charge" }
        assertTrue("wind-up ${charge.telegraph}", charge.telegraph >= 0.9f)
        toStrike(b, "rock charge", b.arena.left + 300f)
        assertTrue("body while charging ${b.bodyH}", b.bodyH <= 76f)
        val jumpApex = 540f * 540f / (2f * 1250f)   // the player's real jump speed and gravity
        assertTrue("a jump (apex $jumpApex) must clear a ${b.bodyH}px beast with room to spare", jumpApex > b.bodyH + 30f)
    }

    @Test
    fun `the charge locks onto the side the player is on at the last moment`() {
        val b = fresh(86)
        val wait = BossTestKit.runUntil(b, 300f, b.arena.left + 300f) { b.mode == BossBrain.Mode.TELEGRAPH && b.attack!!.name == "rock charge" }
        assertTrue(wait >= 0f)
        val tele = b.attack!!.telegraph
        BossTestKit.run(b, tele * 0.5f, b.arena.left + 300f)         // player to its left
        BossTestKit.run(b, tele * 0.45f, b.arena.right - 100f)       // then dashes past to its right
        BossTestKit.runUntil(b, 2f, b.arena.right - 100f) { b.mode == BossBrain.Mode.STRIKE }
        assertEquals("it commits to where the player is when the wind-up ends", 1, b.facing)
    }

    @Test
    fun `slam is a short window with a long reach and a wave, and a parry stops it`() {
        val b = fresh(87)
        var t = 0f
        // keep the player close so it chooses the slam
        while (t < 400f && !(b.mode == BossBrain.Mode.TELEGRAPH && b.attack!!.name == "facet slam")) {
            b.update(DT, b.x - 160f, floor(b)); b.signals.clear(); t += DT
        }
        assertTrue("never slammed", t < 400f)
        assertTrue(b.parryOpen)
        // a parry here would stop it: check on a twin that is interrupted
        val twin = fresh(87)
        var t2 = 0f
        while (t2 < 400f && !(twin.mode == BossBrain.Mode.TELEGRAPH && twin.attack!!.name == "facet slam")) {
            twin.update(DT, twin.x - 160f, floor(twin)); twin.signals.clear(); t2 += DT
        }
        assertTrue(twin.stagger(byParry = true))
        assertTrue(twin.isStaggered)
        // the real one goes on to strike
        while (b.mode != BossBrain.Mode.STRIKE) b.update(DT, b.x - 160f, floor(b))
        var boxFrames = 0
        var width = 0f
        var elapsed = 0f
        while (b.mode == BossBrain.Mode.STRIKE) {
            b.update(DT, b.x - 160f, floor(b))
            if (b.strikeBoxes.isNotEmpty()) {
                boxFrames++
                val box = b.strikeBoxes[0]
                width = box.right - box.left
                assertTrue("reaches in front of the beast", if (b.facing > 0) box.right > b.x + b.bodyW / 2f + 100f else box.left < b.x - b.bodyW / 2f - 100f)
            }
            elapsed += DT
        }
        assertEquals("weapon is live for only the first 0.22s", 0.22f, boxFrames * DT, 3f * DT)
        assertEquals(190f, width, 1f)
        val wave = b.hazards.firstOrNull { it.kind == Hazard.WAVE }
        assertTrue("the slam sends a wave", wave != null)
        assertEquals(380f, abs(wave!!.vx), 0.5f)
        assertEquals("wave travels the way it faces", b.facing.toFloat(), Math.signum(wave.vx), 0f)
        assertTrue("a low wave that a jump clears: ${wave.h}", wave.h <= 45f)
    }

    @Test
    fun `shard rain marks every shard before it falls and spreads across the hall`() {
        for (phase2 in listOf(false, true)) {
            val b = fresh(if (phase2) 88 else 89)
            if (phase2) toPhaseTwo(b)
            val px = b.arena.centerX
            toStrike(b, "shard rain", px)
            val shards = b.hazards.filter { it.kind == Hazard.SHARD }
            val markers = b.hazards.filter { it.kind == Hazard.MARKER }
            assertEquals(if (phase2) 8 else 5, shards.size)
            assertEquals("one marker per shard", shards.size, markers.size)
            assertEquals("the first shard lands on the player", px, shards[0].x, 1f)
            for (i in shards.indices) for (j in i + 1 until shards.size) {
                assertTrue("shards ${i} and $j are only ${abs(shards[i].x - shards[j].x)}px apart", abs(shards[i].x - shards[j].x) >= 79f)
            }
            for (s in shards) {
                assertTrue("warning ${s.warn}", s.warn >= 0.85f)
                val m = markers.first { abs(it.x - s.x) < 0.5f }
                assertTrue(m.life >= s.warn)
                assertTrue(s.vy > 0f)
            }
        }
    }

    @Test
    fun `a shard hurts only while it is falling, never while its marker is showing or after it lands`() {
        val b = fresh(90)
        val px = b.arena.centerX
        toStrike(b, "shard rain", px)
        val shard = b.hazards.first { it.kind == Hazard.SHARD }
        // the player stands exactly under the shard, on its marker
        val l = shard.x - 20f
        val r = shard.x + 20f
        val top = b.arena.floorY - 67f
        val bottom = b.arena.floorY
        var hurtDuringWarning = false
        var hurtWhileFalling = false
        var hurtAfterLanding = false
        var guard = 0
        while (guard++ < 600 && !(shard.landed && shard.life < 0.2f)) {
            b.update(DT, px, floor(b))
            b.signals.clear(); b.shots.clear()
            // judge only the hazard: put the boss body out of the way
            val hurts = b.hurts(l, top, r, bottom)
            val bodyBox = b.bodyHurtsNow && (b.x + b.bodyW / 2f > l && b.x - b.bodyW / 2f < r)
            if (bodyBox) continue
            if (shard.warn > 0f && hurts && b.hazards.none { it.kind == Hazard.WAVE && it.armed }) hurtDuringWarning = true
            if (shard.armed && !shard.landed && hurts) hurtWhileFalling = true
            if (shard.landed && hurts && b.hazards.none { it.kind == Hazard.WAVE && it.armed } && b.hazards.none { it.kind == Hazard.SHARD && it.armed && !it.landed }) hurtAfterLanding = true
        }
        assertFalse("the marker alone must be harmless", hurtDuringWarning)
        assertTrue("a falling shard must hurt", hurtWhileFalling)
        assertFalse("a landed shard is only debris", hurtAfterLanding)
    }

    @Test
    fun `the geode roll comes only in phase two, bounces twice within five seconds and ends exposed`() {
        val b = fresh(91)
        toPhaseTwo(b)
        toStrike(b, "geode roll", b.arena.centerX, 400f)
        assertTrue(b.rolling)
        assertEquals(96f, b.bodyW, 0f)
        assertEquals(96f, b.bodyH, 0f)
        var crashes = 0
        var maxHop = 0f
        var rolled = 0f
        while (b.mode == BossBrain.Mode.STRIKE) {
            rolled += DT
            b.update(DT, b.arena.centerX, floor(b))
            maxHop = maxOf(maxHop, b.hop)
            assertTrue("ball stays on the floor line or hops, y=${b.y}", b.y <= b.arena.floorY + 0.01f)
            if (b.mode == BossBrain.Mode.STRIKE) assertTrue("the rolling ball hurts on contact", b.bodyHurtsNow)
            for (s in b.signals) if (s.type == SignalType.WALL_CRASH) crashes++
            b.signals.clear()
        }
        assertEquals("it ricochets off the walls twice", 2, crashes)
        assertTrue("the roll is a bounded attack, not a marathon: ${rolled}s", rolled <= 5.05f)
        assertTrue("hop height $maxHop", maxHop in 20f..63f)
        assertFalse("it uncurls when spent", b.rolling)
        assertTrue(b.isExposed)
        val t = BossTestKit.runUntil(b, 5f, b.arena.centerX) { b.mode == BossBrain.Mode.MOVE }
        assertEquals("recovery length", 1.5f * b.spec.phase2Pace, t, 0.12f)
    }

    @Test
    fun `no shard or wave ever leaves the hall and the beast never leaves the floor`() {
        val b = fresh(92)
        var t = 0f
        var maxY = 0f
        while (t < 400f && !b.isDying) {
            b.update(DT, b.arena.left + 200f + (t * 37f) % (b.arena.width - 400f), floor(b))
            b.signals.clear()
            for (h in b.hazards) assertTrue("hazard x=${h.x}", h.x > b.arena.left - h.w && h.x < b.arena.right + h.w)
            maxY = maxOf(maxY, b.y)
            assertTrue(b.y <= b.arena.floorY + 0.01f)
            if (t > 120f) b.damageFromReflect()
            t += DT
        }
        assertEquals(b.arena.floorY, maxY, 0.01f)
    }

    @Test
    fun `an unspent beast turns aside most of a blow while the open core takes it all`() {
        val b = fresh(93)
        assertEquals(5, b.damage(18))
        BossTestKit.runUntil(b, 60f, BossTestKit.insideX(b.arena)) { b.isExposed }
        BossTestKit.run(b, 0.2f, BossTestKit.insideX(b.arena))
        assertEquals(18, b.damage(18))
        assertNotEquals(b.maxHp, b.hp)
    }
}
