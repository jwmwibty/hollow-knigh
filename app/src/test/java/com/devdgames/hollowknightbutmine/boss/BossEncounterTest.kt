package com.devdgames.hollowknightbutmine.boss

import com.devdgames.hollowknightbutmine.boss.BossTestKit.DT
import com.devdgames.hollowknightbutmine.game.boss.BossBrain
import com.devdgames.hollowknightbutmine.game.boss.BossEncounter
import com.devdgames.hollowknightbutmine.game.boss.BossProgress
import com.devdgames.hollowknightbutmine.game.boss.BossRoster
import com.devdgames.hollowknightbutmine.game.boss.MemoryBossProgressStore
import com.devdgames.hollowknightbutmine.game.boss.SignalType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The life cycle of a boss fight: starting, shutting the doors, dying, resetting, rewards. */
class BossEncounterTest {
    private val brain = BossTestKit.keener()
    private val arena = brain.arena
    private val inside = BossTestKit.insideX(arena)
    private val floor = BossTestKit.floorY(arena)
    private val outsideX = arena.left - 400f   // in the antechamber

    private fun step(enc: BossEncounter, seconds: Float, px: Float, dead: Boolean = false) {
        var t = 0f
        while (t < seconds) {
            enc.update(DT, px, floor, dead)
            t += DT
        }
    }

    /** Beats the boss up until it is dying. */
    private fun kill(enc: BossEncounter) {
        var guard = 0
        while (!enc.brain.isDying && !enc.brain.isDefeated && guard++ < 20000) {
            enc.brain.damage(60)
            enc.brain.damageFromReflect()
            enc.update(DT, inside, floor, false)
        }
    }

    @Test
    fun `the fight starts only when the player walks in past the trigger`() {
        val enc = BossTestKit.encounter(brain)
        step(enc, 2f, outsideX)
        assertEquals(BossEncounter.State.DORMANT, enc.state)
        assertFalse(enc.gateClosed)
        step(enc, 2f, arena.triggerX - 5f)
        assertEquals(BossEncounter.State.DORMANT, enc.state)
        step(enc, 0.1f, arena.triggerX + 1f)
        assertEquals(BossEncounter.State.INTRO, enc.state)
        assertTrue(enc.gateClosed)
    }

    @Test
    fun `a dying player never starts a fight`() {
        val enc = BossTestKit.encounter(brain)
        step(enc, 1f, inside, dead = true)
        assertEquals(BossEncounter.State.DORMANT, enc.state)
        assertFalse(enc.gateClosed)
    }

    @Test
    fun `doors are shut through the intro and the fight, open otherwise`() {
        val enc = BossTestKit.encounter(brain)
        assertFalse(enc.gateClosed)
        step(enc, 0.2f, inside)
        assertEquals(BossEncounter.State.INTRO, enc.state)
        assertTrue(enc.gateClosed)
        step(enc, brain.spec.introTime + 0.3f, inside)
        assertEquals(BossEncounter.State.FIGHTING, enc.state)
        assertTrue(enc.gateClosed)
        kill(enc)
        assertEquals(BossEncounter.State.DEFEATED, enc.state)
        assertFalse("doors must open once the boss falls", enc.gateClosed)
    }

    @Test
    fun `the boss can not be hurt while it wakes up`() {
        val enc = BossTestKit.encounter(brain)
        step(enc, 0.5f, inside)
        assertEquals(BossBrain.Mode.AWAKEN, brain.mode)
        assertEquals(0, brain.damage(500))
        assertEquals(0, brain.damageFromReflect())
        assertEquals(brain.maxHp, brain.hp)
    }

    @Test
    fun `player death resets the boss to full health and opens the doors`() {
        val enc = BossTestKit.encounter(brain)
        step(enc, brain.spec.introTime + 1.5f, inside)
        brain.damage(30)
        assertTrue(brain.hp < brain.maxHp)
        step(enc, 0.1f, inside, dead = true)
        assertEquals(BossEncounter.State.DORMANT, enc.state)
        assertFalse(enc.gateClosed)
        assertEquals(brain.maxHp, brain.hp)
        assertEquals(BossBrain.Mode.SLEEP, brain.mode)
        assertEquals(1, brain.phase)
        assertTrue("hazards must not outlive the fight", brain.hazards.isEmpty() && brain.shots.isEmpty())
    }

    @Test
    fun `onPlayerDied is safe at any time and a reset fight can start again`() {
        val enc = BossTestKit.encounter(brain)
        enc.onPlayerDied()
        assertEquals(BossEncounter.State.DORMANT, enc.state)
        step(enc, brain.spec.introTime + 1f, inside)
        assertTrue(enc.gateClosed)
        enc.onPlayerDied()
        assertEquals(BossEncounter.State.DORMANT, enc.state)
        assertFalse(enc.gateClosed)
        step(enc, 0.2f, inside)
        assertEquals(BossEncounter.State.INTRO, enc.state)
        assertEquals(brain.maxHp, brain.hp)
    }

