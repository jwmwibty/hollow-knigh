package com.devdgames.hollowknightbutmine

import com.devdgames.hollowknightbutmine.game.world.Biome
import com.devdgames.hollowknightbutmine.game.world.Flora
import com.devdgames.hollowknightbutmine.game.world.FloraHash
import com.devdgames.hollowknightbutmine.game.world.FloraPlan
import com.devdgames.hollowknightbutmine.game.world.WorldAtlas
import com.devdgames.hollowknightbutmine.game.world.WorldLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Vegetation is generated from stable seeds, so these tests pin the properties that make it
 * look natural (patchy, varied, irregular) and safe (deterministic, clear of hazards, absent
 * from biomes whose material should stay bare). They run on the JVM: the generator has no
 * Android dependencies.
 */
class FloraTest {
    private val green = Flora.profile(Biome.GREEN)!!
    private val runLength = 2400f

    private fun signature(plan: FloraPlan): String {
        val sb = StringBuilder()
        for (b in plan.blades) sb.append(b.x.toRawBits()).append(',').append(b.h.toRawBits()).append(',')
            .append(b.w.toRawBits()).append(',').append(b.lean.toRawBits()).append(',')
            .append(b.bend.toRawBits()).append(',').append(b.layer).append(',').append(b.base.toRawBits()).append(';')
        for (c in plan.cushions) sb.append('c').append(c.x.toRawBits()).append(',').append(c.w.toRawBits()).append(';')
        for (m in plan.moss) sb.append('m').append(m.x0.toRawBits()).append(',').append(m.x1.toRawBits()).append(',').append(m.drips.size).append(';')
        for (r in plan.roots) sb.append('r').append(r.x.toRawBits()).append(',').append(r.len.toRawBits()).append(';')
        for (s in plan.specks) sb.append('s').append(s.x.toRawBits()).append(',').append(s.kind).append(';')
        return sb.toString()
    }

    private fun cv(values: List<Float>): Float {
        if (values.size < 2) return 0f
        val mean = values.sum() / values.size
        var v = 0f
        for (x in values) v += (x - mean) * (x - mean)
        return sqrt(v / values.size) / mean
    }

    private fun plans(biome: Biome, count: Int = 40, surface: Int = Flora.SURFACE_ROCK, len: Float = runLength): List<FloraPlan> {
        val p = Flora.profile(biome)!!
        return (1..count).map { Flora.plan(p, Flora.seed("t$it", it, it * 3), 0f, len, surface) }
    }

    private fun bladesPer1000(list: List<FloraPlan>, len: Float = runLength): Float =
        list.sumOf { it.blades.size } / (list.size * len) * 1000f

    @Test
    fun `hash and seeds are pinned so existing worlds keep their plants`() {
        assertEquals(-1270735941, FloraHash.mix(1, 2))
        assertEquals(1856745814, FloraHash.mix(12345, -7))
        assertEquals(0.9063752f, FloraHash.unit(99, 5), 1e-6f)
        assertEquals(1284930415, Flora.seed("greenpath", 3, 4))
    }

    @Test
    fun `hash output is uniform in range and noise is smooth`() {
        var min = 1f
        var max = 0f
        for (i in 0 until 2000) {
            val u = FloraHash.unit(7, i)
            assertTrue("unit out of range: $u", u >= 0f && u < 1f)
            min = minOf(min, u)
            max = maxOf(max, u)
        }
        assertTrue("hash does not cover the range", min < 0.02f && max > 0.98f)
        var prev = FloraHash.noise(3, 0f)
        var x = 0.05f
        while (x < 20f) {
            val n = FloraHash.noise(3, x)
            assertTrue("noise out of range: $n", n >= 0f && n <= 1f)
            assertTrue("noise jumps between samples: $prev -> $n", kotlin.math.abs(n - prev) < 0.25f)
            prev = n
            x += 0.05f
        }
    }

    @Test
    fun `same inputs give the same plan regardless of what was generated in between`() {
        val first = signature(Flora.plan(green, 111, 0f, 900f))
        Flora.plan(green, 222, 0f, 5000f)
        Flora.plan(Flora.profile(Biome.WATERWAYS)!!, 111, 0f, 900f)
        val second = signature(Flora.plan(green, 111, 0f, 900f))
        assertEquals(first, second)
        assertNotEquals(first, signature(Flora.plan(green, 112, 0f, 900f)))
        assertTrue("plan should not be empty", first.isNotEmpty())
    }

    @Test
    fun `every platform in the kingdom has its own seed`() {
        val layout = WorldLayout(WorldAtlas.areas)
        val seen = HashSet<Int>()
        var platforms = 0
        for (g in layout.grids) {
            for (run in g.tops) {
                platforms++
                assertTrue("duplicate seed for ${g.def.id} ${run.col},${run.row}", seen.add(Flora.seed(g.def.id, run.col, run.row)))
            }
            for (l in g.ledges) {
                platforms++
                assertTrue("duplicate seed for ledge ${g.def.id} ${l.col},${l.row}", seen.add(Flora.seed(g.def.id, l.col, l.row, 17)))
            }
        }
        assertTrue("expected many platforms, got $platforms", platforms > 100)
    }

