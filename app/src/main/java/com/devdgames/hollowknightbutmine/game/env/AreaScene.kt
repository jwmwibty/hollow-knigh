package com.devdgames.hollowknightbutmine.game.env

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.devdgames.hollowknightbutmine.game.world.AreaDef
import com.devdgames.hollowknightbutmine.game.world.AreaGrid
import com.devdgames.hollowknightbutmine.game.world.Biome
import com.devdgames.hollowknightbutmine.game.world.EdgeRun
import com.devdgames.hollowknightbutmine.game.world.Flora
import com.devdgames.hollowknightbutmine.game.world.TileRect
import com.devdgames.hollowknightbutmine.game.world.WorldLayout
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Where an enemy should be created when the area is (re)populated. */
class EnemySpawnPoint(val x: Float, val y: Float, val char: Char)

/**
 * The drawable form of one [AreaGrid].
 *
 * Built once: every rock mass, lit edge, crown line, tuft of moss, hanging root, crystal
 * and web is turned into a path with a bounding box at construction time. Drawing is
 * then a culled walk over those lists with no allocation, which is what a software
 * Canvas game loop needs at 60fps with a world this large.
 */
class AreaScene(val grid: AreaGrid, private val layout: WorldLayout) {
    val def: AreaDef = grid.def
    val pal: BiomePalette = Biomes.palette(def.biome)
    val biome: Biome = def.biome

    val bounds = RectF(
        grid.col0 * T, grid.row0 * T,
        (grid.col0 + grid.width) * T, (grid.row0 + grid.height) * T
    )

    /** Physics. */
    val solidRects = ArrayList<RectF>()
    val ledgeRects = ArrayList<RectF>()
    val hazardRects = ArrayList<RectF>()

    /** Gameplay markers. */
    val enemySpawns = ArrayList<EnemySpawnPoint>()
    val benchSpots = ArrayList<FloatArray>()   // x, floorY
    var spawnX = Float.NaN
    var spawnY = Float.NaN

    // ---- art -------------------------------------------------------------------------
    private class Band(val rect: RectF, val shader: Shader)
    private class Piece(val bounds: RectF) {
        val fills = ArrayList<Pair<Path, Int>>()
        val strokes = ArrayList<Triple<Path, Int, Float>>()
        val glows = ArrayList<FloatArray>() // x, y, rx, ry, alpha, warm
    }
    private class LedgeArt(val rect: RectF, val moss: Path?)
    private class PropAt(val x: Float, val y: Float, val seed: Int, val bounds: RectF, val hangY: Float = Float.NaN, val ceilY: Float = Float.NaN)

    private val masses = ArrayList<RectF>()
    private val topBands = ArrayList<Band>()
    private val faceBands = ArrayList<Band>()
    private val bottomRims = ArrayList<RectF>()
    private val pieces = ArrayList<Piece>()
    private val ledgeArt = ArrayList<LedgeArt>()
    /** Grass, moss cushions, roots and flowers, in swaying ~96 px chunks (see [FloraArt]). */
    private val flora = ArrayList<FloraChunk>()
    private val acidPools = ArrayList<RectF>()
    private val spikeBeds = ArrayList<RectF>()
    private val lamps = ArrayList<PropAt>()
    private val landmarks = ArrayList<PropAt>()
    private val wells = ArrayList<RectF>()
    private val candles = ArrayList<FloatArray>()
    private val drips = ArrayList<FloatArray>()  // x, y, phase
    private val shafts = ArrayList<Pair<LightShaft, RectF>>()

    private val rnd = Random(def.id.hashCode())
    private val scratch = Path()

    init {
        buildPhysics()
        buildRock()
        buildLedges()
        buildHazards()
        buildMarkers()
    }

    // ===================================================================== building

    private fun tile(col: Int, row: Int): Char = layout.tileAt(col, row)
    private fun solid(col: Int, row: Int): Boolean = tile(col, row) == '#'

