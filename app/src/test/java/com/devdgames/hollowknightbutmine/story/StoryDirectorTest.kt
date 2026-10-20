package com.devdgames.hollowknightbutmine.story

import com.devdgames.hollowknightbutmine.game.boss.BossProgress
import com.devdgames.hollowknightbutmine.game.puzzle.BeaconPuzzle
import com.devdgames.hollowknightbutmine.game.puzzle.PuzzleAtlas
import com.devdgames.hollowknightbutmine.game.puzzle.RuneSealPuzzle
import com.devdgames.hollowknightbutmine.game.puzzle.SluicePuzzle
import com.devdgames.hollowknightbutmine.game.story.QuestStage
import com.devdgames.hollowknightbutmine.game.story.StoryDirector
import com.devdgames.hollowknightbutmine.game.story.StoryStage
import com.devdgames.hollowknightbutmine.game.world.WorldAtlas
import com.devdgames.hollowknightbutmine.game.world.WorldLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A run of the game, played by a test.
 *
 * The director is driven exactly the way the world drives it — a position, a frame, an
 * Interact press — and the stage it performs on records what it asked for. Every task in the
 * book is finished here at least once, in the order a player would meet them.
 */
class StoryDirectorTest {

    private class RecordingStage : StoryStage {
        var shakes = 0
            private set
        var sparkBursts = 0
            private set
        val announced = ArrayList<String>()

        override fun shake(intensity: Float, seconds: Float) {
            shakes++
        }

        override fun sparks(x: Float, y: Float, count: Int, color: Int) {
            sparkBursts++
        }

        override fun announce(name: String, sub: String, seconds: Float) {
            announced.add(name)
        }
    }

    private val layout = WorldLayout(WorldAtlas.areas)
    private val bosses = BossProgress()
    private val stage = RecordingStage()
    private val story = StoryDirector(layout, bosses, stage)

    // -------------------------------------------------------------------- helpers

    private fun step(x: Float, y: Float, areas: Int = 1) = story.update(1f / 60f, x, y, areas)

    /** Runs frames at a position until whatever is being said has finished being said. */
    private fun settle(x: Float, y: Float, areas: Int = 1) {
        var t = 0f
        do {
            story.update(0.25f, x, y, areas)
            t += 0.25f
        } while (story.dialogue.speaking && t < 40f)
    }

    private fun press(x: Float, y: Float) {
        assertTrue("nothing to use at ($x, $y)", story.interact(x, y))
    }

    private fun talkAndSettle(x: Float, y: Float, areas: Int = 1) {
        press(x, y)
        settle(x, y, areas)
    }

    /** Dustmere's story, from the greeting to the reward, the way a player would do it. */
    private fun finishBell() {
        val keeper = story.npc("keeper")!!
        val witness = story.npc("witness")!!
        val seal = story.puzzle("rune_seal")!! as RuneSealPuzzle
        talkAndSettle(keeper.x, keeper.floorY)
        talkAndSettle(keeper.x, keeper.floorY)
        assertEquals(QuestStage.ACTIVE, story.log.stage("bell"))
        talkAndSettle(witness.x, witness.floorY)
        for (i in seal.order) story.interact(seal.cellX(i), seal.floorY)
        settle(seal.cellX(0), seal.floorY)
        assertTrue(seal.solved)
        step(keeper.x, keeper.floorY)
        assertEquals(QuestStage.READY, story.log.stage("bell"))
        talkAndSettle(keeper.x, keeper.floorY)
        assertEquals(QuestStage.DONE, story.log.stage("bell"))
        story.drainReward()
    }

    // -------------------------------------------------------------------- conversation

    @Test
    fun `somebody introduces themselves before they ask for anything`() {
        val keeper = story.npc("keeper")!!
        step(keeper.x, keeper.floorY)
        assertTrue("they are in reach", story.canInteract(keeper.x, keeper.floorY))
        press(keeper.x, keeper.floorY)
        assertEquals("THE LANTERN KEEPER", story.dialogue.current?.speaker)
        assertEquals("a first meeting is not an acceptance", QuestStage.OFFERED, story.log.stage("bell"))

        settle(keeper.x, keeper.floorY)
        press(keeper.x, keeper.floorY)
        assertEquals(QuestStage.ACTIVE, story.log.stage("bell"))
        assertTrue("taking a task is worth a title card", stage.announced.contains("The Bell That Rang"))
    }

