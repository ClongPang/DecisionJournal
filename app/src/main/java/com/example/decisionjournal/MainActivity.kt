package com.example.decisionjournal

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.decisionjournal.data.DemoDataSeeder
import dagger.hilt.android.AndroidEntryPoint
import com.example.decisionjournal.ui.DecisionJournalApp
import com.example.decisionjournal.ui.theme.DecisionJournalTheme
import javax.inject.Inject
import kotlinx.coroutines.launch

const val EXTRA_REMINDER_DECISION_ID = "extra_reminder_decision_id"
const val EXTRA_DEBUG_WARMUP_DATA = "extra_debug_warmup_data"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var demoDataSeeder: DemoDataSeeder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_DEBUG_WARMUP_DATA, false)) {
            lifecycleScope.launch { demoDataSeeder.seedIfNeeded() }
        }
        val initialDecisionId = intent.getLongExtra(EXTRA_REMINDER_DECISION_ID, 0L).takeIf { it > 0L }
        setContent {
            DecisionJournalTheme { DecisionJournalApp(initialDecisionId = initialDecisionId) }
        }
    }
}
