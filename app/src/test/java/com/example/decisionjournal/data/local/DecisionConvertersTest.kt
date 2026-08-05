package com.example.decisionjournal.data.local

import com.example.decisionjournal.data.model.DecisionStatus
import com.example.decisionjournal.data.model.ExpectationMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DecisionConvertersTest {
    private val converters = DecisionConverters()

    @Test
    fun stringListsRoundTripTextContainingLegacySeparatorAndComma() {
        val values = listOf("普通文本", "逗号,和分隔符\u001f都保留", "")

        assertEquals(values, converters.toStringList(converters.fromStringList(values)))
    }

    @Test
    fun legacyStringListsRemainReadable() {
        assertEquals(listOf("接受", "拒绝"), converters.toStringList("接受\u001f拒绝"))
    }

    @Test
    fun unknownEnumValuesUseSafeFallbacks() {
        assertEquals(DecisionStatus.ACTIVE, converters.toStatus("REMOVED"))
        assertNull(converters.toExpectationMatch("REMOVED"))
    }

    @Test
    fun legacyValueThatMerelyStartsWithThePrefixIsNotDropped() {
        assertEquals(listOf("v2:接受"), converters.toStringList("v2:接受"))
        assertEquals(listOf("v2:接受", "拒绝"), converters.toStringList("v2:接受拒绝"))
    }

    @Test
    fun emptySerializedListStaysEmpty() {
        assertEquals(emptyList<String>(), converters.toStringList("v2:"))
    }
}
