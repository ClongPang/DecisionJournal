package com.example.decisionjournal.ui

import com.example.decisionjournal.data.model.Decision
import com.example.decisionjournal.data.model.DecisionStatus
import com.example.decisionjournal.data.DecisionSearchFields
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import com.example.decisionjournal.ui.screens.reviewIntervalLabel
import org.junit.Test

class JournalStatsTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun decisionPaginationStartsWithTenAndAddsTwentyUntilExhausted() {
        assertEquals(10, INITIAL_DECISION_PAGE_SIZE)
        assertEquals(30, nextDecisionPageSize(INITIAL_DECISION_PAGE_SIZE, 100))
        assertEquals(45, nextDecisionPageSize(30, 45))
        assertEquals(10, nextDecisionPageSize(10, 10))
    }

    @Test
    fun decisionDateDefaultsToCreatedAtButCanBeCustomized() {
        val createdAt = LocalDate.of(2026, 8, 5).atStartOfDay(zone).toInstant().toEpochMilli()
        val defaultDecision = Decision(question = "默认日期", createdAt = createdAt)
        val customDecision = Decision(question = "自定义日期", createdAt = createdAt, decisionDate = createdAt - 86_400_000L)

        assertEquals(createdAt, defaultDecision.decisionDate)
        assertEquals(createdAt - 86_400_000L, customDecision.decisionDate)
    }

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
    fun dueFilterShowsOnlyActiveDecisionsWhoseReviewDateHasArrived() {
        val date = LocalDate.of(2026, 8, 5)
        fun at(day: LocalDate): Long = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val now = at(date)
        val decisions = listOf(
            Decision(question = "已到期", decisionDate = at(date.minusDays(1)), reviewDate = at(date.minusDays(1))),
            Decision(question = "今天回看", decisionDate = now, reviewDate = now),
            Decision(question = "未来回看", reviewDate = at(date.plusDays(1))),
            Decision(question = "已完成", reviewDate = at(date.minusDays(1)), status = DecisionStatus.REVIEWED),
            Decision(question = "未设日期"),
        )

        assertEquals(
            listOf("今天回看", "已到期"),
            filterDecisions(decisions, DecisionFilter.Due, date, zone, now).map { it.question },
        )
    }

    @Test
    fun statusFiltersAndCountsCoverEveryDecisionLifecycleState() {
        val date = LocalDate.of(2026, 8, 5)
        fun at(day: LocalDate): Long = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val now = at(date)
        val decisions = listOf(
            Decision(question = "待回看", reviewDate = at(date.minusDays(1))),
            Decision(question = "等待中", reviewDate = at(date.plusDays(7))),
            Decision(question = "已回看", status = DecisionStatus.REVIEWED),
            Decision(question = "未设日期"),
        )

        assertEquals(DecisionStatusCounts(1, 1, 1, 1), calculateDecisionStatusCounts(decisions, now))
        assertEquals(listOf("等待中"), filterDecisions(decisions, DecisionFilter.Upcoming, date, zone, now).map { it.question })
        assertEquals(listOf("已回看"), filterDecisions(decisions, DecisionFilter.Reviewed, date, zone, now).map { it.question })
        assertEquals(listOf("未设日期"), filterDecisions(decisions, DecisionFilter.Unscheduled, date, zone, now).map { it.question })
    }

    @Test
    fun searchFiltersAcrossDecisionNarrativeFieldsWithoutChangingScopeOrder() {
        val decisions = listOf(
            Decision(question = "是否搬家", context = "想离公司近一点", benefits = listOf("通勤")),
            Decision(question = "学习计划", concerns = listOf("时间成本"), futureNote = "保持节奏"),
            Decision(question = "工作选择", expectedOutcome = "获得成长空间"),
        )

        assertEquals(listOf("是否搬家"), searchDecisions(decisions, "通勤").map { it.question })
        assertEquals(listOf("学习计划"), searchDecisions(decisions, "节奏").map { it.question })
        assertEquals(listOf("工作选择"), searchDecisions(decisions, "成长").map { it.question })
        assertEquals(decisions, searchDecisions(decisions, "   "))
    }

    @Test
    fun searchAlsoMatchesOptionsAndReviewObservations() {
        val decisions = listOf(
            Decision(id = 1, question = "是否搬家"),
            Decision(id = 2, question = "学习计划"),
        )
        val fields = listOf(
            DecisionSearchFields(1, listOf("住到公司附近", "减少通勤")),
            DecisionSearchFields(2, listOf("复盘后发现难以坚持", "下次缩小目标")),
        )

        assertEquals(listOf("是否搬家"), searchDecisions(decisions, "通勤", fields).map { it.question })
        assertEquals(listOf("学习计划"), searchDecisions(decisions, "缩小目标", fields).map { it.question })
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
        assertEquals(3, stats.mostCaredAboutEvidenceCount)
    }

    @Test
    fun emptyBenefitsHaveNoMostCaredAbout() {
        val stats = calculateDecisionStats(listOf(Decision(question = "问题")), now = 1_000L)

        assertEquals(null, stats.mostCaredAbout)
        assertEquals(0, stats.mostCaredAboutEvidenceCount)
    }

    @Test
    fun selfInsightsRequireTraceableEvidence() {
        val decisions = listOf(
            Decision(question = "一", benefits = listOf("成长"), concerns = listOf("压力")),
            Decision(question = "二", benefits = listOf("成长"), concerns = listOf("压力")),
            Decision(question = "三", benefits = listOf("成长"), concerns = listOf("压力")),
        )

        val insights = calculateSelfInsights(decisions)

        assertEquals(listOf("你反复在意", "你经常担心"), insights.map { it.title })
        assertEquals(3, insights.first().evidenceCount)
    }

    @Test
    fun selfInsightsCountDistinctDecisionsNotRepeatedLines() {
        val decisions = listOf(
            Decision(question = "一", benefits = listOf("成长", "成长")),
            Decision(question = "二", benefits = listOf("成长")),
            Decision(question = "三", benefits = listOf("生活")),
        )

        val insights = calculateSelfInsights(decisions, minimumEvidence = 2)

        assertEquals(2, insights.single().evidenceCount)
    }

    @Test
    fun selfInsightsAreNotGeneratedWithInsufficientEvidence() {
        val decisions = listOf(Decision(question = "一", benefits = listOf("成长")))

        assertEquals(emptyList<SelfInsight>(), calculateSelfInsights(decisions))
    }

    @Test
    fun reviewIntervalsDescribeTheDecisionAndPreviousReviewSeparately() {
        assertEquals("距决定 3 天", reviewIntervalLabel(3 * 86_400_000L, 0L, true))
        assertEquals("距上次回看 1 天", reviewIntervalLabel(4 * 86_400_000L, 3 * 86_400_000L, false))
    }
}