    @Test
    fun `a long greeting can be skipped, and the press after it takes the task`() {
        val keeper = story.npc("keeper")!!
        press(keeper.x, keeper.floorY)
        assertEquals(1, story.dialogue.remaining)
        assertTrue("skipping goes forward", story.interact(keeper.x, keeper.floorY))
        assertEquals(0, story.dialogue.remaining)
        assertFalse("the last line has nothing after it", story.interact(keeper.x, keeper.floorY))
        assertFalse(story.dialogue.speaking)
        assertTrue("and the next press is a conversation again", story.interact(keeper.x, keeper.floorY))
        assertEquals(QuestStage.ACTIVE, story.log.stage("bell"))
    }

    @Test
    fun `the same person never says the same thing twice in a row`() {
        val witness = story.npc("witness")!!
        press(witness.x, witness.floorY)
        val first = story.dialogue.current?.text ?: ""
        settle(witness.x, witness.floorY)
        press(witness.x, witness.floorY)
        val second = story.dialogue.current?.text ?: ""
        settle(witness.x, witness.floorY)
        assertTrue("the witness has nothing else to say", first.isNotEmpty() && second.isNotEmpty())
        assertTrue("the witness repeated himself", first != second)
    }

    @Test
    fun `nobody can be spoken to from the wrong height, or from empty air`() {
        val keeper = story.npc("keeper")!!
        assertFalse(story.canInteract(keeper.x, keeper.floorY - 400f))
        assertFalse(story.interact(keeper.x, keeper.floorY - 400f))
        assertFalse(story.canInteract(0f, 0f))
        assertFalse(story.interact(0f, 0f))
    }

    // -------------------------------------------------------------------- dustmere

    @Test
    fun `the bell, the witness and the stones finish Dustmere's story`() {
        finishBell()
        assertEquals(QuestStage.DONE, story.log.stage("bell"))
        assertEquals(QuestStage.OFFERED, story.log.stage("lamplight"))
        assertEquals(QuestStage.OFFERED, story.log.stage("roads"))
        assertTrue("the seal was woken in the world, not in a menu", stage.sparkBursts > 0)
        assertTrue("and it was worth a title card", stage.announced.isNotEmpty())
    }

    @Test
    fun `turning a task in pays once and only once`() {
        val keeper = story.npc("keeper")!!
        val witness = story.npc("witness")!!
        val seal = story.puzzle("rune_seal")!! as RuneSealPuzzle
        talkAndSettle(keeper.x, keeper.floorY)
        talkAndSettle(keeper.x, keeper.floorY)
        talkAndSettle(witness.x, witness.floorY)
        for (i in seal.order) story.interact(seal.cellX(i), seal.floorY)
        settle(seal.cellX(0), seal.floorY)
        step(keeper.x, keeper.floorY)
        assertEquals(QuestStage.READY, story.log.stage("bell"))
        talkAndSettle(keeper.x, keeper.floorY)

        val reward = story.drainReward()
        assertEquals(60, reward.soul)
        assertEquals(0, reward.masks)
        assertTrue("it does not pay twice", story.drainReward().isEmpty)
    }

    @Test
    fun `the stones reset when they are woken out of order`() {
        val seal = story.puzzle("rune_seal")!! as RuneSealPuzzle
        val wrong = (0 until seal.cells).first { it != seal.order[0] }
        story.interact(seal.cellX(wrong), seal.floorY)
        settle(seal.x, seal.floorY)
        assertFalse(seal.solved)
        assertEquals(0, seal.woken)
        assertTrue("a reset is worth saying out loud", stage.shakes > 0)
        for (i in seal.order) story.interact(seal.cellX(i), seal.floorY)
        settle(seal.x, seal.floorY)
        assertTrue(seal.solved)
    }

    @Test
    fun `the sealed door can be opened before anybody asked for it`() {
        val seal = story.puzzle("rune_seal")!! as RuneSealPuzzle
        for (i in seal.order) story.interact(seal.cellX(i), seal.floorY)
        settle(seal.x, seal.floorY)
        assertTrue(seal.solved)

        val keeper = story.npc("keeper")!!
        talkAndSettle(keeper.x, keeper.floorY)
        talkAndSettle(keeper.x, keeper.floorY)
        talkAndSettle(story.npc("witness")!!.x, story.npc("witness")!!.floorY)
        step(keeper.x, keeper.floorY)
        assertEquals("the world is sampled, so nothing is missed", QuestStage.READY, story.log.stage("bell"))
    }

    // -------------------------------------------------------------------- relics

