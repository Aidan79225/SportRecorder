package com.crazystudio.sportrecorder.ui.nav

import androidx.compose.material.navigation.ModalBottomSheetLayout
import androidx.compose.material.navigation.bottomSheet
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.diet_eat_create
import com.crazystudio.sportrecorder.ui.diet.editor.EatTimeEditorSheet
import com.crazystudio.sportrecorder.ui.diet.editor.EatTimeEditorUiState
import com.crazystudio.sportrecorder.ui.theme.SportRecorderTheme
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression test for the "create record" sheet opening half-expanded: the meal editor is taller
 * than half the screen, and a Material 2 sheet navigator with its default state stops at
 * HalfExpanded for such content, leaving the confirm button below the screen edge until the user
 * drags the sheet up. Hosted exactly like AppRoot (same navigator factory, same sheet layout).
 */
@RunWith(AndroidJUnit4::class)
class AppBottomSheetNavigatorTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tallEditorSheet_opensWithConfirmButtonFullyOnScreen() {
        compose.setContent {
            SportRecorderTheme {
                val navigator = rememberAppBottomSheetNavigator()
                val navController = rememberNavController(navigator)
                ModalBottomSheetLayout(navigator) {
                    NavHost(navController, startDestination = "home") {
                        composable("home") {
                            Button(onClick = { navController.navigate("editor") }) { Text("open") }
                        }
                        bottomSheet("editor") {
                            EatTimeEditorSheet(
                                state = EatTimeEditorUiState(),
                                photoModel = { null },
                                onPickDate = {}, onPickTime = {}, onNoteChange = {},
                                onAddPhoto = {}, onSelectPhoto = {},
                                onRemovePendingPhoto = {}, onRemoveExistingPhoto = {},
                                onRecaptureLocation = {}, onClearLocation = {},
                                onConfirm = {},
                            )
                        }
                    }
                }
            }
        }
        val create = runBlocking { getString(Res.string.diet_eat_create) }

        compose.onNodeWithText("open").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(create).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle() // lets the sheet's open animation settle at its resting state

        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val confirm = compose.onNodeWithText(create).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "confirm button bottom ${confirm.bottom} is below the screen bottom ${root.bottom}",
            confirm.bottom <= root.bottom,
        )
    }
}
