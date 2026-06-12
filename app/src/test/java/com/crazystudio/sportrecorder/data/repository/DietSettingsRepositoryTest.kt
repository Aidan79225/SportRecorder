package com.crazystudio.sportrecorder.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.model.FastingWindow
import kotlinx.coroutines.CoroutineScope
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

    // Each call gets its own directory, so tests never share a DataStore file.
    private fun newRepo(scope: CoroutineScope): DietSettingsRepositoryImpl {
        val dir = tmp.newFolder()
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(dir, "diet.preferences_pb") },
        )
        return DietSettingsRepositoryImpl(dataStore)
    }

    @Test
    fun emptyStore_returnsDefaults() = runTest {
        val repo = newRepo(backgroundScope)
        assertEquals(DietSettings(fastingHours = 16, eatingHours = 8), repo.settings.first())
    }

    @Test
    fun setSelection_roundTrips() = runTest {
        val repo = newRepo(backgroundScope)
        repo.setSelection(FastingWindow(fastingHours = 20, eatingHours = 4))
        assertEquals(DietSettings(fastingHours = 20, eatingHours = 4), repo.settings.first())
    }
}
