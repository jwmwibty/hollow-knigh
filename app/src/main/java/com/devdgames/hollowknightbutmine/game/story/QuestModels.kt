package com.devdgames.hollowknightbutmine.game.story

/**
 * The task system: what the kingdom asks of the player, and how far along it is.
 *
 * Pure Kotlin — no Android types — because a task is a rule, not a picture, and every rule
 * here is unit-tested on the JVM. The authored tasks live in [QuestBook]; the drawing of the
 * tracker and the journal lives in [StoryHud].
 */

/** What one objective of a task asks for. */
enum class ObjectiveKind {
    TALK,       // Speak with someone: target is an NPC id.
    SOLVE,      // Wake a puzzle: target is a puzzle id.
    COLLECT,    // Gather relics: target is a relic group, `needed` is the count.
    DISCOVER,   // Set foot in `needed` areas of the kingdom.
    DEFEAT      // Put down a boss: target is a boss id.
}

/** How far a task has got. */
enum class QuestStage {
    LOCKED,     // Its prerequisite is not finished; the giver says nothing about it yet.
    OFFERED,    // Waiting to be accepted from the giver.
    ACTIVE,     // Under way.
    READY,      // Every objective met; back to the giver.
    DONE        // Turned in and rewarded.
}

/** One thing the player must do. [label] is what the journal and tracker show. */
class Objective(
    val kind: ObjectiveKind,
    val target: String,
    val label: String,
    val needed: Int = 1
)

/** A task: who gives it, what it asks, and what it pays. */
class QuestDef(
    val id: String,
    val name: String,
    /** The NPC who offers it and takes it back in. */
    val giver: String,
    /** One or two sentences of intent, shown in the journal. */
    val brief: String,
    val objectives: List<Objective>,
    val soulReward: Int = 0,
    /** Permanent masks, granted once, exactly like a boss victory. */
    val maskReward: Int = 0,
    /** The task that must be finished before this one can be offered. */
    val requires: String? = null,
    /** Shown on the title card when the task is turned in. */
    val doneLine: String = ""
)

/** Everything a task needs to know about the world, sampled once per frame. */
class WorldObservation(
    val areasDiscovered: Int,
    val solvedPuzzles: Set<String>,
    /** Relics held, counted per group (see [RelicDef.group]). */
    val relicGroups: Map<String, Int>,
    val bossesDown: Set<String>
)

/** What a finished task pays. Applied by the world, never by the log. */
class StoryReward(val masks: Int, val soul: Int) {
    val isEmpty: Boolean get() = masks <= 0 && soul <= 0
}

/** What one pass over the world changed, so the story can announce it. */
class QuestChanges {
    val offered = ArrayList<String>()
    val ready = ArrayList<String>()
    val isEmpty: Boolean get() = offered.isEmpty() && ready.isEmpty()
}

/** Where one task stands, and how far each of its objectives has got. */
class QuestState(val def: QuestDef) {
    var stage: QuestStage = if (def.requires == null) QuestStage.OFFERED else QuestStage.LOCKED
    /** Counted progress per objective: talks, stones woken, relics held, areas charted. */
    val progress = IntArray(def.objectives.size)

    fun isComplete(i: Int): Boolean = progress[i] >= def.objectives[i].needed
    val allComplete: Boolean get() = def.objectives.indices.all { isComplete(it) }
    val openCount: Int get() = def.objectives.indices.count { !isComplete(it) }

    /** "Gather the shards (2 / 3)" — the label plus its count when there is one to show. */
    fun describe(i: Int): String {
        val o = def.objectives[i]
        if (o.needed <= 1) return o.label
        val got = progress[i].coerceAtMost(o.needed)
        return o.label + "  (" + got + " / " + o.needed + ")"
    }

    /** The first objective still outstanding, for the compact tracker. */
    fun firstOpen(): Int {
        for (i in def.objectives.indices) if (!isComplete(i)) return i
        return def.objectives.size - 1
    }
}

