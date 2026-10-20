package com.devdgames.hollowknightbutmine.game.story

import com.devdgames.hollowknightbutmine.game.world.AreaDef
import com.devdgames.hollowknightbutmine.game.world.WorldLayout

/**
 * The things lying about the kingdom that can be picked up.
 *
 * Relics are not items in a bag: they are counted per [group], and a task that asks for
 * shards simply reads the count, so a shard taken before the task was accepted still counts.
 * They hang in the air just above the floor and are collected by walking into them.
 */
class RelicDef(
    val id: String,
    val name: String,
    /** Relics of one group are interchangeable to any task that asks for that group. */
    val group: String,
    val areaId: String,
    val col: Int,
    val row: Int,
    val accent: Int,
    /** Said through the dialogue box the moment it is taken. */
    val line: String
)

/** A relic hanging somewhere real. */
class PlacedRelic(val def: RelicDef, val x: Float, val floorY: Float) {
    var taken = false
    /** Runs the bob and the take-away sparkle. */
    var takeAnim = 0f
}

object RelicRoster {

    /**
     * Three lantern shards, thrown out of Dustmere when the well opened. One on the cliffs
     * where the wind can reach it, one on a rock spur over the acid of the Verdant Path, one
     * in the rain of the drowned capital — an easy walk, a jump, and a long way down.
     */
    val cliffs = RelicDef(
        id = "shard_cliffs", name = "Lantern Shard", group = "shard",
        areaId = "cliffs", col = 31, row = 12,
        accent = 0xFFFFD98A.toInt(),
        line = "A lantern shard, still warm, as though somebody had only just put it down."
    )

    val greenpath = RelicDef(
        id = "shard_greenpath", name = "Lantern Shard", group = "shard",
        areaId = "greenpath", col = 20, row = 17,
        accent = 0xFFFFD98A.toInt(),
        line = "A lantern shard on the last dry stone before the acid. Somebody was careful here."
    )

    val city = RelicDef(
        id = "shard_city", name = "Lantern Shard", group = "shard",
        areaId = "city", col = 15, row = 37,
        accent = 0xFFFFD98A.toInt(),
        line = "A lantern shard, and the rain has not managed to put it out."
    )

    val all: List<RelicDef> = listOf(cliffs, greenpath, city)

    fun place(layout: WorldLayout): List<PlacedRelic> = all.map { place(it, layout) }

    fun place(def: RelicDef, layout: WorldLayout): PlacedRelic {
        val g = layout.grid(def.areaId)
        val worldCol = g.col0 + def.col
        val worldRow = g.row0 + def.row
        val floorRow = layout.floorRowBelow(worldCol, worldRow)
            ?: throw IllegalStateException(
                "Relic '${def.id}' at (${def.col},${def.row}) in ${def.areaId} has no floor under it"
            )
        return PlacedRelic(def, worldCol * AreaDef.TILE + AreaDef.TILE * 0.5f, floorRow * AreaDef.TILE)
    }
}
