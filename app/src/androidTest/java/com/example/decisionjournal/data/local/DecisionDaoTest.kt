package com.example.decisionjournal.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.decisionjournal.data.model.Choice
import com.example.decisionjournal.data.model.Decision
import com.example.decisionjournal.data.model.DecisionStatus
import com.example.decisionjournal.data.model.Review
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DecisionDaoTest {
    private lateinit var database: DecisionDatabase
    private lateinit var dao: DecisionDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, DecisionDatabase::class.java).allowMainThreadQueries().build()
        dao = database.decisionDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun saveRewritesChoicesAndMapsSelectedChoice() = runBlocking {
        val id = dao.save(Decision(question = "问题", selectedChoiceId = 1L), listOf(Choice(0, 0, "第一"), Choice(0, 0, "第二")))
        val saved = dao.getById(id)
        val choices = dao.observeChoices(id).first()
        assertEquals(2, choices.size)
        assertEquals("第二", choices[saved!!.selectedChoiceId?.let { selectedId -> choices.indexOfFirst { it.id == selectedId } } ?: -1].text)
    }

    @Test
    fun deleteCascadeRemovesDecisionChoicesAndReviews() = runBlocking {
        val id = dao.save(Decision(question = "问题"), listOf(Choice(0, 0, "方案")))
        dao.insertReview(Review(decisionId = id, result = "结果"))
        dao.deleteCascade(id)
        assertNull(dao.getById(id))
        assertEquals(emptyList<Choice>(), dao.observeChoices(id).first())
        assertEquals(emptyList<Review>(), dao.observeReviews(id).first())
    }

    @Test
    fun reviewsAreOrderedNewestFirst() = runBlocking {
        val id = dao.save(Decision(question = "问题"), listOf(Choice(0, 0, "方案")))
        dao.insertReview(Review(decisionId = id, createdAt = 100L, result = "旧"))
        dao.insertReview(Review(decisionId = id, createdAt = 200L, result = "新"))
        assertEquals(listOf("新", "旧"), dao.observeReviews(id).first().map { it.result })
    }

    @Test
    fun decisionsAreOrderedByCreatedAtNotUpdatedAt() = runBlocking {
        dao.save(Decision(question = "旧但最近编辑", createdAt = 100L, updatedAt = 900L, decisionDate = 100L), emptyList())
        dao.save(Decision(question = "新决定", createdAt = 100L, updatedAt = 200L, decisionDate = 200L), emptyList())

        assertEquals(listOf("新决定", "旧但最近编辑"), dao.observeAll().first().map { it.question })
    }

    @Test
    fun reviewWithNextDateKeepsDecisionActiveAndReschedulesDate() = runBlocking {
        val id = dao.save(Decision(question = "问题"), listOf(Choice(0, 0, "方案")))
        dao.saveReview(Review(decisionId = id, result = "第一次结果"), 2_000L, 1_000L)

        val saved = dao.getById(id)
        assertEquals(2_000L, saved?.reviewDate)
        assertEquals(DecisionStatus.ACTIVE, saved?.status)
        assertEquals(1, dao.observeReviews(id).first().size)
    }

    @Test
    fun reviewWithoutNextDateEndsReminderButKeepsHistory() = runBlocking {
        val id = dao.save(Decision(question = "问题"), listOf(Choice(0, 0, "方案")))
        dao.saveReview(Review(decisionId = id, result = "最终结果"), null, 1_000L)

        val saved = dao.getById(id)
        assertEquals(null, saved?.reviewDate)
        assertEquals(DecisionStatus.REVIEWED, saved?.status)
        assertEquals(listOf("最终结果"), dao.observeReviews(id).first().map { it.result })
    }

    @Test
    fun updatingMissingDecisionDoesNotCreateOrphanChoices() = runBlocking {
        try {
            dao.save(Decision(id = 999L, question = "不存在"), listOf(Choice(0, 0, "孤儿")))
            fail("缺失的决定 ID 应拒绝更新")
        } catch (_: IllegalStateException) {
            assertEquals(emptyList<Choice>(), dao.observeChoices(999L).first())
        }
    }

    @Test
    fun reviewForMissingDecisionDoesNotInsertReview() = runBlocking {
        try {
            dao.saveReview(Review(decisionId = 999L, result = "孤儿复盘"), null, 1_000L)
            fail("缺失的决定 ID 应拒绝复盘")
        } catch (_: IllegalStateException) {
            assertEquals(emptyList<Review>(), dao.observeReviews(999L).first())
        }
    }

    @Test
    fun childTablesHaveDecisionIndexes() = runBlocking {
        val choicesIndex = database.openHelper.readableDatabase.query("PRAGMA index_list(choices)").use { cursor ->
            generateSequence { if (cursor.moveToNext()) cursor.getString(cursor.getColumnIndexOrThrow("name")) else null }.toList()
        }
        val reviewsIndex = database.openHelper.readableDatabase.query("PRAGMA index_list(reviews)").use { cursor ->
            generateSequence { if (cursor.moveToNext()) cursor.getString(cursor.getColumnIndexOrThrow("name")) else null }.toList()
        }
        assertTrue(choicesIndex.contains("index_choices_decisionId"))
        assertTrue(reviewsIndex.contains("index_reviews_decisionId"))
    }
}
