package com.devdgames.hollowknightbutmine.combat

import com.devdgames.hollowknightbutmine.game.PlayerState
import com.devdgames.hollowknightbutmine.game.combat.AttackPhase
import com.devdgames.hollowknightbutmine.game.combat.CombatMove
import com.devdgames.hollowknightbutmine.game.combat.MoveSpec
import com.devdgames.hollowknightbutmine.game.combat.PlayerCombat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The player's whole combat move set, tested against the same model [Player] drives at 60 fps.
 *
 * Covers the acceptance rules for the weapon redesign: only a light attack and a stab exist,
 * each plays anticipation -> strike -> short recovery, the blade only hurts during the strike,
 * and no grab, parry, block or headbutt survives anywhere in the model or the player's states.
 */
class PlayerCombatTest {
    private val dt = 1f / 60f

    private val slash = PlayerCombat.DEFAULT_SLASH
    private val thrust = PlayerCombat.DEFAULT_THRUST
    private val specs: List<Pair<String, MoveSpec>> = listOf("slash" to slash, "thrust" to thrust)

    /** Steps [c] forward by [seconds] at a fixed frame rate. */
    private fun run(c: PlayerCombat, seconds: Float) {
        var t = 0f
        while (t < seconds - 1e-6f) {
            c.update(dt)
            t += dt
        }
    }

    // ---- the move set is exactly two attacks ------------------------------------------

    @Test
    fun `the combat move set is only a slash and a thrust`() {
        assertEquals(listOf(CombatMove.SLASH, CombatMove.THRUST), CombatMove.values().toList())
        assertEquals(2, CombatMove.values().size)
    }

    @Test
    fun `the player has no grab, parry, block or headbutt state`() {
        val removed = setOf("GRABBING", "HOLDING_ENEMY", "HEADBUTTING", "PARRYING", "BLOCKING", "GUARDING")
        val names = PlayerState.values().map { it.name }
        for (gone in removed) {
            assertFalse("PlayerState still contains $gone", names.contains(gone))
        }
        // The two attacks each own a state, so the animation layer can tell them apart.
        assertTrue(names.contains("SLASHING"))
        assertTrue(names.contains("STABBING"))
    }

    @Test
    fun `every move resolves to a spec and nothing else can be triggered`() {
        val c = PlayerCombat()
        for (move in CombatMove.values()) {
            assertTrue(c.begin(move))
            assertEquals(move, c.move)
            assertEquals(move, c.currentSpec?.move)
            assertEquals(c.specFor(move), c.currentSpec)
            c.cancel()
        }
    }

    // ---- anticipation, strike, recovery -------------------------------------------------

    @Test
    fun `an attack plays windup then strike then recovery then idle`() {
        for ((name, spec) in specs) {
            val c = PlayerCombat()
            assertTrue(c.begin(spec.move))
            assertEquals("$name should start in anticipation", AttackPhase.WINDUP, c.phase)

            val seen = mutableListOf(c.phase)
            var t = 0f
            while (c.phase != AttackPhase.IDLE && t < spec.duration + 1f) {
                c.update(dt)
                t += dt
                if (seen.last() != c.phase) seen.add(c.phase)
            }
            assertEquals(
                "$name phase order",
                listOf(AttackPhase.WINDUP, AttackPhase.ACTIVE, AttackPhase.RECOVERY, AttackPhase.IDLE),
                seen
            )
        }
    }

    @Test
    fun `an attack lasts exactly its windup plus strike plus recovery`() {
        for ((name, spec) in specs) {
            val c = PlayerCombat()
            c.begin(spec.move)
            run(c, spec.duration)
            assertEquals("$name should be finished after its duration", AttackPhase.IDLE, c.phase)
            assertNull("$name should clear its move", c.move)
            // The three phases really are the whole timeline.
            assertEquals(spec.windup + spec.active + spec.recovery, spec.duration, 1e-5f)
        }
    }

    @Test
    fun `the blade is live only during the strike and for exactly that long`() {
        for ((name, spec) in specs) {
            val c = PlayerCombat()
            c.begin(spec.move)
            var activeFrames = 0
            var t = 0f
            while (c.phase != AttackPhase.IDLE && t < spec.duration + 1f) {
                assertEquals("$name isActive must track the ACTIVE phase", c.phase == AttackPhase.ACTIVE, c.isActive)
                if (c.isActive) activeFrames++
                c.update(dt)
                t += dt
            }
            val window = activeFrames * dt
            assertTrue(
                "$name damage window was ${window}s, expected ~${spec.active}s",
                kotlin.math.abs(window - spec.active) < dt * 1.5f
            )
            // Nothing before the strike and nothing after it.
            val c2 = PlayerCombat().apply { begin(spec.move) }
            assertFalse("$name must not hurt during anticipation", c2.isActive)
            run(c2, spec.windup)
            assertTrue("$name must hurt once the strike starts", c2.isActive)
            run(c2, spec.active)
            assertFalse("$name must stop hurting in recovery", c2.isActive)
        }
    }

    @Test
    fun `anticipation is shorter than the recovery and both are short`() {
        for ((name, spec) in specs) {
            assertTrue("$name needs a visible anticipation", spec.windup > 0f)
            assertTrue("$name needs a strike window", spec.active > 0f)
            assertTrue("$name needs a recovery tail", spec.recovery > 0f)
            assertTrue("$name anticipation should be under a fifth of a second", spec.windup < 0.2f)
            assertTrue("$name recovery should stay short", spec.recovery < 0.2f)
        }
    }

    // ---- cancel rules -------------------------------------------------------------------

