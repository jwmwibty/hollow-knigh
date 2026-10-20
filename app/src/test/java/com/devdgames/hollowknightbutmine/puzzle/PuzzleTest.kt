package com.devdgames.hollowknightbutmine.puzzle

import com.devdgames.hollowknightbutmine.game.puzzle.BeaconPuzzle
import com.devdgames.hollowknightbutmine.game.puzzle.LitanyPuzzle
import com.devdgames.hollowknightbutmine.game.puzzle.PuzzleAtlas
import com.devdgames.hollowknightbutmine.game.puzzle.RuneSealPuzzle
import com.devdgames.hollowknightbutmine.game.puzzle.SluicePuzzle
import com.devdgames.hollowknightbutmine.game.world.AreaDef
import com.devdgames.hollowknightbutmine.game.world.WorldAtlas
import com.devdgames.hollowknightbutmine.game.world.WorldLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.floor

/**
 * The four puzzles, run head-first.
 *
 * They are pure Kotlin state machines, so every rule — the order the stones want, the parity
 * of the grave-lights, the clock on the facets, the combination on the wheels — is checked
 * here on the JVM rather than by hand on a phone.
 */
class PuzzleTest {

    private val layout = WorldLayout(WorldAtlas.areas)
    private val built = PuzzleAtlas.build(layout).associateBy { it.spec.id }

    private val rune get() = built.getValue("rune_seal") as RuneSealPuzzle
    private val litany get() = built.getValue("litany") as LitanyPuzzle
    private val beacon get() = built.getValue("beacon") as BeaconPuzzle
    private val sluice get() = built.getValue("sluice") as SluicePuzzle

    private fun freshLitany() = LitanyPuzzle(PuzzleAtlas.litany, 0f, 0f, PuzzleAtlas.litanyScramble)

    // ---------------------------------------------------------------- the sealed door

    @Test
    fun `the seal wants its stones woken fewest notches first`() {
        val p = rune
        assertEquals("the notches decide the order", listOf(1, 2, 0), p.order.toList())
        p.press(p.order[0])
        p.press(p.order[1])
        assertEquals(2, p.woken)
        assertFalse("two of three is not open", p.solved)
        p.press(p.order[2])
        assertTrue(p.solved)
    }

    @Test
    fun `a stone out of turn resets the seal instead of punishing the player`() {
        val p = rune
        p.press(p.order[0])
        val wrong = (0 until p.cells).first { it != p.order[0] && it != p.order[1] }
        val reply = p.press(wrong)
        assertTrue("a wrong stone says so", reply.failed)
        assertFalse(p.solved)
        assertEquals(0, p.woken)
        assertFalse("nothing stays lit after a reset", p.lit.any { it })
        // and the door can still be opened straight afterwards
        for (i in p.order) p.press(i)
        assertTrue(p.solved)
    }

    @Test
    fun `a stone already awake is not woken twice`() {
        val p = rune
        p.press(p.order[0])
        val reply = p.press(p.order[0])
        assertEquals(1, p.woken)
        assertFalse(reply.failed)
        assertFalse(p.solved)
    }

    // ---------------------------------------------------------------- the litany of graves

    @Test
    fun `the litany starts ragged`() {
        val p = litany
        assertFalse("it would not be a rite if it were already done", p.solved)
        assertEquals(2, p.litCount)
        assertFalse(p.lit.all { it })
    }

    @Test
    fun `waking a grave-light wakes the ones beside it`() {
        val p = freshLitany()
        val before = p.lit.copyOf()
        p.press(2)
        assertEquals(!before[1], p.lit[1])
        assertEquals(!before[2], p.lit[2])
        assertEquals(!before[3], p.lit[3])
        assertEquals(before[0], p.lit[0])
        assertEquals(before[4], p.lit[4])
    }

    @Test
    fun `the presses that made the litany ragged put it right again`() {
        val p = freshLitany()
        for (i in PuzzleAtlas.litanyScramble) p.press(i)
        assertTrue(p.solved)
        assertTrue(p.lit.all { it })
    }

    @Test
    fun `the litany has exactly two short routes and both are findable`() {
        var short = 0
        var any = 0
        for (mask in 0 until 1 shl litany.cells) {
            val presses = (0 until litany.cells).filter { (mask shr it) and 1 == 1 }
            val p = freshLitany()
            for (i in presses) p.press(i)
            if (!p.solved) continue
            any++
            if (presses.size <= 3) short++
        }
        assertEquals("the second route: 1, 2 and 3 together", 2, short)
        assertTrue("a superset of a route also ends woken", any >= short)
    }

    @Test
    fun `a woken litany stays woken`() {
        val p = freshLitany()
        for (i in PuzzleAtlas.litanyScramble) p.press(i)
        assertTrue(p.solved)
        p.press(0)
        p.press(4)
        assertTrue(p.solved)
        assertEquals(5, p.litCount)
    }

