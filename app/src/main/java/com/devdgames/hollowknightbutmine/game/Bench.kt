package com.devdgames.hollowknightbutmine.game

import android.graphics.Canvas
import android.graphics.Paint
import com.devdgames.hollowknightbutmine.game.env.Props

/**
 * A resting bench. Sitting (standing still on it for a moment) heals the player, sets
 * the respawn point and repopulates the world's regular enemies — exactly the rhythm
 * Hallownest is built around: push out, find the bench, breathe, push further.
 */
class Bench(val areaId: String, val x: Float, val y: Float) {
    var discovered = false
    var isCurrent = false
    var restTimer = 0f
    var resting = false

    fun playerOnBench(px: Float, py: Float): Boolean =
        kotlin.math.abs(px - x) < 40f && kotlin.math.abs(py - y) < 6f

    fun draw(canvas: Canvas, paint: Paint, t: Float) {
        Props.bench(canvas, paint, x, y, lit = isCurrent || resting, t = t)
    }
}
