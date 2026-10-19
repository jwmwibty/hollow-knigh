package com.devdgames.hollowknightbutmine.game.boss

/**
 * Pure-Kotlin building blocks of the boss system (no Android types), so every rule of an
 * encounter - timing, damage, phases, rewards - is unit-tested on the JVM.
 */

/** Axis-aligned box in world pixels; the same overlap rule as android.graphics.RectF. */
class Box(var left: Float = 0f, var top: Float = 0f, var right: Float = 0f, var bottom: Float = 0f) {
    fun set(l: Float, t: Float, r: Float, b: Float) {
        left = l; top = t; right = r; bottom = b
    }

    fun intersects(l: Float, t: Float, r: Float, b: Float): Boolean = left < r && l < right && top < b && t < bottom
}

/** One move in a boss's repertoire. Times are seconds at phase-1 pace. */
class Attack(
    val id: Int,
    val name: String,
    val telegraph: Float,
    val active: Float,
    val recovery: Float,
    /** A well-timed parry during telegraph/strike interrupts the attack and staggers the boss. */
    val parryable: Boolean = true,
    /** Masks taken by the player when the boss's own body or weapon connects. */
    val damage: Int = 1
)

/** A shot to be fired into the world's projectile system (so parry-reflect works on it). */
class Shot(val x: Float, val y: Float, val vx: Float, val vy: Float, val radius: Float)

/** Something a boss has put in the arena: a travelling wave, a falling shard, a floor marker. */
class Hazard(
    val kind: Int,
    /** Centre x, and the y of the bottom edge. */
    var x: Float,
    var y: Float,
    val w: Float,
    val h: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    /** Seconds before the hazard acts (a marker is only a warning). */
    var warn: Float = 0f,
    /** Seconds left once warned. */
    var life: Float = 1f,
    val damage: Int = 1
) {
    /** True once a falling shard has hit the floor; it is then harmless debris. */
    var landed = false

    val armed: Boolean get() = warn <= 0f
    val harmful: Boolean get() = kind != MARKER && armed && !landed

    fun box(out: Box) = out.set(x - w / 2f, y - h, x + w / 2f, y)

    companion object {
        const val WAVE = 0
        const val SHARD = 1
        const val MARKER = 2
    }
}

enum class SignalType {
    AWAKEN, TELEGRAPH, STRIKE, HIT, GUARD, STAGGER, PHASE, DYING, DEFEATED, LAND, WALL_CRASH, SHOT
}

/** Something that happened this frame; the world turns these into shake, sparks and sound hooks. */
class BossSignal(val type: SignalType, val x: Float, val y: Float, val value: Float = 0f)

/** Arena layout in tiles, relative to the origin of the area that contains it. */
class ArenaTiles(
    /** First and last column of open floor in the arena (inclusive). */
    val firstCol: Int,
    val lastCol: Int,
    /** Row of the floor tiles, and the first row of open air under the ceiling. */
    val floorRow: Int,
    val topRow: Int,
    /** The doorway that closes during the fight: one column, rows inclusive. */
    val gateCol: Int,
    val gateTopRow: Int,
    val gateBottomRow: Int,
    /** Crossing this column on the way in starts the fight. */
    val triggerCol: Int,
    val spawnCol: Int
)

/** Everything that defines one boss encounter. */
class BossSpec(
    val id: String,
    val name: String,
    val title: String,
    val defeatLine: String,
    val areaId: String,
    val maxHp: Int,
    /** Permanent extra masks granted on the first victory. */
    val maskReward: Int,
    /** Share of damage taken while the boss is not exposed (0..1). */
    val guard: Float,
    /** The boss enters its second phase when hp falls to this value. */
    val phase2Hp: Int,
    val introTime: Float,
    val transitionTime: Float,
    val deathTime: Float,
    /** Phase 2 multiplies telegraph and recovery times by this (smaller is faster). */
    val phase2Pace: Float,
    val parryStagger: Float,
    val grabStagger: Float,
    /** After a stagger ends the boss cannot be staggered again for this long. */
    val poiseTime: Float,
    /** Damage of a reflected shot: it ignores guard. */
    val reflectDamage: Int,
    val accent: Int,
    val arena: ArenaTiles
)

/** The arena in world pixels. */
class BossArena(
    val left: Float,
    val right: Float,
    val floorY: Float,
    val ceilingY: Float,
    val gate: Box,
    val triggerX: Float,
    val spawnX: Float
) {
    val centerX: Float get() = (left + right) * 0.5f
    val width: Float get() = right - left
    val height: Float get() = floorY - ceilingY

    fun inside(x: Float, y: Float, pad: Float = 0f): Boolean =
        x >= left - pad && x <= right + pad && y >= ceilingY - pad && y <= floorY + pad

    /** True once the player has walked far enough in for the doors to shut behind them. */
    fun entered(px: Float, py: Float): Boolean = px >= triggerX && inside(px, py, 4f)

    companion object {
        fun from(t: ArenaTiles, col0: Int, row0: Int, tile: Float): BossArena {
            val gx = (col0 + t.gateCol) * tile
            return BossArena(
                left = (col0 + t.firstCol) * tile,
                right = (col0 + t.lastCol + 1) * tile,
                floorY = (row0 + t.floorRow) * tile,
                ceilingY = (row0 + t.topRow) * tile,
                gate = Box(gx, (row0 + t.gateTopRow) * tile, gx + tile, (row0 + t.gateBottomRow + 1) * tile),
                triggerX = (col0 + t.triggerCol) * tile,
                spawnX = (col0 + t.spawnCol) * tile + tile / 2f
            )
        }
    }
}

/** Where defeated bosses are remembered. The game keeps this in memory for the run. */
interface BossProgressStore {
    fun load(): Set<String>
    fun save(ids: Set<String>)
}

class MemoryBossProgressStore(initial: Set<String> = emptySet()) : BossProgressStore {
    private var ids: Set<String> = initial
    var saves = 0
        private set

    override fun load(): Set<String> = ids
    override fun save(ids: Set<String>) {
        this.ids = ids
        saves++
    }
}

/** Which bosses are down. [defeat] is the one-time gate for rewards. */
class BossProgress(private val store: BossProgressStore = MemoryBossProgressStore()) {
    private val defeated = LinkedHashSet<String>(store.load())

    val count: Int get() = defeated.size

    fun isDefeated(id: String): Boolean = id in defeated

    /** Records a victory; true only the first time, which is when the reward is due. */
    fun defeat(id: String): Boolean {
        if (!defeated.add(id)) return false
        store.save(defeated.toSet())
        return true
    }
}
