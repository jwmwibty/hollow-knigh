package com.devdgames.hollowknightbutmine.game.puzzle

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.sin

/**
 * The puzzles, drawn.
 *
 * Every puzzle carries its rule into the world: a lintel over the stones, a tally scratched
 * into a pipe, a line cut into the shelf. Nothing a player has to open a menu to learn, and
 * nothing that reads the same twice. Allocation-free, culled, and it leaves the [Paint] as it
 * found it.
 */
object PuzzleArt {

    private val scratch = Path()
    private val box = RectF()

    /** Half the width of the whole installation, so culling does not clip its edges. */
    private const val MARGIN = 78f
    private const val HEADROOM = 150f

    fun draw(canvas: Canvas, paint: Paint, puzzle: Puzzle, time: Float, view: RectF, highlight: Boolean) {
        val right = puzzle.cellX(puzzle.cells - 1)
        if (view.right < puzzle.x - MARGIN || view.left > right + MARGIN) return
        if (view.bottom < puzzle.floorY - HEADROOM || view.top > puzzle.floorY + 12f) return
        when (puzzle) {
            is RuneSealPuzzle -> runeSeal(canvas, paint, puzzle, time, highlight)
            is LitanyPuzzle -> litany(canvas, paint, puzzle, time, highlight)
            is BeaconPuzzle -> beacon(canvas, paint, puzzle, time, highlight)
            is SluicePuzzle -> sluice(canvas, paint, puzzle, time, highlight)
        }
    }

    // ===================================================================== helpers

    private fun fill(paint: Paint, color: Int) {
        paint.style = Paint.Style.FILL
        paint.color = color
    }

    private fun stroke(paint: Paint, color: Int, width: Float) {
        paint.style = Paint.Style.STROKE
        paint.color = color
        paint.strokeWidth = width
    }

    private fun accentAt(accent: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(accent), Color.green(accent), Color.blue(accent))

    /** Words cut into stone: small caps with a shadow, the way the rest of the kingdom writes. */
    private fun carved(canvas: Canvas, paint: Paint, text: String, cx: Float, cy: Float, size: Float, ink: Int) {
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.SERIF
        paint.textSize = size
        paint.letterSpacing = 0.12f
        paint.color = Color.argb(120, 4, 6, 10)
        canvas.drawText(text, cx + 1f, cy + 1f, paint)
        paint.color = ink
        canvas.drawText(text, cx, cy, paint)
        paint.letterSpacing = 0f
        paint.textAlign = Paint.Align.LEFT
        paint.typeface = Typeface.DEFAULT
    }

    /** The ground ring that says "this can be used", drawn under whatever it belongs to. */
    private fun promptRing(canvas: Canvas, paint: Paint, cx: Float, ground: Float, time: Float, accent: Int) {
        val pulse = 0.6f + 0.4f * sin(time * 4.2f)
        stroke(paint, accentAt(accent, (120 * pulse).toInt()), 1.8f)
        box.set(cx - 15f, ground - 5f, cx + 15f, ground + 5f)
        canvas.drawOval(box, paint)
        paint.style = Paint.Style.FILL
    }

    /** A stone that has been standing here a very long time. */
    private fun standingStone(canvas: Canvas, paint: Paint, cx: Float, ground: Float, w: Float, h: Float, body: Int, cap: Int) {
        fill(paint, body)
        scratch.reset()
        scratch.moveTo(cx - w, ground)
        scratch.lineTo(cx - w * 0.82f, ground - h)
        scratch.quadTo(cx, ground - h - 5f, cx + w * 0.82f, ground - h)
        scratch.lineTo(cx + w, ground)
        scratch.close()
        canvas.drawPath(scratch, paint)
        fill(paint, cap)
        canvas.drawRect(cx - w, ground - 3f, cx + w, ground + 2f, paint)
    }

    // ===================================================================== THE SEALED DOOR

