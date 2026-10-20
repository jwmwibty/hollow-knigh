package com.devdgames.hollowknightbutmine.game.env

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.devdgames.hollowknightbutmine.game.world.Biome
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Silhouette family for a biome's ground cover. The colours do the heavy lifting;
 * the style keeps a pale ash biome from sprouting the same soft tufts as a moss cave.
 */
enum class GrassStyle {
    TUFT,   // ordinary soft clump
    REED,   // tall, thin, with seed heads
    FERN,   // wide and arching, with bright spores on the tips
    DRY,    // short, stiff, straw-like
    SHARD   // angular crystal blades
}

/** Which bloom shader a patch's tips should use, if any. */
enum class GrassGlow { NONE, TEAL, WARM, ROSE, PALE, VOID }

/**
 * The grass identity of one biome: its three blade tones, the colour of cut flecks,
 * an optional glow, and how lush the ground cover is (0..1).
 */
class GrassPalette(
    val deep: Int,
    val mid: Int,
    val lit: Int,
    val fleck: Int,
    val glow: Int,
    val glowKind: GrassGlow,
    val style: GrassStyle,
    val density: Float,
    val scale: Float = 1f
)

/**
 * Biome-matched ground cover.
 *
 * Grass is not a gameplay obstacle: it sways in the wind, bends when the player wades
 * through it, and is sheared away by the nail. Cut patches leave stubble and grow back
 * after a while (resting at a bench restores them immediately).
 */
object Greenery {
    private fun c(r: Int, g: Int, b: Int) = Color.rgb(r, g, b)

    /** Dustmere: dry straw that has grown through the ash. Warm, low, grey-gold. */
    private val town = GrassPalette(
        deep = c(54, 49, 41), mid = c(112, 103, 84), lit = c(178, 167, 134),
        fleck = c(220, 208, 168), glow = 0, glowKind = GrassGlow.NONE,
        style = GrassStyle.DRY, density = 0.66f, scale = 0.92f
    )

    /** The cliffs: wind-flattened blue-green sedge. */
    private val cliffs = GrassPalette(
        deep = c(36, 52, 50), mid = c(82, 112, 100), lit = c(142, 176, 150),
        fleck = c(202, 226, 196), glow = 0, glowKind = GrassGlow.NONE,
        style = GrassStyle.TUFT, density = 0.46f
    )

    /** Blue-grey cavern moss: pale, almost lichen. */
    private val crossroads = GrassPalette(
        deep = c(42, 53, 66), mid = c(84, 105, 120), lit = c(140, 164, 176),
        fleck = c(194, 214, 218), glow = 0, glowKind = GrassGlow.NONE,
        style = GrassStyle.TUFT, density = 0.36f
    )

    /** The Verdant Path: lush, arching, bioluminescent. */
    private val green = GrassPalette(
        deep = c(17, 62, 40), mid = c(46, 126, 72), lit = c(124, 196, 108),
        fleck = c(202, 240, 166), glow = c(150, 232, 206), glowKind = GrassGlow.TEAL,
        style = GrassStyle.FERN, density = 0.9f, scale = 1.08f
    )

    /** Fungal Hollows: warm rust-red fronds with glowing spores. */
    private val fungal = GrassPalette(
        deep = c(70, 38, 42), mid = c(140, 76, 54), lit = c(204, 138, 78),
        fleck = c(246, 202, 132), glow = c(255, 178, 92), glowKind = GrassGlow.WARM,
        style = GrassStyle.FERN, density = 0.58f, scale = 1.05f
    )

    /** City of Rain: rain-pale blue reeds in the gutters. */
    private val city = GrassPalette(
        deep = c(30, 48, 70), mid = c(62, 96, 128), lit = c(112, 154, 184),
        fleck = c(174, 208, 226), glow = 0, glowKind = GrassGlow.NONE,
        style = GrassStyle.REED, density = 0.24f
    )

    /** Glimmer Peak: crystal blades, lit from inside. */
    private val crystal = GrassPalette(
        deep = c(62, 32, 74), mid = c(122, 62, 132), lit = c(200, 120, 208),
        fleck = c(246, 200, 246), glow = c(255, 150, 230), glowKind = GrassGlow.ROSE,
        style = GrassStyle.SHARD, density = 0.32f
    )

