package com.jacksen168.syncclipboard.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jacksen168.syncclipboard.R
import com.jacksen168.syncclipboard.presentation.screen.HomeScreen
import com.jacksen168.syncclipboard.presentation.screen.LogScreen // 添加日志页面导入
import com.jacksen168.syncclipboard.presentation.screen.SettingsScreen

/**
 * 导航路由
 */
sealed class Screen(val route: String, val titleResId: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Home : Screen("home", R.string.home, Icons.Filled.Home)
    object Logs : Screen("logs", R.string.logs, Icons.Filled.List) // 添加日志页面路由
    object Settings : Screen("settings", R.string.navigation_settings, Icons.Filled.Settings)
}

/**
 * 主导航组件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncClipboardNavigation(
    onDownloadLocationRequest: () -> Unit = {},
    onCreateLogFile: ((String) -> Unit)? = null
) {
    val navController = rememberNavController()
    
    val items = listOf(
        Screen.Home,
        Screen.Logs, // 添加日志页面到导航项
        Screen.Settings
    )
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { 
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = stringResource(screen.titleResId)
                            )
                        },
                        label = { Text(stringResource(screen.titleResId)) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                // Pop up to the start destination of the graph to
                                // avoid building up a large stack of destinations
                                // on the back stack as users select items
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                // Avoid multiple copies of the same destination when
                                // reselecting the same item
                                launchSingleTop = true
                                // Restore state when reselecting a previously selected item
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen()
            }
            composable(Screen.Logs.route) { // 添加日志页面路由
                LogScreen(
                    onCreateLogFile = onCreateLogFile
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onDownloadLocationRequest = onDownloadLocationRequest
                )
            }
        }
    }
}