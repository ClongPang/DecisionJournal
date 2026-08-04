package com.example.decisionjournal.ui

import com.example.decisionjournal.data.model.Decision

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