    private fun buildPhysics() {
        for (r in grid.solids) solidRects.add(RectF(r.col * T, r.row * T, r.right * T, r.bottom * T))
        for (l in grid.ledges) ledgeRects.add(RectF(l.col * T, l.row * T, l.right * T, l.row * T + LEDGE_H))
        for (s in grid.spikes) hazardRects.add(RectF(s.col * T + 5f, s.row * T + 18f, s.right * T - 5f, s.bottom * T))
        for (a in grid.acid) hazardRects.add(RectF(a.col * T + 2f, a.row * T + 10f, a.right * T - 2f, a.bottom * T))
    }

    private fun thicknessBelow(run: EdgeRun): Int {
        var t = Int.MAX_VALUE
        for (c in run.col until run.col + run.length) {
            var d = 0
            while (d < 6 && solid(c, run.row + d)) d++
            t = min(t, d)
        }
        return max(1, t)
    }

    private fun thicknessRight(run: EdgeRun): Int {
        var t = Int.MAX_VALUE
        for (r in run.row until run.row + run.length) {
            var d = 0
            while (d < 4 && solid(run.col + d, r)) d++
            t = min(t, d)
        }
        return max(1, t)
    }

    private fun thicknessLeft(run: EdgeRun): Int {
        var t = Int.MAX_VALUE
        for (r in run.row until run.row + run.length) {
            var d = 0
            while (d < 4 && solid(run.col - d, r)) d++
            t = min(t, d)
        }
        return max(1, t)
    }

    private fun mix(a: Int, b: Int, k: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt()
    )

    private fun buildRock() {
        for (r in solidRects) masses.add(r)

        for (run in grid.tops) {
            val x0 = run.col * T
            val x1 = (run.col + run.length) * T
            val y = run.row * T
            val h = min(120f, thicknessBelow(run) * T)
            topBands.add(Band(RectF(x0, y, x1, y + h), LinearGradient(
                0f, y, 0f, y + h,
                intArrayOf(pal.rock, mix(pal.rock, pal.rockDeep, 0.55f), pal.rockDeep),
                floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP
            )))
            pieces.add(buildCrown(run, x0, x1, y))
        }
        for (run in grid.bottoms) {
            val x0 = run.col * T
            val x1 = (run.col + run.length) * T
            val y = (run.row + 1) * T
            bottomRims.add(RectF(x0, y - 3f, x1, y))
            val p = buildHanging(run, x0, x1, y)
            if (p != null) pieces.add(p)
        }
        for (run in grid.leftFaces) {
            // air is on the left of this column of solid tiles
            val x = run.col * T
            val y0 = run.row * T
            val y1 = (run.row + run.length) * T
            val w = min(48f, thicknessRight(run) * T)
            faceBands.add(Band(RectF(x, y0, x + w, y1), LinearGradient(
                x, 0f, x + w, 0f, intArrayOf(pal.rockFace, pal.rockDeep), null, Shader.TileMode.CLAMP
            )))
        }
        for (run in grid.rightFaces) {
            val x = (run.col + 1) * T
            val y0 = run.row * T
            val y1 = (run.row + run.length) * T
            val w = min(48f, thicknessLeft(run) * T)
            faceBands.add(Band(RectF(x - w, y0, x, y1), LinearGradient(
                x - w, 0f, x, 0f, intArrayOf(pal.rockDeep, pal.rockFace), null, Shader.TileMode.CLAMP
            )))
        }
    }

