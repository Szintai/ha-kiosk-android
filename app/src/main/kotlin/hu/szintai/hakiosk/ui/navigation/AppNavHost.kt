package hu.szintai.hakiosk.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import hu.szintai.hakiosk.ui.dashboard.DashboardScreen
import hu.szintai.hakiosk.ui.settings.SettingsScreen

object Routes {
    const val DASHBOARD = "dashboard"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(onOpenSettings = { navController.navigate(Routes.SETTINGS) })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onDone = { navController.popBackStack() })
        }
    }
}
