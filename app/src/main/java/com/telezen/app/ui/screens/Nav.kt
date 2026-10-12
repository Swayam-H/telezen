package com.telezen.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.telezen.app.ui.components.LocalSnack

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("home", "Home", Icons.Outlined.Home),
    Tab("apps", "Apps", Icons.Outlined.Apps),
    Tab("schedules", "Schedules", Icons.Outlined.Schedule),
    Tab("insights", "Insights", Icons.Outlined.BarChart),
    Tab("settings", "Settings", Icons.Outlined.Settings),
)

@Composable
fun MainNav(startRoute: String?) {
    val nav = rememberNavController()
    val snack = remember { SnackbarHostState() }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = TABS.any { it.route == route }

    LaunchedEffect(startRoute) {
        if (startRoute != null && TABS.any { it.route == startRoute }) nav.navigateTab(startRoute)
    }

    CompositionLocalProvider(LocalSnack provides snack) {
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(snack) },
            bottomBar = {
                if (showBar) {
                    NavigationBar {
                        TABS.forEach { tab ->
                            NavigationBarItem(
                                selected = route == tab.route,
                                onClick = { nav.navigateTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                NavHost(nav, startDestination = "home") {
                    composable("home") { HomeScreen(nav) }
                    composable("apps") { AppsScreen(nav) }
                    composable("pick_app") { AppPickerScreen(nav) }
                    composable("app/{pkg}") { AppEditScreen(nav, it.arguments?.getString("pkg").orEmpty()) }
                    composable("schedules") { SchedulesScreen(nav) }
                    composable("schedule/{id}") { ScheduleEditScreen(nav, it.arguments?.getString("id").orEmpty()) }
                    composable("insights") { InsightsScreen() }
                    composable("settings") { SettingsScreen(nav) }
                    composable("prompts") { PromptsScreen(nav) }
                    composable("rules") { RulesScreen(nav) }
                    composable("reliability") { ReliabilityScreen(nav) }
                }
            }
        }
    }
}

fun NavHostController.navigateTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
