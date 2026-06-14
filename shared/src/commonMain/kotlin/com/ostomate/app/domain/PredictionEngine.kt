package com.ostomate.app.domain

object PredictionEngine {
    private const val MILLIS_PER_DAY = 86_400_000.0
    private const val MIN_INTERVAL_DAYS = 0.1

    /**
     * Average days between supply changes from up to [sampleSize] most recent timestamps.
     * Input order does not matter — the list is sorted internally.
     * Returns null when fewer than two timestamps are provided.
     * Coerced to [MIN_INTERVAL_DAYS] minimum so zero-gap bursts don't collapse predictions.
     */
    fun averageDaysBetween(
        timestamps: List<Long>,
        sampleSize: Int = 10,
    ): Double? {
        val sample = timestamps.sortedDescending().take(sampleSize)
        if (sample.size < 2) return null
        val intervals = (0 until sample.size - 1).map { sample[it] - sample[it + 1] }
        val avgMillis = intervals.average()
        return (avgMillis / MILLIS_PER_DAY).coerceAtLeast(MIN_INTERVAL_DAYS)
    }

    /**
     * Estimated days of supply remaining.
     * Returns 0.0 when [onHand] ≤ 0; null when [avgDaysBetween] is null (no history yet).
     */
    fun daysRemaining(
        onHand: Int,
        avgDaysBetween: Double?,
    ): Double? {
        if (onHand <= 0) return 0.0
        return avgDaysBetween?.let { onHand * it }
    }

    fun daysRemainingFromHistory(
        onHand: Int,
        timestamps: List<Long>,
        sampleSize: Int = 10,
    ): Double? = daysRemaining(onHand, averageDaysBetween(timestamps, sampleSize))
}
