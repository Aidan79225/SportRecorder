package com.crazystudio.sportrecorder

import android.Manifest
import android.content.Intent
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.diet_eat_create
import com.crazystudio.sportrecorder.shared.resources.editor_not_map_link
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * MainActivity as a share target (#84). Shares plain text ("hello"), never a Maps link, so no test
 * here touches the network: the editor reports "not a Google Maps link" and stays usable.
 */
@RunWith(AndroidJUnit4::class)
class ShareEntryTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun grantLocation() {
        // A new meal asks for location on open; granted up front, no system dialog covers the editor.
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_FINE_LOCATION)
        automation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    private fun str(res: StringResource) = runBlocking { getString(res) }

    private fun shareIntent(text: CharSequence? = null) = Intent(Intent.ACTION_SEND)
        .setClass(context, MainActivity::class.java)
        .setType("text/plain")
        .apply { if (text != null) putExtra(Intent.EXTRA_TEXT, text) }

    private fun waitForText(text: String) =
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    /** The home screen's add-a-meal FAB: the only clickable there with neither text nor a description. */
    private val addMealButton = hasClickAction() and
        SemanticsMatcher("has no text") { SemanticsProperties.Text !in it.config } and
        SemanticsMatcher("has no description") { SemanticsProperties.ContentDescription !in it.config }

    @Test fun aShareOpensTheEditor() {
        ActivityScenario.launch<MainActivity>(shareIntent("hello")).use {
            waitForText(str(Res.string.diet_eat_create))
            waitForText(str(Res.string.editor_not_map_link))
        }
    }

    @Test fun aShareFinishesAfterSaving() {
        ActivityScenario.launch<MainActivity>(shareIntent("hello")).use { scenario ->
            val create = str(Res.string.diet_eat_create)
            waitForText(create)
            compose.onNodeWithText(create).performClick()
            // Back to the app the user shared from: this activity was only the share's host. Waited
            // through compose, whose clock drives the sheet's exit until the editor leaves the stack.
            compose.waitUntil(TIMEOUT_MS) { scenario.state == Lifecycle.State.DESTROYED }
        }
    }

    @Test fun aMultiLineShareWithPercentSurvivesTheRoute() {
        // Newlines and a bare % travel through the route's encoding without breaking it.
        ActivityScenario.launch<MainActivity>(shareIntent("鼎泰豐 信義店\n100% 好吃\nhello")).use {
            waitForText(str(Res.string.diet_eat_create))
            waitForText(str(Res.string.editor_not_map_link))
        }
    }

    @Test fun aStyledShareOpensTheEditor() {
        // A styled share arrives as a Spanned, which getStringExtra would read as null.
        val styled = SpannableString("hello").apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
        }
        ActivityScenario.launch<MainActivity>(shareIntent(styled)).use {
            waitForText(str(Res.string.diet_eat_create))
            waitForText(str(Res.string.editor_not_map_link))
        }
    }

    @Test fun aShareWithoutTextOpensTheEditor() {
        ActivityScenario.launch<MainActivity>(shareIntent()).use {
            waitForText(str(Res.string.diet_eat_create))
            waitForText(str(Res.string.editor_not_map_link))
        }
    }

    @Test fun aShareReopenedFromRecents_isANormalLaunch() {
        val fromRecents = shareIntent("hello").addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        ActivityScenario.launch<MainActivity>(fromRecents).use { scenario ->
            compose.waitUntil(TIMEOUT_MS) { compose.onAllNodes(addMealButton).fetchSemanticsNodes().size == 1 }
            compose.waitForIdle()
            compose.onAllNodesWithText(str(Res.string.diet_eat_create)).assertCountEquals(0)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    @Test fun recreating_keepsOneEditorAndDoesNotFinish() {
        ActivityScenario.launch<MainActivity>(shareIntent("hello")).use { scenario ->
            val create = str(Res.string.diet_eat_create)
            waitForText(create)
            scenario.recreate()
            waitForText(create)
            compose.waitForIdle()
            compose.onAllNodesWithText(create).assertCountEquals(1)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    @Test fun aNormalLaunchNeverFinishesOnSave() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val create = str(Res.string.diet_eat_create)
            compose.waitUntil(TIMEOUT_MS) { compose.onAllNodes(addMealButton).fetchSemanticsNodes().size == 1 }
            compose.onNode(addMealButton).performClick()
            waitForText(create)
            compose.onNodeWithText(create).performClick()
            compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithText(create).fetchSemanticsNodes().isEmpty() }
            compose.waitForIdle()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
