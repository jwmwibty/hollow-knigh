package com.devdgames.hollowknightbutmine.game.env

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader

/** A doorway out of a room. Walking into [rect] moves the player to another room. */
class RoomExit(
    val rect: RectF,
    val targetRoomId: String,
    val targetX: Float,
    val targetY: Float
)

/** Where a room wants enemies to stand when it is loaded. */
class EnemySpawn(val x: Float, val y: Float, val boss: Boolean = false)

/**
 * One screen-sized area of the map.
 *
 * A room owns three things: its collision geometry ([platforms], [hazards]),
 * its connections ([exits]) and everything you see. Drawing is split into
 * backdrop (screen space, parallaxed), world (camera space, behind the actor
 * layer) and front (screen space, over everything) so the GameWorld can slot
 * the player and enemies in between them.
 */
abstract class Room(
    val id: String,
    val title: String,
    val bounds: RectF
) {
    val platforms = ArrayList<RectF>()
    val hazards = ArrayList<RectF>()
    val exits = ArrayList<RoomExit>()
    val enemySpawns = ArrayList<EnemySpawn>()
    val slabs = ArrayList<Slab>()

    var spawnX = bounds.left + 140f
    var spawnY = bounds.bottom - 60f

    private var bgShader: LinearGradient? = null
    private var bgW = -1f
    private var bgH = -1f
    private var bgTop = 0
    private var bgBottom = 0

    abstract fun build()
    abstract fun drawBackdrop(canvas: Canvas, paint: Paint, camX: Float, camY: Float, viewW: Float, viewH: Float, t: Float)
    abstract fun drawWorld(canvas: Canvas, paint: Paint, t: Float)
    abstract fun drawFront(canvas: Canvas, paint: Paint, camX: Float, camY: Float, viewW: Float, viewH: Float, t: Float)

    // ---------------------------------------------------------------- build helpers

    protected fun solid(l: Float, t: Float, r: Float, b: Float): RectF =
        RectF(l, t, r, b).also { platforms.add(it) }

    protected fun hazard(l: Float, t: Float, r: Float, b: Float): RectF =
        RectF(l, t, r, b).also { hazards.add(it) }

    protected fun exit(l: Float, t: Float, r: Float, b: Float, room: String, tx: Float, ty: Float) {
        exits.add(RoomExit(RectF(l, t, r, b), room, tx, ty))
    }

    protected fun addSlab(
        rect: RectF,
        seed: Int,
        mossy: Boolean = true,
        body: Int = EnvPalette.rockBody,
        bottom: Int = EnvPalette.rockShadow,
        band: Int = EnvPalette.mossDeep,
        tuft: Int = EnvPalette.mossLit,
        rim: Int = EnvPalette.rockRim
    ): Slab {
        platforms.add(rect)
        return Slab(rect, seed, mossy, body, bottom, band, tuft, rim).also { slabs.add(it) }
    }

    // ---------------------------------------------------------------- draw helpers

    /** Flat vertical wash covering the whole view. Cached per size + colours. */
    protected fun fillVoid(canvas: Canvas, paint: Paint, viewW: Float, viewH: Float, top: Int, bottom: Int) {
        if (bgShader == null || bgW != viewW || bgH != viewH || bgTop != top || bgBottom != bottom) {
            bgShader = LinearGradient(0f, 0f, 0f, viewH, top, bottom, Shader.TileMode.CLAMP)
            bgW = viewW
            bgH = viewH
            bgTop = top
            bgBottom = bottom
        }
        paint.style = Paint.Style.FILL
        paint.shader = bgShader
        paint.alpha = 255
        canvas.drawRect(0f, 0f, viewW, viewH, paint)
        paint.shader = null
    }

    protected fun drawSlabs(canvas: Canvas, paint: Paint, camX: Float, camY: Float, viewW: Float, viewH: Float) {
        for (s in slabs) if (s.isVisible(camX, camY, viewW, viewH)) s.draw(canvas, paint)
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
