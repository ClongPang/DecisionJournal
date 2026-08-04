package com.example.decisionjournal.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.example.decisionjournal.data.model.DecisionStatus
import java.time.LocalDate
import java.time.ZoneId

class ValidationTest {
    @Test
    fun blankQuestionIsRejected() {
        val input = DecisionInput(question = "  ", context = null, reviewDate = null, selectedChoiceIndex = null, choices = listOf(ChoiceInput("接受")))
        assertEquals("问题不能为空", DecisionValidation.validate(input))
    }

    @Test
    fun emptyChoicesAreRejectedAfterTrimming() {
        val input = DecisionInput(question = "要不要换工作", context = null, reviewDate = null, selectedChoiceIndex = null, choices = listOf(ChoiceInput(" "), ChoiceInput("")))
        assertEquals("至少需要一个候选选项", DecisionValidation.validate(input))
    }

    @Test
    fun selectedChoiceMustBeInCleanedChoices() {
        val input = DecisionInput(question = "要不要换工作", context = null, reviewDate = null, selectedChoiceIndex = 2, choices = listOf(ChoiceInput("接受"), ChoiceInput("拒绝")))
        assertEquals("最终选择无效", DecisionValidation.validate(input))
    }

    @Test
    fun selectingABlankChoiceIsRejectedInsteadOfSelectingANeighborAfterCleaning() {
        val input = DecisionInput(
            question = "要不要换工作",
            context = null,
            reviewDate = null,
            selectedChoiceIndex = 1,
            choices = listOf(ChoiceInput("接受"), ChoiceInput("  "), ChoiceInput("拒绝")),
        )

        assertEquals("最终选择无效", DecisionValidation.validate(input))
    }

    @Test
    fun selectionIsRemappedWhenBlankChoicesPrecedeTheSelectedChoice() {
        val choices = listOf(ChoiceInput("  "), ChoiceInput("接受"), ChoiceInput("拒绝"))

        assertEquals(0, DecisionValidation.normalizedSelectedChoiceIndex(choices, 1))
    }

    @Test
    fun validDecisionIsAcceptedAndChoicesAreNormalized() {
        val input = DecisionInput(question = " 要不要换工作 ", context = "背景", reviewDate = null, selectedChoiceIndex = 0, choices = listOf(ChoiceInput(" 接受 "), ChoiceInput("拒绝")))
        assertNull(DecisionValidation.validate(input))
        assertEquals(listOf("接受", "拒绝"), DecisionValidation.cleanChoices(input.choices).map { it.text })
    }

    @Test
    fun satisfactionMustBeBetweenOneAndFive() {
        val invalid = ReviewInput(decisionId = 1L, result = "结果", satisfaction = 6)
        assertEquals("满意度必须为 1 至 5", ReviewValidation.validate(invalid))
        assertNull(ReviewValidation.validate(invalid.copy(satisfaction = 1)))
        assertNull(ReviewValidation.validate(invalid.copy(satisfaction = null)))
    }

    @Test
    fun confidenceMustBeBetweenOneAndFive() {
        val input = DecisionInput(
            question = "要不要换工作",
            context = null,
            reviewDate = null,
            selectedChoiceIndex = null,
            choices = listOf(ChoiceInput("接受")),
            confidence = 6,
        )
        assertEquals("判断信心必须为 1 至 5", DecisionValidation.validate(input))
        assertNull(DecisionValidation.validate(input.copy(confidence = 1)))
        assertNull(DecisionValidation.validate(input.copy(confidence = null)))
    }

    @Test
    fun futureNoteCannotExceedFiveHundredCharacters() {
        val input = DecisionInput(
            question = "要不要换工作",
            context = null,
            reviewDate = null,
            selectedChoiceIndex = null,
            choices = listOf(ChoiceInput("接受")),
            futureNote = "a".repeat(501),
        )
        assertEquals("写给未来的自己的话不能超过 500 个字符", DecisionValidation.validate(input))
        assertNull(DecisionValidation.validate(input.copy(futureNote = "a".repeat(500))))
    }

    @Test
    fun editingReviewedDecisionKeepsReviewedStatusWhenDateIsUnchanged() {
        assertEquals(
            DecisionStatus.REVIEWED,
            DecisionStatusRules.afterDecisionSave(DecisionStatus.REVIEWED, 100L, 100L),
        )
    }

    @Test
    fun changingReviewDateReactivatesReviewedDecision() {
        assertEquals(
            DecisionStatus.ACTIVE,
            DecisionStatusRules.afterDecisionSave(DecisionStatus.REVIEWED, 100L, 200L),
        )
    }

    @Test
    fun reviewWithoutNextDateIsCompleted() {
        assertEquals(DecisionStatus.REVIEWED, DecisionStatusRules.afterReview(null))
        assertEquals(DecisionStatus.ACTIVE, DecisionStatusRules.afterReview(200L))
    }

    @Test
    fun changedReviewDateCannotBeBeforeTodayButExistingDueDateCanBeRetained() {
        val today = 1_000L
        assertEquals("复盘日期不能早于今天", DecisionValidation.validateReviewDate(null, 999L, today))
        assertNull(DecisionValidation.validateReviewDate(999L, 999L, today))
        assertNull(DecisionValidation.validateReviewDate(999L, 1_000L, today))
    }

    @Test
    fun nextReviewDateCannotBeBeforeToday() {
        val input = ReviewInput(decisionId = 1L, result = "结果", satisfaction = null, nextReviewDate = 999L)
        assertEquals("下一次复盘日期不能早于今天", ReviewValidation.validate(input, todayStart = 1_000L))
        assertNull(ReviewValidation.validate(input.copy(nextReviewDate = 1_000L), todayStart = 1_000L))
    }

    @Test
    fun decisionDateCannotBeAfterToday() {
        val tomorrow = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val input = DecisionInput(
            question = "要不要换工作",
            context = null,
            reviewDate = null,
            selectedChoiceIndex = null,
            choices = listOf(ChoiceInput("接受")),
            decisionDate = tomorrow,
        )

        assertEquals("决定日期不能晚于今天", DecisionValidation.validate(input))
    }
}