    @Test
    fun `walking into a shard takes it, and says so`() {
        val shard = story.relic("shard_cliffs")!!
        step(shard.x + 240f, shard.floorY)
        assertFalse("it is not taken from across the room", shard.taken)
        step(shard.x, shard.floorY)
        assertTrue(shard.taken)
        assertEquals(1, story.relicCount("shard"))
        assertTrue("a relic says something when it is picked up", story.dialogue.speaking)
        assertTrue(stage.sparkBursts > 0)
    }

    @Test
    fun `three shards finish the lamplighter's errand whenever they were gathered`() {
        assertEquals("the errand waits behind the bell", QuestStage.LOCKED, story.log.stage("lamplight"))
        for (r in story.relics) {
            step(r.x, r.floorY)
            settle(r.x, r.floorY)
        }
        assertEquals(3, story.relicCount("shard"))

        finishBell()
        val sable = story.npc("sable")!!
        assertEquals("and the bell opens it", QuestStage.OFFERED, story.log.stage("lamplight"))
        talkAndSettle(sable.x, sable.floorY)
        talkAndSettle(sable.x, sable.floorY)
        // Taken and finished in the same breath: the shards were in hand before she asked, and the
        // world is sampled every frame, so the errand is ready the moment it is accepted.
        assertEquals("the shards were already in hand", QuestStage.READY, story.log.stage("lamplight"))
        talkAndSettle(sable.x, sable.floorY)
        assertEquals(QuestStage.DONE, story.log.stage("lamplight"))
        assertEquals(50, story.drainReward().soul)
    }

    // -------------------------------------------------------------------- the regions

    @Test
    fun `the cartographer's task is finished by walking, not by pressing`() {
        finishBell()
        val quill = story.npc("quill")!!
        talkAndSettle(quill.x, quill.floorY)
        talkAndSettle(quill.x, quill.floorY)
        assertEquals(QuestStage.ACTIVE, story.log.stage("roads"))
        settle(quill.x, quill.floorY, areas = 7)
        assertEquals(QuestStage.ACTIVE, story.log.stage("roads"))
        settle(quill.x, quill.floorY, areas = 8)
        assertEquals(QuestStage.READY, story.log.stage("roads"))
        talkAndSettle(quill.x, quill.floorY, areas = 8)
        assertEquals(QuestStage.DONE, story.log.stage("roads"))
        assertEquals(40, story.drainReward().soul)
    }

    @Test
    fun `the sister's rite pays in a mask`() {
        val vell = story.npc("vell")!!
        val litany = story.puzzle("litany")!!
        talkAndSettle(vell.x, vell.floorY)
        talkAndSettle(vell.x, vell.floorY)
        assertEquals(QuestStage.ACTIVE, story.log.stage("litany"))
        for (i in PuzzleAtlas.litanyScramble) story.interact(litany.cellX(i), litany.floorY)
        settle(litany.x, litany.floorY)
        assertTrue(litany.solved)
        step(vell.x, vell.floorY)
        assertEquals(QuestStage.READY, story.log.stage("litany"))
        talkAndSettle(vell.x, vell.floorY)
        assertEquals(QuestStage.DONE, story.log.stage("litany"))
        val reward = story.drainReward()
        assertEquals(1, reward.masks)
        assertEquals(33, reward.soul)
    }

    @Test
    fun `the facets can be run in one go without a paragraph in the way`() {
        val beacon = story.puzzle("beacon")!!
        story.interact(beacon.cellX(0), beacon.floorY)
        assertFalse("a timed puzzle keeps the dialogue box shut", story.dialogue.speaking)
        assertTrue("but it still answers", story.toastText.isNotEmpty())
        for (i in 1 until beacon.cells) {
            story.interact(beacon.cellX(i), beacon.floorY)
            story.update(0.1f, beacon.cellX(i), beacon.floorY, 1)
        }
        assertTrue(beacon.solved)
        assertTrue("the moment it holds is worth reading", story.dialogue.speaking)
    }

    @Test
    fun `the facets go out if the run is too slow`() {
        val beacon = story.puzzle("beacon")!!
        for (i in 0 until beacon.cells) {
            story.interact(beacon.cellX(i), beacon.floorY)
            // Half a burn between braziers: by the time the last is lit the first is cold.
            story.update(PuzzleAtlas.BEACON_BURN * 0.5f, beacon.cellX(i), beacon.floorY, 1)
        }
        assertFalse(beacon.solved)
        assertEquals("only the last one is still alight", 1, (beacon as BeaconPuzzle).litCount)
    }

