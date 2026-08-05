package com.example.decisionjournal.ui.screens

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.example.decisionjournal.data.ChoiceInput
import com.example.decisionjournal.data.DecisionInput
import com.example.decisionjournal.data.SaveOutcome
import com.example.decisionjournal.ui.CreateDecisionViewModel
import com.example.decisionjournal.ui.DecisionEditorState
import com.example.decisionjournal.ui.SaveState
import com.example.decisionjournal.ui.components.JournalErrorText
import com.example.decisionjournal.ui.components.JournalTopBar
import com.example.decisionjournal.ui.components.JournalTextField
import com.example.decisionjournal.ui.components.NarrativeCard
import com.example.decisionjournal.ui.components.ChoiceSelectionRail
import com.example.decisionjournal.ui.components.SoftSurfaceCard
import com.example.decisionjournal.ui.theme.JournalDimens
import com.example.decisionjournal.ui.theme.SoftSand
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val createDate = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

private fun lines(value: String): List<String> = value.split(',', '，', '\n').map(String::trim).filter(String::isNotEmpty)

private val choiceListSaver: Saver<List<ChoiceInput>, List<String>> = Saver(
    save = { choices ->
        buildList {
            add(choices.size.toString())
            choices.forEach { choice ->
                add(choice.text)
                add(choice.benefits.size.toString())
                addAll(choice.benefits)
                add(choice.concerns.size.toString())
                addAll(choice.concerns)
            }
        }
    },
    restore = { values ->
        val choices = runCatching {
            var cursor = 0
            fun next() = values[cursor++]
            List(next().toInt()) {
                val text = next()
                val benefits = List(next().toInt()) { next() }
                val concerns = List(next().toInt()) { next() }
                ChoiceInput(text, benefits, concerns)
            }.also { check(cursor == values.size) }
        }.getOrDefault(emptyList())
        choices
    },
)