    // ---------------------------------------------------------------- the four facets

    @Test
    fun `the facets go out again unless all four are held at once`() {
        val p = beacon
        p.press(0)
        p.press(1)
        assertEquals(2, p.litCount)
        p.update(1f)
        assertEquals(2, p.litCount)
        assertFalse(p.solved)
        p.update(PuzzleAtlas.BEACON_BURN)
        assertEquals("the fire is a clock, not a switch", 0, p.litCount)
        assertFalse(p.solved)
    }

    @Test
    fun `running the hall lights all four facets`() {
        val p = beacon
        for (i in 0 until p.cells) {
            p.press(i)
            p.update(0.1f)   // a stride between braziers
            if (i < p.cells - 1) assertFalse("the row is not alight yet", p.solved)
        }
        assertTrue(p.solved)
        assertEquals(p.cells, p.litCount)
    }

    @Test
    fun `a facet held too long between the others goes out mid-run`() {
        val p = beacon
        p.press(0)
        p.update(PuzzleAtlas.BEACON_BURN * 0.5f)
        p.press(1)
        p.update(PuzzleAtlas.BEACON_BURN * 0.6f)
        assertEquals("the first has burnt down while the second still holds", 1, p.litCount)
        p.press(2)
        p.press(3)
        assertFalse(p.solved)
    }

    // ---------------------------------------------------------------- the sluice gate

    @Test
    fun `the sluice wants three wheels running and the second shut`() {
        val p = sluice
        for (i in 0 until p.cells) assertEquals("it starts shut", p.wants(i), i != 1)
        p.press(0)
        p.press(2)
        assertFalse(p.solved)
        p.press(3)
        assertTrue(p.solved)
        assertEquals(3, p.openCount)
    }

    @Test
    fun `opening every wheel floods the chamber and spins them all back`() {
        val p = sluice
        for (i in 0 until p.cells) p.press(i)
        assertFalse("wide open is not the answer", p.solved)
        assertEquals(0, p.openCount)
        // and the wheels can still be set properly afterwards
        p.press(0); p.press(2); p.press(3)
        assertTrue(p.solved)
    }

    @Test
    fun `a solved puzzle keeps saying that it is solved`() {
        val p = sluice
        p.press(0); p.press(2); p.press(3)
        assertTrue(p.solved)
        val reply = p.press(1)
        assertTrue("a solved gate does not move again", p.solved)
        assertEquals(3, p.openCount)
        assertTrue(reply.text.isNotEmpty())
    }

    // ---------------------------------------------------------------- placement

    @Test
    fun `every puzzle stands on real ground and every control has the same floor`() {
        for (p in PuzzleAtlas.build(layout)) {
            val spec = p.spec
            assertTrue("${spec.id} has no floor", p.floorY > 0f)
            assertEquals("${spec.id} cells disagree with the spec", spec.cells, p.cells)
            val anchorRow = layout.floorRowBelow(
                layout.grid(spec.areaId).col0 + spec.col, layout.grid(spec.areaId).row0 + spec.row
            )
            assertEquals("${spec.id} does not stand where it was authored", anchorRow!! * AreaDef.TILE, p.floorY, 0.01f)
            for (i in 0 until p.cells) {
                val cellCol = floor(p.cellX(i) / AreaDef.TILE).toInt()
                val cellRow = layout.floorRowBelow(cellCol, layout.grid(spec.areaId).row0 + spec.row)
                assertTrue("${spec.id} control $i hangs over a hole or a hazard", cellRow != null)
                assertEquals("${spec.id} control $i is on a different floor", p.floorY, cellRow!! * AreaDef.TILE, 0.01f)
            }
        }
    }

    @Test
    fun `the controls of a puzzle are ordered and within reach of one another`() {
        for (p in PuzzleAtlas.build(layout)) {
            for (i in 1 until p.cells) assertTrue(p.cellX(i) > p.cellX(i - 1))
            // The player must be able to stand between two controls without ambiguity.
            val spacing = p.spec.cellSpacing
            assertTrue("${p.spec.id} controls are too close to tell apart", spacing >= 24f)
            assertEquals(-1, p.cellNear(p.x - spacing, 10f))
            assertEquals(0, p.cellNear(p.x + 1f, 10f))
            assertEquals(p.cells - 1, p.cellNear(p.cellX(p.cells - 1), 10f))
        }
    }

    @Test
    fun `the four puzzles are four different kinds of thinking`() {
        assertEquals(4, PuzzleAtlas.specs.size)
        assertEquals(4, PuzzleAtlas.specs.map { it.kind }.distinct().size)
        assertEquals(4, PuzzleAtlas.specs.map { it.areaId }.distinct().size)
        for (spec in PuzzleAtlas.specs) {
            assertTrue("${spec.id} carries its rule into the world", spec.clue.length > 8)
        }
    }
}
