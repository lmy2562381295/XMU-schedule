package com.zako.xmuschedule.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zako.xmuschedule.CrashReport
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

    // 上次运行如有未捕获崩溃或异常记录，弹出报告供复制反馈
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var crashText by remember { mutableStateOf(CrashReport.read(context)) }
    if (crashText != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("上次运行报告") },
            text = {
                SelectionContainer {
                    Text(
                        text = (crashText!!.ifBlank { "（无堆栈）" } + "\n\n--- 运行日志 ---\n" +
                            com.zako.xmuschedule.util.AppLog.read(context)).take(4000),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.height(280.dp).verticalScroll(rememberScrollState()),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { clipboard.setText(AnnotatedString(crashText ?: "")) }) { Text("复制") }
            },
            dismissButton = {
                TextButton(onClick = {
                    CrashReport.clear(context)
                    com.zako.xmuschedule.util.AppLog.clear(context)
                    crashText = null
                }) { Text("忽略") }
            },
        )
    }

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
