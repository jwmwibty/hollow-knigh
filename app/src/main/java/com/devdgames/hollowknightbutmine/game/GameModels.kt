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
    SLASHING,      // Light attack: sword slash through a wide arc
    STABBING,      // Committed forward thrust with the sword's point
    HEALING,       // Focus soul / silk to restore HP
    HURT,
    DEAD           // Brief death pause before respawning
}

enum class EnemyType {
    CRAWLER,           // Small beetle / husk
    FLYER,             // Winged mosquito / squit that swoops down from above
    SPITTER,           // Acid / silk spitter artillery bug
    SHIELDER,          // Heavy armored husk with a chitin shield
    MOSS_CHARGER,      // Aggressive charger beast
    BROODMOTHER,       // Chapter 1 Boss: Giant winged matriarch that bombards acid & spawns swarms
    OSSUARY_TITAN,     // Chapter 2 Boss: Gigantic bone golem with hammer smashes & shockwaves
    COLONNADE_KNIGHT   // Chapter 3 Boss: Ancient Royal Duelist with lance thrusts & teleports
}

data class AttackHitbox(
    val rect: RectF,
    val damage: Int,
    val knockbackX: Float = 0f,
    val knockbackY: Float = 0f
)
