package com.devdgames.hollowknightbutmine.game.world

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Ground vegetation: where plants grow on a surface, and what they look like.
 *
 * Pure Kotlin (no Android types), so every rule here is unit-tested on the JVM. The renderer
 * ([com.devdgames.hollowknightbutmine.game.env.FloraArt]) only turns a [FloraPlan] into paths.
 *
 * Design rules, in the order they were applied:
 *  - Vegetation is *patchy*. A low-frequency noise field decides where plants may grow, so a
 *    surface alternates between lush stretches and bare stone instead of one continuous fringe.
 *  - Blades come in clumps with a tall centre and shorter, leaning outer blades, plus the
 *    occasional stray stalk and low dark undergrowth. Heights are skewed towards short.
 *  - Every random decision comes from [FloraHash] seeded by stable (area, platform) ids, so the
 *    result is identical on every launch and never changes between frames.
 *  - Plants never grow next to hazards: callers pass keep-out intervals for spike beds and
 *    acid, so grass can not be mistaken for something that hurts.
 *  - Every biome grows something: the lush caves carry dense vegetation, while stone, crystal,
 *    bone and the abyss keep only scattered tufts, so there is cuttable grass in more places.
 */

/** Stateless integer hashing and 1-D value noise. Same inputs, same outputs, always. */
object FloraHash {
    fun mix(a: Int, b: Int): Int {
        var h = a * -1640531527 + b
        h = h xor (h ushr 16)
        h *= -2048144789
        h = h xor (h ushr 13)
        h *= -1028477387
        h = h xor (h ushr 16)
        return h
    }

    /** Uniform in [0, 1). */
    fun unit(seed: Int, i: Int): Float = (mix(seed, i) ushr 8) * (1f / 16777216f)

    /** Smooth value noise in [0, 1], one lattice point per unit of [x]. */
    fun noise(seed: Int, x: Float): Float {
        val i = floor(x).toInt()
        val f = x - i
        val a = unit(seed, i)
        val b = unit(seed, i + 1)
        val u = f * f * (3f - 2f * f)
        return a + (b - a) * u
    }
}

/** A tiny sequential generator whose whole stream is fixed by one seed. */
class FloraRng(private val seed: Int) {
    private var n = 0
    fun next(): Float {
        n++
        return FloraHash.unit(seed, n)
    }

    fun range(a: Float, b: Float): Float = a + (b - a) * next()
    fun int(a: Int, bInclusive: Int): Int = min(bInclusive, a + (next() * (bInclusive - a + 1)).toInt())
    fun chance(p: Float): Boolean = next() < p
}

/** One curved, tapered leaf. [x] is its root; [base] is the root's height above the surface. */
class Blade(
    val x: Float,
    val h: Float,
    val w: Float,
    val lean: Float,
    val bend: Float,
    /** 0 = dark back layer, 1 = mid, 2 = light front layer. */
    val layer: Int,
    val base: Float = 0f
)

/** A low rounded lump of moss. */
class Cushion(val x: Float, val w: Float, val h: Float)

/** A hanging bead of moss along the front of a patch. */
class Drip(val x: Float, val w: Float, val len: Float)

/** A continuous stretch of moss on the surface line, thickest in the middle. */
class MossPatch(val x0: Float, val x1: Float, val thick: Float, val drips: List<Drip>)

/** An exposed root arching out of the rock and back in again. */
class Root(val x: Float, val len: Float, val arch: Float, val width: Float)

/** A glowing spore / flower head, or a dark reed pod. [y] is the height above the surface. */
class Speck(val x: Float, val y: Float, val rx: Float, val ry: Float, val kind: Int) {
    companion object {
        const val GLOW = 0
        const val WARM = 1
        const val POD = 2
    }
}

class FloraPlan(
    val blades: List<Blade>,
    val cushions: List<Cushion>,
    val moss: List<MossPatch>,
    val roots: List<Root>,
    val specks: List<Speck>
) {
    val isEmpty: Boolean
        get() = blades.isEmpty() && cushions.isEmpty() && moss.isEmpty() && roots.isEmpty() && specks.isEmpty()
}

