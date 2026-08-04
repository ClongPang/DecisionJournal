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
import com.example.decisionjournal.BuildConfig
import com.example.decisionjournal.ui.DecisionJournalApp
import com.example.decisionjournal.ui.theme.DecisionJournalTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var demoDataSeeder: DemoDataSeeder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (BuildConfig.DEBUG) {
            lifecycleScope.launch { demoDataSeeder.seedIfNeeded() }
        }
        setContent {
            DecisionJournalTheme { DecisionJournalApp() }
        }
    }
}
