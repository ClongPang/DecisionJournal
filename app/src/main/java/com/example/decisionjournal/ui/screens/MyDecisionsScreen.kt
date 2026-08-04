package com.example.decisionjournal.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.decisionjournal.data.model.Decision
import com.example.decisionjournal.ui.DecisionsViewModel
import com.example.decisionjournal.ui.CustomDateRange
import com.example.decisionjournal.ui.DecisionFilter
import com.example.decisionjournal.ui.DecisionPeriod
import com.example.decisionjournal.ui.INITIAL_DECISION_PAGE_SIZE
import com.example.decisionjournal.ui.PeriodCounts
import com.example.decisionjournal.ui.calculateSelfInsights
import com.example.decisionjournal.ui.nextDecisionPageSize
import com.example.decisionjournal.ui.components.EmptyJournalState
import com.example.decisionjournal.ui.components.JournalTopBar
import com.example.decisionjournal.ui.components.SectionHeader
import com.example.decisionjournal.ui.components.SoftSurfaceCard
import com.example.decisionjournal.ui.components.StatusPill
import com.example.decisionjournal.ui.theme.JournalDimens
import com.example.decisionjournal.ui.theme.MistBlue
import com.example.decisionjournal.ui.theme.MistGreen
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timelineDate = DateTimeFormatter.ofPattern("M月d日")