/** How one biome grows plants. Distances are world pixels. */
class FloraProfile(
    /** Share of a surface that may carry plants (0..1); the rest stays bare. */
    val coverage: Float,
    /** Noise wavelength: bigger means longer lush and bare stretches. */
    val patch: Float,
    val spacingMin: Float,
    val spacingMax: Float,
    val clumpMin: Int,
    val clumpMax: Int,
    val hMin: Float,
    val hMax: Float,
    val wMin: Float,
    val wMax: Float,
    /** Chance that a clump has one stray tall stalk, and how much taller than [hMax] it can be. */
    val tall: Float,
    val tallScale: Float,
    /** Constant lean in blade heights (wind). */
    val wind: Float,
    val cushions: Float,
    val moss: Float,
    /** Exposed roots per 1000 px of rock surface. */
    val roots: Float,
    val sprouts: Float,
    val flowers: Float,
    val ferns: Float,
    val reeds: Float,
    /** Chance (scaled by height) that a blade tip carries a glowing spore. */
    val spores: Float,
    /** Sway amplitude (skew factor) used by the renderer. */
    val sway: Float
)

object Flora {
    const val SURFACE_ROCK = 0
    const val SURFACE_LEDGE = 1

    /** Padding around hazards inside which no plant is rooted. */
    const val HAZARD_PAD = 24f
    private const val MIN_RUN = 14f
    private val NO_KEEP_OUT = FloatArray(0)

