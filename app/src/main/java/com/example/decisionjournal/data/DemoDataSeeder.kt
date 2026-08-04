package com.example.decisionjournal.data

import android.content.Context
import com.example.decisionjournal.data.local.DecisionDao
import com.example.decisionjournal.data.model.Choice
import com.example.decisionjournal.data.model.Decision
import com.example.decisionjournal.data.model.DecisionStatus
import com.example.decisionjournal.data.model.ExpectationMatch
import com.example.decisionjournal.data.model.Review
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 仅用于 debuggable 构建的体验预热数据。通过本地标记保证幂等，不进入正式用户数据。
 */
@Singleton
class DemoDataSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DecisionDao,
    private val reminderScheduler: ReviewReminderScheduler,
) {
    suspend fun seedIfNeeded() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(SEEDED_KEY, false)) return
        if (dao.observeAll().first().isNotEmpty()) {
            prefs.edit().putBoolean(SEEDED_KEY, true).apply()
            return
        }

        seedReviewed(
            decision = Decision(
                question = "要不要接受那份工作？",
                context = "薪资更高，但需要搬家，也意味着进入新的团队。",
                benefits = listOf("成长", "学习机会"),
                concerns = listOf("稳定", "生活平衡"),
                expectedOutcome = "希望获得更大的成长空间，同时保持可接受的生活节奏。",
                confidence = 3,
                futureNote = "无论结果如何，希望我记得当时是在认真为自己选择。",
                createdAt = daysAgo(30),
                updatedAt = daysAgo(12),
                status = DecisionStatus.ACTIVE,
            ),
            choices = listOf(
                Choice(decisionId = 0, text = "接受", benefits = listOf("进入更大的平台"), concerns = listOf("通勤时间变长")),
                Choice(decisionId = 0, text = "拒绝", benefits = listOf("生活保持稳定"), concerns = listOf("可能错过成长机会")),
            ),
            review = Review(
                decisionId = 0,
                createdAt = daysAgo(3),
                result = "接受了工作。前两周压力比预期大，但确实遇到了新的学习机会。",
                satisfaction = 4,
                expectationMatch = ExpectationMatch.EXPECTED,
                accurateJudgment = "判断到成长机会会增加。",
                unexpectedFinding = "没有预料到适应团队需要这么久。",
                nextTimeNote = "除了机会，也要提前了解团队的工作节奏。",
            ),
        )

        seedActive(
            Decision(
                question = "要不要开始一个长期副业？",
                context = "有一个想做很久的产品想法，每周可以投入几个小时。",
                benefits = listOf("成长", "自由"),
                concerns = listOf("压力", "生活平衡"),
                expectedOutcome = "先用三个月验证想法，不急着把它变成收入来源。",
                confidence = 4,
                createdAt = daysAgo(18),
                updatedAt = daysAgo(5),
                reviewDate = daysFromNow(14),
            ),
            listOf(
                Choice(decisionId = 0, text = "开始小规模验证", benefits = listOf("获得真实反馈"), concerns = listOf("挤占休息时间")),
                Choice(decisionId = 0, text = "暂时不开始", benefits = listOf("保持精力"), concerns = listOf("继续停留在想法阶段")),
            ),
            selectedIndex = 0,
        )

        seedActive(
            Decision(
                question = "这次旅行要不要选择慢一点的路线？",
                context = "时间更充裕，但需要放弃几个热门景点。",
                benefits = listOf("生活平衡", "自由"),
                concerns = listOf("错过机会"),
                expectedOutcome = "希望少一点赶路，多留一些真正放松的时间。",
                confidence = 5,
                createdAt = daysAgo(6),
                updatedAt = daysAgo(2),
                reviewDate = daysAgo(1),
            ),
            listOf(
                Choice(decisionId = 0, text = "慢一点旅行", benefits = listOf("更从容"), concerns = listOf("少看几个地方")),
                Choice(decisionId = 0, text = "尽量多去几个地方", benefits = listOf("体验更多"), concerns = listOf("行程可能很赶")),
            ),
            selectedIndex = 0,
        )

        seedReviewed(
            Decision(
                question = "要不要买一台新的相机？",
                context = "旧相机还能使用，但新设备更轻便。",
                benefits = listOf("自由", "创造"),
                concerns = listOf("金钱", "不确定"),
                expectedOutcome = "买了以后会更愿意带出门记录生活。",
                confidence = 2,
                createdAt = daysAgo(60),
                updatedAt = daysAgo(20),
            ),
            listOf(
                Choice(decisionId = 0, text = "购买", benefits = listOf("更轻便"), concerns = listOf("价格较高")),
                Choice(decisionId = 0, text = "继续使用旧相机", benefits = listOf("不增加支出"), concerns = listOf("可能继续闲置")),
            ),
            Review(
                decisionId = 0,
                createdAt = daysAgo(20),
                result = "买了之后前几周很常用，后来发现真正限制自己的不是设备，而是没有安排时间。",
                satisfaction = 3,
                expectationMatch = ExpectationMatch.WORSE,
                accurateJudgment = "新设备确实更方便携带。",
                unexpectedFinding = "设备并没有自动带来更多创作。",
                nextTimeNote = "先确认自己是否愿意安排时间，再考虑购买工具。",
            ),
        )

        prefs.edit().putBoolean(SEEDED_KEY, true).apply()
    }

    private suspend fun seedActive(decision: Decision, choices: List<Choice>, selectedIndex: Int? = null) {
        val id = dao.save(decision.copy(status = DecisionStatus.ACTIVE, selectedChoiceId = selectedIndex?.toLong()), choices)
        reminderScheduler.scheduleOrCancel(id, decision.reviewDate)
    }

    private suspend fun seedReviewed(decision: Decision, choices: List<Choice>, review: Review) {
        val id = dao.save(decision.copy(status = DecisionStatus.ACTIVE), choices)
        dao.saveReview(review.copy(decisionId = id), null, review.createdAt)
    }

    private fun daysAgo(days: Long): Long = dayOffset(-days)
    private fun daysFromNow(days: Long): Long = dayOffset(days)
    private fun dayOffset(days: Long): Long = LocalDate.now().plusDays(days).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private companion object {
        const val PREFS_NAME = "demo-data"
        const val SEEDED_KEY = "seeded-v1"
    }
}
