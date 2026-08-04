package com.example.decisionjournal.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.decisionjournal.ui.screens.*
import com.example.decisionjournal.ui.theme.Ink
import com.example.decisionjournal.ui.theme.MistGreen

@Composable
fun DecisionJournalApp() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route.orEmpty()
    val showNavigation = route in setOf("home", "decisions", "mine")
    Scaffold(bottomBar = {
        if (showNavigation) NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
            val navColors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                indicatorColor = MistGreen,
                selectedIconColor = Ink,
                selectedTextColor = Ink,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NavigationBarItem(route == "home", { nav.navigate("home") { launchSingleTop = true } }, { Icon(Icons.Rounded.Home, null, Modifier.size(22.dp)) }, label = { Text("今天") }, colors = navColors)
            NavigationBarItem(route == "decisions", { nav.navigate("decisions") { launchSingleTop = true } }, { Icon(Icons.Rounded.CalendarToday, null, Modifier.size(22.dp)) }, label = { Text("决定") }, colors = navColors)
            NavigationBarItem(route == "mine", { nav.navigate("mine") { launchSingleTop = true } }, { Icon(Icons.Rounded.PersonOutline, null, Modifier.size(22.dp)) }, label = { Text("我的") }, colors = navColors)
        }
    }) { padding ->
        NavHost(navController = nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { HomeScreen({ nav.navigate("create") }, { nav.navigate("detail/$it") }) }
            composable("decisions") { MyDecisionsScreen(onOpen = { nav.navigate("detail/$it") }, onCreate = { nav.navigate("create") }, showStats = false) }
            composable("mine") { MyDecisionsScreen(onOpen = { nav.navigate("detail/$it") }, onCreate = { nav.navigate("create") }, showStats = true) }
            composable("create?decisionId={decisionId}", arguments = listOf(navArgument("decisionId") { type = NavType.LongType; defaultValue = -1L })) { entry ->
                val decisionId = entry.arguments?.getLong("decisionId")?.takeIf { it > 0L }
                CreateDecisionScreen(decisionId = decisionId, onDone = { nav.navigate("detail/$it") { popUpTo("home") } })
            }
            composable("detail/{id}") { entry ->
                val id = entry.arguments?.getString("id")!!.toLong()
                DecisionDetailScreen(id, { nav.navigate("review/$id") }, { nav.navigate("create?decisionId=$id") }, { nav.popBackStack() })
            }
            composable("review/{id}") { entry -> ReviewScreen(entry.arguments?.getString("id")!!.toLong(), onDone = { nav.popBackStack() }) }
        }
    }
}
