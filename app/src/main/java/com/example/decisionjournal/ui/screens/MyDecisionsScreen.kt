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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.decisionjournal.data.model.DecisionStatus
import com.example.decisionjournal.ui.DecisionsViewModel
import com.example.decisionjournal.ui.DecisionListState
import com.example.decisionjournal.ui.CustomDateRange
import com.example.decisionjournal.ui.DecisionFilter
import com.example.decisionjournal.ui.DecisionPeriod
import com.example.decisionjournal.ui.DecisionStatusCounts
import com.example.decisionjournal.ui.INITIAL_DECISION_PAGE_SIZE
import com.example.decisionjournal.ui.PeriodCounts
import com.example.decisionjournal.ui.calculateSelfInsights
import com.example.decisionjournal.ui.nextDecisionPageSize
import com.example.decisionjournal.ui.searchDecisions
import com.example.decisionjournal.ui.components.EmptyJournalState
import com.example.decisionjournal.ui.components.ArchiveKicker
import com.example.decisionjournal.ui.components.JournalTopBar
import com.example.decisionjournal.ui.components.JournalTextField
import com.example.decisionjournal.ui.components.NarrativeCard
import com.example.decisionjournal.ui.components.SectionHeader
import com.example.decisionjournal.ui.components.SoftSurfaceCard
import com.example.decisionjournal.ui.components.StatusPill
import com.example.decisionjournal.ui.theme.JournalDimens
import com.example.decisionjournal.ui.theme.MistBlue
import com.example.decisionjournal.ui.theme.MistGreen
import com.example.decisionjournal.ui.theme.MistSand
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timelineDate = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)

