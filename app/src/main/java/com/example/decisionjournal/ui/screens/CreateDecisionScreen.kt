package com.example.decisionjournal.ui.screens

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.example.decisionjournal.ui.components.SoftSurfaceCard
import com.example.decisionjournal.ui.theme.JournalDimens
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val createDate = DateTimeFormatter.ofPattern("yyyy年M月d日")

private fun lines(value: String): List<String> = value.split(',', '，', '\n').map(String::trim).filter(String::isNotEmpty)

@Composable
fun CreateDecisionScreen(decisionId: Long?, onDone: (SaveOutcome) -> Unit, onBack: () -> Unit, vm: CreateDecisionViewModel = hiltViewModel()) {
    val editorState by (decisionId?.let { vm.editor(it) } ?: flowOf<DecisionEditorState>(DecisionEditorState.Loading)).collectAsStateWithLifecycle(DecisionEditorState.Loading)
    val editor = (editorState as? DecisionEditorState.Content)?.data
    var step by remember { mutableStateOf(0) }
    var question by remember { mutableStateOf("") }
    var contextText by remember { mutableStateOf("") }
    var benefitsText by remember { mutableStateOf("") }
    var concernsText by remember { mutableStateOf("") }
    var futureNote by remember { mutableStateOf("") }
    var expectedOutcome by remember { mutableStateOf("") }
    var confidence by remember { mutableStateOf("") }
    var choiceText by remember { mutableStateOf("") }
    var choiceBenefits by remember { mutableStateOf("") }
    var choiceConcerns by remember { mutableStateOf("") }
    var editingChoiceIndex by remember { mutableStateOf<Int?>(null) }
    var choices by remember { mutableStateOf(listOf<ChoiceInput>()) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var decisionDate by remember { mutableStateOf(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()) }
    var reviewDate by remember { mutableStateOf<Long?>(null) }
    var initialized by remember { mutableStateOf(false) }
    var pendingInput by remember { mutableStateOf<DecisionInput?>(null) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
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
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            Text("正在加载决定…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    if (decisionId != null && editorState is DecisionEditorState.Missing) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            SoftSurfaceCard(modifier = Modifier.padding(JournalDimens.pageHorizontal)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("找不到这条决定", style = MaterialTheme.typography.titleMedium)
                    Text("它可能已被删除，无法继续编辑。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onBack) { Text("返回") }
                }
            }
        }
        return
    }

    fun save() {
        val input = DecisionInput(decisionId ?: 0L, question, contextText, reviewDate, selected, choices, lines(benefitsText), lines(concernsText), futureNote, expectedOutcome, confidence.toIntOrNull(), decisionDate)
        if (!vm.validateBeforePermissionRequest(input)) return
        val needsPermission = Build.VERSION.SDK_INT >= 33 && reviewDate != null && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingInput = input
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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
            },
            date.year,
            date.monthValue - 1,
            date.dayOfMonth,
        ).show()
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
            Text("${step + 1} / 3", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(listOf("先想清楚", "比较选择", "未来再看")[step], color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        }
        LinearProgressIndicator(
            { (step + 1) / 3f },
            Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primaryContainer,
        )
        when (step) {
            0 -> {
                Text("我在决定什么？", style = MaterialTheme.typography.titleMedium)
                Text("把问题说清楚，不急着马上找到答案。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                JournalTextField(question, { hasUnsavedChanges = true; question = it }, Modifier.fillMaxWidth(), label = { Text("我在决定什么？*") }, placeholder = { Text("例如：要不要接受那份工作？") })
                JournalTextField(contextText, { hasUnsavedChanges = true; contextText = it }, Modifier.fillMaxWidth(), label = { Text("背景（可选）") })
                SoftSurfaceCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("决定日期", style = MaterialTheme.typography.titleMedium)
                            Text("记录这次选择发生的时间", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                            TextButton(onClick = { hasUnsavedChanges = true; showDecisionDatePicker() }) {
                            Text(Instant.ofEpochMilli(decisionDate).atZone(ZoneId.systemDefault()).toLocalDate().format(createDate))
                        }
                    }
                }
            }
            1 -> {
                Text("我有哪些选择？", style = MaterialTheme.typography.titleMedium)
                Text("列出真实存在的可能性，再看看它们各自带来什么。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                JournalTextField(choiceText, { hasUnsavedChanges = true; choiceText = it }, Modifier.fillMaxWidth(), label = { Text("候选选项*") })
                JournalTextField(choiceBenefits, { hasUnsavedChanges = true; choiceBenefits = it }, Modifier.fillMaxWidth(), label = { Text("这个选项的利好（可选）") })
                JournalTextField(choiceConcerns, { hasUnsavedChanges = true; choiceConcerns = it }, Modifier.fillMaxWidth(), label = { Text("这个选项的担忧（可选）") })
                OutlinedButton(
                    enabled = choiceText.isNotBlank(),
                    onClick = ::savePendingChoice,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                ) { Text(if (editingChoiceIndex == null) "添加这个选项" else "保存修改") }
                choices.forEachIndexed { index, choice ->
                    val isSelected = selected == index
                    SoftSurfaceCard(modifier = Modifier.fillMaxWidth(), containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, hero = isSelected) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(isSelected, { hasUnsavedChanges = true; selected = index })
                                Text(choice.text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                if (isSelected) Text("已选择", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
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
            }
            else -> {
                Text("写给未来的自己", style = MaterialTheme.typography.titleMedium)
                Text("留下此刻的判断，未来再回来看看。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                JournalTextField(futureNote, { if (it.length <= 500) { hasUnsavedChanges = true; futureNote = it } }, Modifier.fillMaxWidth().height(180.dp), label = { Text("写给未来的自己") }, placeholder = { Text("希望我能做出不后悔的选择。") })
                Text("${futureNote.length}/500", color = MaterialTheme.colorScheme.onSurfaceVariant)
                JournalTextField(benefitsText, { hasUnsavedChanges = true; benefitsText = it }, Modifier.fillMaxWidth(), label = { Text("我在意的事（可选，用逗号分隔）") })
                JournalTextField(concernsText, { hasUnsavedChanges = true; concernsText = it }, Modifier.fillMaxWidth(), label = { Text("我担心的事（可选，用逗号分隔）") })
                JournalTextField(expectedOutcome, { hasUnsavedChanges = true; expectedOutcome = it }, Modifier.fillMaxWidth(), label = { Text("我预期会发生什么（可选）") })
                JournalTextField(confidence, { hasUnsavedChanges = true; confidence = it.filter(Char::isDigit).take(1) }, Modifier.fillMaxWidth(), label = { Text("判断信心（1–5，可选）") })
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
                Text("设置复盘日期后，系统会询问是否允许发送提醒。拒绝权限也不影响保存。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        vm.error?.let { JournalErrorText(it) }
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
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
                ) { Text(if (vm.saveState == SaveState.Saving) "保存中…" else if (step < 2) "继续" else "保存") }
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
}
