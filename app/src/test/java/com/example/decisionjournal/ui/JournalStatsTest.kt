package com.example.decisionjournal.ui

import com.example.decisionjournal.data.model.Decision
import com.example.decisionjournal.data.model.DecisionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class JournalStatsTest {
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
}
