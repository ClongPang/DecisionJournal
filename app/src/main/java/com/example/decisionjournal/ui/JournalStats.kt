package com.example.decisionjournal.ui

import com.example.decisionjournal.data.model.Decision
import java.time.LocalDate
import java.time.ZoneId

data class DecisionStats(
    val completedCount: Int,
    val dueCount: Int,
    val mostCaredAbout: String?,
)

data class SelfInsight(
    val title: String,
    val description: String,
    val evidenceCount: Int,
)

enum class DecisionPeriod { TODAY, WEEK, MONTH, YEAR }

data class DateTimeRange(val startInclusive: Long, val endExclusive: Long)

data class CustomDateRange(val start: LocalDate, val endInclusive: LocalDate)

sealed interface DecisionFilter {
    data object All : DecisionFilter
    data class Preset(val period: DecisionPeriod) : DecisionFilter
    data class Custom(val range: CustomDateRange) : DecisionFilter
}

data class PeriodCounts(
    val today: Int = 0,
    val week: Int = 0,
    val month: Int = 0,
    val year: Int = 0,
)

const val INITIAL_DECISION_PAGE_SIZE = 10
const val DECISION_PAGE_SIZE = 20

fun nextDecisionPageSize(currentSize: Int, totalSize: Int): Int =
    (currentSize + DECISION_PAGE_SIZE).coerceAtMost(totalSize)

fun dateTimeRange(period: DecisionPeriod, date: LocalDate, zone: ZoneId): DateTimeRange = when (period) {
    DecisionPeriod.TODAY -> rangeBetween(date, date.plusDays(1), zone)
    DecisionPeriod.WEEK -> {
        val start = date.minusDays((date.dayOfWeek.value - 1).toLong())
        rangeBetween(start, start.plusDays(7), zone)
    }
    DecisionPeriod.MONTH -> {
        val start = date.withDayOfMonth(1)
        rangeBetween(start, start.plusMonths(1), zone)
    }
    DecisionPeriod.YEAR -> {
        val start = date.withDayOfYear(1)
        rangeBetween(start, start.plusYears(1), zone)
    }
}

fun dateTimeRange(range: CustomDateRange, zone: ZoneId): DateTimeRange =
    rangeBetween(range.start, range.endInclusive.plusDays(1), zone)

fun calculatePeriodCounts(decisions: List<Decision>, date: LocalDate, zone: ZoneId): PeriodCounts =
    PeriodCounts(
        today = countCreatedInRange(decisions, dateTimeRange(DecisionPeriod.TODAY, date, zone)),
        week = countCreatedInRange(decisions, dateTimeRange(DecisionPeriod.WEEK, date, zone)),
        month = countCreatedInRange(decisions, dateTimeRange(DecisionPeriod.MONTH, date, zone)),
        year = countCreatedInRange(decisions, dateTimeRange(DecisionPeriod.YEAR, date, zone)),
    )

fun filterDecisions(decisions: List<Decision>, filter: DecisionFilter, date: LocalDate, zone: ZoneId): List<Decision> {
    val range = when (filter) {
        DecisionFilter.All -> null
        is DecisionFilter.Preset -> dateTimeRange(filter.period, date, zone)
        is DecisionFilter.Custom -> dateTimeRange(filter.range, zone)
    }
    return decisions
        .asSequence()
        .filter { range == null || it.decisionDate in range.startInclusive until range.endExclusive }
        .sortedWith(compareByDescending<Decision> { it.decisionDate }.thenByDescending { it.id })
        .toList()
}

private fun countCreatedInRange(decisions: List<Decision>, range: DateTimeRange): Int =
    decisions.count { it.decisionDate in range.startInclusive until range.endExclusive }

private fun rangeBetween(start: LocalDate, endExclusive: LocalDate, zone: ZoneId): DateTimeRange =
    DateTimeRange(
        startInclusive = start.atStartOfDay(zone).toInstant().toEpochMilli(),
        endExclusive = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli(),
    )

fun calculateDecisionStats(decisions: List<Decision>, now: Long): DecisionStats {
    val caredAbout = decisions
        .flatMap { it.benefits }
        .map(String::trim)
        .filter(String::isNotEmpty)
        .groupingBy { it }
        .eachCount()
        .maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        ?.key

    return DecisionStats(
        completedCount = decisions.count { it.status.name == "REVIEWED" },
        dueCount = decisions.count { it.reviewDate != null && it.reviewDate <= now && it.status.name != "REVIEWED" },
        mostCaredAbout = caredAbout,
    )
}

/**
 * 只生成可追溯的描述性观察；记录不足时不强行给用户下结论。
 */
fun calculateSelfInsights(decisions: List<Decision>, minimumEvidence: Int = 3): List<SelfInsight> {
    fun frequency(values: List<String>): Pair<String, Int>? = values
        .map(String::trim)
        .filter(String::isNotEmpty)
        .groupingBy { it }
        .eachCount()
        .maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        ?.let { it.key to it.value }

    val cared = frequency(decisions.flatMap { it.benefits })
    val concerns = frequency(decisions.flatMap { it.concerns })
    return buildList {
        if (cared != null && cared.second >= minimumEvidence) {
            add(SelfInsight("你最近反复在意", cared.first, cared.second))
        }
        if (concerns != null && concerns.second >= minimumEvidence) {
            add(SelfInsight("你经常担心", concerns.first, concerns.second))
        }
    }
}
