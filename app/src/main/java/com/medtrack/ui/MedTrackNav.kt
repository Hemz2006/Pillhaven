package com.medtrack.ui

import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import com.medtrack.ui.common.PillNavItem
import com.medtrack.ui.common.PillNavigation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.medtrack.AppContainer
import com.medtrack.ui.detail.DetailScreen
import com.medtrack.ui.edit.EditMedicationScreen
import com.medtrack.ui.meds.MedicationsScreen
import com.medtrack.ui.settings.SettingsScreen
import com.medtrack.ui.setup.SetupScreen
import com.medtrack.ui.today.TodayScreen

object Routes {
    const val TODAY = "today"
    const val MEDS = "meds"
    const val SETTINGS = "settings"
    const val SETUP = "setup"
    const val EDIT = "edit/{id}"
    const val DETAIL = "detail/{id}"
    fun edit(id: Long) = "edit/$id"
    fun detail(id: Long) = "detail/$id"
}

private data class Tab(val route: String, val label: String)

private val tabs = listOf(
    Tab(Routes.TODAY, "Today"),
    Tab(Routes.MEDS, "Medications"),
    Tab(Routes.SETTINGS, "Settings"),
)

@Composable
fun MedTrackNav(c: AppContainer, startOnSetup: Boolean) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    // The island floats over the pages rather than taking a full-width strip of the screen.
    Box(Modifier.fillMaxSize()) {
        NavHost(nav, startDestination = if (startOnSetup) Routes.SETUP else Routes.TODAY, modifier = Modifier.fillMaxSize()) {
            composable(Routes.TODAY) {
                TodayScreen(
                    c,
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    onAddMedication = { nav.navigate(Routes.edit(0)) },
                    onOpenDetails = { nav.navigate(Routes.detail(it)) },
                )
            }
            composable(Routes.MEDS) {
                MedicationsScreen(c, onAdd = { nav.navigate(Routes.edit(0)) }, onOpen = { nav.navigate(Routes.detail(it)) })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(c)
            }
            composable(Routes.SETUP) {
                SetupScreen(c, onDone = {
                    c.prefs.setupDone = true
                    nav.navigate(Routes.TODAY) { popUpTo(0) { inclusive = true } }
                })
            }
            composable(Routes.EDIT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                EditMedicationScreen(c, entry.arguments!!.getLong("id"), onDone = { nav.popBackStack() })
            }
            composable(Routes.DETAIL, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments!!.getLong("id")
                DetailScreen(c, id, onBack = { nav.popBackStack() }, onEdit = { nav.navigate(Routes.edit(id)) })
            }
        }
        if (tabs.any { it.route == route }) {
            PillNavigation(
                items = tabs.map { PillNavItem(it.route, it.label) },
                selectedId = route,
                onSelect = { target ->
                    nav.navigate(target) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
