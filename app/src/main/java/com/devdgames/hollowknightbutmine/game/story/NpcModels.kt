package com.devdgames.hollowknightbutmine.game.story

import com.devdgames.hollowknightbutmine.game.world.AreaDef
import com.devdgames.hollowknightbutmine.game.world.WorldLayout

/**
 * The people of the kingdom: who they are, where they stand, and what they say.
 *
 * Pure Kotlin, like everything else in game/story — the cast is data plus lines, and the
 * drawing is in [NpcArt]. Positions are authored as tile coordinates inside a real area and
 * resolved onto the real floor at build time, the same way [com.devdgames.hollowknightbutmine.game.puzzle.PuzzleAtlas]
 * places its stones.
 */

/** How an NPC is drawn. One kind, one silhouette, so nobody in this kingdom looks alike. */
enum class NpcKind {
    SPIRIT,          // A pale witness in a torn shroud, bobbing gently.
    SHADE,           // The same idea, hungrier: dimmer, jittering, wrong-coloured.
    LAMPLIGHTER,     // Living, hooded, oil-stained, carrying a lit lamp on a hook.
    CARTOGRAPHER,    // Small, inked, buried in a map case taller than they are.
    SISTER,          // Hooded and veiled, holding a hand bell against the chest.
    WARDEN,          // Faceted and mineral, glowing at the seams, slowly turning.
    SLUICE_KEEPER,   // Broad, wet, in a slicker, with a wheel-key over the shoulder.
    PALE_SCRIBE      // Tall, thin, masked, holding a ledger and a light that is not a lamp.
}

/**
 * One character.
 *
 * The lines are grouped by the moment they answer for (see [NpcCue]); [StoryDirector] picks
 * the group and plays it. Givers use every group. Witnesses and by-standers use [firstMeet]
 * and [idle], which the director cycles through so a second conversation is never a repeat.
 */
class NpcDef(
    val id: String,
    /** Spoken and labelled in capitals, like everything else written on stone in this game. */
    val name: String,
    val title: String,
    val kind: NpcKind,
    val accent: Int,
    val areaId: String,
    val col: Int,
    val row: Int,
    /** The task this character hands out and takes back in, if they hand one out at all. */
    val questId: String?,
    val firstMeet: List<DialogueLine>,
    val offer: List<DialogueLine> = emptyList(),
    val busy: List<DialogueLine> = emptyList(),
    val ready: List<DialogueLine> = emptyList(),
    val after: List<DialogueLine> = emptyList(),
    val idle: List<DialogueLine> = emptyList()
) {
    /** The lines for a cue, falling back so that nobody ever has nothing to say. */
    fun linesFor(cue: NpcCue, talks: Int): List<DialogueLine> {
        val group = when (cue) {
            NpcCue.FIRST_MEET -> firstMeet
            NpcCue.OFFER -> offer
            NpcCue.BUSY -> busy
            NpcCue.READY -> ready
            NpcCue.AFTER -> after
            NpcCue.IDLE -> idle
        }
        val pool = when {
            group.isNotEmpty() -> group
            cue == NpcCue.FIRST_MEET && idle.isNotEmpty() -> idle
            idle.isNotEmpty() -> idle
            else -> firstMeet
        }
        if (pool.isEmpty()) return pool
        if (pool.size == 1) return pool
        // A long conversation cycles; a single scripted beat (an offer, a turn-in) plays whole.
        if (cue == NpcCue.OFFER || cue == NpcCue.READY || cue == NpcCue.FIRST_MEET) return pool
        val start = talks % pool.size
        return listOf(pool[start])
    }
}

/** An NPC standing somewhere real, with the memory of the conversations it has had. */
class PlacedNpc(val def: NpcDef, val x: Float, val floorY: Float) {
    var met = false
    var talks = 0
    /** Set while the player is close enough to speak, so the art can offer the prompt. */
    var inReach = false
}

/** The cast, and where in the kingdom each of them can be found. */
object NpcRoster {

