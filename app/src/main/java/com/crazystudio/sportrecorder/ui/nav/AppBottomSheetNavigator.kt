package com.crazystudio.sportrecorder.ui.nav

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.navigation.BottomSheetNavigator
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * The one [BottomSheetNavigator] every bottom-sheet route in the app is hosted by.
 *
 * Material 2's sheet has a HalfExpanded stop it rests at whenever the content is taller than half
 * the screen. The meal editor (date, time, note, location, photo rows, confirm) is taller than that,
 * so with the library default the sheet opened at half height with the CREATE button below the
 * screen edge, and every capture needed a drag first. Skipping HalfExpanded makes tall sheets open
 * fully; short ones are unaffected. `rememberBottomSheetNavigator` has no switch for this in the
 * androidx artifact, hence the hand-built state.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun rememberAppBottomSheetNavigator(): BottomSheetNavigator {
    val sheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        skipHalfExpanded = true,
    )
    return remember(sheetState) { BottomSheetNavigator(sheetState) }
}
