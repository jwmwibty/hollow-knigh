package com.devdgames.hollowknightbutmine.game.story

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The cast, drawn.
 *
 * Eight kinds, eight silhouettes: nobody in this kingdom is a recolour of somebody else.
 * Everything is procedural Canvas work in the same idiom as the rest of the game — no bitmaps,
 * no allocation in the frame, and every call leaves the [Paint] exactly as it found it.
 */
object NpcArt {

    private val scratch = Path()

    /** Draws one character standing (or floating) on [npc.floorY]. */
    fun draw(canvas: Canvas, paint: Paint, npc: PlacedNpc, time: Float, offerPrompt: Boolean) {
        val x = npc.x
        val ground = npc.floorY
        val accent = npc.def.accent
        shadow(canvas, paint, x, ground, npc.def.kind)
        when (npc.def.kind) {
            NpcKind.SPIRIT -> spirit(canvas, paint, x, ground, time, accent, restless = false)
            NpcKind.SHADE -> spirit(canvas, paint, x, ground, time, accent, restless = true)
            NpcKind.LAMPLIGHTER -> lamplighter(canvas, paint, x, ground, time, accent)
            NpcKind.CARTOGRAPHER -> cartographer(canvas, paint, x, ground, time, accent)
            NpcKind.SISTER -> sister(canvas, paint, x, ground, time, accent)
            NpcKind.WARDEN -> warden(canvas, paint, x, ground, time, accent)
            NpcKind.SLUICE_KEEPER -> sluiceKeeper(canvas, paint, x, ground, time, accent)
            NpcKind.PALE_SCRIBE -> paleScribe(canvas, paint, x, ground, time, accent)
        }
        if (offerPrompt) promptGlyph(canvas, paint, x, headY(npc.def.kind, ground, time) - 26f, time, accent)
        label(canvas, paint, npc.def.name, x, headY(npc.def.kind, ground, time) - 36f, accent)
    }

