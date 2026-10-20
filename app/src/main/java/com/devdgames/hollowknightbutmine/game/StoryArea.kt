package com.devdgames.hollowknightbutmine.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.sin

/**
 * First story slice in Dustmere: two spectral witnesses, a contradictory inscription,
 * and a three-rune sequence puzzle. All positions are relative to the town spawn.
 */
class StoryArea(private val originX: Float, private val floorY: Float) {
    private val keeperX = originX + 170f
    private val witnessX = originX + 390f
    private val puzzleX = originX + 650f
    private val runeOrder = intArrayOf(0, 1, 2) // left, middle, right
    private val activated = BooleanArray(3)
    private var nextRune = 0
    private var solved = false
    private var dialogue = ""
    private var speaker = ""
    private var dialogueTimer = 0f
    private var clueFound = false
    private val panel = RectF()

    fun update(dt: Float) {
        if (dialogueTimer > 0f) dialogueTimer = (dialogueTimer - dt).coerceAtLeast(0f)
    }

    /** True when an NPC or rune can be used from the player's current position. */
    fun canInteract(playerX: Float, playerY: Float): Boolean {
        if (abs(playerY - floorY) > 115f) return false
        return abs(playerX - keeperX) <= 82f || abs(playerX - witnessX) <= 82f || (0..2).any { abs(playerX - runeX(it)) <= 70f }
    }

    /** Interact with the closest story object in reach. Returns true when something happened. */
    fun interact(playerX: Float, playerY: Float): Boolean {
        if (abs(playerY - floorY) > 115f) return false
        val keeperDistance = abs(playerX - keeperX)
        val witnessDistance = abs(playerX - witnessX)
        val runeIndex = (0..2).minByOrNull { abs(playerX - runeX(it)) } ?: 0
        val runeDistance = abs(playerX - runeX(runeIndex))

        return when {
            keeperDistance <= 82f && keeperDistance <= witnessDistance && keeperDistance <= runeDistance -> {
                speak("THE LANTERN KEEPER", "The bell has not rung in years. If you heard it... tell no one. Especially not the Witness.", 6.5f)
                true
            }
            witnessDistance <= 82f && witnessDistance <= runeDistance -> {
                clueFound = true
                speak("THE HOLLOW WITNESS", "You came here before, little knight. You asked us to forget. Read the stones, if you still dare.", 6.5f)
                true
            }
            runeDistance <= 70f -> {
                activateRune(runeIndex)
                true
            }
            else -> false
        }
    }

    private fun activateRune(index: Int) {
        if (solved) {
            speak("THE SEALED DOOR", "The passage is open. Something below is breathing.", 4.5f)
            return
        }
        if (activated[index]) {
            speak("THE STONE", "This rune is already awake.", 2.5f)
            return
        }
        if (index == runeOrder[nextRune]) {
            activated[index] = true
            nextRune++
            if (nextRune == runeOrder.size) {
                solved = true
                speak("THE SEALED DOOR", "The stones fall silent. The passage opens... and a voice whispers your name.", 7f)
            } else {
                speak("THE STONE", "A low note answers from beneath the earth.", 3f)
            }
        } else {
            activated.fill(false)
            nextRune = 0
            speak("THE STONE", "The runes flare, then die. The sequence resets.", 3.5f)
        }
    }

    private fun runeX(index: Int) = puzzleX + index * 52f

    private fun speak(who: String, words: String, seconds: Float) {
        speaker = who
        dialogue = words
        dialogueTimer = seconds
    }

