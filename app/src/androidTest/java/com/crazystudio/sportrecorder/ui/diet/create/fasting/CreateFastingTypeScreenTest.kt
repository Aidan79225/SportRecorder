package com.crazystudio.sportrecorder.ui.diet.create.fasting

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * The create-fasting-type sheet's two buttons, in the language the app is in.
 *
 * The app speaks the device's language, which is the JVM default locale to Compose Resources. Every test
 * starts in English; the `inTraditionalChinese_` one switches to zh-TW and asserts hand-written literals, so
 * a string that was never translated can't pass by being compared with itself.
 */
@RunWith(AndroidJUnit4::class)
class CreateFastingTypeScreenTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var defaultLocale: Locale

    @Before
    fun pinEnglish() {
        defaultLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
    }

    /**
     * Shows the sheet in [locale], presses the buttons labelled [cancel] and [confirm] in that order, and
     * returns which actions ran. A swapped pair would still show both words, so what counts is that each
     * label sits on the button that does the thing. The language is switched first because the sheet reads
     * its strings as it first composes; [restoreLocale] puts the device's back afterwards.
     */
    private fun pressLabelled(locale: Locale, cancel: String, confirm: String): List<String> {
        Locale.setDefault(locale)
        val ran = mutableListOf<String>()
        compose.setContent {
            CreateFastingTypeScreen(
                onDismissRequest = { ran += "cancel" },
                onConfirmRequest = { _, _, _ -> ran += "confirm" },
            )
        }

        compose.onNodeWithText(cancel).performClick()
        compose.onNodeWithText(confirm).performClick()
        return ran
    }

    @Test
    fun inEnglish_eachButtonIsLabelledForWhatItDoes() {
        val ran = pressLabelled(Locale.US, cancel = "CANCEL", confirm = "OK")

        assertEquals(listOf("cancel", "confirm"), ran)
    }

    @Test
    fun inTraditionalChinese_eachButtonIsLabelledForWhatItDoes() {
        val ran = pressLabelled(Locale.TRADITIONAL_CHINESE, cancel = "取消", confirm = "新增")

        assertEquals(listOf("cancel", "confirm"), ran)
    }
}