    /**
     * Dustmere. The two who watched the town forget, standing either side of the spawn: one
     * afraid of the bell, one afraid of being remembered.
     */
    val keeper = NpcDef(
        id = "keeper",
        name = "THE LANTERN KEEPER",
        title = "Who keeps a bell for nobody",
        kind = NpcKind.SPIRIT,
        accent = 0xFF6AC9D5.toInt(),
        areaId = "town", col = 12, row = 12,
        questId = "bell",
        firstMeet = listOf(
            DialogueLine("THE LANTERN KEEPER", "You are new. Or you are old, and I have forgotten which. The bell rang three nights past and no hand was near the rope.", 7f),
            DialogueLine("THE LANTERN KEEPER", "If you heard it too, then say nothing of it. Least of all to the Witness.", 5.5f)
        ),
        offer = listOf(
            DialogueLine("THE LANTERN KEEPER", "There is a door in the wall of this town that nobody built and nobody opens. Three stones sleep in front of it.", 7f),
            DialogueLine("THE LANTERN KEEPER", "Wake the Witness. Wake the stones. Then come and tell me what you found — I have not the courage left to look myself.", 7.5f)
        ),
        busy = listOf(
            DialogueLine("THE LANTERN KEEPER", "The Witness speaks in riddles because the truth would not sit still for him.", 5f),
            DialogueLine("THE LANTERN KEEPER", "Read the lintel over the stones. It is plainer than he is, and it was carved by someone who wanted to be obeyed.", 7f)
        ),
        ready = listOf(
            DialogueLine("THE LANTERN KEEPER", "You opened it. Say it plainly, so that I can be frightened properly.", 5.5f),
            DialogueLine("THE LANTERN KEEPER", "Then it is as I feared. Take this light with you — I have no further use for lamps where you are going.", 7f)
        ),
        after = listOf(
            DialogueLine("THE LANTERN KEEPER", "I keep the bell. Somebody ought to, even if it only ever rings for itself.", 6f),
            DialogueLine("THE LANTERN KEEPER", "The town sleeps better since you walked out of it. That is not a compliment. Sleep is not the same as peace.", 7.5f)
        ),
        idle = listOf(
            DialogueLine("THE LANTERN KEEPER", "The lamps are lit because I light them. That is the whole of the story, and it is not a happy one.", 6.5f)
        )
    )

    val witness = NpcDef(
        id = "witness",
        name = "THE HOLLOW WITNESS",
        title = "Who was asked to forget, and did not",
        kind = NpcKind.SHADE,
        accent = 0xFF9C6FB8.toInt(),
        areaId = "town", col = 18, row = 12,
        questId = null,
        firstMeet = listOf(
            DialogueLine("THE HOLLOW WITNESS", "You came here before, little knight. You stood where you are standing, and you asked us to forget.", 7f),
            DialogueLine("THE HOLLOW WITNESS", "We did not. That was the unkind part of it.", 4.5f)
        ),
        idle = listOf(
            DialogueLine("THE HOLLOW WITNESS", "Read the stones, if you still dare. They were cut by hands that wanted to be obeyed.", 6f),
            DialogueLine("THE HOLLOW WITNESS", "The Keeper rings a bell for a town with no living in it. I have stopped telling him so.", 6.5f),
            DialogueLine("THE HOLLOW WITNESS", "Forget me. It is the only courtesy anybody down here still offers.", 5.5f)
        )
    )

