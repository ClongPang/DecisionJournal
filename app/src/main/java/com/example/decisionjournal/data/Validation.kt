package com.example.decisionjournal.data

import com.example.decisionjournal.data.model.DecisionStatus

object DecisionValidation {
    fun cleanChoices(choices: List<ChoiceInput>): List<ChoiceInput> = choices.map { it.copy(text = it.text.trim(), benefits = it.benefits.map(String::trim).filter(String::isNotEmpty), concerns = it.concerns.map(String::trim).filter(String::isNotEmpty)) }.filter { it.text.isNotEmpty() }

    fun validate(input: DecisionInput): String? {
        if (input.question.trim().isEmpty()) return "问题不能为空"
        val choices = cleanChoices(input.choices)
        if (choices.isEmpty()) return "至少需要一个候选选项"
        if (input.selectedChoiceIndex != null && input.selectedChoiceIndex !in choices.indices) return "最终选择无效"
        if (input.confidence != null && input.confidence !in 1..5) return "判断信心必须为 1 至 5"
        return null
    }
}

object ReviewValidation {
    fun validate(input: ReviewInput): String? {
        if (input.result.trim().isEmpty()) return "复盘结果不能为空"
        if (input.satisfaction != null && input.satisfaction !in 1..5) return "满意度必须为 1 至 5"
        return null
    }
}

object DecisionStatusRules {
    fun afterDecisionSave(previous: DecisionStatus?, previousReviewDate: Long?, newReviewDate: Long?): DecisionStatus {
        if (previous == null) return DecisionStatus.ACTIVE
        if (previous == DecisionStatus.REVIEWED && previousReviewDate != newReviewDate) return DecisionStatus.ACTIVE
        return previous
    }

    fun afterReview(nextReviewDate: Long?): DecisionStatus =
        if (nextReviewDate == null) DecisionStatus.REVIEWED else DecisionStatus.ACTIVE
}