    @Test
    fun `lush surfaces are patchy rather than a continuous fringe`() {
        val list = plans(Biome.GREEN)
        var covSum = 0f
        var longestBare = 0
        for (plan in list) {
            val bins = (runLength / 16f).toInt()
            val occupied = BooleanArray(bins)
            for (b in plan.blades) occupied[(b.x / 16f).toInt().coerceIn(0, bins - 1)] = true
            val cov = occupied.count { it }.toFloat() / bins
            covSum += cov
            assertTrue("a run is wall-to-wall vegetation (coverage $cov)", cov < 0.995f)
            var run = 0
            for (o in occupied) {
                if (!o) { run++; longestBare = maxOf(longestBare, run) } else run = 0
            }
        }
        val avg = covSum / list.size
        assertTrue("lush biome should be mostly covered, was $avg", avg > 0.6f && avg < 0.97f)
        assertTrue("expected bare stretches of stone, longest was ${longestBare * 16}px", longestBare * 16 >= 128)
    }

    @Test
    fun `windswept cliffs are much barer than the lush caves`() {
        val cliffs = plans(Biome.CLIFFS)
        val lush = plans(Biome.GREEN)
        assertTrue("cliffs ${bladesPer1000(cliffs)} vs green ${bladesPer1000(lush)}", bladesPer1000(cliffs) * 2.5f < bladesPer1000(lush))
        assertTrue("cliffs should still have some plants", bladesPer1000(cliffs) > 20f)
    }

    @Test
    fun `blades vary in height width lean and layer`() {
        val blades = plans(Biome.GREEN).flatMap { it.blades }
        assertTrue(blades.size > 1000)
        assertTrue("heights too uniform: cv=${cv(blades.map { it.h })}", cv(blades.map { it.h }) > 0.35f)
        assertTrue("widths too uniform: cv=${cv(blades.map { it.w })}", cv(blades.map { it.w }) > 0.15f)
        assertTrue("needs both short ground cover and tall blades", blades.minOf { it.h } < 5f && blades.maxOf { it.h } > green.hMax * 1.2f)
        assertTrue("blades should lean both ways", blades.count { it.lean > 0.5f } > 100 && blades.count { it.lean < -0.5f } > 100)
        assertEquals(setOf(0, 1, 2), blades.map { it.layer }.toSet())
    }

    @Test
    fun `clump spacing is irregular, not a picket fence`() {
        var total = 0f
        var n = 0
        for (plan in plans(Biome.GREEN)) {
            val xs = plan.blades.map { it.x }.sorted()
            if (xs.size < 10) continue
            total += cv(xs.zipWithNext { a, b -> b - a + 0.01f })
            n++
        }
        assertTrue(n > 20)
        assertTrue("gaps between blades are too regular: cv=${total / n}", total / n > 1.0f)
    }

    @Test
    fun `plants stay on their surface with sensible sizes`() {
        for (biome in Biome.values()) {
            val profile = Flora.profile(biome) ?: continue
            for (seed in 1..25) {
                val x0 = 100f
                val x1 = 100f + 300f + seed * 40f
                val plan = Flora.plan(profile, seed * 977, x0, x1)
                for (b in plan.blades) {
                    assertTrue("$biome blade x ${b.x} outside run", b.x >= x0 && b.x <= x1)
                    assertTrue("$biome blade height ${b.h}", b.h > 2f && b.h < 40f)
                    assertTrue("$biome blade width ${b.w}", b.w > 0.8f && b.w < 6.5f)
                    assertTrue("$biome blade layer ${b.layer}", b.layer in 0..2)
                    assertTrue("$biome blade base ${b.base}", b.base >= 0f && b.base < 15f)
                }
                for (c in plan.cushions) assertTrue("$biome cushion", c.x >= x0 && c.x <= x1 && c.w > 0f && c.h > 0f && c.h < 6f)
                for (m in plan.moss) assertTrue("$biome moss ${m.x0}..${m.x1}", m.x0 >= x0 && m.x1 <= x1 && m.x1 > m.x0 && m.thick > 0f && m.thick < 8f)
                for (r in plan.roots) assertTrue("$biome root", r.x >= x0 && r.x + r.len <= x1 && r.arch > 0f && r.width > 0f)
                for (s in plan.specks) assertTrue("$biome speck x ${s.x}", s.x >= x0 - 12f && s.x <= x1 + 12f && s.y > 0f)
            }
        }
    }