    /** Dustmere's last living trade: somebody has to climb the lamps. */
    val sable = NpcDef(
        id = "sable",
        name = "SABLE",
        title = "The last lamplighter of Dustmere",
        kind = NpcKind.LAMPLIGHTER,
        accent = 0xFFFFC46B.toInt(),
        areaId = "town", col = 40, row = 12,
        questId = "lamplight",
        firstMeet = listOf(
            DialogueLine("SABLE", "Mind the oil. Half the lamps in Dustmere are dark and I am the only fool left who climbs them.", 6.5f),
            DialogueLine("SABLE", "You have the look of someone going down. Good. I have an errand that needs legs and no sense.", 6.5f)
        ),
        offer = listOf(
            DialogueLine("SABLE", "Three lantern shards went out of this town when the well opened: one to the cliffs, one into the green, one into the rain.", 8f),
            DialogueLine("SABLE", "Bring them back and I will light the road as far as the crossroads. The dark is not the danger. Not knowing where you are — that is the danger.", 9f)
        ),
        busy = listOf(
            DialogueLine("SABLE", "Shards glimmer. That is the whole craft of finding them: walk slowly and let your eyes go first.", 6.5f),
            DialogueLine("SABLE", "Cliffs, green, rain. In that order, if you like a story with a shape to it.", 5.5f)
        ),
        ready = listOf(
            DialogueLine("SABLE", "You have got them. All three. Hold still a moment and let me look at you in the light.", 6.5f),
            DialogueLine("SABLE", "There. Dustmere will see you out and see you home. That is all a town can do for anybody now.", 7f)
        ),
        after = listOf(
            DialogueLine("SABLE", "The cliff lamp burns again. It costs me a climb a week and it is worth every rung of it.", 6.5f),
            DialogueLine("SABLE", "If you come back through, mind the oil.", 3.5f)
        ),
        idle = listOf(
            DialogueLine("SABLE", "Lamplighting is grief with a ladder.", 3.5f)
        )
    )

    /** The Forgotten Crossroads: an apprentice with a map that is suddenly his. */
    val quill = NpcDef(
        id = "quill",
        name = "QUILL",
        title = "The cartographer's apprentice",
        kind = NpcKind.CARTOGRAPHER,
        accent = 0xFFA8D8B0.toInt(),
        areaId = "crossroads", col = 29, row = 19,
        questId = "roads",
        firstMeet = listOf(
            DialogueLine("QUILL", "Don't — don't step on that. That is the western gallery, and it cost me four days and one very bad fall.", 7f),
            DialogueLine("QUILL", "My master went down and did not come back up. So the map is mine, which is a terrible thing to be handed.", 7f)
        ),
        offer = listOf(
            DialogueLine("QUILL", "Walk it. Anywhere, everywhere. Stand in eight of these regions and come back, and I will ink you into them.", 7.5f),
            DialogueLine("QUILL", "A map with a person on it is a different document. It stops being a drawing and starts being a warning.", 7f)
        ),
        busy = listOf(
            DialogueLine("QUILL", "Eight regions. I count them on my fingers twice a day, in case the kingdom has quietly grown.", 6.5f),
            DialogueLine("QUILL", "You do not have to go anywhere sensible. Sensible places are already drawn.", 5.5f)
        ),
        ready = listOf(
            DialogueLine("QUILL", "You have been further than my boots ever went. Stand still — this is the part where you become real.", 7f),
            DialogueLine("QUILL", "There. Look at that: somebody is down there on purpose. Take the spare ink, you have earned the mess of it.", 7.5f)
        ),
        after = listOf(
            DialogueLine("QUILL", "The blank places are not empty. They are only unvisited. That distinction was my master's and it was the good one.", 8f),
            DialogueLine("QUILL", "If you fall off the edge of anything, I shall draw it in. It is the only revenge available to me.", 6.5f)
        ),
        idle = listOf(
            DialogueLine("QUILL", "I have drawn this chamber nine times. It keeps being slightly different.", 5f)
        )
    )

    /** The Resting Grounds: the ground is full, and it is light-sleeping. */
    val vell = NpcDef(
        id = "vell",
        name = "SISTER VELL",
        title = "Who rings at dusk for nobody",
        kind = NpcKind.SISTER,
        accent = 0xFFC9B6F0.toInt(),
        areaId = "resting", col = 15, row = 9,
        questId = "litany",
        firstMeet = listOf(
            DialogueLine("SISTER VELL", "Hush. Not for my sake — for theirs. The ground here is full, and it sleeps lightly.", 6.5f),
            DialogueLine("SISTER VELL", "You are not the first to come down with a sword. You may be the first to come down quietly.", 6.5f)
        ),
        offer = listOf(
            DialogueLine("SISTER VELL", "The litany along this shelf has gone out. Five grave-lights, and not one of them burning with the others.", 7.5f),
            DialogueLine("SISTER VELL", "Wake all five together and the ground will settle. Leave them ragged and the sleepers keep turning over.", 7.5f)
        ),
        busy = listOf(
            DialogueLine("SISTER VELL", "Each light wakes the ones beside it. That is the whole of the rite, and unfortunately the whole of the difficulty.", 7.5f),
            DialogueLine("SISTER VELL", "Two of the five still burn. I have stopped counting them aloud; it upsets the row.", 6f)
        ),
        ready = listOf(
            DialogueLine("SISTER VELL", "I felt it from down here: the row is even. Say it, so that I may write the date against it.", 6.5f),
            DialogueLine("SISTER VELL", "Waked, settled and accounted for. You have done more for this ground in an hour than I have in a decade.", 7.5f)
        ),
        after = listOf(
            DialogueLine("SISTER VELL", "I ring for them at dusk. Nobody comes. That has never once been the point of it.", 6.5f),
            DialogueLine("SISTER VELL", "Walk softly. It costs nothing and it is remembered.", 4.5f)
        ),
        idle = listOf(
            DialogueLine("SISTER VELL", "Every grave here has a name on it that nobody left.", 5f)
        )
    )

