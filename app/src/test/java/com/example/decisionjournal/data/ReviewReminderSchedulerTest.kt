package com.example.decisionjournal.data

import com.example.decisionjournal.data.local.DecisionDao
import com.example.decisionjournal.data.model.Choice
import com.example.decisionjournal.data.model.Decision
import com.example.decisionjournal.data.model.DecisionStatus
import com.example.decisionjournal.data.model.ReminderState
import com.example.decisionjournal.data.model.Review
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
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

    @Test
    fun calendarDateKeyStaysStableAcrossTimezonesWhileReminderFollowsLocalTime() {
        val key = "2026-08-20"
        val shanghai = ZoneId.of("Asia/Shanghai")
        val losAngeles = ZoneId.of("America/Los_Angeles")
        val kiritimati = ZoneId.of("Pacific/Kiritimati")
        val legacyInstant = LocalDate.of(2026, 8, 20).atStartOfDay(shanghai).toInstant().toEpochMilli()
        val decision = Decision(id = 7, question = "跨时区", reviewDate = legacyInstant, reviewDateKey = key)
        val beforeDate = LocalDate.of(2026, 8, 18).atTime(12, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()

        assertEquals(LocalDate.of(2026, 8, 20), localReviewDate(decision, losAngeles))
        assertEquals(LocalDate.of(2026, 8, 20), localReviewDate(decision, kiritimati))
        assertEquals(false, isReviewDue(decision, beforeDate, losAngeles))
        assertEquals(false, isReviewDue(decision, beforeDate, kiritimati))
        assertEquals(
            LocalDate.of(2026, 8, 20).atTime(20, 0).atZone(losAngeles).toInstant().toEpochMilli(),
            reviewReminderAt(legacyInstant, beforeDate, losAngeles, key),
        )
    }

    @Test
    fun editingReviewAppliesChangedNextReviewDateAndReminder() = runBlocking {
        val dao = FakeDecisionDao()
        val scheduler = FakeReminderScheduler()
        val repo = DecisionRepository(dao, scheduler)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val originalDate = today.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
        val decisionId = dao.insertDecision(
            Decision(
                question = "是否换工作",
                reviewDate = originalDate,
                reviewDateKey = today.plusDays(7).toString(),
                reminderAt = today.plusDays(7).atTime(20, 0).atZone(zone).toInstant().toEpochMilli(),
                reminderState = ReminderState.SCHEDULED,
            ),
        )
        val reviewId = dao.insertReview(
            Review(decisionId = decisionId, result = "第一次结果", createdAt = today.atStartOfDay(zone).toInstant().toEpochMilli()),
        )
        val newDate = today.plusDays(30).atStartOfDay(zone).toInstant().toEpochMilli()
        val newKey = today.plusDays(30).toString()
        val expectedReminder = today.plusDays(30).atTime(20, 0).atZone(zone).toInstant().toEpochMilli()

        val outcome = repo.review(
            ReviewInput(
                decisionId = decisionId,
                result = "更正后的结果",
                satisfaction = 4,
                reviewId = reviewId,
                nextReviewDate = newDate,
                nextReviewDateKey = newKey,
            ),
        )

        assertTrue(outcome.isSuccess)
        val saved = dao.decisions.getValue(decisionId)
        assertEquals(newDate, saved.reviewDate)
        assertEquals(newKey, saved.reviewDateKey)
        assertEquals(DecisionStatus.ACTIVE, saved.status)
        assertEquals(expectedReminder, saved.reminderAt)
        assertEquals(ReminderState.SCHEDULED, saved.reminderState)
        assertEquals("更正后的结果", dao.reviews.getValue(reviewId).result)
        assertEquals(Triple(decisionId, newDate, expectedReminder), scheduler.lastSchedule)
    }

    @Test
    fun editingReviewCanClearNextReviewDateAndReminder() = runBlocking {
        val dao = FakeDecisionDao()
        val scheduler = FakeReminderScheduler()
        val repo = DecisionRepository(dao, scheduler)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val decisionId = dao.insertDecision(
            Decision(
                question = "是否换工作",
                reviewDate = today.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli(),
                reviewDateKey = today.plusDays(7).toString(),
                reminderAt = today.plusDays(7).atTime(20, 0).atZone(zone).toInstant().toEpochMilli(),
                reminderState = ReminderState.SCHEDULED,
            ),
        )
        val reviewId = dao.insertReview(
            Review(decisionId = decisionId, result = "第一次结果", createdAt = today.atStartOfDay(zone).toInstant().toEpochMilli()),
        )

        val outcome = repo.review(
            ReviewInput(decisionId = decisionId, result = "最终结果", satisfaction = null, reviewId = reviewId),
        )

        assertTrue(outcome.isSuccess)
        val saved = dao.decisions.getValue(decisionId)
        assertEquals(null, saved.reviewDate)
        assertEquals(null, saved.reviewDateKey)
        assertEquals(DecisionStatus.REVIEWED, saved.status)
        assertEquals(null, saved.reminderAt)
        assertEquals(ReminderState.NOT_APPLICABLE, saved.reminderState)
        assertEquals(Triple(decisionId, null, null), scheduler.lastSchedule)
    }

    @Test
    fun refreshReminderStateRebuildsTheTaskWhenPermissionBecomesAvailable() = runBlocking {
        val dao = FakeDecisionDao()
        val scheduler = FakeReminderScheduler()
        val repo = DecisionRepository(dao, scheduler)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val reviewDate = today.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
        val reminderAt = today.plusDays(7).atTime(20, 0).atZone(zone).toInstant().toEpochMilli()
        val decisionId = dao.insertDecision(
            // The saved record already holds the expected reminder epoch, but no WorkManager
            // request was enqueued because notification permission was missing at save time.
            Decision(
                question = "是否换工作",
                reviewDate = reviewDate,
                reviewDateKey = today.plusDays(7).toString(),
                reminderAt = reminderAt,
                reminderState = ReminderState.PERMISSION_REQUIRED,
            ),
        )
        scheduler.lastSchedule = null

        val restored = repo.refreshReminderState(decisionId)

        assertTrue(restored)
        assertEquals(ReminderState.SCHEDULED, dao.decisions.getValue(decisionId).reminderState)
        // Merely persisting SCHEDULED would promise a notification that never fires. The task
        // must actually be re-enqueued.
        assertEquals(Triple(decisionId, reviewDate, reminderAt), scheduler.lastSchedule)
    }

    @Test
    fun refreshReminderStateDoesNotClaimRestoredWhenStillUnavailable() = runBlocking {
        val dao = FakeDecisionDao()
        val scheduler = UnavailableReminderScheduler()
        val repo = DecisionRepository(dao, scheduler)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val reviewDate = today.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
        val reminderAt = today.plusDays(7).atTime(20, 0).atZone(zone).toInstant().toEpochMilli()
        val decisionId = dao.insertDecision(
            Decision(
                question = "是否换工作",
                reviewDate = reviewDate,
                reviewDateKey = today.plusDays(7).toString(),
                reminderAt = reminderAt,
                reminderState = ReminderState.PERMISSION_REQUIRED,
            ),
        )

        val restored = repo.refreshReminderState(decisionId)

        assertFalse(restored)
        assertEquals(ReminderState.PERMISSION_REQUIRED, dao.decisions.getValue(decisionId).reminderState)
    }
}