    private val green = FloraProfile(
        coverage = 0.90f, patch = 125f, spacingMin = 7f, spacingMax = 16f, clumpMin = 4, clumpMax = 7,
        hMin = 20f, hMax = 46f, wMin = 2.8f, wMax = 5.0f, tall = 0.28f, tallScale = 1.7f, wind = 0f,
        cushions = 0.9f, moss = 1f, roots = 1.3f, sprouts = 0.25f, flowers = 0.18f, ferns = 0.1f, reeds = 0f,
        spores = 0f, sway = 0.045f
    )
    private val cliffs = FloraProfile(
        coverage = 0.5f, patch = 140f, spacingMin = 14f, spacingMax = 34f, clumpMin = 2, clumpMax = 4,
        hMin = 5f, hMax = 11f, wMin = 1.8f, wMax = 3.2f, tall = 0.12f, tallScale = 1.7f, wind = 0.5f,
        cushions = 0.35f, moss = 0.45f, roots = 0.5f, sprouts = 0.1f, flowers = 0.04f, ferns = 0f, reeds = 0f,
        spores = 0f, sway = 0.06f
    )
    private val fungal = FloraProfile(
        coverage = 0.7f, patch = 100f, spacingMin = 10f, spacingMax = 24f, clumpMin = 3, clumpMax = 5,
        hMin = 6f, hMax = 13f, wMin = 2f, wMax = 3.8f, tall = 0.18f, tallScale = 1.7f, wind = 0f,
        cushions = 0.7f, moss = 0.8f, roots = 1.2f, sprouts = 0.1f, flowers = 0f, ferns = 0f, reeds = 0f,
        spores = 0.6f, sway = 0.04f
    )
    private val waterways = FloraProfile(
        coverage = 0.72f, patch = 120f, spacingMin = 10f, spacingMax = 26f, clumpMin = 2, clumpMax = 5,
        hMin = 6f, hMax = 14f, wMin = 2f, wMax = 3.6f, tall = 0.2f, tallScale = 1.7f, wind = 0f,
        cushions = 0.5f, moss = 0.9f, roots = 0.5f, sprouts = 0.15f, flowers = 0f, ferns = 0.12f, reeds = 0.28f,
        spores = 0f, sway = 0.05f
    )
    private val town = FloraProfile(
        coverage = 0.58f, patch = 105f, spacingMin = 12f, spacingMax = 27f, clumpMin = 3, clumpMax = 5,
        hMin = 16f, hMax = 34f, wMin = 2.4f, wMax = 4.2f, tall = 0.25f, tallScale = 1.6f, wind = 0.2f,
        cushions = 0.25f, moss = 0.25f, roots = 0f, sprouts = 0.15f, flowers = 0.12f, ferns = 0f, reeds = 0f,
        spores = 0f, sway = 0.05f
    )
    private val crossroads = FloraProfile(
        coverage = 0.38f, patch = 110f, spacingMin = 20f, spacingMax = 42f, clumpMin = 2, clumpMax = 3,
        hMin = 6f, hMax = 14f, wMin = 1.8f, wMax = 2.8f, tall = 0.18f, tallScale = 1.6f, wind = 0f,
        cushions = 0.2f, moss = 0.2f, roots = 0.3f, sprouts = 0.12f, flowers = 0.05f, ferns = 0f, reeds = 0f,
        spores = 0f, sway = 0.04f
    )
    private val resting = FloraProfile(
        coverage = 0.38f, patch = 130f, spacingMin = 16f, spacingMax = 36f, clumpMin = 2, clumpMax = 4,
        hMin = 6f, hMax = 14f, wMin = 1.6f, wMax = 2.6f, tall = 0.2f, tallScale = 1.6f, wind = 0.15f,
        cushions = 0f, moss = 0f, roots = 0f, sprouts = 0.1f, flowers = 0.1f, ferns = 0f, reeds = 0f,
        spores = 0f, sway = 0.05f
    )
    private val edge = FloraProfile(
        coverage = 0.2f, patch = 150f, spacingMin = 20f, spacingMax = 46f, clumpMin = 1, clumpMax = 3,
        hMin = 4f, hMax = 9f, wMin = 1.6f, wMax = 2.6f, tall = 0.08f, tallScale = 1.5f, wind = 0.4f,
        cushions = 0f, moss = 0f, roots = 0f, sprouts = 0f, flowers = 0f, ferns = 0f, reeds = 0f,
        spores = 0f, sway = 0.06f
    )
    private val city = FloraProfile(
        coverage = 0.3f, patch = 90f, spacingMin = 20f, spacingMax = 44f, clumpMin = 1, clumpMax = 2,
        hMin = 3f, hMax = 9f, wMin = 1.4f, wMax = 2.6f, tall = 0.05f, tallScale = 1.4f, wind = 0.3f,
        cushions = 0f, moss = 0f, roots = 0f, sprouts = 0.08f, flowers = 0.03f, ferns = 0f, reeds = 0f,
        spores = 0f, sway = 0.05f
    )
    private val crystal = FloraProfile(
        coverage = 0.26f, patch = 100f, spacingMin = 22f, spacingMax = 48f, clumpMin = 1, clumpMax = 2,
        hMin = 2f, hMax = 7f, wMin = 1.2f, wMax = 2.2f, tall = 0.02f, tallScale = 1.3f, wind = 0.2f,
        cushions = 0f, moss = 0f, roots = 0f, sprouts = 0.05f, flowers = 0f, ferns = 0f, reeds = 0f,
        spores = 0f, sway = 0.04f
    )
    private val deepnest = FloraProfile(
        coverage = 0.3f, patch = 95f, spacingMin = 20f, spacingMax = 46f, clumpMin = 1, clumpMax = 2,
        hMin = 3f, hMax = 8f, wMin = 1.4f, wMax = 2.4f, tall = 0.03f, tallScale = 1.4f, wind = 0.2f,
        cushions = 0f, moss = 0f, roots = 0f, sprouts = 0.06f, flowers = 0f, ferns = 0f, reeds = 0f,
        spores = 0f, sway = 0.05f
    )
    private val basin = FloraProfile(
        coverage = 0.3f, patch = 95f, spacingMin = 20f, spacingMax = 46f, clumpMin = 1, clumpMax = 2,
        hMin = 3f, hMax = 8f, wMin = 1.4f, wMax = 2.4f, tall = 0.03f, tallScale = 1.4f, wind = 0.2f,
        cushions = 0f, moss = 0f, roots = 0f, sprouts = 0.06f, flowers = 0f, ferns = 0f, reeds = 0f,
        spores = 0f, sway = 0.05f
    )
    private val abyss = FloraProfile(
        coverage = 0.22f, patch = 110f, spacingMin = 24f, spacingMax = 52f, clumpMin = 1, clumpMax = 1,
        hMin = 2f, hMax = 6f, wMin = 1.2f, wMax = 2.0f, tall = 0f, tallScale = 1f, wind = 0.4f,
        cushions = 0f, moss = 0f, roots = 0f, sprouts = 0f, flowers = 0f, ferns = 0f, reeds = 0f,
        spores = 0f, sway = 0.04f
    )

