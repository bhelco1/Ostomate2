package com.ostomate.app.domain

import com.ostomate.app.data.db.ChangeEventWithSupply
import com.ostomate.app.data.db.SupplyTypeEntity
import com.ostomate.app.platform.Notifier

/**
 * Schedules (or replaces) one reorder reminder per supply based on current predictions.
 * Called each time the home screen state recomputes — WorkManager/UNUserNotificationCenter
 * replace the pending work when the tag matches, so this is safe to call repeatedly.
 */
class NotificationScheduler(private val notifier: Notifier) {
    fun reschedule(
        supplies: List<SupplyTypeEntity>,
        eventsBySupply: Map<Long, List<ChangeEventWithSupply>>,
    ) {
        for (supply in supplies) {
            val timestamps =
                eventsBySupply[supply.id]?.map { it.event.timestampMillis } ?: emptyList()
            val daysRemaining =
                PredictionEngine.daysRemainingFromHistory(supply.onHand, timestamps)
                    ?: continue // not enough history yet

            val thresholdDays = supply.warnThresholdDays
            val tag = "reorder-${supply.id}"

            when {
                daysRemaining <= 0 -> {
                    notifier.schedule(
                        tag = tag,
                        delaySeconds = 0,
                        title = "${supply.name} — out of stock",
                        body = "Time to reorder ${supply.name}.",
                    )
                }
                daysRemaining < thresholdDays -> {
                    notifier.schedule(
                        tag = tag,
                        delaySeconds = 0,
                        title = "Low stock: ${supply.name}",
                        body = "~${daysRemaining.toInt()} days remaining. Time to reorder.",
                    )
                }
                else -> {
                    // Schedule the reminder to fire when daysRemaining hits the threshold
                    val daysUntilThreshold = daysRemaining - thresholdDays
                    val delaySeconds = (daysUntilThreshold * 86_400).toInt().coerceAtLeast(0)
                    notifier.schedule(
                        tag = tag,
                        delaySeconds = delaySeconds,
                        title = "Time to reorder ${supply.name}",
                        body = "Your ${supply.name} supply will run out in ~$thresholdDays days.",
                    )
                }
            }
        }
    }
}
