package com.devdgames.hollowknightbutmine.game.env

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.devdgames.hollowknightbutmine.game.EnemyType

/** A doorway out of a room. Walking into [rect] moves the player to another room. */
class RoomExit(
    val rect: RectF,
    val targetRoomId: String,
    val targetX: Float,
    val targetY: Float
)

/** Where a room wants enemies to stand when it is loaded. */
class EnemySpawn(
    val x: Float,
    val y: Float,
    val boss: Boolean = false,
    val type: EnemyType? = null
)

/** A point of interest authored in room-local coordinates (bench, soul cache). */
class MapPoint(val x: Float, val y: Float)

/**
 * One biome of the map.
 *
 * A room owns its collision geometry ([platforms], [hazards]), its points of
 * interest ([benches], [soulCaches]) and everything you see. Drawing is split
 * into backdrop (screen space, parallaxed in layers), world (camera space,
 * behind the actor layer, culled against [view]) and front (screen space,
 * over everything) so the GameWorld can slot the player and enemies between
 * them.
 *
 * All drawing happens in *room-local* coordinates: the GameWorld passes a
 * local camera (world camera minus the room's world offset) and clips each
 * biome's backdrop to its own stretch of the screen, so neighbouring biomes
 * never paint over each other.
 *
 * Jump metrics the layouts are authored against (see Player):
 * single-jump height ≈ 116px, comfortable rise ≤ 90px, flat gap ≤ ~190px.
 * Main routes only ever need a single jump; double-jump / dash routes are
 * reserved for soul caches and shortcuts. `tools/verify_map.py` checks this.
 */
