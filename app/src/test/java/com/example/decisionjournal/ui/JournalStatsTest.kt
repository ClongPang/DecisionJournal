package com.example.decisionjournal.ui

import com.example.decisionjournal.data.model.Decision
import com.example.decisionjournal.data.model.DecisionStatus
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class JournalStatsTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun periodCountsUseCreatedAtAndNaturalCalendarBoundaries() {
        val date = LocalDate.of(2026, 8, 5)
        fun at(day: LocalDate, hour: Int = 12): Long = day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
        val decisions = listOf(
            Decision(question = "今日", createdAt = at(date)),
            Decision(question = "本周一", createdAt = at(LocalDate.of(2026, 8, 3))),
            Decision(question = "本月一", createdAt = at(LocalDate.of(2026, 8, 1))),
            Decision(question = "今年一", createdAt = at(LocalDate.of(2026, 1, 1))),
            Decision(question = "上月", createdAt = at(LocalDate.of(2026, 7, 31))),
            Decision(question = "去年", createdAt = at(LocalDate.of(2025, 12, 31))),
        )

        assertEquals(1, calculatePeriodCounts(decisions, date, zone).today)
        assertEquals(2, calculatePeriodCounts(decisions, date, zone).week)
        assertEquals(3, calculatePeriodCounts(decisions, date, zone).month)
        assertEquals(5, calculatePeriodCounts(decisions, date, zone).year)
    }

    @Test
    fun customDateRangeIncludesEndDateAndAllFilterSortsByCreatedAt() {
        val date = LocalDate.of(2026, 8, 5)
        fun at(day: LocalDate): Long = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val decisions = listOf(
            Decision(id = 1, question = "早", createdAt = at(date)),
            Decision(id = 2, question = "晚", createdAt = at(date.plusDays(1))),
            Decision(id = 3, question = "范围外", createdAt = at(date.plusDays(2))),
        )

        val result = filterDecisions(
            decisions,
            DecisionFilter.Custom(CustomDateRange(date, date.plusDays(1))),
            date,
            zone,
        )

        assertEquals(listOf("晚", "早"), result.map { it.question })
        assertEquals(listOf("范围外", "晚", "早"), filterDecisions(decisions, DecisionFilter.All, date, zone).map { it.question })
    }
    @Test
    fun calculatesCompletedDueAndMostCaredAbout() {
        val decisions = listOf(
            Decision(question = "已回看", status = DecisionStatus.REVIEWED, benefits = listOf("生活平衡")),
            Decision(question = "已到期", reviewDate = 900L, benefits = listOf("成长机会", "生活平衡")),
            Decision(question = "未来", reviewDate = 2_000L, benefits = listOf("生活平衡")),
        )

        val stats = calculateDecisionStats(decisions, now = 1_000L)

        assertEquals(1, stats.completedCount)
        assertEquals(1, stats.dueCount)
        assertEquals("生活平衡", stats.mostCaredAbout)
    }

    @Test
    fun emptyBenefitsHaveNoMostCaredAbout() {
        val stats = calculateDecisionStats(listOf(Decision(question = "问题")), now = 1_000L)

        assertEquals(null, stats.mostCaredAbout)
    }

    @Test
    fun selfInsightsRequireTraceableEvidence() {
        val decisions = listOf(
            Decision(question = "一", benefits = listOf("成长"), concerns = listOf("压力")),
            Decision(question = "二", benefits = listOf("成长"), concerns = listOf("压力")),
            Decision(question = "三", benefits = listOf("成长"), concerns = listOf("压力")),
        )

        val insights = calculateSelfInsights(decisions)

        assertEquals(listOf("你最近反复在意", "你经常担心"), insights.map { it.title })
        assertEquals(3, insights.first().evidenceCount)
    }

    @Test
    fun selfInsightsAreNotGeneratedWithInsufficientEvidence() {
        val decisions = listOf(Decision(question = "一", benefits = listOf("成长")))

        assertEquals(emptyList<SelfInsight>(), calculateSelfInsights(decisions))
    }
}
