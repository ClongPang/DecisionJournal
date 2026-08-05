package com.example.decisionjournal.data

import com.example.decisionjournal.data.model.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReviewReminderSchedulerTest {
    @Test
    fun notificationTagsStayUniqueWhenLongIdsHaveTheSameLegacyHashCode() {
        val firstId = 1L
        val secondId = 1L shl 32

        assertEquals(firstId.hashCode(), secondId.hashCode())
        assertNotEquals(reviewNotificationTag(firstId), reviewNotificationTag(secondId))
    }

    @Test
    fun onlyTheCurrentReviewDateMayDeliverItsReminder() {
        val oldSchedule = 1_700_000_000_000L
        val rescheduled = oldSchedule + 86_400_000L
        val decision = Decision(id = 4, question = "是否改期？", reviewDate = rescheduled, reminderAt = rescheduled + 3_600_000L)

        assertFalse(isCurrentReviewReminder(decision, oldSchedule))
        assertFalse(isCurrentReviewReminder(decision.copy(reviewDate = null), rescheduled))
        // Existing releases did not persist the task date. Preserve their one-time delivery when
        // the record still has a review date, while all newly scheduled tasks use the strict path.
        assertTrue(isCurrentReviewReminder(decision, 0L))
        assertFalse(isCurrentReviewReminder(decision, rescheduled, rescheduled + 7_200_000L))
        assertFalse(isCurrentReviewReminder(decision, rescheduled))
        assertTrue(isCurrentReviewReminder(decision, rescheduled, decision.reminderAt!!))
    }

    @Test
    fun futureReviewDatesNotifyAtLocalEveningButTodayStaysImmediatelyDue() {
        val zone = ZoneId.of("Asia/Shanghai")
        val today = LocalDate.of(2026, 8, 5)
        val now = today.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val tomorrowStart = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        assertEquals(null, reviewReminderAt(todayStart, now, zone))
        val expected = today.plusDays(1).atTime(20, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, reviewReminderAt(tomorrowStart, now, zone))
    }
}
