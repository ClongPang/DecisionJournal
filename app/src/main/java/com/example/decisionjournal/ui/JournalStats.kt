package com.example.decisionjournal.ui

import com.example.decisionjournal.data.model.Decision

data class DecisionStats(
    val completedCount: Int,
    val dueCount: Int,
    val mostCaredAbout: String?,
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
