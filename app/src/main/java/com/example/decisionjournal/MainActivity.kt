package com.example.decisionjournal

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.example.decisionjournal.data.DemoDataSeeder
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import com.example.decisionjournal.BuildConfig
import com.example.decisionjournal.ui.DecisionJournalApp

import com.example.decisionjournal.ui.theme.DecisionJournalTheme

const val EXTRA_REMINDER_DECISION_ID = "extra_reminder_decision_id"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var demoDataSeeder: DemoDataSeeder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (BuildConfig.DEBUG) {
            lifecycleScope.launch {
                try {
                    demoDataSeeder.seedIfNeeded()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Debug-only warmup data must never prevent the app from opening.
                }
            }
        }
        val initialDecisionId = intent.getLongExtra(EXTRA_REMINDER_DECISION_ID, 0L).takeIf { it > 0L }
        setContent {
            DecisionJournalTheme { DecisionJournalApp(initialDecisionId = initialDecisionId) }
        }
    }
}
