package com.example.decisionjournal.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
}
