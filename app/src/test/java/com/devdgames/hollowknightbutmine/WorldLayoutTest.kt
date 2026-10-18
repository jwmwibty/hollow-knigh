package com.devdgames.hollowknightbutmine

import com.devdgames.hollowknightbutmine.game.world.WorldAtlas
import com.devdgames.hollowknightbutmine.game.world.WorldLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The kingdom is authored by hand as text; these tests keep it honest. They run on the
 * JVM because the world model has no Android dependencies.
 */
class WorldLayoutTest {
    private val layout = WorldLayout(WorldAtlas.areas)

    @Test
    fun `areas do not overlap, passages line up and everything is reachable`() {
        val issues = layout.validate()
        assertTrue("World layout issues:\n" + issues.joinToString("\n"), issues.isEmpty())
    }

    @Test
    fun `every area connects to at least one neighbour`() {
        val openings = layout.openings()
        for (g in layout.grids) {
            val connected = openings.any { it.fromId == g.def.id || it.toId == g.def.id }
            assertTrue("${g.def.id} is an island", connected)
        }
    }

    @Test
    fun `every border opening is open from both sides`() {
        val openings = layout.openings()
        val keys = openings.map { Triple(it.fromId, it.toId, it.col to it.row) }.toSet()
        for (o in openings) {
            // The reverse entry is recorded from the neighbouring tile.
            val reverse = openings.any { it.fromId == o.toId && it.toId == o.fromId && kotlin.math.abs(it.col - o.col) + kotlin.math.abs(it.row - o.row) == 1 }
            assertTrue("Opening ${o.fromId}->${o.toId} at (${o.col},${o.row}) has no matching side", reverse)
        }
        assertTrue(keys.isNotEmpty())
    }

    @Test
    fun `there is exactly one spawn and a bench in every major area`() {
        val spawns = layout.grids.sumOf { g -> g.markers.count { it.char == 'S' } }
        assertEquals(1, spawns)
        val withoutBench = layout.grids.filter { g -> g.markers.none { it.char == 'B' } }.map { it.def.id }
        assertTrue("Areas without a bench: $withoutBench", withoutBench.isEmpty())
    }
}