    private fun runeSeal(canvas: Canvas, paint: Paint, p: RuneSealPuzzle, time: Float, highlight: Boolean) {
        val accent = p.spec.accent
        val ground = p.floorY
        val centre = p.cellX(1)
        val open = p.solved

        // The door itself: an arch in the town wall that no one remembers building.
        fill(paint, Color.rgb(30, 32, 40))
        box.set(centre - 40f, ground - 112f, centre + 40f, ground)
        canvas.drawRoundRect(box, 26f, 26f, paint)
        fill(paint, if (open) Color.rgb(9, 12, 18) else Color.rgb(22, 24, 31))
        box.set(centre - 32f, ground - 104f, centre + 32f, ground)
        canvas.drawRoundRect(box, 21f, 21f, paint)
        if (open) {
            // Something below is breathing: a slow light, from a long way down.
            val breath = 0.55f + 0.45f * sin(time * 1.4f)
            fill(paint, accentAt(accent, (46 * breath).toInt()))
            box.set(centre - 30f, ground - 70f, centre + 30f, ground)
            canvas.drawRoundRect(box, 14f, 14f, paint)
        } else {
            // The seam of a door that has never been opened, and three bolts of stone across it.
            stroke(paint, Color.argb(120, 78, 84, 98), 1.6f)
            canvas.drawLine(centre, ground - 104f, centre, ground - 4f, paint)
            fill(paint, Color.rgb(48, 51, 62))
            canvas.drawRoundRect(centre - 34f, ground - 84f, centre + 34f, ground - 74f, 3f, 3f, paint)
            canvas.drawRoundRect(centre - 34f, ground - 52f, centre + 34f, ground - 42f, 3f, 3f, paint)
        }
        stroke(paint, Color.argb(if (open) 190 else 110, 120, 132, 150), 2f)
        box.set(centre - 40f, ground - 112f, centre + 40f, ground)
        canvas.drawRoundRect(box, 26f, 26f, paint)

        // The lintel carries the rule, so the puzzle can be read before it is tried.
        fill(paint, Color.rgb(44, 47, 57))
        canvas.drawRoundRect(centre - 74f, ground - 136f, centre + 74f, ground - 112f, 4f, 4f, paint)
        stroke(paint, Color.argb(90, 96, 104, 122), 1.2f)
        canvas.drawLine(centre - 70f, ground - 116f, centre + 70f, ground - 116f, paint)
        carved(canvas, paint, p.spec.clue, centre, ground - 121f, 9f, Color.argb(215, 224, 204, 157))

        // Three stones, each with the notches that decide its turn.
        for (i in 0 until p.cells) {
            val x = p.cellX(i)
            val lit = open || p.lit[i]
            val pulse = if (lit) sin(time * 3f + i) * 1.4f else 0f
            if (lit) {
                fill(paint, accentAt(accent, 44))
                canvas.drawCircle(x, ground - 20f, 22f + pulse, paint)
            }
            standingStone(
                canvas, paint, x, ground, 15f, 34f,
                if (lit) Color.rgb(71, 116, 112) else Color.rgb(44, 48, 59),
                Color.argb(120, 5, 8, 14)
            )
            fill(paint, if (lit) accentAt(accent, 255) else Color.rgb(106, 127, 150))
            canvas.drawCircle(x, ground - 20f, 6f + pulse, paint)
            // The notches: the count is the answer, and the answer is on the stone.
            stroke(paint, if (lit) accentAt(accent, 225) else Color.argb(190, 150, 162, 180), 1.6f)
            val n = p.notchesOn(i)
            for (k in 0 until n) {
                val ny = ground - 31f + k * 4f
                canvas.drawLine(x - 6f, ny, x + 6f, ny, paint)
            }
            if (highlight) promptRing(canvas, paint, x, ground, time, accent)
        }

        if (open) {
            stroke(paint, accentAt(accent, (150 + sin(time * 4f) * 60f).toInt()), 3f)
            canvas.drawRoundRect(p.x - 30f, ground - 105f, p.cellX(p.cells - 1) + 30f, ground - 2f, 10f, 10f, paint)
        }
        paint.style = Paint.Style.FILL
    }