    /**
     * How [biome] grows plants. Every biome has a profile so the nail always finds grass
     * somewhere; coverage varies from lush (green, fungal, waterways) to sparse (city, crystal,
     * deepnest, basin, abyss), where the sparse profiles still scatter tufts along most
     * platforms instead of leaving them bare.
     */
    fun profile(biome: Biome): FloraProfile? = when (biome) {
        Biome.GREEN -> green
        Biome.CLIFFS -> cliffs
        Biome.FUNGAL -> fungal
        Biome.WATERWAYS -> waterways
        Biome.TOWN -> town
        Biome.CROSSROADS -> crossroads
        Biome.RESTING -> resting
        Biome.EDGE -> edge
        Biome.CITY -> city
        Biome.CRYSTAL -> crystal
        Biome.DEEPNEST -> deepnest
        Biome.BASIN -> basin
        Biome.ABYSS -> abyss
    }

    /** Stable per-platform seed from the area id and the platform's tile position. */
    fun seed(areaId: String, col: Int, row: Int, salt: Int = 0): Int =
        FloraHash.mix(FloraHash.mix(FloraHash.mix(areaId.hashCode(), col), row), salt)

    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /**
     * Lays out the vegetation for one surface between [x0] and [x1] (world px). Offsets are
     * relative to the surface line, which the renderer supplies. [keepOut] holds pairs of
     * (from, to) x-intervals that must stay clear of roots, for hazards sitting on the surface.
     */
    fun plan(
        p: FloraProfile,
        seed: Int,
        x0: Float,
        x1: Float,
        surface: Int = SURFACE_ROCK,
        keepOut: FloatArray = NO_KEEP_OUT
    ): FloraPlan {
        val len = x1 - x0
        val blades = ArrayList<Blade>()
        val cushions = ArrayList<Cushion>()
        val moss = ArrayList<MossPatch>()
        val roots = ArrayList<Root>()
        val specks = ArrayList<Speck>()
        if (len < MIN_RUN) return FloraPlan(blades, cushions, moss, roots, specks)

        val ledge = surface == SURFACE_LEDGE
        val cover = if (ledge) p.coverage * 0.7f else p.coverage
        val scale = if (ledge) 0.62f else 1f
        val noiseSeed = FloraHash.mix(seed, 0x51ED)
        val threshold = 1f - cover

        fun dens(x: Float): Float =
            smooth(threshold - 0.06f, threshold + 0.14f, FloraHash.noise(noiseSeed, (x - x0) / p.patch))

        fun blocked(x: Float, pad: Float): Boolean {
            var i = 0
            while (i + 1 < keepOut.size) {
                if (x > keepOut[i] - pad && x < keepOut[i + 1] + pad) return true
                i += 2
            }
            return false
        }

        val lo = x0 + 1f
        val hi = x1 - 1f

        // ---- clumps, sprouts, flowers, ferns, reeds ------------------------------------
        var x = x0 + 9f + FloraHash.unit(seed, 1) * p.spacingMin
        var idx = 0
        while (x < x1 - 9f) {
            idx++
            val r = FloraRng(FloraHash.mix(seed, 1000 + idx))
            val d = dens(x)
            val gate = r.next()
            val spacing = r.range(p.spacingMin, p.spacingMax)
            if (d > 0.02f && gate < d && !blocked(x, HAZARD_PAD)) {
                clump(blades, specks, p, r, x, d, scale, lo, hi)
                if (r.chance(p.sprouts)) sprout(blades, r, x + r.range(-12f, 12f), scale, lo, hi)
                if (r.chance(p.flowers)) flower(blades, specks, p, r, x + r.range(-12f, 12f), scale, lo, hi)
                if (p.ferns > 0f && d > 0.5f && !ledge && r.chance(p.ferns)) fern(blades, r, x, lo, hi)
                if (p.reeds > 0f && !ledge && r.chance(p.reeds)) reeds(blades, specks, r, x, lo, hi)
            }
            x += spacing
        }

        // ---- moss cushions ---------------------------------------------------------------
        if (p.cushions > 0f) {
            val cr = FloraRng(FloraHash.mix(seed, 31))
            var cx = x0 + 8f
            while (cx < x1 - 8f) {
                val gap = cr.range(11f, 26f)
                val w = cr.range(6f, 15f) * (if (ledge) 0.7f else 1f)
                val h = cr.range(2f, 4.6f) * (if (ledge) 0.7f else 1f)
                val roll = cr.next()
                if (roll < p.cushions * dens(cx) && !blocked(cx, 12f)) cushions.add(Cushion(cx, w, h))
                cx += gap
            }
        }

        // ---- moss patches: runs where the field is dense enough ------------------------
        if (p.moss > 0f) {
            val mr = FloraRng(FloraHash.mix(seed, 53))
            var start = -1f
            var sum = 0f
            var cnt = 0
            var mx = x0 + 4f
            while (mx <= x1 - 4f) {
                val dd = dens(mx)
                val on = dd > 0.2f && !blocked(mx, 10f)
                if (on) {
                    if (start < 0f) {
                        start = mx
                        sum = 0f
                        cnt = 0
                    }
                    sum += dd
                    cnt++
                }
                if (start >= 0f && (!on || mx + 4f > x1 - 4f)) {
                    if (mx - start >= 20f) moss.add(mossPatch(p, mr, start, mx, sum / cnt, ledge))
                    start = -1f
                }
                mx += 4f
            }
        }

        // ---- exposed roots (rock only) ---------------------------------------------------
        if (p.roots > 0f && !ledge) {
            val rr = FloraRng(FloraHash.mix(seed, 77))
            val expected = len / 1000f * p.roots
            var n = expected.toInt()
            if (rr.next() < expected - n) n++
            for (i in 0 until n) {
                val rl = rr.range(20f, 46f)
                if (len < rl + 24f) continue
                val rx = x0 + 12f + rr.next() * (len - rl - 24f)
                val arch = rr.range(4.5f, 10f) * (0.6f + 0.4f * min(1f, rl / 46f))
                val w = rr.range(1.8f, 3f)
                if (blocked(rx, 12f) || blocked(rx + rl, 12f)) continue
                roots.add(Root(rx, rl, arch, w))
            }
        }
        return FloraPlan(blades, cushions, moss, roots, specks)
    }