private class FakeDecisionDao : DecisionDao() {
    val decisions = mutableMapOf<Long, Decision>()
    val reviews = mutableMapOf<Long, Review>()
    private var nextDecisionId = 1L
    private var nextReviewId = 1L

    override suspend fun getAll(): List<Decision> = decisions.values.sortedBy { it.id }
    override suspend fun getById(id: Long): Decision? = decisions[id]
    override fun observeAll(): Flow<List<Decision>> = flowOf(decisions.values.toList())
    override fun observeById(id: Long): Flow<Decision?> = flowOf(decisions[id])
    override suspend fun insertDecision(decision: Decision): Long {
        val id = if (decision.id == 0L) nextDecisionId++ else decision.id
        decisions[id] = decision.copy(id = id)
        return id
    }
    override suspend fun updateDecision(decision: Decision): Int {
        if (decisions[decision.id] == null) return 0
        decisions[decision.id] = decision
        return 1
    }
    override suspend fun insertChoices(choices: List<Choice>): List<Long> = choices.map { 1L }
    override suspend fun deleteChoices(decisionId: Long) {}
    override fun observeChoices(decisionId: Long): Flow<List<Choice>> = flowOf(emptyList())
    override fun observeAllChoices(): Flow<List<Choice>> = flowOf(emptyList())
    override suspend fun insertReview(review: Review): Long {
        val id = if (review.id == 0L) nextReviewId++ else review.id
        reviews[id] = review.copy(id = id)
        return id
    }
    override suspend fun updateReview(review: Review): Int {
        if (reviews[review.id] == null) return 0
        reviews[review.id] = review
        return 1
    }
    override suspend fun getReview(id: Long): Review? = reviews[id]
    override suspend fun deleteReview(reviewId: Long, decisionId: Long): Int =
        if (reviews.remove(reviewId) != null) 1 else 0
    override suspend fun countReviews(decisionId: Long): Int = reviews.values.count { it.decisionId == decisionId }
    override suspend fun updateReviewSchedule(id: Long, nextReviewDate: Long?, nextReminderAt: Long?, nextReviewDateKey: String?, status: DecisionStatus, updatedAt: Long): Int {
        val decision = decisions[id] ?: return 0
        decisions[id] = decision.copy(reviewDate = nextReviewDate, reminderAt = nextReminderAt, reviewDateKey = nextReviewDateKey, status = status, updatedAt = updatedAt)
        return 1
    }
    override suspend fun updateReminderAt(id: Long, reminderAt: Long?, reviewDateKey: String?): Int {
        val decision = decisions[id] ?: return 0
        decisions[id] = decision.copy(reminderAt = reminderAt, reviewDateKey = reviewDateKey)
        return 1
    }
    override suspend fun updateReminderState(id: Long, state: ReminderState): Int {
        val decision = decisions[id] ?: return 0
        decisions[id] = decision.copy(reminderState = state)
        return 1
    }
    override fun observeReviews(decisionId: Long): Flow<List<Review>> =
        flowOf(reviews.values.filter { it.decisionId == decisionId })
    override fun observeAllReviews(): Flow<List<Review>> = flowOf(reviews.values.toList())
    override fun observeReviewedDecisionIds(): Flow<List<Long>> =
        flowOf(reviews.values.map { it.decisionId }.distinct())
    override suspend fun deleteReviews(decisionId: Long) {
        reviews.entries.removeAll { it.value.decisionId == decisionId }
    }
    override suspend fun deleteDecision(id: Long) {
        decisions.remove(id)
    }
}

private class FakeReminderScheduler : ReminderScheduler {
    var lastSchedule: Triple<Long, Long?, Long?>? = null
    override fun scheduleOrCancel(decisionId: Long, reviewDate: Long?, reminderAt: Long?): ReminderState {
        lastSchedule = Triple(decisionId, reviewDate, reminderAt)
        return if (reviewDate == null || reminderAt == null || reminderAt <= System.currentTimeMillis()) {
            ReminderState.NOT_APPLICABLE
        } else {
            ReminderState.SCHEDULED
        }
    }
    override fun notificationAvailability(): ReminderState? = null
    override fun cancel(decisionId: Long) {}
}

/** Simulates the delivery path still being unavailable (permission denied, notifications off). */
private class UnavailableReminderScheduler : ReminderScheduler {
    override fun scheduleOrCancel(decisionId: Long, reviewDate: Long?, reminderAt: Long?): ReminderState = ReminderState.PERMISSION_REQUIRED
    override fun notificationAvailability(): ReminderState? = ReminderState.PERMISSION_REQUIRED
    override fun cancel(decisionId: Long) {}
}