    /** Resting Grounds: muted lavender crypt grass. */
    private val resting = GrassPalette(
        deep = c(54, 50, 76), mid = c(100, 94, 132), lit = c(158, 150, 184),
        fleck = c(216, 208, 238), glow = c(214, 222, 255), glowKind = GrassGlow.PALE,
        style = GrassStyle.TUFT, density = 0.36f
    )

    /**
     * The Ashen Edge: bleached white grass. This is the biome the pale palette is
     * built around - silver stems with bone-white tips over grey ash.
     */
    private val edge = GrassPalette(
        deep = c(70, 70, 76), mid = c(136, 136, 142), lit = c(204, 204, 208),
        fleck = c(242, 242, 240), glow = 0, glowKind = GrassGlow.NONE,
        style = GrassStyle.DRY, density = 0.62f, scale = 1.05f
    )

    /** Old Waterways: murky green reed beds. */
    private val waterways = GrassPalette(
        deep = c(32, 66, 38), mid = c(68, 118, 62), lit = c(132, 166, 96),
        fleck = c(196, 222, 152), glow = 0, glowKind = GrassGlow.NONE,
        style = GrassStyle.REED, density = 0.6f
    )

    /** Silk Deep: nearly black threads with a sickly violet sheen. */
    private val deepnest = GrassPalette(
        deep = c(36, 27, 38), mid = c(66, 52, 70), lit = c(108, 88, 110),
        fleck = c(170, 148, 172), glow = c(255, 140, 70), glowKind = GrassGlow.WARM,
        style = GrassStyle.TUFT, density = 0.2f, scale = 0.85f
    )

    /** Ancient Basin: bone-pale sprouts among the ribs. */
    private val basin = GrassPalette(
        deep = c(52, 58, 64), mid = c(100, 110, 112), lit = c(160, 170, 166),
        fleck = c(222, 224, 212), glow = c(170, 200, 230), glowKind = GrassGlow.PALE,
        style = GrassStyle.TUFT, density = 0.3f
    )

    /** The Abyss: pale, ghostly fronds that catch the last of the light. */
    private val abyss = GrassPalette(
        deep = c(22, 22, 34), mid = c(50, 50, 74), lit = c(104, 106, 140),
        fleck = c(176, 180, 214), glow = c(200, 210, 230), glowKind = GrassGlow.VOID,
        style = GrassStyle.SHARD, density = 0.16f, scale = 0.9f
    )

    fun palette(biome: Biome): GrassPalette = when (biome) {
        Biome.TOWN -> town
        Biome.CLIFFS -> cliffs
        Biome.CROSSROADS -> crossroads
        Biome.GREEN -> green
        Biome.FUNGAL -> fungal
        Biome.CITY -> city
        Biome.CRYSTAL -> crystal
        Biome.RESTING -> resting
        Biome.EDGE -> edge
        Biome.WATERWAYS -> waterways
        Biome.DEEPNEST -> deepnest
        Biome.BASIN -> basin
        Biome.ABYSS -> abyss
    }
}

/**
 * One clump of breakable grass.
 *
 * All blade contours are built once at construction; drawing is a handful of path
 * fills per visible clump, rotated as a whole for wind and rustle. Cut grass keeps a
 * short stubble and grows back after [regrowAfter] seconds.
 */
