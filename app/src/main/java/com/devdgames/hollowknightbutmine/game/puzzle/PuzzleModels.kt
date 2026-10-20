package com.devdgames.hollowknightbutmine.game.puzzle

import kotlin.math.abs

/**
 * The kingdom's puzzles: models and rules.
 *
 * Pure Kotlin (no Android types), exactly like game/boss — a puzzle is a small state machine
 * the player presses, and every rule is unit-tested on the JVM. The pictures are in
 * [PuzzleArt]; the authored placements are in [PuzzleAtlas].
 */

/** The four kinds of puzzle the kingdom keeps. Each is a different way of thinking. */
enum class PuzzleKind {
    RUNE_SEAL,   // A sequence: wake the stones in the order their notches ask for.
    LITANY,      // A parity: each light wakes its neighbours, so all must burn at once.
    BEACON,      // A clock: four facets, none of which stays lit for long.
    SLUICE       // A combination: the wheels must be left in one particular arrangement.
}

/** What a puzzle says back through the dialogue box, and what the world should do about it. */
class PuzzleReply(
    val speaker: String,
    val text: String,
    val seconds: Float,
    val solvedNow: Boolean = false,
    val failed: Boolean = false,
    val shake: Float = 0f,
    val sparks: Int = 0
)

/**
 * Where a puzzle sits and how wide it is.
 *
 * [col] and [row] are tile coordinates inside [areaId], the same convention the boss arenas
 * use. The row of controls is centred on that tile, so a five-stone litany on tile 22 spans
 * tiles 20 to 24 without the author having to count pixels.
 */
class PuzzleSpec(
    val id: String,
    val name: String,
    val kind: PuzzleKind,
    val areaId: String,
    val col: Int,
    val row: Int,
    val cells: Int,
    val cellSpacing: Float,
    val accent: Int,
    /** The rule, carved in the world next to the puzzle. Never only in a menu. */
    val clue: String
) {
    /** Half the width of the row of controls, in world pixels. */
    val halfWidth: Float get() = (cells - 1) * cellSpacing * 0.5f
}

/**
 * One puzzle in the world: its state, its rules and where its controls stand.
 *
 * [x] is the centre of the first control and [floorY] the floor it stands on; both come from
 * the real tile grid at placement time.
 */
abstract class Puzzle(val spec: PuzzleSpec, val x: Float, val floorY: Float) {

    var solved = false
        protected set

    val cells: Int get() = spec.cells

    fun cellX(i: Int): Float = x + i * spec.cellSpacing

    /** True when a world position is close enough to any control to be worth a press. */
    fun inReach(px: Float, py: Float, reach: Float, heightTolerance: Float): Boolean {
        if (abs(py - floorY) > heightTolerance) return false
        for (i in 0 until cells) if (abs(px - cellX(i)) <= reach) return true
        return false
    }

    /** The control nearest to a world x, or -1 when none is within [reach]. */
    fun cellNear(px: Float, reach: Float): Int {
        var best = -1
        var bestDistance = reach
        for (i in 0 until cells) {
            val d = abs(px - cellX(i))
            if (d <= bestDistance) {
                bestDistance = d
                best = i
            }
        }
        return best
    }

    /** Presses one control. Never call with an index out of range (see [cellNear]). */
    abstract fun press(i: Int): PuzzleReply

    /** What the tracker says when this puzzle finally gives in. */
    open val wokenLine: String get() = spec.name + " wakes"

    open fun update(dt: Float) {}

    /** 0..1: how awake the puzzle looks. Drives glow, hum and the solved shimmer. */
    open fun awake(): Float = if (solved) 1f else 0f

    protected fun say(text: String, seconds: Float): PuzzleReply = PuzzleReply(spec.name, text, seconds)
}
