package com.crazystudio.sportrecorder.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.navigation.ModalBottomSheetLayout
import androidx.compose.material.navigation.bottomSheet
import androidx.compose.material.navigation.rememberBottomSheetNavigator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.ui.diet.create.fasting.CreateFastingTypeScreen
import com.crazystudio.sportrecorder.ui.diet.create.fasting.CreateFastingTypeViewModel
import com.crazystudio.sportrecorder.ui.diet.select.SelectFastingTypeScreen
import com.crazystudio.sportrecorder.ui.diet.select.SelectFastingTypeViewModel
import com.crazystudio.sportrecorder.ui.nav.Route
import com.crazystudio.sportrecorder.ui.theme.bg_black2
import com.crazystudio.sportrecorder.ui.theme.grey_1
import com.crazystudio.sportrecorder.ui.theme.light_green
import kotlinx.coroutines.launch

private data class Tab(val route: Route, val label: String, @DrawableRes val icon: Int)

@Composable
fun AppRoot() {
    val bottomSheetNavigator = rememberBottomSheetNavigator()
    val navController = rememberNavController(bottomSheetNavigator)

    val tabs = listOf(
        Tab(Route.Diet, "Home", R.drawable.ic_home_black_24dp),
        Tab(Route.Record, "Record", R.drawable.ic_dashboard_black_24dp),
        Tab(Route.Notifications, "Notifications", R.drawable.ic_notifications_black_24dp),
    )

    ModalBottomSheetLayout(bottomSheetNavigator) {
        Scaffold(
            bottomBar = {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDest = backStackEntry?.destination
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = currentDest?.hierarchy?.any { dest ->
                            when (tab.route) {
                                Route.Diet -> dest.hasRoute(Route.Diet::class)
                                Route.Record -> dest.hasRoute(Route.Record::class)
                                Route.Notifications -> dest.hasRoute(Route.Notifications::class)
                                else -> false
                            }
                        } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(Route.Diet) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(painterResource(tab.icon), contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = light_green,
                                selectedTextColor = light_green,
                                unselectedIconColor = grey_1,
                                unselectedTextColor = grey_1,
                                indicatorColor = bg_black2,
                            ),
                        )
                    }
                }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Route.Diet,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                composable<Route.Diet> { Text("Diet (placeholder)") }
                composable<Route.Record> { Text("Record (placeholder)") }
                composable<Route.Notifications> { Text("Notifications (placeholder)") }

                bottomSheet<Route.SelectFastingType> {
                    val vm: SelectFastingTypeViewModel = hiltViewModel()
                    val items by vm.fastingItemFlow.collectAsStateWithLifecycle(emptyList())
                    SelectFastingTypeScreen(
                        items,
                        { navController.navigate(Route.CreateFastingType) },
                        { fastingHours, eatingHours ->
                            vm.saveSelection(fastingHours, eatingHours)
                            navController.popBackStack()
                        },
                    )
                }
                bottomSheet<Route.CreateFastingType> {
                    val vm: CreateFastingTypeViewModel = hiltViewModel()
                    val scope = rememberCoroutineScope()
                    CreateFastingTypeScreen(
                        onDismissRequest = { navController.popBackStack() },
                        onConfirmRequest = { fastingTime, eatingTime ->
                            val fastingHours = fastingTime.toLong()
                            val eatingHours = eatingTime.toLong()
                            scope.launch {
                                vm.createCustomFastingType(fastingHours, eatingHours)
                                navController.popBackStack()
                            }
                        },
                    )
                }
                bottomSheet<Route.CreateEatTime> { Text("CreateEatTime (placeholder)") }
                bottomSheet<Route.CreateFoodRecord> { Text("CreateFoodRecord (placeholder)") }
            }
        }
    }
}
