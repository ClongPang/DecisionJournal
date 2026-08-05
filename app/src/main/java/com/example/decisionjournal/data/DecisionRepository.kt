package com.example.decisionjournal.data

import com.example.decisionjournal.data.local.DecisionDao
import com.example.decisionjournal.data.model.Choice
import com.example.decisionjournal.data.model.Decision
import com.example.decisionjournal.data.model.Review
import com.example.decisionjournal.data.model.ExpectationMatch
import com.example.decisionjournal.data.model.ReminderState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject

data class ChoiceInput(val text: String, val benefits: List<String> = emptyList(), val concerns: List<String> = emptyList())
data class DecisionInput(
    val id: Long = 0,
    val question: String,
    val context: String?,
    val reviewDate: Long?,
    val selectedChoiceIndex: Int?,
    val choices: List<ChoiceInput>,
    val benefits: List<String> = emptyList(),
    val concerns: List<String> = emptyList(),
    val futureNote: String? = null,
    val expectedOutcome: String? = null,
    val confidence: Int? = null,
    val decisionDate: Long = System.currentTimeMillis(),
    val reviewDateKey: String? = null,
)
data class ReviewInput(
    val decisionId: Long,
    val result: String,
    val satisfaction: Int?,
    val nextReviewDate: Long? = null,
    val expectationMatch: ExpectationMatch? = null,
    val accurateJudgment: String? = null,
    val unexpectedFinding: String? = null,
    val nextTimeNote: String? = null,
    val nextReviewDateKey: String? = null,
)
data class DecisionEditorData(val decision: Decision, val choices: List<Choice>)
data class DecisionSearchFields(val decisionId: Long, val terms: List<String>)
data class SaveOutcome(val id: Long, val reminderWarning: String? = null)

private suspend fun <T> capture(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}

