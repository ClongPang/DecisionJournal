package com.example.decisionjournal.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "decisions",
    indices = [
        Index(value = ["decisionDate"]),
        Index(value = ["reviewDate", "status"]),
    ],
)
data class Decision(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val question: String,
    val context: String? = null,
    val benefits: List<String> = emptyList(),
    val concerns: List<String> = emptyList(),
    val futureNote: String? = null,
    val expectedOutcome: String? = null,
    val confidence: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val decisionDate: Long = createdAt,
    val reviewDate: Long? = null,
    val reminderAt: Long? = null,
    val reminderState: ReminderState = ReminderState.NOT_APPLICABLE,
    val status: DecisionStatus = DecisionStatus.ACTIVE,
    val selectedChoiceId: Long? = null,
)

enum class DecisionStatus { ACTIVE, REVIEWED, ARCHIVED }

/** Persisted separately from the decision itself so a notification failure never hides content. */
enum class ReminderState {
    NOT_APPLICABLE,
    SCHEDULED,
    PERMISSION_REQUIRED,
    NOTIFICATIONS_DISABLED,
    CHANNEL_DISABLED,
    SCHEDULING_FAILED;

    val needsAttention: Boolean
        get() = this !in setOf(NOT_APPLICABLE, SCHEDULED)

    val userMessage: String?
        get() = when (this) {
            NOT_APPLICABLE, SCHEDULED -> null
            PERMISSION_REQUIRED -> "通知权限未开启，请在系统设置中允许提醒。"
            NOTIFICATIONS_DISABLED -> "系统通知已关闭，请在系统设置中重新开启。"
            CHANNEL_DISABLED -> "“复盘提醒”通知频道已关闭，请在系统设置中重新开启。"
            SCHEDULING_FAILED -> "暂时无法安排提醒，请稍后重试。"
        }
}
