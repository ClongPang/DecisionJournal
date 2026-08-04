package com.example.decisionjournal.ui.screens

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.decisionjournal.ui.DetailViewModel
import com.example.decisionjournal.ui.components.SectionHeader
import com.example.decisionjournal.ui.components.SoftSurfaceCard
import com.example.decisionjournal.ui.components.StatusPill
import com.example.decisionjournal.ui.theme.JournalDimens
import com.example.decisionjournal.ui.theme.MistBlue
import com.example.decisionjournal.ui.theme.MistGreen
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = JournalDimens.pageHorizontal, vertical = JournalDimens.pageVertical), verticalArrangement = Arrangement.spacedBy(JournalDimens.cardSpacing)) {
        TextButton(onClick = onBack) { Text("‹ 返回") }
        decision?.let { d ->
            Text(d.question, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 4.dp))
            d.context?.takeIf { it.isNotBlank() }?.let { Text(it) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatusPill(if (d.status.name == "REVIEWED") "已回看" else "等待回看", if (d.status.name == "REVIEWED") MistGreen else MistBlue)
                d.reviewDate?.let { Text("复盘日：" + Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(detailDateFormatter), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            SectionHeader("候选选项")
            choices.forEach { choice ->
                SoftSurfaceCard(modifier = Modifier.fillMaxWidth(), containerColor = if (choice.id == d.selectedChoiceId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (choice.id == d.selectedChoiceId) "✓  ${choice.text}" else choice.text, style = MaterialTheme.typography.titleMedium)
                        if (choice.benefits.isNotEmpty()) Text("利好：${choice.benefits.joinToString("、")}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (choice.concerns.isNotEmpty()) Text("担忧：${choice.concerns.joinToString("、")}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (d.benefits.isNotEmpty()) DetailNoteCard("我在意的事", d.benefits)
            if (d.concerns.isNotEmpty()) DetailNoteCard("我担心的事", d.concerns)
            d.futureNote?.takeIf { it.isNotBlank() }?.let {
                SoftSurfaceCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("写给未来的自己", style = MaterialTheme.typography.titleMedium); Text(it) }
                }
            }
            d.expectedOutcome?.takeIf { it.isNotBlank() }?.let {
                DetailNoteCard("当时的预期", listOf(it) + (d.confidence?.let { level -> listOf("判断信心：$level/5") } ?: emptyList()))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("编辑") }
                Button(onClick = onReview, modifier = Modifier.weight(1f)) { Text("记录复盘") }
            }
            SectionHeader("复盘记录")
            if (reviews.isEmpty()) Text("还没有复盘记录")
            reviews.forEach { review ->
                SoftSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(review.result)
                        review.satisfaction?.let { Text("满意度 $it/5", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
                        review.expectationMatch?.let { Text("与预期：${it.label()}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
                        review.accurateJudgment?.takeIf { it.isNotBlank() }?.let { Text("判断准确：$it") }
                        review.unexpectedFinding?.takeIf { it.isNotBlank() }?.let { Text("意外发现：$it") }
                        review.nextTimeNote?.takeIf { it.isNotBlank() }?.let { Text("下次注意：$it") }
                    }
                }
            }
            TextButton(onClick = { confirmDelete = true }) { Text("删除此决定", color = MaterialTheme.colorScheme.error) }
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

private fun com.example.decisionjournal.data.model.ExpectationMatch.label(): String = when (this) {
    com.example.decisionjournal.data.model.ExpectationMatch.EXPECTED -> "符合预期"
    com.example.decisionjournal.data.model.ExpectationMatch.BETTER -> "比预期好"
    com.example.decisionjournal.data.model.ExpectationMatch.WORSE -> "比预期差"
    com.example.decisionjournal.data.model.ExpectationMatch.UNCLEAR -> "还不确定"
}

@Composable
private fun DetailNoteCard(title: String, notes: List<String>) {
    SoftSurfaceCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            notes.forEach { Text("•  $it") }
        }
    }
}