@Composable
fun CreateDecisionScreen(
    decisionId: Long?,
    onDone: (SaveOutcome) -> Unit,
    onBack: () -> Unit,
    onReturnHome: () -> Unit = onBack,
    vm: CreateDecisionViewModel = hiltViewModel(),
) {
    val editorState by (decisionId?.let { vm.editor(it) } ?: flowOf<DecisionEditorState>(DecisionEditorState.Loading)).collectAsStateWithLifecycle(DecisionEditorState.Loading)
    val editor = (editorState as? DecisionEditorState.Content)?.data
    var step by rememberSaveable { mutableStateOf(0) }
    var question by rememberSaveable { mutableStateOf("") }
    var contextText by rememberSaveable { mutableStateOf("") }
    var benefitsText by rememberSaveable { mutableStateOf("") }
    var concernsText by rememberSaveable { mutableStateOf("") }
    var futureNote by rememberSaveable { mutableStateOf("") }
    var expectedOutcome by rememberSaveable { mutableStateOf("") }
    var confidence by rememberSaveable { mutableStateOf("") }
    var choiceText by rememberSaveable { mutableStateOf("") }
    var choiceBenefits by rememberSaveable { mutableStateOf("") }
    var choiceConcerns by rememberSaveable { mutableStateOf("") }
    var editingChoiceIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var choices by rememberSaveable(stateSaver = choiceListSaver) { mutableStateOf(emptyList<ChoiceInput>()) }
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    var decisionDate by rememberSaveable { mutableStateOf(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()) }
    var reviewDate by rememberSaveable { mutableStateOf<Long?>(null) }
    var initialized by rememberSaveable { mutableStateOf(false) }
    var pendingInput by remember { mutableStateOf<DecisionInput?>(null) }
    var hasUnsavedChanges by rememberSaveable { mutableStateOf(false) }
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    var showNotificationRationale by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingInput?.let { vm.save(it, onDone) }
        pendingInput = null
    }

    LaunchedEffect(editor, decisionId) {
        val editorData = editor
        if (!initialized && editorData != null) {
            val decision = editorData.decision
            val loadedChoices = editorData.choices
            question = decision.question
            contextText = decision.context.orEmpty()
            benefitsText = decision.benefits.joinToString("，")
            concernsText = decision.concerns.joinToString("，")
            futureNote = decision.futureNote.orEmpty()
            expectedOutcome = decision.expectedOutcome.orEmpty()
            confidence = decision.confidence?.toString().orEmpty()
            choices = loadedChoices.map { ChoiceInput(it.text, it.benefits, it.concerns) }
            selected = loadedChoices.indexOfFirst { it.id == decision.selectedChoiceId }.takeIf { it >= 0 }
            decisionDate = decision.decisionDate
            reviewDate = decision.reviewDate
            initialized = true
        }
    }

    if (decisionId != null && editorState is DecisionEditorState.Loading) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = JournalDimens.pageHorizontal, vertical = JournalDimens.pageVertical),
            verticalArrangement = Arrangement.spacedBy(JournalDimens.cardSpacing),
        ) {
            JournalTopBar(title = "编辑决定", onBack = onBack)
            SoftSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("正在加载", style = MaterialTheme.typography.titleMedium)
                    Text("正在打开这段记录…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        return
    }
    if (decisionId != null && editorState is DecisionEditorState.Missing) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = JournalDimens.pageHorizontal, vertical = JournalDimens.pageVertical),
            verticalArrangement = Arrangement.spacedBy(JournalDimens.cardSpacing),
        ) {
            JournalTopBar(title = "编辑决定", onBack = onBack)
            SoftSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("找不到这条决定", style = MaterialTheme.typography.titleMedium)
                    Text("它可能已被删除，无法继续编辑。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onReturnHome) { Text("回到今天") }
                }
            }
        }
        return
    }

    fun save() {
        val input = DecisionInput(decisionId ?: 0L, question, contextText, reviewDate, selected, choices, lines(benefitsText), lines(concernsText), futureNote, expectedOutcome, confidence.toIntOrNull(), decisionDate)
        if (!vm.validateBeforePermissionRequest(input)) return
        val selectedReviewDate = reviewDate
        val needsPermission = Build.VERSION.SDK_INT >= 33 && selectedReviewDate != null && selectedReviewDate > System.currentTimeMillis() && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingInput = input
            showNotificationRationale = true
        } else {
            vm.save(input, onDone)
        }
    }
    fun requestBack() {
        if (hasUnsavedChanges && vm.saveState != SaveState.Saving) confirmExit = true else onBack()
    }
    BackHandler(onBack = ::requestBack)
    fun savePendingChoice() {
        if (choiceText.trim().isEmpty()) return
        hasUnsavedChanges = true
        val nextChoice = ChoiceInput(choiceText.trim(), lines(choiceBenefits), lines(choiceConcerns))
        choices = editingChoiceIndex?.takeIf { it in choices.indices }?.let { index ->
            choices.toMutableList().also { it[index] = nextChoice }
        } ?: (choices + nextChoice)
        editingChoiceIndex = null
        choiceText = ""
        choiceBenefits = ""
        choiceConcerns = ""
    }
    fun editChoice(index: Int) {
        val choice = choices[index]
        editingChoiceIndex = index
        choiceText = choice.text
        choiceBenefits = choice.benefits.joinToString("，")
        choiceConcerns = choice.concerns.joinToString("，")
    }
    fun cancelChoiceEdit() {
        editingChoiceIndex = null
        choiceText = ""
        choiceBenefits = ""
        choiceConcerns = ""
    }
    fun deleteChoice(index: Int) {
        hasUnsavedChanges = true
        choices = choices.toMutableList().also { it.removeAt(index) }
        selected = when {
            selected == index -> null
            selected != null && selected!! > index -> selected!! - 1
            else -> selected
        }
        editingChoiceIndex = when {
            editingChoiceIndex == index -> {
                choiceText = ""
                choiceBenefits = ""
                choiceConcerns = ""
                null
            }
            editingChoiceIndex != null && editingChoiceIndex!! > index -> editingChoiceIndex!! - 1
            else -> editingChoiceIndex
        }
    }
    fun moveChoice(index: Int, offset: Int) {
        val target = index + offset
        if (target !in choices.indices) return
        choices = choices.toMutableList().also { list ->
            val moved = list.removeAt(index)
            list.add(target, moved)
        }
        selected = when (selected) {
            index -> target
            target -> index
            else -> selected
        }
        editingChoiceIndex = when (editingChoiceIndex) {
            index -> target
            target -> index
            else -> editingChoiceIndex
        }
        hasUnsavedChanges = true
    }
    fun showDatePicker() {
        val date = reviewDate?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() } ?: LocalDate.now().plusDays(7)
        DatePickerDialog(context, { _, year, month, day -> reviewDate = LocalDate.of(year, month + 1, day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }, date.year, date.monthValue - 1, date.dayOfMonth).apply { datePicker.minDate = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }.show()
    }
    fun showDecisionDatePicker() {
        val date = Instant.ofEpochMilli(decisionDate).atZone(ZoneId.systemDefault()).toLocalDate()
        DatePickerDialog(
            context,
            { _, year, month, day ->
                decisionDate = LocalDate.of(year, month + 1, day)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                vm.clearError()
            },
            date.year,
            date.monthValue - 1,
            date.dayOfMonth,
        ).apply {
            datePicker.maxDate = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.show()
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = JournalDimens.pageHorizontal,
                    end = JournalDimens.pageHorizontal,
                    top = JournalDimens.pageVertical,
                    bottom = JournalDimens.buttonHeight + 48.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(JournalDimens.cardSpacing),
        ) {
        JournalTopBar(title = if (decisionId == null) "新的决定" else "编辑决定", onBack = ::requestBack)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("第 0${step + 1} 步", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            Text(listOf("写下问题", "比较选择", "留给未来")[step], color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        }
        LinearProgressIndicator(
            { (step + 1) / 3f },
            Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primaryContainer,
        )
        when (step) {
            0 -> {
                Text("先把问题写下来", style = MaterialTheme.typography.headlineSmall)
                Text("把问题说清楚，不急着马上找到答案。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                JournalTextField(question, { hasUnsavedChanges = true; vm.clearError(); question = it }, Modifier.fillMaxWidth(), label = { Text("我在决定什么？*") }, placeholder = { Text("例如：要不要接受那份工作？") })
                JournalTextField(contextText, { hasUnsavedChanges = true; contextText = it }, Modifier.fillMaxWidth(), label = { Text("背景（可选）") })
                SoftSurfaceCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("决定日期", style = MaterialTheme.typography.titleMedium)
                            Text("记录这次选择发生的时间", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(
                            onClick = { hasUnsavedChanges = true; showDecisionDatePicker() },
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text(Instant.ofEpochMilli(decisionDate).atZone(ZoneId.systemDefault()).toLocalDate().format(createDate))
                        }
                    }
                }
            }
            1 -> {
                Text("我会选择什么？", style = MaterialTheme.typography.headlineSmall)
                Text("列出真实存在的可能性，再看看它们各自带来什么。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                JournalTextField(choiceText, { hasUnsavedChanges = true; vm.clearError(); choiceText = it }, Modifier.fillMaxWidth(), label = { Text("候选选项*") })
                JournalTextField(choiceBenefits, { hasUnsavedChanges = true; choiceBenefits = it }, Modifier.fillMaxWidth(), label = { Text("这个选项的利好（可选）") })
                JournalTextField(choiceConcerns, { hasUnsavedChanges = true; choiceConcerns = it }, Modifier.fillMaxWidth(), label = { Text("这个选项的担忧（可选）") })
                OutlinedButton(
                    enabled = choiceText.isNotBlank(),
                    onClick = ::savePendingChoice,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                ) { Text(if (editingChoiceIndex == null) "添加这个选项" else "保存修改") }
                if (editingChoiceIndex != null) {
                    TextButton(onClick = ::cancelChoiceEdit, modifier = Modifier.align(Alignment.End)) { Text("取消编辑") }
                }
                Text("最终选择可先不确定，之后仍可继续编辑。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                choices.forEachIndexed { index, choice ->
                    val isSelected = selected == index
                    SoftSurfaceCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        hero = isSelected,
                        borderColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.52f) else null,
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(isSelected, { hasUnsavedChanges = true; selected = index })
                                if (isSelected) ChoiceSelectionRail(Modifier.padding(end = 10.dp))
                                Text(choice.text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                if (isSelected) Text("最终选择", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            Text("利好 ${choice.benefits.size} · 担忧 ${choice.concerns.size}", modifier = Modifier.padding(start = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(enabled = index > 0, onClick = { moveChoice(index, -1) }) { Text("上移") }
                                TextButton(enabled = index < choices.lastIndex, onClick = { moveChoice(index, 1) }) { Text("下移") }
                                TextButton(onClick = { editChoice(index) }) { Text("编辑") }
                                TextButton(onClick = { deleteChoice(index) }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
                if (selected != null) {
                    TextButton(
                        onClick = { hasUnsavedChanges = true; selected = null },
                        modifier = Modifier.align(Alignment.End),
                    ) { Text("暂不确定最终选择") }
                }
            }
            else -> {
                Text("写给未来的自己", style = MaterialTheme.typography.headlineSmall)
                Text("留下此刻的判断，未来再回来看看。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                NarrativeCard(modifier = Modifier.fillMaxWidth(), color = SoftSand) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        JournalTextField(futureNote, { if (it.length <= 500) { hasUnsavedChanges = true; futureNote = it } }, Modifier.fillMaxWidth().height(180.dp), label = { Text("写给未来的自己") }, placeholder = { Text("希望我能做出不后悔的选择。") })
                        Text("${futureNote.length}/500", modifier = Modifier.align(Alignment.End), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
                JournalTextField(benefitsText, { hasUnsavedChanges = true; benefitsText = it }, Modifier.fillMaxWidth(), label = { Text("我在意的事（可选，用逗号分隔）") })
                JournalTextField(concernsText, { hasUnsavedChanges = true; concernsText = it }, Modifier.fillMaxWidth(), label = { Text("我担心的事（可选，用逗号分隔）") })
                JournalTextField(expectedOutcome, { hasUnsavedChanges = true; expectedOutcome = it }, Modifier.fillMaxWidth(), label = { Text("我预期会发生什么（可选）") })
                ConfidenceSelector(
                    value = confidence.toIntOrNull(),
                    onSelect = { score ->
                        hasUnsavedChanges = true
                        confidence = if (confidence == score.toString()) "" else score.toString()
                    },
                )
                SoftSurfaceCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("未来回看", style = MaterialTheme.typography.titleMedium)
                                Text("给这段经历留一个时间点", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { hasUnsavedChanges = true; showDatePicker() }) { Text(if (reviewDate == null) "设置日期" else "修改日期") }
                        }
                        reviewDate?.let {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(createDate), style = MaterialTheme.typography.bodyMedium)
                                TextButton({ hasUnsavedChanges = true; reviewDate = null }) { Text("清除") }
                            }
                        }
                    }
                }
                Text(
                    if (reviewDate?.let { it <= System.currentTimeMillis() } == true) {
                        "今天回看会在保存后立即显示为待回看，不会发送系统通知。"
                    } else {
                        "设置未来复盘日期后，系统会询问是否允许发送提醒。拒绝权限也不影响保存。"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                SoftSurfaceCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("准备封存", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(question.ifBlank { "还没有写下问题" }, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                        Text(
                            "${choices.size} 个候选选项 · ${selected?.let { "已确定最终选择" } ?: "暂未确定最终选择"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            reviewDate?.let { "计划于 ${Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(createDate)} 回看" } ?: "尚未设置回看日期",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        vm.error?.let { JournalErrorText(it) }
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().imePadding(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = JournalDimens.pageHorizontal, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (step > 0) OutlinedButton(onClick = { step-- }, modifier = Modifier.weight(1f).height(JournalDimens.buttonHeight), shape = MaterialTheme.shapes.medium) { Text("上一步") }
                Button(
                    enabled = vm.saveState != SaveState.Saving && when (step) { 0 -> question.trim().isNotEmpty(); 1 -> choices.isNotEmpty() || choiceText.trim().isNotEmpty(); else -> true },
                    onClick = {
                        if (step == 1 && choiceText.trim().isNotEmpty()) savePendingChoice()
                        if (step < 2) step++ else save()
                    },
                    modifier = Modifier.weight(2f).height(JournalDimens.buttonHeight),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) { Text(if (vm.saveState == SaveState.Saving) "保存中…" else if (step < 2) "继续" else "封存这段判断") }
            }
        }
    }
    if (confirmExit) AlertDialog(
        onDismissRequest = { confirmExit = false },
        title = { Text("放弃未保存内容？") },
        text = { Text("离开后，刚才填写的内容不会保留。") },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { confirmExit = false; onBack() }) { Text("放弃") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmExit = false }) { Text("继续编辑") } },
    )
    if (showNotificationRationale) AlertDialog(
        onDismissRequest = { showNotificationRationale = false },
        title = { Text("要在回看日提醒你吗？") },
        text = { Text("提醒仅用于你设置的回看日期。即使不开启通知，这条决定也会照常保存。") },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                showNotificationRationale = false
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }) { Text("开启提醒") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = {
                showNotificationRationale = false
                pendingInput?.let { vm.save(it, onDone) }
                pendingInput = null
            }) { Text("仅保存") }
        },
    )
}

@Composable
private fun ConfidenceSelector(value: Int?, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("判断信心（可选）", style = MaterialTheme.typography.titleMedium)
        Text("从 1 到 5，选择此刻对判断的把握程度。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { score ->
                val selected = value == score
                Surface(
                    onClick = { onSelect(score) },
                    modifier = Modifier.weight(1f).height(48.dp).semantics {
                        role = Role.RadioButton
                        this.selected = selected
                    },
                    shape = MaterialTheme.shapes.small,
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                ) {
                    Box(contentAlignment = Alignment.Center) { Text(score.toString(), style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
}