    fun drawWorld(canvas: Canvas, paint: Paint, time: Float, view: RectF) {
        if (view.right < keeperX - 120f || view.left > puzzleX + 180f) return
        drawSpirit(canvas, paint, keeperX, floorY, time, helpful = true)
        drawSpirit(canvas, paint, witnessX, floorY, time, helpful = false)

        // Weathered inscription: a permanent environmental clue, not a dialogue dump.
        if (view.left < witnessX + 190f && view.right > witnessX + 80f) {
            val x = witnessX + 130f
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(48, 49, 57)
            canvas.drawRoundRect(x - 24f, floorY - 58f, x + 24f, floorY - 3f, 5f, 5f, paint)
            paint.color = Color.rgb(194, 174, 126)
            paint.strokeWidth = 2f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(x - 12f, floorY - 42f, x + 12f, floorY - 42f, paint)
            canvas.drawLine(x - 8f, floorY - 32f, x + 8f, floorY - 32f, paint)
            canvas.drawLine(x - 12f, floorY - 22f, x + 10f, floorY - 22f, paint)
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 10f
            paint.typeface = Typeface.SERIF
            paint.color = Color.rgb(224, 204, 157)
            canvas.drawText("WE CHOSE TO FORGET", x, floorY - 68f, paint)
            paint.textSize = 9f
            canvas.drawText("LEFT  •  MIDDLE  •  RIGHT", x, floorY - 79f, paint)
            paint.textAlign = Paint.Align.LEFT
            paint.typeface = Typeface.DEFAULT
        }

        // Three rune stones. Correct order is indicated by the carved inscription nearby.
        for (i in 0..2) {
            val x = runeX(i)
            paint.style = Paint.Style.FILL
            paint.color = if (solved) Color.rgb(71, 116, 112) else Color.rgb(44, 48, 59)
            canvas.drawRoundRect(x - 15f, floorY - 34f, x + 15f, floorY - 2f, 4f, 4f, paint)
            paint.color = when {
                solved || activated[i] -> Color.rgb(255, 207, 115)
                else -> Color.rgb(106, 127, 150)
            }
            canvas.drawCircle(x, floorY - 19f, 6f + if (solved || activated[i]) sin(time * 3f) * 1.2f else 0f, paint)
            paint.color = Color.argb(120, 5, 8, 14)
            canvas.drawRect(x - 18f, floorY - 1f, x + 18f, floorY + 3f, paint)
        }
        if (solved) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = Color.argb((150 + sin(time * 4f) * 60f).toInt().coerceIn(0, 255), 231, 190, 112)
            canvas.drawRoundRect(puzzleX - 30f, floorY - 105f, puzzleX + 134f, floorY - 2f, 10f, 10f, paint)
            paint.style = Paint.Style.FILL
        }
    }

    private fun drawSpirit(canvas: Canvas, paint: Paint, x: Float, ground: Float, time: Float, helpful: Boolean) {
        val bob = sin(time * if (helpful) 2.1f else 3.4f + x * 0.001f) * if (helpful) 4f else 7f
        val cy = ground - 36f + bob
        val aura = if (helpful) Color.rgb(106, 201, 213) else Color.rgb(156, 111, 184)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(if (helpful) 28 else 38, Color.red(aura), Color.green(aura), Color.blue(aura))
        canvas.drawCircle(x, cy, if (helpful) 29f else 34f, paint)
        paint.color = Color.argb(if (helpful) 220 else 170, Color.red(aura), Color.green(aura), Color.blue(aura))
        val body = Path().apply {
            moveTo(x - 15f, cy - 7f)
            cubicTo(x - 20f, cy + 13f, x - 11f, cy + 19f, x - 14f, cy + 28f)
            lineTo(x - 5f, cy + 21f)
            lineTo(x + 2f, cy + 29f)
            lineTo(x + 8f, cy + 20f)
            lineTo(x + 15f, cy + 26f)
            cubicTo(x + 12f, cy + 12f, x + 19f, cy + 1f, x + 12f, cy - 9f)
            close()
        }
        canvas.drawPath(body, paint)
        paint.color = Color.argb(235, 235, 239, 244)
        canvas.drawOval(x - 9f, cy - 15f, x + 9f, cy + 7f, paint)
        paint.color = if (helpful) Color.rgb(24, 53, 63) else Color.rgb(34, 19, 46)
        canvas.drawOval(x - 5f, cy - 7f, x - 2f, cy + 1f, paint)
        canvas.drawOval(x + 3f, cy - 7f, x + 6f, cy + 1f, paint)
        paint.color = Color.argb(175, 210, 220, 232)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 10f
        paint.typeface = Typeface.SERIF
        canvas.drawText(if (helpful) "LANTERN KEEPER" else "HOLLOW WITNESS", x, ground - 70f + bob, paint)
        paint.textAlign = Paint.Align.LEFT
        paint.typeface = Typeface.DEFAULT
    }

    fun drawDialogue(canvas: Canvas, paint: Paint, screenWidth: Float, screenHeight: Float) {
        if (dialogueTimer <= 0f) return
        val left = 28f
        val top = screenHeight - 154f
        panel.set(left, top, screenWidth - left, screenHeight - 28f)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(232, 8, 12, 22)
        canvas.drawRoundRect(panel, 12f, 12f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = Color.rgb(103, 146, 165)
        canvas.drawRoundRect(panel, 12f, 12f, paint)
        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.SERIF
        paint.textSize = 16f
        paint.color = Color.rgb(238, 210, 148)
        canvas.drawText(speaker, left + 18f, top + 27f, paint)
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 13f
        paint.color = Color.rgb(218, 225, 235)
        val words = dialogue.split(" ")
        val maxWidth = screenWidth - left * 2f - 36f
        var line = ""
        var y = top + 53f
        for (word in words) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                canvas.drawText(line, left + 18f, y, paint)
                y += 19f
                line = word
            } else line = candidate
        }
        if (line.isNotEmpty()) canvas.drawText(line, left + 18f, y, paint)
        paint.textSize = 10f
        paint.color = Color.rgb(124, 153, 171)
        canvas.drawText("F / INTERACT", screenWidth - 112f, screenHeight - 42f, paint)
        paint.typeface = Typeface.DEFAULT
    }

    fun isClueFound() = clueFound
    fun isPuzzleSolved() = solved
}
