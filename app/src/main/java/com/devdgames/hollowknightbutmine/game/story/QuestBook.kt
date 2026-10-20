package com.devdgames.hollowknightbutmine.game.story

/**
 * The tasks of the kingdom, as authored.
 *
 * Seven of them, in three shapes: the story that starts in Dustmere and unlocks the two that
 * follow it, four regional tasks handed out by whoever is standing nearest to the trouble, and
 * one long task at the bottom of the world that asks for the two bosses of the east wing.
 * Nothing here blocks the game — every task can be refused, taken late, or finished out of
 * order, and the kingdom carries on either way.
 *
 * Targets are the real ids of the things they refer to: an NPC id from [NpcRoster], a puzzle
 * id from [com.devdgames.hollowknightbutmine.game.puzzle.PuzzleAtlas], a relic group from
 * [RelicRoster], or a boss id from [com.devdgames.hollowknightbutmine.game.boss.BossRoster].
 * StoryCastTest checks every one of them against the thing it names.
 */
object QuestBook {

    /** Dustmere's story: the bell, the witness, and the door nobody built. */
    val bell = QuestDef(
        id = "bell",
        name = "The Bell That Rang",
        giver = "keeper",
        brief = "The Lantern Keeper heard a bell that nobody rang. Find out what else heard it, and open the door the town keeps pretending is a wall.",
        objectives = listOf(
            Objective(ObjectiveKind.TALK, "witness", "Hear what the Hollow Witness remembers"),
            Objective(ObjectiveKind.SOLVE, "rune_seal", "Wake the three stones at the sealed door")
        ),
        soulReward = 60,
        requires = null,
        doneLine = "the door is open"
    )

    /** Sable's errand: three shards, three regions, one lit road. */
    val lamplight = QuestDef(
        id = "lamplight",
        name = "Light for the Road",
        giver = "sable",
        brief = "Three lantern shards went out of Dustmere when the well opened. Bring them home and Sable will light the road as far as the crossroads.",
        objectives = listOf(
            Objective(ObjectiveKind.COLLECT, "shard", "Gather the lantern shards", needed = 3)
        ),
        soulReward = 50,
        requires = "bell",
        doneLine = "the road has light again"
    )

    /** Quill's request: be somewhere, so that the map can stop being a drawing. */
    val roads = QuestDef(
        id = "roads",
        name = "Roads Unwalked",
        giver = "quill",
        brief = "The apprentice has inherited a map with no one on it. Stand in eight regions of the kingdom and he will ink you into them.",
        objectives = listOf(
            Objective(ObjectiveKind.DISCOVER, "areas", "Stand in eight regions of the kingdom", needed = 8)
        ),
        soulReward = 40,
        requires = "bell",
        doneLine = "a name on the map"
    )

    /** Sister Vell's rite: five grave-lights, woken together. */
    val litany = QuestDef(
        id = "litany",
        name = "Wake the Sleepers",
        giver = "vell",
        brief = "The litany above the Resting Grounds bench has gone ragged. Wake all five grave-lights in the same moment and the ground will settle.",
        objectives = listOf(
            Objective(ObjectiveKind.SOLVE, "litany", "Wake all five grave-lights together")
        ),
        soulReward = 33,
        maskReward = 1,
        requires = null,
        doneLine = "the ground settles"
    )

    /** The Facet Warden's hall: four braziers, one moment. */
    val facets = QuestDef(
        id = "facets",
        name = "Light Caught in Stone",
        giver = "warden",
        brief = "Glimmer Peak will show what it is hiding to anyone who can hold all four facets alight at once. The Warden is structural, and cannot run.",
        objectives = listOf(
            Objective(ObjectiveKind.SOLVE, "beacon", "Hold the four facets alight at once")
        ),
        maskReward = 1,
        requires = null,
        doneLine = "the peak hums"
    )

    /** Gristle's wheels: three running, one keeping its counsel. */
    val sluice = QuestDef(
        id = "sluice",
        name = "The Drowned Gate",
        giver = "gristle",
        brief = "Four valve wheels on the pipe above the flooded channel, and the keeper has forgotten which one stays shut. The tally is scratched into the wall.",
        objectives = listOf(
            Objective(ObjectiveKind.SOLVE, "sluice", "Set the four wheels and open the gate")
        ),
        soulReward = 66,
        requires = null,
        doneLine = "the water goes down the right way"
    )

    /** The Pale Scribe's record: the east wing, quieted. */
    val vigil = QuestDef(
        id = "vigil",
        name = "What Waits Below",
        giver = "scribe",
        brief = "Two things in the east wing were asked to stop and did not. The Scribe wants the news of both, and has left the line blank on purpose.",
        objectives = listOf(
            Objective(ObjectiveKind.DEFEAT, "keener", "Quiet the mourner in the Vigil Hall"),
            Objective(ObjectiveKind.DEFEAT, "shardback", "Break the beast in the Geode Court")
        ),
        maskReward = 1,
        requires = null,
        doneLine = "the last two debts are paid"
    )

    val all: List<QuestDef> = listOf(bell, lamplight, roads, litany, facets, sluice, vigil)
}
