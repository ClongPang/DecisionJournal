package com.example.decisionjournal.data

import com.example.decisionjournal.data.model.Decision
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Review dates are calendar dates, not instants. The key keeps a date stable when the device
 * timezone changes; the legacy epoch is retained only for backwards compatibility and scheduling.
 */
internal fun reviewDateKey(timestamp: Long?, zone: ZoneId = ZoneId.systemDefault()): String? =
    timestamp?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toString() }

internal fun reviewDateFromKey(key: String?, zone: ZoneId = ZoneId.systemDefault()): Long? =
    key?.let { LocalDate.parse(it).atStartOfDay(zone).toInstant().toEpochMilli() }

internal fun localReviewDate(decision: Decision, zone: ZoneId = ZoneId.systemDefault()): LocalDate? =
    decision.reviewDateKey?.let(LocalDate::parse)
        ?: decision.reviewDate?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }

internal fun isReviewDue(decision: Decision, now: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
    if (decision.reviewDateKey == null) return decision.reviewDate != null && decision.reviewDate <= now
    val date = localReviewDate(decision, zone) ?: return false
    return !date.isAfter(Instant.ofEpochMilli(now).atZone(zone).toLocalDate())
}

internal fun isReviewUpcoming(decision: Decision, now: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
    if (decision.reviewDateKey == null) return decision.reviewDate != null && decision.reviewDate > now
    val date = localReviewDate(decision, zone) ?: return false
    return date.isAfter(Instant.ofEpochMilli(now).atZone(zone).toLocalDate())
}
