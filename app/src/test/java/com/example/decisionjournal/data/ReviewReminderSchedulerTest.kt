package com.example.decisionjournal.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReviewReminderSchedulerTest {
    @Test
    fun notificationTagsStayUniqueWhenLongIdsHaveTheSameLegacyHashCode() {
        val firstId = 1L
        val secondId = 1L shl 32

        assertEquals(firstId.hashCode(), secondId.hashCode())
        assertNotEquals(reviewNotificationTag(firstId), reviewNotificationTag(secondId))
    }
}
