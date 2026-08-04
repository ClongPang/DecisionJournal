package com.example.decisionjournal.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.decisionjournal.data.ReviewInput
import com.example.decisionjournal.data.model.ExpectationMatch
import com.example.decisionjournal.ui.ReviewViewModel
import com.example.decisionjournal.ui.components.PrimaryActionButton
import com.example.decisionjournal.ui.theme.JournalDimens
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val reviewDateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")

@Composable
fun ReviewScreen(decisionId: Long, onDone: () -> Unit, onBack: () -> Unit, vm: ReviewViewModel = hiltViewModel()) {
    var result by remember { mutableStateOf("") }
    var satisfaction by remember { mutableStateOf("") }
    var nextReviewDate by remember { mutableStateOf<Long?>(null) }
    var expectationMatch by remember { mutableStateOf<ExpectationMatch?>(null) }
    var accurateJudgment by remember { mutableStateOf("") }
    var unexpectedFinding by remember { mutableStateOf("") }
    var nextTimeNote by remember { mutableStateOf("") }
    val context = LocalContext.current

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
        ).apply { datePicker.minDate = System.currentTimeMillis() - 86_400_000L }.show()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = JournalDimens.pageHorizontal, vertical = JournalDimens.pageVertical), verticalArrangement = Arrangement.spacedBy(JournalDimens.cardSpacing)) {
        TextButton(onClick = onBack) { Text("‹ 返回") }
        Text("未来再看", style = MaterialTheme.typography.headlineSmall)
        Text("当时的决定，现在感觉如何？", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(value = result, onValueChange = { result = it }, modifier = Modifier.fillMaxWidth().height(180.dp), label = { Text("记录结果*") }, placeholder = { Text("事情后来怎么样了？") })
        OutlinedTextField(value = satisfaction, onValueChange = { satisfaction = it.filter(Char::isDigit).take(1) }, modifier = Modifier.fillMaxWidth(), label = { Text("满意度（1–5，可选）") })
        Text("结果和预期相比如何？", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ExpectationMatch.values().forEach { match ->
                TextButton(onClick = { expectationMatch = match }) {
                    Text(if (expectationMatch == match) "✓ ${match.label()}" else match.label())
                }
            }
        }
        OutlinedTextField(value = accurateJudgment, onValueChange = { accurateJudgment = it }, modifier = Modifier.fillMaxWidth(), label = { Text("我判断准确的地方（可选）") })
        OutlinedTextField(value = unexpectedFinding, onValueChange = { unexpectedFinding = it }, modifier = Modifier.fillMaxWidth(), label = { Text("我没想到的地方（可选）") })
        OutlinedTextField(value = nextTimeNote, onValueChange = { nextTimeNote = it }, modifier = Modifier.fillMaxWidth(), label = { Text("下次我会注意什么（可选）") })
        Text("还要继续回看吗？", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            TextButton(onClick = ::showDatePicker) { Text(if (nextReviewDate == null) "设置下一次复盘日期" else "修改日期") }
            nextReviewDate?.let {
                Text(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(reviewDateFormatter))
                TextButton(onClick = { nextReviewDate = null }) { Text("不再提醒") }
            }
        }
        Text("不设置日期也可以保存，之后可在详情页再次发起复盘。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.weight(1f))
        PrimaryActionButton(
            if (vm.saveState == com.example.decisionjournal.ui.SaveState.Saving) "保存中…" else "保存复盘",
            onClick = { vm.save(ReviewInput(decisionId, result, satisfaction.toIntOrNull(), nextReviewDate, expectationMatch, accurateJudgment, unexpectedFinding, nextTimeNote), onDone) },
            enabled = vm.saveState != com.example.decisionjournal.ui.SaveState.Saving && result.isNotBlank() && (satisfaction.isBlank() || satisfaction.toIntOrNull() in 1..5),
        )
    }
}

private fun ExpectationMatch.label(): String = when (this) {
    ExpectationMatch.EXPECTED -> "符合预期"
    ExpectationMatch.BETTER -> "比预期好"
    ExpectationMatch.WORSE -> "比预期差"
    ExpectationMatch.UNCLEAR -> "还不确定"
}
