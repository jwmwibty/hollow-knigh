package com.devdgames.hollowknightbutmine

import com.devdgames.hollowknightbutmine.game.Enemy
import com.devdgames.hollowknightbutmine.game.EnemyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for enemy variety requirements (Part C):
 * - Distinct roles: Lurker (burrowing ambush), Swooper (ceiling diver), Runeweaver (tactical caster)
 * - Behavior, states, damage immunity when buried, and attack triggers
 */
class EnemyVarietyTest {

    @Test
    fun `new enemy types have valid distinct HP, speed and bounding dimensions`() {
        val lurker = Enemy(100f, 200f, type = EnemyType.LURKER)
        val swooper = Enemy(100f, 200f, type = EnemyType.SWOOPER)
        val runeweaver = Enemy(100f, 200f, type = EnemyType.RUNEWEAVER)

        assertEquals(55, lurker.maxHp)
        assertEquals(55, lurker.hp)
        assertEquals(48f, lurker.width, 0.01f)
        assertEquals(36f, lurker.height, 0.01f)
        assertEquals(0f, lurker.patrolSpeed, 0.01f)

        assertEquals(40, swooper.maxHp)
        assertEquals(40, swooper.hp)
        assertEquals(36f, swooper.width, 0.01f)
        assertEquals(36f, swooper.height, 0.01f)
        assertEquals(90f, swooper.patrolSpeed, 0.01f)

        assertEquals(65, runeweaver.maxHp)
        assertEquals(65, runeweaver.hp)
        assertEquals(46f, runeweaver.width, 0.01f)
        assertEquals(58f, runeweaver.height, 0.01f)
        assertEquals(50f, runeweaver.patrolSpeed, 0.01f)
    }

    @Test
    fun `lurker begins underground, is immune to damage and does not hurt player while hiding`() {
        val lurker = Enemy(200f, 300f, type = EnemyType.LURKER)
        assertTrue("lurker should start hidden in the ground", lurker.isHiding)

        // Player far away - remains hidden
        lurker.update(0.1f, playerX = 800f, playerY = 300f, platforms = emptyList())
        assertTrue(lurker.isHiding)
        assertFalse("hiding lurker does not hurt player", lurker.hurtsPlayer(android.graphics.RectF(190f, 290f, 210f, 310f)))

        // Cannot take damage while hiding
        val initialHp = lurker.hp
        lurker.takeDamage(18, knockbackX = 100f)
        assertEquals("hiding lurker should be immune to damage", initialHp, lurker.hp)
    }

    @Test
    fun `lurker emerges after telegraph when player steps within 120px`() {
        val lurker = Enemy(200f, 300f, type = EnemyType.LURKER)
        assertTrue(lurker.isHiding)

        // Player approaches to 80px distance
        lurker.update(0.3f, playerX = 280f, playerY = 300f, platforms = emptyList())
        assertTrue("still in warning windup phase (< 0.6s)", lurker.isHiding)

        // Complete warning phase (total 0.7s > 0.6s)
        lurker.update(0.4f, playerX = 280f, playerY = 300f, platforms = emptyList())
        assertFalse("lurker emerges and unhides", lurker.isHiding)
        assertTrue("lurker attacks upon emerging", lurker.isAttacking)

        // Now can take damage
        lurker.takeDamage(20)
        assertEquals(35, lurker.hp)
    }

    @Test
    fun `swooper tracks player and dives rapidly when player passes directly beneath`() {
        val swooper = Enemy(300f, 100f, type = EnemyType.SWOOPER)
        swooper.ceilingY = 64f
        assertEquals(0, swooper.swooperState)

        // Player is far horizontally: swooper does not dive
        swooper.update(0.1f, playerX = 600f, playerY = 350f, platforms = emptyList())
        assertEquals("should remain in tracking state", 0, swooper.swooperState)
        assertFalse(swooper.isAttacking)

        // Player moves directly beneath swooper (within 80px horizontal, lower Y)
        swooper.attackCooldown = 0f
        swooper.update(0.1f, playerX = 320f, playerY = 350f, platforms = emptyList())
        assertEquals("should trigger diving state", 1, swooper.swooperState)
        assertTrue("is attacking during dive", swooper.isAttacking)
        assertTrue("vy should be downward", swooper.vy > 400f)

        // Simulate reaching ground/player level
        swooper.update(0.5f, playerX = 320f, playerY = 350f, platforms = emptyList())
        assertEquals("should pause on ground", 2, swooper.swooperState)
        assertFalse("recovers from dive attack on ground", swooper.isAttacking)
    }

    @Test
    fun `moss charger is durable but fair and can be hit again after short immunity`() {
        val charger = Enemy(100f, 300f, type = EnemyType.MOSS_CHARGER)
        assertEquals(100, charger.maxHp)
        assertEquals(100, charger.hp)

        charger.takeDamage(20)
        assertEquals(80, charger.hp)
        assertTrue("a confirmed hit should briefly protect against duplicate frame hits", charger.invincibilityTimer > 0f)

        // Once the short hit-immunity window has elapsed, a separate attack may connect.
        charger.update(0.11f, playerX = 10000f, playerY = 300f, platforms = emptyList())
        charger.takeDamage(20)
        assertEquals("a follow-up should damage the charger after its brief immunity", 60, charger.hp)
    }

    @Test
    fun `runeweaver retreats when player is close and fires 3-shot arc when ready`() {
        val runeweaver = Enemy(400f, 300f, type = EnemyType.RUNEWEAVER)
        runeweaver.attackCooldown = 0f

        var shotsFired = 0
        runeweaver.onShootProjectile = { _, _, _, _ ->
            shotsFired++
        }

        // When player is very close (< 200px), runeweaver retreats
        runeweaver.update(0.1f, playerX = 480f, playerY = 300f, platforms = emptyList())
        assertTrue("should flee away from player (moving left)", runeweaver.vx < 0f)

        // Player in sweet spot (200px to 500px) and cooldown is ready: wind up and attack
        runeweaver.attackCooldown = 0f
        runeweaver.update(0.8f, playerX = 650f, playerY = 300f, platforms = emptyList())

        assertEquals("runeweaver fires 3 projectiles in arc spread", 3, shotsFired)
        assertTrue("attack cooldown resets to > 3s", runeweaver.attackCooldown > 3.0f)
    }
}
