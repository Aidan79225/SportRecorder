package com.crazystudio.sportrecorder.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.model.FastingWindow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DietSettingsRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun emptyStore_returnsDefaults() = runTest {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(tmp.root, "diet.preferences_pb") },
        )
        val repo = DietSettingsRepositoryImpl(dataStore)
        assertEquals(DietSettings(fastingHours = 16, eatingHours = 8), repo.settings.first())
    }

    @Test
    fun setSelection_roundTrips() = runTest {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(tmp.root, "diet.preferences_pb") },
        )
        val repo = DietSettingsRepositoryImpl(dataStore)
        repo.setSelection(FastingWindow(fastingHours = 20, eatingHours = 4))
        assertEquals(DietSettings(fastingHours = 20, eatingHours = 4), repo.settings.first())
    }
}
