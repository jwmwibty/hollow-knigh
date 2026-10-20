package com.devdgames.hollowknightbutmine.game.story

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The story on screen: the dialogue box, the task tracker, the small line that says something
 * was picked up, and the journal that opens over the whole kingdom.
 *
 * Same idiom as the rest of the game's UI — native Canvas, text and shapes, nothing cached in a
 * bitmap, and every function leaves the [Paint] the way it found it.
 */
object StoryHud {

    private val scratch = Path()
    private val wrapped = ArrayList<String>()

    private val serif: Typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
    private val serifBold: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)

    // ===================================================================== dialogue

    /** The panel along the bottom of the screen, exactly where the old story slice put it. */
    fun drawDialogue(canvas: Canvas, paint: Paint, queue: DialogueQueue, time: Float, screenWidth: Float, screenHeight: Float) {
        val line = queue.current ?: return
        val left = 28f
        val top = screenHeight - 154f
        val right = screenWidth - left
        val bottom = screenHeight - 28f

        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.argb(232, 8, 12, 22)
        canvas.drawRoundRect(left, top, right, bottom, 12f, 12f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = Color.argb(170, 103, 146, 165)
        canvas.drawRoundRect(left, top, right, bottom, 12f, 12f, paint)

        // A hairline under the speaker, so the name reads as a name.
        paint.strokeWidth = 1f
        paint.color = Color.argb(90, 150, 178, 196)
        canvas.drawLine(left + 18f, top + 35f, right - 18f, top + 35f, paint)

        paint.style = Paint.Style.FILL
        paint.typeface = serif
        paint.textSize = 16f
        paint.letterSpacing = 0.1f
        paint.color = Color.rgb(238, 210, 148)
        canvas.drawText(line.speaker, left + 18f, top + 27f, paint)
        paint.letterSpacing = 0f

        paint.typeface = Typeface.DEFAULT
        paint.textSize = 13f
        paint.color = Color.rgb(218, 225, 235)
        val maxWidth = right - left - 36f
        wrapTo(paint, line.text, maxWidth)
        var y = top + 55f
        var shown = 0
        for (w in wrapped) {
            if (shown == 4) break
            canvas.drawText(w, left + 18f, y, paint)
            y += 19f
            shown++
        }

        paint.textSize = 10f
        paint.color = Color.rgb(124, 153, 171)
        val hint = if (queue.remaining > 0) "F / INTERACT  ·  " + (queue.remaining + 1) + " more" else "F / INTERACT"
        canvas.drawText(hint, right - 18f - paint.measureText(hint), bottom - 14f, paint)

        // The continuation diamond: there is more, and Interact will get to it.
        if (queue.remaining > 0) {
            val pulse = 0.55f + 0.45f * sin(time * 4.5f)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.6f
            paint.color = Color.argb((170 * pulse).toInt().coerceIn(0, 255), 238, 210, 148)
            val cx = right - 30f
            val cy = top + 22f
            scratch.reset()
            scratch.moveTo(cx, cy - 6f); scratch.lineTo(cx + 4f, cy); scratch.lineTo(cx, cy + 6f); scratch.lineTo(cx - 4f, cy)
            scratch.close()
            canvas.drawPath(scratch, paint)
        }

        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.DEFAULT
        paint.strokeWidth = 1f
    }

    // ===================================================================== tracker

    /** Two lines under the masks: what the kingdom is currently asking for. */
    fun drawTracker(canvas: Canvas, paint: Paint, entries: List<TrackerEntry>, screenWidth: Float) {
        if (entries.isEmpty()) return
        var y = 128f
        for (e in entries) {
            paint.shader = null
            paint.typeface = serif
            paint.textSize = 12.5f
            paint.textAlign = Paint.Align.LEFT
            val nameWidth = paint.measureText(e.name)
            paint.typeface = Typeface.DEFAULT
            paint.textSize = 11f
            val lineWidth = paint.measureText(e.line)
            val w = min(max(nameWidth, lineWidth) + 46f, screenWidth * 0.5f)

            paint.style = Paint.Style.FILL
            paint.color = Color.argb(150, 8, 13, 23)
            canvas.drawRoundRect(18f, y - 15f, 18f + w, y + 25f, 8f, 8f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            paint.color = Color.argb(if (e.ready) 150 else 90, if (e.ready) 214 else 125, if (e.ready) 176 else 165, if (e.ready) 96 else 183)
            canvas.drawRoundRect(18f, y - 15f, 18f + w, y + 25f, 8f, 8f, paint)

            // The mark: a diamond, filled when the task only wants walking back.
            val cx = 32f
            val cy = y
            paint.style = if (e.ready) Paint.Style.FILL else Paint.Style.STROKE
            paint.strokeWidth = 1.6f
            paint.color = if (e.ready) Color.argb(235, 240, 208, 130) else Color.argb(190, 170, 200, 216)
            scratch.reset()
            scratch.moveTo(cx, cy - 5f); scratch.lineTo(cx + 4f, cy); scratch.lineTo(cx, cy + 5f); scratch.lineTo(cx - 4f, cy)
            scratch.close()
            canvas.drawPath(scratch, paint)

            paint.style = Paint.Style.FILL
            paint.typeface = serif
            paint.textSize = 12.5f
            paint.letterSpacing = 0.05f
            paint.color = if (e.ready) Color.argb(235, 240, 214, 150) else Color.argb(215, 214, 226, 240)
            canvas.drawText(e.name, 44f, y + 1f, paint)
            paint.letterSpacing = 0f
            paint.typeface = Typeface.DEFAULT
            paint.textSize = 11f
            paint.color = Color.argb(175, 168, 186, 202)
            canvas.drawText(clip(paint, e.line, w - 46f), 44f, y + 18f, paint)
            y += 46f
        }
        paint.strokeWidth = 1f
    }

    // ===================================================================== toast

    /** One quiet line, centred, for a shard taken or a task turned in. */
    fun drawToast(canvas: Canvas, paint: Paint, text: String, timer: Float, screenWidth: Float, screenHeight: Float) {
        if (timer <= 0f || text.isEmpty()) return
        val a = ((min(timer, 0.6f) / 0.6f) * 235f).toInt().coerceIn(0, 235)
        val y = screenHeight * 0.2f

        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.typeface = serif
        paint.textSize = 14f
        paint.textAlign = Paint.Align.CENTER
        paint.letterSpacing = 0.08f
        val shown = clip(paint, text, screenWidth * 0.78f)
        val w = paint.measureText(shown)
        paint.color = Color.argb((a * 0.55f).toInt(), 6, 10, 18)
        canvas.drawRoundRect(screenWidth / 2f - w / 2f - 18f, y - 19f, screenWidth / 2f + w / 2f + 18f, y + 11f, 8f, 8f, paint)
        paint.color = Color.argb(a, 226, 214, 176)
        canvas.drawText(shown, screenWidth / 2f, y, paint)
        paint.letterSpacing = 0f
        paint.typeface = Typeface.DEFAULT
        paint.textAlign = Paint.Align.LEFT
    }

    // ===================================================================== journal

    /**
     * The journal of tasks: everything the kingdom has asked for, in the order it asked, with
     * what is still outstanding. Tasks nobody has offered yet are not described, only counted —
     * the map does the same thing with rooms the player has not stood in.
     */
    fun drawJournal(canvas: Canvas, paint: Paint, log: QuestLog, nameOf: (String) -> String, screenWidth: Float, screenHeight: Float) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(240, 7, 10, 18)
        canvas.drawRect(0f, 0f, screenWidth, screenHeight, paint)

        val margin = min(screenWidth, screenHeight) * 0.06f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = Color.argb(140, 150, 170, 200)
        canvas.drawRoundRect(margin * 0.5f, margin * 0.5f, screenWidth - margin * 0.5f, screenHeight - margin * 0.5f, 10f, 10f, paint)
        paint.strokeWidth = 0.8f
        canvas.drawRoundRect(margin * 0.5f + 6f, margin * 0.5f + 6f, screenWidth - margin * 0.5f - 6f, screenHeight - margin * 0.5f - 6f, 8f, 8f, paint)

        paint.style = Paint.Style.FILL
        paint.typeface = serifBold
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = max(18f, min(screenWidth, screenHeight) * 0.04f)
        paint.letterSpacing = 0.22f
        paint.color = Color.rgb(226, 232, 242)
        canvas.drawText("TASKS", screenWidth / 2f, margin * 0.5f + paint.textSize + 10f, paint)
        paint.letterSpacing = 0f

        // One pass, no allocation: the journal is open for a while and drawn every frame.
        var finished = 0
        var readyCount = 0
        var activeCount = 0
        var offered = 0
        var locked = 0
        for (s in log.states.values) {
            when (s.stage) {
                QuestStage.DONE -> finished++
                QuestStage.READY -> readyCount++
                QuestStage.ACTIVE -> activeCount++
                QuestStage.OFFERED -> offered++
                QuestStage.LOCKED -> locked++
            }
        }
        paint.typeface = serif
        paint.textSize = 12.5f
        paint.color = Color.argb(180, 176, 190, 210)
        canvas.drawText(
            "" + finished + " finished   ·   " + (readyCount + activeCount) + " under way   ·   " + offered + " waiting to be taken" +
                (if (locked > 0) "   ·   " + locked + " not yet spoken of" else ""),
            screenWidth / 2f, margin * 0.5f + paint.textSize + 34f, paint
        )
        paint.typeface = Typeface.DEFAULT
        paint.textAlign = Paint.Align.LEFT

        // Two columns, filled in the order the tasks were written.
        val columnWidth = (screenWidth - margin * 2f - 34f) / 2f
        val columnTops = floatArrayOf(margin + 66f, margin + 66f)
        val columnLefts = floatArrayOf(margin + 12f, margin + 22f + columnWidth)
        val bottomLimit = screenHeight - margin - 34f
        var column = 0

        for (id in log.ids) {
            val s = log.state(id) ?: continue
            if (s.stage == QuestStage.LOCKED) continue
            val height = entryHeight(paint, s, columnWidth)
            if (columnTops[column] + height > bottomLimit && column == 0) column = 1
            if (columnTops[column] + height > bottomLimit + 60f) break
            drawEntry(canvas, paint, s, nameOf, columnLefts[column], columnTops[column], columnWidth)
            columnTops[column] += height + 16f
        }

        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 12f
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.argb(170, 180, 192, 210)
        canvas.drawText("T  /  TASKS to close", screenWidth / 2f, screenHeight - margin * 0.5f - 12f, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    /** How tall one entry will be, so the columns can be filled without drawing twice. */
    private fun entryHeight(paint: Paint, s: QuestState, width: Float): Float {
        var h = 32f
        if (s.stage != QuestStage.DONE) {
            paint.textSize = 11.5f
            wrapTo(paint, s.def.brief, width - 26f)
            h += min(wrapped.size, 2) * 15f
        }
        if (s.stage == QuestStage.ACTIVE || s.stage == QuestStage.READY) {
            paint.textSize = 12f
            for (i in s.def.objectives.indices) {
                wrapTo(paint, s.describe(i), width - 46f)
                h += max(1, min(wrapped.size, 2)) * 15f + 2f
            }
        }
        if (s.stage == QuestStage.OFFERED) h += 18f
        if (s.stage == QuestStage.READY) h += 18f
        if (s.stage == QuestStage.DONE && s.def.maskReward > 0) h += 18f
        return h
    }

    private fun drawEntry(
        canvas: Canvas, paint: Paint, s: QuestState, nameOf: (String) -> String,
        left: Float, top: Float, width: Float
    ) {
        val ready = s.stage == QuestStage.READY
        val done = s.stage == QuestStage.DONE
        val offered = s.stage == QuestStage.OFFERED
        val nameColor = when {
            ready -> Color.rgb(240, 208, 130)
            done -> Color.argb(190, 150, 164, 180)
            offered -> Color.rgb(168, 216, 176)
            else -> Color.rgb(216, 228, 242)
        }

        // The stage mark.
        val cx = left + 7f
        val cy = top + 5f
        paint.style = if (ready || done) Paint.Style.FILL else Paint.Style.STROKE
        paint.strokeWidth = 1.6f
        paint.color = nameColor
        scratch.reset()
        if (done) {
            scratch.moveTo(cx - 5f, cy); scratch.lineTo(cx - 1f, cy + 4f); scratch.lineTo(cx + 6f, cy - 5f)
            canvas.drawPath(scratch, paint)
        } else {
            scratch.moveTo(cx, cy - 5f); scratch.lineTo(cx + 4f, cy); scratch.lineTo(cx, cy + 5f); scratch.lineTo(cx - 4f, cy)
            scratch.close()
            canvas.drawPath(scratch, paint)
        }
        paint.style = Paint.Style.FILL

        paint.typeface = serif
        paint.textSize = 15f
        paint.letterSpacing = 0.04f
        paint.color = nameColor
        canvas.drawText(s.def.name, left + 20f, top + 10f, paint)
        paint.letterSpacing = 0f
        paint.typeface = Typeface.DEFAULT

        var y = top + 28f
        if (!done) {
            paint.textSize = 11.5f
            paint.color = Color.argb(160, 168, 182, 200)
            wrapTo(paint, s.def.brief, width - 26f)
            var shown = 0
            for (w in wrapped) {
                if (shown == 2) break
                canvas.drawText(w, left + 20f, y, paint)
                y += 15f
                shown++
            }
        }
        if (s.stage == QuestStage.ACTIVE || ready) {
            paint.textSize = 12f
            for (i in s.def.objectives.indices) {
                val complete = s.isComplete(i)
                val markX = left + 24f
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.3f
                paint.color = if (complete) Color.argb(200, 150, 200, 168) else Color.argb(150, 140, 156, 176)
                canvas.drawCircle(markX, y - 4f, 3.4f, paint)
                if (complete) {
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(markX, y - 4f, 1.8f, paint)
                }
                paint.style = Paint.Style.FILL
                paint.color = if (complete) Color.argb(150, 158, 178, 166) else Color.argb(225, 214, 226, 240)
                wrapTo(paint, s.describe(i), width - 46f)
                var shown = 0
                for (w in wrapped) {
                    if (shown == 2) break
                    canvas.drawText(w, left + 36f, y, paint)
                    y += 15f
                    shown++
                }
                y += 2f
            }
        }
        if (offered) {
            paint.textSize = 11.5f
            paint.color = Color.argb(190, 168, 216, 176)
            canvas.drawText("Offered by " + nameOf(s.def.giver), left + 20f, y, paint)
            y += 16f
        }
        if (ready) {
            paint.textSize = 11.5f
            paint.color = Color.argb(210, 240, 208, 130)
            canvas.drawText("Back to " + nameOf(s.def.giver), left + 20f, y + 14f, paint)
        }
        if (done && s.def.maskReward > 0) {
            paint.textSize = 11.5f
            paint.color = Color.argb(170, 200, 190, 160)
            canvas.drawText("Paid in a mask, and kept", left + 20f, y + 14f, paint)
        }
    }

    // ===================================================================== text helpers

    /** Word-wraps into [wrapped], reusing the list so a frame never allocates. */
    private fun wrapTo(paint: Paint, text: String, maxWidth: Float) {
        wrapped.clear()
        if (maxWidth <= 0f) {
            wrapped.add(text)
            return
        }
        var line = ""
        for (word in text.split(" ")) {
            val candidate = if (line.isEmpty()) word else line + " " + word
            if (line.isNotEmpty() && paint.measureText(candidate) > maxWidth) {
                wrapped.add(line)
                line = word
            } else {
                line = candidate
            }
        }
        if (line.isNotEmpty()) wrapped.add(line)
    }

    /** One line, cut to fit, with the usual three dots. */
    private fun clip(paint: Paint, text: String, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }
}
