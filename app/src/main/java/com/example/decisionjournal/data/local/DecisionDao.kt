package com.example.decisionjournal.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.decisionjournal.data.model.Choice
import com.example.decisionjournal.data.model.Decision
import com.example.decisionjournal.data.model.ReminderState
import com.example.decisionjournal.data.model.Review
import kotlinx.coroutines.flow.Flow

@Dao
abstract class DecisionDao {
    @Query("SELECT * FROM decisions ORDER BY decisionDate DESC, id DESC")
    abstract fun observeAll(): Flow<List<Decision>>

    @Query("SELECT * FROM decisions ORDER BY id ASC")
    abstract suspend fun getAll(): List<Decision>

    @Query("SELECT * FROM decisions WHERE id = :id")
    abstract fun observeById(id: Long): Flow<Decision?>

    @Query("SELECT * FROM decisions WHERE id = :id")
    abstract suspend fun getById(id: Long): Decision?

    @Query("SELECT * FROM choices WHERE decisionId = :decisionId ORDER BY position ASC")
    abstract fun observeChoices(decisionId: Long): Flow<List<Choice>>

    @Query("SELECT * FROM choices ORDER BY decisionId ASC, position ASC")
    abstract fun observeAllChoices(): Flow<List<Choice>>

    @Query("SELECT * FROM reviews WHERE decisionId = :decisionId ORDER BY createdAt DESC")
    abstract fun observeReviews(decisionId: Long): Flow<List<Review>>

    @Query("SELECT * FROM reviews ORDER BY decisionId ASC, createdAt DESC")
    abstract fun observeAllReviews(): Flow<List<Review>>

    @Query("SELECT DISTINCT decisionId FROM reviews")
    abstract fun observeReviewedDecisionIds(): Flow<List<Long>>

    @Insert abstract suspend fun insertDecision(decision: Decision): Long
    @Update abstract suspend fun updateDecision(decision: Decision): Int
    @Insert abstract suspend fun insertChoices(choices: List<Choice>): List<Long>
    @Query("DELETE FROM choices WHERE decisionId = :decisionId") abstract suspend fun deleteChoices(decisionId: Long)
    @Insert abstract suspend fun insertReview(review: Review): Long
    @Update abstract suspend fun updateReview(review: Review): Int
    @Query("SELECT * FROM reviews WHERE id = :id") abstract suspend fun getReview(id: Long): Review?
    @Query("DELETE FROM reviews WHERE id = :reviewId AND decisionId = :decisionId") abstract suspend fun deleteReview(reviewId: Long, decisionId: Long): Int
    @Query("SELECT COUNT(*) FROM reviews WHERE decisionId = :decisionId") abstract suspend fun countReviews(decisionId: Long): Int
    @Query("UPDATE decisions SET reviewDate = :nextReviewDate, reviewDateKey = :nextReviewDateKey, reminderAt = :nextReminderAt, status = :status, updatedAt = :updatedAt WHERE id = :id")
    abstract suspend fun updateReviewSchedule(id: Long, nextReviewDate: Long?, nextReminderAt: Long?, nextReviewDateKey: String?, status: com.example.decisionjournal.data.model.DecisionStatus, updatedAt: Long): Int
    @Query("UPDATE decisions SET reminderAt = :reminderAt, reviewDateKey = :reviewDateKey WHERE id = :id")
    abstract suspend fun updateReminderAt(id: Long, reminderAt: Long?, reviewDateKey: String?): Int
    @Query("UPDATE decisions SET reminderState = :state WHERE id = :id")
    abstract suspend fun updateReminderState(id: Long, state: ReminderState): Int
    @Query("DELETE FROM reviews WHERE decisionId = :decisionId") abstract suspend fun deleteReviews(decisionId: Long)
    @Query("DELETE FROM decisions WHERE id = :id") abstract suspend fun deleteDecision(id: Long)

    @Transaction
    open suspend fun deleteReviewAndUpdateDecision(reviewId: Long, decisionId: Long, nextReviewDate: Long?, nextReviewDateKey: String?, updatedAt: Long): Int {
        check(getById(decisionId) != null) { "这条决定不存在或已被删除" }
        check(deleteReview(reviewId, decisionId) == 1) { "这条复盘不存在或已被删除" }
        val remaining = countReviews(decisionId)
        val current = getById(decisionId) ?: error("这条决定不存在或已被删除")
        val date = if (remaining == 0 && nextReviewDate == null) null else nextReviewDate
        return updateReviewSchedule(decisionId, date, if (date == null) null else current.reminderAt, if (date == null) null else nextReviewDateKey, if (date == null) com.example.decisionjournal.data.model.DecisionStatus.ACTIVE else current.status, updatedAt)
    }

    @Transaction
    open suspend fun saveReview(review: Review, nextReviewDate: Long?, nextReminderAt: Long?, nextReviewDateKey: String?, updatedAt: Long): Long {
        check(getById(review.decisionId) != null) { "这条决定不存在或已被删除" }
        val id = insertReview(review)
        val updated = updateReviewSchedule(
            review.decisionId,
            nextReviewDate,
            nextReminderAt,
            nextReviewDateKey,
            com.example.decisionjournal.data.DecisionStatusRules.afterReview(nextReviewDate),
            updatedAt,
        )
        check(updated == 1) { "更新决定状态失败" }
        return id
    }

    @Transaction
    open suspend fun save(decision: Decision, choices: List<Choice>): Long {
        val id = if (decision.id == 0L) {
            insertDecision(decision)
        } else {
            check(updateDecision(decision) == 1) { "这条决定不存在或已被删除" }
            decision.id
        }
        deleteChoices(id)
        val inserted = choices.mapIndexed { index, choice -> choice.copy(id = 0, decisionId = id, position = index) }
        val ids = insertChoices(inserted)
        check(updateDecision(decision.copy(id = id, selectedChoiceId = decision.selectedChoiceId?.let { ids.getOrNull(it.toInt()) })) == 1) {
            "保存决定失败"
        }
        return id
    }

    @Transaction
    open suspend fun deleteCascade(id: Long) {
        deleteChoices(id)
        deleteReviews(id)
        deleteDecision(id)
    }
}
