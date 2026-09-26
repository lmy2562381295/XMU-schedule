package com.zako.xmuschedule.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zako.xmuschedule.ui.importing.ImportScreen
import com.zako.xmuschedule.ui.settings.SettingsScreen
import com.zako.xmuschedule.ui.today.TodayScreen
import com.zako.xmuschedule.ui.week.WeekScreen

object Routes {
    const val TODAY = "today"
    const val WEEK = "week"
    const val SETTINGS = "settings"
    const val IMPORT = "import"
}

private data class BottomItem(val route: String, val label: String, val icon: ImageVector)

private val bottomItems = listOf(
    BottomItem(Routes.TODAY, "今日", Icons.Filled.Today),
    BottomItem(Routes.WEEK, "周课表", Icons.Filled.CalendarMonth),
    BottomItem(Routes.SETTINGS, "设置", Icons.Filled.Settings),
)

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.TODAY

    Scaffold(
        bottomBar = {
            if (currentRoute != Routes.IMPORT) {
                NavigationBar {
                    bottomItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.TODAY,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.TODAY) {
                TodayScreen(
                    onGoImport = { navController.navigate(Routes.IMPORT) },
                    onGoSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.WEEK) {
                WeekScreen(onGoImport = { navController.navigate(Routes.IMPORT) })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onGoImport = { navController.navigate(Routes.IMPORT) })
            }
            composable(Routes.IMPORT) {
                ImportScreen(onClose = { navController.popBackStack() })
            }
        }
    }
}
