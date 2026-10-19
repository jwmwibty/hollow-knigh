package com.devdgames.hollowknightbutmine.game.world

/**
 * Tracks which grass chunks have been cut by the player's weapon, and when they regrow.
 *
 * Pure Kotlin — no Android imports — so it can be exercised on the JVM by unit tests.
 *
 * A chunk is identified by a [Long] key built from its area id hash and its chunk index
 * within that area's flora list:
 *
 *   key = (areaId.hashCode().toLong() shl 32) or chunkIndex.toLong()
 *
 * Cuts remain visible for [REGROW_TIME] seconds, then are silently removed so the
 * chunk draws normally again. Cutting an already-cut chunk resets its timer.
 *
 * This class is NOT thread-safe; all access must happen on the game thread.
 */
class GrassCutState {

    /** How long a cut stays visible before the grass regrows. */
    companion object {
        const val REGROW_TIME = 4.0f

        /** Build the stable key for a chunk: area hash in the high 32 bits, chunk index in low 32. */
        fun key(areaId: String, chunkIndex: Int): Long =
            (areaId.hashCode().toLong() shl 32) or (chunkIndex.toLong() and 0xFFFFFFFFL)
    }

    /** key → remaining seconds until regrowth. */
    private val timers = HashMap<Long, Float>()

    /**
     * Register a cut on the chunk identified by [key]. If the chunk was already cut its
     * timer is reset to [REGROW_TIME].
     */
    fun cut(key: Long) {
        timers[key] = REGROW_TIME
    }

    /** Convenience overload that builds the key from [areaId] and [chunkIndex]. */
    fun cut(areaId: String, chunkIndex: Int) = cut(key(areaId, chunkIndex))

    /** Returns true if the chunk [key] is currently cut (timer still running). */
    fun isCut(key: Long): Boolean = timers.containsKey(key)

    /** Convenience overload. */
    fun isCut(areaId: String, chunkIndex: Int): Boolean = isCut(key(areaId, chunkIndex))

    /**
     * Tick all timers by [dt] seconds; remove entries whose timer has expired so they
     * start drawing normally again. Must be called once per frame.
     */
    fun update(dt: Float) {
        val iter = timers.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            entry.setValue(entry.value - dt)
            if (entry.value <= 0f) iter.remove()
        }
    }

    /** The set of chunk keys that are currently cut. Useful for passing to the renderer. */
    fun cutKeys(): Set<Long> = timers.keys

    /** Returns the number of chunks currently cut (primarily for tests). */
    fun cutCount(): Int = timers.size

    /** Clear all cuts immediately (e.g. on area reset). */
    fun clear() = timers.clear()
}