    // ===================================================================== THE LITANY OF GRAVES

    private fun litany(canvas: Canvas, paint: Paint, p: LitanyPuzzle, time: Float, highlight: Boolean) {
        val accent = p.spec.accent
        val ground = p.floorY
        val centre = p.cellX(2)

        // A low slab behind the row, with the rite cut into it.
        fill(paint, Color.rgb(40, 40, 52))
        canvas.drawRoundRect(centre - 84f, ground - 62f, centre + 84f, ground - 34f, 5f, 5f, paint)
        stroke(paint, Color.argb(80, 92, 92, 112), 1.2f)
        canvas.drawRoundRect(centre - 84f, ground - 62f, centre + 84f, ground - 34f, 5f, 5f, paint)
        carved(canvas, paint, p.spec.clue, centre, ground - 44f, 9f, Color.argb(205, 214, 200, 232))

        // The chain that runs between the lights: they are one rite, not five.
        stroke(paint, Color.argb(120, 88, 88, 104), 1.6f)
        canvas.drawLine(p.x, ground - 9f, p.cellX(p.cells - 1), ground - 9f, paint)

        for (i in 0 until p.cells) {
            val x = p.cellX(i)
            val lit = p.lit[i] || p.solved
            val flicker = if (lit) 0.75f + 0.25f * sin(time * 6f + i * 1.7f) else 0f
            if (lit) {
                fill(paint, accentAt(accent, (40 * flicker).toInt()))
                canvas.drawCircle(x, ground - 20f, 17f + 3f * flicker, paint)
            }
            standingStone(
                canvas, paint, x, ground, 9f, 20f,
                if (lit) Color.rgb(66, 62, 84) else Color.rgb(44, 44, 58),
                Color.argb(110, 4, 6, 10)
            )
            // The flame, or the place where one should be.
            if (lit) {
                fill(paint, accentAt(accent, (225 * flicker).toInt()))
                scratch.reset()
                scratch.moveTo(x, ground - 34f - 5f * flicker)
                scratch.quadTo(x + 4.5f, ground - 26f, x, ground - 21f)
                scratch.quadTo(x - 4.5f, ground - 26f, x, ground - 34f - 5f * flicker)
                scratch.close()
                canvas.drawPath(scratch, paint)
                fill(paint, Color.argb((230 * flicker).toInt(), 255, 250, 232))
                canvas.drawCircle(x, ground - 24f, 1.6f, paint)
            } else {
                fill(paint, Color.rgb(28, 28, 38))
                canvas.drawCircle(x, ground - 24f, 2.4f, paint)
            }
            if (highlight) promptRing(canvas, paint, x, ground, time, accent)
        }

        if (p.solved) {
            // The row is even: an arc of light settles over all five and stays there.
            stroke(paint, accentAt(accent, (130 + sin(time * 2.4f) * 50f).toInt()), 2.4f)
            box.set(p.x - 22f, ground - 66f, p.cellX(p.cells - 1) + 22f, ground + 18f)
            canvas.drawArc(box, 180f, 180f, false, paint)
        }
        paint.style = Paint.Style.FILL
    }

    // ===================================================================== THE FOUR FACETS

