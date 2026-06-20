package com.crazystudio.sportrecorder.domain.reminder

import com.crazystudio.sportrecorder.domain.model.DietSettings
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Moved to :shared/commonTest (kotlin.test). The injected UTC zone makes quiet-hours time-of-day
// deterministic on every platform — this runs on JVM and on the iOS simulator in CI, exercising
// the kotlinx-datetime path on Kotlin/Native.
class ReminderPlannerTest {

    private val utc = TimeZone.UTC

    // day0 = 2023-11-14T00:00:00Z, so day0 + Nh is N:00 in UTC.
    private val day0 = 1_699_920_000_000L
    private val settings = DietSettings(fastingHours = 16, eatingHours = 8)
    private fun h(n: Long) = n * 3_600_000L
    private fun min(n: Long) = n * 60_000L

    @Test
    fun noEats_producesNothing() {
        val out = ReminderPlanner.plan(emptyList(), settings, ReminderPrefs(), day0 + h(1), utc)
        assertTrue(out.isEmpty())
    }

    @Test
    fun allDisabled_producesNothing() {
        val out = ReminderPlanner.plan(listOf(day0 + h(10)), settings, ReminderPrefs(), day0 + h(11), utc)
        assertTrue(out.isEmpty())
    }

    @Test
    fun windowClosing_onlyWhileEating_appliesLeadTime() {
        // Eat 10:00 → windowEnd 18:00; lead 30 → trigger 17:30. now 11:00 (EATING).
        val prefs = ReminderPrefs(windowClosingEnabled = true)
        val out = ReminderPlanner.plan(listOf(day0 + h(10)), settings, prefs, day0 + h(11), utc)

        assertEquals(1, out.size)
        assertEquals(ReminderType.WINDOW_CLOSING, out[0].type)
        assertEquals(day0 + h(18) - min(30), out[0].triggerAtMillis)
    }

    @Test
    fun windowClosing_customLeadTime() {
        val prefs = ReminderPrefs(windowClosingEnabled = true, leadMinutes = 45)
        val out = ReminderPlanner.plan(listOf(day0 + h(10)), settings, prefs, day0 + h(11), utc)
        assertEquals(day0 + h(18) - min(45), out[0].triggerAtMillis)
    }

    @Test
    fun windowClosing_skippedWhenFasting() {
        val prefs = ReminderPrefs(windowClosingEnabled = true)
        val out = ReminderPlanner.plan(listOf(day0 + h(10)), settings, prefs, day0 + h(20), utc)
        assertTrue(out.isEmpty())
    }

    @Test
    fun windowClosing_skippedWhenTriggerAlreadyPast() {
        val prefs = ReminderPrefs(windowClosingEnabled = true)
        val out = ReminderPlanner.plan(listOf(day0 + h(10)), settings, prefs, day0 + h(17) + min(45), utc)
        assertTrue(out.isEmpty())
    }

    @Test
    fun fastComplete_atLastEatPlusFastingHours() {
        // Single meal 10:00 → fast from 11:00 (meal + 1h grace) → target 03:00 next day.
        val prefs = ReminderPrefs(fastCompleteEnabled = true)
        val out = ReminderPlanner.plan(listOf(day0 + h(10)), settings, prefs, day0 + h(11), utc)

        assertEquals(1, out.size)
        assertEquals(ReminderType.FAST_COMPLETE, out[0].type)
        assertEquals(day0 + h(27), out[0].triggerAtMillis)
    }

    @Test
    fun fastComplete_usesLastEatNotFirst() {
        val prefs = ReminderPrefs(fastCompleteEnabled = true)
        val out = ReminderPlanner.plan(listOf(day0 + h(10), day0 + h(12)), settings, prefs, day0 + h(13), utc)
        assertEquals(day0 + h(28), out[0].triggerAtMillis)
    }

    @Test
    fun fastComplete_droppedWhenAlreadyComplete() {
        val prefs = ReminderPrefs(fastCompleteEnabled = true)
        val out = ReminderPlanner.plan(listOf(day0 + h(10)), settings, prefs, day0 + h(27), utc)
        assertTrue(out.isEmpty())
    }

    @Test
    fun fastComplete_suppressedByQuietHours_spanningMidnight() {
        // target 03:00 falls inside the default quiet window 22:00–08:00 → suppressed.
        val prefs = ReminderPrefs(fastCompleteEnabled = true, quietHoursEnabled = true)
        val out = ReminderPlanner.plan(listOf(day0 + h(10)), settings, prefs, day0 + h(11), utc)
        assertTrue(out.isEmpty())
    }

    @Test
    fun fastComplete_notSuppressedOutsideQuietHours_spanningMidnight() {
        // Single meal 22:00 → fast from 23:00 → target 15:00 next day, outside 22:00–08:00.
        val prefs = ReminderPrefs(fastCompleteEnabled = true, quietHoursEnabled = true)
        val out = ReminderPlanner.plan(listOf(day0 + h(22)), settings, prefs, day0 + h(23), utc)

        assertEquals(1, out.size)
        assertEquals(ReminderType.FAST_COMPLETE, out[0].type)
        assertEquals(day0 + h(39), out[0].triggerAtMillis)
    }

    @Test
    fun fastComplete_suppressedByQuietHours_nonSpanning() {
        // Non-spanning quiet window 01:00–05:00; target 03:00 is inside → suppressed.
        val prefs = ReminderPrefs(
            fastCompleteEnabled = true,
            quietHoursEnabled = true,
            quietStartMinutes = 60,
            quietEndMinutes = 300,
        )
        val out = ReminderPlanner.plan(listOf(day0 + h(10)), settings, prefs, day0 + h(11), utc)
        assertTrue(out.isEmpty())
    }

    @Test
    fun fastComplete_notSuppressed_nonSpanningWindowMissesTarget() {
        // Single meal 14:00 → fast from 15:00 → target 07:00, outside 01:00–05:00.
        val prefs = ReminderPrefs(
            fastCompleteEnabled = true,
            quietHoursEnabled = true,
            quietStartMinutes = 60,
            quietEndMinutes = 300,
        )
        val out = ReminderPlanner.plan(listOf(day0 + h(14)), settings, prefs, day0 + h(15), utc)
        assertEquals(day0 + h(31), out[0].triggerAtMillis)
    }

    @Test
    fun bothReminders_whenEatingAndEnabled() {
        val prefs = ReminderPrefs(windowClosingEnabled = true, fastCompleteEnabled = true)
        val out = ReminderPlanner.plan(listOf(day0 + h(10)), settings, prefs, day0 + h(11), utc)

        val types = out.map { it.type }.toSet()
        assertEquals(setOf(ReminderType.WINDOW_CLOSING, ReminderType.FAST_COMPLETE), types)
    }
}
