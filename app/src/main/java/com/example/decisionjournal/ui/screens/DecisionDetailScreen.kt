package com.example.decisionjournal.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.decisionjournal.data.model.ExpectationMatch
import com.example.decisionjournal.data.model.Review
import com.example.decisionjournal.data.model.DecisionStatus
import com.example.decisionjournal.ui.DetailViewModel
import com.example.decisionjournal.ui.DecisionLoadState
import com.example.decisionjournal.ui.components.JournalTopBar
import com.example.decisionjournal.ui.components.JournalErrorText
import com.example.decisionjournal.ui.components.SectionHeader
import com.example.decisionjournal.ui.components.SoftSurfaceCard
import com.example.decisionjournal.ui.components.StatusPill
import com.example.decisionjournal.ui.components.ArchiveKicker
import com.example.decisionjournal.ui.components.ChoiceSelectionRail
import com.example.decisionjournal.ui.theme.JournalDimens
import com.example.decisionjournal.ui.theme.MistBlue
import com.example.decisionjournal.ui.theme.MistGreen
import com.example.decisionjournal.ui.theme.MistSand
import com.example.decisionjournal.ui.theme.Hairline
import com.example.decisionjournal.ui.theme.MutedTerracotta
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val detailDateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

@Composable
fun DecisionDetailScreen(
    id: Long,
    reminderWarning: Boolean = false,
    onReview: () -> Unit,
    onEdit: () -> Unit,
    onBack: () -> Unit,
    onReturnHome: () -> Unit = onBack,
    vm: DetailViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val decisionState by vm.decisionState(id).collectAsStateWithLifecycle(DecisionLoadState.Loading)
    val choices by vm.choices(id).collectAsStateWithLifecycle(emptyList())
    val reviews by vm.reviews(id).collectAsStateWithLifecycle(emptyList())
    val now by vm.now.collectAsStateWithLifecycle()
    var showReminderWarning by remember(reminderWarning) { mutableStateOf(reminderWarning) }
    var confirmDelete by remember { mutableStateOf(false) }
    fun openNotificationSettings() {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        )
    }
    val decision = when (val state = decisionState) {
        DecisionLoadState.Loading -> {
            DecisionStatePage("回看这一刻", "正在打开这段记录…", onBack)
            return
        }
        DecisionLoadState.Missing -> {
            DecisionStatePage("回看这一刻", "这条决定可能已被删除，或链接已经失效。", onBack, onReturnHome, missing = true)
            return
        }
        is DecisionLoadState.Content -> state.decision
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = JournalDimens.pageHorizontal, vertical = JournalDimens.pageVertical),
        verticalArrangement = Arrangement.spacedBy(JournalDimens.cardSpacing),
    ) {
        JournalTopBar(title = "这一次决定", onBack = onBack)

        if (showReminderWarning || vm.reminderError != null) {
            SoftSurfaceCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MistSand,
                borderColor = MutedTerracotta.copy(alpha = 0.38f),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = MutedTerracotta)
                        Text("提醒尚未安排", style = MaterialTheme.typography.titleMedium, color = MutedTerracotta)
                    }
                    Text(
                        vm.reminderError ?: "内容已保存，但复盘提醒未安排。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { vm.retryReminder(id) { showReminderWarning = false } },
                        enabled = !vm.reminderRetrying,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        border = BorderStroke(1.dp, MutedTerracotta.copy(alpha = 0.68f)),
                    ) { Text(if (vm.reminderRetrying) "正在安排…" else "重新安排提醒") }
                    TextButton(onClick = ::openNotificationSettings) { Text("打开通知设置") }
                }
            }
        }
        vm.deleteError?.let { JournalErrorText(it) }

        decision.let { d ->
            val reviewed = d.status == DecisionStatus.REVIEWED
            val due = !reviewed && d.reviewDate != null && d.reviewDate <= now
            val statusText = when {
                reviewed -> "已回看"
                due -> "待复盘"
                d.reviewDate != null -> "等待回看"
                else -> "尚未设置日期"
            }
            SoftSurfaceCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = if (reviewed) MistGreen else if (due) MistBlue else MaterialTheme.colorScheme.surface,
                hero = true,
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        ArchiveKicker("那时的判断")
                        StatusPill(statusText, if (reviewed) MistGreen else if (due) MistBlue else MaterialTheme.colorScheme.surfaceVariant)
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

            SectionHeader("那时")
            if (choices.isNotEmpty()) {
                Text("候选选项", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (d.selectedChoiceId == null) {
                    Text("当时还没有确定最终选择。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SoftSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        choices.forEachIndexed { index, choice ->
                            ChoiceArchiveRow(
                                choice = choice,
                                selected = choice.id == d.selectedChoiceId,
                            )
                            if (index < choices.lastIndex) {
                                Spacer(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
                            }
                        }
                    }
                }
            }
            if (d.benefits.isNotEmpty() || d.concerns.isNotEmpty() || !d.expectedOutcome.isNullOrBlank()) {
                DecisionContextPanel(
                    benefits = d.benefits,
                    concerns = d.concerns,
                    expectedOutcome = d.expectedOutcome,
                    confidence = d.confidence,
                )
            }
            d.futureNote?.takeIf { it.isNotBlank() }?.let {
                SoftSurfaceCard(modifier = Modifier.fillMaxWidth(), containerColor = MistSand, hero = true) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("写给未来的自己", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(it, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            val latestReview = reviews.firstOrNull()
            if (!d.expectedOutcome.isNullOrBlank() && latestReview != null) {
                SectionHeader("预期与实际")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReflectionPanel("预期", d.expectedOutcome, MistBlue, Modifier.weight(1f))
                    ReflectionPanel("实际", latestReview.result, MistGreen, Modifier.weight(1f))
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
                ) { Text("编辑") }
                Button(onClick = onReview, modifier = Modifier.weight(1.35f), shape = MaterialTheme.shapes.medium) {
                    Text(
                        when {
                            reviewed -> "补充一次回看"
                            due -> "现在回看"
                            d.reviewDate != null -> "提前记录进展"
                            else -> "记录复盘"
                        },
                    )
                }
            }

            SectionHeader("后来")
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
        text = { Text("将永久删除 ${choices.size} 个候选项和 ${reviews.size} 条回看，且无法恢复。") },
        confirmButton = { TextButton(enabled = !vm.deleting, onClick = { confirmDelete = false; vm.delete(id, onBack) }) { Text(if (vm.deleting) "删除中…" else "删除") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
    )
}

@Composable
private fun DecisionStatePage(
    title: String,
    message: String,
    onBack: () -> Unit,
    onReturnHome: () -> Unit = onBack,
    missing: Boolean = false,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = JournalDimens.pageHorizontal, vertical = JournalDimens.pageVertical),
        verticalArrangement = Arrangement.spacedBy(JournalDimens.cardSpacing),
    ) {
        JournalTopBar(title = title, onBack = onBack)
        SoftSurfaceCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (missing) "找不到这条决定" else "正在加载", style = MaterialTheme.typography.titleMedium)
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (missing) TextButton(onClick = onReturnHome) { Text("回到今天") }
            }
        }
    }
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
private fun ChoiceArchiveRow(choice: com.example.decisionjournal.data.model.Choice, selected: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected) ChoiceSelectionRail(Modifier.padding(end = 10.dp))
                Text(choice.text, style = MaterialTheme.typography.titleMedium)
            }
            if (selected) StatusPill("当时选择", MaterialTheme.colorScheme.primaryContainer)
        }
        if (choice.benefits.isNotEmpty()) NoteLine("让我期待", choice.benefits.joinToString("、"))
        if (choice.concerns.isNotEmpty()) NoteLine("让我犹豫", choice.concerns.joinToString("、"))
    }
}

@Composable
private fun DecisionContextPanel(
    benefits: List<String>,
    concerns: List<String>,
    expectedOutcome: String?,
    confidence: Int?,
) {
    val sections = buildList {
        if (benefits.isNotEmpty()) add("我在意的事" to benefits.joinToString("、"))
        if (concerns.isNotEmpty()) add("我担心的事" to concerns.joinToString("、"))
        expectedOutcome?.takeIf { it.isNotBlank() }?.let { value ->
            add("当时的预期" to listOf(value, confidence?.let { "判断信心：$it/5" }).filterNotNull().joinToString("\n"))
        }
    }
    SoftSurfaceCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surface) {
        Column {
            sections.forEachIndexed { index, (title, value) ->
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.bodyMedium)
                }
                if (index < sections.lastIndex) {
                    Spacer(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
                }
            }
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

@Composable
private fun ReflectionPanel(title: String, content: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    SoftSurfaceCard(modifier = modifier, containerColor = color) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(content, style = MaterialTheme.typography.bodySmall, maxLines = 5)
        }
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
