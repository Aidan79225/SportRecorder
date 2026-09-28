package com.crazystudio.sportrecorder.ui.nav

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.diet_eat_create
import com.crazystudio.sportrecorder.ui.diet.editor.EatTimeEditorSheet
import com.crazystudio.sportrecorder.ui.diet.editor.EatTimeEditorUiState
import com.crazystudio.sportrecorder.ui.theme.SportRecorderTheme
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.getString
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@Serializable private object Home

@Serializable private object Editor

@Serializable private object Second

/**
 * [BottomSheetHost] hosting the real meal editor the way `AppRoot` does. Each sheet is its own
 * window, so the test rule sees several compose roots; queries by text search all of them.
 */
@RunWith(AndroidJUnit4::class)
class BottomSheetHostTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val navigator = BottomSheetNavigator()

    private fun setUpGraph() {
        compose.setContent {
            SportRecorderTheme {
                val navController = rememberNavController(navigator)
                NavHost(navController, startDestination = Home) {
                    composable<Home> {
                        Button(onClick = { navController.navigate(Editor) }) { Text("open") }
                    }
                    bottomSheet<Editor> {
                        Column {
                            Button(onClick = { navController.navigate(Second) }) { Text("stack") }
                            Button(onClick = { navController.popBackStack() }) { Text("pop") }
                            // Plain remember: survives only while the sheet stays composed.
                            var taps by remember { mutableIntStateOf(0) }
                            Button(onClick = { taps++ }) { Text("taps $taps") }
                            EatTimeEditorSheet(
                                state = EatTimeEditorUiState(), photoModel = { null },
                                onPickDate = {}, onPickTime = {}, onNoteChange = {}, onAddPhoto = {}, onSelectPhoto = {},
                                onRemovePendingPhoto = {}, onRemoveExistingPhoto = {}, onRecaptureLocation = {},
                                onClearLocation = {}, onConfirm = {},
                            )
                        }
                    }
                    bottomSheet<Second> { Text("second sheet") }
                }
                BottomSheetHost(navigator)
            }
        }
    }

    private fun createLabel() = runBlocking { getString(Res.string.diet_eat_create) }

    private fun sheetShown() = compose.onAllNodesWithText(createLabel()).fetchSemanticsNodes().isNotEmpty()

    /** The sheet is gone and its entry released: nothing left on the stack or stuck mid-transition. */
    private fun assertSheetClosedAndReleased() {
        compose.waitUntil(5_000) { !sheetShown() && navigator.transitionsInProgress.value.isEmpty() }
        assertTrue(navigator.backStack.value.isEmpty())
        compose.onNodeWithText("open").assertIsDisplayed()
    }

    private fun openEditor() {
        compose.onNodeWithText("open").performClick()
        compose.waitUntil(5_000) { sheetShown() }
        compose.waitForIdle()
    }

    @Test fun tallSheet_opensWithConfirmButtonOnScreen() {
        setUpGraph(); openEditor()
        val confirm = compose.onNodeWithText(createLabel()).fetchSemanticsNode()
        // The sheet's own window root: the M3 sheet window spans the screen.
        val rootHeight = confirm.layoutInfo.coordinates.findRootCoordinates().size.height
        val bottom = confirm.boundsInRoot.bottom
        assertTrue("confirm bottom $bottom > sheet window bottom $rootHeight", bottom <= rootHeight)
    }

    @Test fun popBackStackFromContent_closesTheSheet() {
        setUpGraph(); openEditor()
        compose.onNodeWithText("pop").performClick()
        assertSheetClosedAndReleased()
    }

    @Test fun systemBack_dismissesAndPops() {
        setUpGraph(); openEditor()
        Espresso.pressBack()
        assertSheetClosedAndReleased()
    }

    @Test fun sheetOverSheet_showsTheSecond() {
        setUpGraph(); openEditor()
        compose.onNodeWithText("stack").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("second sheet").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("second sheet").assertIsDisplayed()
    }

    @Test fun popBackStackFromContent_slidesTheSheetOutBeforeRemovingIt() {
        setUpGraph(); openEditor()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("pop").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        assertTrue("sheet vanished instead of sliding out", sheetShown())
        compose.mainClock.autoAdvance = true
        assertSheetClosedAndReleased()
    }

    @Test fun activityStopAndStart_keepsTheSheetComposed() {
        setUpGraph(); openEditor()
        compose.onNodeWithText("taps 0").performClick()
        compose.onNodeWithText("taps 1").assertIsDisplayed()
        // What launching the camera or photo picker from the editor does to the host activity.
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        compose.onNodeWithText("taps 1").assertIsDisplayed()
    }
}
