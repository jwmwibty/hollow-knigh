package com.devdgames.hollowknightbutmine.game.boss

import com.devdgames.hollowknightbutmine.game.world.AreaDef
import com.devdgames.hollowknightbutmine.game.world.WorldLayout

/**
 * The bosses added to the east wing of the kingdom, each in a dead-end hall of its own:
 * an antechamber with a bench (so a defeat sends the player back to the door, not across
 * the world), a doorway that shuts during the fight, and a flat arena.
 *
 * Arena tile coordinates are relative to the origin of the owning area in WorldAtlas.
 */
object BossRoster {

    /**
     * The Keener haunts the Vigil Hall off the Resting Grounds. A caster: it rewards patience
     * and parrying, and is the gentler of the two.
     */
    val keener = BossSpec(
        id = "keener",
        name = "The Keener",
        title = "Mourner of the Vigil Hall",
        defeatLine = "falls silent",
        areaId = "vigil",
        maxHp = 420,
        maskReward = 1,
        guard = 0.35f,
        phase2Hp = 210,
        introTime = 2.6f,
        transitionTime = 1.6f,
        deathTime = 2.6f,
        phase2Pace = 0.85f,
        parryStagger = 1.4f,
        grabStagger = 0.5f,
        poiseTime = 3.0f,
        reflectDamage = 60,
        accent = 0xFFB8A8F0.toInt(),
        arena = ArenaTiles(
            firstCol = 19, lastCol = 52, floorRow = 12, topRow = 2,
            gateCol = 17, gateTopRow = 9, gateBottomRow = 11,
            triggerCol = 23, spawnCol = 46
        )
    )

    /**
     * Shardback holds the Geode Court beyond Glimmer Peak. A brawler: dodge the charge, strike
     * the crash. It is the harder fight and sits deeper in the east wing.
     */
    val shardback = BossSpec(
        id = "shardback",
        name = "Shardback",
        title = "Geode Beast of Glimmer Peak",
        defeatLine = "shatters",
        areaId = "geode",
        maxHp = 520,
        maskReward = 1,
        guard = 0.3f,
        phase2Hp = 260,
        introTime = 2.4f,
        transitionTime = 1.7f,
        deathTime = 2.4f,
        phase2Pace = 0.85f,
        parryStagger = 1.3f,
        grabStagger = 0.5f,
        poiseTime = 3.0f,
        reflectDamage = 60,
        accent = 0xFFEE88EE.toInt(),
        arena = ArenaTiles(
            firstCol = 19, lastCol = 56, floorRow = 12, topRow = 2,
            gateCol = 17, gateTopRow = 9, gateBottomRow = 11,
            triggerCol = 23, spawnCol = 50
        )
    )

    val all: List<BossSpec> = listOf(keener, shardback)

    /** The arena of [spec] in world pixels, from the real area it lives in. */
    fun arenaFor(spec: BossSpec, layout: WorldLayout): BossArena {
        val g = layout.grid(spec.areaId)
        return BossArena.from(spec.arena, g.col0, g.row0, AreaDef.TILE)
    }

    fun brainFor(spec: BossSpec, arena: BossArena, seed: Int = System.nanoTime().toInt()): BossBrain = when (spec.id) {
        keener.id -> KeenerBrain(spec, arena, seed)
        shardback.id -> ShardbackBrain(spec, arena, seed)
        else -> throw IllegalArgumentException("unknown boss ${spec.id}")
    }
}
