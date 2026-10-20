package com.devdgames.hollowknightbutmine.game.puzzle

/**
 * The four puzzles, as state machines.
 *
 * Each one is a different kind of thinking, so the kingdom never asks the same thing twice:
 * a sequence, a parity, a clock and a combination. All of them are deterministic and all of
 * them can be run head-first in a unit test.
 */

/**
 * THE SEALED DOOR — Dustmere.
 *
 * Three stones, each carved with a different number of notches. The lintel above them gives
 * the rule: wake them fewest notches first. A wrong stone does not punish, it resets, so the
 * puzzle can be tried again immediately.
 */
class RuneSealPuzzle(
    spec: PuzzleSpec,
    x: Float,
    floorY: Float,
    private val notches: IntArray
) : Puzzle(spec, x, floorY) {

    val lit = BooleanArray(notches.size)
    private var next = 0

    /** The order the stones want: fewest notches first. */
    val order: IntArray = notches.indices.sortedBy { notches[it] }.toIntArray()

    val woken: Int get() = next

    fun notchesOn(i: Int): Int = notches[i]

    override val wokenLine: String get() = "The sealed door opens"

    override fun awake(): Float = if (solved) 1f else next.toFloat() / cells.coerceAtLeast(1)

    override fun press(i: Int): PuzzleReply {
        if (i < 0 || i >= cells) return say("There is nothing there to wake.", 1.6f)
        if (solved) return say("The passage is open. Something below is breathing.", 4f)
        if (lit[i]) return say("This stone is already awake.", 2.2f)
        if (i == order[next]) {
            lit[i] = true
            next++
            if (next >= cells) {
                solved = true
                return PuzzleReply(
                    spec.name, "The stones fall silent. The passage opens... and a voice below whispers your name.",
                    6.5f, solvedNow = true, shake = 7f, sparks = 26
                )
            }
            return PuzzleReply(spec.name, "A low note answers from beneath the earth.", 2.6f, sparks = 8)
        }
        lit.fill(false)
        next = 0
        return PuzzleReply(spec.name, "The runes flare, then die. The sequence resets.", 3.2f, failed = true, shake = 5f, sparks = 10)
    }
}

/**
 * THE LITANY OF GRAVES — Resting Grounds.
 *
 * Five grave-lights in a row. Waking one wakes its neighbours too, so the row has to be
 * brought up together. It is scrambled by pressing stones from an all-lit row, which means
 * it is always solvable and the presses that made it will undo it.
 */
class LitanyPuzzle(
    spec: PuzzleSpec,
    x: Float,
    floorY: Float,
    private val scramble: IntArray
) : Puzzle(spec, x, floorY) {

    val lit = BooleanArray(cells)

    init {
        lit.fill(true)
        for (p in scramble) toggle(p)
    }

    val litCount: Int get() = lit.count { it }

    private fun toggle(i: Int) {
        if (i < 0 || i >= cells) return
        lit[i] = !lit[i]
        if (i > 0) lit[i - 1] = !lit[i - 1]
        if (i + 1 < cells) lit[i + 1] = !lit[i + 1]
    }

    override val wokenLine: String get() = "The litany is whole"

    override fun awake(): Float = litCount.toFloat() / cells.coerceAtLeast(1)

    override fun press(i: Int): PuzzleReply {
        if (i < 0 || i >= cells) return say("There is nothing there to wake.", 1.6f)
        if (solved) return say("The litany is whole. Let them sleep.", 3f)
        toggle(i)
        if (lit.all { it }) {
            solved = true
            return PuzzleReply(
                spec.name, "Every light burns at once. The graves settle, and are quiet, and are pleased.",
                6f, solvedNow = true, shake = 5f, sparks = 22
            )
        }
        return PuzzleReply(spec.name, "$litCount of the five hold their light. A neighbour always wakes.", 2.8f, sparks = 5)
    }
}

/**
 * THE FOUR FACETS — Glimmer Peak.
 *
 * Four crystal braziers that burn down in a few seconds. There is nothing to deduce and
 * nothing to memorise: the mountain asks whether you can cross its hall fast enough to hold
 * all four alight at the same moment.
 */
class BeaconPuzzle(
    spec: PuzzleSpec,
    x: Float,
    floorY: Float,
    /** Seconds a facet stays lit after it is kindled. */
    val burnTime: Float
) : Puzzle(spec, x, floorY) {

    val burn = FloatArray(cells)

    val litCount: Int get() = burn.count { it > 0f }

    /** 0..1 for the flame of one facet. */
    fun flame(i: Int): Float = if (solved) 1f else (burn[i] / burnTime).coerceIn(0f, 1f)

    override val wokenLine: String get() = "The four facets hold"

    override fun awake(): Float = if (solved) 1f else litCount.toFloat() / cells.coerceAtLeast(1)

    override fun update(dt: Float) {
        if (solved) return
        for (i in burn.indices) {
            if (burn[i] > 0f) {
                burn[i] -= dt
                if (burn[i] < 0f) burn[i] = 0f
            }
        }
    }

    override fun press(i: Int): PuzzleReply {
        if (i < 0 || i >= cells) return say("There is nothing there to kindle.", 1.6f)
        if (solved) return say("The facets burn steadily now. They have forgotten how to go out.", 3.4f)
        burn[i] = burnTime
        if (litCount >= cells) {
            solved = true
            return PuzzleReply(
                spec.name, "All four facets hold the light at once, and the mountain hums like a struck glass.",
                6f, solvedNow = true, shake = 6f, sparks = 30
            )
        }
        val burning = litCount
        return PuzzleReply(spec.name, "$burning facet" + (if (burning == 1) "" else "s") + " burn. The dark is quicker than it looks.", 2.4f, sparks = 7)
    }
}

/**
 * THE SLUICE GATE — Old Waterways.
 *
 * Four valve wheels on a drowned pipe. The tally scratched into the wall says how many must
 * run; the wheels remember nothing, so the arrangement has to be found and left alone.
 * Opening every wheel at once floods the chamber and spins them all back.
 */
class SluicePuzzle(
    spec: PuzzleSpec,
    x: Float,
    floorY: Float,
    private val target: BooleanArray
) : Puzzle(spec, x, floorY) {

    val open = BooleanArray(cells)

    val openCount: Int get() = open.count { it }

    fun wants(i: Int): Boolean = target[i]

    override val wokenLine: String get() = "The sluice gate opens"

    override fun awake(): Float = if (solved) 1f else openCount.toFloat() / cells.coerceAtLeast(1)

    override fun press(i: Int): PuzzleReply {
        if (i < 0 || i >= cells) return say("There is nothing there to turn.", 1.6f)
        if (solved) return say("The gate stands open. The waterways breathe.", 3.2f)
        open[i] = !open[i]
        if (open.contentEquals(target)) {
            solved = true
            return PuzzleReply(
                spec.name, "The gate groans once, and the water goes down the right way at last.",
                5.5f, solvedNow = true, shake = 9f, sparks = 20
            )
        }
        if (open.all { it }) {
            open.fill(false)
            return PuzzleReply(
                spec.name, "Every wheel wide open. The chamber screams, floods, and the wheels spin back shut.",
                4.5f, failed = true, shake = 13f, sparks = 16
            )
        }
        val running = openCount
        return PuzzleReply(spec.name, "$running of the four run. Somewhere below, water takes an interest.", 2.6f, sparks = 4)
    }
}
