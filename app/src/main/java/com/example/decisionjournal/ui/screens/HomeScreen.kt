package com.example.decisionjournal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.decisionjournal.data.model.Decision
import com.example.decisionjournal.data.model.DecisionStatus
import com.example.decisionjournal.ui.HomeViewModel
import com.example.decisionjournal.ui.DecisionListState
import com.example.decisionjournal.ui.components.NarrativeCard
import com.example.decisionjournal.ui.components.PrimaryActionButton
import com.example.decisionjournal.ui.components.StatusPill
import com.example.decisionjournal.ui.components.ArchiveKicker
import com.example.decisionjournal.ui.theme.JournalDimens
import com.example.decisionjournal.ui.theme.MistGreen
import com.example.decisionjournal.ui.theme.Hairline
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val homeDate = DateTimeFormatter.ofPattern("M月d日 · EEEE", Locale.CHINA)

@Composable
fun HomeScreen(
    onCreate: () -> Unit,
    onOpen: (Long) -> Unit,
    onViewDue: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val listState by vm.listState.collectAsStateWithLifecycle()
    val due by vm.due.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = JournalDimens.pageHorizontal, end = JournalDimens.pageHorizontal, top = JournalDimens.pageVertical, bottom = JournalDimens.buttonHeight + 40.dp),
        verticalArrangement = Arrangement.spacedBy(JournalDimens.cardSpacing),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ArchiveKicker("今日回看")
            Text(Instant.now().atZone(ZoneId.systemDefault()).toLocalDate().format(homeDate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("给自己一点时间", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 12.dp))
        Text("把当时的判断留住，等生活给出答案。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        when (listState) {
            DecisionListState.Loading -> HomeStatusCard("正在读取你的本机记录…")
            is DecisionListState.Error -> HomeStatusCard(
                "暂时无法读取本机记录。",
                actionText = "重试",
                onAction = vm::retry,
            )
            DecisionListState.Empty -> EmptyArchiveState()
            is DecisionListState.Content -> {
                val decisions = (listState as DecisionListState.Content).decisions
                val featured = due.firstOrNull() ?: decisions.firstOrNull()
                val featuredStatus = when {
                    due.isNotEmpty() -> "待回看"
                    featured?.status == DecisionStatus.REVIEWED -> "已回看"
                    featured?.reviewDate != null -> "等待回看"
                    else -> "此刻的判断"
                }
                featured?.let { decision ->
                    ArchiveCoverCard(decision, featuredStatus, onOpen)
                    if (due.size > 1) {
                        Text("接下来还要回看（还有 ${due.size - 1} 条）", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = onViewDue, modifier = Modifier.align(Alignment.End)) {
                            Text("查看全部 ${due.size} 条待回看")
                        }
                        due.drop(1).take(1).forEach { queued ->
                            Text(
                                "下一条：${queued.question}",
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
    PrimaryActionButton(
        "记录一个决定",
        onCreate,
        modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = JournalDimens.pageHorizontal, vertical = 10.dp),
    )
    }
}

@Composable
private fun HomeStatusCard(message: String, actionText: String? = null, onAction: (() -> Unit)? = null) {
    NarrativeCard(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (actionText != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionText) }
            }
        }
    }
}

@Composable
private fun ArchiveCoverCard(decision: Decision, status: String, onOpen: (Long) -> Unit) {
    NarrativeCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onOpen(decision.id) },
        accessibilityLabel = "$status，${decision.question}。打开决定详情",
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (status == "待回看") Icons.Rounded.Schedule else Icons.Rounded.AutoStories, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(if (status == "待回看") "该回来看看了" else "最近的一段记录", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusPill(status, if (status == "已回看") MistGreen else MaterialTheme.colorScheme.surface)
            }
            Text(decision.question, style = MaterialTheme.typography.headlineSmall)
            decision.context?.takeIf { it.isNotBlank() }?.let { Text(it, maxLines = 2, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.CalendarToday, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    if (status == "待回看") "现在就可以记录结果" else "决定于 ${Instant.ofEpochMilli(decision.decisionDate).atZone(ZoneId.systemDefault()).toLocalDate().format(homeDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyArchiveState() {
    Column(Modifier.fillMaxWidth().padding(top = 42.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ArchiveKicker("一段记录，会慢慢显影")
        Text("还没有写下任何决定。", style = MaterialTheme.typography.titleMedium)
        Text("从一个小小的选择开始，为未来留下线索。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            verticalAlignment = Alignment.Top,
        ) {
            ArchiveCyclePoint("01", "记录", "写下此刻", emphasized = true, Modifier.weight(1f))
            ArchiveCycleRule()
            ArchiveCyclePoint("02", "等待", "让生活发生", emphasized = false, Modifier.weight(1f))
            ArchiveCycleRule()
            ArchiveCyclePoint("03", "回看", "收下答案", emphasized = false, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ArchiveCyclePoint(index: String, title: String, note: String, emphasized: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(if (emphasized) MaterialTheme.colorScheme.primary else Hairline, MaterialTheme.shapes.small),
            )
            Text(index, style = MaterialTheme.typography.labelMedium, color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(title, style = MaterialTheme.typography.labelMedium)
        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ArchiveCycleRule() {
    Box(
        Modifier
            .padding(top = 4.dp)
            .width(14.dp)
            .height(1.dp)
            .background(Hairline),
    )
}
