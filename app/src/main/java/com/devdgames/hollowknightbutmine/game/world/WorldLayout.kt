package com.devdgames.hollowknightbutmine.game.world

/** An axis-aligned block of tiles, in tile units. */
data class TileRect(val col: Int, val row: Int, val w: Int, val h: Int) {
    val right get() = col + w
    val bottom get() = row + h
}

/** A horizontal run of exposed surface along the edge of a solid block (tile units). */
data class EdgeRun(val col: Int, val row: Int, val length: Int)

/** A single-tile marker in an area grid (spawn, bench, enemy, lamp, prop). */
data class Marker(val char: Char, val col: Int, val row: Int)

/**
 * A parsed [AreaDef]: collision rectangles merged from the tile grid, hazards, ledges and
 * markers. Everything is in *world tile* coordinates (area origin already applied) so the
 * renderer and the physics can use it directly.
 *
 * Pure Kotlin (no Android types) so the layout can be validated and rendered on the JVM.
 */
class AreaGrid(val def: AreaDef) {
    val solids = ArrayList<TileRect>()
    val ledges = ArrayList<TileRect>()
    val spikes = ArrayList<TileRect>()
    val acid = ArrayList<TileRect>()
    val markers = ArrayList<Marker>()

    /** Exposed surfaces of the solid blocks: where a walkable top / ceiling / wall faces air. */
    val tops = ArrayList<EdgeRun>()
    val bottoms = ArrayList<EdgeRun>()
    /** Vertical wall faces; `length` runs downwards. Left faces air on the left, etc. */
    val leftFaces = ArrayList<EdgeRun>()
    val rightFaces = ArrayList<EdgeRun>()

    /** Top-row air tiles merged into rows, used for the in-game map silhouette. */
    val airRuns = ArrayList<TileRect>()

    val col0 = def.originX
    val row0 = def.originY
    val width = def.width
    val height = def.height

    init {
        parse()
    }

    private fun solidAt(c: Int, r: Int): Boolean = AreaDef.isSolid(def.tile(c, r))

    private fun parse() {
        val used = Array(height) { BooleanArray(width) }

        // Greedy rectangle merge of solid tiles: horizontal run first, then extend downwards
        // while the exact same run repeats.
        for (r in 0 until height) {
            var c = 0
            while (c < width) {
                if (!solidAt(c, r) || used[r][c]) { c++; continue }
                var runEnd = c
                while (runEnd + 1 < width && solidAt(runEnd + 1, r) && !used[r][runEnd + 1]) runEnd++
                val w = runEnd - c + 1
                var h = 1
                outer@ while (r + h < height) {
                    for (cc in c..runEnd) {
                        if (!solidAt(cc, r + h) || used[r + h][cc]) break@outer
                    }
                    // Only merge when the run below is bounded exactly like this one, so that
                    // large masses stay rectangular and we don't swallow stair-steps.
                    if (c > 0 && solidAt(c - 1, r + h) && !used[r + h][c - 1]) break
                    if (runEnd + 1 < width && solidAt(runEnd + 1, r + h) && !used[r + h][runEnd + 1]) break
                    h++
                }
                for (rr in r until r + h) for (cc in c..runEnd) used[rr][cc] = true
                solids.add(TileRect(col0 + c, row0 + r, w, h))
                c = runEnd + 1
            }
        }

        // Exposed edges, as runs of tiles.
        for (r in 0 until height) {
            var c = 0
            while (c < width) {
                if (solidAt(c, r) && !solidAt(c, r - 1) && r > 0) {
                    var e = c
                    while (e + 1 < width && solidAt(e + 1, r) && !solidAt(e + 1, r - 1)) e++
                    tops.add(EdgeRun(col0 + c, row0 + r, e - c + 1))
                    c = e + 1
                } else c++
            }
            c = 0
            while (c < width) {
                if (solidAt(c, r) && !solidAt(c, r + 1) && r < height - 1) {
                    var e = c
                    while (e + 1 < width && solidAt(e + 1, r) && !solidAt(e + 1, r + 1)) e++
                    bottoms.add(EdgeRun(col0 + c, row0 + r, e - c + 1))
                    c = e + 1
                } else c++
            }
        }
        for (c in 0 until width) {
            var r = 0
            while (r < height) {
                if (solidAt(c, r) && !solidAt(c - 1, r) && c > 0) {
                    var e = r
                    while (e + 1 < height && solidAt(c, e + 1) && !solidAt(c - 1, e + 1)) e++
                    leftFaces.add(EdgeRun(col0 + c, row0 + r, e - r + 1))
                    r = e + 1
                } else r++
            }
            r = 0
            while (r < height) {
                if (solidAt(c, r) && !solidAt(c + 1, r) && c < width - 1) {
                    var e = r
                    while (e + 1 < height && solidAt(c, e + 1) && !solidAt(c + 1, e + 1)) e++
                    rightFaces.add(EdgeRun(col0 + c, row0 + r, e - r + 1))
                    r = e + 1
                } else r++
            }
        }

        // Ledges, spikes, acid: horizontal runs. Acid additionally merges vertically.
        val acidUsed = Array(height) { BooleanArray(width) }
        for (r in 0 until height) {
            var c = 0
            while (c < width) {
                val ch = def.tile(c, r)
                when {
                    ch == '=' -> {
                        var e = c
                        while (e + 1 < width && def.tile(e + 1, r) == '=') e++
                        ledges.add(TileRect(col0 + c, row0 + r, e - c + 1, 1)); c = e + 1
                    }
                    ch == '^' -> {
                        var e = c
                        while (e + 1 < width && def.tile(e + 1, r) == '^') e++
                        spikes.add(TileRect(col0 + c, row0 + r, e - c + 1, 1)); c = e + 1
                    }
                    ch == '~' && !acidUsed[r][c] -> {
                        var e = c
                        while (e + 1 < width && def.tile(e + 1, r) == '~') e++
                        var h = 1
                        outer@ while (r + h < height) {
                            for (cc in c..e) if (def.tile(cc, r + h) != '~' || acidUsed[r + h][cc]) break@outer
                            h++
                        }
                        for (rr in r until r + h) for (cc in c..e) acidUsed[rr][cc] = true
                        acid.add(TileRect(col0 + c, row0 + r, e - c + 1, h)); c = e + 1
                    }
                    ch != ' ' && ch != '#' && ch != '=' && ch != '^' && ch != '~' -> {
                        markers.add(Marker(ch, col0 + c, row0 + r)); c++
                    }
                    else -> c++
                }
            }
        }

        // Air runs for the map silhouette.
        for (r in 0 until height) {
            var c = 0
            while (c < width) {
                if (!solidAt(c, r)) {
                    var e = c
                    while (e + 1 < width && !solidAt(e + 1, r)) e++
                    airRuns.add(TileRect(col0 + c, row0 + r, e - c + 1, 1)); c = e + 1
                } else c++
            }
        }
    }

