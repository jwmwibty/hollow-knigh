package com.devdgames.hollowknightbutmine.game.world

/**
 * The visual / thematic family an area belongs to. Each biome has its own palette,
 * parallax backdrop, props and weather (see env/Biomes.kt and env/Backdrops.kt).
 */
enum class Biome {
    TOWN,        // Surface town: grey stone, lamp posts, falling ash, dark sky
    CLIFFS,      // Wind-scoured cliffs above the kingdom
    CROSSROADS,  // Blue-grey hub caverns with hanging lanterns and old arches
    GREEN,       // Lush mossy caves, acid pools, bioluminescent spores
    FUNGAL,      // Purple-brown fungus caverns, giant mushrooms, warm spores
    CITY,        // Rain-soaked blue city of towers and lamps
    CRYSTAL,     // Pink glowing crystal mines
    RESTING,     // Quiet lavender crypts, graves and drifting essence
    EDGE,        // Pale ash-fall cliffs at the kingdom's far edge
    WATERWAYS,   // Green sewer tunnels and standing water
    DEEPNEST,    // Near-black web-strewn tunnels
    BASIN,       // Ancient bone-strewn hollows below everything
    ABYSS        // Pure void
}

/**
 * One authored area of the kingdom.
 *
 * The map is a plain ASCII grid; every character is one 40px tile. All areas share one
 * world tile grid ([originX], [originY] are tile offsets), so an opening on the edge of one
 * area lines up with the opening of its neighbour and the whole kingdom plays as a single
 * continuous, interconnected space - the way Hallownest does.
 *
 * Legend
 * ------
 *  ' '  air
 *  '#'  solid rock
 *  '='  thin one-way ledge (jump through from below)
 *  '^'  spikes (hazard)
 *  '~'  acid / deep water (hazard)
 *  'S'  player start
 *  'B'  bench (rest point: heals, sets respawn)
 *  'L'  lamp / light source prop
 *  '*'  biome-specific landmark prop (house, grave, crystal, mushroom, statue...)
 *  'c'  crawler       'f' flyer       'p' spitter      'h' shielder     'm' charger
 *  'M'  moss charger (mini-boss)      '1' Broodmother  '2' Ossuary Titan '3' Colonnade Knight
 */
class AreaDef(
    val id: String,
    val name: String,
    val subtitle: String,
    val biome: Biome,
    val originX: Int,
    val originY: Int,
    rows: List<String>,
    /** The top border opens onto sky rather than rock (surface areas). */
    val openSky: Boolean = false,
    /** Ambient darkness 0..1 applied as a vignette around the player. */
    val darkness: Float = 0f
) {
    val width: Int = rows.maxOf { it.length }
    val height: Int = rows.size
    val rows: List<String> = rows.map { it.padEnd(width, ' ') }

    fun tile(col: Int, row: Int): Char =
        if (col < 0 || row < 0 || col >= width || row >= height) '#' else rows[row][col]

    fun containsTile(worldCol: Int, worldRow: Int): Boolean =
        worldCol >= originX && worldRow >= originY &&
            worldCol < originX + width && worldRow < originY + height

    companion object {
        const val TILE = 40f

        fun isAir(c: Char): Boolean = c != '#'
        fun isSolid(c: Char): Boolean = c == '#'
        fun isLedge(c: Char): Boolean = c == '='
        fun isHazard(c: Char): Boolean = c == '^' || c == '~'
        fun isEnemy(c: Char): Boolean = c in "cfphmM123"
        fun isBoss(c: Char): Boolean = c in "M123"
    }
}
