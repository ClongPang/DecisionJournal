package com.example.decisionjournal.ui.screens

import android.app.DatePickerDialog
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.example.decisionjournal.data.ReviewInput
import com.example.decisionjournal.data.SaveOutcome
import com.example.decisionjournal.data.reminderTimeLabel
import com.example.decisionjournal.data.model.ExpectationMatch
import com.example.decisionjournal.ui.ReviewViewModel
import com.example.decisionjournal.ui.DecisionLoadState
import com.example.decisionjournal.ui.SaveState
import com.example.decisionjournal.ui.components.JournalTopBar
import com.example.decisionjournal.ui.components.JournalErrorText
import com.example.decisionjournal.ui.components.JournalTextField
import com.example.decisionjournal.ui.components.PrimaryActionButton
import com.example.decisionjournal.ui.components.SoftSurfaceCard
import com.example.decisionjournal.ui.components.ArchiveKicker
import com.example.decisionjournal.ui.theme.JournalDimens
import com.example.decisionjournal.ui.theme.MistBlue
import com.example.decisionjournal.ui.theme.MistGreen
import com.example.decisionjournal.ui.theme.MistSand
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val reviewDateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)
private val expectationMatchSaver: Saver<ExpectationMatch?, String> = Saver(
    save = { value -> value?.name.orEmpty() },
    restore = { value -> value.takeIf(String::isNotBlank)?.let(ExpectationMatch::valueOf) },
)

