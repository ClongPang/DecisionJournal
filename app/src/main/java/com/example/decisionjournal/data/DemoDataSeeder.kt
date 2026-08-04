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

        // 已回看：包含两次复盘，用于验证详情页历史、预期对照和时间线。
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
            reviews = listOf(
                Review(
                    decisionId = 0,
                    createdAt = daysAgo(20),
                    result = "接受了工作。前两周压力比预期大，但确实遇到了新的学习机会。",
                    satisfaction = 4,
                    expectationMatch = ExpectationMatch.EXPECTED,
                    accurateJudgment = "判断到成长机会会增加。",
                    unexpectedFinding = "没有预料到适应团队需要这么久。",
                    nextTimeNote = "除了机会，也要提前了解团队的工作节奏。",
                ),
                Review(
                    decisionId = 0,
                    createdAt = daysAgo(3),
                    result = "适应期已经过去，工作内容比原岗位更有挑战，也开始建立新的生活节奏。",
                    satisfaction = 5,
                    expectationMatch = ExpectationMatch.BETTER,
                    accurateJudgment = "成长空间和学习机会判断准确。",
                    unexpectedFinding = "团队支持比预想中更充分。",
                    nextTimeNote = "以后评估机会时，也要主动了解团队协作方式。",
                ),
            ),
        )

        // 未来待复盘：用于验证未来日期、通知任务和首页最近决定。
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

        // 已到期：用于验证首页待复盘卡片和“待复盘”状态。
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

        // 没有复盘日期：用于验证未设置日期状态和后续主动复盘入口。
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
            reviews = listOf(
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
            ),
        )

        // 活跃但不设置日期：用于验证无需提醒也可以长期保存的记录。
        seedActive(
            Decision(
                question = "要不要搬到离公司更近的地方？",
                context = "通勤时间很长，但搬家会增加租金和整理成本。",
                benefits = listOf("生活平衡", "时间"),
                concerns = listOf("金钱", "稳定"),
                expectedOutcome = "如果搬家，预计每天可以多出一个小时休息和运动。",
                confidence = 3,
                futureNote = "不要只比较房租，也要比较每天被通勤占用的时间。",
            ),
            listOf(
                Choice(decisionId = 0, text = "搬到公司附近", benefits = listOf("减少通勤", "有更多休息时间"), concerns = listOf("租金增加")),
                Choice(decisionId = 0, text = "继续住在现在的地方", benefits = listOf("保持熟悉的生活环境"), concerns = listOf("长期通勤消耗精力")),
            ),
            selectedIndex = null,
        )

        // 今天到期且尚未选最终方案：用于验证不强制最终选择也能保存和复盘。
        seedActive(
            Decision(
                question = "要不要报名那门周末课程？",
                context = "课程内容很感兴趣，但会占用连续八个周末。",
                benefits = listOf("学习", "成长"),
                concerns = listOf("时间", "生活平衡"),
                expectedOutcome = "希望建立一个稳定的学习节奏。",
                confidence = 2,
                reviewDate = daysAgo(0),
            ),
            listOf(
                Choice(decisionId = 0, text = "报名课程", benefits = listOf("系统学习"), concerns = listOf("周末时间减少")),
                Choice(decisionId = 0, text = "先自学两周", benefits = listOf("成本更低"), concerns = listOf("容易缺少持续性")),
            ),
            selectedIndex = null,
        )

        prefs.edit().putBoolean(SEEDED_KEY, true).apply()
    }

    private suspend fun seedActive(decision: Decision, choices: List<Choice>, selectedIndex: Int? = null) {
        val id = dao.save(decision.copy(status = DecisionStatus.ACTIVE, selectedChoiceId = selectedIndex?.toLong()), choices)
        reminderScheduler.scheduleOrCancel(id, decision.reviewDate)
    }

    private suspend fun seedReviewed(decision: Decision, choices: List<Choice>, reviews: List<Review>) {
        val id = dao.save(decision.copy(status = DecisionStatus.ACTIVE), choices)
        reviews.sortedBy { it.createdAt }.forEach { review ->
            dao.saveReview(review.copy(decisionId = id), null, review.createdAt)
        }
    }

    private fun daysAgo(days: Long): Long = dayOffset(-days)
    private fun daysFromNow(days: Long): Long = dayOffset(days)
    private fun dayOffset(days: Long): Long = LocalDate.now().plusDays(days).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private companion object {
        const val PREFS_NAME = "demo-data"
        const val SEEDED_KEY = "seeded-v2"
    }
}