    private fun clump(
        out: ArrayList<Blade>, specks: ArrayList<Speck>, p: FloraProfile, r: FloraRng,
        x: Float, d: Float, scale: Float, lo: Float, hi: Float
    ) {
        val n = max(1, (r.range(p.clumpMin.toFloat(), p.clumpMax + 0.99f) * (0.55f + 0.45f * d)).toInt())
        val centerH = (p.hMin + (p.hMax - p.hMin) * r.next().pow(2.2f)) * scale
        val spread = r.range(2.5f, 4f + n * 1.6f)
        for (i in 0 until n) {
            val fan = if (n == 1) 0f else (i / (n - 1f)) * 2f - 1f
            val shape = 1f - 0.42f * fan * fan
            val h = max(2.5f, centerH * shape * r.range(0.72f, 1.14f))
            val bx = (x + fan * spread + r.range(-1.6f, 1.6f)).coerceIn(lo, hi)
            val lean = fan * h * 0.5f + p.wind * h * 0.35f + r.range(-0.18f, 0.18f) * h
            val bend = lean * 0.4f + r.range(-0.16f, 0.16f) * h
            val w = (p.wMin + (p.wMax - p.wMin) * min(1f, h / p.hMax) * r.range(0.7f, 1.15f)) * (0.8f + 0.2f * scale)
            val layer = if (abs(fan) < 0.35f) (if (r.chance(0.55f)) 2 else 1) else (if (r.chance(0.45f)) 0 else 1)
            out.add(Blade(bx, h, w, lean, bend, layer))
            if (p.spores > 0f && r.chance(p.spores * min(1f, h / p.hMax))) {
                specks.add(Speck(bx + lean, h * 0.98f, r.range(1.1f, 1.9f), r.range(1.1f, 1.9f), Speck.WARM))
            }
        }
        if (r.chance(p.tall)) {
            val h = p.hMax * r.range(1f, p.tallScale) * scale
            val lean = r.range(-0.3f, 0.3f) * h + p.wind * h * 0.5f
            out.add(Blade((x + r.range(-spread, spread)).coerceIn(lo, hi), h, p.wMin * 0.9f, lean, lean * 0.5f + r.range(-0.2f, 0.2f) * h, 1))
        }
        if (d > 0.35f) {
            val u = r.int(1, 3)
            for (i in 0 until u) {
                val h = r.range(3f, 6.5f) * scale
                val lean = r.range(-0.35f, 0.35f) * h
                out.add(Blade((x + r.range(-10f, 10f)).coerceIn(lo, hi), h, r.range(2.2f, 3.2f), lean, lean * 0.4f, 0))
            }
        }
    }