    fun worldTile(worldCol: Int, worldRow: Int): Char = def.tile(worldCol - col0, worldRow - row0)
}

/**
 * The whole kingdom: all areas on one tile grid, plus validation and the filler rock that
 * seals the gaps between areas.
 */
class WorldLayout(val defs: List<AreaDef>) {
    val grids: List<AreaGrid> = defs.map { AreaGrid(it) }
    private val byId = grids.associateBy { it.def.id }

    val minCol = defs.minOf { it.originX }
    val minRow = defs.minOf { it.originY }
    val maxCol = defs.maxOf { it.originX + it.width }   // exclusive
    val maxRow = defs.maxOf { it.originY + it.height }  // exclusive

    fun grid(id: String): AreaGrid = byId.getValue(id)

    fun areaAt(worldCol: Int, worldRow: Int): AreaGrid? {
        for (g in grids) if (g.def.containsTile(worldCol, worldRow)) return g
        return null
    }

    fun tileAt(worldCol: Int, worldRow: Int): Char = areaAt(worldCol, worldRow)?.worldTile(worldCol, worldRow) ?: '#'

    /** The player start tile, from the 'S' marker. */
    fun spawnTile(): Marker = grids.flatMap { it.markers }.first { it.char == 'S' }

    /** Rock that fills everything not covered by an area, so there are no holes into nothing. */
    fun fillerRects(margin: Int = 10): List<TileRect> {
        val c0 = minCol - margin
        val r0 = minRow - margin
        val w = (maxCol - minCol) + margin * 2
        val h = (maxRow - minRow) + margin * 2
        val covered = Array(h) { BooleanArray(w) }
        for (g in grids) {
            for (r in 0 until g.height) for (c in 0 until g.width) covered[g.row0 + r - r0][g.col0 + c - c0] = true
            if (g.def.openSky) {
                // Keep the sky open above surface areas.
                for (r in 0 until (g.row0 - r0)) for (c in 0 until g.width) covered[r][g.col0 + c - c0] = true
            }
        }
        val out = ArrayList<TileRect>()
        val used = Array(h) { BooleanArray(w) }
        for (r in 0 until h) {
            var c = 0
            while (c < w) {
                if (covered[r][c] || used[r][c]) { c++; continue }
                var e = c
                while (e + 1 < w && !covered[r][e + 1] && !used[r][e + 1]) e++
                var hh = 1
                outer@ while (r + hh < h) {
                    for (cc in c..e) if (covered[r + hh][cc] || used[r + hh][cc]) break@outer
                    hh++
                }
                for (rr in r until r + hh) for (cc in c..e) used[rr][cc] = true
                out.add(TileRect(c + c0, r + r0, e - c + 1, hh))
                c = e + 1
            }
        }
        return out
    }

    /** One passage between two areas. */
    data class Opening(val fromId: String, val toId: String, val col: Int, val row: Int)