    @Test
    fun `an attack cannot be interrupted during its windup or its strike`() {
        for ((name, spec) in specs) {
            // Mid wind-up.
            val c = PlayerCombat()
            c.begin(spec.move)
            run(c, spec.windup * 0.5f)
            assertFalse("$name should refuse a new attack mid windup", c.begin(otherOf(spec)))
            assertEquals(spec.move, c.move)
            assertEquals(AttackPhase.WINDUP, c.phase)

            // Mid strike.
            run(c, spec.windup * 0.5f + spec.active * 0.5f)
            assertEquals("$name should have reached its strike", AttackPhase.ACTIVE, c.phase)
            assertFalse("$name should refuse a new attack mid strike", c.begin(otherOf(spec)))
            assertEquals(spec.move, c.move)
        }
    }

    @Test
    fun `the recovery tail cancels into the next attack so the controls never lock up`() {
        for ((name, spec) in specs) {
            val c = PlayerCombat()
            c.begin(spec.move)
            run(c, spec.windup + spec.active + spec.recovery * 0.5f)
            assertEquals("$name should be in recovery", AttackPhase.RECOVERY, c.phase)
            assertTrue("$name recovery should accept the next attack", c.canAttack)

            val next = otherOf(spec)
            assertTrue(c.begin(next))
            assertEquals(next, c.move)
            assertEquals(2, c.swings)
            assertEquals(AttackPhase.WINDUP, c.phase)
            assertFalse("a fresh swing must be hittable again", c.connected)
        }
    }

    @Test
    fun `a swing hits at most once and the flag resets for the next swing`() {
        val c = PlayerCombat()
        c.begin(CombatMove.SLASH)
        run(c, slash.windup)
        assertTrue(c.isActive)
        assertFalse(c.connected)
        c.onHit()
        assertTrue("the hit should close the damage window", c.connected)
        // Still active, but connected: Player leaves the hitbox down for the rest of the swing.
        run(c, slash.active * 0.5f)
        assertTrue(c.connected)

        c.begin(CombatMove.THRUST)
        assertFalse("the next swing must be able to hit again", c.connected)
    }

    @Test
    fun `cancel drops the attack immediately`() {
        val c = PlayerCombat()
        c.begin(CombatMove.SLASH)
        run(c, slash.windup + slash.active * 0.5f)
        assertTrue(c.isActive)
        c.cancel()
        assertEquals(AttackPhase.IDLE, c.phase)
        assertNull(c.move)
        assertNull(c.currentSpec)
        assertFalse(c.isActive)
        assertFalse(c.connected)
        assertEquals(0f, c.elapsed, 1e-6f)
        // Updating an idle model changes nothing and cannot resurrect the swing.
        run(c, 1f)
        assertEquals(AttackPhase.IDLE, c.phase)
        assertTrue("the player is free to attack again", c.canAttack)
    }

    // ---- damage and hitbox numbers -------------------------------------------------------

    @Test
    fun `both attacks deal damage and the thrust is the heavier of the two`() {
        for ((name, spec) in specs) assertTrue("$name must deal damage", spec.damage > 0)
        assertTrue("the thrust should out-damage the light attack", thrust.damage > slash.damage)
        assertTrue("the thrust should reach further", thrust.reach > slash.reach)
        assertTrue("the light attack should swing wider", slash.reach > 0f && slash.boxTopFraction > thrust.boxTopFraction)
    }

    @Test
    fun `the hitbox is a real box in front of the player`() {
        for ((name, spec) in specs) {
            assertTrue("$name reach", spec.reach > 0f)
            assertTrue("$name box top must be above the box bottom", spec.boxTopFraction > spec.boxBottomFraction)
            assertTrue("$name box top within the body", spec.boxTopFraction <= 1f)
            assertTrue("$name box bottom off the floor", spec.boxBottomFraction >= 0f)
            assertTrue("$name knocks the target away", spec.knockbackX > 0f)
            assertTrue("$name soul gain", spec.soulGain > 0)
            assertTrue("$name keeps some movement", spec.travelScale in 0.1f..1f)
        }
    }

    @Test
    fun `progress stays inside its range through a whole attack`() {
        val c = PlayerCombat()
        c.begin(CombatMove.SLASH)
        assertEquals(0f, c.progress, 1e-6f)
        var t = 0f
        while (c.phase != AttackPhase.IDLE && t < slash.duration + 1f) {
            c.update(dt)
            t += dt
            assertTrue("progress ${c.progress}", c.progress in 0f..1f)
            assertTrue("phase progress ${c.phaseProgress}", c.phaseProgress in 0f..1f)
        }
        assertEquals(0f, c.phaseProgress, 1e-6f)
    }

    @Test
    fun `a custom spec drives the model exactly`() {
        val spec = MoveSpec(
            move = CombatMove.SLASH, windup = 0.10f, active = 0.20f, recovery = 0.30f,
            damage = 7, reach = 40f, boxTopFraction = 0.9f, boxBottomFraction = 0.1f,
            knockbackX = 10f, knockbackY = -5f, soulGain = 3, travelScale = 0.5f
        )
        val c = PlayerCombat(slashSpec = spec, thrustSpec = spec)
        assertEquals(spec, c.specFor(CombatMove.SLASH))
        assertEquals(0.60f, spec.duration, 1e-6f)
        c.begin(CombatMove.SLASH)
        run(c, 0.10f)
        assertEquals(AttackPhase.ACTIVE, c.phase)
        assertNotNull(c.currentSpec)
        run(c, 0.20f)
        assertEquals(AttackPhase.RECOVERY, c.phase)
    }

    private fun otherOf(spec: MoveSpec): CombatMove =
        if (spec.move == CombatMove.SLASH) CombatMove.THRUST else CombatMove.SLASH
}