    /** Glimmer Peak: mineral, patient, and not quick enough to run the hall itself. */
    val warden = NpcDef(
        id = "warden",
        name = "THE FACET WARDEN",
        title = "Structural, not quick",
        kind = NpcKind.WARDEN,
        accent = 0xFFFF9AD9.toInt(),
        areaId = "crystal", col = 29, row = 5,
        questId = "facets",
        firstMeet = listOf(
            DialogueLine("THE FACET WARDEN", "Halt. No — do not halt. Halting is what the mountain does. Stand, instead, and be refracted.", 7f),
            DialogueLine("THE FACET WARDEN", "I keep the facets. They keep the light. Neither of us has ever kept a schedule.", 6.5f)
        ),
        offer = listOf(
            DialogueLine("THE FACET WARDEN", "Four braziers along this hall. Each holds fire for a few heartbeats and not one moment longer.", 7f),
            DialogueLine("THE FACET WARDEN", "Hold all four alight in the same moment and the peak will show you what it has been hiding. I cannot do it. I am structural.", 8.5f)
        ),
        busy = listOf(
            DialogueLine("THE FACET WARDEN", "Run. Do not think — run. Thinking is what the dark is for.", 5f),
            DialogueLine("THE FACET WARDEN", "Kindle the far one first if you are slow, and the near one first if you are honest.", 6.5f)
        ),
        ready = listOf(
            DialogueLine("THE FACET WARDEN", "The hum came through the wall and into my teeth. Say that it was you, so I may be proud of something other than geometry.", 8f),
            DialogueLine("THE FACET WARDEN", "Then the peak is awake and owes you a favour, and the peak pays its debts in armour.", 6.5f)
        ),
        after = listOf(
            DialogueLine("THE FACET WARDEN", "Light caught in stone. That is the whole of us, once you take the poetry out.", 6f),
            DialogueLine("THE FACET WARDEN", "Do not touch the pink ones. They are not pink. They are a colour you are seeing as pink.", 6.5f)
        ),
        idle = listOf(
            DialogueLine("THE FACET WARDEN", "I have stood in this hall for a very long time and it has not once been boring.", 6f)
        )
    )

    /** The Old Waterways: wet, blunt, and the only witness to his own mistakes. */
    val gristle = NpcDef(
        id = "gristle",
        name = "GRISTLE",
        title = "Sluice-keeper, Old Waterways",
        kind = NpcKind.SLUICE_KEEPER,
        accent = 0xFF9BE3C6.toInt(),
        areaId = "waterways", col = 44, row = 4,
        questId = "sluice",
        firstMeet = listOf(
            DialogueLine("GRISTLE", "Mind your step, and then mind your nose, in that order.", 5f),
            DialogueLine("GRISTLE", "Gristle. Sluice-keeper. I turn wheels so the city above never has to think about what it swallows.", 7.5f)
        ),
        offer = listOf(
            DialogueLine("GRISTLE", "Four wheels on this pipe. Three of them want to run and one wants to keep its counsel, and I have forgotten which.", 8f),
            DialogueLine("GRISTLE", "The tally is scratched into the wall because I do not trust paper down here. Set them right and the gate opens.", 7.5f)
        ),
        busy = listOf(
            DialogueLine("GRISTLE", "Open all four and the chamber floods. I know because I have done it. Twice. That is not a threat, it is a warning with a witness.", 8.5f),
            DialogueLine("GRISTLE", "Three running, one shut. Read the tally before you read the water; the water lies.", 6f)
        ),
        ready = listOf(
            DialogueLine("GRISTLE", "Water went down the right way. I heard it with my whole body. Tell me that was you.", 6f),
            DialogueLine("GRISTLE", "Right then. The waterways are yours to walk dry, which is the finest gift anybody down here can give.", 7.5f)
        ),
        after = listOf(
            DialogueLine("GRISTLE", "The city cries and I catch it. Somebody has to stand at the bottom of everybody else's sadness.", 7f),
            DialogueLine("GRISTLE", "Do not drink anything. Do not even look at it thirstily.", 5f)
        ),
        idle = listOf(
            DialogueLine("GRISTLE", "Wheels, water, waiting. It is a living, if you do not mind that it isn't.", 5.5f)
        )
    )