    /** The walkable rim of a rock surface: wavy highlight, moss, tufts and biome litter. */
    private fun buildCrown(run: EdgeRun, x0: Float, x1: Float, y: Float): Piece {
        val piece = Piece(RectF(x0 - 20f, y - 70f, x1 + 20f, y + 30f))
        val phA = rnd.nextFloat() * 6.283f
        val phB = rnd.nextFloat() * 6.283f
        fun crownY(x: Float): Float = y + sin(x * 0.021f + phA) * 1.5f + sin(x * 0.057f + phB) * 0.8f

        // highlight line
        val crown = Path()
        var x = x0
        crown.moveTo(x, crownY(x))
        while (x < x1) { x = min(x1, x + 12f); crown.lineTo(x, crownY(x)) }
        piece.strokes.add(Triple(crown, pal.rockRim, 2.2f))

        // Vegetation: patchy moss, clumped grass, roots and tiny plants, only in biomes that
        // grow them and never beside a hazard (see world/Flora.kt for the rules).
        val profile = Flora.profile(biome)
        if (profile != null) {
            val seed = Flora.seed(def.id, run.col, run.row)
            val plan = Flora.plan(profile, seed, x0, x1, Flora.SURFACE_ROCK, keepOutFor(run))
            val moss = FloraArt.mossPath(plan, seed) { px -> crownY(px) }
            if (moss != null) piece.fills.add(moss to pal.moss)
            flora.addAll(FloraArt.build(plan, profile, pal, seed, x0, x1) { px -> crownY(px) })
        }

        when (biome) {
            Biome.GREEN -> smallMushrooms(piece, x0, x1, y, 0.08f, pal.glow, Color.rgb(196, 216, 200))
            Biome.FUNGAL -> smallMushrooms(piece, x0, x1, y, 0.22f, Color.rgb(222, 120, 88), Color.rgb(226, 206, 180))
            Biome.CRYSTAL -> crystals(piece, x0, x1, y)
            Biome.BASIN -> bones(piece, x0, x1, y)
            Biome.DEEPNEST -> eggsAndWebs(piece, run, x0, x1, y)
            Biome.EDGE -> ashMounds(piece, x0, x1, y)
            Biome.RESTING -> { var cx = x0 + 30f; while (cx < x1 - 20f) { if (rnd.nextFloat() < 0.25f) candles.add(floatArrayOf(cx, y, rnd.nextFloat() * 6f)); cx += 60f } }
            Biome.TOWN, Biome.CROSSROADS, Biome.CITY, Biome.CLIFFS, Biome.WATERWAYS, Biome.ABYSS -> pebbles(piece, x0, x1, y)
        }
        return piece
    }

    private fun smallMushrooms(piece: Piece, x0: Float, x1: Float, y: Float, chance: Float, cap: Int, stem: Int) {
        val caps = Path()
        val stems = Path()
        var x = x0 + 16f
        while (x < x1 - 16f) {
            if (rnd.nextFloat() < chance) {
                val n = 1 + rnd.nextInt(3)
                for (i in 0 until n) {
                    val mx = x + i * 7f - n * 3f
                    val h = 6f + rnd.nextFloat() * 12f
                    val w = 4f + rnd.nextFloat() * 5f
                    stems.addRoundRect(RectF(mx - 1.6f, y - h, mx + 1.6f, y + 1f), 1.5f, 1.5f, Path.Direction.CW)
                    caps.addOval(RectF(mx - w, y - h - w * 0.6f, mx + w, y - h + w * 0.35f), Path.Direction.CW)
                }
                piece.glows.add(floatArrayOf(x, y - 10f, 26f, 18f, 60f, if (biome == Biome.FUNGAL) 1f else 0f))
            }
            x += 22f + rnd.nextFloat() * 30f
        }
        piece.fills.add(stems to stem)
        piece.fills.add(caps to cap)
    }

    private fun crystals(piece: Piece, x0: Float, x1: Float, y: Float) {
        val shards = Path()
        val hi = Path()
        var x = x0 + 14f
        while (x < x1 - 14f) {
            if (rnd.nextFloat() < 0.3f) {
                val n = 2 + rnd.nextInt(3)
                for (i in 0 until n) {
                    val cx = x + (rnd.nextFloat() - 0.5f) * 22f
                    val h = 10f + rnd.nextFloat() * 22f
                    val w = 3f + rnd.nextFloat() * 4f
                    val lean = (rnd.nextFloat() - 0.5f) * 10f
                    shards.moveTo(cx - w, y + 1f); shards.lineTo(cx + lean, y - h); shards.lineTo(cx + w, y + 1f); shards.close()
                    hi.moveTo(cx - w * 0.25f, y); hi.lineTo(cx + lean * 0.9f, y - h * 0.9f); hi.lineTo(cx + w * 0.25f, y); hi.close()
                }
                piece.glows.add(floatArrayOf(x, y - 10f, 34f, 24f, 55f, 0f))
            }
            x += 26f + rnd.nextFloat() * 50f
        }
        piece.fills.add(shards to pal.glow)
        piece.fills.add(hi to Color.argb(120, 255, 255, 255))
    }

