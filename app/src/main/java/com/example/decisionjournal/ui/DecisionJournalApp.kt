package com.example.decisionjournal.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import android.net.Uri
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

@Composable
fun DecisionJournalApp(initialDecisionId: Long? = null) {
    val nav = rememberNavController()
    fun navigateHome() {
        nav.navigate("home") {
            popUpTo(nav.graph.startDestinationId) { inclusive = true }
            launchSingleTop = true
        }
    }
    fun navigateBackOrHome() {
        if (nav.popBackStack()) return
        navigateHome()
    }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route.orEmpty()
    val isDecisionsRoute = route.startsWith("decisions")
    val showNavigation = route == "home" || isDecisionsRoute || route == "mine"
    Scaffold(bottomBar = {
        if (showNavigation) NavigationBar(
            modifier = Modifier.border(1.dp, Hairline.copy(alpha = 0.8f)),
            containerColor = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp,
        ) {
            val navColors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                indicatorColor = MaterialTheme.colorScheme.background,
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
            }, { ArchiveNavigationIcon(Icons.Rounded.Home, route == "home") }, label = { Text("今天") }, colors = navColors, modifier = Modifier.semantics {
                contentDescription = "今天"
                selected = route == "home"
            })
            NavigationBarItem(isDecisionsRoute, {
                nav.navigate("decisions?filter=all") {
                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    // The bottom tab means “all decisions”, not “resume the last archive
                    // filter”. Restoring a saved Due entry here ignored filter=all.
                }
            }, { ArchiveNavigationIcon(Icons.Rounded.CalendarToday, isDecisionsRoute) }, label = { Text("决定") }, colors = navColors, modifier = Modifier.semantics {
                contentDescription = "决定"
                selected = isDecisionsRoute
            })
            NavigationBarItem(route == "mine", {
                nav.navigate("mine") {
                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }, { ArchiveNavigationIcon(Icons.Rounded.PersonOutline, route == "mine") }, label = { Text("我的") }, colors = navColors, modifier = Modifier.semantics {
                contentDescription = "我的"
                selected = route == "mine"
            })
        }
    }) { padding ->
        NavHost(
            navController = nav,
            startDestination = initialDecisionId?.let { "detail/$it" } ?: "home",
            modifier = Modifier.padding(padding).background(MaterialTheme.colorScheme.background),
        ) {
            composable("home") {
                HomeScreen(
                    onCreate = { nav.navigate("create") },
                    onOpen = { nav.navigate("detail/$it") },
                    onViewDue = { nav.navigate("decisions?filter=due") },
                    onReview = { nav.navigate("review/$it") },
                )
            }
            composable(
                "decisions?filter={filter}&query={query}",
                arguments = listOf(
                    navArgument("filter") { type = NavType.StringType; defaultValue = "all" },
                    navArgument("query") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                MyDecisionsScreen(
                    onOpen = { nav.navigate("detail/$it") },
                    onCreate = { nav.navigate("create") },
                    showStats = false,
                    initialDueFilter = entry.arguments?.getString("filter") == "due",
                    initialSearchQuery = entry.arguments?.getString("query").orEmpty(),
                )
            }
            composable("mine") {
                MyDecisionsScreen(
                    onOpen = { nav.navigate("detail/$it") },
                    onCreate = { nav.navigate("create") },
                    showStats = true,
                    onExploreKeyword = { keyword -> nav.navigate("decisions?filter=all&query=${Uri.encode(keyword)}") },
                )
            }
            composable("create?decisionId={decisionId}", arguments = listOf(navArgument("decisionId") { type = NavType.LongType; defaultValue = -1L })) { entry ->
                val decisionId = entry.arguments?.getLong("decisionId")?.takeIf { it > 0L }
                CreateDecisionScreen(
                    decisionId = decisionId,
                    onDone = { outcome ->
                        val sourceEntry = nav.previousBackStackEntry
                        nav.navigate("detail/${outcome.id}?reminderWarning=${outcome.reminderWarning != null}&savedMessage=${Uri.encode("决定已保存")}") {
                            if (decisionId != null && sourceEntry != null) {
                                // Editing: remove the previous detail and this editor, then
                                // land on the fresh detail without stacking a duplicate.
                                popUpTo(sourceEntry.destination.id) { inclusive = true }
                            } else {
                                // Creating from a tab (决定/我的) or home: drop only this
                                // editor so Back returns to the tab the user came from
                                // instead of resetting to home.
                                popUpTo(entry.destination.id) { inclusive = true }
                            }
                        }
                    },
                    onBack = ::navigateBackOrHome,
                    onReturnHome = ::navigateHome,
                )
            }
            composable(
                "detail/{id}?reminderWarning={reminderWarning}&savedMessage={savedMessage}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("reminderWarning") { type = NavType.BoolType; defaultValue = false },
                    navArgument("savedMessage") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: return@composable
                val reminderWarning by entry.savedStateHandle
                    .getStateFlow("reminderWarning", entry.arguments?.getBoolean("reminderWarning") ?: false)
                    .collectAsStateWithLifecycle()
                val savedMessage by entry.savedStateHandle
                    .getStateFlow("savedMessage", entry.arguments?.getString("savedMessage").orEmpty())
                    .collectAsStateWithLifecycle()
                DecisionDetailScreen(
                    id = id,
                    reminderWarning = reminderWarning,
                    savedMessage = savedMessage,
                    onSavedMessageConsumed = { entry.savedStateHandle["savedMessage"] = "" },
                    onReminderRestored = { message ->
                        entry.savedStateHandle["reminderWarning"] = false
                        entry.savedStateHandle["savedMessage"] = message
                    },
                    onReview = { nav.navigate("review/$id") },
                    onEditReview = { reviewId -> nav.navigate("review/$id?reviewId=$reviewId") },
                    onEdit = { nav.navigate("create?decisionId=$id") },
                    onBack = ::navigateBackOrHome,
                    onReturnHome = ::navigateHome,
                )
            }
            composable("review/{id}?reviewId={reviewId}", arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("reviewId") { type = NavType.LongType; defaultValue = -1L },
            )) { entry ->
                val id = entry.arguments?.getLong("id") ?: return@composable
                val reviewId = entry.arguments?.getLong("reviewId")?.takeIf { it > 0L }
                ReviewScreen(
                    id,
                    reviewId = reviewId,
                    onDone = { outcome ->
                        nav.previousBackStackEntry?.savedStateHandle?.set("reminderWarning", outcome.reminderWarning != null)
                        nav.previousBackStackEntry?.savedStateHandle?.set("savedMessage", "复盘已保存")
                        nav.popBackStack()
                    },
                    onBack = ::navigateBackOrHome,
                    onReturnHome = ::navigateHome,
                )
            }
        }
    }
}

@Composable
private fun ArchiveNavigationIcon(icon: ImageVector, selected: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp))
        Spacer(Modifier.height(4.dp))
        Spacer(
            Modifier
                .width(16.dp)
                .height(2.dp)
                .background(if (selected) Ink else Color.Transparent, RoundedCornerShape(1.dp)),
        )
    }
}