class DecisionRepository @Inject constructor(
    private val dao: DecisionDao,
    private val reminderScheduler: ReviewReminderScheduler,
) {
    val decisions = dao.observeAll()
    val searchFields = combine(dao.observeAllChoices(), dao.observeAllReviews()) { choices, reviews ->
        val termsByDecision = linkedMapOf<Long, MutableList<String>>()
        choices.forEach { choice ->
            termsByDecision.getOrPut(choice.decisionId) { mutableListOf() }.apply {
                add(choice.text)
                addAll(choice.benefits)
                addAll(choice.concerns)
            }
        }
        reviews.forEach { review ->
            termsByDecision.getOrPut(review.decisionId) { mutableListOf() }.apply {
                add(review.result)
                review.accurateJudgment?.let(::add)
                review.unexpectedFinding?.let(::add)
                review.nextTimeNote?.let(::add)
            }
        }
        termsByDecision.map { (decisionId, terms) -> DecisionSearchFields(decisionId, terms) }
    }
    fun due(now: Long = System.currentTimeMillis()) = decisions.map { items ->
        items.filter { isReviewDue(it, now) && it.status != com.example.decisionjournal.data.model.DecisionStatus.REVIEWED }
            .sortedWith(compareBy<Decision> { localReviewDate(it) }.thenByDescending { it.decisionDate })
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    fun due(clock: Flow<Long>): Flow<List<Decision>> = combine(decisions, clock) { items, now ->
        items.filter { isReviewDue(it, now) && it.status != com.example.decisionjournal.data.model.DecisionStatus.REVIEWED }
            .sortedWith(compareBy<Decision> { localReviewDate(it) }.thenByDescending { it.decisionDate })
    }
    fun observe(id: Long): Flow<Decision?> = dao.observeById(id)
    fun editor(id: Long): Flow<DecisionEditorData?> = combine(dao.observeById(id), dao.observeChoices(id)) { decision, choices ->
        decision?.let { DecisionEditorData(it, choices) }
    }
    fun choices(id: Long) = dao.observeChoices(id)
    fun reviews(id: Long) = dao.observeReviews(id)
    suspend fun save(input: DecisionInput): Result<SaveOutcome> = capture {
        val validationError = DecisionValidation.validate(input)
        require(validationError == null) { validationError ?: "决策内容无效" }
        val cleanChoices = DecisionValidation.cleanChoices(input.choices)
        val selectedChoiceIndex = DecisionValidation.normalizedSelectedChoiceIndex(input.choices, input.selectedChoiceIndex)
        val previous = if (input.id == 0L) null else dao.getById(input.id)
        require(input.id == 0L || previous != null) { "这条决定不存在或已被删除" }
        require(DecisionValidation.validateReviewDate(previous?.reviewDate, input.reviewDate) == null) {
            "复盘日期不能早于今天"
        }
        val now = System.currentTimeMillis()
        val normalizedReviewDateKey = input.reviewDateKey ?: reviewDateKey(input.reviewDate)
        val reminderAt = reviewReminderAt(input.reviewDate, now, reviewDateKey = normalizedReviewDateKey)
        val decision = Decision(
            id = input.id,
            question = input.question.trim(),
            context = input.context?.trim()?.ifEmpty { null },
            benefits = input.benefits.map(String::trim).filter(String::isNotEmpty),
            concerns = input.concerns.map(String::trim).filter(String::isNotEmpty),
            futureNote = input.futureNote?.trim()?.takeIf { it.isNotEmpty() },
            expectedOutcome = input.expectedOutcome?.trim()?.takeIf { it.isNotEmpty() },
            confidence = input.confidence,
            createdAt = previous?.createdAt ?: now,
            updatedAt = now,
            decisionDate = input.decisionDate,
            reviewDate = input.reviewDate,
            reminderAt = reminderAt,
            reviewDateKey = normalizedReviewDateKey,
            status = DecisionStatusRules.afterDecisionSave(previous?.status, previous?.reviewDate, input.reviewDate, previous?.reviewDateKey, normalizedReviewDateKey),
            selectedChoiceId = selectedChoiceIndex?.toLong(),
        )
        val id = dao.save(decision, cleanChoices.map { Choice(decisionId = 0L, text = it.text, benefits = it.benefits, concerns = it.concerns) })
        val reminderState = updateReminderState(id, input.reviewDate, reminderAt)
        val warning = reminderWarning("内容已保存", reminderState)
        SaveOutcome(id, warning)
    }
    suspend fun review(input: ReviewInput): Result<SaveOutcome> = capture {
        val validationError = ReviewValidation.validate(input)
        require(validationError == null) { validationError ?: "复盘内容无效" }
        require(dao.getById(input.decisionId) != null) { "这条决定不存在或已被删除" }
        val normalizedNextReviewDateKey = input.nextReviewDateKey ?: reviewDateKey(input.nextReviewDate)
        val reminderAt = reviewReminderAt(input.nextReviewDate, reviewDateKey = normalizedNextReviewDateKey)
        val id = dao.saveReview(
            Review(
                decisionId = input.decisionId,
                result = input.result.trim(),
                satisfaction = input.satisfaction,
                expectationMatch = input.expectationMatch,
                accurateJudgment = input.accurateJudgment?.trim()?.takeIf { it.isNotEmpty() },
                unexpectedFinding = input.unexpectedFinding?.trim()?.takeIf { it.isNotEmpty() },
                nextTimeNote = input.nextTimeNote?.trim()?.takeIf { it.isNotEmpty() },
            ),
            input.nextReviewDate,
            reminderAt,
            normalizedNextReviewDateKey,
            System.currentTimeMillis(),
        )
        val reminderState = updateReminderState(input.decisionId, input.nextReviewDate, reminderAt)
        val warning = reminderWarning("复盘已保存", reminderState)
        SaveOutcome(id, warning)
    }

    suspend fun retryReminder(decisionId: Long): Result<Unit> = capture {
        val decision = dao.getById(decisionId) ?: error("这条决定不存在或已被删除")
        require(isReviewUpcoming(decision, System.currentTimeMillis())) {
            "回看日期已过，无法再安排提醒"
        }
        val state = updateReminderState(decisionId, decision.reviewDate, decision.reminderAt)
        require(state == ReminderState.SCHEDULED) { state.userMessage ?: "暂时无法安排提醒，请稍后重试。" }
    }

    suspend fun refreshReminderState(decisionId: Long): Boolean =
        capture {
            val decision = dao.getById(decisionId) ?: return@capture false
            val reviewDate = decision.reviewDate ?: return@capture false
            val expectedKey = decision.reviewDateKey ?: reviewDateKey(reviewDate)
            val expectedReminderAt = reviewReminderAt(reviewDate, reviewDateKey = expectedKey) ?: return@capture false
            if (decision.reminderAt != expectedReminderAt) {
                check(dao.updateReminderAt(decisionId, expectedReminderAt, expectedKey) == 1) { "更新提醒时间失败" }
                val state = updateReminderState(decisionId, reviewDate, expectedReminderAt)
                return@capture state == ReminderState.SCHEDULED
            }
            val state = reminderScheduler.notificationAvailability() ?: ReminderState.SCHEDULED
            if (state != decision.reminderState) {
                check(dao.updateReminderState(decisionId, state) == 1) { "更新提醒状态失败" }
            }
            decision.reminderState.needsAttention && state == ReminderState.SCHEDULED
        }.getOrDefault(false)

    /** Rebuilds pre-v9 midnight work using the current evening reminder policy after upgrade. */
    suspend fun reconcileReminders() {
        dao.getAll().forEach { decision ->
            val expectedKey = decision.reviewDateKey ?: reviewDateKey(decision.reviewDate)
            val expectedReminderAt = reviewReminderAt(decision.reviewDate, reviewDateKey = expectedKey) ?: return@forEach
            if (decision.reminderAt != expectedReminderAt || decision.reviewDateKey != expectedKey) {
                check(dao.updateReminderAt(decision.id, expectedReminderAt, expectedKey) == 1) { "更新提醒时间失败" }
                updateReminderState(decision.id, decision.reviewDate, expectedReminderAt)
            }
        }
    }

    suspend fun delete(id: Long) {
        // A scheduler failure must not leave the user's local record undeletable.
        try {
            reminderScheduler.cancel(id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The database remains the source of truth for deletion.
        }
        dao.deleteCascade(id)
    }

    private suspend fun updateReminderState(decisionId: Long, reviewDate: Long?, reminderAt: Long?): ReminderState {
        val state = capture { reminderScheduler.scheduleOrCancel(decisionId, reviewDate, reminderAt) }
            .getOrDefault(ReminderState.SCHEDULING_FAILED)
        check(dao.updateReminderState(decisionId, state) == 1) { "更新提醒状态失败" }
        return state
    }

    private fun reminderWarning(savedLabel: String, state: ReminderState): String? =
        state.userMessage?.let { "$savedLabel，但提醒未安排：$it" }
}
