package com.example.decisionjournal.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "choices",
    indices = [Index(value = ["decisionId"])],
)
data class Choice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val decisionId: Long,
    val text: String,
    val benefits: List<String> = emptyList(),
    val concerns: List<String> = emptyList(),
    val position: Int = 0,
)
