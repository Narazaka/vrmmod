package net.narazaka.vrmmod.render

/**
 * Shared delta-time computation logic.
 *
 * Returns a safe delta time in seconds. If the raw delta exceeds [MAX_DELTA]
 * (e.g., after a game pause or tab-out), returns 0 to prevent animation
 * and physics from jumping.
 */
object DeltaTimeUtil {
    private const val MAX_DELTA = 0.1f
    private const val MIN_DELTA = 0.001f
    private const val DEFAULT_DELTA = 1f / 60f

    /**
     * Computes delta time from the given [lastNano] timestamp.
     *
     * @param lastNano previous System.nanoTime(), or 0 for the first frame
     * @param now current System.nanoTime()
     * @return delta time in seconds, or 0 if a pause was detected
     */
    @JvmStatic
    fun compute(lastNano: Long, now: Long): Float {
        val rawDelta = if (lastNano == 0L) {
            DEFAULT_DELTA
        } else {
            (now - lastNano) / 1_000_000_000f
        }
        return if (rawDelta > MAX_DELTA) 0f else rawDelta.coerceAtLeast(MIN_DELTA)
    }
}
