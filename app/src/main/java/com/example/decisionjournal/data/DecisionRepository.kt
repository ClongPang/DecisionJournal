package com.example.decisionjournal.data

import com.example.decisionjournal.data.local.DecisionDao
import com.example.decisionjournal.data.local.DecisionDetail
import com.example.decisionjournal.data.model.Choice
import com.example.decisionjournal.data.model.Decision
import com.example.decisionjournal.data.model.Review
import com.example.decisionjournal.data.model.ExpectationMatch
import kotlinx.coroutines.flow.Flow
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

class DecisionRepository @Inject constructor(
    private val dao: DecisionDao,
    private val reminderScheduler: ReviewReminderScheduler,
) {
    val decisions = dao.observeAll()
    fun due(now: Long = System.currentTimeMillis()) = dao.observeDue(now)
    fun observe(id: Long): Flow<Decision?> = dao.observeById(id)
    fun choices(id: Long) = dao.observeChoices(id)
    fun reviews(id: Long) = dao.observeReviews(id)
    suspend fun save(input: DecisionInput): Result<Long> = runCatching {
        require(DecisionValidation.validate(input) == null)
        val cleanChoices = DecisionValidation.cleanChoices(input.choices)
        val previous = if (input.id == 0L) null else dao.getById(input.id)
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
            reviewDate = input.reviewDate,
            status = com.example.decisionjournal.data.model.DecisionStatus.ACTIVE,
            selectedChoiceId = input.selectedChoiceIndex?.toLong(),
        )
        val id = dao.save(decision, cleanChoices.map { Choice(decisionId = input.id, text = it.text, benefits = it.benefits, concerns = it.concerns) })
        reminderScheduler.scheduleOrCancel(id, input.reviewDate)
        id
    }
    suspend fun review(input: ReviewInput): Result<Long> = runCatching {
        require(ReviewValidation.validate(input) == null)
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
        reminderScheduler.scheduleOrCancel(input.decisionId, input.nextReviewDate)
        id
    }
    suspend fun delete(id: Long) {
        reminderScheduler.cancel(id)
        dao.deleteCascade(id)
    }
}
