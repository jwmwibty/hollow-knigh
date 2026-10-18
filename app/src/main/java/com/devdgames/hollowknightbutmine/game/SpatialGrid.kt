package com.devdgames.hollowknightbutmine.game

import android.graphics.RectF
import kotlin.math.floor

/**
 * Uniform grid over the world's collision rectangles. The world has a few thousand
 * solids; physics only ever needs the handful near an actor, so every query walks the
 * cells under a rectangle and returns the de-duplicated set of rects they contain.
 *
 * Queries reuse one result list per [SpatialGrid] instance; callers must consume the
 * result before issuing the next query on the same grid.
 */
class SpatialGrid(private val cell: Float = 320f) {
    private val cells = HashMap<Long, ArrayList<RectF>>()
    private val result = ArrayList<RectF>(64)
    private val stamp = HashMap<RectF, Int>()
    private var queryId = 0
    val all = ArrayList<RectF>()

    private fun key(cx: Int, cy: Int): Long = (cx.toLong() shl 32) xor (cy.toLong() and 0xffffffffL)

    fun add(r: RectF) {
        all.add(r)
        val x0 = floor(r.left / cell).toInt()
        val x1 = floor((r.right - 0.01f) / cell).toInt()
        val y0 = floor(r.top / cell).toInt()
        val y1 = floor((r.bottom - 0.01f) / cell).toInt()
        for (cy in y0..y1) for (cx in x0..x1) {
            cells.getOrPut(key(cx, cy)) { ArrayList(8) }.add(r)
        }
    }

    fun addAll(list: Collection<RectF>) { for (r in list) add(r) }

    fun query(left: Float, top: Float, right: Float, bottom: Float): List<RectF> {
        result.clear()
        queryId++
        val x0 = floor(left / cell).toInt()
        val x1 = floor(right / cell).toInt()
        val y0 = floor(top / cell).toInt()
        val y1 = floor(bottom / cell).toInt()
        for (cy in y0..y1) for (cx in x0..x1) {
            val bucket = cells[key(cx, cy)] ?: continue
            for (r in bucket) {
                if (stamp[r] == queryId) continue
                stamp[r] = queryId
                result.add(r)
            }
        }
        return result
    }

    fun query(r: RectF, pad: Float): List<RectF> = query(r.left - pad, r.top - pad, r.right + pad, r.bottom + pad)

    /** Convenience: first rect containing the point, or null. */
    fun rectAt(x: Float, y: Float): RectF? {
        val bucket = cells[key(floor(x / cell).toInt(), floor(y / cell).toInt())] ?: return null
        for (r in bucket) if (r.contains(x, y)) return r
        return null
    }
}
