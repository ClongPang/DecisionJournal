package com.example.decisionjournal.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.decisionjournal.data.model.ExpectationMatch
import com.example.decisionjournal.data.model.Review
import com.example.decisionjournal.ui.DetailViewModel
import com.example.decisionjournal.ui.components.JournalTopBar
import com.example.decisionjournal.ui.components.SectionHeader
import com.example.decisionjournal.ui.components.SoftSurfaceCard
import com.example.decisionjournal.ui.components.StatusPill
import com.example.decisionjournal.ui.theme.JournalDimens
import com.example.decisionjournal.ui.theme.MistBlue
import com.example.decisionjournal.ui.theme.MistGreen
import com.example.decisionjournal.ui.theme.MistSand
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val detailDateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")

@Composable
fun DecisionDetailScreen(
    id: Long,
    onReview: () -> Unit,
    onEdit: () -> Unit,
    onBack: () -> Unit,
    vm: DetailViewModel = hiltViewModel(),
) {
    val decision by vm.decision(id).collectAsStateWithLifecycle(null)
    val choices by vm.choices(id).collectAsStateWithLifecycle(emptyList())
    val reviews by vm.reviews(id).collectAsStateWithLifecycle(emptyList())
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = JournalDimens.pageHorizontal, vertical = JournalDimens.pageVertical),
        verticalArrangement = Arrangement.spacedBy(JournalDimens.cardSpacing),
    ) {
        JournalTopBar(title = "决定详情", onBack = onBack, trailing = {
            Text("本地记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        })

        decision?.let { d ->
            val reviewed = d.status.name == "REVIEWED"
            SoftSurfaceCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = if (reviewed) MistGreen else MistBlue,
                hero = true,
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        Text("当时的决定", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        StatusPill(if (reviewed) "已回看" else "等待回看", if (reviewed) MistGreen else MistBlue)
                    }
                    Text(d.question, style = MaterialTheme.typography.headlineSmall)
                    d.context?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("决定于 ${formatDate(d.decisionDate)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        d.reviewDate?.let { Text("回看于 ${formatDate(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }

            SectionHeader("我当时怎么想")
            if (choices.isNotEmpty()) {
                Text("候选选项", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                choices.forEach { choice ->
                    val selected = choice.id == d.selectedChoiceId
                    SoftSurfaceCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(choice.text, style = MaterialTheme.typography.titleMedium)
                                if (selected) StatusPill("当时选择", MaterialTheme.colorScheme.primaryContainer)
                            }
                            if (choice.benefits.isNotEmpty()) NoteLine("让我期待", choice.benefits.joinToString("、"))
                            if (choice.concerns.isNotEmpty()) NoteLine("让我犹豫", choice.concerns.joinToString("、"))
                        }
                    }
                }
            }
            if (d.benefits.isNotEmpty()) DetailNoteCard("我在意的事", d.benefits, MistGreen)
            if (d.concerns.isNotEmpty()) DetailNoteCard("我担心的事", d.concerns, MistSand)
            d.expectedOutcome?.takeIf { it.isNotBlank() }?.let {
                DetailNoteCard("当时的预期", listOf(it) + (d.confidence?.let { level -> listOf("判断信心：$level/5") } ?: emptyList()), MaterialTheme.colorScheme.surface)
            }
            d.futureNote?.takeIf { it.isNotBlank() }?.let {
                SoftSurfaceCard(modifier = Modifier.fillMaxWidth(), containerColor = MistSand, hero = true) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("写给未来的自己", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(it, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
                ) { Text("编辑") }
                Button(onClick = onReview, modifier = Modifier.weight(1.35f), shape = MaterialTheme.shapes.medium) { Text("记录复盘") }
            }

            SectionHeader("后来发生了什么")
            if (reviews.isEmpty()) {
                SoftSurfaceCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("还没有复盘记录", style = MaterialTheme.typography.titleMedium)
                        Text("等事情走过一段路，再回来写下结果。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                reviews.forEachIndexed { index, review -> ReviewArchiveCard(index, review) }
            }
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text("删除此决定", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("删除决定？") },
        text = { Text("关联的候选选项和复盘也会被删除。") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(id, onBack) }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
    )
}

@Composable
private fun ReviewArchiveCard(index: Int, review: Review) {
    SoftSurfaceCard(modifier = Modifier.fillMaxWidth(), containerColor = if (index == 0) MistGreen else MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("第 ${index + 1} 次回看", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatDate(review.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(review.result, style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                review.satisfaction?.let { StatusPill("满意度 $it/5", MistGreen) }
                review.expectationMatch?.let { StatusPill(it.label(), MistBlue) }
            }
            review.accurateJudgment?.takeIf { it.isNotBlank() }?.let { NoteLine("判断准确", it) }
            review.unexpectedFinding?.takeIf { it.isNotBlank() }?.let { NoteLine("意外发现", it) }
            review.nextTimeNote?.takeIf { it.isNotBlank() }?.let { NoteLine("下次注意", it) }
        }
    }
}

@Composable
private fun DetailNoteCard(title: String, notes: List<String>, color: androidx.compose.ui.graphics.Color) {
    SoftSurfaceCard(modifier = Modifier.fillMaxWidth(), containerColor = color) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            notes.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun NoteLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatDate(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate().format(detailDateFormatter)

private fun ExpectationMatch.label(): String = when (this) {
    ExpectationMatch.EXPECTED -> "符合预期"
    ExpectationMatch.BETTER -> "比预期好"
    ExpectationMatch.WORSE -> "比预期差"
    ExpectationMatch.UNCLEAR -> "还不确定"
}
