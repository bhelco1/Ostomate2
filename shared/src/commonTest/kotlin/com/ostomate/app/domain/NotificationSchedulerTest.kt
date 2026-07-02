package com.ostomate.app.domain

import com.ostomate.app.data.db.ChangeEventEntity
import com.ostomate.app.data.db.ChangeEventWithSupply
import com.ostomate.app.data.db.SupplyTypeEntity
import com.ostomate.app.platform.ReminderNotifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val DAY_MS = 86_400_000L

class NotificationSchedulerTest {
    private class RecordingNotifier : ReminderNotifier {
        data class Scheduled(val tag: String, val delaySeconds: Int, val title: String)

        val scheduled = mutableListOf<Scheduled>()

        override fun schedule(
            tag: String,
            delaySeconds: Int,
            title: String,
            body: String,
        ) {
            scheduled += Scheduled(tag, delaySeconds, title)
        }
    }

    private val notifier = RecordingNotifier()
    private val scheduler = NotificationScheduler(notifier)

    private fun supply(
        id: Long,
        onHand: Int,
        warnThresholdDays: Int = 7,
        name: String = "Bag",
    ) = SupplyTypeEntity(
        id = id,
        name = name,
        kind = SupplyKind.BAG,
        boxSize = 30,
        warnThresholdDays = warnThresholdDays,
        onHand = onHand,
        sortOrder = 0,
    )

    private fun dailyEvents(
        supplyId: Long,
        count: Int,
    ): List<ChangeEventWithSupply> =
        (0 until count).map { i ->
            ChangeEventWithSupply(
                event =
                    ChangeEventEntity(
                        id = i + 1L,
                        supplyTypeId = supplyId,
                        timestampMillis = i * DAY_MS,
                        createdAtMillis = i * DAY_MS,
                    ),
                supplyName = "Bag",
                supplyKind = SupplyKind.BAG,
            )
        }

    @Test
    fun noHistoryMeansNoReminder() {
        scheduler.reschedule(listOf(supply(id = 1, onHand = 10)), emptyMap())
        assertTrue(notifier.scheduled.isEmpty())
    }

    @Test
    fun outOfStockFiresImmediately() {
        scheduler.reschedule(
            listOf(supply(id = 1, onHand = 0)),
            mapOf(1L to dailyEvents(1, count = 3)),
        )
        val call = notifier.scheduled.single()
        assertEquals("reorder-1", call.tag)
        assertEquals(0, call.delaySeconds)
        assertTrue(call.title.contains("out of stock"))
    }

    @Test
    fun belowThresholdWarnsImmediately() {
        // 3 on hand × 1 day/change = 3 days remaining, under the 7-day threshold.
        scheduler.reschedule(
            listOf(supply(id = 1, onHand = 3, warnThresholdDays = 7)),
            mapOf(1L to dailyEvents(1, count = 3)),
        )
        val call = notifier.scheduled.single()
        assertEquals(0, call.delaySeconds)
        assertTrue(call.title.startsWith("Low stock"))
    }

    @Test
    fun healthyStockSchedulesForTheThresholdCrossing() {
        // 10 on hand × 1 day/change = 10 days remaining; threshold 7 → fire in ~3 days.
        scheduler.reschedule(
            listOf(supply(id = 1, onHand = 10, warnThresholdDays = 7)),
            mapOf(1L to dailyEvents(1, count = 3)),
        )
        val call = notifier.scheduled.single()
        assertEquals(3 * 86_400, call.delaySeconds)
        assertTrue(call.title.startsWith("Time to reorder"))
    }

    @Test
    fun eachSupplyGetsItsOwnTaggedReminder() {
        scheduler.reschedule(
            listOf(supply(id = 1, onHand = 0), supply(id = 2, onHand = 0, name = "Flange")),
            mapOf(1L to dailyEvents(1, count = 2), 2L to dailyEvents(2, count = 2)),
        )
        assertEquals(listOf("reorder-1", "reorder-2"), notifier.scheduled.map { it.tag })
    }
}
