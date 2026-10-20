package com.devdgames.hollowknightbutmine.story

import com.devdgames.hollowknightbutmine.game.story.ObjectiveKind
import com.devdgames.hollowknightbutmine.game.story.QuestLog
import com.devdgames.hollowknightbutmine.game.story.QuestStage
import com.devdgames.hollowknightbutmine.game.story.WorldObservation
import com.devdgames.hollowknightbutmine.game.story.QuestBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The task book, as rules.
 *
 * Nothing here needs a world: a task moves through its stages because it was accepted, because
 * somebody was spoken to, or because a sample of the kingdom said so. Those are the only three
 * ways anything in game/story can progress, and they are all pinned down below.
 */
class QuestLogTest {

    private fun log() = QuestLog(QuestBook.all)

    private fun obs(
        areas: Int = 0,
        solved: Set<String> = emptySet(),
        relics: Map<String, Int> = emptyMap(),
        bosses: Set<String> = emptySet()
    ) = WorldObservation(areas, solved, relics, bosses)

    @Test
    fun `the dustmere task is offered at once and the ones behind it wait`() {
        val log = log()
        assertEquals(QuestStage.OFFERED, log.stage("bell"))
        assertEquals(QuestStage.LOCKED, log.stage("lamplight"))
        assertEquals(QuestStage.LOCKED, log.stage("roads"))
        assertEquals("regional tasks belong to whoever is standing there", QuestStage.OFFERED, log.stage("litany"))
        assertEquals(QuestStage.OFFERED, log.stage("vigil"))
    }

    @Test
    fun `a task can only be taken once, and only when it is offered`() {
        val log = log()
        assertFalse(log.accept("lamplight"))
        assertTrue(log.accept("bell"))
        assertFalse("accepting twice does not restart it", log.accept("bell"))
        assertEquals(QuestStage.ACTIVE, log.stage("bell"))
    }

    @Test
    fun `the bell is finished by the witness and the stones, and pays once`() {
        val log = log()
        log.accept("bell")
        log.update(obs())
        val state = log.state("bell")!!
        assertFalse(state.allComplete)
        assertEquals(2, state.openCount)

        log.onSpokeTo("witness")
        assertTrue(state.isComplete(0))
        assertEquals("one of two is not ready", QuestStage.ACTIVE, state.stage)

        log.update(obs(solved = setOf("rune_seal")))
        assertTrue(state.allComplete)
        assertEquals(QuestStage.READY, state.stage)

        val paid = log.turnIn("bell")
        assertNotNull(paid)
        assertEquals(60, paid!!.soulReward)
        assertEquals(0, paid.maskReward)
        assertEquals(QuestStage.DONE, state.stage)
        assertNull("a task pays once", log.turnIn("bell"))
    }

    @Test
    fun `finishing the bell opens the two that follow it`() {
        val log = log()
        log.accept("bell")
        log.update(obs())
        log.onSpokeTo("witness")
        val changes = log.update(obs(solved = setOf("rune_seal")))
        assertTrue(changes.ready.contains("bell"))
        log.turnIn("bell")

        val after = log.update(obs())
        assertEquals(QuestStage.OFFERED, log.stage("lamplight"))
        assertEquals(QuestStage.OFFERED, log.stage("roads"))
        assertTrue("the world is told what opened", after.offered.containsAll(listOf("lamplight", "roads")))
    }

    @Test
    fun `speaking to somebody before a task asks for it does not count`() {
        val log = log()
        log.onSpokeTo("witness")
        log.accept("bell")
        log.update(obs())
        val state = log.state("bell")!!
        assertFalse("the task still wants the conversation", state.isComplete(0))
        log.onSpokeTo("witness")
        assertTrue(state.isComplete(0))
    }

    @Test
    fun `shards picked up early still count, because the world is sampled not told`() {
        val log = log()
        log.accept("bell")
        log.update(obs())
        log.onSpokeTo("witness")
        log.update(obs(solved = setOf("rune_seal")))
        log.turnIn("bell")
        log.update(obs(relics = mapOf("shard" to 3)))
        assertTrue(log.accept("lamplight"))
        log.update(obs(relics = mapOf("shard" to 3)))
        assertEquals(QuestStage.READY, log.stage("lamplight"))
    }