/**
 * The kingdom's task book.
 *
 * The log never looks at the world itself: it is handed a [WorldObservation] and a couple of
 * events (somebody was spoken to), and it moves tasks through their stages. Counted
 * objectives are polled rather than pushed, so a shard picked up before the task was
 * accepted still counts, and a boss slain in passing is never missed.
 */
class QuestLog(defs: List<QuestDef>) {

    /** Tasks in the order they were authored; the journal keeps that order. */
    val ids: List<String> = defs.map { it.id }

    val states: Map<String, QuestState>

    init {
        val byId = LinkedHashMap<String, QuestState>()
        for (d in defs) byId[d.id] = QuestState(d)
        states = byId
    }

    fun state(id: String): QuestState? = states[id]

    fun stage(id: String): QuestStage = states[id]?.stage ?: QuestStage.LOCKED

    fun def(id: String): QuestDef? = states[id]?.def

    /** The task this NPC hands out, if any. */
    fun forGiver(npcId: String): QuestState? {
        for (s in states.values) if (s.def.giver == npcId) return s
        return null
    }

    fun inStage(stage: QuestStage): List<QuestState> {
        val out = ArrayList<QuestState>()
        for (id in ids) {
            val s = states[id]
            if (s != null && s.stage == stage) out.add(s)
        }
        return out
    }

    val doneCount: Int get() = inStage(QuestStage.DONE).size

    /** Takes a task on. Only an offered task can be accepted. */
    fun accept(id: String): Boolean {
        val s = states[id] ?: return false
        if (s.stage != QuestStage.OFFERED) return false
        s.stage = QuestStage.ACTIVE
        return true
    }

    /**
     * Samples the world. Returns the tasks that changed stage, so the caller can say so.
     * Talking is an event (see [onSpokeTo]); everything else is read from [obs].
     */
    fun update(obs: WorldObservation): QuestChanges {
        val changes = QuestChanges()
        for (id in ids) {
            val s = states[id] ?: continue
            if (s.stage == QuestStage.LOCKED) {
                val prereq = s.def.requires
                if (prereq != null && stage(prereq) == QuestStage.DONE) {
                    s.stage = QuestStage.OFFERED
                    changes.offered.add(id)
                }
                continue
            }
            if (s.stage != QuestStage.ACTIVE) continue
            for (i in s.def.objectives.indices) {
                val o = s.def.objectives[i]
                when (o.kind) {
                    ObjectiveKind.TALK -> { /* an event, not a sample */ }
                    ObjectiveKind.SOLVE -> if (o.target in obs.solvedPuzzles) s.progress[i] = o.needed
                    ObjectiveKind.COLLECT -> s.progress[i] = (obs.relicGroups[o.target] ?: 0).coerceAtMost(o.needed)
                    ObjectiveKind.DISCOVER -> s.progress[i] = obs.areasDiscovered.coerceAtMost(o.needed)
                    ObjectiveKind.DEFEAT -> if (o.target in obs.bossesDown) s.progress[i] = o.needed
                }
            }
            if (s.allComplete) {
                s.stage = QuestStage.READY
                changes.ready.add(id)
            }
        }
        return changes
    }

    /** Somebody was spoken to: any active task that asked for them counts it. */
    fun onSpokeTo(npcId: String): QuestChanges {
        val changes = QuestChanges()
        for (id in ids) {
            val s = states[id] ?: continue
            if (s.stage != QuestStage.ACTIVE) continue
            var moved = false
            for (i in s.def.objectives.indices) {
                val o = s.def.objectives[i]
                if (o.kind == ObjectiveKind.TALK && o.target == npcId) {
                    s.progress[i] = o.needed
                    moved = true
                }
            }
            if (moved && s.allComplete) {
                s.stage = QuestStage.READY
                changes.ready.add(id)
            }
        }
        return changes
    }

    /** Hands a finished task back. Returns the task only when a reward is actually due. */
    fun turnIn(id: String): QuestDef? {
        val s = states[id] ?: return null
        if (s.stage != QuestStage.READY) return null
        s.stage = QuestStage.DONE
        return s.def
    }
}