    private fun bones(piece: Piece, x0: Float, x1: Float, y: Float) {
        val ribs = Path()
        val skulls = Path()
        var x = x0 + 20f
        while (x < x1 - 20f) {
            if (rnd.nextFloat() < 0.22f) {
                val w = 14f + rnd.nextFloat() * 22f
                val h = 10f + rnd.nextFloat() * 18f
                ribs.addArc(RectF(x - w, y - h, x + w, y + h), 180f, 180f)
                if (rnd.nextFloat() < 0.4f) ribs.addArc(RectF(x - w * 0.6f, y - h * 0.7f, x + w * 0.6f, y + h), 180f, 180f)
            } else if (rnd.nextFloat() < 0.12f) {
                skulls.addOval(RectF(x - 8f, y - 12f, x + 8f, y + 1f), Path.Direction.CW)
            }
            x += 30f + rnd.nextFloat() * 40f
        }
        piece.strokes.add(Triple(ribs, Color.rgb(150, 164, 160), 2.4f))
        piece.fills.add(skulls to Color.rgb(150, 164, 160))
    }

    private fun eggsAndWebs(piece: Piece, run: EdgeRun, x0: Float, x1: Float, y: Float) {
        val eggs = Path()
        var x = x0 + 14f
        while (x < x1 - 14f) {
            if (rnd.nextFloat() < 0.12f) {
                val n = 2 + rnd.nextInt(3)
                for (i in 0 until n) {
                    val ex = x + (rnd.nextFloat() - 0.5f) * 20f
                    val r = 4f + rnd.nextFloat() * 5f
                    eggs.addOval(RectF(ex - r, y - r * 2.2f, ex + r, y + 1f), Path.Direction.CW)
                }
            }
            x += 30f + rnd.nextFloat() * 50f
        }
        piece.fills.add(eggs to Color.rgb(176, 166, 170))
        // corner webs where this surface meets a wall rising above it
        val webs = Path()
        if (solid(run.col - 1, run.row - 1)) cornerWeb(webs, x0, y, 1f)
        if (solid(run.col + run.length, run.row - 1)) cornerWeb(webs, x1, y, -1f)
        piece.strokes.add(Triple(webs, Color.argb(140, 220, 216, 226), 1.2f))
    }

    private fun cornerWeb(p: Path, x: Float, y: Float, dir: Float) {
        val s = 34f + rnd.nextFloat() * 30f
        for (i in 0..4) {
            val a = i / 4f * 1.5708f
            p.moveTo(x, y)
            p.lineTo(x + kotlin.math.cos(a) * s * dir, y - sin(a) * s)
        }
        for (k in 1..3) {
            val rr = s * k / 3.4f
            p.moveTo(x + rr * dir, y)
            p.quadTo(x + rr * 0.62f * dir, y - rr * 0.62f, x, y - rr)
        }
    }

    private fun ashMounds(piece: Piece, x0: Float, x1: Float, y: Float) {
        val mounds = Path()
        var x = x0 + 10f
        while (x < x1 - 10f) {
            if (rnd.nextFloat() < 0.4f) {
                val w = 16f + rnd.nextFloat() * 30f
                val h = 4f + rnd.nextFloat() * 7f
                mounds.moveTo(x - w, y + 1f)
                mounds.quadTo(x, y - h * 2f, x + w, y + 1f)
                mounds.close()
            }
            x += 28f + rnd.nextFloat() * 40f
        }
        piece.fills.add(mounds to Color.argb(190, 150, 150, 158))
    }

