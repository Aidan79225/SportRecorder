package com.crazystudio.sportrecorder.flow

import androidx.lifecycle.SavedStateHandle
import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.domain.usecase.LoadEatRecordUseCase
import com.crazystudio.sportrecorder.domain.usecase.SaveEatRecordUseCase
import com.crazystudio.sportrecorder.fake.FakeEatRecordRepository
import com.crazystudio.sportrecorder.fake.FakeLocationProvider
import com.crazystudio.sportrecorder.fake.FakePhotoFileStore
import com.crazystudio.sportrecorder.fake.FakePhotoImageSource
import com.crazystudio.sportrecorder.fake.FakePhotoImporter
import com.crazystudio.sportrecorder.fake.FakeRemindersRescheduler
import com.crazystudio.sportrecorder.testutil.MainDispatcherRule
import com.crazystudio.sportrecorder.ui.diet.editor.EatTimeEditorUiState
import com.crazystudio.sportrecorder.ui.diet.editor.EatTimeEditorViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Capture journey through the real editor ViewModel — the screen a user actually fills in when
 * recording a meal: 時間 / 備註 / 照片 / 位置, plus the edit path and the two ways it can fail.
 *
 * The VM seeds its time from the wall clock and has no clock seam, so the tests drive it the way
 * the UI does — through `updateDate` / `updateTime` — and use dates that are unambiguously in the
 * past (or future) rather than asserting on "now".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EatTimeEditorFlowTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private lateinit var defaultTz: TimeZone
    private lateinit var defaultLocale: Locale

    @Before
    fun pinTimeZoneAndLocale() {
        // kotlinx-datetime's currentSystemDefault() reads the JVM default, and the VM converts
        // the picked date/time through it — pin it so the stored epoch millis are deterministic.
        defaultTz = TimeZone.getDefault()
        defaultLocale = Locale.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreTimeZoneAndLocale() {
        TimeZone.setDefault(defaultTz)
        Locale.setDefault(defaultLocale)
    }

    private class Editor(records: List<EatRecord> = emptyList()) {
        val repo = FakeEatRecordRepository(records)
        val rescheduler = FakeRemindersRescheduler()
        val locationProvider = FakeLocationProvider()
        val photoImporter = FakePhotoImporter()
        val photoFileStore = FakePhotoFileStore()

        fun viewModel(eatTimeId: Int? = null) = EatTimeEditorViewModel(
            loadEatRecord = LoadEatRecordUseCase(repo),
            saveEatRecord = SaveEatRecordUseCase(repo, rescheduler),
            locationProvider = locationProvider,
            photoImporter = photoImporter,
            photoFileStore = photoFileStore,
            photoImageSource = FakePhotoImageSource(),
            savedStateHandle = if (eatTimeId == null) {
                SavedStateHandle()
            } else {
                SavedStateHandle(mapOf("eatTimeId" to eatTimeId))
            },
        )
    }

    /** Epoch millis of a UTC wall-clock moment; the JVM default zone is pinned to UTC above. */
    private fun utcMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.clear()
        calendar.set(year, month - 1, day, hour, minute, 0)
        return calendar.timeInMillis
    }

    /** The VM keeps the seconds it was seeded with, so assert to the chosen minute. */
    private fun assertWithinMinute(expectedMinuteStart: Long, actual: Long) {
        assertTrue(
            "expected a time inside the minute starting at $expectedMinuteStart but was $actual",
            actual >= expectedMinuteStart && actual < expectedMinuteStart + MILLIS_PER_MINUTE,
        )
    }

    private val pastMeal = utcMillis(2024, 3, 5, 12, 30)

    @Test
    fun create_capturesNotePhotoLocationAndTheChosenMoment() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor()
        editor.locationProvider.location = GeoPoint(25.033, 121.565)
        val vm = editor.viewModel()

        vm.setNote("野餐")
        vm.addCapturedPhoto("/cache/camera-tmp.jpg")
        vm.requestLocation()
        advanceUntilIdle()
        vm.updateDate(year = 2024, month = 2, dayOfMonth = 5) // month is 0-based here
        vm.updateTime(hourOfDay = 12, minute = 30)

        assertTrue(vm.save())

        val saved = editor.repo.stored.single()
        assertEquals("野餐", saved.note)
        assertEquals(GeoPoint(25.033, 121.565), saved.location)
        assertEquals(listOf("capture-1.webp"), saved.photos.map { it.fileName })
        assertWithinMinute(pastMeal, saved.time)
        assertEquals(1, editor.rescheduler.rescheduleCount)
    }

    @Test
    fun create_bareMeal_savesWithoutNotePhotoOrLocation() = runTest(mainRule.testDispatcher.scheduler) {
        // 記錄不該是負擔: a moment is worth keeping even with nothing attached to it.
        val editor = Editor()
        val vm = editor.viewModel()

        vm.updateDate(year = 2024, month = 2, dayOfMonth = 5)
        vm.updateTime(hourOfDay = 12, minute = 30)

        assertTrue(vm.save())

        val saved = editor.repo.stored.single()
        assertNull(saved.note)
        assertNull(saved.location)
        assertTrue(saved.photos.isEmpty())
    }

    @Test
    fun create_blankNoteIsStoredAsNull() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor()
        val vm = editor.viewModel()

        vm.setNote("   ")
        vm.updateDate(year = 2024, month = 2, dayOfMonth = 5)
        vm.updateTime(hourOfDay = 12, minute = 30)
        vm.save()

        assertNull(editor.repo.stored.single().note)
    }

    @Test
    fun create_aTimeInTheFutureIsRejected() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor()
        val vm = editor.viewModel()

        vm.setNote("未來的一餐")
        vm.updateDate(year = 2099, month = 0, dayOfMonth = 1)

        assertFalse(vm.save())
        assertTrue(editor.repo.stored.isEmpty())
        assertEquals(0, editor.rescheduler.rescheduleCount)
    }

    @Test
    fun create_locationUnavailable_stillSaves() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor()
        editor.locationProvider.location = null
        val vm = editor.viewModel()

        vm.requestLocation()
        advanceUntilIdle()

        assertEquals(EatTimeEditorUiState.LocationStatus.UNAVAILABLE, vm.uiState.value.locationStatus)

        vm.updateDate(year = 2024, month = 2, dayOfMonth = 5)
        vm.updateTime(hourOfDay = 12, minute = 30)

        assertTrue(vm.save())
        assertNull(editor.repo.stored.single().location)
    }

    @Test
    fun create_clearingAFoundLocationDropsIt() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor()
        editor.locationProvider.location = GeoPoint(1.0, 2.0)
        val vm = editor.viewModel()

        vm.requestLocation()
        advanceUntilIdle()
        vm.clearLocation()

        assertNull(vm.uiState.value.location)
        assertEquals(EatTimeEditorUiState.LocationStatus.IDLE, vm.uiState.value.locationStatus)
    }

    @Test
    fun create_aFailedPhotoImportIsIgnored() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor()
        editor.photoImporter.succeeds = false
        val vm = editor.viewModel()

        vm.addCapturedPhoto("/cache/broken.jpg")
        advanceUntilIdle()

        assertEquals(listOf("/cache/broken.jpg"), editor.photoImporter.importedSources)
        assertTrue(vm.uiState.value.pendingPhotos.isEmpty())
    }

    @Test
    fun create_removingAPendingPhotoDropsOnlyThatOne() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor()
        val vm = editor.viewModel()

        vm.addCapturedPhoto("/cache/a.jpg")
        vm.addPickedPhoto("content://media/b")
        advanceUntilIdle()
        val first = vm.uiState.value.pendingPhotos.first()
        vm.removePendingPhoto(first)

        assertEquals(listOf("picked-2.webp"), vm.uiState.value.pendingPhotos)
    }

    @Test
    fun edit_loadsTheExistingRecordIntoTheForm() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(
            listOf(
                EatRecord(
                    id = 1,
                    time = pastMeal,
                    location = GeoPoint(25.0, 121.5),
                    note = "上次的午餐",
                    photos = listOf(EatPhoto(7, "old.webp", pastMeal)),
                ),
            ),
        )
        val vm = editor.viewModel(eatTimeId = 1)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.isEditMode)
        assertEquals(pastMeal, state.dateMillis)
        assertEquals("上次的午餐", state.note)
        assertEquals(listOf("old.webp"), state.existingPhotos.map { it.fileName })
        assertEquals(EatTimeEditorUiState.LatLng(25.0, 121.5), state.location)
        assertEquals(EatTimeEditorUiState.LocationStatus.AVAILABLE, state.locationStatus)
    }

    @Test
    fun edit_updatesTheNoteAndReconcilesPhotos() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(
            listOf(
                EatRecord(
                    id = 1,
                    time = pastMeal,
                    location = null,
                    note = "午餐",
                    photos = listOf(EatPhoto(7, "old.webp", pastMeal), EatPhoto(8, "keep.webp", pastMeal)),
                ),
            ),
        )
        val vm = editor.viewModel(eatTimeId = 1)
        advanceUntilIdle()

        vm.removeExistingPhoto(vm.uiState.value.existingPhotos.single { it.fileName == "old.webp" })
        vm.setNote("午餐(補記)")
        vm.addCapturedPhoto("/cache/new.jpg")
        advanceUntilIdle()

        assertTrue(vm.save())

        val saved = editor.repo.stored.single()
        assertEquals(1, saved.id)
        assertEquals("午餐(補記)", saved.note)
        assertEquals(listOf("keep.webp", "capture-1.webp"), saved.photos.map { it.fileName })
        assertEquals(1, editor.rescheduler.rescheduleCount)
    }

    @Test
    fun edit_aRecordThatNoLongerExistsLeavesAnEmptyForm() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor()
        val vm = editor.viewModel(eatTimeId = 42)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.isEditMode)
        assertEquals("", state.note)
        assertTrue(state.existingPhotos.isEmpty())
        assertNull(state.location)
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
