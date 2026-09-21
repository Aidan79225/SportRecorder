package com.crazystudio.sportrecorder.flow

import com.crazystudio.sportrecorder.backup.fakes.FakeDietSettingsRepository
import com.crazystudio.sportrecorder.backup.fakes.FakeFastingTypeRepository
import com.crazystudio.sportrecorder.backup.fakes.FakeRemindersRescheduler
import com.crazystudio.sportrecorder.domain.model.CustomFastingType
import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.usecase.CreateCustomFastingTypeUseCase
import com.crazystudio.sportrecorder.domain.usecase.ObserveCustomFastingTypesUseCase
import com.crazystudio.sportrecorder.domain.usecase.SaveFastingSelectionUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 自訂斷食類型 journey — 建立 → 出現在清單 → 選取 → 套用到設定, through the real use cases.
 */
class CustomFastingTypeFlowTest {

    private class Harness {
        val typeRepo = FakeFastingTypeRepository()
        val settingsRepo = FakeDietSettingsRepository()
        val rescheduler = FakeRemindersRescheduler()
        val createType = CreateCustomFastingTypeUseCase(typeRepo)
        val observeTypes = ObserveCustomFastingTypesUseCase(typeRepo)
        val selectWindow = SaveFastingSelectionUseCase(settingsRepo, rescheduler)
    }

    @Test
    fun emptyState_hasNoCustomTypes() = runTest {
        val app = Harness()

        assertEquals(emptyList<CustomFastingType>(), app.observeTypes().first())
    }

    @Test
    fun create_addsANamedCustomWindow() = runTest {
        val app = Harness()

        val created = app.createType(FastingWindow(fastingHours = 18, eatingHours = 6), name = "我的節奏")

        assertTrue(created)
        assertEquals(
            listOf(CustomFastingType(18, 6, "我的節奏")),
            app.observeTypes().first(),
        )
    }

    @Test
    fun create_withoutAName_storesNull() = runTest {
        val app = Harness()

        app.createType(FastingWindow(fastingHours = 18, eatingHours = 6))

        assertNull(app.observeTypes().first().single().name)
    }

    @Test
    fun create_duplicateOfABuiltInDefault_isRejected() = runTest {
        val app = Harness()

        val created = app.createType(FastingWindow(fastingHours = 16, eatingHours = 8), name = "again")

        assertFalse(created)
        assertEquals(emptyList<CustomFastingType>(), app.observeTypes().first())
    }

    @Test
    fun create_duplicateOfAnExistingCustomType_isRejected() = runTest {
        val app = Harness()
        app.createType(FastingWindow(fastingHours = 18, eatingHours = 6), name = "first")

        val created = app.createType(FastingWindow(fastingHours = 18, eatingHours = 6), name = "second")

        assertFalse(created)
        assertEquals(listOf("first"), app.observeTypes().first().map { it.name })
    }

    @Test
    fun createdTypesAreListedNewestFirst() = runTest {
        val app = Harness()

        app.createType(FastingWindow(fastingHours = 18, eatingHours = 6), name = "older")
        app.createType(FastingWindow(fastingHours = 12, eatingHours = 12), name = "newer")

        assertEquals(listOf("newer", "older"), app.observeTypes().first().map { it.name })
    }

    @Test
    fun selectingACustomType_appliesItToSettingsAndRearmsReminders() = runTest {
        val app = Harness()
        app.createType(FastingWindow(fastingHours = 18, eatingHours = 6), name = "我的節奏")
        val custom = app.observeTypes().first().single()

        app.selectWindow(FastingWindow(custom.fastingHours, custom.eatingHours))

        assertEquals(DietSettings(fastingHours = 18, eatingHours = 6), app.settingsRepo.state.value)
        assertEquals(1, app.rescheduler.rescheduleCount)
    }

    @Test
    fun selectingABuiltInType_takesTheSamePath() = runTest {
        val app = Harness()

        app.selectWindow(FastingWindow(fastingHours = 20, eatingHours = 4))

        assertEquals(DietSettings(fastingHours = 20, eatingHours = 4), app.settingsRepo.state.value)
        assertEquals(1, app.rescheduler.rescheduleCount)
    }
}
