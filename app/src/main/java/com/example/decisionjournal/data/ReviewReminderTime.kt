package com.example.decisionjournal.data

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * A review date answers “which day should I revisit this?”. It remains a local calendar day so
 * due lists change at the beginning of that day. Notification delivery is a separate concern.
 */
internal const val DEFAULT_REMINDER_HOUR = 20
private val defaultReminderTime = LocalTime.of(DEFAULT_REMINDER_HOUR, 0)

internal fun reviewReminderAt(
    reviewDate: Long?,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): Long? {
    val reviewDay = reviewDate?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: return null
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    // Selecting today means “show it in my review list now”, not “surprise me later today”.
    if (!reviewDay.isAfter(today)) return null
    return reviewDay.atTime(defaultReminderTime).atZone(zone).toInstant().toEpochMilli()
}

internal fun reminderTimeLabel(): String = "晚上 8 点左右"