@Composable
fun MyDecisionsScreen(
    onOpen: (Long) -> Unit,
    onCreate: () -> Unit,
    showStats: Boolean,
    vm: DecisionsViewModel = hiltViewModel(),
) {
    val decisions by vm.decisions.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val periodCounts by vm.periodCounts.collectAsStateWithLifecycle()
    val selectedFilter by vm.selectedFilter.collectAsStateWithLifecycle()
    val filteredDecisions by vm.filteredDecisions.collectAsStateWithLifecycle()
    val now = remember { System.currentTimeMillis() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scrollState = rememberScrollState()
    var customError by remember { mutableStateOf<String?>(null) }
    var visibleDecisionCount by remember { mutableStateOf(INITIAL_DECISION_PAGE_SIZE) }

    fun chooseCustomRange() {
        customError = null
        val today = LocalDate.now()
        val initialStart = today.minusDays(6)
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val start = LocalDate.of(year, month + 1, day)
                DatePickerDialog(
                    context,
                    { _, endYear, endMonth, endDay ->
                        val end = LocalDate.of(endYear, endMonth + 1, endDay)
                        if (end.isBefore(start)) {
                            customError = "结束日期不能早于开始日期"
                        } else {
                            vm.selectFilter(DecisionFilter.Custom(CustomDateRange(start, end)))
                        }
                    },
                    today.year,
                    today.monthValue - 1,
                    today.dayOfMonth,
                ).apply {
                    datePicker.minDate = start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    datePicker.maxDate = System.currentTimeMillis()
                }.show()
            },
            initialStart.year,
            initialStart.monthValue - 1,
            initialStart.dayOfMonth,
        ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
    }

    val timelineDecisions = if (showStats) decisions else filteredDecisions
    LaunchedEffect(showStats, selectedFilter, timelineDecisions.size) {
        visibleDecisionCount = INITIAL_DECISION_PAGE_SIZE
    }
    LaunchedEffect(scrollState, timelineDecisions.size, visibleDecisionCount) {
        snapshotFlow { scrollState.value to scrollState.maxValue }.collect { (offset, maxOffset) ->
            val nearBottom = maxOffset == 0 || offset >= (maxOffset - 96).coerceAtLeast(0)
            if (nearBottom && visibleDecisionCount < timelineDecisions.size) {
                visibleDecisionCount = nextDecisionPageSize(visibleDecisionCount, timelineDecisions.size)
            }
        }
    }

    Column(
        Modifier.padding(horizontal = JournalDimens.pageHorizontal, vertical = JournalDimens.pageVertical)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(JournalDimens.sectionSpacing),
    ) {
        JournalTopBar(
            title = if (showStats) "认识自己" else "全部决定",
            subtitle = if (showStats) "看见自己是如何做决定的" else null,
        )

        if (!showStats) {
            PeriodOverview(
                counts = periodCounts,
                selectedFilter = selectedFilter,
                customError = customError,
                onSelect = { vm.selectFilter(DecisionFilter.Preset(it)) },
                onCustomRange = ::chooseCustomRange,
                onClear = vm::clearFilter,
            )
        }

        if (showStats) {
            Overview(stats.completedCount, stats.mostCaredAbout ?: "还在认识自己", stats.dueCount)
            Text(
                "记录是给未来的线索，不是给现在的评分。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            calculateSelfInsights(decisions).forEach { insight ->
                SoftSurfaceCard(modifier = Modifier.fillMaxWidth(), containerColor = MistBlue, hero = true) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(insight.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(insight.description, style = MaterialTheme.typography.titleMedium)
                        Text("来自 ${insight.evidenceCount} 条记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        val timelineTitle = if (showStats) "决策时间线" else when (val filter = selectedFilter) {
            DecisionFilter.All -> "全部记录"
            is DecisionFilter.Preset -> "${filter.period.label()}的决定"
            is DecisionFilter.Custom -> "指定范围内的决定"
        }
        SectionHeader(timelineTitle)
        if (timelineDecisions.isEmpty()) {
            EmptyJournalState(
                if (showStats || selectedFilter == DecisionFilter.All) "还没有记录，先写下一个决定吧。" else "这个时间范围还没有决定。",
                if (showStats || selectedFilter == DecisionFilter.All) "记录一个决定" else "清除筛选",
                if (showStats || selectedFilter == DecisionFilter.All) onCreate else vm::clearFilter,
            )
        } else {
            val displayedDecisions = timelineDecisions.take(visibleDecisionCount)
            TimelineContainer(displayedDecisions, now, onOpen)
            if (displayedDecisions.size < timelineDecisions.size) {
                Text(
                    "继续滑动加载更多 · 还剩 ${timelineDecisions.size - displayedDecisions.size} 条",
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PeriodOverview(
    counts: PeriodCounts,
    selectedFilter: DecisionFilter,
    customError: String?,
    onSelect: (DecisionPeriod) -> Unit,
    onCustomRange: () -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("按时间回看", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PeriodCard("今日", counts.today, selectedFilter == DecisionFilter.Preset(DecisionPeriod.TODAY), onClick = { onSelect(DecisionPeriod.TODAY) }, modifier = Modifier.weight(1f))
            PeriodCard("本周", counts.week, selectedFilter == DecisionFilter.Preset(DecisionPeriod.WEEK), onClick = { onSelect(DecisionPeriod.WEEK) }, modifier = Modifier.weight(1f))
            PeriodCard("本月", counts.month, selectedFilter == DecisionFilter.Preset(DecisionPeriod.MONTH), onClick = { onSelect(DecisionPeriod.MONTH) }, modifier = Modifier.weight(1f))
            PeriodCard("今年", counts.year, selectedFilter == DecisionFilter.Preset(DecisionPeriod.YEAR), onClick = { onSelect(DecisionPeriod.YEAR) }, modifier = Modifier.weight(1f))
        }
        val custom = (selectedFilter as? DecisionFilter.Custom)?.range
        SoftSurfaceCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = if (custom != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            onClick = onCustomRange,
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("指定范围", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (custom == null) "选择开始和结束日期" else "${custom.start.format(timelineDate)} — ${custom.endInclusive.format(timelineDate)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                TextButton(onClick = if (custom == null) onCustomRange else onClear) { Text(if (custom == null) "选择" else "清除") }
            }
        }
        customError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun PeriodCard(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    SoftSurfaceCard(
        modifier = modifier.height(78.dp),
        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        onClick = onClick,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text("$count", style = MaterialTheme.typography.headlineSmall, maxLines = 1)
        }
    }
}

private fun DecisionPeriod.label(): String = when (this) {
    DecisionPeriod.TODAY -> "今日"
    DecisionPeriod.WEEK -> "本周"
    DecisionPeriod.MONTH -> "本月"
    DecisionPeriod.YEAR -> "今年"
}

@Composable
private fun Overview(completed: Int, caredAbout: String, due: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SoftSurfaceCard(modifier = Modifier.fillMaxWidth(), containerColor = MistBlue, hero = true) {
            Row(
                Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("已完成决策", style = MaterialTheme.typography.labelMedium)
                    Text("$completed", style = MaterialTheme.typography.displaySmall)
                    Text("次回看，慢慢认识自己的选择", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                Icon(Icons.Rounded.CalendarToday, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 4.dp))
            }
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallInsightCard("最常在意", caredAbout, Icons.Rounded.FavoriteBorder, Modifier.weight(1f), MaterialTheme.colorScheme.surface)
            SmallInsightCard("待复盘", "$due 个", Icons.Rounded.Schedule, Modifier.weight(1f), MistGreen)
        }
    }
}

@Composable
private fun SmallInsightCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, color: Color) {
    SoftSurfaceCard(modifier = modifier.fillMaxHeight(), containerColor = color) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(18.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TimelineContainer(decisions: List<Decision>, now: Long, onOpen: (Long) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        decisions.forEachIndexed { index, decision ->
            TimelineItem(
                decision = decision,
                now = now,
                isFirst = index == 0,
                isLast = index == decisions.lastIndex,
                onOpen = onOpen,
            )
        }
    }
}

@Composable
private fun TimelineItem(
    decision: Decision,
    now: Long,
    isFirst: Boolean,
    isLast: Boolean,
    onOpen: (Long) -> Unit,
) {
    val due = decision.reviewDate != null && decision.reviewDate <= now && decision.status.name != "REVIEWED"
    val reviewed = decision.status.name == "REVIEWED"
    val statusText = when {
        due -> "待复盘"
        reviewed -> "已回看"
        decision.reviewDate != null -> "等待回看"
        else -> "尚未设置日期"
    }
    val nodeColor = if (due) MaterialTheme.colorScheme.primary else if (reviewed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline

    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.Top) {
        TimelineRail(nodeColor, isFirst, isLast, Modifier.fillMaxHeight())
        SoftSurfaceCard(
            modifier = Modifier.weight(1f),
            containerColor = if (due) MistBlue.copy(alpha = 0.42f) else MaterialTheme.colorScheme.surface,
            onClick = { onOpen(decision.id) },
        ) {
            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        Instant.ofEpochMilli(decision.decisionDate).atZone(ZoneId.systemDefault()).toLocalDate().format(timelineDate),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.height(14.dp))
                }
                Text(decision.question, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                StatusPill(statusText, if (due) MistBlue else if (reviewed) MistGreen else MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }
}

@Composable
private fun TimelineRail(color: Color, isFirst: Boolean, isLast: Boolean, modifier: Modifier) {
    val lineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    Canvas(modifier.padding(end = 10.dp).width(22.dp)) {
        val center = Offset(size.width / 2f, 12.dp.toPx())
        if (!isFirst) drawLine(lineColor, Offset(center.x, 0f), Offset(center.x, center.y), strokeWidth = 1.dp.toPx())
        if (!isLast) drawLine(lineColor, Offset(center.x, center.y), Offset(center.x, size.height), strokeWidth = 1.dp.toPx())
        drawCircle(color, radius = 5.dp.toPx(), center = center)
        drawCircle(Color.Transparent, radius = 8.dp.toPx(), center = center, style = Stroke(width = 1.dp.toPx()))
    }
}
