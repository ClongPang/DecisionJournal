package com.example.decisionjournal.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reviews")
data class Review(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val decisionId: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val result: String,
    val satisfaction: Int? = null,
)
