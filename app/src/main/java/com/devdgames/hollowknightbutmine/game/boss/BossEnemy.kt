package com.devdgames.hollowknightbutmine.game.boss

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.devdgames.hollowknightbutmine.game.Enemy
import com.devdgames.hollowknightbutmine.game.EnemyType
import com.devdgames.hollowknightbutmine.game.Facing

/**
 * Presents a [BossEncounter] to the rest of the game as an ordinary [Enemy], so the player's
 * sword attacks work on bosses unchanged. Everything that is specific to a boss fight (when it
 * can be hurt, what hurts the player, stagger and poise) is decided by the pure [BossBrain];
 * this class only translates.
 */
class BossEnemy(val encounter: BossEncounter) : Enemy(
    encounter.brain.x, encounter.brain.y, encounter.brain.bodyW, encounter.brain.bodyH,
    EnemyType.CRAWLER, isBoss = true
) {
    val brain: BossBrain get() = encounter.brain

    /** Set by the world each frame: the player is in the middle of dying. */
    var playerDead = false

    init {
        sync()
    }

    private fun sync() {
        val b = encounter.brain
        x = b.x
        y = b.y
        width = b.bodyW
        height = b.bodyH
        facing = if (b.facing > 0) Facing.RIGHT else Facing.LEFT
        hp = b.hp
        maxHp = b.maxHp
        isStunned = b.isStaggered
        stunTimer = b.staggerRemaining
        isAttacking = b.parryOpen
        flashTimer = b.hitFlash
        vx = 0f
        vy = 0f
        isAlive = encounter.bossPresent
    }

    override fun update(dt: Float, playerX: Float, playerY: Float, platforms: List<RectF>) {
        if (!isAlive) return
        encounter.update(dt, playerX, playerY, playerDead)
        sync()
    }

    /** Attacks pass straight through a boss that is asleep, vanished, changing phase or dying. */
    override fun getBoundingBox(): RectF {
        if (isAlive && brain.isHittable) {
            hitbox.set(x - width / 2f, y - height, x + width / 2f, y)
        } else {
            hitbox.set(FAR, FAR, FAR, FAR)
        }
        return hitbox
    }

    override fun takeDamage(damage: Int, knockbackX: Float, knockbackY: Float, playEffect: (() -> Unit)?) {
        if (!isAlive) return
        val dealt = brain.damage(damage)
        sync()
        if (dealt > 0) playEffect?.invoke()
    }

    override fun hurtsPlayer(playerBox: RectF): Boolean =
        isAlive && !isStunned &&
            brain.hurts(playerBox.left, playerBox.top, playerBox.right, playerBox.bottom)

    override val touchDamage: Int get() = brain.lastDamage

    override fun draw(canvas: Canvas, paint: Paint) {
        if (!isAlive) return
        BossArt.draw(canvas, paint, encounter)
    }

    companion object {
        private const val FAR = -1.0e7f
    }
}