    private fun pebbles(piece: Piece, x0: Float, x1: Float, y: Float) {
        val p = Path()
        var x = x0 + 8f
        while (x < x1 - 8f) {
            if (rnd.nextFloat() < 0.35f) {
                val w = 3f + rnd.nextFloat() * 5f
                p.addOval(RectF(x - w, y - w * 0.6f, x + w, y + 1f), Path.Direction.CW)
            }
            x += 14f + rnd.nextFloat() * 34f
        }
        piece.fills.add(p to pal.rockFace)
    }

    /** Things hanging from an exposed ceiling. */
    private fun buildHanging(run: EdgeRun, x0: Float, x1: Float, y: Float): Piece? {
        val piece = Piece(RectF(x0 - 10f, y - 4f, x1 + 10f, y + 140f))
        maybeShaft(run, x0, x1, y)
        when (biome) {
            Biome.GREEN, Biome.FUNGAL, Biome.WATERWAYS -> {
                val vines = Path()
                var x = x0 + 6f
                while (x < x1 - 6f) {
                    if (rnd.nextFloat() < 0.5f) {
                        val len = 14f + rnd.nextFloat() * 70f
                        EnvArt.tapered(vines, x, y - 1f, x + (rnd.nextFloat() - 0.5f) * 10f, y + len * 0.55f, x + (rnd.nextFloat() - 0.5f) * 16f, y + len, 4f, 0.6f, 5)
                    }
                    x += 12f + rnd.nextFloat() * 26f
                }
                piece.fills.add(vines to pal.moss)
                if (biome == Biome.GREEN) {
                    var gx = x0 + 20f
                    while (gx < x1 - 20f) { if (rnd.nextFloat() < 0.2f) piece.glows.add(floatArrayOf(gx, y + 30f + rnd.nextFloat() * 30f, 2f, 0f, 255f, 0f)); gx += 30f }
                }
            }
            Biome.DEEPNEST -> {
                val strands = Path()
                val cocoons = Path()
                var x = x0 + 8f
                while (x < x1 - 8f) {
                    if (rnd.nextFloat() < 0.5f) {
                        val len = 20f + rnd.nextFloat() * 90f
                        strands.moveTo(x, y)
                        strands.quadTo(x + (rnd.nextFloat() - 0.5f) * 10f, y + len * 0.5f, x + (rnd.nextFloat() - 0.5f) * 6f, y + len)
                        if (rnd.nextFloat() < 0.3f) cocoons.addOval(RectF(x - 7f, y + len - 4f, x + 7f, y + len + 18f), Path.Direction.CW)
                    }
                    x += 16f + rnd.nextFloat() * 30f
                }
                piece.strokes.add(Triple(strands, Color.argb(150, 210, 206, 216), 1.2f))
                piece.fills.add(cocoons to Color.rgb(150, 142, 150))
            }
            Biome.CITY -> {
                var x = x0 + 20f
                while (x < x1 - 10f) { if (rnd.nextFloat() < 0.35f) drips.add(floatArrayOf(x, y, rnd.nextFloat())); x += 40f + rnd.nextFloat() * 60f }
                return null
            }
            Biome.ABYSS -> return null
            else -> {
                // stalactites (crystal ones glow)
                val stal = Path()
                var x = x0 + 8f
                while (x < x1 - 8f) {
                    if (rnd.nextFloat() < 0.45f) {
                        val len = 10f + rnd.nextFloat() * 46f
                        val w = 5f + rnd.nextFloat() * 7f
                        stal.moveTo(x - w, y - 1f); stal.lineTo(x + (rnd.nextFloat() - 0.5f) * 6f, y + len); stal.lineTo(x + w, y - 1f); stal.close()
                        if (biome == Biome.CRYSTAL && rnd.nextFloat() < 0.5f) piece.glows.add(floatArrayOf(x, y + len * 0.6f, 30f, 30f, 50f, 0f))
                    }
                    x += 14f + rnd.nextFloat() * 30f
                }
                piece.fills.add(stal to if (biome == Biome.CRYSTAL) pal.glow else pal.rockFace)
                if (biome == Biome.WATERWAYS || biome == Biome.CROSSROADS) {
                    var dx = x0 + 30f
                    while (dx < x1 - 10f) { if (rnd.nextFloat() < 0.2f) drips.add(floatArrayOf(dx, y, rnd.nextFloat())); dx += 60f + rnd.nextFloat() * 60f }
                }
            }
        }
        return piece
    }

