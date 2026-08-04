package com.example.decisionjournal.ui.screens

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.example.decisionjournal.data.model.Decision
import com.example.decisionjournal.ui.CreateDecisionViewModel
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
fun CreateDecisionScreen(decisionId: Long?, onDone: (Long) -> Unit, onBack: () -> Unit, vm: CreateDecisionViewModel = hiltViewModel()) {
    val existing by (decisionId?.let { vm.decision(it) } ?: flowOf<Decision?>(null)).collectAsStateWithLifecycle(null)
    val existingChoices by (decisionId?.let { vm.choices(it) } ?: flowOf(emptyList())).collectAsStateWithLifecycle(emptyList())
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
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingInput?.let { vm.save(it, onDone) }
        pendingInput = null
    }

    LaunchedEffect(existing, existingChoices, decisionId) {
        if (!initialized && existing != null) {
            val decision = existing!!
            question = decision.question
            contextText = decision.context.orEmpty()
            benefitsText = decision.benefits.joinToString("，")
            concernsText = decision.concerns.joinToString("，")
            futureNote = decision.futureNote.orEmpty()
            expectedOutcome = decision.expectedOutcome.orEmpty()
            confidence = decision.confidence?.toString().orEmpty()
            choices = existingChoices.map { ChoiceInput(it.text, it.benefits, it.concerns) }
            selected = existingChoices.indexOfFirst { it.id == decision.selectedChoiceId }.takeIf { it >= 0 }
            decisionDate = decision.decisionDate
            reviewDate = decision.reviewDate
            initialized = true
        }
    }

    fun save() {
        val input = DecisionInput(decisionId ?: 0L, question, contextText, reviewDate, selected, choices, lines(benefitsText), lines(concernsText), futureNote, expectedOutcome, confidence.toIntOrNull(), decisionDate)
        val needsPermission = Build.VERSION.SDK_INT >= 33 && reviewDate != null && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingInput = input
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.save(input, onDone)
        }
    }
    fun savePendingChoice() {
        if (choiceText.trim().isEmpty()) return
        val nextChoice = ChoiceInput(choiceText.trim(), lines(choiceBenefits), lines(choiceConcerns))
        choices = editingChoiceIndex?.let { index -> choices.toMutableList().also { it[index] = nextChoice } } ?: (choices + nextChoice)
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
    fun showDatePicker() {
        val date = reviewDate?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() } ?: LocalDate.now().plusDays(7)
        DatePickerDialog(context, { _, year, month, day -> reviewDate = LocalDate.of(year, month + 1, day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }, date.year, date.monthValue - 1, date.dayOfMonth).apply { datePicker.minDate = System.currentTimeMillis() - 86_400_000L }.show()
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

    Box(Modifier.fillMaxSize()) {
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
        JournalTopBar(title = if (decisionId == null) "新的决定" else "编辑决定", onBack = onBack)
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
                JournalTextField(question, { question = it }, Modifier.fillMaxWidth(), label = { Text("我在决定什么？*") }, placeholder = { Text("例如：要不要接受那份工作？") })
                JournalTextField(contextText, { contextText = it }, Modifier.fillMaxWidth(), label = { Text("背景（可选）") })
                SoftSurfaceCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("决定日期", style = MaterialTheme.typography.titleMedium)
                            Text("记录这次选择发生的时间", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = ::showDecisionDatePicker) {
                            Text(Instant.ofEpochMilli(decisionDate).atZone(ZoneId.systemDefault()).toLocalDate().format(createDate))
                        }
                    }
                }
            }
            1 -> {
                Text("我有哪些选择？", style = MaterialTheme.typography.titleMedium)
                Text("列出真实存在的可能性，再看看它们各自带来什么。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                JournalTextField(choiceText, { choiceText = it }, Modifier.fillMaxWidth(), label = { Text("候选选项*") })
                JournalTextField(choiceBenefits, { choiceBenefits = it }, Modifier.fillMaxWidth(), label = { Text("这个选项的利好（可选）") })
                JournalTextField(choiceConcerns, { choiceConcerns = it }, Modifier.fillMaxWidth(), label = { Text("这个选项的担忧（可选）") })
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
                                RadioButton(isSelected, { selected = index })
                                Text(choice.text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                if (isSelected) Text("已选择", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            Text("利好 ${choice.benefits.size} · 担忧 ${choice.concerns.size}", modifier = Modifier.padding(start = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { editChoice(index) }) { Text("编辑") }
                                TextButton(onClick = { choices = choices.toMutableList().also { it.removeAt(index) }; selected = when { selected == index -> null; selected != null && selected!! > index -> selected!! - 1; else -> selected } }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
            else -> {
                Text("写给未来的自己", style = MaterialTheme.typography.titleMedium)
                Text("留下此刻的判断，未来再回来看看。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                JournalTextField(futureNote, { if (it.length <= 500) futureNote = it }, Modifier.fillMaxWidth().height(180.dp), label = { Text("写给未来的自己") }, placeholder = { Text("希望我能做出不后悔的选择。") })
                Text("${futureNote.length}/500", color = MaterialTheme.colorScheme.onSurfaceVariant)
                JournalTextField(benefitsText, { benefitsText = it }, Modifier.fillMaxWidth(), label = { Text("我在意的事（可选，用逗号分隔）") })
                JournalTextField(concernsText, { concernsText = it }, Modifier.fillMaxWidth(), label = { Text("我担心的事（可选，用逗号分隔）") })
                JournalTextField(expectedOutcome, { expectedOutcome = it }, Modifier.fillMaxWidth(), label = { Text("我预期会发生什么（可选）") })
                JournalTextField(confidence, { confidence = it.filter(Char::isDigit).take(1) }, Modifier.fillMaxWidth(), label = { Text("判断信心（1–5，可选）") })
                SoftSurfaceCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("未来回看", style = MaterialTheme.typography.titleMedium)
                                Text("给这段经历留一个时间点", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = ::showDatePicker) { Text(if (reviewDate == null) "设置日期" else "修改日期") }
                        }
                        reviewDate?.let {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(createDate), style = MaterialTheme.typography.bodyMedium)
                                TextButton({ reviewDate = null }) { Text("清除") }
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
                    enabled = when (step) { 0 -> question.trim().isNotEmpty(); 1 -> choices.isNotEmpty() || choiceText.trim().isNotEmpty(); else -> true },
                    onClick = {
                        if (step == 1 && choiceText.trim().isNotEmpty()) savePendingChoice()
                        if (step < 2) step++ else save()
                    },
                    modifier = Modifier.weight(2f).height(JournalDimens.buttonHeight),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) { Text(if (vm.saveState == com.example.decisionjournal.ui.SaveState.Saving) "保存中…" else if (step < 2) "继续" else "保存") }
            }
        }
    }
}
