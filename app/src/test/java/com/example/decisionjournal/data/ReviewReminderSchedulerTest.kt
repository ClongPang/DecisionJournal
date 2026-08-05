package com.example.decisionjournal.data

import com.example.decisionjournal.data.model.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
        val decision = Decision(id = 4, question = "是否改期？", reviewDate = rescheduled)

        assertFalse(isCurrentReviewReminder(decision, oldSchedule))
        assertFalse(isCurrentReviewReminder(decision.copy(reviewDate = null), rescheduled))
        assertTrue(isCurrentReviewReminder(decision, rescheduled))
        // Existing releases did not persist the task date. Preserve their one-time delivery when
        // the record still has a review date, while all newly scheduled tasks use the strict path.
        assertTrue(isCurrentReviewReminder(decision, 0L))
    }
}
