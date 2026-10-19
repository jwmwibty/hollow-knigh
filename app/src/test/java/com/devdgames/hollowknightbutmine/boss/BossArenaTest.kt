package com.devdgames.hollowknightbutmine.boss

import com.devdgames.hollowknightbutmine.game.boss.BossProgress
import com.devdgames.hollowknightbutmine.game.boss.BossRoster
import com.devdgames.hollowknightbutmine.game.boss.MemoryBossProgressStore
import com.devdgames.hollowknightbutmine.game.world.AreaDef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Each boss hall is checked against the real map, so a map edit can not quietly break a fight. */
class BossArenaTest {
    private val layout = BossTestKit.layout

    @Test
    fun `the arena of every boss is open air with a solid floor, ceiling and end wall`() {
        for (spec in BossRoster.all) {
            val g = layout.grid(spec.areaId)
            val t = spec.arena
            for (col in t.firstCol..t.lastCol) {
                for (row in t.topRow until t.floorRow) {
                    assertTrue("${spec.id} arena tile ($col,$row) is solid", AreaDef.isAir(g.def.tile(col, row)))
                }
                assertTrue("${spec.id} floor under col $col", AreaDef.isSolid(g.def.tile(col, t.floorRow)))
                assertTrue("${spec.id} ceiling over col $col", AreaDef.isSolid(g.def.tile(col, t.topRow - 1)))
            }
            for (row in t.topRow until t.floorRow) {
                assertTrue("${spec.id} end wall at row $row", AreaDef.isSolid(g.def.tile(t.lastCol + 1, row)))
            }
            // the wall at the entrance is solid above the doorway
            for (row in t.topRow until t.gateTopRow) {
                assertTrue("${spec.id} entrance wall at row $row", AreaDef.isSolid(g.def.tile(t.firstCol - 1, row)))
            }
        }
    }

    @Test
    fun `the gate is exactly the doorway and the doorway is open when the doors are up`() {
        for (spec in BossRoster.all) {
            val g = layout.grid(spec.areaId)
            val t = spec.arena
            val arena = BossTestKit.arena(spec)
            for (row in t.gateTopRow..t.gateBottomRow) {
                for (col in t.gateCol - 1..t.firstCol - 1) assertTrue("${spec.id} doorway ($col,$row) blocked", AreaDef.isAir(g.def.tile(col, row)))
                assertTrue("${spec.id} wall above the doorway", AreaDef.isSolid(g.def.tile(t.gateCol, t.gateTopRow - 1)))
            }
            assertEquals((g.col0 + t.gateCol) * AreaDef.TILE, arena.gate.left, 0.01f)
            assertEquals(arena.gate.left + AreaDef.TILE, arena.gate.right, 0.01f)
            assertEquals(arena.floorY, arena.gate.bottom, 0.01f)
            assertTrue("gate must be tall enough to stop the player (67px)", arena.gate.bottom - arena.gate.top >= 100f)
            assertTrue("gate sits under the ceiling", arena.gate.top > arena.ceilingY)
            // the doorway is tall enough to walk through
            assertTrue((t.gateBottomRow - t.gateTopRow + 1) * AreaDef.TILE > 67.2f * 1.5f)
        }
    }

    @Test
    fun `the trigger is well inside the arena and the doors can not close on the player`() {
        for (spec in BossRoster.all) {
            val a = BossTestKit.arena(spec)
            assertTrue("trigger inside", a.triggerX > a.left && a.triggerX < a.right)
            assertTrue("trigger at least 3 tiles past the gate", a.triggerX - a.gate.right >= 3f * AreaDef.TILE)
            assertTrue("the boss starts well away from the trigger", a.spawnX - a.triggerX >= 600f)
            assertTrue(a.spawnX > a.left && a.spawnX < a.right)
            assertTrue("arena wide enough for charges and waves: ${a.width}", a.width >= 30f * AreaDef.TILE)
            assertTrue("arena tall enough to jump a wave: ${a.height}", a.height >= 9f * AreaDef.TILE)
            assertTrue(a.entered(a.triggerX + 1f, a.floorY - 1f))
            assertFalse(a.entered(a.triggerX - 1f, a.floorY - 1f))
            assertFalse("never triggered from the antechamber", a.entered(a.gate.left - 100f, a.floorY - 1f))
        }
    }

    @Test
    fun `a bench waits in the antechamber so a defeat sends the player back to the door`() {
        for (spec in BossRoster.all) {
            val g = layout.grid(spec.areaId)
            val t = spec.arena
            val benches = g.markers.filter { it.char == 'B' }
            assertEquals("${spec.id} should have exactly one bench", 1, benches.size)
            val b = benches[0]
            val localCol = b.col - g.col0
            assertTrue("bench left of the gate", localCol < t.gateCol)
            assertTrue("bench within 14 tiles of the gate (col $localCol)", t.gateCol - localCol <= 14)
            val floor = layout.floorRowBelow(b.col, b.row)
            assertTrue("bench stands on solid rock", floor != null && AreaDef.isSolid(layout.tileAt(b.col, floor)))
        }
    }

    @Test
    fun `each hall is a dead end behind one doorway from the old map`() {
        val openings = layout.openings()
        val expected = mapOf("vigil" to "resting", "geode" to "crystal")
        for (spec in BossRoster.all) {
            val neighbours = openings.filter { it.fromId == spec.areaId }.map { it.toId }.toSet()
            assertEquals("${spec.id} hall neighbours", setOf(expected.getValue(spec.areaId)), neighbours)
            assertEquals("a 3-tile doorway", 3, openings.count { it.fromId == spec.areaId })
        }
        assertTrue("the world must stay consistent", layout.validate().isEmpty())
    }

    @Test
    fun `the two halls do not overlap each other or any other area`() {
        val ids = BossRoster.all.map { it.areaId }
        for (id in ids) {
            val a = layout.grid(id).def
            for (g in layout.grids) {
                if (g.def.id == id) continue
                val b = g.def
                val overlap = a.originX < b.originX + b.width && b.originX < a.originX + a.width &&
                    a.originY < b.originY + b.height && b.originY < a.originY + a.height
                assertFalse("$id overlaps ${b.id}", overlap)
            }
        }
    }

    @Test
    fun `every boss is wired to a real area and has a brain`() {
        for (spec in BossRoster.all) {
            assertTrue(layout.defs.any { it.id == spec.areaId })
            val brain = BossRoster.brainFor(spec, BossTestKit.arena(spec), seed = 1)
            assertEquals(spec.maxHp, brain.hp)
            assertTrue(spec.phase2Hp in 1 until spec.maxHp)
            assertTrue(spec.maskReward in 1..1)
            assertTrue(spec.guard in 0.1f..0.6f)
        }
        assertEquals(BossRoster.all.map { it.id }.toSet().size, BossRoster.all.size)
    }

    @Test
    fun `progress remembers a victory once and survives a reload`() {
        val store = MemoryBossProgressStore()
        val progress = BossProgress(store)
        assertFalse(progress.isDefeated("keener"))
        assertTrue(progress.defeat("keener"))
        assertFalse("a second victory pays nothing", progress.defeat("keener"))
        assertTrue(progress.isDefeated("keener"))
        assertEquals(1, progress.count)
        assertEquals(1, store.saves)
        val reloaded = BossProgress(store)
        assertTrue(reloaded.isDefeated("keener"))
        assertFalse(reloaded.defeat("keener"))
        assertTrue(reloaded.defeat("shardback"))
        assertEquals(2, reloaded.count)
    }
}
