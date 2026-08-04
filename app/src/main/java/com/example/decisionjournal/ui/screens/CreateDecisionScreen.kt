package com.example.decisionjournal.ui.screens

import android.app.DatePickerDialog
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import com.example.decisionjournal.data.ChoiceInput
import com.example.decisionjournal.data.DecisionInput
import com.example.decisionjournal.data.model.Decision
import com.example.decisionjournal.ui.CreateDecisionViewModel
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
fun CreateDecisionScreen(decisionId: Long?, onDone: (Long) -> Unit, vm: CreateDecisionViewModel = hiltViewModel()) {
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
    var choices by remember { mutableStateOf(listOf<ChoiceInput>()) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var reviewDate by remember { mutableStateOf<Long?>(null) }
    var initialized by remember { mutableStateOf(false) }
    val context = LocalContext.current

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
            reviewDate = decision.reviewDate
            initialized = true
        }
    }

    fun save() = vm.save(DecisionInput(decisionId ?: 0L, question, contextText, reviewDate, selected, choices, lines(benefitsText), lines(concernsText), futureNote, expectedOutcome, confidence.toIntOrNull()), onDone)
    fun addPendingChoice() {
        if (choiceText.trim().isEmpty()) return
        choices = choices + ChoiceInput(choiceText.trim(), lines(choiceBenefits), lines(choiceConcerns))
        choiceText = ""
        choiceBenefits = ""
        choiceConcerns = ""
    }
    fun showDatePicker() {
        val date = reviewDate?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() } ?: LocalDate.now().plusDays(7)
        DatePickerDialog(context, { _, year, month, day -> reviewDate = LocalDate.of(year, month + 1, day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }, date.year, date.monthValue - 1, date.dayOfMonth).apply { datePicker.minDate = System.currentTimeMillis() - 86_400_000L }.show()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = JournalDimens.pageHorizontal, vertical = JournalDimens.pageVertical), verticalArrangement = Arrangement.spacedBy(JournalDimens.cardSpacing)) {
        Text(if (decisionId == null) "新的决定" else "编辑决定", style = MaterialTheme.typography.headlineSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${step + 1} / 3", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(listOf("先想清楚", "比较选择", "未来再看")[step], color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        }
        LinearProgressIndicator({ (step + 1) / 3f }, Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.primaryContainer)
        when (step) {
            0 -> {
                Text("我在决定什么？", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(question, { question = it }, Modifier.fillMaxWidth(), label = { Text("我在决定什么？*") }, placeholder = { Text("例如：要不要接受那份工作？") })
                OutlinedTextField(contextText, { contextText = it }, Modifier.fillMaxWidth(), label = { Text("背景（可选）") })
                OutlinedTextField(benefitsText, { benefitsText = it }, Modifier.fillMaxWidth(), label = { Text("我在意的事（用逗号分隔）") })
                OutlinedTextField(concernsText, { concernsText = it }, Modifier.fillMaxWidth(), label = { Text("我担心的事（用逗号分隔）") })
                OutlinedTextField(expectedOutcome, { expectedOutcome = it }, Modifier.fillMaxWidth(), label = { Text("我预期会发生什么（可选）") })
                OutlinedTextField(confidence, { confidence = it.filter(Char::isDigit).take(1) }, Modifier.fillMaxWidth(), label = { Text("判断信心（1–5，可选）") })
            }
            1 -> {
                Text("我有哪些选择？", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(choiceText, { choiceText = it }, Modifier.fillMaxWidth(), label = { Text("候选选项*") })
                OutlinedTextField(choiceBenefits, { choiceBenefits = it }, Modifier.fillMaxWidth(), label = { Text("这个选项的利好（可选）") })
                OutlinedTextField(choiceConcerns, { choiceConcerns = it }, Modifier.fillMaxWidth(), label = { Text("这个选项的担忧（可选）") })
                TextButton(enabled = choiceText.isNotBlank(), onClick = ::addPendingChoice) { Text("添加选项") }
                choices.forEachIndexed { index, choice ->
                    SoftSurfaceCard(modifier = Modifier.fillMaxWidth(), containerColor = if (selected == index) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected == index, { selected = index })
                            Column(Modifier.weight(1f)) { Text(choice.text); Text("利好 ${choice.benefits.size} · 担忧 ${choice.concerns.size}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            IconButton(onClick = { choices = choices.toMutableList().also { it.removeAt(index) }; selected = when { selected == index -> null; selected != null && selected!! > index -> selected!! - 1; else -> selected } }) { Text("×") }
                        }
                    }
                }
            }
            else -> {
                Text("写给未来的自己", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(futureNote, { if (it.length <= 500) futureNote = it }, Modifier.fillMaxWidth().height(180.dp), label = { Text("写给未来的自己") }, placeholder = { Text("希望我能做出不后悔的选择。") })
                Text("${futureNote.length}/500", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = ::showDatePicker) { Text(if (reviewDate == null) "设置复盘日期" else "修改复盘日期") }
                    reviewDate?.let { Text(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(createDate)) }
                    if (reviewDate != null) TextButton({ reviewDate = null }) { Text("清除") }
                }
            }
        }
        vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (step > 0) TextButton(onClick = { step-- }, modifier = Modifier.weight(1f)) { Text("上一步") }
            Button(
                enabled = when (step) { 0 -> question.trim().isNotEmpty(); 1 -> choices.isNotEmpty() || choiceText.trim().isNotEmpty(); else -> true },
                onClick = {
                    if (step == 1 && choiceText.trim().isNotEmpty()) addPendingChoice()
                    if (step < 2) step++ else save()
                },
                modifier = Modifier.weight(2f).height(JournalDimens.buttonHeight),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) { Text(if (step < 2) "继续" else "保存") }
        }
    }
}