    @Test
    fun `plants keep clear of hazards`() {
        val keepOut = floatArrayOf(400f, 480f, 1100f, 1140f)
        for (biome in listOf(Biome.GREEN, Biome.WATERWAYS, Biome.FUNGAL, Biome.CLIFFS)) {
            val profile = Flora.profile(biome)!!
            var count = 0
            for (seed in 1..40) {
                val plan = Flora.plan(profile, seed * 31, 0f, 1600f, Flora.SURFACE_ROCK, keepOut)
                count += plan.blades.size
                var i = 0
                while (i < keepOut.size) {
                    val a = keepOut[i]
                    val b = keepOut[i + 1]
                    for (bl in plan.blades) assertTrue("$biome blade at ${bl.x} touches hazard $a..$b", bl.x <= a - 8f || bl.x >= b + 8f)
                    for (c in plan.cushions) assertTrue("$biome cushion at ${c.x}", c.x + c.w / 2 <= a - 4f || c.x - c.w / 2 >= b + 4f)
                    for (m in plan.moss) assertTrue("$biome moss ${m.x0}..${m.x1}", m.x1 <= a - 5f || m.x0 >= b + 5f)
                    for (r in plan.roots) assertTrue("$biome root at ${r.x}", r.x + r.len <= a - 10f || r.x >= b + 10f)
                    i += 2
                }
            }
            assertTrue("$biome grew nothing at all", count > 200)
        }
    }

    @Test
    fun `a surface that is entirely hazard grows nothing`() {
        val plan = Flora.plan(green, 5, 0f, 400f, Flora.SURFACE_ROCK, floatArrayOf(-10f, 410f))
        assertTrue(plan.blades.isEmpty())
        assertTrue(plan.moss.isEmpty())
        assertTrue(plan.cushions.isEmpty())
        assertTrue(plan.roots.isEmpty())
    }

    @Test
    fun `stone metal crystal and bone surfaces stay bare`() {
        for (b in listOf(Biome.CITY, Biome.CRYSTAL, Biome.DEEPNEST, Biome.BASIN, Biome.ABYSS)) {
            assertNull("$b should not grow plants", Flora.profile(b))
        }
        for (b in listOf(Biome.GREEN, Biome.CLIFFS, Biome.FUNGAL, Biome.WATERWAYS)) {
            assertNotNull("$b should grow plants", Flora.profile(b))
        }
        val vegetated = Biome.values().count { Flora.profile(it) != null }
        assertTrue("too many biomes are vegetated: $vegetated of ${Biome.values().size}", vegetated <= 8)
    }

    @Test
    fun `sparse biomes stay subtle while lush ones are dense`() {
        val lush = bladesPer1000(plans(Biome.GREEN))
        for (b in listOf(Biome.TOWN, Biome.CROSSROADS, Biome.EDGE)) {
            val sparse = bladesPer1000(plans(b))
            assertTrue("$b is not subtle: $sparse blades per 1000px", sparse < 45f)
            assertTrue("$b is not even slightly planted", sparse > 3f)
            assertTrue("$b ($sparse) should be far sparser than green ($lush)", sparse * 5f < lush)
        }
        assertTrue(bladesPer1000(plans(Biome.RESTING)) < 100f)
    }

    @Test
    fun `ledges carry less and smaller growth than rock tops`() {
        val rock = plans(Biome.GREEN, surface = Flora.SURFACE_ROCK)
        val ledge = plans(Biome.GREEN, surface = Flora.SURFACE_LEDGE)
        assertTrue(bladesPer1000(ledge) < bladesPer1000(rock))
        val rockH = rock.flatMap { it.blades }.map { it.h }.average()
        val ledgeH = ledge.flatMap { it.blades }.map { it.h }.average()
        assertTrue("ledge blades ($ledgeH) should be shorter than rock blades ($rockH)", ledgeH < rockH)
        assertTrue("ledges get no exposed roots", ledge.all { it.roots.isEmpty() })
    }

    @Test
    fun `tiny and degenerate runs are safe`() {
        assertTrue(Flora.plan(green, 1, 0f, 10f).isEmpty)
        assertTrue(Flora.plan(green, 1, 50f, 20f).isEmpty)
        val huge = Flora.plan(green, 1, 0f, 60000f)
        assertTrue("density must stay bounded on long runs: ${huge.blades.size} blades", huge.blades.size < 60000 * 0.6f)
        assertFalse(huge.blades.isEmpty())
    }

    @Test
    fun `every profile is internally consistent and sways gently`() {
        for (biome in Biome.values()) {
            val p = Flora.profile(biome) ?: continue
            assertTrue("$biome coverage", p.coverage > 0f && p.coverage <= 1f)
            assertTrue("$biome spacing", p.spacingMin > 0f && p.spacingMax > p.spacingMin)
            assertTrue("$biome height", p.hMin > 0f && p.hMax > p.hMin)
            assertTrue("$biome width", p.wMin > 0f && p.wMax > p.wMin)
            assertTrue("$biome clump", p.clumpMin >= 1 && p.clumpMax >= p.clumpMin)
            assertTrue("$biome tall", p.tall in 0f..1f && p.tallScale >= 1f)
            assertTrue("$biome sway ${p.sway}", p.sway > 0f && p.sway <= 0.1f)
            assertTrue("$biome patch", p.patch >= 60f)
        }
    }
}
