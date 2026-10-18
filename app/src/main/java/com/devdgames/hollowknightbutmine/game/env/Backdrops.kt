package com.devdgames.hollowknightbutmine.game.env

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.devdgames.hollowknightbutmine.game.world.Biome
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * Sky, parallax silhouettes, weather and darkness for every biome.
 *
 * Parallax and weather are drawn as an infinite grid of seeded cells: for a cell index
 * (i, j) the same pseudo-random content is produced every frame, so the layers are
 * stable, allocation free (bar one Random per visible cell) and cover any world size.
 * All coordinates are design units: the caller has already applied the zoom.
 */
object Backdrops {

    private const val HORIZON_WORLD_Y = 13 * 40f       // the surface floor row
    private const val HORIZON_CAM_Y = 100f             // typical camera top when standing on it

    private val path = Path()
    private val rect = RectF()
    private var skyShader: Shader? = null
    private var skyKey = 0
    private var skyH = 0f

    private val darkShader = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(200, 0, 0, 0), Color.BLACK),
        floatArrayOf(0f, 0.3f, 0.75f, 1f), Shader.TileMode.CLAMP
    )

    // ------------------------------------------------------------------ sky

    fun drawSky(canvas: Canvas, paint: Paint, pal: BiomePalette, viewW: Float, viewH: Float, camX: Float, camY: Float, openSky: Boolean, t: Float) {
        val key = pal.skyTop * 31 + pal.skyBottom
        if (skyShader == null || skyKey != key || skyH != viewH) {
            skyShader = LinearGradient(0f, 0f, 0f, viewH, intArrayOf(pal.skyTop, pal.skyBottom), null, Shader.TileMode.CLAMP)
            skyKey = key
            skyH = viewH
        }
        paint.style = Paint.Style.FILL
        paint.shader = skyShader
        paint.alpha = 255
        canvas.drawRect(0f, 0f, viewW, viewH, paint)
        paint.shader = null

        if (openSky) {
            // Moon and a scatter of fixed stars that barely move with the camera.
            val ox = camX * 0.02f
            val oy = camY * 0.02f
            val rnd = Random(991)
            paint.color = Color.argb(170, 230, 236, 255)
            for (i in 0 until 90) {
                val sx = ((rnd.nextFloat() * 2400f - ox) % 2400f + 2400f) % 2400f - 400f
                val sy = rnd.nextFloat() * viewH * 0.7f - oy
                val tw = 0.6f + 0.4f * sin(t * (1f + rnd.nextFloat() * 2f) + i)
                if (sx in -4f..viewW + 4f && sy > -4f) canvas.drawCircle(sx, sy, (0.8f + rnd.nextFloat() * 1.3f) * tw, paint)
            }
            val mx = viewW * 0.74f - ox * 0.5f
            val my = viewH * 0.16f - oy * 0.5f
            EnvArt.bloom(canvas, paint, mx, my, 220f, 220f, EnvArt.glowTeal, 60)
            paint.color = Color.rgb(226, 232, 244)
            canvas.drawCircle(mx, my, 34f, paint)
            paint.color = pal.skyTop
            canvas.drawCircle(mx + 14f, my - 8f, 30f, paint)
        }
    }

    // ------------------------------------------------------------------ parallax

    fun drawParallax(canvas: Canvas, paint: Paint, biome: Biome, pal: BiomePalette, camX: Float, camY: Float, viewW: Float, viewH: Float, t: Float) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        when (biome) {
            Biome.TOWN, Biome.CLIFFS -> surface(canvas, paint, biome, pal, camX, camY, viewW, viewH)
            Biome.CROSSROADS -> {
                cells(canvas, camX, camY, 0.18f, viewW, viewH, 600f, 700f, 11) { r, x, y -> pillars(canvas, paint, r, x, y, 600f, 700f, pal.far) }
                cells(canvas, camX, camY, 0.42f, viewW, viewH, 520f, 520f, 12) { r, x, y -> chainsAndRubble(canvas, paint, r, x, y, 520f, 520f, pal.mid) }
            }
            Biome.GREEN -> {
                cells(canvas, camX, camY, 0.16f, viewW, viewH, 620f, 760f, 21) { r, x, y -> roots(canvas, paint, r, x, y, 620f, 760f, pal.far) }
                cells(canvas, camX, camY, 0.4f, viewW, viewH, 520f, 520f, 22) { r, x, y -> ferns(canvas, paint, r, x, y, 520f, 520f, pal.mid, pal.glow) }
            }
            Biome.FUNGAL -> {
                cells(canvas, camX, camY, 0.16f, viewW, viewH, 640f, 720f, 31) { r, x, y -> bigShrooms(canvas, paint, r, x, y, 640f, 720f, pal.far, 1.6f) }
                cells(canvas, camX, camY, 0.4f, viewW, viewH, 520f, 520f, 32) { r, x, y -> bigShrooms(canvas, paint, r, x, y, 520f, 520f, pal.mid, 0.8f) }
            }
            Biome.CITY -> {
                cells(canvas, camX, camY, 0.14f, viewW, viewH, 520f, 900f, 41) { r, x, y -> towers(canvas, paint, r, x, y, 520f, 900f, pal.far, 90) }
                cells(canvas, camX, camY, 0.36f, viewW, viewH, 560f, 760f, 42) { r, x, y -> towers(canvas, paint, r, x, y, 560f, 760f, pal.mid, 60) }
            }
            Biome.CRYSTAL -> {
                cells(canvas, camX, camY, 0.16f, viewW, viewH, 600f, 720f, 51) { r, x, y -> spires(canvas, paint, r, x, y, 600f, 720f, pal.far, pal.glow, 1.7f) }
                cells(canvas, camX, camY, 0.4f, viewW, viewH, 500f, 500f, 52) { r, x, y -> spires(canvas, paint, r, x, y, 500f, 500f, pal.mid, pal.glow, 0.9f) }
            }
            Biome.RESTING -> {
                cells(canvas, camX, camY, 0.18f, viewW, viewH, 640f, 700f, 61) { r, x, y -> crypt(canvas, paint, r, x, y, 640f, 700f, pal.far) }
                cells(canvas, camX, camY, 0.42f, viewW, viewH, 520f, 520f, 62) { r, x, y -> chainsAndRubble(canvas, paint, r, x, y, 520f, 520f, pal.mid) }
            }
            Biome.EDGE -> {
                cells(canvas, camX, camY, 0.14f, viewW, viewH, 700f, 800f, 71) { r, x, y -> crags(canvas, paint, r, x, y, 700f, 800f, pal.far, 1.4f) }
                cells(canvas, camX, camY, 0.36f, viewW, viewH, 560f, 600f, 72) { r, x, y -> crags(canvas, paint, r, x, y, 560f, 600f, pal.mid, 0.8f) }
            }
            Biome.WATERWAYS -> {
                cells(canvas, camX, camY, 0.18f, viewW, viewH, 640f, 640f, 81) { r, x, y -> pipes(canvas, paint, r, x, y, 640f, 640f, pal.far) }
                cells(canvas, camX, camY, 0.42f, viewW, viewH, 520f, 520f, 82) { r, x, y -> pipes(canvas, paint, r, x, y, 520f, 520f, pal.mid) }
            }
            Biome.DEEPNEST -> {
                cells(canvas, camX, camY, 0.2f, viewW, viewH, 600f, 600f, 91) { r, x, y -> webs(canvas, paint, r, x, y, 600f, 600f, pal.far) }
                cells(canvas, camX, camY, 0.45f, viewW, viewH, 480f, 480f, 92) { r, x, y -> webs(canvas, paint, r, x, y, 480f, 480f, pal.mid) }
            }
            Biome.BASIN -> {
                cells(canvas, camX, camY, 0.16f, viewW, viewH, 760f, 760f, 101) { r, x, y -> ribs(canvas, paint, r, x, y, 760f, 760f, pal.far, 16f) }
                cells(canvas, camX, camY, 0.4f, viewW, viewH, 540f, 540f, 102) { r, x, y -> ribs(canvas, paint, r, x, y, 540f, 540f, pal.mid, 8f) }
            }
            Biome.ABYSS -> {
                cells(canvas, camX, camY, 0.3f, viewW, viewH, 600f, 600f, 111) { r, x, y -> voidTendrils(canvas, paint, r, x, y, 600f, 600f, t) }
            }
        }
        paint.shader = null
        paint.alpha = 255
    }

    private inline fun cells(
        canvas: Canvas, camX: Float, camY: Float, f: Float, viewW: Float, viewH: Float,
        cw: Float, ch: Float, seed: Int, block: (Random, Float, Float) -> Unit
    ) {
        val ox = camX * f
        val oy = camY * f
        canvas.save()
        canvas.translate(-ox, -oy)
        val i0 = floor(ox / cw).toInt() - 1
        val i1 = floor((ox + viewW) / cw).toInt() + 1
        val j0 = floor(oy / ch).toInt() - 1
        val j1 = floor((oy + viewH) / ch).toInt() + 1
        for (j in j0..j1) for (i in i0..i1) {
            block(Random(seed * 1_000_003 + i * 73_856_093 + j * 19_349_663), i * cw, j * ch)
        }
        canvas.restore()
    }

    // ---- surface (Dustmere + the Howling Cliffs)

    private fun surface(canvas: Canvas, paint: Paint, biome: Biome, pal: BiomePalette, camX: Float, camY: Float, viewW: Float, viewH: Float) {
        // far mountains
        horizonBand(canvas, camX, camY, 0.12f, viewW) { r, x ->
            paint.color = pal.far
            var px = x
            while (px < x + 700f) {
                val w = 180f + r.nextFloat() * 220f
                val h = 140f + r.nextFloat() * 220f
                path.reset()
                path.moveTo(px - w, 40f)
                path.lineTo(px + w * 0.1f, -h)
                path.lineTo(px + w * 0.35f, -h * 0.75f)
                path.lineTo(px + w, 40f)
                path.close()
                canvas.drawPath(path, paint)
                px += w * 0.9f
            }
        }
        // mid: hills with rooftops (town) or dead trees (cliffs)
        horizonBand(canvas, camX, camY, 0.3f, viewW) { r, x ->
            paint.color = pal.mid
            path.reset()
            path.moveTo(x, 60f)
            var px = x
            while (px <= x + 700f) { path.lineTo(px, -20f - r.nextFloat() * 50f); px += 140f }
            path.lineTo(x + 700f, 60f)
            path.close()
            canvas.drawPath(path, paint)
            for (k in 0 until 4) {
                val bx = x + 60f + k * 170f + r.nextFloat() * 50f
                if (biome == Biome.TOWN) {
                    val w = 40f + r.nextFloat() * 50f
                    val h = 40f + r.nextFloat() * 60f
                    canvas.drawRect(bx - w / 2, -h, bx + w / 2, 10f, paint)
                    path.reset(); path.moveTo(bx - w / 2 - 6f, -h); path.lineTo(bx, -h - 30f - r.nextFloat() * 30f); path.lineTo(bx + w / 2 + 6f, -h); path.close()
                    canvas.drawPath(path, paint)
                    if (r.nextFloat() < 0.5f) {
                        paint.color = Color.argb(150, 255, 200, 120)
                        canvas.drawRect(bx - 4f, -h * 0.6f, bx + 4f, -h * 0.6f + 9f, paint)
                        paint.color = pal.mid
                    }
                } else {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 4f
                    val h = 60f + r.nextFloat() * 70f
                    canvas.drawLine(bx, 10f, bx + 4f, -h, paint)
                    paint.strokeWidth = 2f
                    canvas.drawLine(bx + 2f, -h * 0.5f, bx - 24f, -h * 0.8f, paint)
                    canvas.drawLine(bx + 3f, -h * 0.7f, bx + 26f, -h * 0.95f, paint)
                    paint.style = Paint.Style.FILL
                }
            }
        }
    }

    private inline fun horizonBand(canvas: Canvas, camX: Float, camY: Float, f: Float, viewW: Float, block: (Random, Float) -> Unit) {
        val ox = camX * f
        val horizonY = (HORIZON_WORLD_Y - HORIZON_CAM_Y) + (HORIZON_CAM_Y - camY) * f
        canvas.save()
        canvas.translate(-ox, horizonY)
        val i0 = floor(ox / 700f).toInt() - 1
        val i1 = floor((ox + viewW) / 700f).toInt() + 1
        for (i in i0..i1) block(Random(5000 + i * 7919 + (f * 1000).toInt()), i * 700f)
        canvas.restore()
    }

    // ---- underground cell painters

    private fun pillars(canvas: Canvas, paint: Paint, r: Random, x: Float, y: Float, cw: Float, ch: Float, color: Int) {
        paint.color = color
        val n = 1 + r.nextInt(2)
        for (k in 0 until n) {
            val px = x + r.nextFloat() * cw
            val w = 44f + r.nextFloat() * 50f
            canvas.drawRect(px - w / 2, y - 10f, px + w / 2, y + ch + 10f, paint)
            // capital
            canvas.drawRect(px - w * 0.8f, y + ch * r.nextFloat() * 0.3f, px + w * 0.8f, y + ch * 0.3f + 18f, paint)
        }
        // arch across the cell
        if (r.nextFloat() < 0.6f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 26f
            val ax = x + r.nextFloat() * cw
            val ay = y + r.nextFloat() * ch
            rect.set(ax - 160f, ay - 120f, ax + 160f, ay + 120f)
            canvas.drawArc(rect, 200f, 140f, false, paint)
            paint.style = Paint.Style.FILL
        }
    }

    private fun chainsAndRubble(canvas: Canvas, paint: Paint, r: Random, x: Float, y: Float, cw: Float, ch: Float, color: Int) {
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        for (k in 0 until 3) {
            val cx = x + r.nextFloat() * cw
            val len = 80f + r.nextFloat() * 240f
            canvas.drawLine(cx, y, cx, y + len, paint)
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(cx - 6f, y + len, cx + 6f, y + len + 16f, 3f, 3f, paint)
            if (r.nextFloat() < 0.35f) EnvArt.bloom(canvas, paint, cx, y + len + 8f, 40f, 40f, EnvArt.glowWarm, 45)
            paint.color = color
            paint.style = Paint.Style.STROKE
        }
        paint.style = Paint.Style.FILL
        for (k in 0 until 3) {
            val mx = x + r.nextFloat() * cw
            val my = y + r.nextFloat() * ch
            val w = 20f + r.nextFloat() * 40f
            canvas.drawOval(mx - w, my - w * 0.4f, mx + w, my + w * 0.4f, paint)
        }
    }

    private fun roots(canvas: Canvas, paint: Paint, r: Random, x: Float, y: Float, cw: Float, ch: Float, color: Int) {
        paint.color = color
        path.reset()
        for (k in 0 until 2) {
            val rx = x + r.nextFloat() * cw
            EnvArt.tapered(path, rx, y - 20f, rx + (r.nextFloat() - 0.5f) * 120f, y + ch * 0.5f, rx + (r.nextFloat() - 0.5f) * 160f, y + ch + 20f, 30f + r.nextFloat() * 30f, 10f, 8)
        }
        canvas.drawPath(path, paint)
        for (k in 0 until 3) {
            val lx = x + r.nextFloat() * cw
            val ly = y + r.nextFloat() * ch
            val w = 50f + r.nextFloat() * 70f
            canvas.save()
            canvas.rotate(-30f + r.nextFloat() * 60f, lx, ly)
            canvas.drawOval(lx - w, ly - w * 0.35f, lx + w, ly + w * 0.35f, paint)
            canvas.restore()
        }
    }

    private fun ferns(canvas: Canvas, paint: Paint, r: Random, x: Float, y: Float, cw: Float, ch: Float, color: Int, glow: Int) {
        paint.color = color
        path.reset()
        for (k in 0 until 4) {
            val vx = x + r.nextFloat() * cw
            val len = 60f + r.nextFloat() * 200f
            EnvArt.tapered(path, vx, y, vx + (r.nextFloat() - 0.5f) * 40f, y + len * 0.5f, vx + (r.nextFloat() - 0.5f) * 60f, y + len, 8f, 1f, 6)
        }
        for (k in 0 until 2) {
            val fx = x + r.nextFloat() * cw
            val fy = y + r.nextFloat() * ch
            for (b in 0 until 5) {
                val a = -2.2f + b * 0.55f
                EnvArt.tapered(path, fx, fy, fx + cos(a) * 40f, fy + sin(a) * 40f, fx + cos(a) * 80f, fy + sin(a) * 80f, 10f, 1f, 5)
            }
        }
        canvas.drawPath(path, paint)
        for (k in 0 until 2) {
            if (r.nextFloat() < 0.5f) EnvArt.bloom(canvas, paint, x + r.nextFloat() * cw, y + r.nextFloat() * ch, 50f, 50f, EnvArt.glowTeal, 40)
        }
    }

    private fun bigShrooms(canvas: Canvas, paint: Paint, r: Random, x: Float, y: Float, cw: Float, ch: Float, color: Int, s: Float) {
        paint.color = color
        for (k in 0 until 2) {
            val mx = x + r.nextFloat() * cw
            val base = y + ch * (0.5f + r.nextFloat() * 0.5f)
            val h = (160f + r.nextFloat() * 220f) * s
            val capW = (90f + r.nextFloat() * 90f) * s
            canvas.drawRect(mx - 12f * s, base - h, mx + 12f * s, base + 40f, paint)
            rect.set(mx - capW, base - h - capW * 0.6f, mx + capW, base - h + capW * 0.3f)
            canvas.drawArc(rect, 180f, 180f, true, paint)
            if (r.nextFloat() < 0.5f) EnvArt.bloom(canvas, paint, mx, base - h - 10f, capW * 0.7f, 30f, EnvArt.glowWarm, 30)
        }
    }

    private fun towers(canvas: Canvas, paint: Paint, r: Random, x: Float, y: Float, cw: Float, ch: Float, color: Int, windowAlpha: Int) {
        val n = 1 + r.nextInt(2)
        for (k in 0 until n) {
            val tx = x + r.nextFloat() * cw
            val w = 70f + r.nextFloat() * 80f
            val top = y + r.nextFloat() * ch * 0.5f
            paint.color = color
            canvas.drawRect(tx - w / 2, top, tx + w / 2, y + ch + 20f, paint)
            path.reset()
            path.moveTo(tx - w / 2 - 8f, top + 4f)
            path.lineTo(tx, top - 60f - r.nextFloat() * 50f)
            path.lineTo(tx + w / 2 + 8f, top + 4f)
            path.close()
            canvas.drawPath(path, paint)
            paint.color = Color.argb(windowAlpha, 255, 214, 150)
            var wy = top + 30f
            while (wy < y + ch) {
                var wx = tx - w / 2 + 14f
                while (wx < tx + w / 2 - 10f) {
                    if (r.nextFloat() < 0.3f) canvas.drawRect(wx, wy, wx + 7f, wy + 14f, paint)
                    wx += 26f
                }
                wy += 64f
            }
        }
    }

    private fun spires(canvas: Canvas, paint: Paint, r: Random, x: Float, y: Float, cw: Float, ch: Float, color: Int, glow: Int, s: Float) {
        paint.color = color
        for (k in 0 until 3) {
            val sx = x + r.nextFloat() * cw
            val sy = y + r.nextFloat() * ch
            val h = (80f + r.nextFloat() * 160f) * s
            val w = (16f + r.nextFloat() * 24f) * s
            val up = r.nextBoolean()
            path.reset()
            path.moveTo(sx - w, sy)
            path.lineTo(sx + (r.nextFloat() - 0.5f) * w, if (up) sy - h else sy + h)
            path.lineTo(sx + w, sy)
            path.close()
            canvas.drawPath(path, paint)
            if (r.nextFloat() < 0.4f) {
                EnvArt.bloom(canvas, paint, sx, if (up) sy - h * 0.5f else sy + h * 0.5f, w * 3f, h * 0.6f, EnvArt.glowTeal, 30)
                paint.color = color
            }
        }
    }

    private fun crypt(canvas: Canvas, paint: Paint, r: Random, x: Float, y: Float, cw: Float, ch: Float, color: Int) {
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 18f
        for (k in 0 until 2) {
            val ax = x + r.nextFloat() * cw
            val ay = y + r.nextFloat() * ch
            val w = 90f + r.nextFloat() * 80f
            rect.set(ax - w, ay - w, ax + w, ay + w)
            canvas.drawArc(rect, 180f, 180f, false, paint)
            canvas.drawLine(ax - w, ay, ax - w, ay + 220f, paint)
            canvas.drawLine(ax + w, ay, ax + w, ay + 220f, paint)
        }
        paint.style = Paint.Style.FILL
        for (k in 0 until 3) {
            val gx = x + r.nextFloat() * cw
            val gy = y + r.nextFloat() * ch
            canvas.drawRoundRect(gx - 14f, gy - 40f, gx + 14f, gy, 12f, 12f, paint)
        }
    }

    private fun crags(canvas: Canvas, paint: Paint, r: Random, x: Float, y: Float, cw: Float, ch: Float, color: Int, s: Float) {
        paint.color = color
        for (k in 0 until 2) {
            val cx = x + r.nextFloat() * cw
            val cy = y + r.nextFloat() * ch
            path.reset()
            path.moveTo(cx - 120f * s, cy + 200f * s)
            path.lineTo(cx - 60f * s, cy - 40f * s * r.nextFloat() - 60f)
            path.lineTo(cx - 10f * s, cy - 180f * s)
            path.lineTo(cx + 50f * s, cy - 60f * s)
            path.lineTo(cx + 130f * s, cy + 200f * s)
            path.close()
            canvas.drawPath(path, paint)
        }
    }

    private fun pipes(canvas: Canvas, paint: Paint, r: Random, x: Float, y: Float, cw: Float, ch: Float, color: Int) {
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 20f
        val py = y + r.nextFloat() * ch
        canvas.drawLine(x - 10f, py, x + cw + 10f, py, paint)
        val px = x + r.nextFloat() * cw
        val len = 100f + r.nextFloat() * 300f
        canvas.drawLine(px, py, px, py + len, paint)
        paint.strokeWidth = 30f
        for (k in 0 until 3) {
            val jx = x + k * cw / 3f + r.nextFloat() * 80f
            canvas.drawLine(jx, py, jx + 14f, py, paint)
        }
        paint.style = Paint.Style.FILL
        canvas.drawCircle(px, py + len, 22f, paint)
        if (r.nextFloat() < 0.4f) EnvArt.bloom(canvas, paint, px, py + len + 30f, 60f, 30f, EnvArt.glowTeal, 35)
    }

    private fun webs(canvas: Canvas, paint: Paint, r: Random, x: Float, y: Float, cw: Float, ch: Float, color: Int) {
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        val ax = x + r.nextFloat() * cw
        val ay = y + r.nextFloat() * ch
        for (k in 0 until 6) {
            val a = r.nextFloat() * 6.283f
            canvas.drawLine(ax, ay, ax + cos(a) * 320f, ay + sin(a) * 320f, paint)
        }
        for (k in 1..4) {
            rect.set(ax - k * 40f, ay - k * 40f, ax + k * 40f, ay + k * 40f)
            canvas.drawArc(rect, r.nextFloat() * 360f, 200f, false, paint)
        }
        paint.style = Paint.Style.FILL
        for (k in 0 until 2) {
            val cx = x + r.nextFloat() * cw
            val cy = y + r.nextFloat() * ch
            canvas.drawOval(cx - 16f, cy - 30f, cx + 16f, cy + 30f, paint)
        }
    }

    private fun ribs(canvas: Canvas, paint: Paint, r: Random, x: Float, y: Float, cw: Float, ch: Float, color: Int, w: Float) {
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = w
        paint.strokeCap = Paint.Cap.ROUND
        val cx = x + r.nextFloat() * cw
        val cy = y + r.nextFloat() * ch
        for (k in 0 until 4) {
            val rr = 120f + k * 60f
            rect.set(cx - rr, cy - rr * 1.3f, cx + rr, cy + rr * 1.3f)
            canvas.drawArc(rect, 200f + r.nextFloat() * 20f, 120f, false, paint)
        }
        paint.strokeCap = Paint.Cap.BUTT
        paint.style = Paint.Style.FILL
        for (k in 0 until 3) {
            val ex = x + r.nextFloat() * cw
            val ey = y + r.nextFloat() * ch
            canvas.drawOval(ex - 14f, ey - 20f, ex + 14f, ey + 20f, paint)
        }
    }

    private fun voidTendrils(canvas: Canvas, paint: Paint, r: Random, x: Float, y: Float, cw: Float, ch: Float, t: Float) {
        paint.color = Color.rgb(3, 3, 7)
        path.reset()
        for (k in 0 until 5) {
            val tx = x + r.nextFloat() * cw
            val sway = sin(t * 0.7f + tx * 0.01f) * 20f
            EnvArt.tapered(path, tx, y + ch + 30f, tx + sway, y + ch * 0.5f, tx + sway * 2f, y + ch * (0.1f + r.nextFloat() * 0.4f), 16f + r.nextFloat() * 16f, 1f, 7)
        }
        canvas.drawPath(path, paint)
        if (r.nextFloat() < 0.3f) EnvArt.bloom(canvas, paint, x + r.nextFloat() * cw, y + r.nextFloat() * ch, 90f, 90f, EnvArt.glowVoid, 50)
    }

    // ------------------------------------------------------------------ weather

    fun drawWeather(canvas: Canvas, paint: Paint, biome: Biome, pal: BiomePalette, camX: Float, camY: Float, viewW: Float, viewH: Float, t: Float) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        val cw = 480f
        cells(canvas, camX, camY, 1f, viewW, viewH, cw, cw, 777) { r, x, y ->
            when (biome) {
                Biome.TOWN, Biome.CLIFFS, Biome.EDGE -> {
                    // drifting ash
                    val n = if (biome == Biome.EDGE) 16 else 9
                    paint.color = Color.argb(if (biome == Biome.EDGE) 150 else 110, 220, 220, 228)
                    for (k in 0 until n) {
                        val sp = 26f + r.nextFloat() * 40f
                        val px = x + ((r.nextFloat() * cw + t * (if (biome == Biome.EDGE) 60f else 14f) + sin(t + k) * 8f) % cw + cw) % cw
                        val py = y + ((r.nextFloat() * cw + t * sp) % cw + cw) % cw
                        canvas.drawCircle(px, py, 1.2f + r.nextFloat() * 1.6f, paint)
                    }
                }
                Biome.CROSSROADS, Biome.BASIN, Biome.RESTING -> {
                    paint.color = Color.argb(70, 220, 224, 240)
                    for (k in 0 until 6) {
                        val px = x + ((r.nextFloat() * cw + sin(t * 0.4f + k) * 30f) % cw + cw) % cw
                        val py = y + ((r.nextFloat() * cw + t * 9f) % cw + cw) % cw
                        canvas.drawCircle(px, py, 1.3f, paint)
                    }
                }
                Biome.GREEN, Biome.FUNGAL -> {
                    val warm = biome == Biome.FUNGAL
                    for (k in 0 until 7) {
                        val px = x + ((r.nextFloat() * cw + sin(t * 0.6f + k * 1.7f) * 26f) % cw + cw) % cw
                        val py = y + ((r.nextFloat() * cw - t * (10f + r.nextFloat() * 16f)) % cw + cw) % cw
                        val pulse = 0.55f + 0.45f * sin(t * 2f + k * 2.1f)
                        EnvArt.bloom(canvas, paint, px, py, 9f, 9f, if (warm) EnvArt.glowWarm else EnvArt.glowTeal, (110 * pulse).toInt())
                        paint.color = if (warm) EnvPalette.sporeWarm else pal.glow
                        paint.alpha = (255 * pulse).toInt()
                        canvas.drawCircle(px, py, 1.6f, paint)
                    }
                }
                Biome.CITY -> {
                    paint.color = Color.argb(95, 170, 196, 240)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 1.2f
                    for (k in 0 until 26) {
                        val px = x + ((r.nextFloat() * cw - t * 90f) % cw + cw) % cw
                        val py = y + ((r.nextFloat() * cw + t * (520f + r.nextFloat() * 120f)) % cw + cw) % cw
                        canvas.drawLine(px, py, px - 3f, py + 16f, paint)
                    }
                    paint.style = Paint.Style.FILL
                }
                Biome.CRYSTAL -> {
                    for (k in 0 until 5) {
                        val px = x + r.nextFloat() * cw
                        val py = y + r.nextFloat() * cw
                        val s = sin(t * 3f + k * 2.3f + px * 0.01f)
                        if (s > 0.6f) {
                            paint.color = Color.WHITE
                            paint.alpha = ((s - 0.6f) * 600f).toInt().coerceAtMost(255)
                            canvas.drawCircle(px, py, 1.6f, paint)
                            canvas.drawLine(px - 6f, py, px + 6f, py, paint)
                            canvas.drawLine(px, py - 6f, px, py + 6f, paint)
                        }
                    }
                }
                Biome.WATERWAYS -> {
                    paint.color = Color.argb(60, 160, 220, 200)
                    for (k in 0 until 4) {
                        val px = x + ((r.nextFloat() * cw + t * 12f) % cw + cw) % cw
                        val py = y + r.nextFloat() * cw
                        canvas.drawOval(px - 40f, py - 8f, px + 40f, py + 8f, paint)
                    }
                }
                Biome.DEEPNEST -> { /* nothing but the dark */ }
                Biome.ABYSS -> {
                    paint.color = Color.argb(180, 14, 10, 24)
                    for (k in 0 until 10) {
                        val px = x + ((r.nextFloat() * cw + sin(t * 0.8f + k) * 10f) % cw + cw) % cw
                        val py = y + ((r.nextFloat() * cw - t * 28f) % cw + cw) % cw
                        canvas.drawCircle(px, py, 2f + r.nextFloat() * 2.5f, paint)
                    }
                }
            }
        }
        paint.alpha = 255
    }

    // ------------------------------------------------------------------ darkness

    /** Radial darkness centred on the player; [darkness] 0..1 is the area's setting. */
    fun drawDarkness(canvas: Canvas, paint: Paint, px: Float, py: Float, darkness: Float, viewW: Float, viewH: Float) {
        if (darkness <= 0.01f) return
        val radius = 620f - darkness * 300f
        paint.style = Paint.Style.FILL
        paint.shader = darkShader
        paint.alpha = (darkness * 255f).toInt().coerceIn(0, 255)
        canvas.save()
        canvas.translate(px, py)
        canvas.scale(radius, radius)
        val ex = (viewW / radius) + 1f
        val ey = (viewH / radius) + 1f
        canvas.drawRect(-ex, -ey, ex, ey, paint)
        canvas.restore()
        paint.shader = null
        paint.alpha = 255
    }
}
