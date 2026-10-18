package com.devdgames.hollowknightbutmine.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * Dedicated high-performance 60FPS SurfaceView Game Loop for smooth platforming & combat.
 */
class GameSurfaceView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    val world = GameWorld()
    var inputX = 0f

    // PC Keyboard state tracking
    private var isAPressed = false
    private var isDPressed = false

    private var gameThread: Thread? = null
    @Volatile
    private var isRunning = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()
    }

    fun onKeyDownEvent(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_A -> {
                isAPressed = true
                updateKeyboardInputX()
                return true
            }
            KeyEvent.KEYCODE_D -> {
                isDPressed = true
                updateKeyboardInputX()
                return true
            }
            KeyEvent.KEYCODE_SPACE -> {
                if (event.repeatCount == 0) {
                    world.player.jump()
                }
                return true
            }
            KeyEvent.KEYCODE_Q -> {
                if (event.repeatCount == 0) {
                    world.player.dash()
                }
                return true
            }
            KeyEvent.KEYCODE_E -> {
                world.player.startHeal()
                return true
            }
            KeyEvent.KEYCODE_F -> {
                if (event.repeatCount == 0) {
                    world.player.grab()
                }
                return true
            }
        }
        return false
    }

    fun onKeyUpEvent(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_A -> {
                isAPressed = false
                updateKeyboardInputX()
                return true
            }
            KeyEvent.KEYCODE_D -> {
                isDPressed = false
                updateKeyboardInputX()
                return true
            }
            KeyEvent.KEYCODE_E -> {
                world.player.stopHeal()
                return true
            }
        }
        return false
    }

    private fun updateKeyboardInputX() {
        inputX = when {
            isAPressed && !isDPressed -> -1f
            isDPressed && !isAPressed -> 1f
            else -> 0f
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val buttonState = event.buttonState

        if (action == MotionEvent.ACTION_BUTTON_PRESS || action == MotionEvent.ACTION_DOWN) {
            if ((buttonState and MotionEvent.BUTTON_PRIMARY) != 0) {
                // Left Mouse Click -> Stab
                world.player.stab()
                return true
            } else if ((buttonState and MotionEvent.BUTTON_SECONDARY) != 0) {
                // Right Mouse Click -> Parry
                world.player.startParry()
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Also catch mouse clicks passed via touch events on Android emulator/PC
        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE) {
            val buttonState = event.buttonState
            if (event.action == MotionEvent.ACTION_DOWN) {
                if ((buttonState and MotionEvent.BUTTON_SECONDARY) != 0) {
                    world.player.startParry()
                    return true
                } else {
                    world.player.stab()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        isRunning = true
        gameThread = Thread(this, "GameLoopThread")
        gameThread?.start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        var retry = true
        isRunning = false
        while (retry) {
            try {
                gameThread?.join()
                retry = false
            } catch (e: InterruptedException) {
                // Keep retrying until thread is terminated
            }
        }
        gameThread = null
    }

    override fun run() {
        var lastTime = System.nanoTime()

        while (isRunning) {
            val now = System.nanoTime()
            var dt = (now - lastTime) / 1_000_000_000f
            lastTime = now

            // Cap dt to prevent spiraling after pauses
            if (dt > 0.05f) dt = 0.05f

            val width = width.toFloat()
            val height = height.toFloat()

            if (width > 0f && height > 0f) {
                // Update physics & combat
                world.update(dt, inputX, width, height)

                // Render frame
                var canvas: Canvas? = null
                try {
                    canvas = holder.lockCanvas()
                    if (canvas != null) {
                        synchronized(holder) {
                            world.draw(canvas, paint, width, height)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    if (canvas != null) {
                        try {
                            holder.unlockCanvasAndPost(canvas)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            // Target ~60 FPS (sleep ~16ms minus elapsed)
            val elapsedMs = (System.nanoTime() - now) / 1_000_000
            val sleepMs = 16 - elapsedMs
            if (sleepMs > 0) {
                try {
                    Thread.sleep(sleepMs)
                } catch (e: InterruptedException) {
                    // Ignore
                }
            }
        }
    }
}
