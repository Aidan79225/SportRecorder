package com.crazystudio.sportrecorder.ui.diet.record

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazystudio.sportrecorder.ui.theme.SportRecorderTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * The photo viewer's buttons are icon-only, so their labels are all a screen reader has to go on.
 *
 * The toolbar is :app code and reads Android string resources, which follow the Context's configuration, not
 * the JVM default locale that Compose Resources follow (see `RecordScreenTest`). So each test hands it a context
 * set to the language it is checking, which also keeps the English one from depending on the device. The
 * Chinese test asserts hand-written literals, so a string that was never translated can't pass by being
 * compared with itself.
 */
@RunWith(AndroidJUnit4::class)
class PhotoViewerToolbarTest {
    @get:Rule val compose = createComposeRule()

    /**
     * Shows the toolbar as a device set to [locale] would, presses the buttons labelled [share], [save] and
     * [close] in that order, and returns which actions ran. A swapped pair would still show every word, so
     * what counts is that each label sits on the button that does the thing.
     */
    private fun pressLabelled(locale: Locale, share: String, save: String, close: String): List<String> {
        val ran = mutableListOf<String>()
        compose.setContent {
            val host = LocalContext.current
            val config = Configuration(LocalConfiguration.current).apply { setLocale(locale) }
            val localized = remember { LocalizedContext(host, host.createConfigurationContext(config)) }
            CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides config) {
                SportRecorderTheme {
                    PhotoViewerToolbar(
                        onShare = { ran += "share" },
                        onSave = { ran += "save" },
                        onClose = { ran += "close" },
                    )
                }
            }
        }

        compose.onNodeWithContentDescription(share).performClick()
        compose.onNodeWithContentDescription(save).performClick()
        compose.onNodeWithContentDescription(close).performClick()
        return ran
    }

    @Test
    fun inEnglish_eachButtonIsLabelledForWhatItDoes() {
        val ran = pressLabelled(Locale.US, share = "Share", save = "Save", close = "Close")

        assertEquals(listOf("share", "save", "close"), ran)
    }

    @Test
    fun inTraditionalChinese_eachButtonIsLabelledForWhatItDoes() {
        val ran = pressLabelled(Locale.TRADITIONAL_CHINESE, share = "分享", save = "儲存", close = "關閉")

        assertEquals(listOf("share", "save", "close"), ran)
    }
}

/** [host] (the activity), except that its string resources are [localized]'s. */
private class LocalizedContext(host: Context, private val localized: Context) : ContextWrapper(host) {
    override fun getResources(): Resources = localized.resources
}
