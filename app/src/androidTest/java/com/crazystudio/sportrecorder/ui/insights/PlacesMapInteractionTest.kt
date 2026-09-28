package com.crazystudio.sportrecorder.ui.insights

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazystudio.sportrecorder.domain.insights.LocationCount
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.insights_map_close
import com.crazystudio.sportrecorder.ui.insights.map.FullScreenPlacesMap
import com.crazystudio.sportrecorder.ui.insights.map.PlacesMap
import com.crazystudio.sportrecorder.ui.theme.SportRecorderTheme
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Mirrors `LocationsCard`'s wiring: a still [PlacesMap] with [PlacesMap.onClick] opening a
 * [FullScreenPlacesMap]. Two nearby Taipei places are enough to exercise the open/close flow;
 * the map tiles come from the network and may not load on the test device, so nothing here
 * asserts on tile content — only on the card's content description and the close button.
 */
@RunWith(AndroidJUnit4::class)
class PlacesMapInteractionTest {
    @get:Rule val compose = createComposeRule()

    private val summary = "summary"

    private val locations = listOf(
        LocationCount(lat = 25.0340, lng = 121.5645, count = 2),
        LocationCount(lat = 25.0400, lng = 121.5700, count = 1),
    )

    @Composable
    private fun Host() {
        var expanded by remember { mutableStateOf(false) }
        PlacesMap(locations = locations, contentDescription = summary, onClick = { expanded = true })
        if (expanded) {
            FullScreenPlacesMap(
                locations = locations,
                contentDescription = summary,
                onDismiss = { expanded = false },
            )
        }
    }

    private fun show() {
        compose.setContent {
            SportRecorderTheme {
                Host()
            }
        }
    }

    private fun closeDescription(): String = runBlocking { getString(Res.string.insights_map_close) }

    @Test fun tappingCard_opensFullScreenMap_showingCloseButton() {
        show()
        // The card's description carries the summary plus the "tap to enlarge" hint.
        compose.onNodeWithContentDescription(summary, substring = true).assertExists().performClick()
        compose.onNodeWithContentDescription(closeDescription()).assertIsDisplayed()
    }

    @Test fun tappingClose_dismissesFullScreenMap() {
        show()
        compose.onNodeWithContentDescription(summary, substring = true).performClick()
        val close = closeDescription()
        compose.onNodeWithContentDescription(close).assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription(close).assertDoesNotExist()
    }
}
