package com.devdgames.hollowknightbutmine.game.puzzle

import com.devdgames.hollowknightbutmine.game.world.AreaDef
import com.devdgames.hollowknightbutmine.game.world.WorldLayout

/**
 * The puzzles the kingdom keeps, and where they stand.
 *
 * Placements are authored in tile coordinates inside a real area, exactly like the boss
 * arenas in game/boss/BossRoster.kt, and resolved against the real tile grid: the row of
 * controls is centred on the anchor tile and dropped onto whatever floor lies under it.
 * StoryPlacementTest fails the build if an anchor has no floor to stand on.
 */
object PuzzleAtlas {

    /**
     * Dustmere: three stones before a door nobody built. The lintel gives the rule, so the
     * puzzle is fair to a player who reads the room instead of the menu.
     */
    val runeSeal = PuzzleSpec(
        id = "rune_seal",
        name = "THE SEALED DOOR",
        kind = PuzzleKind.RUNE_SEAL,
        areaId = "town",
        col = 32, row = 12,
        cells = 3, cellSpacing = 40f,
        accent = 0xFFFFCF73.toInt(),
        clue = "WAKE THEM FEWEST NOTCHES FIRST"
    )

    /**
     * Resting Grounds: five grave-lights on the shelf above the bench. Waking one wakes its
     * neighbours, so the row has to come up together.
     */
    val litany = PuzzleSpec(
        id = "litany",
        name = "THE LITANY OF GRAVES",
        kind = PuzzleKind.LITANY,
        areaId = "resting",
        col = 22, row = 9,
        cells = 5, cellSpacing = 34f,
        accent = 0xFFCBB6F2.toInt(),
        clue = "A LIGHT WAKES THE ONES BESIDE IT"
    )

    /**
     * Glimmer Peak: four crystal braziers down the length of the upper hall. Nothing to
     * deduce — the mountain only asks whether you can run.
     */
    val beacon = PuzzleSpec(
        id = "beacon",
        name = "THE FOUR FACETS",
        kind = PuzzleKind.BEACON,
        areaId = "crystal",
        col = 21, row = 5,
        cells = 4, cellSpacing = 120f,
        accent = 0xFFFF9AD9.toInt(),
        clue = "FOUR AT ONCE, OR NONE AT ALL"
    )

    /**
     * Old Waterways: four valve wheels on the drowned pipe above the flooded channel. A
     * combination, with a flood for anyone who simply opens everything.
     */
    val sluice = PuzzleSpec(
        id = "sluice",
        name = "THE SLUICE GATE",
        kind = PuzzleKind.SLUICE,
        areaId = "waterways",
        col = 39, row = 4,
        cells = 4, cellSpacing = 40f,
        accent = 0xFF9BE3C6.toInt(),
        clue = "THREE RUN. ONE KEEPS ITS COUNSEL"
    )

    val specs: List<PuzzleSpec> = listOf(runeSeal, litany, beacon, sluice)

    // ---- the answers, kept beside the specs so the tests can hold the atlas to them ----

    /** Notches carved on each stone of the seal; the door wants them fewest first. */
    val runeNotches = intArrayOf(2, 0, 1)

    /** Presses taken from an all-lit row, which is why the litany is always solvable. */
    val litanyScramble = intArrayOf(0, 2, 4)

    /** Seconds a facet holds its fire. Long enough to be fair, short enough to matter. */
    const val BEACON_BURN = 3.6f

    /** The arrangement the sluice wants: three running, the second keeping its counsel. */
    val sluiceTarget = booleanArrayOf(true, false, true, true)

    /** Builds every puzzle, standing on the real floor of the real area it was authored into. */
    fun build(layout: WorldLayout): List<Puzzle> = specs.map { create(it, layout) }

    fun create(spec: PuzzleSpec, layout: WorldLayout): Puzzle {
        val g = layout.grid(spec.areaId)
        val worldCol = g.col0 + spec.col
        val worldRow = g.row0 + spec.row
        val floorRow = layout.floorRowBelow(worldCol, worldRow)
            ?: throw IllegalStateException(
                "Puzzle '${spec.id}' at (${spec.col},${spec.row}) in ${spec.areaId} has no floor under it"
            )
        val anchorX = worldCol * AreaDef.TILE + AreaDef.TILE * 0.5f
        val x = anchorX - spec.halfWidth
        val floorY = floorRow * AreaDef.TILE
        return when (spec.kind) {
            PuzzleKind.RUNE_SEAL -> RuneSealPuzzle(spec, x, floorY, runeNotches)
            PuzzleKind.LITANY -> LitanyPuzzle(spec, x, floorY, litanyScramble)
            PuzzleKind.BEACON -> BeaconPuzzle(spec, x, floorY, BEACON_BURN)
            PuzzleKind.SLUICE -> SluicePuzzle(spec, x, floorY, sluiceTarget)
        }
    }
}
