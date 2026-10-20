package com.devdgames.hollowknightbutmine.game.story

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.devdgames.hollowknightbutmine.game.boss.BossProgress
import com.devdgames.hollowknightbutmine.game.boss.BossRoster
import com.devdgames.hollowknightbutmine.game.puzzle.Puzzle
import com.devdgames.hollowknightbutmine.game.puzzle.PuzzleArt
import com.devdgames.hollowknightbutmine.game.puzzle.PuzzleAtlas
import com.devdgames.hollowknightbutmine.game.puzzle.PuzzleReply
import com.devdgames.hollowknightbutmine.game.world.WorldLayout
import kotlin.math.abs

/**
 * What the story is allowed to do to the world around it.
 *
 * The director never reaches into the player, the particles or the camera; it asks the stage
 * for shake, sparks and title cards. That keeps the whole story system runnable on the JVM
 * with a [SilentStage], which is how the tests drive a full playthrough.
 */
interface StoryStage {
    fun shake(intensity: Float, seconds: Float)
    fun sparks(x: Float, y: Float, count: Int, color: Int)
    fun announce(name: String, sub: String, seconds: Float)
}

/** A stage that does nothing, for tests and for headless runs. */
object SilentStage : StoryStage {
    override fun shake(intensity: Float, seconds: Float) {}
    override fun sparks(x: Float, y: Float, count: Int, color: Int) {}
    override fun announce(name: String, sub: String, seconds: Float) {}
}

/** One line of the compact tracker under the masks: a task and the next thing it wants. */
class TrackerEntry(val name: String, val line: String, val ready: Boolean)

/**
 * The story of a run: who is standing where, what they said, which stones are awake, which
 * shards are in a pocket, and how far every task has got.
 *
 * GameWorld owns one of these in place of the old single-area story slice. Everything it does
 * is driven by the player's position and a small sample of world state, so the kingdom can be
 * simulated — and tested — without a canvas.
 */