    @Test
    fun `opening every wheel floods the sluice and costs the player the attempt`() {
        val sluice = story.puzzle("sluice")!! as SluicePuzzle
        for (i in 0 until sluice.cells) story.interact(sluice.cellX(i), sluice.floorY)
        settle(sluice.x, sluice.floorY)
        assertFalse(sluice.solved)
        assertEquals(0, sluice.openCount)
        assertTrue("the chamber shakes when it floods", stage.shakes > 0)
        story.interact(sluice.cellX(0), sluice.floorY)
        story.interact(sluice.cellX(2), sluice.floorY)
        story.interact(sluice.cellX(3), sluice.floorY)
        settle(sluice.x, sluice.floorY)
        assertTrue("and it can be set properly straight afterwards", sluice.solved)
    }

    @Test
    fun `the sluice-keeper's task is behind the gate he cannot remember`() {
        val gristle = story.npc("gristle")!!
        talkAndSettle(gristle.x, gristle.floorY)
        talkAndSettle(gristle.x, gristle.floorY)
        assertEquals(QuestStage.ACTIVE, story.log.stage("sluice"))
        val sluice = story.puzzle("sluice")!!
        for (i in 0 until sluice.cells) if (i != 1) story.interact(sluice.cellX(i), sluice.floorY)
        settle(sluice.x, sluice.floorY)
        step(gristle.x, gristle.floorY)
        assertEquals(QuestStage.READY, story.log.stage("sluice"))
        talkAndSettle(gristle.x, gristle.floorY)
        assertEquals(QuestStage.DONE, story.log.stage("sluice"))
        assertEquals(66, story.drainReward().soul)
    }

    // -------------------------------------------------------------------- the bottom of the world

    @Test
    fun `the scribe's record wants both bosses of the east wing`() {
        val scribe = story.npc("scribe")!!
        talkAndSettle(scribe.x, scribe.floorY)
        talkAndSettle(scribe.x, scribe.floorY)
        assertEquals(QuestStage.ACTIVE, story.log.stage("vigil"))

        bosses.defeat("keener")
        step(scribe.x, scribe.floorY)
        assertEquals("one of two is not the record", QuestStage.ACTIVE, story.log.stage("vigil"))

        bosses.defeat("shardback")
        step(scribe.x, scribe.floorY)
        assertEquals(QuestStage.READY, story.log.stage("vigil"))

        talkAndSettle(scribe.x, scribe.floorY)
        assertEquals(QuestStage.DONE, story.log.stage("vigil"))
        assertEquals(1, story.drainReward().masks)
    }

    // -------------------------------------------------------------------- the tracker

    @Test
    fun `the tracker carries the task and the next thing it wants`() {
        val keeper = story.npc("keeper")!!
        talkAndSettle(keeper.x, keeper.floorY)
        assertTrue("nothing is under way yet", story.tracker().isEmpty())
        talkAndSettle(keeper.x, keeper.floorY)
        step(keeper.x, keeper.floorY)

        val entries = story.tracker()
        assertEquals(1, entries.size)
        assertEquals("The Bell That Rang", entries[0].name)
        assertFalse(entries[0].ready)
        assertTrue("it names the first objective", entries[0].line.contains("Witness"))

        talkAndSettle(story.npc("witness")!!.x, story.npc("witness")!!.floorY)
        step(keeper.x, keeper.floorY)
        assertTrue("and then the next one", story.tracker()[0].line.contains("stones"))

        val seal = story.puzzle("rune_seal")!! as RuneSealPuzzle
        for (i in seal.order) story.interact(seal.cellX(i), seal.floorY)
        settle(keeper.x, keeper.floorY)
        step(keeper.x, keeper.floorY)
        assertTrue(story.tracker()[0].ready)
        assertTrue(story.tracker()[0].line.contains("Return to"))
        assertEquals("the tracker never carries more than two tasks", 2, StoryDirector.TRACKER_LIMIT)
    }

    @Test
    fun `the tracker has room for two tasks and no more`() {
        finishBell()
        val vell = story.npc("vell")!!
        talkAndSettle(vell.x, vell.floorY)
        talkAndSettle(vell.x, vell.floorY)
        val quill = story.npc("quill")!!
        talkAndSettle(quill.x, quill.floorY)
        talkAndSettle(quill.x, quill.floorY)
        step(vell.x, vell.floorY)
        assertEquals(2, story.tracker().size)
    }

    @Test
    fun `nothing is owed when nothing was finished`() {
        step(story.npc("keeper")!!.x, story.npc("keeper")!!.floorY)
        val reward = story.drainReward()
        assertTrue(reward.isEmpty)
        assertEquals(0, reward.masks)
        assertEquals(0, reward.soul)
    }
}
