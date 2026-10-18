package com.devdgames.hollowknightbutmine.game

import android.graphics.RectF

/**
 * Common data structures and math for the game.
 */

data class Vec2(var x: Float = 0f, var y: Float = 0f) {
    fun set(newX: Float, newY: Float) {
        x = newX
        y = newY
    }
}

enum class Facing {
    LEFT, RIGHT
}

enum class PlayerState {
    IDLE,
    RUNNING,
    JUMPING,
    FALLING,
    DASHING,
    STABBING,      // Quick safety pin thrust
    GRABBING,      // Reaching out with open safety pin clasp/hook
    HOLDING_ENEMY, // Pin has latched onto enemy, ready to headbutt!
    HEADBUTTING,   // Smashing grabbed enemy with the horned mask
    PARRYING,      // Defending with clasp
    HEALING,       // Focus soul / silk to restore HP
    HURT
}

enum class EnemyType {
    CRAWLER,      // Small beetle / husk
    FLYER
}

data class AttackHitbox(
    val rect: RectF,
    val damage: Int,
    val isGrab: Boolean = false,
    val knockbackX: Float = 0f,
    val knockbackY: Float = 0f
)