class StoryDirector(
    private val layout: WorldLayout,
    private val bosses: BossProgress,
    private val stage: StoryStage = SilentStage
) {

    val npcs: List<PlacedNpc> = NpcRoster.place(layout)
    val puzzles: List<Puzzle> = PuzzleAtlas.build(layout)
    val relics: List<PlacedRelic> = RelicRoster.place(layout)
    val log: QuestLog = QuestLog(QuestBook.all)
    val dialogue: DialogueQueue = DialogueQueue()

    private val solvedIds = LinkedHashSet<String>()
    private val takenIds = LinkedHashSet<String>()
    private val groupCounts = LinkedHashMap<String, Int>()

    private var dirty = true
    private var lastAreas = -1
    private var lastBosses = -1

    private var pendingMasks = 0
    private var pendingSoul = 0

    var toastText = ""
        private set
    var toastTimer = 0f
        private set

    /** The interactable the player is nearest to this frame, so the world can offer a prompt. */
    var nearestNpc: PlacedNpc? = null
        private set
    var nearestPuzzle: Puzzle? = null
        private set

    fun npc(id: String): PlacedNpc? = npcs.firstOrNull { it.def.id == id }
    fun puzzle(id: String): Puzzle? = puzzles.firstOrNull { it.spec.id == id }
    fun relic(id: String): PlacedRelic? = relics.firstOrNull { it.def.id == id }
    fun npcName(id: String): String = npc(id)?.def?.name ?: id

    fun relicCount(group: String): Int = groupCounts[group] ?: 0

    // ===================================================================== frame

    /**
     * One frame of story. [areasDiscovered] is the number of regions the player has set foot
     * in; the rest is sampled from the things this class owns.
     */
    fun update(dt: Float, px: Float, py: Float, areasDiscovered: Int) {
        dialogue.update(dt)
        if (toastTimer > 0f) {
            toastTimer -= dt
            if (toastTimer < 0f) toastTimer = 0f
        }
        for (p in puzzles) {
            val wasSolved = p.solved
            p.update(dt)
            if (p.solved && !wasSolved) markSolved(p)
        }
        for (r in relics) {
            if (r.takeAnim > 0f) {
                r.takeAnim -= dt
                if (r.takeAnim < 0f) r.takeAnim = 0f
            }
            if (!r.taken && abs(px - r.x) < RELIC_REACH && abs(py - r.floorY) < HEIGHT_TOLERANCE) take(r)
        }
        nearestNpc = npcInReach(px, py)
        nearestPuzzle = puzzleInReach(px, py)
        for (n in npcs) n.inReach = n === nearestNpc

        if (areasDiscovered != lastAreas) {
            lastAreas = areasDiscovered
            dirty = true
        }
        if (bosses.count != lastBosses) {
            lastBosses = bosses.count
            dirty = true
        }
        if (dirty) {
            dirty = false
            refresh()
        }
    }

    private fun markSolved(p: Puzzle) {
        if (solvedIds.add(p.spec.id)) dirty = true
        // The moment a puzzle gives in is spoken aloud; the toast is the quiet version, for the
        // case where nothing was said (a puzzle that solves itself between frames).
        if (!dialogue.speaking) toast(p.wokenLine)
    }

    private fun take(r: PlacedRelic) {
        r.taken = true
        r.takeAnim = 1.1f
        takenIds.add(r.def.id)
        groupCounts[r.def.group] = (groupCounts[r.def.group] ?: 0) + 1
        dirty = true
        stage.sparks(r.x, r.floorY - 40f, 20, r.def.accent)
        dialogue.play(listOf(DialogueLine(r.def.name.uppercase(), r.def.line, 4.5f)))
        val wanted = wantedCount(r.def.group)
        toast(
            if (wanted > 0) r.def.name + "  (" + relicCount(r.def.group) + " / " + wanted + ")"
            else r.def.name
        )
    }

    /** How many of a group any task wants, for the "2 / 3" in the toast. */
    private fun wantedCount(group: String): Int {
        var best = 0
        for (id in log.ids) {
            val s = log.state(id) ?: continue
            for (o in s.def.objectives) if (o.kind == ObjectiveKind.COLLECT && o.target == group && o.needed > best) best = o.needed
        }
        return best
    }

    private fun refresh() {
        val down = LinkedHashSet<String>()
        for (spec in BossRoster.all) if (bosses.isDefeated(spec.id)) down.add(spec.id)
        val changes = log.update(WorldObservation(lastAreas.coerceAtLeast(0), solvedIds, groupCounts, down))
        for (id in changes.offered) {
            val s = log.state(id) ?: continue
            toast("A task waits — " + s.def.name)
        }
        for (id in changes.ready) {
            val s = log.state(id) ?: continue
            toast("Ready to hand in — " + s.def.name + ", to " + npcName(s.def.giver))
        }
        rebuildTracker()
    }

    // ===================================================================== interaction

    /** True when Interact would talk, press a stone or skip a line rather than heal. */
    fun canInteract(px: Float, py: Float): Boolean {
        if (dialogue.speaking) return false
        return npcInReach(px, py) != null || puzzleInReach(px, py) != null
    }

    /** Interacts with the closest thing in reach. True when the press did something. */
    fun interact(px: Float, py: Float): Boolean {
        if (dialogue.speaking) return dialogue.skip()

        val n = npcInReach(px, py)
        val p = puzzleInReach(px, py)
        val npcDistance = if (n == null) Float.MAX_VALUE else abs(px - n.x)
        val puzzleDistance = if (p == null) Float.MAX_VALUE else puzzleDistance(p, px)
        return when {
            n != null && npcDistance <= puzzleDistance -> talkTo(n)
            p != null -> press(p, px)
            else -> false
        }
    }

    private fun npcInReach(px: Float, py: Float): PlacedNpc? {
        val n = npcBeside(px) ?: return null
        return if (abs(py - n.floorY) <= HEIGHT_TOLERANCE) n else null
    }

    /** The closest character to a world x, whatever height they are standing at. */
    private fun npcBeside(px: Float): PlacedNpc? {
        var best: PlacedNpc? = null
        var bestDistance = NPC_REACH
        for (n in npcs) {
            val d = abs(px - n.x)
            if (d <= bestDistance) {
                bestDistance = d
                best = n
            }
        }
        return best
    }

    private fun puzzleInReach(px: Float, py: Float): Puzzle? {
        val p = puzzleBeside(px) ?: return null
        return if (abs(py - p.floorY) <= HEIGHT_TOLERANCE) p else null
    }

    /** The closest puzzle to a world x, whatever height its controls are standing at. */
    private fun puzzleBeside(px: Float): Puzzle? {
        var best: Puzzle? = null
        var bestDistance = CELL_REACH
        for (p in puzzles) {
            if (p.cellNear(px, CELL_REACH) < 0) continue
            val d = puzzleDistance(p, px)
            if (d <= bestDistance) {
                bestDistance = d
                best = p
            }
        }
        return best
    }

    private fun puzzleDistance(p: Puzzle, px: Float): Float {
        var best = Float.MAX_VALUE
        for (i in 0 until p.cells) {
            val d = abs(px - p.cellX(i))
            if (d < best) best = d
        }
        return best
    }

    /**
     * Presses one control of a puzzle.
     *
     * Only the moments that matter — a puzzle giving in, or a mistake that costs something —
     * take the dialogue box. Ordinary feedback is a toast, so a puzzle that asks the player to
     * be quick never puts a paragraph in front of them.
     */
    private fun press(p: Puzzle, px: Float): Boolean {
        val cell = p.cellNear(px, CELL_REACH)
        if (cell < 0) return false
        val wasSolved = p.solved
        val reply: PuzzleReply = p.press(cell)
        if (reply.solvedNow || reply.failed) answer(reply.speaker, reply.text, reply.seconds) else toast(reply.text)
        if (reply.shake > 0f) stage.shake(reply.shake, 0.35f)
        if (reply.sparks > 0) stage.sparks(p.cellX(cell), p.floorY - 24f, reply.sparks, p.spec.accent)
        if (p.solved && !wasSolved) markSolved(p)
        return true
    }

    private fun talkTo(n: PlacedNpc): Boolean {
        val first = !n.met
        n.met = true
        n.talks++

        val quest = n.def.questId?.let { log.state(it) }
        val cue = when {
            first -> NpcCue.FIRST_MEET
            quest == null -> NpcCue.IDLE
            quest.stage == QuestStage.OFFERED -> NpcCue.OFFER
            quest.stage == QuestStage.ACTIVE -> NpcCue.BUSY
            quest.stage == QuestStage.READY -> NpcCue.READY
            quest.stage == QuestStage.DONE -> NpcCue.AFTER
            else -> NpcCue.IDLE
        }

        if (cue == NpcCue.OFFER && quest != null) {
            if (log.accept(quest.def.id)) {
                dirty = true
                stage.announce(quest.def.name, "a task taken", 3.2f)
                toast("Task taken — " + quest.def.name)
                stage.sparks(n.x, n.floorY - 46f, 14, n.def.accent)
            }
        } else if (cue == NpcCue.READY && quest != null) {
            turnIn(n, quest)
        }

        dialogue.play(n.def.linesFor(cue, n.talks))

        // Talking is an objective of its own somewhere in the book; count it wherever it lands.
        // A conversation can move an objective, so the tracker is rebuilt on the next frame.
        val changes = log.onSpokeTo(n.def.id)
        dirty = true
        for (id in changes.ready) {
            val s = log.state(id) ?: continue
            toast("Ready to hand in — " + s.def.name + ", to " + npcName(s.def.giver))
        }
        return true
    }

    private fun turnIn(n: PlacedNpc, quest: QuestState) {
        val def = log.turnIn(quest.def.id) ?: return
        pendingMasks += def.maskReward
        pendingSoul += def.soulReward
        dirty = true
        stage.announce(def.name, def.doneLine, 3.8f)
        stage.sparks(n.x, n.floorY - 52f, 26, n.def.accent)
        stage.shake(if (def.maskReward > 0) 8f else 4f, 0.4f)
        toast("Task complete — " + def.name)
    }

    /** Takes whatever the story owes the player. Called once per frame by the world. */
    fun drainReward(): StoryReward {
        if (pendingMasks == 0 && pendingSoul == 0) return StoryReward(0, 0)
        val r = StoryReward(pendingMasks, pendingSoul)
        pendingMasks = 0
        pendingSoul = 0
        return r
    }

    private fun answer(speaker: String, text: String, seconds: Float) {
        dialogue.play(listOf(DialogueLine(speaker, text, seconds)))
    }

    private fun toast(text: String) {
        toastText = text
        toastTimer = TOAST_TIME
    }

    // ===================================================================== the tracker

    private val trackerCache = ArrayList<TrackerEntry>()

    /**
     * What the corner of the screen shows: at most [TRACKER_LIMIT] tasks, ready ones first,
     * then the ones under way, in the order they were written. Rebuilt only when the story
     * actually moves, so a frame never allocates for it.
     */
    fun tracker(): List<TrackerEntry> = trackerCache

    private fun rebuildTracker() {
        trackerCache.clear()
        for (s in log.inStage(QuestStage.READY)) {
            if (trackerCache.size >= TRACKER_LIMIT) break
            trackerCache.add(TrackerEntry(s.def.name, "Return to " + npcName(s.def.giver), true))
        }
        for (s in log.inStage(QuestStage.ACTIVE)) {
            if (trackerCache.size >= TRACKER_LIMIT) break
            trackerCache.add(TrackerEntry(s.def.name, s.describe(s.firstOpen()), false))
        }
    }

    // ===================================================================== drawing

    /** The world layer: relics, puzzles and the cast, culled to the view. */
    fun drawWorld(canvas: Canvas, paint: Paint, time: Float, view: RectF) {
        for (r in relics) {
            if (r.taken && r.takeAnim <= 0f) continue
            if (r.x < view.left - 60f || r.x > view.right + 60f) continue
            if (r.floorY < view.top - 200f || r.floorY > view.bottom + 60f) continue
            RelicArt.draw(canvas, paint, r, time)
        }
        for (p in puzzles) PuzzleArt.draw(canvas, paint, p, time, view, p === nearestPuzzle)
        for (n in npcs) {
            if (n.x < view.left - 100f || n.x > view.right + 100f) continue
            if (n.floorY < view.top - 140f || n.floorY > view.bottom + 60f) continue
            NpcArt.draw(canvas, paint, n, time, n.inReach)
        }
    }

    /** The screen layer, in the order it should be read: tracker, toast, then the dialogue. */
    fun drawScreen(canvas: Canvas, paint: Paint, time: Float, screenWidth: Float, screenHeight: Float) {
        StoryHud.drawTracker(canvas, paint, trackerCache, screenWidth)
        StoryHud.drawToast(canvas, paint, toastText, toastTimer, screenWidth, screenHeight)
        StoryHud.drawDialogue(canvas, paint, dialogue, time, screenWidth, screenHeight)
    }

    /** The journal overlay, opened over the whole kingdom. */
    fun drawJournal(canvas: Canvas, paint: Paint, screenWidth: Float, screenHeight: Float) {
        StoryHud.drawJournal(canvas, paint, log, { id -> npcName(id) }, screenWidth, screenHeight)
    }

    companion object {
        /** How close the player has to be to speak to somebody. */
        const val NPC_REACH = 78f

        /** How close to press one control of a puzzle. */
        const val CELL_REACH = 62f

        /** Walking into a relic takes it. */
        const val RELIC_REACH = 46f

        /** Interaction only works at roughly the height of the thing being used. */
        const val HEIGHT_TOLERANCE = 115f

        const val TOAST_TIME = 4.2f

        /** How many tasks the corner of the screen will carry at once. */
        const val TRACKER_LIMIT = 2
    }
}