    /** Every border tile that is open on both sides. */
    fun openings(): List<Opening> {
        val out = ArrayList<Opening>()
        for (g in grids) {
            val d = g.def
            for (r in 0 until g.height) for (c in 0 until g.width) {
                if (!AreaDef.isAir(d.tile(c, r))) continue
                val onBorder = c == 0 || r == 0 || c == g.width - 1 || r == g.height - 1
                if (!onBorder) continue
                val wc = g.col0 + c
                val wr = g.row0 + r
                val neighbours = listOf(wc - 1 to wr, wc + 1 to wr, wc to wr - 1, wc to wr + 1)
                for ((nc, nr) in neighbours) {
                    if (d.containsTile(nc, nr)) continue
                    val other = areaAt(nc, nr) ?: continue
                    if (AreaDef.isAir(other.worldTile(nc, nr))) out.add(Opening(d.id, other.def.id, wc, wr))
                }
            }
        }
        return out
    }

    /** Human readable problems with the atlas. Empty when the world is consistent. */
    fun validate(): List<String> {
        val issues = ArrayList<String>()
        // Overlaps
        for (i in grids.indices) for (j in i + 1 until grids.size) {
            val a = grids[i].def
            val b = grids[j].def
            val overlap = a.originX < b.originX + b.width && b.originX < a.originX + a.width &&
                a.originY < b.originY + b.height && b.originY < a.originY + a.height
            if (overlap) issues.add("Areas '${a.id}' and '${b.id}' overlap")
        }
        // Row widths
        for (g in grids) {
            val raw = g.def.rows
            raw.forEachIndexed { r, row -> if (row.length != g.width) issues.add("${g.def.id} row $r has width ${row.length}, expected ${g.width}") }
        }
        // Markers must stand in air and (for ground things) have something to land on eventually
        for (g in grids) {
            val spawns = g.markers.count { it.char == 'S' }
            if (spawns > 1) issues.add("${g.def.id} has $spawns spawns")
        }
        if (grids.sumOf { m -> m.markers.count { it.char == 'S' } } != 1) issues.add("World needs exactly one 'S' spawn")
        // Benches must come to rest on solid rock (they are traced down to the floor).
        for (g in grids) for (m in g.markers) {
            if (m.char == 'B') {
                val floor = floorRowBelow(m.col, m.row)
                if (floor == null || tileAt(m.col, floor) != '#') issues.add("${g.def.id}: bench at (${m.col - g.col0},${m.row - g.row0}) has no solid floor under it")
            }
        }
        // Enemy / bench / spawn markers shouldn't fall into acid or spikes: trace straight down.
        for (g in grids) for (m in g.markers) {
            if (m.char == 'L' || m.char == '*') continue
            if (m.char == 'f' || m.char == '1') continue // flyers hover
            var r = m.row
            while (r < maxRow + 1) {
                val t = tileAt(m.col, r)
                if (t == '#' || t == '=') break
                if (t == '~' || t == '^') { issues.add("${g.def.id}: '${m.char}' at (${m.col - g.col0},${m.row - g.row0}) drops into hazard '$t'"); break }
                r++
            }
        }
        // Reachability: flood fill air through the whole world from spawn (ignores gravity).
        val spawn = grids.flatMap { it.markers }.firstOrNull { it.char == 'S' }
        if (spawn != null) {
            val seen = HashSet<Long>()
            val stack = ArrayDeque<Pair<Int, Int>>()
            stack.add(spawn.col to spawn.row)
            val reachedAreas = HashSet<String>()
            while (stack.isNotEmpty()) {
                val (c, r) = stack.removeLast()
                val key = c.toLong() * 100000L + r
                if (!seen.add(key)) continue
                val g = areaAt(c, r) ?: continue
                val t = g.worldTile(c, r)
                if (!AreaDef.isAir(t)) continue
                reachedAreas.add(g.def.id)
                stack.add(c + 1 to r); stack.add(c - 1 to r); stack.add(c to r + 1); stack.add(c to r - 1)
            }
            for (g in grids) if (g.def.id !in reachedAreas) issues.add("Area '${g.def.id}' is not connected to the spawn")
        }
        return issues
    }

    /** Row of the first solid or ledge tile straight below (col,row), or null if it falls out of the world. */
    fun floorRowBelow(col: Int, row: Int): Int? {
        var r = row + 1
        while (r < maxRow + 2) {
            val t = tileAt(col, r)
            if (t == '#' || t == '=') return r
            if (t == '~' || t == '^') return null
            r++
        }
        return null
    }

    /** ASCII composite of the whole kingdom (for debugging / tests). */
    fun render(): String {
        val sb = StringBuilder()
        for (r in minRow until maxRow) {
            for (c in minCol until maxCol) {
                val g = areaAt(c, r)
                sb.append(if (g == null) '%' else g.worldTile(c, r))
            }
            sb.append('\n')
        }
        return sb.toString()
    }
}
