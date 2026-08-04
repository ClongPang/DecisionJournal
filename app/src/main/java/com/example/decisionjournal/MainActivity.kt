package com.example.decisionjournal

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import com.example.decisionjournal.ui.DecisionJournalApp

import com.example.decisionjournal.ui.theme.DecisionJournalTheme

const val EXTRA_REMINDER_DECISION_ID = "extra_reminder_decision_id"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialDecisionId = intent.getLongExtra(EXTRA_REMINDER_DECISION_ID, 0L).takeIf { it > 0L }
        setContent {
            DecisionJournalTheme { DecisionJournalApp(initialDecisionId = initialDecisionId) }
        }
    }
}
