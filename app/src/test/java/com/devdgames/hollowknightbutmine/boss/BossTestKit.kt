package com.devdgames.hollowknightbutmine.boss

import com.devdgames.hollowknightbutmine.game.boss.BossArena
import com.devdgames.hollowknightbutmine.game.boss.BossBrain
import com.devdgames.hollowknightbutmine.game.boss.BossEncounter
import com.devdgames.hollowknightbutmine.game.boss.BossProgress
import com.devdgames.hollowknightbutmine.game.boss.BossRoster
import com.devdgames.hollowknightbutmine.game.boss.BossSpec
import com.devdgames.hollowknightbutmine.game.boss.KeenerBrain
import com.devdgames.hollowknightbutmine.game.boss.ShardbackBrain
import com.devdgames.hollowknightbutmine.game.world.WorldAtlas
import com.devdgames.hollowknightbutmine.game.world.WorldLayout

/** Shared helpers: real arenas from the real map, and a fixed-step clock. */
internal object BossTestKit {
    const val DT = 1f / 60f
    val layout = WorldLayout(WorldAtlas.areas)

    fun arena(spec: BossSpec): BossArena = BossRoster.arenaFor(spec, layout)

    fun keener(seed: Int = 7): KeenerBrain = KeenerBrain(BossRoster.keener, arena(BossRoster.keener), seed).also { it.reset() }
    fun shardback(seed: Int = 7): ShardbackBrain = ShardbackBrain(BossRoster.shardback, arena(BossRoster.shardback), seed).also { it.reset() }

    fun encounter(brain: BossBrain, progress: BossProgress = BossProgress()): BossEncounter =
        BossEncounter(brain.spec, brain.arena, brain, progress)

    /** x a player would stand at to be inside the arena (past the trigger), and the floor height. */
    fun insideX(a: BossArena): Float = a.triggerX + 80f
    fun floorY(a: BossArena): Float = a.floorY - 1f

    /** Runs the brain alone for [seconds] with the player standing at ([px], floor). */
    fun run(brain: BossBrain, seconds: Float, px: Float, py: Float = brain.arena.floorY - 1f) {
        var t = 0f
        while (t < seconds) {
            brain.update(DT, px, py)
            t += DT
        }
    }

    /** Runs until [done] is true or [limit] seconds pass; returns seconds elapsed, or -1 on timeout. */
    fun runUntil(brain: BossBrain, limit: Float, px: Float, py: Float = brain.arena.floorY - 1f, done: () -> Boolean): Float {
        var t = 0f
        while (t < limit) {
            if (done()) return t
            brain.update(DT, px, py)
            t += DT
        }
        return if (done()) t else -1f
    }

    /** Wakes the brain and runs through its intro, so it is in its first MOVE. */
    fun wake(brain: BossBrain, px: Float) {
        brain.awaken()
        run(brain, brain.spec.introTime + 0.1f, px)
    }
}