    /** Volumetric light falling from a few ceilings; which biomes get it, and in what colour. */
    private fun maybeShaft(run: EdgeRun, x0: Float, x1: Float, y: Float) {
        val (chance, color, alpha) = when (biome) {
            Biome.GREEN -> Triple(0.22f, Color.rgb(150, 230, 190), 70)
            Biome.CROSSROADS -> Triple(0.10f, Color.rgb(190, 205, 235), 55)
            Biome.CITY -> Triple(0.16f, Color.rgb(140, 170, 230), 60)
            Biome.FUNGAL -> Triple(0.10f, Color.rgb(240, 190, 120), 50)
            Biome.CRYSTAL -> Triple(0.14f, Color.rgb(240, 170, 230), 55)
            Biome.RESTING -> Triple(0.12f, Color.rgb(230, 200, 150), 50)
            else -> return
        }
        if (run.length < 3 || rnd.nextFloat() > chance) return
        val x = x0 + 40f + rnd.nextFloat() * (x1 - x0 - 80f).coerceAtLeast(1f)
        // how far down can the light fall before hitting rock?
        var depth = 1
        val col = (x / T).toInt()
        while (depth < 10 && !solid(col, run.row + depth)) depth++
        val len = (depth - 1) * T + 20f
        if (len < 100f) return
        val lean = (rnd.nextFloat() - 0.5f) * 60f
        shafts.add(LightShaft(x, y - 4f, y + len, 14f + rnd.nextFloat() * 20f, 50f + rnd.nextFloat() * 40f, lean, color, alpha)
            to RectF(x - 120f, y - 10f, x + 120f, y + len + 10f))
    }

    private fun buildLedges() {
        val profile = Flora.profile(biome)
        for (i in ledgeRects.indices) {
            val r = ledgeRects[i]
            var moss: Path? = null
            if (profile != null) {
                val seed = Flora.seed(def.id, (r.left / T).toInt(), (r.top / T).toInt(), 17)
                val plan = Flora.plan(profile, seed, r.left + 3f, r.right - 3f, Flora.SURFACE_LEDGE)
                val top = r.top + 0.5f
                moss = FloraArt.mossPath(plan, seed) { top }
                flora.addAll(FloraArt.build(plan, profile, pal, seed, r.left + 3f, r.right - 3f) { top })
            }
            ledgeArt.add(LedgeArt(r, moss))
        }
    }

    /**
     * x-intervals along [run] where a hazard sits on (or is level with) the surface, so plants
     * keep clear of spike beds and acid and can never be mistaken for them.
     */
    private fun keepOutFor(run: EdgeRun): FloatArray {
        val x0 = run.col * T
        val x1 = (run.col + run.length) * T
        val out = ArrayList<Float>()
        for (s in grid.spikes) {
            if (s.bottom == run.row && s.right * T > x0 - 1f && s.col * T < x1 + 1f) {
                out.add(s.col * T); out.add(s.right * T)
            }
        }
        for (a in grid.acid) {
            if (a.bottom == run.row && a.right * T > x0 - 1f && a.col * T < x1 + 1f) {
                out.add(a.col * T); out.add(a.right * T)
            } else if (a.row <= run.row && a.bottom > run.row) {
                // a pool beside this platform, level with its surface: keep the end clear
                if (abs(a.right * T - x0) < 1f) { out.add(x0 - 8f); out.add(x0 + 8f) }
                if (abs(a.col * T - x1) < 1f) { out.add(x1 - 8f); out.add(x1 + 8f) }
            }
        }
        return out.toFloatArray()
    }

