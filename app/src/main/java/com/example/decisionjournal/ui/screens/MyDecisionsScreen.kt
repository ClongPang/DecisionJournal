package com.example.decisionjournal.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.decisionjournal.data.model.Decision
import com.example.decisionjournal.ui.DecisionsViewModel
import com.example.decisionjournal.ui.components.EmptyJournalState
import com.example.decisionjournal.ui.components.JournalTopBar
import com.example.decisionjournal.ui.components.SectionHeader
import com.example.decisionjournal.ui.components.SoftSurfaceCard
import com.example.decisionjournal.ui.components.StatusPill
import com.example.decisionjournal.ui.theme.JournalDimens
import com.example.decisionjournal.ui.theme.MistBlue
import com.example.decisionjournal.ui.theme.MistGreen
import java.time.Instant
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
    val now = remember { System.currentTimeMillis() }

    Column(
        Modifier.padding(horizontal = JournalDimens.pageHorizontal, vertical = JournalDimens.pageVertical)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(JournalDimens.sectionSpacing),
    ) {
        JournalTopBar(
            title = if (showStats) "我的决定" else "全部决定",
            subtitle = if (showStats) "每一次选择，都值得被记住" else null,
        )

        if (showStats) {
            Overview(stats.completedCount, stats.mostCaredAbout ?: "还在认识自己", stats.dueCount)
        }

        SectionHeader(if (showStats) "决策时间线" else "全部记录")
        if (decisions.isEmpty()) {
            EmptyJournalState("还没有记录，先写下一个决定吧。", "记录一个决定", onCreate)
        } else {
            TimelineContainer(decisions, now, onOpen)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Overview(completed: Int, caredAbout: String, due: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SoftSurfaceCard(modifier = Modifier.fillMaxWidth(), containerColor = MistBlue) {
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallInsightCard("最常在意", caredAbout, Icons.Rounded.FavoriteBorder, Modifier.weight(1f), MaterialTheme.colorScheme.surface)
            SmallInsightCard("待复盘", "$due 个", Icons.Rounded.Schedule, Modifier.weight(1f), MistGreen)
        }
    }
}

@Composable
private fun SmallInsightCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, color: Color) {
    SoftSurfaceCard(modifier = modifier, containerColor = color) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(18.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TimelineContainer(decisions: List<Decision>, now: Long, onOpen: (Long) -> Unit) {
    SoftSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
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
        Column(Modifier.weight(1f)) {
            Surface(
                onClick = { onOpen(decision.id) },
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(decision.question, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            Instant.ofEpochMilli(decision.updatedAt).atZone(ZoneId.systemDefault()).toLocalDate().format(timelineDate),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        StatusPill(statusText, if (due) MistBlue else if (reviewed) MistGreen else MaterialTheme.colorScheme.surfaceVariant)
                    }
                    Icon(Icons.Rounded.ArrowForwardIos, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.height(15.dp))
                }
            }
            if (!isLast) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
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
