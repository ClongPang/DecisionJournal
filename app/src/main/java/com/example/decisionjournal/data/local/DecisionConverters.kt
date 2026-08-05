package com.example.decisionjournal.data.local

import androidx.room.TypeConverter
import com.example.decisionjournal.data.model.DecisionStatus
import com.example.decisionjournal.data.model.ExpectationMatch
import com.example.decisionjournal.data.model.ReminderState
import java.nio.charset.StandardCharsets
import java.util.Base64

class DecisionConverters {
    @TypeConverter fun fromStatus(value: DecisionStatus): String = value.name
    @TypeConverter fun toStatus(value: String): DecisionStatus =
        runCatching { DecisionStatus.valueOf(value) }.getOrDefault(DecisionStatus.ACTIVE)
    @TypeConverter fun fromReminderState(value: ReminderState): String = value.name
    @TypeConverter fun toReminderState(value: String): ReminderState =
        runCatching { ReminderState.valueOf(value) }.getOrDefault(ReminderState.NOT_APPLICABLE)
    @TypeConverter fun fromExpectationMatch(value: ExpectationMatch?): String? = value?.name
    @TypeConverter fun toExpectationMatch(value: String?): ExpectationMatch? =
        value?.let { runCatching { ExpectationMatch.valueOf(it) }.getOrNull() }

    /**
     * New values are length-safe and preserve arbitrary user text. The old separator format
     * remains readable so existing databases do not need a destructive migration.
     */
    @TypeConverter fun fromStringList(value: List<String>): String =
        LIST_PREFIX + value.joinToString(",") { encoded(it) }

    @TypeConverter fun toStringList(value: String): List<String> {
        if (value.isEmpty()) return emptyList()
        if (value.startsWith(LIST_PREFIX)) {
            // An empty payload is the serialized empty list, not a broken entry.
            val payload = value.removePrefix(LIST_PREFIX)
            if (payload.isEmpty()) return emptyList()
            // New values are length-safe. A legacy value that merely starts with the prefix
            // (or a truncated payload) must not silently drop the user's text, so only accept
            // the new format when every segment really is a decodable base64 entry.
            val decoded = payload.split(',').map(::decoded)
            if (decoded.none { it == null }) return decoded.filterNotNull()
        }
        return value.split(LEGACY_SEPARATOR)
    }

    private fun encoded(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decoded(value: String): String? =
        runCatching { String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8) }.getOrNull()

    private companion object {
        const val LIST_PREFIX = "v2:"
        const val LEGACY_SEPARATOR = "\u001f"
    }
}