    /** Where the top of this character's head is, for the label and the prompt. */
    private fun headY(kind: NpcKind, ground: Float, time: Float): Float = when (kind) {
        NpcKind.SPIRIT, NpcKind.SHADE -> ground - 74f + sin(time * 2.1f) * 4f
        NpcKind.LAMPLIGHTER -> ground - 66f
        NpcKind.CARTOGRAPHER -> ground - 52f
        NpcKind.SISTER -> ground - 64f
        NpcKind.WARDEN -> ground - 70f + sin(time * 1.3f) * 2f
        NpcKind.SLUICE_KEEPER -> ground - 60f
        NpcKind.PALE_SCRIBE -> ground - 84f + sin(time * 1.1f) * 3f
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

    private fun shadow(canvas: Canvas, paint: Paint, x: Float, ground: Float, kind: NpcKind) {
        val w = when (kind) {
            NpcKind.SPIRIT, NpcKind.SHADE -> 20f
            NpcKind.CARTOGRAPHER -> 17f
            NpcKind.WARDEN -> 22f
            NpcKind.PALE_SCRIBE -> 15f
            else -> 21f
        }
        // Things that float do not press on the ground, so their shadow is thin and soft.
        val floats = kind == NpcKind.SPIRIT || kind == NpcKind.SHADE || kind == NpcKind.PALE_SCRIBE
        fill(paint, Color.argb(if (floats) 60 else 110, 4, 7, 12))
        canvas.drawOval(x - w, ground - (if (floats) 3f else 5f), x + w, ground + (if (floats) 3f else 5f), paint)
    }

    private fun label(canvas: Canvas, paint: Paint, text: String, x: Float, y: Float, accent: Int) {
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 10f
        paint.typeface = Typeface.SERIF
        paint.color = Color.argb(60, 4, 7, 12)
        canvas.drawText(text, x + 1f, y + 1f, paint)
        paint.color = Color.argb(
            185,
            (Color.red(accent) + 190) / 2,
            (Color.green(accent) + 205) / 2,
            (Color.blue(accent) + 220) / 2
        )
        canvas.drawText(text, x, y, paint)
        paint.textAlign = Paint.Align.LEFT
        paint.typeface = Typeface.DEFAULT
    }

    /** A small pulsing diamond: this character has something to say right now. */
    private fun promptGlyph(canvas: Canvas, paint: Paint, x: Float, y: Float, time: Float, accent: Int) {
        val pulse = 0.6f + 0.4f * sin(time * 4.5f)
        val r = 4.5f + 1.6f * pulse
        val yy = y - 3f * pulse
        stroke(paint, Color.argb((150 * pulse).toInt().coerceIn(0, 255), Color.red(accent), Color.green(accent), Color.blue(accent)), 1.6f)
        scratch.reset()
        scratch.moveTo(x, yy - r)
        scratch.lineTo(x + r * 0.7f, yy)
        scratch.lineTo(x, yy + r)
        scratch.lineTo(x - r * 0.7f, yy)
        scratch.close()
        canvas.drawPath(scratch, paint)
        fill(paint, Color.argb((200 * pulse).toInt().coerceIn(0, 255), 240, 236, 214))
        canvas.drawCircle(x, yy, 1.6f, paint)
    }

    /** A pale mask face: the two dark eyes every character in this kingdom shares. */
    private fun face(canvas: Canvas, paint: Paint, cx: Float, cy: Float, w: Float, h: Float, skin: Int, eye: Int) {
        fill(paint, skin)
        canvas.drawOval(cx - w, cy - h, cx + w, cy + h, paint)
        fill(paint, eye)
        canvas.drawOval(cx - w * 0.52f, cy - h * 0.22f, cx - w * 0.16f, cy + h * 0.3f, paint)
        canvas.drawOval(cx + w * 0.16f, cy - h * 0.22f, cx + w * 0.52f, cy + h * 0.3f, paint)
    }

    /** A hanging robe: shoulders at [top], hem flaring to the ground, swaying a little. */
    private fun robe(canvas: Canvas, paint: Paint, cx: Float, ground: Float, top: Float, shoulder: Float, hem: Float, sway: Float, body: Int, trim: Int) {
        fill(paint, body)
        scratch.reset()
        scratch.moveTo(cx - shoulder, top)
        scratch.cubicTo(cx - shoulder - 3f, top + (ground - top) * 0.45f, cx - hem + sway, ground - 8f, cx - hem + sway * 1.6f, ground)
        scratch.lineTo(cx + hem + sway * 1.6f, ground)
        scratch.cubicTo(cx + hem + sway, ground - 8f, cx + shoulder + 3f, top + (ground - top) * 0.45f, cx + shoulder, top)
        scratch.close()
        canvas.drawPath(scratch, paint)
        if (trim != 0) {
            stroke(paint, trim, 1.4f)
            canvas.drawLine(cx - hem * 0.62f + sway, ground - 6f, cx + hem * 0.62f + sway, ground - 6f, paint)
        }
    }

    // ===================================================================== the cast

    /** The Keeper and the Witness: the same shroud, worn very differently. */
    private fun spirit(canvas: Canvas, paint: Paint, x: Float, ground: Float, time: Float, accent: Int, restless: Boolean) {
        val speed = if (restless) 3.4f else 2.1f
        val bob = sin(time * speed + x * 0.001f) * (if (restless) 7f else 4f)
        val jitter = if (restless) sin(time * 17f) * 1.6f else 0f
        val cy = ground - 40f + bob
        val cx = x + jitter

        fill(paint, Color.argb(if (restless) 40 else 26, Color.red(accent), Color.green(accent), Color.blue(accent)))
        canvas.drawCircle(cx, cy, if (restless) 34f else 29f, paint)

        // Tattered shroud: the hem is a row of points, and it drifts behind the direction of travel.
        fill(paint, Color.argb(if (restless) 172 else 220, Color.red(accent), Color.green(accent), Color.blue(accent)))
        scratch.reset()
        scratch.moveTo(cx - 15f, cy - 7f)
        scratch.cubicTo(cx - 20f, cy + 13f, cx - 11f, cy + 19f, cx - 14f, cy + 28f)
        scratch.lineTo(cx - 5f, cy + 21f)
        scratch.lineTo(cx + 2f, cy + 29f)
        scratch.lineTo(cx + 8f, cy + 20f)
        scratch.lineTo(cx + 15f, cy + 26f)
        scratch.cubicTo(cx + 12f, cy + 12f, cx + 19f, cy + 1f, cx + 12f, cy - 9f)
        scratch.close()
        canvas.drawPath(scratch, paint)

        if (restless) {
            // A wisp of the Witness peels off and goes nowhere, over and over.
            val w = (time * 0.9f) % 1f
            fill(paint, Color.argb((90 * (1f - w)).toInt().coerceIn(0, 255), Color.red(accent), Color.green(accent), Color.blue(accent)))
            canvas.drawCircle(cx + 14f + w * 12f, cy - 10f - w * 26f, 2.6f + 2.4f * w, paint)
        }

        face(
            canvas, paint, cx, cy - 4f, 9f, 11f,
            Color.argb(235, 235, 239, 244),
            if (restless) Color.rgb(34, 19, 46) else Color.rgb(24, 53, 63)
        )
        // Horns of the mask: the two little points this kingdom's dead all wear.
        fill(paint, Color.argb(235, 235, 239, 244))
        scratch.reset()
        scratch.moveTo(cx - 8f, cy - 11f); scratch.lineTo(cx - 13f, cy - 21f); scratch.lineTo(cx - 3f, cy - 14f); scratch.close()
        scratch.moveTo(cx + 8f, cy - 11f); scratch.lineTo(cx + 13f, cy - 21f); scratch.lineTo(cx + 3f, cy - 14f); scratch.close()
        canvas.drawPath(scratch, paint)
    }

    /** Sable: alive, oil-stained, carrying the only lit lamp in Dustmere. */
    private fun lamplighter(canvas: Canvas, paint: Paint, x: Float, ground: Float, time: Float, accent: Int) {
        val sway = sin(time * 1.6f) * 1.6f
        robe(canvas, paint, x, ground, ground - 52f, 12f, 15f, sway, Color.rgb(46, 44, 54), Color.argb(150, 92, 84, 70))

        // Oil stains on the hem, and a belt of rags.
        fill(paint, Color.argb(150, 26, 24, 30))
        canvas.drawOval(x - 9f, ground - 12f, x - 2f, ground - 4f, paint)
        canvas.drawOval(x + 3f, ground - 9f, x + 10f, ground - 3f, paint)

        // Hood and face.
        fill(paint, Color.rgb(56, 53, 64))
        canvas.drawOval(x - 11f, ground - 66f, x + 11f, ground - 44f, paint)
        face(canvas, paint, x, ground - 55f, 6.4f, 7.6f, Color.rgb(214, 199, 182), Color.rgb(30, 26, 34))
        // A scarf pulled up over the chin.
        fill(paint, Color.rgb(122, 84, 62))
        canvas.drawRoundRect(x - 8f, ground - 50f, x + 8f, ground - 44f, 3f, 3f, paint)

        // The lamp on its hook: the warmest thing on the surface of this world.
        val swing = sin(time * 2.2f) * 5f
        val lx = x + 20f + swing * 0.4f
        val ly = ground - 30f + abs(swing) * 0.2f
        stroke(paint, Color.rgb(96, 88, 78), 1.8f)
        canvas.drawLine(x + 10f, ground - 44f, lx, ly - 11f, paint)
        fill(paint, Color.argb(46, Color.red(accent), Color.green(accent), Color.blue(accent)))
        canvas.drawCircle(lx, ly, 16f + sin(time * 5f) * 1.4f, paint)
        fill(paint, Color.rgb(58, 52, 46))
        canvas.drawRoundRect(lx - 6f, ly - 10f, lx + 6f, ly + 8f, 2f, 2f, paint)
        fill(paint, Color.argb(235, Color.red(accent), Color.green(accent), Color.blue(accent)))
        canvas.drawRoundRect(lx - 4f, ly - 7f, lx + 4f, ly + 5f, 1.6f, 1.6f, paint)
        fill(paint, Color.argb(255, 255, 244, 210))
        canvas.drawOval(lx - 1.6f, ly - 4f + sin(time * 7f) * 0.8f, lx + 1.6f, ly + 1f, paint)
    }

    /** Quill: small, inked, and carrying more map than person. */
    private fun cartographer(canvas: Canvas, paint: Paint, x: Float, ground: Float, time: Float, accent: Int) {
        val sway = sin(time * 2.4f) * 1.2f
        // The map case on the back, taller than the apprentice.
        fill(paint, Color.rgb(88, 74, 58))
        canvas.save()
        canvas.rotate(-12f, x - 12f, ground - 30f)
        canvas.drawRoundRect(x - 18f, ground - 62f, x - 8f, ground - 8f, 4f, 4f, paint)
        stroke(paint, Color.argb(190, Color.red(accent), Color.green(accent), Color.blue(accent)), 1.4f)
        canvas.drawLine(x - 17f, ground - 54f, x - 9f, ground - 54f, paint)
        canvas.drawLine(x - 17f, ground - 18f, x - 9f, ground - 18f, paint)
        canvas.restore()

        robe(canvas, paint, x, ground, ground - 40f, 10f, 13f, sway, Color.rgb(58, 66, 62), Color.argb(160, 120, 140, 124))
        // An apron that has taken more ink than the page has.
        fill(paint, Color.rgb(40, 44, 48))
        canvas.drawRoundRect(x - 8f, ground - 26f, x + 8f, ground - 4f, 3f, 3f, paint)
        fill(paint, Color.argb(200, 26, 30, 44))
        canvas.drawCircle(x - 3f, ground - 18f, 2.2f, paint)
        canvas.drawCircle(x + 4f, ground - 12f, 1.6f, paint)
        canvas.drawCircle(x + 1f, ground - 8f, 1.2f, paint)

        face(canvas, paint, x, ground - 45f, 7f, 8f, Color.rgb(222, 208, 190), Color.rgb(28, 30, 36))
        // Spectacles, and a quill that has never been anywhere.
        stroke(paint, Color.argb(220, 210, 220, 230), 1.2f)
        canvas.drawCircle(x - 3.4f, ground - 45f, 3.1f, paint)
        canvas.drawCircle(x + 3.4f, ground - 45f, 3.1f, paint)
        canvas.drawLine(x - 0.3f, ground - 45f, x + 0.3f, ground - 45f, paint)
        stroke(paint, Color.rgb(228, 224, 210), 1.6f)
        canvas.drawLine(x + 7f, ground - 50f, x + 13f, ground - 62f, paint)
        fill(paint, Color.argb(190, Color.red(accent), Color.green(accent), Color.blue(accent)))
        canvas.drawCircle(x + 13f, ground - 62f, 1.8f, paint)
    }

    /** Sister Vell: hooded, veiled, holding a bell she rings for nobody. */
    private fun sister(canvas: Canvas, paint: Paint, x: Float, ground: Float, time: Float, accent: Int) {
        val sway = sin(time * 1.2f) * 1.1f
        robe(canvas, paint, x, ground, ground - 50f, 12f, 16f, sway, Color.rgb(42, 40, 56), Color.argb(170, Color.red(accent), Color.green(accent), Color.blue(accent)))

        // Hood and the dark inside it.
        fill(paint, Color.rgb(52, 49, 68))
        scratch.reset()
        scratch.moveTo(x - 12f, ground - 46f)
        scratch.cubicTo(x - 13f, ground - 66f, x + 13f, ground - 66f, x + 12f, ground - 46f)
        scratch.close()
        canvas.drawPath(scratch, paint)
        fill(paint, Color.rgb(16, 15, 24))
        canvas.drawOval(x - 7.4f, ground - 60f, x + 7.4f, ground - 46f, paint)
        // Two patient points of light, where eyes would be.
        fill(paint, Color.argb(200, Color.red(accent), Color.green(accent), Color.blue(accent)))
        canvas.drawCircle(x - 3f, ground - 54f, 1.5f, paint)
        canvas.drawCircle(x + 3f, ground - 54f, 1.5f, paint)

        // Hands together, and the bell between them.
        val bellY = ground - 32f + sin(time * 1.9f) * 0.9f
        fill(paint, Color.rgb(206, 192, 168))
        canvas.drawOval(x - 6f, bellY - 3f, x - 1f, bellY + 3f, paint)
        canvas.drawOval(x + 1f, bellY - 3f, x + 6f, bellY + 3f, paint)
        fill(paint, Color.argb(235, Color.red(accent), Color.green(accent), Color.blue(accent)))
        scratch.reset()
        scratch.moveTo(x - 5f, bellY + 6f)
        scratch.cubicTo(x - 5f, bellY - 6f, x + 5f, bellY - 6f, x + 5f, bellY + 6f)
        scratch.close()
        canvas.drawPath(scratch, paint)
        fill(paint, Color.rgb(238, 228, 206))
        canvas.drawCircle(x, bellY + 6f, 1.6f, paint)

        // Motes drifting up off the ground: the graves are breathing.
        for (i in 0..2) {
            val w = (time * 0.35f + i * 0.33f) % 1f
            fill(paint, Color.argb((70 * (1f - w)).toInt().coerceIn(0, 255), Color.red(accent), Color.green(accent), Color.blue(accent)))
            canvas.drawCircle(x - 16f + i * 15f + sin(time + i) * 3f, ground - 4f - w * 34f, 1.4f + 1.2f * w, paint)
        }
    }

    /** The Facet Warden: mineral, faceted, and turning very slowly toward the player. */
    private fun warden(canvas: Canvas, paint: Paint, x: Float, ground: Float, time: Float, accent: Int) {
        val turn = sin(time * 0.7f) * 2.4f
        val top = ground - 58f
        fill(paint, Color.argb(38, Color.red(accent), Color.green(accent), Color.blue(accent)))
        canvas.drawCircle(x, ground - 34f, 30f, paint)

        // The body is one faceted column, drawn as four planes that catch different light.
        val planes = intArrayOf(
            Color.rgb(64, 44, 68), Color.rgb(86, 58, 88), Color.rgb(52, 36, 58), Color.rgb(104, 70, 104)
        )
        val edges = floatArrayOf(-15f, -5f, 5f, 15f)
        for (i in 0..2) {
            fill(paint, planes[i])
            scratch.reset()
            scratch.moveTo(x + edges[i] + turn, top)
            scratch.lineTo(x + edges[i + 1] + turn, top)
            scratch.lineTo(x + edges[i + 1] * 1.25f + turn, ground - 2f)
            scratch.lineTo(x + edges[i] * 1.25f + turn, ground - 2f)
            scratch.close()
            canvas.drawPath(scratch, paint)
        }
        fill(paint, planes[3])
        scratch.reset()
        scratch.moveTo(x + edges[3] + turn, top)
        scratch.lineTo(x + edges[3] * 0.4f + turn, top - 12f)
        scratch.lineTo(x + edges[2] + turn, top)
        scratch.lineTo(x + edges[2] * 1.25f + turn, ground - 2f)
        scratch.lineTo(x + edges[3] * 1.25f + turn, ground - 2f)
        scratch.close()
        canvas.drawPath(scratch, paint)

        // Seams that glow, and the two of them that read as a face.
        stroke(paint, Color.argb((150 + sin(time * 2.6f) * 60f).toInt().coerceIn(0, 255), Color.red(accent), Color.green(accent), Color.blue(accent)), 1.6f)
        canvas.drawLine(x + edges[1] + turn, top, x + edges[1] * 1.25f + turn, ground - 2f, paint)
        canvas.drawLine(x + edges[2] + turn, top, x + edges[2] * 1.25f + turn, ground - 2f, paint)
        canvas.drawLine(x - 14f + turn, top + 18f, x + 14f + turn, top + 18f, paint)
        fill(paint, Color.argb(235, 255, 236, 250))
        canvas.drawOval(x - 7f + turn, top + 6f, x - 3f + turn, top + 13f, paint)
        canvas.drawOval(x + 3f + turn, top + 6f, x + 7f + turn, top + 13f, paint)

        // A shard in orbit above the head, which is the only part of it that is in a hurry.
        val a = time * 1.4f
        val sx = x + cos(a) * 15f
        val sy = top - 20f + sin(a) * 4f
        fill(paint, Color.argb(225, Color.red(accent), Color.green(accent), Color.blue(accent)))
        scratch.reset()
        scratch.moveTo(sx, sy - 7f); scratch.lineTo(sx + 4f, sy); scratch.lineTo(sx, sy + 7f); scratch.lineTo(sx - 4f, sy)
        scratch.close()
        canvas.drawPath(scratch, paint)
    }

    /** Gristle: broad, wet, and carrying the key to something that should stay shut. */
    private fun sluiceKeeper(canvas: Canvas, paint: Paint, x: Float, ground: Float, time: Float, accent: Int) {
        val sway = sin(time * 1.1f) * 1.4f
        // Slicker: wide shoulders, no waist to speak of.
        fill(paint, Color.rgb(44, 58, 54))
        scratch.reset()
        scratch.moveTo(x - 15f, ground - 46f)
        scratch.lineTo(x + 15f, ground - 46f)
        scratch.lineTo(x + 18f + sway, ground - 6f)
        scratch.lineTo(x - 18f + sway, ground - 6f)
        scratch.close()
        canvas.drawPath(scratch, paint)
        // Wet sheen down one side, and a hem that has never been dry.
        fill(paint, Color.argb(70, 190, 220, 210))
        scratch.reset()
        scratch.moveTo(x - 15f, ground - 46f)
        scratch.lineTo(x - 8f, ground - 46f)
        scratch.lineTo(x - 10f + sway, ground - 6f)
        scratch.lineTo(x - 18f + sway, ground - 6f)
        scratch.close()
        canvas.drawPath(scratch, paint)
        fill(paint, Color.rgb(28, 40, 38))
        canvas.drawRect(x - 18f + sway, ground - 10f, x + 18f + sway, ground - 5f, paint)

        // Boots.
        fill(paint, Color.rgb(34, 30, 30))
        canvas.drawRoundRect(x - 13f, ground - 6f, x - 3f, ground + 1f, 2f, 2f, paint)
        canvas.drawRoundRect(x + 3f, ground - 6f, x + 13f, ground + 1f, 2f, 2f, paint)

        // Head: bare, bald, and unimpressed.
        face(canvas, paint, x, ground - 54f, 8f, 8.6f, Color.rgb(176, 168, 150), Color.rgb(26, 30, 30))
        stroke(paint, Color.rgb(120, 112, 98), 1.4f)
        canvas.drawLine(x - 5f, ground - 58f, x + 5f, ground - 58f, paint)

        // The wheel-key over the shoulder: a circle with four spokes, dripping.
        val kx = x + 20f
        val ky = ground - 46f
        stroke(paint, Color.rgb(122, 112, 96), 2.4f)
        canvas.drawLine(x + 8f, ground - 40f, kx, ky, paint)
        stroke(paint, Color.argb(230, Color.red(accent), Color.green(accent), Color.blue(accent)), 2.2f)
        canvas.drawCircle(kx, ky, 9f, paint)
        paint.strokeWidth = 1.6f
        canvas.drawLine(kx - 9f, ky, kx + 9f, ky, paint)
        canvas.drawLine(kx, ky - 9f, kx, ky + 9f, paint)
        // Drips off the key, on a clock of their own.
        for (i in 0..1) {
            val w = (time * 0.7f + i * 0.5f) % 1f
            fill(paint, Color.argb((150 * (1f - w)).toInt().coerceIn(0, 255), 150, 200, 190))
            canvas.drawOval(kx - 1.4f, ky + 9f + w * 22f, kx + 1.4f, ky + 13f + w * 22f, paint)
        }
    }

    /** The Pale Scribe: the tallest thing in the kingdom, and the last one keeping records. */
    private fun paleScribe(canvas: Canvas, paint: Paint, x: Float, ground: Float, time: Float, accent: Int) {
        val hover = sin(time * 1.1f) * 3f
        val hem = ground - 6f + hover
        fill(paint, Color.argb(30, Color.red(accent), Color.green(accent), Color.blue(accent)))
        canvas.drawCircle(x, hem - 40f, 34f, paint)

        // A long pale robe that ends without ever quite reaching the floor.
        fill(paint, Color.argb(238, 226, 224, 214))
        scratch.reset()
        scratch.moveTo(x - 11f, hem - 58f)
        scratch.cubicTo(x - 15f, hem - 34f, x - 12f, hem - 12f, x - 9f, hem)
        scratch.lineTo(x + 9f, hem)
        scratch.cubicTo(x + 12f, hem - 12f, x + 15f, hem - 34f, x + 11f, hem - 58f)
        scratch.close()
        canvas.drawPath(scratch, paint)
        stroke(paint, Color.argb(120, 150, 140, 120), 1.2f)
        canvas.drawLine(x, hem - 56f, x, hem - 4f, paint)

        // The mask: long, blank, and hollow where the eyes go.
        fill(paint, Color.rgb(242, 240, 232))
        canvas.drawOval(x - 9f, hem - 78f, x + 9f, hem - 54f, paint)
        fill(paint, Color.rgb(14, 15, 20))
        canvas.drawOval(x - 5f, hem - 72f, x - 1.6f, hem - 62f, paint)
        canvas.drawOval(x + 1.6f, hem - 72f, x + 5f, hem - 62f, paint)
        // The crown of the mask, split, like every other face down here.
        fill(paint, Color.rgb(242, 240, 232))
        scratch.reset()
        scratch.moveTo(x - 8f, hem - 74f); scratch.lineTo(x - 12f, hem - 88f); scratch.lineTo(x - 3f, hem - 77f); scratch.close()
        scratch.moveTo(x + 8f, hem - 74f); scratch.lineTo(x + 12f, hem - 88f); scratch.lineTo(x + 3f, hem - 77f); scratch.close()
        canvas.drawPath(scratch, paint)

        // The ledger, and the light that reads it: not a lamp, and not a flame.
        fill(paint, Color.rgb(58, 52, 44))
        canvas.save()
        canvas.rotate(-8f, x - 16f, hem - 34f)
        canvas.drawRoundRect(x - 24f, hem - 44f, x - 10f, hem - 26f, 2f, 2f, paint)
        fill(paint, Color.argb(225, 240, 234, 214))
        canvas.drawRect(x - 22f, hem - 42f, x - 12f, hem - 28f, paint)
        canvas.restore()
        val pulse = 0.65f + 0.35f * sin(time * 3.1f)
        fill(paint, Color.argb((120 * pulse).toInt().coerceIn(0, 255), 255, 248, 220))
        canvas.drawCircle(x - 17f, hem - 52f, 9f + 2f * pulse, paint)
        fill(paint, Color.argb(255, 255, 252, 238))
        canvas.drawCircle(x - 17f, hem - 52f, 3.2f, paint)
    }
}
