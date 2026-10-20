# Tasks, characters and puzzles

`master` had one slice of story: two spirits in Dustmere and a three-rune sequence beside them
(`game/StoryArea.kt`, deleted here). This replaces it with a small story *system* — a cast that
stands in the kingdom, puzzles they keep, relics lying about the map, and a task book that ties
all three together and pays in masks and soul.

Eight characters, seven tasks, four puzzles, three relics, across six regions.

## Controls

| Input | Does |
|---|---|
| **F** / **Interact** (and the heal button, when something is in reach) | talk to whoever is nearest, press the nearest stone or valve, skip the line being spoken |
| **T** / the **📜 Tasks** button | the journal: every task offered so far, and what it still wants |

Nothing is menu-driven. Puzzles are solved by standing in front of their controls and pressing
Interact, the same button that talks to people — which is why `startHealOrInteract()` prefers
Interact whenever a character, a control or a line of dialogue is in reach, and why no placement
in this branch sits within healing distance of a bench.

## The cast

| id | name | kind | stands in | what they are for |
|---|---|---|---|---|
| `keeper` | THE LANTERN KEEPER | spirit | Dustmere | gives *The Bell That Rang* |
| `witness` | THE HOLLOW WITNESS | shade | Dustmere | the bell's first objective: hear what it remembers |
| `sable` | SABLE | lamplighter | Dustmere | gives *Light for the Road* |
| `quill` | QUILL | cartographer | The Forgotten Crossroads | gives *Roads Unwalked* |
| `vell` | SISTER VELL | sister | The Resting Grounds | gives *Wake the Sleepers* |
| `warden` | THE FACET WARDEN | warden | Glimmer Peak | gives *Light Caught in Stone* |
| `gristle` | GRISTLE | sluice-keeper | The Old Waterways | gives *The Drowned Gate* |
| `scribe` | THE PALE SCRIBE | pale scribe | The Abyss | gives *What Waits Below* |

Each has its own silhouette, colour and idle animation (`NpcArt`), a prompt mark when in reach,
and a dialogue pool per situation: first meeting, offering a task, task under way, task ready to
hand in, after it is done, and idle chatter for when nothing is pending. `firstMeet` and `idle`
never overlap, and `linesFor` walks a pool rather than repeating a line, so a character met three
times says three different things before it starts to cycle.

## The tasks

A task moves `LOCKED → OFFERED → ACTIVE → READY → DONE`. Talking to a giver while it is
`OFFERED` accepts it; talking to them while it is `READY` hands it in and pays.

| id | name | given by | objectives | reward | needs |
|---|---|---|---|---|---|
| `bell` | The Bell That Rang | keeper | talk to the Witness, wake the sealed door | 60 soul | — |
| `lamplight` | Light for the Road | sable | gather the three lantern shards | 50 soul | `bell` |
| `roads` | Roads Unwalked | quill | stand in eight regions | 40 soul | `bell` |
| `litany` | Wake the Sleepers | vell | solve the litany | 1 mask, 33 soul | — |
| `facets` | Light Caught in Stone | warden | hold four facets alight at once | 1 mask | — |
| `sluice` | The Drowned Gate | gristle | open the drowned gate | 66 soul | — |
| `vigil` | What Waits Below | scribe | defeat the Keener and Shardback | 1 mask | — |

Objective kinds are `TALK`, `SOLVE`, `COLLECT`, `DISCOVER`, `DEFEAT` (`QuestModels.kt`). Progress
is sampled from the world each frame — solved puzzles, shard counts, regions entered, bosses down
— rather than pushed by event callbacks, so a puzzle solved *before* its task was accepted still
counts the moment the task is taken, and a shard picked up early is not lost.

Three masks come out of the task book, on top of the five the run starts with; the HUD mask panel
sizes itself from `maxMasks` and still fits.

## The puzzles

| id | where | controls | the rule | clue |
|---|---|---|---|---|
| `rune_seal` | Dustmere | 3 stones | wake them in order of their notches; a wrong stone resets all three | *wake them fewest notches first* |
| `litany` | The Resting Grounds | 5 sleepers | each touch wakes the one before and after it; all five awake | *a light wakes the ones beside it* |
| `beacon` | Glimmer Peak | 4 braziers | each kindling burns 3.6 s; all four must be alight together | *four at once, or none at all* |
| `sluice` | The Old Waterways | 4 valves | three run, one keeps its counsel; opening all four floods the works and shuts them again | — |