    @Test
    fun `leaving the arena mid fight also ends it cleanly`() {
        val enc = BossTestKit.encounter(brain)
        step(enc, brain.spec.introTime + 1f, inside)
        assertEquals(BossEncounter.State.FIGHTING, enc.state)
        step(enc, 0.1f, arena.left - 400f)
        assertEquals(BossEncounter.State.DORMANT, enc.state)
        assertEquals(brain.maxHp, brain.hp)
    }

    @Test
    fun `victory is locked in once the boss starts dying`() {
        val progress = BossProgress()
        val enc = BossTestKit.encounter(brain, progress)
        step(enc, brain.spec.introTime + 1f, inside)
        kill(enc)
        assertTrue(progress.isDefeated(brain.spec.id))
        // the player dying during the death animation must not revive the boss
        step(enc, 0.5f, inside, dead = true)
        enc.onPlayerDied()
        assertEquals(BossEncounter.State.DEFEATED, enc.state)
        assertTrue(brain.isDying || brain.isDefeated)
        step(enc, brain.spec.deathTime + 0.5f, inside)
        assertTrue(brain.isDefeated)
        assertFalse(enc.bossPresent)
    }

    @Test
    fun `the reward is granted exactly once, ever`() {
        val store = MemoryBossProgressStore()
        val progress = BossProgress(store)
        val enc = BossTestKit.encounter(brain, progress)
        assertEquals(0, enc.takeReward())
        step(enc, brain.spec.introTime + 1f, inside)
        kill(enc)
        assertEquals(brain.spec.maskReward, enc.takeReward())
        assertEquals("asking again must not pay again", 0, enc.takeReward())
        assertEquals(1, store.saves)
        // walking back in afterwards does nothing
        step(enc, 3f, outsideX)
        step(enc, 3f, inside)
        assertEquals(BossEncounter.State.DEFEATED, enc.state)
        assertFalse(enc.gateClosed)
        assertEquals(0, enc.takeReward())
        // a brand-new encounter sharing the same record (a reloaded world) pays nothing either
        val again = BossTestKit.encounter(BossTestKit.keener(), progress)
        assertEquals(BossEncounter.State.DEFEATED, again.state)
        assertEquals(0, again.takeReward())
        assertEquals(1, store.saves)
    }

    @Test
    fun `a boss defeated earlier never wakes`() {
        val progress = BossProgress(MemoryBossProgressStore(setOf(BossRoster.keener.id)))
        val enc = BossTestKit.encounter(brain, progress)
        assertEquals(BossEncounter.State.DEFEATED, enc.state)
        assertFalse(enc.bossPresent)
        step(enc, 3f, inside)
        assertEquals(BossEncounter.State.DEFEATED, enc.state)
        assertFalse(enc.gateClosed)
        assertEquals(BossBrain.Mode.SLEEP, brain.mode)
    }

    @Test
    fun `a boss held by the player does not advance`() {
        val enc = BossTestKit.encounter(brain)
        step(enc, brain.spec.introTime + 1f, inside)
        val t = brain.time
        var f = 0f
        while (f < 1f) { enc.update(DT, inside, floor, false, frozen = true); f += DT }
        assertEquals(t, brain.time, 1e-4f)
        assertTrue("rules still apply while frozen", enc.gateClosed)
    }

    @Test
    fun `each boss has its own encounter and progress`() {
        val progress = BossProgress()
        val a = BossTestKit.encounter(BossTestKit.keener(), progress)
        val b = BossTestKit.encounter(BossTestKit.shardback(), progress)
        step(a, a.brain.spec.introTime + 1f, BossTestKit.insideX(a.arena))
        kill(a)
        assertEquals(BossEncounter.State.DEFEATED, a.state)
        assertEquals(BossEncounter.State.DORMANT, b.state)
        assertEquals(1, progress.count)
        assertFalse(b.gateClosed)
    }

    @Test
    fun `awakening and dying are reported once each`() {
        val enc = BossTestKit.encounter(brain)
        val seen = HashMap<SignalType, Int>()
        fun drain() { for (s in brain.signals) seen[s.type] = (seen[s.type] ?: 0) + 1; brain.signals.clear() }
        var t = 0f
        while (t < brain.spec.introTime + 1f) { enc.update(DT, inside, floor, false); drain(); t += DT }
        var guard = 0
        while (!brain.isDefeated && guard++ < 20000) {
            brain.damage(60); brain.damageFromReflect()
            enc.update(DT, inside, floor, false); drain()
            if (brain.isDying) { enc.update(brain.spec.deathTime + 0.1f, inside, floor, false); drain() }
        }
        assertEquals(1, seen[SignalType.AWAKEN])
        assertEquals(1, seen[SignalType.DYING])
        assertEquals(1, seen[SignalType.DEFEATED])
        assertEquals(1, seen[SignalType.PHASE])
    }
}