    /** A tiny two-leaf seedling. */
    private fun sprout(out: ArrayList<Blade>, r: FloraRng, x: Float, scale: Float, lo: Float, hi: Float) {
        val bx = x.coerceIn(lo, hi)
        val h = r.range(6f, 11f) * scale
        val lean = r.range(-0.25f, 0.25f) * h
        out.add(Blade(bx, h, 1.4f, lean, lean * 0.5f, 1))
        val leaf = r.range(3.5f, 6f) * scale
        val at = h * r.range(0.45f, 0.7f)
        out.add(Blade(bx + lean * 0.5f, leaf * 1.25f, 2.6f, -leaf, -leaf * 0.5f, 1, at))
        out.add(Blade(bx + lean * 0.5f, leaf * 1.25f, 2.6f, leaf, leaf * 0.5f, 1, at))
    }

    /** A thin stem topped with a small glowing bloom. */
    private fun flower(
        out: ArrayList<Blade>, specks: ArrayList<Speck>, p: FloraProfile, r: FloraRng,
        x: Float, scale: Float, lo: Float, hi: Float
    ) {
        val bx = x.coerceIn(lo, hi)
        val h = r.range(8f, 15f) * scale
        val lean = r.range(-0.3f, 0.3f) * h + p.wind * h * 0.3f
        out.add(Blade(bx, h, 1.3f, lean, lean * 0.6f, 1))
        specks.add(Speck(bx + lean, h, r.range(1.3f, 2.1f), r.range(1.3f, 2.1f), if (r.chance(0.3f)) Speck.WARM else Speck.GLOW))
    }

    /** A fan of broad, strongly curved fronds. */
    private fun fern(out: ArrayList<Blade>, r: FloraRng, x: Float, lo: Float, hi: Float) {
        val n = r.int(4, 6)
        val h0 = r.range(12f, 21f)
        for (i in 0 until n) {
            val fan = (i / (n - 1f)) * 2f - 1f
            val h = h0 * (1f - 0.35f * fan * fan) * r.range(0.85f, 1.1f)
            val lean = fan * h * 0.85f
            out.add(Blade((x + fan * 6f).coerceIn(lo, hi), h, r.range(3.4f, 5.2f), lean, lean * 0.2f + h * 0.18f * (if (fan < 0f) -1f else 1f), 1))
        }
    }

    /** Tall thin water reeds, sometimes with a dark seed pod. */
    private fun reeds(out: ArrayList<Blade>, specks: ArrayList<Speck>, r: FloraRng, x: Float, lo: Float, hi: Float) {
        val n = r.int(1, 3)
        for (i in 0 until n) {
            val h = r.range(20f, 34f)
            val lean = r.range(-0.1f, 0.1f) * h
            val bx = (x + r.range(-11f, 11f)).coerceIn(lo, hi)
            out.add(Blade(bx, h, r.range(1.5f, 2.2f), lean, lean * 0.7f, if (r.chance(0.5f)) 2 else 1))
            if (r.chance(0.4f)) specks.add(Speck(bx + lean, h * 0.9f, 1.6f, 4.2f, Speck.POD))
        }
    }

    private fun mossPatch(p: FloraProfile, r: FloraRng, start: Float, end: Float, avg: Float, ledge: Boolean): MossPatch {
        val thick = (2.2f + 3.4f * avg) * p.moss * (if (ledge) 0.6f else 1f)
        val drips = ArrayList<Drip>()
        val len = end - start
        if (len > 36f) {
            var count = (len / 80f).toInt()
            if (r.chance(0.4f)) count++
            for (i in 0 until count) {
                val dx = start + 8f + r.next() * (len - 16f)
                drips.add(Drip(dx, r.range(2.4f, 3.6f) * (if (ledge) 0.7f else 1f), r.range(3.5f, 9f) * (if (ledge) 0.7f else 1f)))
            }
        }
        return MossPatch(start, end, thick, drips)
    }
}
