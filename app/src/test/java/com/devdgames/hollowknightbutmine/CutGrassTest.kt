package com.devdgames.hollowknightbutmine

import com.devdgames.hollowknightbutmine.game.world.GrassCutState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [GrassCutState] verifying that grass cuts are tracked, timed, regrow after 4.0 seconds,
 * and handle independent chunk registrations correctly on the JVM.
 */
class CutGrassTest {

    @Test
    fun `cut registers a chunk and marks it as cut`() {
        val state = GrassCutState()
        assertEquals(0, state.cutCount())
        assertFalse(state.isCut("greenpath", 0))

        state.cut("greenpath", 0)

        assertEquals(1, state.cutCount())
        assertTrue(state.isCut("greenpath", 0))
        val expectedKey = GrassCutState.key("greenpath", 0)
        assertTrue(state.cutKeys().contains(expectedKey))
    }

    @Test
    fun `cuts stay visible before 4 seconds then regrow naturally`() {
        val state = GrassCutState()
        state.cut("greenpath", 2)

        // Advance 2 seconds - should still be cut
        state.update(2.0f)
        assertTrue("grass should remain cut at 2s", state.isCut("greenpath", 2))
        assertEquals(1, state.cutCount())

        // Advance 1.9 seconds (3.9s total) - still cut
        state.update(1.9f)
        assertTrue("grass should remain cut at 3.9s", state.isCut("greenpath", 2))

        // Advance another 0.2 seconds (4.1s total) - should have regrown
        state.update(0.2f)
        assertFalse("grass should regrow after 4s", state.isCut("greenpath", 2))
        assertEquals(0, state.cutCount())
    }

    @Test
    fun `cutting already-cut grass resets the regrowth timer`() {
        val state = GrassCutState()
        state.cut("waterways", 5)

        // Advance 3 seconds
        state.update(3.0f)
        assertTrue(state.isCut("waterways", 5))

        // Cut again to reset timer
        state.cut("waterways", 5)

        // Advance another 2 seconds (5s after first cut, 2s after second cut)
        state.update(2.0f)
        assertTrue("regrowth timer should have been reset", state.isCut("waterways", 5))

        // Advance another 2.1s (4.1s after second cut) -> regrows
        state.update(2.1f)
        assertFalse(state.isCut("waterways", 5))
    }

    @Test
    fun `multiple chunks in same or different areas are tracked independently`() {
        val state = GrassCutState()
        state.cut("greenpath", 0)
        state.cut("greenpath", 1)
        state.cut("fungal", 0)

        assertEquals(3, state.cutCount())
        assertTrue(state.isCut("greenpath", 0))
        assertTrue(state.isCut("greenpath", 1))
        assertTrue(state.isCut("fungal", 0))

        // Advance 2 seconds, recut greenpath 1
        state.update(2.0f)
        state.cut("greenpath", 1)

        // Advance 2.5 seconds (4.5s for greenpath 0 and fungal 0; 2.5s for greenpath 1)
        state.update(2.5f)
        assertFalse(state.isCut("greenpath", 0))
        assertFalse(state.isCut("fungal", 0))
        assertTrue("greenpath 1 was recut and should still be cut", state.isCut("greenpath", 1))

        // Advance 1.6s -> all regrown
        state.update(1.6f)
        assertEquals(0, state.cutCount())
    }

    @Test
    fun `clear immediately removes all cut records`() {
        val state = GrassCutState()
        state.cut("cliffs", 1)
        state.cut("cliffs", 2)
        assertEquals(2, state.cutCount())

        state.clear()
        assertEquals(0, state.cutCount())
        assertFalse(state.isCut("cliffs", 1))
        assertFalse(state.isCut("cliffs", 2))
    }

    @Test
    fun `key generation is deterministic and unique per chunk`() {
        val key1 = GrassCutState.key("greenpath", 0)
        val key2 = GrassCutState.key("greenpath", 1)
        val key3 = GrassCutState.key("fungal", 0)

        assertEquals(key1, GrassCutState.key("greenpath", 0))
        org.junit.Assert.assertNotEquals(key1, key2)
        org.junit.Assert.assertNotEquals(key1, key3)
    }
}
