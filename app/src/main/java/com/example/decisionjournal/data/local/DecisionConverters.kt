package com.example.decisionjournal.data.local

import androidx.room.TypeConverter
import com.example.decisionjournal.data.model.DecisionStatus

class DecisionConverters {
    @TypeConverter fun fromStatus(value: DecisionStatus): String = value.name
    @TypeConverter fun toStatus(value: String): DecisionStatus = DecisionStatus.valueOf(value)
    @TypeConverter fun fromStringList(value: List<String>): String = value.joinToString("\u001f")
    @TypeConverter fun toStringList(value: String): List<String> = if (value.isEmpty()) emptyList() else value.split("\u001f")
}
