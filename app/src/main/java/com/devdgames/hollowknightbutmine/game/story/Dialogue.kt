package com.devdgames.hollowknightbutmine.game.story

/**
 * Pure-Kotlin conversation plumbing (no Android types), so which line an NPC says for a
 * given moment of the story is unit-tested on the JVM. The drawing lives in [StoryHud].
 */

/** One panel of speech. [seconds] is how long it stays up before the next one follows. */
class DialogueLine(val speaker: String, val text: String, seconds: Float) {
    /** Panels are never so short that they cannot be read. */
    val seconds: Float = if (seconds < 0.6f) 0.6f else seconds
}

/** The moment of the story an NPC is answering for. Chosen by [StoryDirector]. */
enum class NpcCue {
    FIRST_MEET,   // Nobody has spoken to them yet this run.
    OFFER,        // They have a task to give and the player has not taken it.
    BUSY,         // The task is underway: they repeat the objective, with a hint.
    READY,        // Every objective is met: this is the turn-in.
    AFTER,        // The task is done; they have new, quieter things to say.
    IDLE          // Nothing to give (yet): lore, weather, warnings.
}

/**
 * A short queue of panels. Panels follow one another on their own once their time is up,
 * and Interact skips ahead — the player never has to wait for a slow speaker, and never
 * loses the tail of a long one.
 */
class DialogueQueue {
    private val lines = ArrayList<DialogueLine>()
    private var index = -1
    private var timer = 0f

    /** True while a panel is on screen. */
    val speaking: Boolean get() = index >= 0 && index < lines.size

    /** The panel on screen, or null. */
    val current: DialogueLine? get() = if (speaking) lines[index] else null

    /** Panels still to come after the one on screen. */
    val remaining: Int get() = if (speaking) lines.size - index - 1 else 0

    fun play(newLines: List<DialogueLine>) {
        lines.clear()
        lines.addAll(newLines)
        index = if (lines.isEmpty()) -1 else 0
        timer = if (index == 0) lines[0].seconds else 0f
    }

    /** Jumps to the next panel. True when one is now on screen. */
    fun skip(): Boolean {
        if (!speaking) return false
        index++
        if (index >= lines.size) {
            index = -1
            timer = 0f
            return false
        }
        timer = lines[index].seconds
        return true
    }

    fun update(dt: Float) {
        if (!speaking) return
        timer -= dt
        while (timer <= 0f) {
            index++
            if (index >= lines.size) {
                index = -1
                timer = 0f
                return
            }
            timer += lines[index].seconds
        }
    }

    fun clear() {
        lines.clear()
        index = -1
        timer = 0f
    }
}