class GrassPatch(
    val x: Float,
    val baseY: Float,
    val width: Float,
    val maxHeight: Float,
    val deepColor: Int,
    val midColor: Int,
    val litColor: Int,
    val fleckColor: Int,
    private val glowColor: Int,
    private val glowKind: GrassGlow,
    private val style: GrassStyle,
    seed: Int
) {
    val bounds = RectF()

    private val back = Path()
    private val main = Path()
    private val lit = Path()
    private val tips = Path()
    private val stubble = Path()

    private val phase: Float
    private val regrowAfter: Float
    private val glows: Boolean

    var isCut = false
        private set
    private var regrowTimer = 0f
    private var growTimer = 0f

    /** 0..1, how hard the player is pushing this clump aside. */
    var rustle = 0f
        private set
    private var rustleDir = 1f

    init {
        val rnd = Random(seed)
        phase = rnd.nextFloat() * 6.2832f
        regrowAfter = 22f + rnd.nextFloat() * 24f
        glows = rnd.nextFloat() < 0.6f
        bounds.set(
            x - width * 0.95f - 18f, baseY - maxHeight - 12f,
            x + width * 0.95f + 18f, baseY + 8f
        )
        buildBlades(rnd)
    }

    private fun buildBlades(rnd: Random) {
        val count = when (style) {
            GrassStyle.REED -> 6 + rnd.nextInt(4)
            GrassStyle.FERN -> 8 + rnd.nextInt(4)
            GrassStyle.SHARD -> 4 + rnd.nextInt(3)
            GrassStyle.DRY -> 5 + rnd.nextInt(3)
            else -> 6 + rnd.nextInt(4)
        }

        // Shadow blades: broader and shorter, sitting behind the lit ones.
        val backCount = if (style == GrassStyle.SHARD) 1 else 3
        for (i in 0 until backCount) {
            val bx = (rnd.nextFloat() - 0.5f) * width * 0.8f
            val h = maxHeight * (0.34f + rnd.nextFloat() * 0.3f)
            val lean = bx / width * 10f + (rnd.nextFloat() - 0.5f) * 6f
            EnvArt.tapered(
                back, bx, 0f, bx + lean * 0.4f, -h * 0.55f, bx + lean, -h,
                if (style == GrassStyle.SHARD) 3.4f else 4.4f, 0.4f, 4
            )
        }

        for (i in 0 until count) {
            val spread = if (count == 1) 0f else (i / (count - 1f) - 0.5f)
            val bx = spread * width * 0.8f + (rnd.nextFloat() - 0.5f) * 3f
            val centre = 1f - abs(spread) * 1.25f
            val h = maxHeight * (0.52f + 0.48f * centre) * (0.86f + rnd.nextFloat() * 0.28f)
            val outward = when (style) {
                GrassStyle.FERN -> spread * width * 0.6f
                GrassStyle.DRY -> spread * width * 0.34f
                GrassStyle.REED -> spread * width * 0.14f
                else -> spread * width * 0.32f
            }
            val jitter = (rnd.nextFloat() - 0.5f) * 7f
            val tipX = bx + outward + jitter
            val tipY = -h
            val cx = bx + outward * 0.35f + (rnd.nextFloat() - 0.5f) * 4f
            val cy = -h * 0.58f
            val baseW = when (style) {
                GrassStyle.REED -> 2.1f + rnd.nextFloat() * 1.0f
                GrassStyle.FERN -> 4.2f + rnd.nextFloat() * 1.8f
                GrassStyle.SHARD -> 4.4f + rnd.nextFloat() * 1.9f
                else -> 2.9f + rnd.nextFloat() * 1.5f
            }

            if (style == GrassStyle.SHARD) {
                // Straight faceted blades, like splinters of crystal.
                main.moveTo(bx - baseW * 0.5f, 0f)
                main.lineTo(tipX, tipY)
                main.lineTo(bx + baseW * 0.5f, 0f)
                main.close()
                if (i % 2 == 0) {
                    lit.moveTo(bx - baseW * 0.16f, 0f)
                    lit.lineTo(tipX, tipY)
                    lit.lineTo(bx + baseW * 0.16f, 0f)
                    lit.close()
                }
            } else {
                EnvArt.tapered(main, bx, 0f, cx, cy, tipX, tipY, baseW, 0.55f, 6)
                if (i % 2 == 0) {
                    // Lit outer third of the blade catches the biome's key light.
                    val mx = 0.25f * bx + 0.5f * cx + 0.25f * tipX
                    val my = 0.5f * cy + 0.25f * tipY
                    EnvArt.tapered(lit, mx, my, cx, cy, tipX, tipY, baseW * 0.58f, 0.45f, 4)
                }
            }

            if (style == GrassStyle.FERN && i % 3 == 0) {
                tips.addCircle(tipX, tipY - 1.5f, 2.1f, Path.Direction.CW)
            }
            if (style == GrassStyle.REED && i % 4 == 1) {
                tips.addOval(RectF(tipX - 2.1f, tipY - 8f, tipX + 2.1f, tipY + 1f), Path.Direction.CW)
            }
            // Dew / spore points on the plain and straw tufts: they carry the
            // biome's glow in caves and a pale catch-light on the surface.
            if ((style == GrassStyle.TUFT || style == GrassStyle.DRY) && i % 4 == 1) {
                tips.addCircle(tipX, tipY - 1f, 1.35f, Path.Direction.CW)
            }
        }

        for (i in 0 until 4 + rnd.nextInt(3)) {
            val bx = (rnd.nextFloat() - 0.5f) * width * 0.7f
            val h = 4f + rnd.nextFloat() * 5f
            EnvArt.tapered(
                stubble, bx, 0f, bx, -h * 0.5f, bx + (rnd.nextFloat() - 0.5f) * 4f, -h,
                2.4f, 0.4f, 3
            )
        }
    }

    fun update(dt: Float) {
        if (rustle > 0f) rustle = (rustle - dt * 2.6f).coerceAtLeast(0f)
        if (growTimer > 0f) growTimer = (growTimer - dt).coerceAtLeast(0f)
        if (isCut) {
            regrowTimer -= dt
            if (regrowTimer <= 0f) {
                isCut = false
                growTimer = GROW_TIME
            }
        }
    }

    /** Bend away from something passing through the clump. */
    fun disturb(fromX: Float, strength: Float) {
        if (isCut) return
        if (strength > rustle) rustle = strength.coerceIn(0f, 1f)
        rustleDir = if (fromX < x) 1f else -1f
    }

    /** Shear the clump. Returns false when it was already cut. */
    fun cut(): Boolean {
        if (isCut) return false
        isCut = true
        regrowTimer = regrowAfter
        rustle = 0f
        return true
    }

    fun restore() {
        if (isCut) {
            isCut = false
            growTimer = GROW_TIME * 0.7f
        }
        rustle = 0f
    }

    private fun shaderFor(kind: GrassGlow): android.graphics.Shader = when (kind) {
        GrassGlow.WARM -> EnvArt.glowWarm
        GrassGlow.ROSE -> EnvArt.glowRose
        GrassGlow.PALE -> EnvArt.glowPale
        GrassGlow.VOID -> EnvArt.glowVoid
        else -> EnvArt.glowTeal
    }

    fun draw(canvas: Canvas, paint: Paint, t: Float) {
        val sway = sin(t * 1.3f + phase) * 1.5f + sin(t * 2.7f + phase * 1.7f) * 0.65f
        val angle = sway + rustleDir * rustle * 11f
        val pulse = 0.55f + 0.45f * sin(t * 2.2f + phase)

        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.strokeCap = Paint.Cap.BUTT

        if (!isCut && glows && glowKind != GrassGlow.NONE) {
            EnvArt.bloom(
                canvas, paint, x, baseY - maxHeight * 0.45f,
                width * 0.95f, maxHeight * 0.6f, shaderFor(glowKind),
                (20 + 26f * pulse).toInt()
            )
        }

        canvas.save()
        canvas.translate(x, baseY)
        canvas.rotate(angle)

        if (isCut) {
            paint.color = deepColor
            paint.alpha = 175
            canvas.drawPath(stubble, paint)
        } else {
            val grow = if (growTimer > 0f) (1f - growTimer / GROW_TIME).coerceIn(0.08f, 1f) else 1f
            if (grow < 1f) canvas.scale(1f, grow)

            paint.color = deepColor
            paint.alpha = 235
            canvas.drawPath(back, paint)
            paint.color = midColor
            paint.alpha = 255
            canvas.drawPath(main, paint)
            paint.color = litColor
            paint.alpha = 220
            canvas.drawPath(lit, paint)

            if (!tips.isEmpty) {
                paint.color = if (glowKind != GrassGlow.NONE && glowColor != 0) glowColor else fleckColor
                paint.alpha = if (glowKind != GrassGlow.NONE) {
                    (150 + 100f * pulse).toInt().coerceIn(0, 255)
                } else {
                    215
                }
                canvas.drawPath(tips, paint)
            }
        }

        canvas.restore()
        paint.alpha = 255
    }

    companion object {
        const val GROW_TIME = 1.1f
    }
}
