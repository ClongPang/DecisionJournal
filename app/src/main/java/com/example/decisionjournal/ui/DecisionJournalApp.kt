package com.example.decisionjournal.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.decisionjournal.ui.screens.*
import com.example.decisionjournal.ui.theme.Ink
import com.example.decisionjournal.ui.theme.Hairline
import com.example.decisionjournal.ui.theme.MistGreen

@Composable
fun DecisionJournalApp(initialDecisionId: Long? = null) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route.orEmpty()
    val showNavigation = route in setOf("home", "decisions", "mine")
    Scaffold(bottomBar = {
        if (showNavigation) NavigationBar(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, Hairline.copy(alpha = 0.75f), RoundedCornerShape(24.dp)),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            val navColors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                indicatorColor = MistGreen,
                selectedIconColor = Ink,
                selectedTextColor = Ink,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NavigationBarItem(route == "home", {
                nav.navigate("home") {
                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }, { Icon(Icons.Rounded.Home, null, Modifier.size(22.dp)) }, label = { Text("今天") }, colors = navColors)
            NavigationBarItem(route == "decisions", {
                nav.navigate("decisions") {
                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }, { Icon(Icons.Rounded.CalendarToday, null, Modifier.size(22.dp)) }, label = { Text("决定") }, colors = navColors)
            NavigationBarItem(route == "mine", {
                nav.navigate("mine") {
                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }, { Icon(Icons.Rounded.PersonOutline, null, Modifier.size(22.dp)) }, label = { Text("我的") }, colors = navColors)
        }
    }) { padding ->
        NavHost(
            navController = nav,
            startDestination = initialDecisionId?.let { "detail/$it" } ?: "home",
            modifier = Modifier.padding(padding).background(MaterialTheme.colorScheme.background),
        ) {
            composable("home") { HomeScreen({ nav.navigate("create") }, { nav.navigate("detail/$it") }) }
            composable("decisions") { MyDecisionsScreen(onOpen = { nav.navigate("detail/$it") }, onCreate = { nav.navigate("create") }, showStats = false) }
            composable("mine") { MyDecisionsScreen(onOpen = { nav.navigate("detail/$it") }, onCreate = { nav.navigate("create") }, showStats = true) }
            composable("create?decisionId={decisionId}", arguments = listOf(navArgument("decisionId") { type = NavType.LongType; defaultValue = -1L })) { entry ->
                val decisionId = entry.arguments?.getLong("decisionId")?.takeIf { it > 0L }
                CreateDecisionScreen(
                    decisionId = decisionId,
                    onDone = { outcome ->
                        nav.navigate("detail/${outcome.id}?reminderWarning=${outcome.reminderWarning != null}") { popUpTo("home") }
                    },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(
                "detail/{id}?reminderWarning={reminderWarning}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("reminderWarning") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: return@composable
                val reminderWarning by entry.savedStateHandle
                    .getStateFlow("reminderWarning", entry.arguments?.getBoolean("reminderWarning") ?: false)
                    .collectAsStateWithLifecycle()
                DecisionDetailScreen(
                    id = id,
                    reminderWarning = reminderWarning,
                    onReview = { nav.navigate("review/$id") },
                    onEdit = { nav.navigate("create?decisionId=$id") },
                    onBack = { nav.popBackStack() },
                )
            }
            composable("review/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("id") ?: return@composable
                ReviewScreen(
                    id,
                    onDone = { outcome ->
                        nav.previousBackStackEntry?.savedStateHandle?.set("reminderWarning", outcome.reminderWarning != null)
                        nav.popBackStack()
                    },
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}