    private fun beacon(canvas: Canvas, paint: Paint, p: BeaconPuzzle, time: Float, highlight: Boolean) {
        val accent = p.spec.accent
        val ground = p.floorY

        // The rule, cut into the rock at the near end of the hall.
        carved(canvas, paint, p.spec.clue, p.x - 6f, ground - 96f, 10f, Color.argb(190, 236, 210, 236))
        stroke(paint, Color.argb(70, 210, 170, 210), 1.2f)
        canvas.drawLine(p.x - 62f, ground - 90f, p.x + 50f, ground - 90f, paint)

        // When all four hold, the hall runs a beam between them.
        if (p.solved) {
            val glow = 0.6f + 0.4f * sin(time * 2.2f)
            stroke(paint, accentAt(accent, (110 * glow).toInt()), 7f)
            canvas.drawLine(p.x, ground - 56f, p.cellX(p.cells - 1), ground - 56f, paint)
            stroke(paint, accentAt(Color.rgb(255, 244, 252), (200 * glow).toInt()), 2f)
            canvas.drawLine(p.x, ground - 56f, p.cellX(p.cells - 1), ground - 56f, paint)
        }

        for (i in 0 until p.cells) {
            val x = p.cellX(i)
            val flame = p.flame(i)
            val burning = flame > 0.02f || p.solved

            // Pedestal and bowl: pink stone, cut to catch whatever is put in it.
            fill(paint, Color.rgb(62, 44, 66))
            scratch.reset()
            scratch.moveTo(x - 13f, ground)
            scratch.lineTo(x - 8f, ground - 34f)
            scratch.lineTo(x + 8f, ground - 34f)
            scratch.lineTo(x + 13f, ground)
            scratch.close()
            canvas.drawPath(scratch, paint)
            stroke(paint, Color.argb(120, 140, 100, 146), 1.4f)
            canvas.drawLine(x - 10f, ground - 12f, x + 10f, ground - 12f, paint)
            fill(paint, Color.rgb(84, 60, 88))
            box.set(x - 12f, ground - 44f, x + 12f, ground - 32f)
            canvas.drawRoundRect(box, 4f, 4f, paint)

            // Facets on the bowl's rim, and the fire they are holding.
            fill(paint, if (burning) accentAt(accent, 235) else Color.rgb(96, 78, 104))
            for (k in 0..2) {
                val fx = x - 8f + k * 8f
                scratch.reset()
                scratch.moveTo(fx, ground - 52f); scratch.lineTo(fx + 4f, ground - 44f); scratch.lineTo(fx - 4f, ground - 44f)
                scratch.close()
                canvas.drawPath(scratch, paint)
            }
            if (burning) {
                val h = if (p.solved) 1f else flame
                val fh = 16f + 26f * h
                fill(paint, accentAt(accent, (54 * h).toInt()))
                canvas.drawCircle(x, ground - 52f, 24f + 8f * h, paint)
                fill(paint, accentAt(accent, (200 + 55 * h).toInt().coerceAtMost(255)))
                scratch.reset()
                scratch.moveTo(x, ground - 46f - fh)
                scratch.quadTo(x + 9f * h + 4f, ground - 52f, x, ground - 44f)
                scratch.quadTo(x - 9f * h - 4f, ground - 52f, x, ground - 46f - fh)
                scratch.close()
                canvas.drawPath(scratch, paint)
                fill(paint, Color.argb((235 * h).toInt().coerceIn(0, 255), 255, 246, 252))
                canvas.drawCircle(x, ground - 50f - fh * 0.28f, 2.6f * h + 1f, paint)
                // Sparks off the top, so the timer can be felt rather than read.
                if (!p.solved) {
                    for (k in 0..1) {
                        val w = (time * 1.6f + i * 0.31f + k * 0.5f) % 1f
                        fill(paint, accentAt(accent, (130 * (1f - w)).toInt()))
                        canvas.drawCircle(x + cos(w * 9f + i) * 7f, ground - 56f - fh - w * 20f, 1.5f, paint)
                    }
                }
            }
            if (highlight) promptRing(canvas, paint, x, ground, time, accent)
        }
        paint.style = Paint.Style.FILL
    }

    // ===================================================================== THE SLUICE GATE

