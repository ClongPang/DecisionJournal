package com.example.decisionjournal.data

import android.content.Context
import androidx.core.content.edit
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
import kotlin.coroutines.cancellation.CancellationException
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
        val existingDecisions = dao.observeAll().first()
        if (prefs.getBoolean(PREVIOUS_SEEDED_KEY, false) && existingDecisions.isNotEmpty()) {
            seedBulkWarmupData()
            prefs.edit { putBoolean(SEEDED_KEY, true) }
            return
        }
        if (existingDecisions.isNotEmpty()) {
            prefs.edit { putBoolean(SEEDED_KEY, true) }
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
            selectedIndex = 0,
        )

        // 未来待回看：用于验证未来日期、通知任务和首页最近决定。
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

        // 已到期：用于验证首页待回看卡片和“待回看”状态。
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

        seedBulkWarmupData()

        prefs.edit { putBoolean(SEEDED_KEY, true) }
    }

    /**
     * 额外生成 48 条跨日期的决定，让 Debug 构建可以完整验证分页、筛选、时间线和统计。
     */
    private suspend fun seedBulkWarmupData() {
        val topics = listOf(
            WarmupTopic("是否要开始规律运动？", "最近久坐很多，希望每周安排固定运动时间。", listOf("健康", "时间"), listOf("自律", "时间安排")),
            WarmupTopic("要不要换一部手机？", "旧手机仍能使用，但续航和拍照已经不太够用。", listOf("金钱", "便利"), listOf("开销", "浪费")),
            WarmupTopic("是否接受朋友的合作邀请？", "合作机会不错，但需要重新分配现有项目时间。", listOf("成长", "关系"), listOf("执行成本", "预期落差")),
            WarmupTopic("这次假期要不要独自旅行？", "想拥有完全按自己节奏安排的假期。", listOf("自由", "安全"), listOf("孤单", "行程风险")),
            WarmupTopic("要不要学习一门新语言？", "工作中可能会用到，也对文化本身感兴趣。", listOf("学习", "成长"), listOf("坚持", "时间")),
            WarmupTopic("是否把家里的房间重新布置？", "现在的空间比较拥挤，影响工作和休息。", listOf("生活平衡", "金钱"), listOf("精力", "效果不确定")),
        )
        repeat(48) { index ->
            // A few warmup records sit in the recent days so today/week/month period counts
            // stay visibly distinct (3/5/7 rather than an artificial 3/3/3), then the rest
            // spread back across the year for pagination and archive verification.
            val days = when {
                index < 5 -> index.toLong()
                else -> 5L + ((index - 5) * 9L % 360L)
            }
            val topic = topics[index % topics.size]
            val decision = Decision(
                // The warmup mechanism must never leak implementation labels into the UI.
                // Repeated questions are intentional: they model real recurring decisions
                // across different dates without making a test fixture visible to the user.
                question = topic.question,
                context = topic.context,
                benefits = topic.benefits,
                concerns = if (index % 2 == 0) topic.concerns else listOf(topic.concerns[1], topic.concerns[0]),
                futureNote = if (index % 3 == 0) "希望未来的我能记得，当时已经认真比较过。" else null,
                expectedOutcome = "先用一段时间验证这个选择是否适合现在的生活。",
                confidence = 3 + (index % 3),
                createdAt = daysAgo(days),
                updatedAt = daysAgo((days - 1).coerceAtLeast(0)),
                decisionDate = daysAgo(days),
                reviewDate = when {
                    index % 7 == 0 -> daysAgo(((index % 5) * 6).toLong())
                    index % 5 == 0 -> daysFromNow(7 + (index % 14).toLong())
                    else -> null
                },
            )
            val choices = listOf(
                Choice(decisionId = 0, text = "先小范围尝试", benefits = listOf("风险较低", "能获得反馈"), concerns = listOf("需要投入额外时间")),
                Choice(decisionId = 0, text = "暂时保持现状", benefits = listOf("节省精力", "不改变习惯"), concerns = listOf("可能错过机会")),
            )
            if (index % 6 == 0) {
                val isExpected = (index / 6) % 2 == 0
                seedReviewed(
                    decision = decision.copy(reviewDate = null),
                    choices = choices,
                    reviews = listOf(
                        Review(
                            decisionId = 0,
                            createdAt = daysAgo((days - 10).coerceAtLeast(0)),
                            result = "实际执行后发现，结果基本符合当时的预期，也获得了一些新的反馈。",
                            // “符合预期”的样本满意度自然分布在 3–5，避免出现大量 1 分。
                            satisfaction = if (isExpected) 3 + ((index / 6) % 3) else 4 + ((index / 6) % 2),
                            expectationMatch = if (isExpected) ExpectationMatch.EXPECTED else ExpectationMatch.BETTER,
                            accurateJudgment = "提前考虑关键因素是有帮助的。",
                            unexpectedFinding = "真正影响结果的是持续执行，而不是最初的选择。",
                            nextTimeNote = "下一次会先设定更小、更容易验证的行动。",
                        ),
                    ),
                    selectedIndex = index % 2,
                )
            } else {
                seedActive(decision, choices, selectedIndex = index % 2)
            }
        }
    }

    private suspend fun seedActive(decision: Decision, choices: List<Choice>, selectedIndex: Int? = null) {
        val seededDecision = decision.copy(
            status = DecisionStatus.ACTIVE,
            selectedChoiceId = selectedIndex?.toLong(),
            reviewDateKey = reviewDateKey(decision.reviewDate),
            reminderAt = reviewReminderAt(decision.reviewDate, reviewDateKey = reviewDateKey(decision.reviewDate)),
        )
        val id = dao.save(seededDecision, choices)
        // Demo data must remain usable when notification permission is denied or blocked.
        try {
            reminderScheduler.scheduleOrCancel(id, seededDecision.reviewDate, seededDecision.reminderAt)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Notifications are optional for debug warmup data.
        }
    }

    private suspend fun seedReviewed(
        decision: Decision,
        choices: List<Choice>,
        reviews: List<Review>,
        selectedIndex: Int? = null,
    ) {
        val id = dao.save(
            decision.copy(status = DecisionStatus.ACTIVE, selectedChoiceId = selectedIndex?.toLong()),
            choices,
        )
        reviews.sortedBy { it.createdAt }.forEach { review ->
            dao.saveReview(review.copy(decisionId = id), null, null, null, review.createdAt)
        }
    }

    private fun daysAgo(days: Long): Long = dayOffset(-days)
    private fun daysFromNow(days: Long): Long = dayOffset(days)
    private fun dayOffset(days: Long): Long = LocalDate.now().plusDays(days).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private companion object {
        const val PREFS_NAME = "demo-data"
        const val PREVIOUS_SEEDED_KEY = "seeded-v2"
        const val SEEDED_KEY = "seeded-v3"
    }
}

/** A warmup topic carries its own concern pool so the demo archive does not repeat one worry. */
private data class WarmupTopic(
    val question: String,
    val context: String,
    val benefits: List<String>,
    val concerns: List<String>,
)
