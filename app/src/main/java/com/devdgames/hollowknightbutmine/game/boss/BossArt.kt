package com.devdgames.hollowknightbutmine.game.boss

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Canvas drawing for the bosses, arena gates and the hazards they leave behind. Flat shapes
 * in the same style as the rest of the game; every attack has a distinct wind-up cue and the
 * punish window (exposed) always glows warm gold, so the fight can be read without text.
 */
internal object BossArt {
    private val path = Path()
    private val rect = RectF()

    private val violetGlow: Shader = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(Color.argb(170, 190, 160, 255), Color.argb(70, 120, 90, 220), Color.argb(0, 60, 40, 140)),
        floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
    )
    private val pinkGlow: Shader = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(Color.argb(170, 255, 150, 235), Color.argb(70, 210, 90, 200), Color.argb(0, 120, 40, 130)),
        floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
    )
    private val goldGlow: Shader = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(Color.argb(190, 255, 232, 150), Color.argb(80, 255, 190, 90), Color.argb(0, 220, 130, 40)),
        floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
    )

    private fun c(a: Float, r: Int, g: Int, b: Int, vis: Float = 1f): Int =
        Color.argb((a * vis).toInt().coerceIn(0, 255), r, g, b)

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
    private fun smooth(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    private fun bloom(canvas: Canvas, paint: Paint, x: Float, y: Float, rx: Float, ry: Float, shader: Shader, alpha: Float) {
        val a = alpha.toInt().coerceIn(0, 255)
        if (a <= 0 || rx <= 0.5f || ry <= 0.5f) return
        paint.style = Paint.Style.FILL
        paint.shader = shader
        paint.alpha = a
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(rx, ry)
        canvas.drawCircle(0f, 0f, 1f, paint)
        canvas.restore()
        paint.alpha = 255
        paint.shader = null
    }

    private fun tri(canvas: Canvas, paint: Paint, x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float) {
        path.reset()
        path.moveTo(x0, y0)
        path.lineTo(x1, y1)
        path.lineTo(x2, y2)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun restore(paint: Paint) {
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.alpha = 255
        paint.strokeCap = Paint.Cap.BUTT
        paint.strokeWidth = 1f
    }

    // ===================================================================== entry point
    fun draw(canvas: Canvas, paint: Paint, enc: BossEncounter) {
        val b = enc.brain
        paint.shader = null
        when (b) {
            is KeenerBrain -> {
                drawKeenerHazards(canvas, paint, b)
                drawKeener(canvas, paint, b)
            }
            is ShardbackBrain -> {
                drawShardbackHazards(canvas, paint, b)
                drawShardback(canvas, paint, b)
            }
        }
        restore(paint)
    }

    // ===================================================================== the Keener
    private fun drawKeener(canvas: Canvas, paint: Paint, b: KeenerBrain) {
        val dying = b.deathProgress
        val vis = ((1f - b.fade) * (1f - dying)).coerceIn(0f, 1f)
        if (vis <= 0.02f) return
        val x = b.x
        val y = b.y
        val dir = b.facing.toFloat()
        val t = b.time
        val mode = b.mode
        val att = b.attack?.id
        val p = b.telegraphProgress
        val sleeping = mode == BossBrain.Mode.SLEEP
        val droop = if (sleeping) 1f else if (mode == BossBrain.Mode.AWAKEN) 1f - smooth(b.awakenProgress) else 0f
        val striking = mode == BossBrain.Mode.STRIKE
        val telegraphing = mode == BossBrain.Mode.TELEGRAPH
        val castAtt = att == KeenerBrain.WAIL || att == KeenerBrain.RING
        var raise = if (telegraphing && castAtt) smooth(p) else if (striking && castAtt) 1f else 0f
        var spread = if (telegraphing && att == KeenerBrain.GALE) smooth(p) else if (striking && att == KeenerBrain.GALE) 1f else 0f
        if (mode == BossBrain.Mode.TRANSITION) { raise = 1f; spread = 0.4f }
        if (telegraphing && att == KeenerBrain.DROP) raise = 0.6f
        val exposed = b.isExposed
        val flash = b.hitFlash > 0f

        // aura
        bloom(canvas, paint, x, y - 62f, 76f, 96f, violetGlow, (46f + 50f * max(raise, spread)) * vis * (1f - droop * 0.6f))
        if (exposed) {
            val pulse = 0.65f + 0.35f * sin(t * 9f)
            bloom(canvas, paint, x, y - 76f, 88f, 108f, goldGlow, 235f * pulse * vis)
            bloom(canvas, paint, x, y - 70f, 44f, 56f, goldGlow, 210f * pulse * vis)
        }

        // gale wind streaks gather in front of her
        if (telegraphing && att == KeenerBrain.GALE) {
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = 2f
            for (i in 0 until 6) {
                val ly = b.arena.floorY - 12f - i * 11f
                val sx = x + dir * (56f + i * 16f + ((t * 380f + i * 40f) % 90f))
                paint.color = c(170f * p, 214, 206, 255)
                canvas.drawLine(sx, ly, sx + dir * (34f + 40f * p), ly, paint)
            }
            paint.strokeCap = Paint.Cap.BUTT
        }
        // drop: motes spiral away as she fades
        if (telegraphing && att == KeenerBrain.DROP) {
            paint.style = Paint.Style.FILL
            for (i in 0 until 7) {
                val ang = t * 6f + i * 0.9f
                val r = 14f + 26f * b.fade
                paint.color = c(200f * (0.3f + 0.7f * b.fade), 190, 170, 255)
                canvas.drawCircle(x + cos(ang) * r, y - 40f - i * 9f - 20f * b.fade, 2.6f, paint)
            }
        }
        // falling streak
        if (striking && att == KeenerBrain.DROP && y < b.arena.floorY - KeenerBrain.HOVER - 6f) {
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = 3f
            for (i in -1..1) {
                paint.color = c(130f, 190, 170, 255)
                canvas.drawLine(x + i * 12f, y - 112f, x + i * 12f, y - 112f - 90f, paint)
            }
            paint.strokeCap = Paint.Cap.BUTT
        }

        drawKeenerBody(canvas, paint, b, vis, droop, raise, spread, if (flash) 1f else 0f)

        // orbs forming in her hands
        if ((telegraphing || striking) && att == KeenerBrain.WAIL) {
            val k = if (telegraphing) p else 1f
            val hx = x + dir * (30f + 8f * raise)
            val hy = y - 96f - 22f * raise
            bloom(canvas, paint, hx, hy, 12f + 22f * k, 12f + 22f * k, violetGlow, 200f * vis)
            paint.style = Paint.Style.FILL
            paint.color = c(255f, 232, 224, 255, vis)
            canvas.drawCircle(hx, hy, 2.5f + 8f * k, paint)
        }
        if ((telegraphing || striking) && att == KeenerBrain.RING) {
            // tears gather in a cloud above her head before they fall
            val k = if (telegraphing) p else 1f
            paint.style = Paint.Style.FILL
            for (i in 0 until 9) {
                val ox = x + (i - 4) * 12f + sin(t * 3f + i) * 4f
                val oy = y - 150f - 12f * k - (i % 3) * 7f + sin(t * 4f + i * 1.7f) * 3f
                bloom(canvas, paint, ox, oy, 11f, 11f, violetGlow, 170f * k * vis)
                paint.color = c(255f, 232, 224, 255, vis * k)
                canvas.drawCircle(ox, oy, 2.8f, paint)
            }
        }

        // dying: motes drift up
        if (dying > 0f) {
            paint.style = Paint.Style.FILL
            for (i in 0 until 12) {
                val px = x + sin(i * 12.9898f) * 34f
                val py = y - 16f - dying * (50f + i * 9f)
                paint.color = c(190f * (1f - dying), 190, 170, 255)
                canvas.drawCircle(px, py, 2.4f, paint)
            }
        }
    }

    private fun drawKeenerBody(canvas: Canvas, paint: Paint, b: KeenerBrain, vis: Float, droop: Float, raise: Float, spread: Float, flash: Float) {
        val x = b.x
        val y = b.y
        val dir = b.facing.toFloat()
        val t = b.time
        val dying = b.deathProgress
        val shoulder = y - 92f + 10f * droop
        val hem = 34f + 8f * spread + 14f * dying
        val whiteA = 200f * flash

        // outer gown with a ragged, swaying hem
        path.reset()
        path.moveTo(x - 13f, shoulder)
        path.quadTo(x - 22f, y - 50f, x - hem, y - 6f)
        for (i in 0..6) {
            val fx = x - hem + 2f * hem * (i / 6f)
            val fy = if (i % 2 == 0) y + 5f + sin(t * 2.1f + i * 1.3f) * 3.5f + 8f * dying else y - 9f
            path.lineTo(fx, fy)
        }
        path.quadTo(x + 22f, y - 50f, x + 13f, shoulder)
        path.close()
        paint.style = Paint.Style.FILL
        paint.color = c(255f, 36, 30, 66, vis)
        canvas.drawPath(path, paint)
        // lighter inner fold
        path.reset()
        path.moveTo(x - 6f, shoulder + 4f)
        path.quadTo(x - 11f, y - 50f, x - 18f, y - 8f)
        path.lineTo(x + 18f, y - 8f)
        path.quadTo(x + 11f, y - 50f, x + 6f, shoulder + 4f)
        path.close()
        paint.color = c(150f, 70, 58, 120, vis)
        canvas.drawPath(path, paint)
        // rim light
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.6f
        paint.color = c(120f, 160, 140, 235, vis)
        path.reset()
        path.moveTo(x - dir * 13f, shoulder)
        path.quadTo(x - dir * 22f, y - 50f, x - dir * hem, y - 6f)
        canvas.drawPath(path, paint)

        // arms
        val restL = floatArrayOf(x - 8f, y - 60f)
        val restR = floatArrayOf(x + 8f, y - 60f)
        val raisedFront = floatArrayOf(x + dir * (30f + 8f * raise), y - 96f - 22f * raise)
        val raisedBack = floatArrayOf(x - dir * 14f, y - 100f - 18f * raise)
        val spreadFront = floatArrayOf(x + dir * (46f + 34f * spread), y - 88f)
        val spreadBack = floatArrayOf(x - dir * (46f + 34f * spread), y - 88f)
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 5f
        for (side in 0..1) {
            val front = side == 0
            val sx = x + (if (front) dir else -dir) * 11f
            val sy = shoulder + 4f
            val rest = if (front == (dir > 0f)) restR else restL
            var hx = lerp(rest[0], if (front) raisedFront[0] else raisedBack[0], raise)
            var hy = lerp(rest[1], if (front) raisedFront[1] else raisedBack[1], raise)
            hx = lerp(hx, if (front) spreadFront[0] else spreadBack[0], spread)
            hy = lerp(hy, if (front) spreadFront[1] else spreadBack[1], spread)
            val ex = (sx + hx) * 0.5f
            val ey = (sy + hy) * 0.5f + 9f * (1f - max(raise, spread))
            paint.color = c(255f, 66, 56, 110, vis)
            canvas.drawLine(sx, sy, ex, ey, paint)
            canvas.drawLine(ex, ey, hx, hy, paint)
            paint.style = Paint.Style.FILL
            paint.color = c(255f, 214, 210, 234, vis)
            canvas.drawCircle(hx, hy, 3.4f, paint)
            paint.style = Paint.Style.STROKE
        }
        paint.strokeCap = Paint.Cap.BUTT

        // hood and weeping mask (bowed while she sleeps)
        val headDrop = 14f * droop
        val fwd = dir * 8f * droop
        paint.style = Paint.Style.FILL
        path.reset()
        path.moveTo(x - 14f + fwd, shoulder + 2f)
        path.quadTo(x - 19f + fwd, y - 120f + headDrop, x + dir * 3f + fwd, y - 128f + headDrop)
        path.quadTo(x + 19f + fwd, y - 120f + headDrop, x + 14f + fwd, shoulder + 2f)
        path.close()
        paint.color = c(255f, 22, 18, 42, vis)
        canvas.drawPath(path, paint)
        val fx = x + dir * 3f + fwd
        val fy = y - 102f + headDrop
        rect.set(fx - 8.5f, fy - 12f, fx + 8.5f, fy + 12f)
        paint.color = c(255f, 214, 210, 234, vis)
        canvas.drawOval(rect, paint)
        for (e in -1..1 step 2) {
            val ex = fx + e * 3.3f
            rect.set(ex - 1.8f, fy - 6f, ex + 1.8f, fy + 1.6f)
            paint.color = c(255f, 30, 24, 58, vis)
            canvas.drawOval(rect, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.4f
            paint.color = c(200f, 64, 54, 120, vis)
            canvas.drawLine(ex, fy + 1f, ex + e * 0.8f, fy + 17f, paint)
            paint.style = Paint.Style.FILL
            if (droop < 0.5f) {
                paint.color = c(255f, 196, 176, 255, vis * (1f - droop * 2f))
                canvas.drawCircle(ex, fy - 2.4f, 1.1f, paint)
            }
        }

        if (whiteA > 0f) {
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(whiteA.toInt(), 255, 255, 255)
            path.reset()
            path.moveTo(x - 13f, shoulder)
            path.quadTo(x - 22f, y - 50f, x - hem, y - 6f)
            path.lineTo(x + hem, y - 6f)
            path.quadTo(x + 22f, y - 50f, x + 13f, shoulder)
            path.close()
            canvas.drawPath(path, paint)
            canvas.drawOval(rect.also { it.set(fx - 8.5f, fy - 12f, fx + 8.5f, fy + 12f) }, paint)
        }
    }

    private fun drawKeenerHazards(canvas: Canvas, paint: Paint, b: KeenerBrain) {
        for (h in b.hazards) {
            when (h.kind) {
                Hazard.WAVE -> if (h.armed) {
                    val dir = if (h.vx >= 0f) 1f else -1f
                    paint.style = Paint.Style.FILL
                    paint.color = c(70f, 214, 206, 255)
                    rect.set(h.x - h.w / 2f, h.y - h.h, h.x + h.w / 2f, h.y)
                    canvas.drawRoundRect(rect, 14f, 14f, paint)
                    paint.style = Paint.Style.STROKE
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeWidth = 2f
                    for (i in 0 until 4) {
                        val ly = h.y - h.h * (0.18f + 0.22f * i)
                        paint.color = c(180f, 236, 230, 255)
                        canvas.drawLine(h.x - dir * 30f, ly, h.x + dir * 18f, ly, paint)
                    }
                    paint.strokeCap = Paint.Cap.BUTT
                    paint.style = Paint.Style.FILL
                }
                Hazard.MARKER -> drawMarker(canvas, paint, h, b.time)
            }
        }
    }

    // ===================================================================== Shardback
    private fun drawShardback(canvas: Canvas, paint: Paint, b: ShardbackBrain) {
        val dying = b.deathProgress
        val x = b.x
        val y = b.y
        val dir = b.facing.toFloat()
        val t = b.time
        val mode = b.mode
        val att = b.attack?.id
        val p = b.telegraphProgress
        val sleeping = mode == BossBrain.Mode.SLEEP
        val wake = if (sleeping) 0f else if (mode == BossBrain.Mode.AWAKEN) smooth(b.awakenProgress) else 1f
        val exposed = b.isExposed
        val flash = b.hitFlash > 0f
        val alpha = (1f - max(0f, (dying - 0.35f) / 0.65f)).coerceIn(0f, 1f)
        if (alpha <= 0.02f) return

        // warm gold on the whole beast when it can be hurt
        if (exposed) {
            val pulse = 0.65f + 0.35f * sin(t * 9f)
            bloom(canvas, paint, x, y - 46f, 130f, 84f, goldGlow, 225f * pulse * alpha)
        }
        bloom(canvas, paint, x, y - 46f, 90f, 56f, pinkGlow, (40f + 70f * p + (if (mode == BossBrain.Mode.TRANSITION) 90f else 0f)) * wake * alpha)

        // roar: ceiling crystals glow before the shards drop
        if (att == ShardbackBrain.RAIN && mode == BossBrain.Mode.TELEGRAPH) {
            for (i in 0 until 9) {
                val gx = b.arena.left + (i + 0.5f) * (b.arena.width / 9f)
                bloom(canvas, paint, gx, b.arena.ceilingY + 8f, 26f, 22f, pinkGlow, 190f * p)
            }
        }

        canvas.save()
        canvas.translate(x, y)
        canvas.scale(dir, 1f)

        if (b.rolling) {
            drawGeodeBall(canvas, paint, b, alpha, flash)
        } else {
            drawBeast(canvas, paint, b, alpha, wake, flash, exposed)
        }
        canvas.restore()

        if (att == ShardbackBrain.CHARGE && mode == BossBrain.Mode.STRIKE) {
            // dust thrown up behind the charge
            paint.style = Paint.Style.FILL
            for (k in 0 until 4) {
                paint.color = c(110f - k * 22f, 190, 170, 200)
                canvas.drawCircle(x - dir * (70f + k * 20f), y - 6f - k * 4f, 8f + k * 3f, paint)
            }
        }
        if (dying > 0f) {
            // the shell bursts apart
            paint.style = Paint.Style.FILL
            for (i in 0 until 14) {
                val a = i * 0.4488f
                val sx = x + cos(a) * dying * (80f + (i % 3) * 40f)
                val sy = y - 46f + sin(a) * dying * 60f + dying * dying * 110f
                paint.color = c(255f * (1f - dying), 214, 120, 232)
                val s = 7f + (i % 4) * 2f
                tri(canvas, paint, sx - s, sy + s, sx, sy - s * 1.8f, sx + s, sy + s)
            }
        }
    }

    private fun drawBeast(canvas: Canvas, paint: Paint, b: ShardbackBrain, alpha: Float, wake: Float, flash: Boolean, exposed: Boolean) {
        val t = b.time
        val mode = b.mode
        val att = b.attack?.id
        val p = b.telegraphProgress
        val glow = if (mode == BossBrain.Mode.TELEGRAPH) p else if (mode == BossBrain.Mode.TRANSITION) 1f else 0f

        // pose: rotation about the rear feet
        var angle = 0f
        when {
            mode == BossBrain.Mode.TELEGRAPH && att == ShardbackBrain.CHARGE -> angle = 7f * smooth(p)
            mode == BossBrain.Mode.TELEGRAPH && att == ShardbackBrain.SLAM -> angle = -34f * smooth(p)
            mode == BossBrain.Mode.STRIKE && att == ShardbackBrain.SLAM -> angle = lerp(-34f, 9f, smooth(b.modeTime / 0.16f))
            att == ShardbackBrain.RAIN && (mode == BossBrain.Mode.TELEGRAPH || mode == BossBrain.Mode.STRIKE) -> angle = -15f * (if (mode == BossBrain.Mode.TELEGRAPH) smooth(p) else 1f)
            mode == BossBrain.Mode.TRANSITION -> angle = -28f
            mode == BossBrain.Mode.RECOVER -> angle = 9f
            mode == BossBrain.Mode.STAGGER -> angle = 12f + sin(t * 30f) * 1.5f
            mode == BossBrain.Mode.DYING -> angle = 14f
        }
        val crouch = 1f - 0.22f * (1f - wake)
        val breathe = 1f + 0.014f * sin(t * 2f)
        canvas.rotate(angle, -46f, 0f)
        canvas.scale(1f, crouch * breathe)

        val shell = if (flash) Color.WHITE else c(255f, 62, 44, 92, alpha)
        val belly = if (flash) Color.WHITE else c(255f, 40, 28, 62, alpha)
        val leg = if (flash) Color.WHITE else c(255f, 46, 32, 72, alpha)
        val headC = if (flash) Color.WHITE else c(255f, 74, 54, 106, alpha)
        paint.style = Paint.Style.FILL

        // legs
        val moving = mode == BossBrain.Mode.MOVE
        val chargeRun = att == ShardbackBrain.CHARGE && mode == BossBrain.Mode.STRIKE
        val ph = if (chargeRun) t * 22f else if (moving) t * 6f else 0f
        val scrape = if (att == ShardbackBrain.CHARGE && mode == BossBrain.Mode.TELEGRAPH) sin(t * 15f) * 12f * p else 0f
        val legX = floatArrayOf(-52f, -28f, 14f, 38f)
        for (i in 0 until 4) {
            var swing = sin(ph + i * 1.57f) * 7f
            if (i == 3) swing += scrape
            val lift = if (i == 3 && scrape != 0f) -abs(sin(t * 15f)) * 8f * p else 0f
            paint.color = leg
            rect.set(legX[i] + swing - 8f, -26f + lift, legX[i] + swing + 8f, 0f + lift)
            canvas.drawRoundRect(rect, 6f, 6f, paint)
        }
        // belly + shell
        paint.color = belly
        rect.set(-64f, -44f, 56f, -12f)
        canvas.drawRoundRect(rect, 16f, 16f, paint)
        paint.color = shell
        rect.set(-66f, -70f, 54f, -22f)
        canvas.drawRoundRect(rect, 26f, 26f, paint)

        // head, snout and horns
        paint.color = headC
        path.reset()
        path.moveTo(42f, -60f)
        path.quadTo(70f, -62f, 90f, -38f)
        path.lineTo(80f, -26f)
        path.quadTo(60f, -16f, 40f, -22f)
        path.close()
        canvas.drawPath(path, paint)
        val hornC = if (flash) Color.WHITE else c(255f, 244, 178, 252, alpha)
        paint.color = hornC
        tri(canvas, paint, 52f, -58f, 62f, -88f - 4f * glow, 68f, -56f)
        tri(canvas, paint, 40f, -60f, 44f, -80f - 3f * glow, 54f, -60f)
        // eye
        val eyeOn = wake > 0.4f
        paint.color = if (eyeOn) c(255f, 255, 170 + (60 * glow).toInt(), 240, alpha) else c(255f, 90, 60, 110, alpha)
        canvas.drawCircle(66f, -48f, if (mode == BossBrain.Mode.TELEGRAPH) 4.4f else 3.4f, paint)

        // back crystals: they lean apart when the core opens
        val open = if (exposed) 1f else 0f
        val bases = floatArrayOf(-52f, -38f, -24f, -10f, 4f, 18f, 32f)
        val heights = floatArrayOf(22f, 34f, 44f, 50f, 40f, 30f, 20f)
        for (i in bases.indices) {
            val lean = (i - 3) * 2.2f * open + (i - 3) * 1.2f
            val h = heights[i] * (0.65f + 0.35f * wake) + 10f * glow
            val bx = bases[i]
            val col = mixC(c(255f, 206, 108, 226, alpha), Color.argb((255 * alpha).toInt(), 255, 236, 255), glow)
            paint.color = if (flash) Color.WHITE else col
            tri(canvas, paint, bx - 8f, -66f, bx + lean, -66f - h, bx + 8f, -66f)
            paint.color = if (flash) Color.WHITE else c(255f, 120, 58, 150, alpha)
            tri(canvas, paint, bx, -66f, bx + lean, -66f - h, bx + 8f, -66f)
            paint.color = c(110f * alpha, 255, 214, 255)
            tri(canvas, paint, bx - 5f, -66f, bx + lean - 1f, -66f - h * 0.9f, bx - 1f, -66f)
        }
        if (exposed) {
            // the core, open between the shards: this is where it hurts
            val pulse = 0.7f + 0.3f * sin(t * 9f)
            bloom(canvas, paint, -10f, -70f, 38f, 24f, goldGlow, 200f * pulse * alpha)
            paint.color = c(255f, 255, 226, 150, alpha)
            rect.set(-22f, -75f, 2f, -63f)
            canvas.drawOval(rect, paint)
        }
    }

    private fun mixC(a: Int, b: Int, k: Float): Int {
        val t = k.coerceIn(0f, 1f)
        return Color.argb(
            (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t).toInt(),
            (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
        )
    }

    private fun drawGeodeBall(canvas: Canvas, paint: Paint, b: ShardbackBrain, alpha: Float, flash: Boolean) {
        val spin = b.time * 9f
        paint.style = Paint.Style.FILL
        for (i in 0 until 10) {
            val a = spin + i * 0.6283f
            val ix = cos(a)
            val iy = sin(a)
            val l = 40f
            paint.color = if (flash) Color.WHITE else c(255f, 206, 108, 226, alpha)
            tri(
                canvas, paint,
                ix * l + -iy * 9f, -48f + iy * l + ix * 9f,
                ix * 62f, -48f + iy * 62f,
                ix * l + iy * 9f, -48f + iy * l - ix * 9f
            )
        }
        paint.color = if (flash) Color.WHITE else c(255f, 62, 44, 92, alpha)
        canvas.drawCircle(0f, -48f, 42f, paint)
        paint.color = c(120f * alpha, 255, 214, 255)
        rect.set(-30f, -78f, 6f, -54f)
        canvas.drawOval(rect, paint)
    }

    private fun drawShardbackHazards(canvas: Canvas, paint: Paint, b: ShardbackBrain) {
        for (h in b.hazards) {
            when (h.kind) {
                Hazard.WAVE -> if (h.armed) {
                    paint.style = Paint.Style.FILL
                    paint.color = c(110f, 255, 150, 235)
                    rect.set(h.x - h.w / 2f, h.y - h.h * 0.5f, h.x + h.w / 2f, h.y)
                    canvas.drawRoundRect(rect, 8f, 8f, paint)
                    paint.color = c(230f, 255, 214, 255)
                    for (i in 0 until 3) {
                        val sx = h.x - h.w / 2f + 8f + i * (h.w / 3f)
                        tri(canvas, paint, sx - 6f, h.y - h.h * 0.4f, sx, h.y - h.h, sx + 6f, h.y - h.h * 0.4f)
                    }
                }
                Hazard.MARKER -> drawMarker(canvas, paint, h, b.time)
                Hazard.SHARD -> if (h.armed) {
                    paint.style = Paint.Style.FILL
                    if (h.landed) {
                        val k = (h.life / 0.35f).coerceIn(0f, 1f)
                        paint.color = c(220f * k, 255, 180, 245)
                        tri(canvas, paint, h.x - 14f, h.y, h.x - 4f, h.y - 12f, h.x + 2f, h.y)
                        tri(canvas, paint, h.x - 2f, h.y, h.x + 8f, h.y - 9f, h.x + 15f, h.y)
                    } else {
                        paint.color = c(255f, 230, 130, 235)
                        tri(canvas, paint, h.x - h.w / 2f, h.y - h.h, h.x + h.w / 2f, h.y - h.h, h.x, h.y)
                        paint.color = c(255f, 255, 210, 255)
                        tri(canvas, paint, h.x - h.w / 2f, h.y - h.h, h.x - 2f, h.y - h.h, h.x - 2f, h.y - h.h * 0.3f)
                    }
                }
            }
        }
    }

    /**
     * The warning for anything that will land or strike on the floor: a bright pulsing pool with a
     * column of light. Every ground-targeted attack of every boss uses the same orange-red, so the
     * player learns one signal for "do not stand here".
     */
    private fun drawMarker(canvas: Canvas, paint: Paint, h: Hazard, t: Float) {
        val pulse = 0.55f + 0.45f * sin(t * 14f)
        val r = 255
        val g = 96
        val bl = 72
        paint.style = Paint.Style.FILL
        paint.color = c(60f + 70f * pulse, r, g, bl)
        rect.set(h.x - h.w * 0.65f, h.y - 10f, h.x + h.w * 0.65f, h.y + 5f)
        canvas.drawOval(rect, paint)
        paint.color = c(150f + 105f * pulse, r, 150, 110)
        rect.set(h.x - h.w * 0.5f, h.y - 5f, h.x + h.w * 0.5f, h.y + 3f)
        canvas.drawOval(rect, paint)
        paint.color = c(34f + 34f * pulse, r, g, bl)
        rect.set(h.x - h.w * 0.34f, h.y - 130f, h.x + h.w * 0.34f, h.y - 2f)
        canvas.drawRect(rect, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.6f
        paint.color = c(150f + 80f * pulse, r, 140, 110)
        canvas.drawLine(h.x - h.w * 0.34f, h.y - 130f, h.x - h.w * 0.34f, h.y - 2f, paint)
        canvas.drawLine(h.x + h.w * 0.34f, h.y - 130f, h.x + h.w * 0.34f, h.y - 2f, paint)
        paint.style = Paint.Style.FILL
    }

    // ===================================================================== gates
    /** The portcullis across an arena doorway; [amount] 0 = raised, 1 = shut. */
    fun drawGate(canvas: Canvas, paint: Paint, gate: Box, amount: Float, accent: Int, t: Float) {
        if (amount <= 0.01f) return
        val len = (gate.bottom - gate.top) * amount
        val cx = (gate.left + gate.right) / 2f
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 4.5f
        paint.color = Color.rgb(132, 136, 156)
        for (i in 0 until 4) {
            val bx = gate.left + 5f + i * 10f
            canvas.drawLine(bx, gate.top, bx, gate.top + len, paint)
        }
        paint.strokeWidth = 3f
        paint.color = Color.rgb(98, 100, 120)
        canvas.drawLine(gate.left + 2f, gate.top + len * 0.34f, gate.right - 2f, gate.top + len * 0.34f, paint)
        canvas.drawLine(gate.left + 2f, gate.top + len * 0.7f, gate.right - 2f, gate.top + len * 0.7f, paint)
        paint.strokeCap = Paint.Cap.BUTT
        // lintel rune glows in the boss's colour once the doors are shut
        val pulse = 0.7f + 0.3f * sin(t * 4f)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb((200f * amount * pulse).toInt().coerceIn(0, 255), Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawCircle(cx, gate.top - 8f, 4f, paint)
        paint.color = Color.argb((60f * amount).toInt().coerceIn(0, 255), Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawCircle(cx, gate.top - 8f, 11f, paint)
        restore(paint)
    }
}