@Composable
fun ReviewScreen(
    decisionId: Long,
    onDone: (SaveOutcome) -> Unit,
    onBack: () -> Unit,
    onReturnHome: () -> Unit = onBack,
    vm: ReviewViewModel = hiltViewModel(),
) {
    var result by rememberSaveable { mutableStateOf("") }
    var satisfaction by rememberSaveable { mutableStateOf("") }
    var nextReviewDate by rememberSaveable { mutableStateOf<Long?>(null) }
    var expectationMatch by rememberSaveable(stateSaver = expectationMatchSaver) { mutableStateOf<ExpectationMatch?>(null) }
    var accurateJudgment by rememberSaveable { mutableStateOf("") }
    var unexpectedFinding by rememberSaveable { mutableStateOf("") }
    var nextTimeNote by rememberSaveable { mutableStateOf("") }
    var hasUnsavedChanges by rememberSaveable { mutableStateOf(false) }
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    var pendingInput by remember { mutableStateOf<ReviewInput?>(null) }
    var showNotificationRationale by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingInput?.let { vm.save(it, onDone) }
        pendingInput = null
    }
    // Do not recreate cold flows on every field edit: doing so restarts their Loading emission
    // and makes the review form visibly flash.
    val decisionStateFlow = remember(vm, decisionId) { vm.decisionState(decisionId) }
    val choicesFlow = remember(vm, decisionId) { vm.choices(decisionId) }
    val reviewsFlow = remember(vm, decisionId) { vm.reviews(decisionId) }
    val decisionState by decisionStateFlow.collectAsStateWithLifecycle(DecisionLoadState.Loading)
    val choices by choicesFlow.collectAsStateWithLifecycle(emptyList())
    val reviews by reviewsFlow.collectAsStateWithLifecycle(emptyList())

    fun showDatePicker() {
        val date = nextReviewDate?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
            ?: LocalDate.now().plusDays(30)
        DatePickerDialog(
            context,
            { _, year, month, day ->
                nextReviewDate = LocalDate.of(year, month + 1, day)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            },
            date.year,
            date.monthValue - 1,
            date.dayOfMonth,
        ).apply { datePicker.minDate = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }.show()
    }
    fun requestBack() {
        if (hasUnsavedChanges && vm.saveState != SaveState.Saving) confirmExit = true else onBack()
    }
    fun saveReview() {
        val input = ReviewInput(decisionId, result, satisfaction.toIntOrNull(), nextReviewDate, expectationMatch, accurateJudgment, unexpectedFinding, nextTimeNote)
        val needsPermission = Build.VERSION.SDK_INT >= 33 &&
            nextReviewDate != null &&
            nextReviewDate!! > System.currentTimeMillis() &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingInput = input
            showNotificationRationale = true
        } else {
            vm.save(input, onDone)
        }
    }
    BackHandler(onBack = ::requestBack)
    val decision = when (val state = decisionState) {
        DecisionLoadState.Loading -> {
            ReviewStatePage("正在打开这段记录…", onBack)
            return
        }
        DecisionLoadState.Missing -> {
            ReviewStatePage("这条决定可能已被删除，无法记录复盘。", onBack, onReturnHome, missing = true)
            return
        }
        is DecisionLoadState.Content -> state.decision
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = JournalDimens.pageHorizontal, vertical = JournalDimens.pageVertical),
        verticalArrangement = Arrangement.spacedBy(JournalDimens.cardSpacing),
    ) {
        JournalTopBar(title = "后来", subtitle = "把事情后来走到哪里写下来", onBack = ::requestBack)
        decision.let { current ->
            val selectedChoice = choices.firstOrNull { it.id == current.selectedChoiceId }
            SoftSurfaceCard(modifier = Modifier.fillMaxWidth(), containerColor = MistBlue) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    ArchiveKicker("当时的记录")
                    Text(current.question, style = MaterialTheme.typography.titleMedium)
                    selectedChoice?.let { Text("当时选择：${it.text}", style = MaterialTheme.typography.bodyMedium) }
                    current.expectedOutcome?.takeIf { it.isNotBlank() }?.let {
                        Text("当时预期：$it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    current.futureNote?.takeIf { it.isNotBlank() }?.let {
                        Text("写给未来的自己：$it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    reviews.firstOrNull()?.let { latestReview ->
                        Spacer(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
                        )
                        ArchiveKicker("此前回看")
                        Text(
                            "已记录 ${reviews.size} 次 · 最近一次 ${formatReviewDate(latestReview.createdAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "上次写下：${latestReview.result}",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "本次保存会追加一条新记录，不会覆盖之前的观察。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        SoftSurfaceCard(modifier = Modifier.fillMaxWidth(), containerColor = MistSand, hero = true) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("回到当时", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("当时的决定，现在感觉如何？", style = MaterialTheme.typography.headlineSmall)
                Text("不用给过去打分，只记录事情后来走到了哪里。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Text("后来发生了什么？", style = MaterialTheme.typography.headlineSmall)
        JournalTextField(
            value = result,
            onValueChange = { hasUnsavedChanges = true; vm.clearError(); result = it },
            modifier = Modifier.fillMaxWidth().height(180.dp),
            label = { Text("记录结果*") },
            placeholder = { Text("事情后来怎么样了？") },
        )

        Text("结果和预期相比如何？", style = MaterialTheme.typography.titleMedium)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ExpectationMatch.values().toList().chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { match ->
                        ReviewChoiceChip(
                            label = match.label(),
                            selected = expectationMatch == match,
                            onClick = { hasUnsavedChanges = true; expectationMatch = match },
                            modifier = Modifier.weight(1f),
                            color = MistBlue,
                        )
                    }
                }
            }
        }

        Text("满意度", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { score ->
                ReviewChoiceChip(
                    label = "$score",
                    selected = satisfaction == score.toString(),
                    onClick = { hasUnsavedChanges = true; satisfaction = score.toString() },
                    modifier = Modifier.weight(1f),
                    color = MistGreen,
                )
            }
        }
        Text("从 1 到 5，凭现在的感受选择即可。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text("留下几句观察", style = MaterialTheme.typography.titleMedium)
        JournalTextField(value = accurateJudgment, onValueChange = { hasUnsavedChanges = true; accurateJudgment = it }, modifier = Modifier.fillMaxWidth(), label = { Text("我判断准确的地方（可选）") })
        JournalTextField(value = unexpectedFinding, onValueChange = { hasUnsavedChanges = true; unexpectedFinding = it }, modifier = Modifier.fillMaxWidth(), label = { Text("我没想到的地方（可选）") })
        JournalTextField(value = nextTimeNote, onValueChange = { hasUnsavedChanges = true; nextTimeNote = it }, modifier = Modifier.fillMaxWidth(), label = { Text("下次我会注意什么（可选）") })

        SoftSurfaceCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("还要继续回看吗？", style = MaterialTheme.typography.titleMedium)
                Text("给这段经历留一个未来的回看日。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(
                        onClick = { hasUnsavedChanges = true; showDatePicker() },
                        modifier = Modifier.semantics {
                            contentDescription = nextReviewDate?.let { "修改下一次复盘日期：${Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(reviewDateFormatter)}" }
                                ?: "设置下一次复盘日期"
                        },
                    ) {
                        Text(if (nextReviewDate == null) "设置下一次复盘日期" else "修改日期")
                    }
                    nextReviewDate?.let {
                        Text(
                            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(reviewDateFormatter),
                            modifier = Modifier.padding(start = 12.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(
                            onClick = { hasUnsavedChanges = true; nextReviewDate = null },
                            modifier = Modifier.semantics { contentDescription = "清除下一次复盘日期，不再安排提醒" },
                        ) { Text("不再提醒") }
                    }
                }
            }
        }
        Text(
            if (nextReviewDate?.let { it <= System.currentTimeMillis() } == true) {
                "今天继续回看会在保存后立即显示为待回看，不会发送系统通知。"
            } else {
                "不设置日期也可以保存；设置未来日期后会在当天${reminderTimeLabel()}提醒你，实际到达可能略有延迟。"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        vm.error?.let { JournalErrorText(it) }
        Spacer(Modifier.height(4.dp))
        PrimaryActionButton(
            if (vm.saveState == SaveState.Saving) "保存中…" else "保存复盘",
            onClick = ::saveReview,
            enabled = vm.saveState != SaveState.Saving && result.trim().isNotEmpty() && (satisfaction.isBlank() || satisfaction.toIntOrNull() in 1..5),
        )
    }
    if (confirmExit) AlertDialog(
        onDismissRequest = { confirmExit = false },
        title = { Text("放弃未保存内容？") },
        text = { Text("离开后，刚才填写的内容不会保留。") },
        confirmButton = { TextButton(onClick = { confirmExit = false; onBack() }) { Text("放弃") } },
        dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("继续编辑") } },
    )
    if (showNotificationRationale) AlertDialog(
        onDismissRequest = { showNotificationRationale = false },
        title = { Text("要在下一次回看日提醒你吗？") },
        text = { Text("提醒仅用于你设置的下一次回看日期。即使不开启通知，这次复盘也会照常保存。") },
        confirmButton = {
            TextButton(onClick = {
                showNotificationRationale = false
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }) { Text("开启提醒") }
        },
        dismissButton = {
            TextButton(onClick = {
                showNotificationRationale = false
                pendingInput?.let { vm.save(it, onDone) }
                pendingInput = null
            }) { Text("仅保存") }
        },
    )
}

@Composable
private fun ReviewStatePage(
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
        JournalTopBar(title = "后来", subtitle = "把事情后来走到哪里写下来", onBack = onBack)
        SoftSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (missing) "无法记录复盘" else "正在加载", style = MaterialTheme.typography.titleMedium)
                Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                if (missing) TextButton(onClick = onReturnHome) { Text("回到今天") }
            }
        }
    }
}

@Composable
private fun ReviewChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    color: androidx.compose.ui.graphics.Color,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(52.dp).semantics {
            role = Role.RadioButton
            this.selected = selected
        },
        shape = MaterialTheme.shapes.small,
        color = if (selected) color else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
        contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(if (selected) "✓ $label" else label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun ExpectationMatch.label(): String = when (this) {
    ExpectationMatch.EXPECTED -> "符合预期"
    ExpectationMatch.BETTER -> "比预期好"
    ExpectationMatch.WORSE -> "比预期差"
    ExpectationMatch.UNCLEAR -> "还不确定"
}

private fun formatReviewDate(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate().format(reviewDateFormatter)
