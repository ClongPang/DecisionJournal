package com.example.decisionjournal.ui

import com.example.decisionjournal.data.model.Decision
import com.example.decisionjournal.data.model.DecisionStatus
import com.example.decisionjournal.data.DecisionSearchFields
import com.example.decisionjournal.data.isReviewDue
import com.example.decisionjournal.data.isReviewUpcoming
import java.time.LocalDate
import java.time.ZoneId

data class DecisionStats(
    val completedCount: Int,
    val dueCount: Int,
    val mostCaredAbout: String?,
    val mostCaredAboutEvidenceCount: Int = 0,
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
    data object Due : DecisionFilter
    data object Upcoming : DecisionFilter
    data object Reviewed : DecisionFilter
    data object Unscheduled : DecisionFilter
    data class Preset(val period: DecisionPeriod) : DecisionFilter
    data class Custom(val range: CustomDateRange) : DecisionFilter
}

data class PeriodCounts(
    val today: Int = 0,
    val week: Int = 0,
    val month: Int = 0,
    val year: Int = 0,
)

data class DecisionStatusCounts(
    val due: Int = 0,
    val upcoming: Int = 0,
    val reviewed: Int = 0,
    val unscheduled: Int = 0,
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

fun calculateDecisionStatusCounts(decisions: List<Decision>, now: Long): DecisionStatusCounts =
    DecisionStatusCounts(
        due = decisions.count { isReviewDue(it, now) && it.status != DecisionStatus.REVIEWED },
        upcoming = decisions.count { isReviewUpcoming(it, now) && it.status != DecisionStatus.REVIEWED },
        reviewed = decisions.count { it.status == DecisionStatus.REVIEWED },
        unscheduled = decisions.count { it.reviewDate == null && it.status != DecisionStatus.REVIEWED },
    )

fun filterDecisions(
    decisions: List<Decision>,
    filter: DecisionFilter,
    date: LocalDate,
    zone: ZoneId,
    now: Long = System.currentTimeMillis(),
): List<Decision> {
    val range = when (filter) {
        DecisionFilter.All -> null
        DecisionFilter.Due -> null
        DecisionFilter.Upcoming -> null
        DecisionFilter.Reviewed -> null
        DecisionFilter.Unscheduled -> null
        is DecisionFilter.Preset -> dateTimeRange(filter.period, date, zone)
        is DecisionFilter.Custom -> dateTimeRange(filter.range, zone)
    }
    return decisions
        .asSequence()
        .filter { range == null || it.decisionDate in range.startInclusive until range.endExclusive }
        .filter {
            when (filter) {
                DecisionFilter.Due -> isReviewDue(it, now) && it.status != DecisionStatus.REVIEWED
                DecisionFilter.Upcoming -> isReviewUpcoming(it, now) && it.status != DecisionStatus.REVIEWED
                DecisionFilter.Reviewed -> it.status == DecisionStatus.REVIEWED
                DecisionFilter.Unscheduled -> it.reviewDate == null && it.status != DecisionStatus.REVIEWED
                else -> true
            }
        }
        .sortedWith(compareByDescending<Decision> { it.decisionDate }.thenByDescending { it.id })
        .toList()
}

/** Filters the already-selected archive scope without changing its sort order. */
fun searchDecisions(
    decisions: List<Decision>,
    query: String,
    searchFields: List<DecisionSearchFields> = emptyList(),
): List<Decision> {
    val keyword = query.trim()
    if (keyword.isEmpty()) return decisions
    val fieldsByDecision = searchFields.associateBy { it.decisionId }
    return decisions.filter { decision ->
        buildList {
            add(decision.question)
            decision.context?.let(::add)
            decision.futureNote?.let(::add)
            decision.expectedOutcome?.let(::add)
            addAll(decision.benefits)
            addAll(decision.concerns)
            addAll(fieldsByDecision[decision.id]?.terms.orEmpty())
        }.any { it.contains(keyword, ignoreCase = true) }
    }
}

/** Returns a short, user-facing reason for a search hit without exposing storage details. */
fun searchMatchSource(
    decision: Decision,
    query: String,
    searchFields: List<DecisionSearchFields> = emptyList(),
): String? {
    val keyword = query.trim()
    if (keyword.isEmpty()) return null
    fun contains(values: List<String?>): Boolean = values.filterNotNull().any { it.contains(keyword, ignoreCase = true) }
    return when {
        contains(listOf(decision.question)) -> "问题"
        contains(listOf(decision.context, decision.futureNote, decision.expectedOutcome)) -> "背景或写给未来"
        contains(decision.benefits + decision.concerns) -> "在意或担心"
        contains(searchFields.firstOrNull { it.decisionId == decision.id }?.terms.orEmpty()) -> "候选项或复盘内容"
        else -> null
    }
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
        .flatMap { decision -> decision.benefits.map(String::trim).filter(String::isNotEmpty).distinct() }
        .groupingBy { it }
        .eachCount()
        .maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key })

    return DecisionStats(
        completedCount = decisions.count { it.status == DecisionStatus.REVIEWED },
        dueCount = decisions.count { isReviewDue(it, now) && it.status != DecisionStatus.REVIEWED },
        mostCaredAbout = caredAbout?.key,
        mostCaredAboutEvidenceCount = caredAbout?.value ?: 0,
    )
}

/**
 * 只生成可追溯的描述性观察；记录不足时不强行给用户下结论。
 */
fun calculateSelfInsights(decisions: List<Decision>, minimumEvidence: Int = 3): List<SelfInsight> {
    fun frequency(values: (Decision) -> List<String>): Pair<String, Int>? = decisions
        .flatMapIndexed { index, decision ->
            values(decision)
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct()
                .map { it to index }
        }
        .groupingBy { it.first }
        .eachCount()
        .maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        ?.let { it.key to it.value }

    val cared = frequency { it.benefits }
    val concerns = frequency { it.concerns }
    return buildList {
        if (cared != null && cared.second >= minimumEvidence) {
            add(SelfInsight("你反复在意", cared.first, cared.second))
        }
        if (concerns != null && concerns.second >= minimumEvidence) {
            add(SelfInsight("你经常担心", concerns.first, concerns.second))
        }
    }
}
