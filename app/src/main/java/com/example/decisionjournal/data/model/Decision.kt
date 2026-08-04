package com.example.decisionjournal.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "decisions")
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
    val status: DecisionStatus = DecisionStatus.ACTIVE,
    val selectedChoiceId: Long? = null,
)

enum class DecisionStatus { ACTIVE, REVIEWED, ARCHIVED }
