package com.devdgames.hollowknightbutmine.story

import com.devdgames.hollowknightbutmine.game.boss.BossProgress
import com.devdgames.hollowknightbutmine.game.boss.BossRoster
import com.devdgames.hollowknightbutmine.game.puzzle.PuzzleAtlas
import com.devdgames.hollowknightbutmine.game.story.NpcRoster
import com.devdgames.hollowknightbutmine.game.story.ObjectiveKind
import com.devdgames.hollowknightbutmine.game.story.QuestBook
import com.devdgames.hollowknightbutmine.game.story.RelicRoster
import com.devdgames.hollowknightbutmine.game.story.StoryDirector
import com.devdgames.hollowknightbutmine.game.world.AreaDef
import com.devdgames.hollowknightbutmine.game.world.WorldAtlas
import com.devdgames.hollowknightbutmine.game.world.WorldLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.floor

/**
 * The cast, the puzzles and the relics are authored by hand against a map that is also
 * authored by hand, so this is the test that keeps them honest: everybody has to be standing
 * on real ground, in the right region, out of the water, clear of the benches, and pointing
 * at things that exist.
 */
class StoryCastTest {

    private val layout = WorldLayout(WorldAtlas.areas)
    private val npcs = NpcRoster.place(layout)
    private val puzzles = PuzzleAtlas.build(layout)
    private val relics = RelicRoster.place(layout)
    private val story = StoryDirector(layout, BossProgress())

    /** Where the benches are, computed the way AreaScene computes them. */
    private val benches: List<FloatArray> = layout.grids.flatMap { g ->
        g.markers.filter { it.char == 'B' }.map { m ->
            val row = layout.floorRowBelow(m.col, m.row) ?: (m.row + 1)
            floatArrayOf(m.col * AreaDef.TILE + AreaDef.TILE * 0.5f, row * AreaDef.TILE)
        }
    }

    // -------------------------------------------------------------------- placement

    @Test
    fun `everybody stands on real ground, in air, and not over a hazard`() {
        for (n in npcs) {
            val d = n.def
            val worldCol = layout.grid(d.areaId).col0 + d.col
            val worldRow = layout.grid(d.areaId).row0 + d.row
            assertEquals("${d.id} is authored inside something", ' ', layout.tileAt(worldCol, worldRow))
            assertNotNull("${d.id} has no floor under it", layout.floorRowBelow(worldCol, worldRow))
            assertTrue("${d.id} is not standing on anything", n.floorY > 0f)
        }
        for (r in relics) {
            val d = r.def
            val g = layout.grid(d.areaId)
            val worldCol = g.col0 + d.col
            val worldRow = g.row0 + d.row
            assertEquals("${d.id} is authored inside something", ' ', layout.tileAt(worldCol, worldRow))
            assertNotNull("${d.id} has no floor under it", layout.floorRowBelow(worldCol, worldRow))
            assertTrue("${d.id} is not hanging over anything", r.floorY > 0f)
        }
    }

    @Test
    fun `nobody stands close enough to a bench to steal the heal key`() {
        // Heal doubles as Interact, so anything within reach of a bench would stop the player
        // from healing on it. Everything the story puts in the world keeps its distance.
        for (b in benches) {
            for (n in npcs) {
                if (abs(n.floorY - b[1]) > StoryDirector.HEIGHT_TOLERANCE) continue
                val gap = abs(n.x - b[0])
                assertTrue(
                    "${n.def.id} stands ${gap}px from a bench at ${b[0]}, needs more than " +
                        "${StoryDirector.NPC_REACH + 20f}",
                    gap > StoryDirector.NPC_REACH + 20f
                )
            }
            for (p in puzzles) {
                if (abs(p.floorY - b[1]) > StoryDirector.HEIGHT_TOLERANCE) continue
                for (i in 0 until p.cells) {
                    val gap = abs(p.cellX(i) - b[0])
                    assertTrue(
                        "${p.spec.id} control $i stands ${gap}px from a bench at ${b[0]}",
                        gap > StoryDirector.CELL_REACH + 20f
                    )
                }
            }
        }
    }

    @Test
    fun `no two characters stand in the same place`() {
        for (i in npcs.indices) for (j in i + 1 until npcs.size) {
            val a = npcs[i]
            val b = npcs[j]
            val sameGround = abs(a.floorY - b.floorY) < 8f
            if (!sameGround) continue
            assertTrue(
                "${a.def.id} and ${b.def.id} are standing on top of one another",
                abs(a.x - b.x) > 100f
            )
        }
    }

    @Test
    fun `the cast is spread across the kingdom, not kept in the town`() {
        val regions = npcs.map { it.def.areaId }.distinct()
        assertTrue("the cast only reaches ${regions.size} regions", regions.size >= 6)
        assertEquals("Dustmere keeps three of them", 3, npcs.count { it.def.areaId == "town" })
    }

    @Test
    fun `every character is drawn as somebody else`() {
        assertEquals(8, npcs.size)
        assertEquals("two of the cast share a silhouette", npcs.size, npcs.map { it.def.kind }.distinct().size)
        for (n in npcs) {
            assertTrue("${n.def.id} has no colour", n.def.accent != 0)
            assertTrue("${n.def.id} has no title", n.def.title.isNotBlank())
        }
    }

    // -------------------------------------------------------------------- the book against the world

    @Test
    fun `every task has a giver who is standing somewhere`() {
        val givers = QuestBook.all.map { it.giver }
        assertEquals("two tasks are handed out by the same person", givers.size, givers.toSet().size)
        for (q in QuestBook.all) {
            val giver = npcs.firstOrNull { it.def.id == q.giver }
            assertNotNull("${q.id} is given by nobody", giver)
            assertEquals("${q.id} and its giver disagree", q.id, giver!!.def.questId)
        }
    }