@Composable
fun MyDecisionsScreen(
    onOpen: (Long) -> Unit,
    onCreate: () -> Unit,
    showStats: Boolean,
    initialDueFilter: Boolean = false,
    initialSearchQuery: String = "",
    onExploreKeyword: ((String) -> Unit)? = null,
    vm: DecisionsViewModel = hiltViewModel(),
) {
    val decisions by vm.decisions.collectAsStateWithLifecycle()
    val searchFields by vm.searchFields.collectAsStateWithLifecycle()
    val decisionListState by vm.listState.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val periodCounts by vm.periodCounts.collectAsStateWithLifecycle()
    val statusCounts by vm.statusCounts.collectAsStateWithLifecycle()
    val selectedFilter by vm.selectedFilter.collectAsStateWithLifecycle()
    val filteredDecisions by vm.filteredDecisions.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var customError by remember { mutableStateOf<String?>(null) }
    var searchQuery by rememberSaveable(showStats) { mutableStateOf("") }

    LaunchedEffect(initialDueFilter) {
        if (!showStats && initialDueFilter) vm.setFilter(DecisionFilter.Due)
    }
    LaunchedEffect(initialSearchQuery, showStats) {
        if (!showStats) searchQuery = initialSearchQuery
    }

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

    val timelineSource = if (showStats) decisions else filteredDecisions
    val searchedTimelineSource = if (showStats) timelineSource else searchDecisions(timelineSource, searchQuery, searchFields)
    var visibleDecisionCount by rememberSaveable(showStats, selectedFilter, searchQuery) { mutableIntStateOf(INITIAL_DECISION_PAGE_SIZE) }
    LaunchedEffect(searchedTimelineSource.size) {
        visibleDecisionCount = visibleDecisionCount.coerceAtMost(searchedTimelineSource.size.coerceAtLeast(INITIAL_DECISION_PAGE_SIZE))
    }
    val timelineDecisions = searchedTimelineSource.take(visibleDecisionCount)
    val insights = remember(decisions) { calculateSelfInsights(decisions) }
    val hasDecisions = decisionListState is DecisionListState.Content
    val timelineTitle = if (showStats) "决策时间线" else if (searchQuery.isNotBlank()) "搜索结果" else when (val filter = selectedFilter) {
        DecisionFilter.All -> "全部记录"
        DecisionFilter.Due -> "待回看的决定"
        DecisionFilter.Upcoming -> "等待回看的决定"
        DecisionFilter.Reviewed -> "已回看的决定"
        DecisionFilter.Unscheduled -> "尚未设置日期的决定"
        is DecisionFilter.Preset -> "${filter.period.label()}的决定"
        is DecisionFilter.Custom -> "指定范围内的决定"
    }
    // These are two distinct tab experiences.  Keeping a list state per mode prevents
    // the archive from opening midway down the decisions timeline after tab switching.
    val timelineListState = remember(showStats) { LazyListState() }

    LazyColumn(
        state = timelineListState,
        modifier = Modifier.padding(horizontal = JournalDimens.pageHorizontal, vertical = JournalDimens.pageVertical),
        verticalArrangement = Arrangement.spacedBy(JournalDimens.sectionSpacing),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(JournalDimens.cardSpacing)) {
                JournalTopBar(
                    title = if (showStats) "我的档案" else "全部决定",
                    subtitle = if (showStats) "从记录里，看见自己如何选择" else "按时间回看每一次判断",
                    trailing = {
                        TextButton(onClick = onCreate) { Text("新建") }
                    },
                )
                if (!showStats && hasDecisions) {
                    PeriodOverview(
                        counts = periodCounts,
                        statusCounts = statusCounts,
                        selectedFilter = selectedFilter,
                        customError = customError,
                        onSelect = { vm.selectFilter(DecisionFilter.Preset(it)) },
                        onSelectStatus = vm::selectFilter,
                        onCustomRange = ::chooseCustomRange,
                        onClear = vm::clearFilter,
                    )
                    JournalTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("搜索决定") },
                        placeholder = { Text("问题、选项、结果或在意的事") },
                        minLines = 1,
                        maxLines = 1,
                    )
                }
                if (showStats && hasDecisions) {
                    Overview(stats.completedCount, stats.mostCaredAbout, stats.mostCaredAboutEvidenceCount, stats.dueCount)
                    Text(
                        "记录是给未来的线索，不是给现在的评分。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (insights.isEmpty()) {
                        SoftSurfaceCard(modifier = Modifier.fillMaxWidth(), containerColor = MistSand) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("还在认识自己", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "再积累至少 3 条重复出现的在意或担忧，会在这里生成可追溯的观察。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    insights.forEachIndexed { index, insight ->
                        val insightColor = listOf(MistBlue, MistSand, MistGreen)[index % 3]
                        val explore = onExploreKeyword?.let { openKeyword -> { openKeyword(insight.description) } }
                        NarrativeCard(
                            modifier = Modifier.fillMaxWidth(),
                            color = insightColor,
                            onClick = explore,
                            accessibilityLabel = if (explore == null) null else "${insight.title}：${insight.description}。查看相关决定",
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(insight.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(insight.description, style = MaterialTheme.typography.titleMedium)
                                Text("来自 ${insight.evidenceCount} 条记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (explore != null) {
                                    Text("查看相关决定", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
                when (val state = decisionListState) {
                    DecisionListState.Loading -> {
                        SectionHeader("正在读取档案")
                        SoftSurfaceCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surface) {
                            Text("正在读取你的本机记录…", modifier = Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    is DecisionListState.Error -> {
                        SectionHeader("暂时无法打开档案")
                        SoftSurfaceCard(modifier = Modifier.fillMaxWidth(), containerColor = MistSand) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(onClick = vm::retry) { Text("重试") }
                            }
                        }
                    }
                    else -> {
                        SectionHeader(if (showStats && !hasDecisions) "从这里开始" else timelineTitle)
                        if (timelineDecisions.isEmpty()) {
                            val isSearching = !showStats && searchQuery.isNotBlank()
                            val displaySearchQuery = searchQuery.trim().let { query ->
                                if (query.length > 20) "${query.take(20)}…" else query
                            }
                            val emptyAction: () -> Unit = when {
                                isSearching -> { { searchQuery = "" } }
                                showStats || selectedFilter == DecisionFilter.All -> onCreate
                                else -> vm::clearFilter
                            }
                            EmptyJournalState(
                                when {
                                    isSearching -> "没有找到包含“$displaySearchQuery”的决定。"
                                    showStats && !hasDecisions -> "还没有属于你的档案，先留下第一个决定吧。"
                                    selectedFilter == DecisionFilter.All -> "还没有记录，先写下第一个决定吧。"
                                    else -> "这个筛选条件下还没有决定。"
                                },
                                when {
                                    isSearching -> "清除搜索"
                                    showStats || selectedFilter == DecisionFilter.All -> "记录一个决定"
                                    else -> "清除筛选"
                                },
                                emptyAction,
                                primaryAction = !isSearching && (showStats || selectedFilter == DecisionFilter.All),
                            )
                        }
                    }
                }
            }
        }
        if (decisionListState !is DecisionListState.Error) {
            itemsIndexed(timelineDecisions, key = { _, decision -> decision.id }) { index, decision ->
                TimelineItem(
                    decision = decision,
                    now = now,
                    isFirst = index == 0,
                    isLast = index == timelineDecisions.lastIndex,
                    onOpen = onOpen,
                )
            }
            if (timelineDecisions.size < searchedTimelineSource.size) {
                item {
                    TextButton(
                        onClick = { visibleDecisionCount = nextDecisionPageSize(visibleDecisionCount, searchedTimelineSource.size) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("加载更多（${timelineDecisions.size}/${searchedTimelineSource.size}）") }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun PeriodOverview(
    counts: PeriodCounts,
    statusCounts: DecisionStatusCounts,
    selectedFilter: DecisionFilter,
    customError: String?,
    onSelect: (DecisionPeriod) -> Unit,
    onSelectStatus: (DecisionFilter) -> Unit,
    onCustomRange: () -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (selectedFilter != DecisionFilter.All && selectedFilter !is DecisionFilter.Custom) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                ArchiveKicker(selectedFilter.label())
                TextButton(onClick = onClear) { Text("查看全部") }
            }
        }
        ArchiveKicker("按状态查找")
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusFilterChip("待回看", statusCounts.due, selectedFilter == DecisionFilter.Due, onClick = { onSelectStatus(DecisionFilter.Due) }, modifier = Modifier.weight(1f))
                StatusFilterChip("等待中", statusCounts.upcoming, selectedFilter == DecisionFilter.Upcoming, onClick = { onSelectStatus(DecisionFilter.Upcoming) }, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusFilterChip("已回看", statusCounts.reviewed, selectedFilter == DecisionFilter.Reviewed, onClick = { onSelectStatus(DecisionFilter.Reviewed) }, modifier = Modifier.weight(1f))
                StatusFilterChip("未设日期", statusCounts.unscheduled, selectedFilter == DecisionFilter.Unscheduled, onClick = { onSelectStatus(DecisionFilter.Unscheduled) }, modifier = Modifier.weight(1f))
            }
        }
        ArchiveKicker("按时间回看")
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
            hero = custom != null,
            borderColor = if (custom != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.52f) else null,
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
private fun StatusFilterChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    SoftSurfaceCard(
        modifier = modifier.height(48.dp),
        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        borderColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.52f) else null,
        onClick = onClick,
        accessibilityLabel = "$label $count",
        accessibilityState = if (selected) "已选中" else "未选中",
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("$label $count", style = MaterialTheme.typography.labelMedium, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

private fun DecisionFilter.label(): String = when (this) {
    DecisionFilter.Due -> "只看待回看"
    DecisionFilter.Upcoming -> "只看等待中"
    DecisionFilter.Reviewed -> "只看已回看"
    DecisionFilter.Unscheduled -> "只看未设日期"
    else -> "筛选结果"
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
        modifier = modifier.height(48.dp),
        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        borderColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.52f) else null,
        onClick = onClick,
        accessibilityLabel = if (count > 0) "$label $count" else label,
        accessibilityState = if (selected) "已选中" else "未选中",
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (count > 0) "$label $count" else label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
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
private fun Overview(completed: Int, caredAbout: String?, caredAboutEvidenceCount: Int, due: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NarrativeCard(modifier = Modifier.fillMaxWidth(), color = MistBlue) {
            Row(
                Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("已回看的决定", style = MaterialTheme.typography.labelMedium)
                    Text("$completed", style = MaterialTheme.typography.displaySmall)
                    Text("段记录，慢慢认识自己的选择", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                Icon(Icons.Rounded.CalendarToday, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 4.dp))
            }
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallInsightCard(
                "最常在意",
                if (caredAbout != null && caredAboutEvidenceCount >= 3) caredAbout else "样本不足",
                if (caredAbout != null && caredAboutEvidenceCount >= 3) "来自 $caredAboutEvidenceCount 条记录" else "积累 3 条后显示",
                Icons.Rounded.FavoriteBorder,
                Modifier.weight(1f),
                MaterialTheme.colorScheme.surface,
            )
            SmallInsightCard("待复盘", "$due 个", null, Icons.Rounded.Schedule, Modifier.weight(1f), MistGreen)
        }
    }
}

@Composable
private fun SmallInsightCard(
    title: String,
    value: String,
    supporting: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
    color: Color,
) {
    SoftSurfaceCard(modifier = modifier.fillMaxHeight(), containerColor = color) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(18.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
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
    val due = decision.reviewDate != null && decision.reviewDate <= now && decision.status != DecisionStatus.REVIEWED
    val reviewed = decision.status == DecisionStatus.REVIEWED
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
            accessibilityLabel = "$statusText，${decision.question}。打开决定详情",
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
