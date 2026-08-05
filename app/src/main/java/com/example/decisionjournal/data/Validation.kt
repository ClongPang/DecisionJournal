package com.example.decisionjournal.data

import com.example.decisionjournal.data.model.DecisionStatus
import java.time.LocalDate
import java.time.ZoneId

object DecisionValidation {
    fun cleanChoices(choices: List<ChoiceInput>): List<ChoiceInput> = choices.map { it.copy(text = it.text.trim(), benefits = it.benefits.map(String::trim).filter(String::isNotEmpty), concerns = it.concerns.map(String::trim).filter(String::isNotEmpty)) }.filter { it.text.isNotEmpty() }

    /** Translates a selection from the submitted list to the list after blank choices are removed. */
    fun normalizedSelectedChoiceIndex(choices: List<ChoiceInput>, selectedChoiceIndex: Int?): Int? =
        selectedChoiceIndex?.let { selected ->
            choices.take(selected + 1).count { it.text.trim().isNotEmpty() } - 1
        }

    fun validate(input: DecisionInput): String? {
        if (input.question.trim().isEmpty()) return "问题不能为空"
        if ((input.futureNote?.trim()?.length ?: 0) > 500) return "写给未来的自己的话不能超过 500 个字符"
        if (input.decisionDate >= tomorrowStart()) return "决定日期不能晚于今天"
        val selected = input.selectedChoiceIndex
        if (selected != null && (selected !in input.choices.indices || input.choices[selected].text.trim().isEmpty())) {
            return "最终选择无效"
        }
        if (cleanChoices(input.choices).isEmpty()) return "至少需要一个候选选项"
        if (input.confidence != null && input.confidence !in 1..5) return "判断信心必须为 1 至 5"
        return null
    }

    /** Existing due dates may be retained while editing; only a changed date must be today/future. */
    fun validateReviewDate(previousReviewDate: Long?, newReviewDate: Long?, todayStart: Long = todayStart()): String? {
        if (newReviewDate != null && newReviewDate < todayStart && newReviewDate != previousReviewDate) {
            return "复盘日期不能早于今天"
        }
        return null
    }

    private fun todayStart(): Long =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun tomorrowStart(): Long =
        LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

object ReviewValidation {
    fun validate(input: ReviewInput, todayStart: Long = todayStart()): String? {
        if (input.result.trim().isEmpty()) return "复盘结果不能为空"
        if (input.satisfaction != null && input.satisfaction !in 1..5) return "满意度必须为 1 至 5"
        if (input.nextReviewDate != null && input.nextReviewDate < todayStart) return "下一次复盘日期不能早于今天"
        return null
    }

    private fun todayStart(): Long =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

object DecisionStatusRules {
    fun afterDecisionSave(
        previous: DecisionStatus?,
        previousReviewDate: Long?,
        newReviewDate: Long?,
        previousReviewDateKey: String? = null,
        newReviewDateKey: String? = null,
    ): DecisionStatus {
        if (previous == null) return DecisionStatus.ACTIVE
        if (previous == DecisionStatus.REVIEWED &&
            if (previousReviewDateKey != null || newReviewDateKey != null) previousReviewDateKey != newReviewDateKey else previousReviewDate != newReviewDate
        ) return DecisionStatus.ACTIVE
        return previous
    }

    fun afterReview(nextReviewDate: Long?): DecisionStatus =
        if (nextReviewDate == null) DecisionStatus.REVIEWED else DecisionStatus.ACTIVE
}