    private fun buildHazards() {
        for (a in grid.acid) acidPools.add(RectF(a.col * T, a.row * T, a.right * T, a.bottom * T))
        for (s in grid.spikes) spikeBeds.add(RectF(s.col * T, s.row * T + 12f, s.right * T, s.bottom * T))
    }

    private fun buildMarkers() {
        for (m in grid.markers) {
            val cx = m.col * T + T / 2f
            val floorRow = layout.floorRowBelow(m.col, m.row)
            val floorY = if (floorRow != null) floorRow * T else (m.row + 1) * T
            when (m.char) {
                'S' -> { spawnX = cx; spawnY = floorY }
                'B' -> benchSpots.add(floatArrayOf(cx, floorY))
                'L' -> {
                    var up = 0
                    while (up < 8 && !solid(m.col, m.row - up - 1)) up++
                    val ceilY = (m.row - up) * T
                    val hangY = m.row * T + T / 2f
                    lamps.add(PropAt(cx, floorY, m.col * 31 + m.row * 17, RectF(cx - 80f, min(ceilY, floorY - 220f), cx + 80f, floorY + 20f), hangY, ceilY))
                }
                '*' -> landmarks.add(PropAt(cx, floorY, m.col * 13 + m.row * 29, RectF(cx - 120f, floorY - 260f, cx + 120f, floorY + 20f)))
                else -> if (AreaDef.isEnemy(m.char)) {
                    val y = if (m.char == 'f' || m.char == '1') m.row * T + T / 2f else (m.row + 1) * T
                    enemySpawns.add(EnemySpawnPoint(cx, y, m.char))
                }
            }
        }
        if (biome == Biome.TOWN) {
            // A stone well over every shaft that leaves the surface floor.
            for (r in grid.airRuns) {
                if (r.row == grid.row0 + grid.height - 1 && r.w in 3..6) {
                    wells.add(RectF(r.col * T, (r.row - 4) * T, r.right * T, (r.row - 4) * T))
                }
            }
        }
    }

    // ===================================================================== drawing

    private fun vis(r: RectF, view: RectF): Boolean =
        r.right > view.left && r.left < view.right && r.bottom > view.top && r.top < view.bottom

    /**
     * Everything that belongs to the world layer, behind the actors. [view] is the camera
     * rectangle in world units.
     */
    fun drawWorld(canvas: Canvas, paint: Paint, view: RectF, t: Float) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = pal.rockDeep
        for (r in masses) if (vis(r, view)) canvas.drawRect(r, paint)

        for (b in faceBands) if (vis(b.rect, view)) { paint.shader = b.shader; canvas.drawRect(b.rect, paint) }
        for (b in topBands) if (vis(b.rect, view)) { paint.shader = b.shader; canvas.drawRect(b.rect, paint) }
        paint.shader = null
        paint.color = pal.rockFace
        paint.alpha = 150
        for (r in bottomRims) if (vis(r, view)) canvas.drawRect(r, paint)
        paint.alpha = 255

        for (p in pieces) if (vis(p.bounds, view)) drawPiece(canvas, paint, p)

        for (l in ledgeArt) if (vis(l.rect, view)) drawLedge(canvas, paint, l)

        // Plants grow over the rock and ledges but under every actor, prop and hazard.
        for (c in flora) if (vis(c.bounds, view)) FloraArt.draw(canvas, paint, c, t)

        for (a in acidPools) if (vis(a, view)) Props.acidPool(canvas, paint, a, pal.hazard, pal.hazardLit, t)
        for (s in spikeBeds) if (vis(s, view)) Props.spikeBed(canvas, paint, s.left, s.right, s.top, s.bottom, pal.hazard, pal.hazardLit)