Four different kinds of thinking: reading an ordering out of the art, a parity puzzle, a timing
run, and choosing the right subset under a failure state. The litany is a five-cell lights-out,
which is deliberately *not* uniquely solvable — two different sets of three presses solve it
(`[0,2,4]` and `[1,2,3]`), and `PuzzleTest` enumerates all 32 press-sets and asserts those counts
rather than a single answer. A third set, `[1,2,3,4]`, also ends woken: once the row is whole it
stops listening, so the extra press is refused instead of undoing the work. The
beacon's burn-down makes it the one puzzle that punishes dawdling, which is also why an ordinary
puzzle press no longer takes the dialogue box (see below).

Each puzzle also has a `wokenLine`, spoken the moment it gives in: *the sealed door opens*, *the
litany is whole*, *the four facets hold*, *the sluice gate opens*.

## Relics

Three lantern shards — one on The Wailing Cliffs, one on The Verdant Path, one in The City of
Rain. Walking into one takes it: sparks, a line naming it, and a `(2 / 3)` count in the toast.
They are counted by group, not by quest, so collecting all three before ever meeting Sable
finishes her errand on the first conversation.

## Speaking versus nagging

Interact does one thing at a time, and while a line is being spoken it *skips* rather than talks
or presses. That made the timed beacon unrunnable — a paragraph after every kindling — so the
director now takes the dialogue box only for the moments that matter: a puzzle giving in, or a
mistake that costs something. Everything else (a stone awake, a valve turned, a flood) is a toast
across the middle of the screen, clipped to 78 % of the width.

## How it fits the codebase

```
game/story/
  Dialogue.kt      DialogueLine, NpcCue, DialogueQueue (the skip semantics live here)
  QuestModels.kt   ObjectiveKind/QuestStage/Objective/QuestDef/WorldObservation/QuestLog
  QuestBook.kt     the seven tasks above
  NpcModels.kt     the eight characters, their lines, and NpcRoster.place(layout)
  RelicModels.kt   the shards and RelicRoster.place(layout)
  StoryDirector.kt StoryStage / SilentStage / the director: reach, press, talk, reward, tracker
  NpcArt.kt        eight Canvas silhouettes, prompt mark, idle motion
  RelicArt.kt      shard, pickup flash
  StoryHud.kt      dialogue panel, tracker, toast, two-column journal
game/puzzle/
  PuzzleModels.kt  Puzzle, PuzzleSpec, PuzzleReply, PlacedPuzzle
  PuzzleBrains.kt  RuneSealPuzzle, LitanyPuzzle, BeaconPuzzle, SluicePuzzle
  PuzzleAtlas.kt   specs plus where each stands
  PuzzleArt.kt     stones, sleepers, braziers, valves, clue plaques
```

Everything except the three `*Art.kt` files and `StoryHud.kt` is plain Kotlin with no `android.*`
imports, which is what lets the tests simulate a whole playthrough on the JVM.

Placement is authored as a tile relative to its region and resolved through
`WorldLayout.floorRowBelow`, so nothing floats in the air, nothing is buried in a wall, and
nothing lands within Interact range of a bench (a marker that did would silently steal healing).
The art follows the house rules: no bitmaps, no allocation inside a draw, `Paint` left the way it
was found, scratch `Path`/`RectF` at object level.

`GameWorld` owns one `StoryDirector` and implements `StoryStage` — the director asks for shake,
sparks and title cards instead of reaching into the player or the particle list. Rewards are
drained once per frame and applied there: masks raise `maxMasks` and refill, soul is capped at
`maxSoul`.

## Tests

| file | covers |
|---|---|
| `puzzle/PuzzleTest.kt` | each puzzle's rules from a cold start, the litany's exhaustive ≤ 3-press enumeration, the beacon burning down mid-run, the sluice flood and recovery, and every placement (floor, ordering, spacing, bench clearance) |
| `story/QuestLogTest.kt` | stages at start, accept-once, `TALK` counting only after accept, shards picked up early, capped progress, eight regions, both bosses, several tasks at once, quest-data well-formedness |
| `story/StoryCastTest.kt` | the cast standing on real ground with no hazard under them, nobody within 100 px of anybody else on the same floor, six or more regions used, every giver and objective target resolving to something that exists |
| `story/StoryDirectorTest.kt` | a recording `StoryStage` and a full playthrough of all seven tasks: greet, accept, skip, solve, hand in, get paid exactly once — plus refusals from the wrong height and from empty air |

## What has not been verified

There is no JDK, no Android SDK and no Gradle in the workspace this was written in, so **none of
this has been compiled or run locally**. `.github/workflows/android-compile.yml`
(`assembleDebug` + `testDebugUnitTest`) is the first real compile. The coordinates, floors and
clearances quoted above were computed by re-implementing `WorldAtlas`/`WorldLayout`'s parsing and
`floorRowBelow` in a throwaway Python script and checking every anchor tile, not by looking at the
game. The new art has not been rendered either — there are no previews for these characters yet —
so proportion, legibility and colour against each region's palette are unverified on a real
screen.
