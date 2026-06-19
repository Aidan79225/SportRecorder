package com.crazystudio.sportrecorder.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.navigation.ModalBottomSheetLayout
import androidx.compose.material.navigation.bottomSheet
import androidx.compose.material.navigation.rememberBottomSheetNavigator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.ui.diet.DietScreen
import com.crazystudio.sportrecorder.ui.diet.DietViewModel
import com.crazystudio.sportrecorder.ui.diet.create.fasting.CreateFastingTypeScreen
import com.crazystudio.sportrecorder.ui.diet.create.fasting.CreateFastingTypeViewModel
import com.crazystudio.sportrecorder.ui.diet.editor.EatTimeEditorSheet
import com.crazystudio.sportrecorder.ui.diet.editor.EatTimeEditorViewModel
import com.crazystudio.sportrecorder.ui.diet.record.DietRecordViewModel
import com.crazystudio.sportrecorder.ui.diet.record.RecordScreen
import com.crazystudio.sportrecorder.ui.diet.select.SelectFastingTypeScreen
import com.crazystudio.sportrecorder.ui.diet.select.SelectFastingTypeViewModel
import com.crazystudio.sportrecorder.ui.insights.InsightsScreen
import com.crazystudio.sportrecorder.ui.insights.InsightsViewModel
import com.crazystudio.sportrecorder.ui.nav.Route
import com.crazystudio.sportrecorder.ui.settings.SettingsRoute
import com.crazystudio.sportrecorder.util.PhotoStorage
import kotlinx.coroutines.launch

private data class Tab(val route: Route, val label: String, @DrawableRes val icon: Int)

@Composable
@Suppress("LongMethod") // cohesive single navigation-graph builder; splitting hurts readability
fun AppRoot() {
    val bottomSheetNavigator = rememberBottomSheetNavigator()
    val navController = rememberNavController(bottomSheetNavigator)

    val tabs = listOf(
        Tab(Route.Diet, "Home", R.drawable.ic_home_black_24dp),
        Tab(Route.Record, "Record", R.drawable.ic_dashboard_black_24dp),
        Tab(Route.Insights, "Insights", R.drawable.ic_baseline_insights_24),
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
                                Route.Insights -> dest.hasRoute(Route.Insights::class)
                                else -> false
                            }
                        } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                // popUpTo(Diet) drops anything above Diet (incl. the Settings
                                // sub-screen) before switching tabs. No saveState/restoreState:
                                // that pair is for nested tab back stacks and would otherwise
                                // re-restore Settings, trapping the user on it.
                                navController.navigate(tab.route) {
                                    popUpTo(Route.Diet)
                                    launchSingleTop = true
                                }
                            },
                            icon = { Icon(painterResource(tab.icon), contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
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
                composable<Route.Diet> {
                    val vm: DietViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    DietScreen(
                        state = state,
                        onEditFastingType = { navController.navigate(Route.SelectFastingType) },
                        onAddEatTime = { navController.navigate(Route.EatTimeEditor()) },
                        onOpenSettings = { navController.navigate(Route.Settings) },
                    )
                }
                composable<Route.Settings> {
                    SettingsRoute(onBack = { navController.popBackStack() })
                }
                composable<Route.Record> {
                    val vm: DietRecordViewModel = hiltViewModel()
                    val records by vm.records.collectAsStateWithLifecycle()
                    RecordScreen(
                        records = records,
                        onDelete = vm::deleteRecord,
                        onEditRecord = { id -> navController.navigate(Route.EatTimeEditor(eatTimeId = id)) },
                    )
                }
                composable<Route.Insights> {
                    val vm: InsightsViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    InsightsScreen(
                        state = state,
                        onSelectPeriod = vm::setPeriod,
                        onShiftMonth = vm::shiftMonth,
                    )
                }

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
                        onConfirmRequest = { name, fastingTime, eatingTime ->
                            val fastingHours = fastingTime.toLong()
                            val eatingHours = eatingTime.toLong()
                            scope.launch {
                                vm.createCustomFastingType(fastingHours, eatingHours, name)
                                navController.popBackStack()
                            }
                        },
                    )
                }
                bottomSheet<Route.EatTimeEditor> {
                    val vm: EatTimeEditorViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    val context = LocalContext.current
                    val scope = rememberCoroutineScope()

                    // Hold the pending capture file across the camera launch.
                    var captureFile by remember { mutableStateOf<java.io.File?>(null) }
                    val cameraLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.TakePicture()
                    ) { success ->
                        val file = captureFile
                        if (success && file != null) {
                            vm.addCapturedPhoto(file)
                        } else {
                            file?.delete()
                        }
                        captureFile = null
                    }
                    val photoPickerLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.PickVisualMedia()
                    ) { uri ->
                        if (uri != null) vm.addPickedPhoto(uri)
                    }
                    val locationPermLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions()
                    ) { result ->
                        if (result.values.any { it }) vm.requestLocation() else vm.locationDenied()
                    }
                    LaunchedEffect(Unit) {
                        if (!state.isEditMode) {
                            locationPermLauncher.launch(
                                arrayOf(
                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION,
                                )
                            )
                        }
                    }

                    EatTimeEditorSheet(
                        state = state,
                        onPickDate = {
                            val cal = vm.currentCalendar
                            DatePickerDialog(
                                context,
                                R.style.DialogStyle,
                                { _, year, month, dayOfMonth ->
                                    vm.updateDate(year, month, dayOfMonth)
                                },
                                cal.get(java.util.Calendar.YEAR),
                                cal.get(java.util.Calendar.MONTH),
                                cal.get(java.util.Calendar.DAY_OF_MONTH),
                            ).show()
                        },
                        onPickTime = {
                            val cal = vm.currentCalendar
                            TimePickerDialog(
                                context,
                                R.style.TimeDialogStyle,
                                { _, hourOfDay, minute -> vm.updateTime(hourOfDay, minute) },
                                cal.get(java.util.Calendar.HOUR_OF_DAY),
                                cal.get(java.util.Calendar.MINUTE),
                                true,
                            ).apply {
                                window?.decorView?.setBackgroundColor(
                                    ContextCompat.getColor(context, R.color.bg_black)
                                )
                            }.show()
                        },
                        onNoteChange = vm::setNote,
                        onAddPhoto = {
                            val (file, uri) = PhotoStorage.newCaptureTarget(context)
                            captureFile = file
                            cameraLauncher.launch(uri)
                        },
                        onSelectPhoto = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onRemovePendingPhoto = vm::removePendingPhoto,
                        onRemoveExistingPhoto = vm::removeExistingPhoto,
                        onRecaptureLocation = {
                            locationPermLauncher.launch(
                                arrayOf(
                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION,
                                )
                            )
                        },
                        onClearLocation = vm::clearLocation,
                        onConfirm = {
                            scope.launch { if (vm.save()) navController.popBackStack() }
                        },
                    )
                }
            }
        }
    }
}