    @Test
    fun `a counted objective reports what it has and never more than it asked for`() {
        val log = log()
        log.accept("bell")
        log.update(obs())
        log.onSpokeTo("witness")
        log.update(obs(solved = setOf("rune_seal")))
        log.turnIn("bell")
        log.update(obs())
        log.accept("lamplight")

        log.update(obs(relics = mapOf("shard" to 1)))
        val state = log.state("lamplight")!!
        assertEquals(1, state.progress[0])
        assertEquals("Gather the lantern shards  (1 / 3)", state.describe(0))
        assertEquals(QuestStage.ACTIVE, state.stage)

        log.update(obs(relics = mapOf("shard" to 9)))
        assertEquals("it cannot be over-collected", 3, state.progress[0])
        assertEquals(QuestStage.READY, state.stage)
    }

    @Test
    fun `eight regions charted finishes the map task`() {
        val log = log()
        log.accept("bell")
        log.update(obs())
        log.onSpokeTo("witness")
        log.update(obs(solved = setOf("rune_seal")))
        log.turnIn("bell")
        log.update(obs())
        log.accept("roads")

        log.update(obs(areas = 7))
        assertEquals(QuestStage.ACTIVE, log.stage("roads"))
        assertEquals("Stand in eight regions of the kingdom  (7 / 8)", log.state("roads")!!.describe(0))
        log.update(obs(areas = 8))
        assertEquals(QuestStage.READY, log.stage("roads"))
    }

    @Test
    fun `the last task wants both bosses of the east wing`() {
        val log = log()
        log.accept("vigil")
        log.update(obs(bosses = setOf("keener")))
        assertEquals(QuestStage.ACTIVE, log.stage("vigil"))
        assertEquals(1, log.state("vigil")!!.openCount)
        log.update(obs(bosses = setOf("keener", "shardback")))
        assertEquals("both of them is the record", QuestStage.READY, log.stage("vigil"))
        assertEquals(0, log.state("vigil")!!.openCount)
        val paid = log.turnIn("vigil")
        assertEquals(1, paid!!.maskReward)
    }

    @Test
    fun `an objective already met is not taken back off by a partial sample`() {
        val log = log()
        log.accept("vigil")
        log.update(obs(bosses = setOf("keener")))
        assertEquals(1, log.state("vigil")!!.openCount)
        // BossProgress only ever grows, so this cannot happen in the game. But the log is sampled
        // rather than told, and a sample that happens to leave a kill out must not un-finish an
        // objective the player has already done: progress ratchets, it does not flap.
        log.update(obs(bosses = emptySet()))
        assertEquals(QuestStage.ACTIVE, log.stage("vigil"))
        assertEquals("the Keener is still down as far as the task is concerned", 1, log.state("vigil")!!.openCount)
    }

    @Test
    fun `several tasks can be under way and each keeps its own progress`() {
        val log = log()
        log.accept("bell")
        log.accept("litany")
        log.accept("facets")
        log.update(obs(solved = setOf("beacon")))
        assertEquals(QuestStage.READY, log.stage("facets"))
        assertEquals(QuestStage.ACTIVE, log.stage("litany"))
        assertEquals(QuestStage.ACTIVE, log.stage("bell"))
        assertEquals(2, log.inStage(QuestStage.ACTIVE).size)
        assertEquals(1, log.inStage(QuestStage.READY).size)
    }

    @Test
    fun `every task in the book is well formed`() {
        val ids = QuestBook.all.map { it.id }
        assertEquals("task ids are unique", ids.size, ids.toSet().size)
        for (q in QuestBook.all) {
            assertTrue("${q.id} asks for something", q.objectives.isNotEmpty())
            assertTrue("${q.id} pays something", q.soulReward > 0 || q.maskReward > 0)
            assertTrue("${q.id} names itself", q.name.isNotBlank())
            assertTrue("${q.id} explains itself", q.brief.length > 20)
            val prereq = q.requires
            if (prereq != null) {
                assertTrue("${q.id} waits on a task that does not exist", prereq in ids)
                assertFalse("${q.id} cannot wait on itself", q.id == prereq)
            }
            for (o in q.objectives) {
                assertTrue("${q.id} has an objective with no words", o.label.isNotBlank())
                assertTrue("${q.id} asks for at least one", o.needed >= 1)
                if (o.kind != ObjectiveKind.DISCOVER) assertTrue("${q.id} names what it wants", o.target.isNotBlank())
            }
        }
    }

    @Test
    fun `the book never asks for more masks than the kingdom can pay`() {
        val masks = QuestBook.all.sumOf { it.maskReward }
        assertTrue("three masks from tasks, on top of the two the bosses pay", masks in 1..3)
    }
}
