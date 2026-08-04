package com.example.decisionjournal.data

import com.example.decisionjournal.data.local.DecisionDao
import com.example.decisionjournal.data.model.Choice
import com.example.decisionjournal.data.model.Decision
import com.example.decisionjournal.data.model.Review
import com.example.decisionjournal.data.model.ExpectationMatch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
)
data class DecisionEditorData(val decision: Decision, val choices: List<Choice>)
data class SaveOutcome(val id: Long, val reminderWarning: String? = null)

class DecisionRepository @Inject constructor(
    private val dao: DecisionDao,
    private val reminderScheduler: ReviewReminderScheduler,
) {
    val decisions = dao.observeAll()
    fun due(now: Long = System.currentTimeMillis()) = dao.observeDue(now)
    @OptIn(ExperimentalCoroutinesApi::class)
    fun due(clock: Flow<Long>): Flow<List<Decision>> = clock.flatMapLatest { dao.observeDue(it) }
    fun observe(id: Long): Flow<Decision?> = dao.observeById(id)
    fun editor(id: Long): Flow<DecisionEditorData?> = combine(dao.observeById(id), dao.observeChoices(id)) { decision, choices ->
        decision?.let { DecisionEditorData(it, choices) }
    }
    fun choices(id: Long) = dao.observeChoices(id)
    fun reviews(id: Long) = dao.observeReviews(id)
    suspend fun save(input: DecisionInput): Result<SaveOutcome> = runCatching {
        val validationError = DecisionValidation.validate(input)
        require(validationError == null) { validationError ?: "决策内容无效" }
        val cleanChoices = DecisionValidation.cleanChoices(input.choices)
        val previous = if (input.id == 0L) null else dao.getById(input.id)
        require(input.id == 0L || previous != null) { "这条决定不存在或已被删除" }
        val now = System.currentTimeMillis()
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
            status = DecisionStatusRules.afterDecisionSave(previous?.status, previous?.reviewDate, input.reviewDate),
            selectedChoiceId = input.selectedChoiceIndex?.toLong(),
        )
        val id = dao.save(decision, cleanChoices.map { Choice(decisionId = 0L, text = it.text, benefits = it.benefits, concerns = it.concerns) })
        val warning = runCatching { reminderScheduler.scheduleOrCancel(id, input.reviewDate) }
            .exceptionOrNull()
            ?.let { "内容已保存，但提醒未安排：${it.message ?: "系统未能创建提醒"}" }
        SaveOutcome(id, warning)
    }
    suspend fun review(input: ReviewInput): Result<SaveOutcome> = runCatching {
        val validationError = ReviewValidation.validate(input)
        require(validationError == null) { validationError ?: "复盘内容无效" }
        require(dao.getById(input.decisionId) != null) { "这条决定不存在或已被删除" }
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
            System.currentTimeMillis(),
        )
        val warning = runCatching { reminderScheduler.scheduleOrCancel(input.decisionId, input.nextReviewDate) }
            .exceptionOrNull()
            ?.let { "复盘已保存，但提醒未安排：${it.message ?: "系统未能创建提醒"}" }
        SaveOutcome(id, warning)
    }

    suspend fun retryReminder(decisionId: Long): Result<Unit> = runCatching {
        val decision = dao.getById(decisionId) ?: error("这条决定不存在或已被删除")
        reminderScheduler.scheduleOrCancel(decisionId, decision.reviewDate)
    }
    suspend fun delete(id: Long) {
        reminderScheduler.cancel(id)
        dao.deleteCascade(id)
    }
}