    /** The Abyss: the last office in the kingdom, and the last person keeping it. */
    val scribe = NpcDef(
        id = "scribe",
        name = "THE PALE SCRIBE",
        title = "Who writes down what was let go",
        kind = NpcKind.PALE_SCRIBE,
        accent = 0xFFEDE3C8.toInt(),
        areaId = "abyss", col = 17, row = 22,
        questId = "vigil",
        firstMeet = listOf(
            DialogueLine("THE PALE SCRIBE", "You are the one they left the door open for.", 5f),
            DialogueLine("THE PALE SCRIBE", "I write it down. That is the office. Somebody must keep the record of what the kingdom kept, and what it let go of.", 8f)
        ),
        offer = listOf(
            DialogueLine("THE PALE SCRIBE", "Two things still walk the east wing that were asked to stop: the mourner in the Vigil Hall, and the beast in the Geode Court.", 8.5f),
            DialogueLine("THE PALE SCRIBE", "Bring me the news of both. I will not ask for anything else — you will not have anything else left.", 7f)
        ),
        busy = listOf(
            DialogueLine("THE PALE SCRIBE", "The mourner keeps a bell it cannot ring. The beast keeps a heart it cannot stop. Both are, in their way, employees.", 8f),
            DialogueLine("THE PALE SCRIBE", "East, past the graves, and up through the pink stone. I have never walked it. I only write it.", 7f)
        ),
        ready = listOf(
            DialogueLine("THE PALE SCRIBE", "You quieted them. Both of them. Say it, and I will write it, and it will be true forever — which is more than either managed.", 8.5f),
            DialogueLine("THE PALE SCRIBE", "Recorded. The kingdom's last two debts are settled by a creature with no name in the book. I have left the line blank on purpose.", 8.5f)
        ),
        after = listOf(
            DialogueLine("THE PALE SCRIBE", "There is nothing below here. That is not a threat; it is the reason the place is so quiet.", 7f),
            DialogueLine("THE PALE SCRIBE", "Go up, and take the light with you. I have the dark, and the dark is sufficient.", 6.5f)
        ),
        idle = listOf(
            DialogueLine("THE PALE SCRIBE", "I have filled four ledgers. Only the last one is about anybody still here.", 6.5f)
        )
    )

    val all: List<NpcDef> = listOf(keeper, witness, sable, quill, vell, warden, gristle, scribe)

    /** Builds the cast, standing on the real floor of the real area each was authored into. */
    fun place(layout: WorldLayout): List<PlacedNpc> = all.map { place(it, layout) }

    fun place(def: NpcDef, layout: WorldLayout): PlacedNpc {
        val g = layout.grid(def.areaId)
        val worldCol = g.col0 + def.col
        val worldRow = g.row0 + def.row
        val floorRow = layout.floorRowBelow(worldCol, worldRow)
            ?: throw IllegalStateException(
                "NPC '${def.id}' at (${def.col},${def.row}) in ${def.areaId} has no floor under it"
            )
        return PlacedNpc(def, worldCol * AreaDef.TILE + AreaDef.TILE * 0.5f, floorRow * AreaDef.TILE)
    }
}