        for (w in wells) if (vis(RectF(w.left - 40f, w.top - 140f, w.right + 40f, w.top + 40f), view)) Props.well(canvas, paint, w.left, w.right, w.top)
        for (l in landmarks) if (vis(l.bounds, view)) Props.landmark(canvas, paint, biome, l.x, l.y, pal.glow, t, l.seed)
        for (l in lamps) if (vis(l.bounds, view)) Props.lamp(canvas, paint, biome, l.x, l.y, l.hangY, l.ceilY, pal.glow, t, l.seed)

        if (candles.isNotEmpty()) {
            for (c in candles) {
                if (c[0] < view.left - 40f || c[0] > view.right + 40f || c[1] < view.top - 40f || c[1] > view.bottom + 40f) continue
                val fl = 0.85f + sin(t * 7f + c[2]) * 0.15f
                paint.shader = null
                paint.style = Paint.Style.FILL
                paint.color = Color.rgb(210, 196, 170)
                canvas.drawRect(c[0] - 2.5f, c[1] - 10f, c[0] + 2.5f, c[1], paint)
                EnvArt.bloom(canvas, paint, c[0], c[1] - 13f, 28f * fl, 28f * fl, EnvArt.glowWarm, 120)
                paint.color = Color.rgb(255, 228, 160)
                canvas.drawOval(c[0] - 2f, c[1] - 17f - fl * 2f, c[0] + 2f, c[1] - 10f, paint)
            }
        }
        if (drips.isNotEmpty()) {
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.6f
            paint.color = pal.glow
            for (d in drips) {
                if (d[0] < view.left || d[0] > view.right) continue
                val phase = (t * 0.9f + d[2]) % 1f
                val dy = d[1] + phase * phase * 180f
                if (dy < view.top - 20f || dy > view.bottom + 20f) continue
                paint.alpha = (200 * (1f - phase)).toInt().coerceIn(0, 255)
                canvas.drawLine(d[0], dy, d[0], dy + 8f + phase * 10f, paint)
            }
            paint.alpha = 255
            paint.style = Paint.Style.FILL
        }
    }

    /** Additive light shafts, drawn over the actors. */
    fun drawLights(canvas: Canvas, paint: Paint, view: RectF) {
        for ((shaft, b) in shafts) if (vis(b, view)) shaft.draw(canvas, paint)
    }

    private fun drawPiece(canvas: Canvas, paint: Paint, p: Piece) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        for ((path, color) in p.fills) { paint.color = color; canvas.drawPath(path, paint) }
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        for ((path, color, w) in p.strokes) { paint.color = color; paint.strokeWidth = w; canvas.drawPath(path, paint) }
        paint.strokeCap = Paint.Cap.BUTT
        paint.style = Paint.Style.FILL
        for (g in p.glows) {
            if (g[3] <= 0f) {
                paint.color = if (g[5] > 0.5f) EnvPalette.sporeWarm else pal.glow
                canvas.drawCircle(g[0], g[1], g[2], paint)
            } else {
                EnvArt.bloom(canvas, paint, g[0], g[1], g[2], g[3], if (g[5] > 0.5f) EnvArt.glowWarm else EnvArt.glowTeal, g[4].toInt())
            }
        }
    }

    private fun drawLedge(canvas: Canvas, paint: Paint, l: LedgeArt) {
        val r = l.rect
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = pal.ledge
        canvas.drawRoundRect(r.left + 1f, r.top, r.right - 1f, r.bottom + 4f, 5f, 5f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = pal.ledgeLit
        canvas.drawLine(r.left + 4f, r.top + 1.5f, r.right - 4f, r.top + 1.5f, paint)
        paint.strokeWidth = 1.2f
        paint.alpha = 120
        var x = r.left + 14f
        while (x < r.right - 8f) { canvas.drawLine(x, r.top + 4f, x - 2f, r.bottom + 2f, paint); x += 26f }
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        val moss = l.moss
        if (moss != null) { paint.color = pal.moss; canvas.drawPath(moss, paint) }
    }

    companion object {
        const val T = AreaDef.TILE
        const val LEDGE_H = 12f
    }
}
