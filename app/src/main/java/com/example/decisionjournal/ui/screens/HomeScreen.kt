package com.example.decisionjournal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.example.decisionjournal.ui.components.JournalTopBar
import com.example.decisionjournal.ui.components.PrimaryActionButton
import com.example.decisionjournal.ui.components.SoftSurfaceCard
import com.example.decisionjournal.ui.components.StatusPill
import com.example.decisionjournal.ui.theme.JournalDimens
import com.example.decisionjournal.ui.theme.MistBlue
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val homeDate = DateTimeFormatter.ofPattern("M月d日")

@Composable
fun HomeScreen(onCreate: () -> Unit, onOpen: (Long) -> Unit, vm: HomeViewModel = hiltViewModel()) {
    val due by vm.due.collectAsStateWithLifecycle()
    val all by vm.all.collectAsStateWithLifecycle()
    val recent = all.firstOrNull()
    Column(
        Modifier.fillMaxSize().padding(horizontal = JournalDimens.pageHorizontal, vertical = JournalDimens.pageVertical),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        JournalTopBar(
            title = "回看",
            subtitle = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate().format(homeDate),
        )
        Text("给自己一点时间", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 18.dp))
        when {
            due.isNotEmpty() -> {
                Text("最近的决定", style = MaterialTheme.typography.titleMedium)
                DecisionCard(due.first(), "待复盘", onOpen)
            }
            recent != null -> {
                Text("最近的决定", style = MaterialTheme.typography.titleMedium)
                DecisionCard(recent, if (recent.status == DecisionStatus.REVIEWED) "已回看" else "等待回看", onOpen)
            }
            else -> Text("今天没有待复盘的决定。\n先把此刻的想法留下来。", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.weight(1f))
        PrimaryActionButton("记录一个决定", onCreate)
    }
}

@Composable
private fun DecisionCard(decision: Decision, status: String, onOpen: (Long) -> Unit) {
    SoftSurfaceCard(modifier = Modifier.fillMaxWidth(), containerColor = MistBlue, hero = true, onClick = { onOpen(decision.id) }) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(Icons.Rounded.CalendarToday, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    when (status) {
                        "待复盘" -> "该回来看看这段记录了"
                        "已回看" -> "这是一段已经回看的记录"
                        else -> "一段正在等待答案的记录"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text(decision.question, style = MaterialTheme.typography.headlineSmall)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.Schedule, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "决定于 " + Instant.ofEpochMilli(decision.decisionDate).atZone(ZoneId.systemDefault()).toLocalDate().format(homeDate),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        when (status) {
                            "待复盘" -> "现在就可以回来记录结果"
                            "等待回看" -> "还有时间，记得回来看看"
                            else -> "这段记录已经被回看"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            StatusPill(status, if (status == "待复盘") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer)
        }
    }
}
