package com.example.decisionjournal.ui.screens

import android.app.DatePickerDialog
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.example.decisionjournal.data.ReviewInput
import com.example.decisionjournal.data.model.ExpectationMatch
import com.example.decisionjournal.ui.ReviewViewModel
import com.example.decisionjournal.ui.SaveState
import com.example.decisionjournal.ui.components.JournalTopBar
import com.example.decisionjournal.ui.components.JournalErrorText
import com.example.decisionjournal.ui.components.JournalTextField
import com.example.decisionjournal.ui.components.PrimaryActionButton
import com.example.decisionjournal.ui.components.SoftSurfaceCard
import com.example.decisionjournal.ui.theme.JournalDimens
import com.example.decisionjournal.ui.theme.MistBlue
import com.example.decisionjournal.ui.theme.MistGreen
import com.example.decisionjournal.ui.theme.MistSand
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

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = JournalDimens.pageHorizontal, vertical = JournalDimens.pageVertical),
        verticalArrangement = Arrangement.spacedBy(JournalDimens.cardSpacing),
    ) {
        JournalTopBar(title = "未来再看", onBack = onBack)
        SoftSurfaceCard(modifier = Modifier.fillMaxWidth(), containerColor = MistSand, hero = true) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("回到当时的自己", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("当时的决定，现在感觉如何？", style = MaterialTheme.typography.headlineSmall)
                Text("不用给过去打分，只记录事情后来走到了哪里。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Text("后来发生了什么？", style = MaterialTheme.typography.titleMedium)
        JournalTextField(
            value = result,
            onValueChange = { result = it },
            modifier = Modifier.fillMaxWidth().height(180.dp),
            label = { Text("记录结果*") },
            placeholder = { Text("事情后来怎么样了？") },
        )

        Text("结果和预期相比如何？", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ExpectationMatch.values().forEach { match ->
                ReviewChoiceChip(
                    label = match.label(),
                    selected = expectationMatch == match,
                    onClick = { expectationMatch = match },
                    modifier = Modifier.weight(1f),
                    color = MistBlue,
                )
            }
        }

        Text("满意度", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { score ->
                ReviewChoiceChip(
                    label = "$score",
                    selected = satisfaction == score.toString(),
                    onClick = { satisfaction = score.toString() },
                    modifier = Modifier.weight(1f),
                    color = MistGreen,
                )
            }
        }
        Text("从 1 到 5，凭现在的感受选择即可。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text("留下几句观察", style = MaterialTheme.typography.titleMedium)
        JournalTextField(value = accurateJudgment, onValueChange = { accurateJudgment = it }, modifier = Modifier.fillMaxWidth(), label = { Text("我判断准确的地方（可选）") })
        JournalTextField(value = unexpectedFinding, onValueChange = { unexpectedFinding = it }, modifier = Modifier.fillMaxWidth(), label = { Text("我没想到的地方（可选）") })
        JournalTextField(value = nextTimeNote, onValueChange = { nextTimeNote = it }, modifier = Modifier.fillMaxWidth(), label = { Text("下次我会注意什么（可选）") })

        SoftSurfaceCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("还要继续回看吗？", style = MaterialTheme.typography.titleMedium)
                Text("给这段经历留一个未来的时间点。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = ::showDatePicker) { Text(if (nextReviewDate == null) "设置下一次复盘日期" else "修改日期") }
                    nextReviewDate?.let {
                        Text(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(reviewDateFormatter), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { nextReviewDate = null }) { Text("不再提醒") }
                    }
                }
            }
        }
        Text("不设置日期也可以保存，之后可在详情页再次发起复盘。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        vm.error?.let { JournalErrorText(it) }
        Spacer(Modifier.height(4.dp))
        PrimaryActionButton(
            if (vm.saveState == SaveState.Saving) "保存中…" else "保存复盘",
            onClick = { vm.save(ReviewInput(decisionId, result, satisfaction.toIntOrNull(), nextReviewDate, expectationMatch, accurateJudgment, unexpectedFinding, nextTimeNote), onDone) },
            enabled = vm.saveState != SaveState.Saving && result.isNotBlank() && (satisfaction.isBlank() || satisfaction.toIntOrNull() in 1..5),
        )
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
        modifier = modifier.height(46.dp),
        shape = MaterialTheme.shapes.small,
        color = if (selected) color else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
        contentColor = MaterialTheme.colorScheme.onSurface,
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
