package com.devdgames.hollowknightbutmine.game.boss

/**
 * The life cycle of one boss fight, separate from the boss's own moves.
 *
 *   DORMANT --(player walks into the arena)--> INTRO --(boss awake)--> FIGHTING
 *      ^                                         |                         |
 *      +---------- player dies or leaves --------+-------------------------+
 *                                                                          |
 *   DEFEATED <------------------------ the boss starts to die -------------+
 *
 * Rules this class guarantees:
 *  - The doors are shut exactly during INTRO and FIGHTING.
 *  - If the player dies (or somehow leaves) mid-fight, the boss resets to full health and
 *    the doors open: there is never a half-finished encounter left behind.
 *  - Once the boss starts to die the victory is locked in; the player dying afterwards
 *    can not bring it back.
 *  - The reward is handed out once, ever ([BossProgress.defeat]); re-entering the arena
 *    afterwards does nothing.
 */
class BossEncounter(
    val spec: BossSpec,
    val arena: BossArena,
    val brain: BossBrain,
    private val progress: BossProgress
) {
    enum class State { DORMANT, INTRO, FIGHTING, DEFEATED }

    var state: State = State.DORMANT
        private set

    private var rewardPending = 0

    init {
        if (progress.isDefeated(spec.id)) {
            state = State.DEFEATED
        } else {
            brain.reset()
        }
    }

    /** The doors across the entrance are shut. */
    val gateClosed: Boolean get() = state == State.INTRO || state == State.FIGHTING

    /**
     * The boss is part of the world: asleep, fighting, or in its death throes. False once it
     * has finished dying, and for a boss that was already defeated before this run.
     */
    val bossPresent: Boolean get() = state != State.DEFEATED || brain.isDying

    /**
     * Advances the encounter and the boss by one frame. [frozen] holds the boss still (the
     * player has it hooked); the encounter rules still apply.
     */
    fun update(dt: Float, playerX: Float, playerY: Float, playerDead: Boolean, frozen: Boolean = false) {
        when (state) {
            State.DORMANT -> {
                if (!playerDead && arena.entered(playerX, playerY)) {
                    state = State.INTRO
                    brain.awaken()
                }
            }
            State.INTRO, State.FIGHTING -> {
                if (brain.isDying || brain.isDefeated) {
                    win()
                } else if (playerDead || !arena.inside(playerX, playerY, 120f)) {
                    abort()
                } else if (state == State.INTRO && brain.isAwake) {
                    state = State.FIGHTING
                }
            }
            State.DEFEATED -> {}
        }
        if (!frozen && !(state == State.DEFEATED && brain.isDefeated)) brain.update(dt, playerX, playerY)
        // the killing blow can land during the update above
        if ((state == State.INTRO || state == State.FIGHTING) && (brain.isDying || brain.isDefeated)) win()
    }

    /** The player died: end the fight and put the boss back to sleep. Safe to call at any time. */
    fun onPlayerDied() {
        if (state == State.INTRO || state == State.FIGHTING) abort()
    }

    /** Masks owed to the player for this victory: the full amount once, then zero. */
    fun takeReward(): Int {
        val r = rewardPending
        rewardPending = 0
        return r
    }

    private fun abort() {
        brain.reset()
        state = State.DORMANT
    }

    private fun win() {
        state = State.DEFEATED
        if (progress.defeat(spec.id)) rewardPending = spec.maskReward
    }
}
