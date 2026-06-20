package com.crazystudio.sportrecorder.ui.diet

import app.cash.turbine.test
import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.usecase.ObserveDietStateUseCase
import com.crazystudio.sportrecorder.fake.FakeDietSettingsRepository
import com.crazystudio.sportrecorder.fake.FakeEatRecordRepository
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.diet_fasting_time
import com.crazystudio.sportrecorder.shared.resources.diet_no_record
import com.crazystudio.sportrecorder.shared.resources.diet_remaining_time
import com.crazystudio.sportrecorder.shared.resources.diet_status_eating
import com.crazystudio.sportrecorder.shared.resources.diet_status_fasting
import com.crazystudio.sportrecorder.shared.resources.diet_status_idle
import com.crazystudio.sportrecorder.shared.resources.diet_status_success
import com.crazystudio.sportrecorder.shared.resources.ic_baseline_fastfood_24
import com.crazystudio.sportrecorder.shared.resources.ic_baseline_no_food_24
import com.crazystudio.sportrecorder.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class DietViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val f = 1_700_000_000_000L
    private fun h(n: Long) = TimeUnit.HOURS.toMillis(n)
    private fun eat(time: Long) =
        EatRecord(id = 1, time = time, location = null, note = null, photos = emptyList())

    private lateinit var defaultTz: TimeZone
    private lateinit var defaultLocale: Locale

    @Before
    fun pinTimeZoneAndLocale() {
        // Calendar (use case) and SimpleDateFormat (VM) read the JVM defaults; pin them
        // so time/elapsed strings are deterministic regardless of the CI machine.
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

    private fun viewModel(
        eats: List<EatRecord>,
        settings: DietSettings = DietSettings(fastingHours = 16, eatingHours = 8),
        now: () -> Long,
    ): DietViewModel {
        val eatRepo = FakeEatRecordRepository(initial = eats)
        val settingsRepo = FakeDietSettingsRepository(settings)
        return DietViewModel(ObserveDietStateUseCase(eatRepo, settingsRepo), now)
    }

    @Test
    fun fasting_mapsStatusProgressLabelAndElapsed() = runTest(mainRule.testDispatcher.scheduler) {
        val vm = viewModel(eats = listOf(eat(f))) { f + h(12) }

        vm.uiState.test {
            skipItems(1) // initial DietUiState() default
            val s = awaitItem()
            assertEquals(Res.string.diet_status_fasting, s.statusText)
            assertEquals(Res.drawable.ic_baseline_no_food_24, s.statusIcon)
            assertEquals(Res.string.diet_fasting_time, s.promptText)
            // Single meal: fast clock starts 1h after the meal, so at +12h it's 11h (68.75%).
            assertEquals(68.75f, s.progress, 0.01f)
            assertEquals("16 : 8", s.fastingLabel)
            assertEquals("11:00:00", s.elapsedText)
            // Fast start is the meal + 1h grace (f = 22:13 UTC → 23:13), not windowEnd.
            assertEquals("23:13", s.fastStart?.time)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun eating_mapsStatusAndPrompt() = runTest(mainRule.testDispatcher.scheduler) {
        val vm = viewModel(eats = listOf(eat(f))) { f + h(2) }

        vm.uiState.test {
            skipItems(1)
            val s = awaitItem()
            assertEquals(Res.string.diet_status_eating, s.statusText)
            assertEquals(Res.drawable.ic_baseline_fastfood_24, s.statusIcon)
            assertEquals(Res.string.diet_remaining_time, s.promptText)
            assertEquals(25f, s.progress, 0.01f)
            assertEquals("06:00:00", s.elapsedText) // windowEnd - now = 6h
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun success_mapsStatusAndFullProgress() = runTest(mainRule.testDispatcher.scheduler) {
        // Single meal: success at meal + 1h grace + 16h fasting.
        val vm = viewModel(eats = listOf(eat(f))) { f + h(17) }

        vm.uiState.test {
            skipItems(1)
            val s = awaitItem()
            assertEquals(Res.string.diet_status_success, s.statusText)
            assertEquals(Res.drawable.ic_baseline_no_food_24, s.statusIcon)
            assertEquals(100f, s.progress, 0.01f)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun idle_whenNoRecords() = runTest(mainRule.testDispatcher.scheduler) {
        val vm = viewModel(eats = emptyList()) { f }

        vm.uiState.test {
            skipItems(1)
            val s = awaitItem()
            assertEquals(Res.string.diet_status_idle, s.statusText)
            assertEquals(Res.string.diet_no_record, s.promptText)
            assertEquals("00:00:00", s.elapsedText)
            assertEquals(null, s.fastStart)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun ticker_recomputesEverySecond() = runTest(mainRule.testDispatcher.scheduler) {
        var nowMs = f + h(12)
        val vm = viewModel(eats = listOf(eat(f))) { nowMs }

        vm.uiState.test {
            skipItems(1) // initial default
            // Single meal: fast clock starts 1h after the meal, so +12h shows 11h.
            assertEquals("11:00:00", awaitItem().elapsedText)

            nowMs += TimeUnit.SECONDS.toMillis(1)
            advanceTimeBy(TimeUnit.SECONDS.toMillis(1))

            assertEquals("11:00:01", awaitItem().elapsedText)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
