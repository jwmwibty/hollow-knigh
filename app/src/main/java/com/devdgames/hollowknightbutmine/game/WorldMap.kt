package com.devdgames.hollowknightbutmine.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.devdgames.hollowknightbutmine.game.env.Biomes
import com.devdgames.hollowknightbutmine.game.world.WorldLayout
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The in-game map: a hand-inked chart of every area the player has set foot in, drawn
 * from the actual air space of the tile grids so corridors, shafts and chambers appear
 * exactly where they are. Undiscovered areas stay blank, like an unbought map.
 */
class WorldMap(private val layout: WorldLayout) {
    private class AreaChart(val id: String, val name: String, val ink: Int, val fill: Int, val path: Path, val bounds: RectF)

    private val charts = ArrayList<AreaChart>()
    private val serif: Typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
    private val serifBold: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    private val worldCols = layout.maxCol - layout.minCol
    private val worldRows = layout.maxRow - layout.minRow
    private val tmp = RectF()

    init {
        for (g in layout.grids) {
            val p = Path()
            // Surface areas chart only the ground level, not the whole sky.
            val minRow = if (g.def.openSky && g.def.biome == com.devdgames.hollowknightbutmine.game.world.Biome.TOWN) g.row0 + g.height - 9 else g.row0
            for (r in g.airRuns) {
                if (r.row < minRow) continue
                p.addRect(r.col.toFloat(), r.row.toFloat(), r.right.toFloat(), r.bottom.toFloat(), Path.Direction.CW)
            }
            // the solid ledges count as floor, not wall
            for (l in g.ledges) p.addRect(l.col.toFloat(), l.row.toFloat(), l.right.toFloat(), l.bottom.toFloat(), Path.Direction.CW)
            val pal = Biomes.palette(g.def.biome)
            val fill = Color.argb(255,
                (Color.red(pal.mapInk) * 0.35f + 14).toInt().coerceAtMost(255),
                (Color.green(pal.mapInk) * 0.35f + 18).toInt().coerceAtMost(255),
                (Color.blue(pal.mapInk) * 0.35f + 30).toInt().coerceAtMost(255))
            charts.add(AreaChart(g.def.id, g.def.name, pal.mapInk, fill,
                p, RectF(g.col0.toFloat(), g.row0.toFloat(), (g.col0 + g.width).toFloat(), (g.row0 + g.height).toFloat())))
        }
    }

    fun draw(
        canvas: Canvas, paint: Paint, sw: Float, sh: Float, t: Float,
        discovered: Set<String>, benches: List<Bench>, playerX: Float, playerY: Float, currentAreaId: String?
    ) {
        // Backing parchment
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(236, 8, 11, 20)
        canvas.drawRect(0f, 0f, sw, sh, paint)

        val margin = min(sw, sh) * 0.06f
        val s = min((sw - margin * 2f) / worldCols, (sh - margin * 2f - 40f) / worldRows)
        val ox = (sw - worldCols * s) / 2f
        val oy = (sh - worldRows * s) / 2f + 14f

        // Frame
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = Color.argb(140, 150, 170, 200)
        canvas.drawRoundRect(margin * 0.5f, margin * 0.5f, sw - margin * 0.5f, sh - margin * 0.5f, 10f, 10f, paint)
        paint.strokeWidth = 0.8f
        canvas.drawRoundRect(margin * 0.5f + 6f, margin * 0.5f + 6f, sw - margin * 0.5f - 6f, sh - margin * 0.5f - 6f, 8f, 8f, paint)

        // Title
        paint.style = Paint.Style.FILL
        paint.typeface = serifBold
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = max(18f, min(sw, sh) * 0.045f)
        paint.color = Color.rgb(226, 232, 242)
        paint.letterSpacing = 0.18f
        canvas.drawText("HALLOWNEST", sw / 2f, margin * 0.5f + paint.textSize + 8f, paint)
        paint.letterSpacing = 0f

        // Area silhouettes
        canvas.save()
        canvas.translate(ox, oy)
        canvas.scale(s, s)
        for (c in charts) {
            if (c.id !in discovered) continue
            paint.style = Paint.Style.STROKE
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeWidth = 2.2f
            paint.color = c.ink
            paint.alpha = 230
            canvas.drawPath(c.path, paint)
            paint.style = Paint.Style.FILL
            paint.color = c.fill
            canvas.drawPath(c.path, paint)
            if (c.id == currentAreaId) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.6f
                paint.color = Color.argb(90, 255, 255, 255)
                canvas.drawPath(c.path, paint)
            }
        }
        canvas.restore()
        paint.alpha = 255

        // Bench icons
        for (b in benches) {
            if (!b.discovered) continue
            val bx = ox + b.x / 40f * s
            val by = oy + b.y / 40f * s
            paint.style = Paint.Style.FILL
            paint.color = if (b.isCurrent) Color.rgb(255, 226, 150) else Color.rgb(205, 212, 226)
            canvas.drawRoundRect(bx - 7f, by - 5f, bx + 7f, by - 2f, 1.5f, 1.5f, paint)
            canvas.drawRect(bx - 6f, by - 2f, bx - 4f, by + 1f, paint)
            canvas.drawRect(bx + 4f, by - 2f, bx + 6f, by + 1f, paint)
            canvas.drawRect(bx - 6f, by - 9f, bx + 6f, by - 7f, paint)
        }

        // Area names
        paint.typeface = serif
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = max(10f, min(sw, sh) * 0.022f)
        for (c in charts) {
            if (c.id !in discovered) continue
            val cx = ox + c.bounds.centerX() * s
            val cy = oy + c.bounds.centerY() * s
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(170, 0, 0, 0)
            canvas.drawText(c.name, cx + 1f, cy + 1f, paint)
            paint.color = Color.rgb(236, 240, 248)
            canvas.drawText(c.name, cx, cy, paint)
        }

        // Player pin
        val px = ox + playerX / 40f * s
        val py = oy + playerY / 40f * s
        val pulse = 0.6f + 0.4f * sin(t * 4f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = Color.argb((160 * pulse).toInt(), 255, 255, 255)
        canvas.drawCircle(px, py - 4f, 9f + 5f * pulse, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawOval(px - 5f, py - 11f, px + 5f, py + 2f, paint)
        paint.color = Color.rgb(8, 11, 20)
        canvas.drawOval(px - 3f, py - 7f, px - 1f, py - 3f, paint)
        canvas.drawOval(px + 1f, py - 7f, px + 3f, py - 3f, paint)

        // Hint
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 12f
        paint.color = Color.argb(170, 180, 192, 210)
        canvas.drawText("${discovered.size} / ${charts.size} areas charted   ·   MAP to close", sw / 2f, sh - margin * 0.5f - 12f, paint)
        paint.textAlign = Paint.Align.LEFT
        paint.typeface = Typeface.DEFAULT
        paint.strokeJoin = Paint.Join.MITER
    }
}