    @Test
    fun `every character who offers a task offers exactly the one`() {
        for (n in npcs) {
            val questId = n.def.questId ?: continue
            val owned = QuestBook.all.filter { it.giver == n.def.id }
            assertEquals("${n.def.id} hands out ${owned.size} tasks", 1, owned.size)
            assertEquals(questId, owned[0].id)
        }
        val withTask = npcs.count { it.def.questId != null }
        assertEquals("seven of the eight have something to ask for", 7, withTask)
    }

    @Test
    fun `every objective points at something that exists`() {
        val puzzleIds = puzzles.map { it.spec.id }
        val npcIds = npcs.map { it.def.id }
        val bossIds = BossRoster.all.map { it.id }
        for (q in QuestBook.all) {
            for (o in q.objectives) {
                when (o.kind) {
                    ObjectiveKind.TALK -> assertTrue("${q.id} wants a conversation with nobody", o.target in npcIds)
                    ObjectiveKind.SOLVE -> assertTrue("${q.id} wants a puzzle that does not exist", o.target in puzzleIds)
                    ObjectiveKind.DEFEAT -> assertTrue("${q.id} wants a boss that does not exist", o.target in bossIds)
                    ObjectiveKind.COLLECT -> {
                        val have = relics.count { it.def.group == o.target }
                        assertTrue("${q.id} asks for $have relics and wants ${o.needed}", have >= o.needed)
                    }
                    ObjectiveKind.DISCOVER ->
                        assertTrue("${q.id} asks for more regions than there are", o.needed <= layout.grids.size)
                }
            }
        }
    }

    @Test
    fun `every task that asks for a puzzle can be finished by that puzzle`() {
        for (q in QuestBook.all) {
            for (o in q.objectives) {
                if (o.kind != ObjectiveKind.SOLVE) continue
                val puzzle = puzzles.first { it.spec.id == o.target }
                assertEquals(1, o.needed)
                assertTrue("${puzzle.spec.id} does not start solved", !puzzle.solved)
                assertTrue("${puzzle.spec.id} has something to press", puzzle.cells >= 3)
            }
        }
    }

    // -------------------------------------------------------------------- what they say

    @Test
    fun `nobody has nothing to say`() {
        for (n in npcs) {
            val d = n.def
            assertTrue("${d.id} does not introduce themselves", d.firstMeet.isNotEmpty())
            assertTrue("${d.id} has nothing to say twice", d.idle.isNotEmpty())
            if (d.questId != null) {
                assertTrue("${d.id} never offers the task", d.offer.isNotEmpty())
                assertTrue("${d.id} has no hint for the road", d.busy.isNotEmpty())
                assertTrue("${d.id} never takes the task back", d.ready.isNotEmpty())
                assertTrue("${d.id} has nothing to say afterwards", d.after.isNotEmpty())
            }
            val all = d.firstMeet + d.offer + d.busy + d.ready + d.after + d.idle
            for (line in all) {
                assertEquals("${d.id} speaks without a name", d.name, line.speaker)
                assertTrue("${d.id} says nothing", line.text.length > 12)
                assertTrue("${d.id} speaks too fast", line.seconds >= 3f)
                assertTrue("${d.id} says too much at once", line.text.length <= 190)
            }
        }
    }

    @Test
    fun `the second conversation is never the same as the first`() {
        for (n in npcs) {
            val d = n.def
            val first = d.firstMeet.map { it.text }.toSet()
            val later = (d.idle + d.after + d.busy).map { it.text }.toSet()
            assertTrue("${d.id} only knows one thing to say", first.intersect(later).isEmpty())
        }
    }

    // -------------------------------------------------------------------- the whole story

    @Test
    fun `the story builds against the real kingdom`() {
        assertEquals(8, story.npcs.size)
        assertEquals(4, story.puzzles.size)
        assertEquals(3, story.relics.size)
        assertEquals(QuestBook.all.size, story.log.ids.size)
        // The director owns every placement it was given, and nothing moved.
        for (n in story.npcs) assertTrue(n.floorY > 0f)
        for (p in story.puzzles) {
            val placed = puzzles.first { it.spec.id == p.spec.id }
            assertEquals(placed.x, p.x, 0.01f)
            assertEquals(placed.floorY, p.floorY, 0.01f)
        }
    }

    @Test
    fun `the story fits inside the areas it was authored into`() {
        for (n in npcs) {
            val g = layout.grid(n.def.areaId)
            val left = g.col0 * AreaDef.TILE
            val right = (g.col0 + g.width) * AreaDef.TILE
            assertTrue("${n.def.id} stands outside ${n.def.areaId}", n.x > left && n.x < right)
        }
        for (p in puzzles) {
            val g = layout.grid(p.spec.areaId)
            val left = g.col0 * AreaDef.TILE
            val right = (g.col0 + g.width) * AreaDef.TILE
            assertTrue("${p.spec.id} hangs outside ${p.spec.areaId}", p.x > left && p.cellX(p.cells - 1) < right)
            // The whole installation, including its art, stays inside the region.
            assertTrue(p.x - 90f > left)
            assertTrue(p.cellX(p.cells - 1) + 90f < right)
        }
        for (r in relics) {
            val g = layout.grid(r.def.areaId)
            val col = floor(r.x / AreaDef.TILE).toInt()
            assertTrue("${r.def.id} is outside ${r.def.areaId}", col in g.col0 until g.col0 + g.width)
        }
    }
}