abstract class Room(
    val id: String,
    val title: String,
    val bounds: RectF,
    val subtitle: String = ""
) {
    val platforms = ArrayList<RectF>()
    val hazards = ArrayList<RectF>()
    val exits = ArrayList<RoomExit>()
    val enemySpawns = ArrayList<EnemySpawn>()
    val slabs = ArrayList<Slab>()
    val benches = ArrayList<MapPoint>()
    val soulCaches = ArrayList<MapPoint>()

    var spawnX = bounds.left + 140f
    var spawnY = bounds.bottom - 60f

    // Biome atmosphere. GameWorld blends these across the seams.
    open val skyTop: Int = EnvPalette.voidTop
    open val skyMid: Int = EnvPalette.voidMid
    open val skyBottom: Int = EnvPalette.voidBottom
    open val fogColor: Int = EnvPalette.mossFog

    abstract fun build()
    abstract fun drawBackdrop(canvas: Canvas, paint: Paint, camX: Float, camY: Float, viewW: Float, viewH: Float, t: Float)

    /** [view] is the visible rect in room-local coordinates (already padded). */
    abstract fun drawWorld(canvas: Canvas, paint: Paint, t: Float, view: RectF)
    abstract fun drawFront(canvas: Canvas, paint: Paint, camX: Float, camY: Float, viewW: Float, viewH: Float, t: Float)

    // ---------------------------------------------------------------- build helpers

    protected fun solid(l: Float, t: Float, r: Float, b: Float): RectF =
        RectF(l, t, r, b).also { platforms.add(it) }

    protected fun hazard(l: Float, t: Float, r: Float, b: Float): RectF =
        RectF(l, t, r, b).also { hazards.add(it) }

    protected fun exit(l: Float, t: Float, r: Float, b: Float, room: String, tx: Float, ty: Float) {
        exits.add(RoomExit(RectF(l, t, r, b), room, tx, ty))
    }

    /** A bench: resting here restores masks and becomes the death respawn. (x, y = floor level) */
    protected fun bench(x: Float, y: Float) {
        benches.add(MapPoint(x, y))
    }

    /** A soul cache: touch it for +33 Soul. (x, y = floor level it hovers over) */
    protected fun cache(x: Float, y: Float) {
        soulCaches.add(MapPoint(x, y))
    }

    protected fun addSlab(
        rect: RectF,
        seed: Int,
        mossy: Boolean = true,
        body: Int = EnvPalette.rockBody,
        bottom: Int = EnvPalette.rockShadow,
        band: Int = EnvPalette.mossDeep,
        tuft: Int = EnvPalette.mossLit,
        rim: Int = EnvPalette.rockRim,
        style: SlabStyle = SlabStyle.ROCK
    ): Slab {
        platforms.add(rect)
        return Slab(rect, seed, mossy, body, bottom, band, tuft, rim, style).also { slabs.add(it) }
    }

    /** Cut-stone slab preset (bridge decks, sanctuary masonry). */
    protected fun addCutStone(rect: RectF, seed: Int, mossy: Boolean = true): Slab =
        addSlab(
            rect, seed, mossy,
            body = EnvPalette.cutStoneBody, bottom = EnvPalette.cutStoneShadow,
            rim = EnvPalette.cutStoneRim, style = SlabStyle.CUT_STONE
        )

    /** Fossil-slab preset (ossuary bone platforms). */
    protected fun addBone(rect: RectF, seed: Int): Slab =
        addSlab(
            rect, seed, mossy = false,
            body = EnvPalette.boneBody, bottom = EnvPalette.boneShadow,
            rim = EnvPalette.boneLit, style = SlabStyle.BONE
        )

    // ---------------------------------------------------------------- draw helpers

    /**
     * Begins a parallax layer whose art is authored in full room-local
     * coordinates. The layer is horizontally compressed so the *whole*
     * room's backdrop is traversed as the camera crosses the room:
     *
     *   screenX = (x - bounds.left) * sx - camU * factor
     *   sx = (factor * span + (1 - factor) * viewW) / span
     *
     * factor < 1 = background (0.12 far … 0.55 near), factor > 1 = foreground
     * that sweeps past the camera. Always pair with [endLayer].
     *
     * Returns the horizontal compression (sx) so callers can un-squash
     * circular glows: draw them with rx / sx.
     */
    protected fun beginLayer(canvas: Canvas, factor: Float, camX: Float, camY: Float, viewW: Float): Float {
        val span = bounds.width().coerceAtLeast(1f)
        val sx = (factor * span + (1f - factor) * viewW) / span
        canvas.save()
        val tx = -factor * camX - (sx - factor) * bounds.left
        val ty = -camY * factor * 0.55f
        canvas.translate(tx, ty)
        canvas.scale(sx, 1f)
        return sx
    }

    protected fun endLayer(canvas: Canvas) {
        canvas.restore()
    }

    protected fun drawSlabs(canvas: Canvas, paint: Paint, t: Float, view: RectF) {
        for (s in slabs) if (s.isVisible(view)) s.draw(canvas, paint, t)
    }

    /** True when a room-local point is near the visible window. */
    protected fun nearView(view: RectF, x: Float, pad: Float = 320f): Boolean =
        x > view.left - pad && x < view.right + pad

    /** True when a room-local horizontal span overlaps the visible window. */
    protected fun spanVisible(view: RectF, l: Float, r: Float, pad: Float = 160f): Boolean =
        r > view.left - pad && l < view.right + pad

    /** Animated swaying grass along every visible mossy slab top. */
    protected fun drawSlabGrass(canvas: Canvas, paint: Paint, t: Float, view: RectF) {
        val wind = kotlin.math.sin(t * 1.8f) * 9f
        for (s in slabs) {
            if (!s.mossy || !s.isVisible(view)) continue
            val top = s.rect.top
            var gx = s.rect.left + 10f
            var k = 0
            while (gx < s.rect.right - 8f && k < 90) {
                if (gx > view.left - 20f && gx < view.right + 20f) {
                    EnvArt.drawGrassPatch(canvas, paint, gx, top, wind + kotlin.math.sin(gx * 0.013f + t) * 2.5f)
                }
                gx += 34f
                k++
            }
        }
    }

    /** Anti-aliased wavy horizontal line (ripples, rails, hanging tendrils). */
    protected fun wavyLine(
        path: Path,
        left: Float,
        right: Float,
        y: Float,
        amp: Float,
        period: Float,
        phase: Float
    ) {
        path.reset()
        var x = left
        path.moveTo(x, y + (kotlin.math.sin(x / period + phase) * amp))
        while (x < right) {
            x += 14f
            path.lineTo(x, y + (kotlin.math.sin(x / period + phase) * amp))
        }
    }
}
