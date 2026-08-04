package com.example.decisionjournal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.lifecycle.lifecycleScope
import com.example.decisionjournal.data.DemoDataSeeder
import kotlinx.coroutines.launch
import android.Manifest
import android.os.Build
import com.example.decisionjournal.ui.DecisionJournalApp
import com.example.decisionjournal.ui.theme.DecisionJournalTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var demoDataSeeder: DemoDataSeeder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            lifecycleScope.launch { demoDataSeeder.seedIfNeeded() }
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        setContent {
            DecisionJournalTheme { DecisionJournalApp() }
        }
    }
}