    private fun sluice(canvas: Canvas, paint: Paint, p: SluicePuzzle, time: Float, highlight: Boolean) {
        val accent = p.spec.accent
        val ground = p.floorY
        val left = p.x - 46f
        val right = p.cellX(p.cells - 1) + 46f

        // The gate behind everything: a slab of drowned masonry, and the seam it opens on.
        fill(paint, Color.rgb(34, 44, 42))
        canvas.drawRoundRect(left, ground - 108f, right, ground, 6f, 6f, paint)
        for (k in 0..2) {
            stroke(paint, Color.argb(70, 62, 78, 74), 1.2f)
            canvas.drawLine(left + 4f, ground - 90f + k * 30f, right - 4f, ground - 90f + k * 30f, paint)
        }
        if (p.solved) {
            val glow = 0.6f + 0.4f * sin(time * 1.7f)
            fill(paint, accentAt(accent, (44 * glow).toInt()))
            canvas.drawRect((left + right) * 0.5f - 22f, ground - 104f, (left + right) * 0.5f + 22f, ground - 2f, paint)
            fill(paint, accentAt(Color.rgb(226, 255, 246), (150 * glow).toInt()))
            canvas.drawRect((left + right) * 0.5f - 3f, ground - 104f, (left + right) * 0.5f + 3f, ground - 2f, paint)
        } else {
            stroke(paint, Color.argb(120, 74, 92, 88), 2f)
            canvas.drawLine((left + right) * 0.5f, ground - 104f, (left + right) * 0.5f, ground - 2f, paint)
        }

        // The pipe the wheels hang off.
        fill(paint, Color.rgb(52, 62, 60))
        canvas.drawRoundRect(left - 10f, ground - 84f, right + 10f, ground - 64f, 9f, 9f, paint)
        fill(paint, Color.argb(70, 120, 150, 144))
        canvas.drawRoundRect(left - 10f, ground - 84f, right + 10f, ground - 78f, 4f, 4f, paint)
        for (i in 0 until p.cells) {
            fill(paint, Color.rgb(70, 82, 78))
            canvas.drawCircle(p.cellX(i), ground - 74f, 3f, paint)
        }

        // The tally: how many wheels should run, scratched by somebody who did not trust paper.
        carved(canvas, paint, p.spec.clue, (left + right) * 0.5f, ground - 94f, 9f, Color.argb(200, 196, 222, 212))
        val tallyX = (left + right) * 0.5f - 26f
        stroke(paint, Color.argb(170, 170, 196, 186), 1.6f)
        for (k in 0..2) {
            canvas.drawLine(tallyX + k * 7f, ground - 116f, tallyX + k * 7f + 2f, ground - 106f, paint)
        }
        canvas.drawLine(tallyX - 3f, ground - 109f, tallyX + 18f, ground - 113f, paint)

        // The wheels themselves.
        for (i in 0 until p.cells) {
            val x = p.cellX(i)
            val open = p.open[i] || p.solved
            val y = ground - 40f
            stroke(paint, Color.rgb(88, 96, 92), 3f)
            canvas.drawLine(x, ground - 64f, x, y - 11f, paint)

            val spin = if (open) time * 1.6f + i else 0f
            stroke(paint, if (open) accentAt(accent, 235) else Color.argb(220, 108, 118, 114), 2.6f)
            canvas.drawCircle(x, y, 11f, paint)
            paint.strokeWidth = 1.8f
            for (k in 0..3) {
                val a = spin + k * (Math.PI / 2.0).toFloat()
                canvas.drawLine(x + cos(a) * 3f, y + sin(a) * 3f, x + cos(a) * 11f, y + sin(a) * 11f, paint)
            }
            fill(paint, if (open) accentAt(accent, 255) else Color.rgb(70, 78, 76))
            canvas.drawCircle(x, y, 3f, paint)

            // Water through an open wheel, and the puddle it is making.
            if (open) {
                fill(paint, Color.argb(60, 150, 210, 196))
                canvas.drawOval(x - 15f, ground - 5f, x + 15f, ground + 3f, paint)
                for (k in 0..2) {
                    val w = (time * 1.9f + k * 0.33f + i * 0.17f) % 1f
                    fill(paint, Color.argb((200 * (1f - w)).toInt().coerceIn(0, 255), 170, 224, 210))
                    canvas.drawOval(x - 1.6f, y + 11f + w * (ground - y - 14f), x + 1.6f, y + 16f + w * (ground - y - 14f), paint)
                }
            }
            if (highlight) promptRing(canvas, paint, x, ground, time, accent)
        }
        paint.style = Paint.Style.FILL
        paint.strokeWidth = 1f
    }
}
