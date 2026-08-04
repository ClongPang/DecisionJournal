package com.example.decisionjournal.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.decisionjournal.data.model.Choice
import com.example.decisionjournal.data.model.Decision
import com.example.decisionjournal.data.model.Review
import kotlinx.coroutines.flow.Flow

data class DecisionSummary(val decision: Decision, val choices: List<Choice>)
data class DecisionDetail(val decision: Decision, val choices: List<Choice>, val reviews: List<Review>)

@Dao
abstract class DecisionDao {
    @Query("SELECT * FROM decisions ORDER BY decisionDate DESC, id DESC")
    abstract fun observeAll(): Flow<List<Decision>>

    @Query("SELECT * FROM decisions WHERE reviewDate IS NOT NULL AND reviewDate <= :today AND status != 'REVIEWED' ORDER BY reviewDate ASC")
    abstract fun observeDue(today: Long): Flow<List<Decision>>

    @Query("SELECT * FROM decisions WHERE id = :id")
    abstract fun observeById(id: Long): Flow<Decision?>

    @Query("SELECT * FROM decisions WHERE id = :id")
    abstract suspend fun getById(id: Long): Decision?

    @Query("SELECT * FROM choices WHERE decisionId = :decisionId ORDER BY position ASC")
    abstract fun observeChoices(decisionId: Long): Flow<List<Choice>>

    @Query("SELECT * FROM reviews WHERE decisionId = :decisionId ORDER BY createdAt DESC")
    abstract fun observeReviews(decisionId: Long): Flow<List<Review>>

    @Insert abstract suspend fun insertDecision(decision: Decision): Long
    @Update abstract suspend fun updateDecision(decision: Decision)
    @Insert abstract suspend fun insertChoices(choices: List<Choice>): List<Long>
    @Query("DELETE FROM choices WHERE decisionId = :decisionId") abstract suspend fun deleteChoices(decisionId: Long)
    @Insert abstract suspend fun insertReview(review: Review): Long
    @Query("UPDATE decisions SET reviewDate = :nextReviewDate, status = :status, updatedAt = :updatedAt WHERE id = :id")
    abstract suspend fun updateReviewSchedule(id: Long, nextReviewDate: Long?, status: com.example.decisionjournal.data.model.DecisionStatus, updatedAt: Long)
    @Query("DELETE FROM reviews WHERE decisionId = :decisionId") abstract suspend fun deleteReviews(decisionId: Long)
    @Query("DELETE FROM decisions WHERE id = :id") abstract suspend fun deleteDecision(id: Long)

    @Transaction
    open suspend fun saveReview(review: Review, nextReviewDate: Long?, updatedAt: Long): Long {
        val id = insertReview(review)
        updateReviewSchedule(
            review.decisionId,
            nextReviewDate,
            com.example.decisionjournal.data.DecisionStatusRules.afterReview(nextReviewDate),
            updatedAt,
        )
        return id
    }

    @Transaction
    open suspend fun save(decision: Decision, choices: List<Choice>): Long {
        val id = if (decision.id == 0L) insertDecision(decision) else { updateDecision(decision); decision.id }
        deleteChoices(id)
        val inserted = choices.mapIndexed { index, choice -> choice.copy(id = 0, decisionId = id, position = index) }
        val ids = insertChoices(inserted)
        updateDecision(decision.copy(id = id, selectedChoiceId = decision.selectedChoiceId?.let { ids.getOrNull(it.toInt()) }))
        return id
    }

    @Transaction
    open suspend fun deleteCascade(id: Long) {
        deleteChoices(id)
        deleteReviews(id)
        deleteDecision(id)
    }
}
