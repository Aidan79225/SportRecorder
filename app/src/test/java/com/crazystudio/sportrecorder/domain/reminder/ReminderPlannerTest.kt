package com.crazystudio.sportrecorder.domain.reminder

import com.crazystudio.sportrecorder.domain.model.DietSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class ReminderPlannerTest {

    // Quiet-hours logic reads time-of-day via Calendar (JVM default TZ); pin it for determinism.
    private lateinit var defaultTz: TimeZone
    private lateinit var defaultLocale: Locale

    @Before
    fun pin() {
        defaultTz = TimeZone.getDefault()
        defaultLocale = Locale.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.US)
    }

    @After
    fun restore() {
        TimeZone.setDefault(defaultTz)
        Locale.setDefault(defaultLocale)
    }

    // day0 = 2023-11-14T00:00:00Z, so day0 + Nh is N:00 local time in the pinned UTC zone.
    private val day0 = 1_699_920_000_000L
    private val settings = DietSettings(fastingHours = 16, eatingHours = 8)
    private fun h(n: Long) = TimeUnit.HOURS.toMillis(n)
    private fun min(n: Long) = TimeUnit.MINUTES.toMillis(n)

    @Test
    fun noEats_producesNothing() {
        val out = ReminderPlanner.plan(emptyList(), settings, ReminderPrefs(), day0 + h(1))
        assertTrue(out.isEmpty())
    }

    @Test
    fun allDisabled_producesNothing() {
        // Eating at 11:00; everything off.
        val out = ReminderPlanner.plan(listOf(day0 + h(10)), settings, ReminderPrefs(), day0 + h(11))
        assertTrue(out.isEmpty())
    }

    @Test
    fun windowClosing_onlyWhileEating_appliesLeadTime() {
        // Eat 10:00 → windowEnd 18:00; lead 30 → trigger 17:30. now 11:00 (EATING).
        val prefs = ReminderPrefs(windowClosingEnabled = true)
        val out = ReminderPlanner.plan(listOf(day0 + h(10)), settings, prefs, day0 + h(11))

        assertEquals(1, out.size)
        assertEquals(ReminderType.WINDOW_CLOSING, out[0].type)
        assertEquals(day0 + h(18) - min(30), out[0].triggerAtMillis)
    }

    @Test
    fun windowClosing_customLeadTime() {
        val prefs = ReminderPrefs(windowClosingEnabled = true, leadMinutes = 45)
        val out = ReminderPlanner.plan(listOf(day0 + h(10)), settings, prefs, day0 + h(11))
        assertEquals(day0 + h(18) - min(45), out[0].triggerAtMillis)
    }

    @Test
    fun windowClosing_skippedWhenFasting() {
        // now 20:00 is past windowEnd 18:00 → FASTING phase, no window-closing reminder.
        val prefs = ReminderPrefs(windowClosingEnabled = true)
        val out = ReminderPlanner.plan(listOf(day0 + h(10)), settings, prefs, day0 + h(20))
        assertTrue(out.isEmpty())
    }

    @Test
    fun windowClosing_skippedWhenTriggerAlreadyPast() {
        // Still EATING (now 17:45 < windowEnd 18:00) but trigger 17:30 has passed.
        val prefs = ReminderPrefs(windowClosingEnabled = true)
        val out = ReminderPlanner.plan(listOf(day0 + h(10)), settings, prefs, day0 + h(17) + min(45))
        assertTrue(out.isEmpty())
    }

    @Test
    fun fastComplete_atLastEatPlusFastingHours() {
        // Single meal 10:00 → fast starts at 11:00 (meal + 1h grace) → target 03:00 next day.
        val prefs = ReminderPrefs(fastCompleteEnabled = true)
        val out = ReminderPlanner.plan(listOf(day0 + h(10)), settings, prefs, day0 + h(11))

        assertEquals(1, out.size)
        assertEquals(ReminderType.FAST_COMPLETE, out[0].type)
        assertEquals(day0 + h(27), out[0].triggerAtMillis)
    }

    @Test
    fun fastComplete_usesLastEatNotFirst() {
        // Two eats in window; fastTargetAt counts from the latest (12:00 + 16 = 04:00 next day).
        val prefs = ReminderPrefs(fastCompleteEnabled = true)
        val out = ReminderPlanner.plan(listOf(day0 + h(10), day0 + h(12)), settings, prefs, day0 + h(13))
        assertEquals(day0 + h(28), out[0].triggerAtMillis)
    }

    @Test
    fun fastComplete_droppedWhenAlreadyComplete() {
        // now 27:00 (03:00 next day) is past target 02:00 → SUCCESS, nothing to schedule.
        val prefs = ReminderPrefs(fastCompleteEnabled = true)
        val out = ReminderPlanner.plan(listOf(day0 + h(10)), settings, prefs, day0 + h(27))
        assertTrue(out.isEmpty())
    }

    @Test
    fun fastComplete_suppressedByQuietHours_spanningMidnight() {
        // target 02:00 falls inside the default quiet window 22:00–08:00 → suppressed.
        val prefs = ReminderPrefs(fastCompleteEnabled = true, quietHoursEnabled = true)
        val out = ReminderPlanner.plan(listOf(day0 + h(10)), settings, prefs, day0 + h(11))
        assertTrue(out.isEmpty())
    }

    @Test
    fun fastComplete_notSuppressedOutsideQuietHours_spanningMidnight() {
        // Single meal 22:00 → fast from 23:00 (meal + 1h) → target 15:00 next day, outside the
        // 22:00–08:00 quiet hours. now 23:00.
        val prefs = ReminderPrefs(fastCompleteEnabled = true, quietHoursEnabled = true)
        val out = ReminderPlanner.plan(listOf(day0 + h(22)), settings, prefs, day0 + h(23))

        assertEquals(1, out.size)
        assertEquals(ReminderType.FAST_COMPLETE, out[0].type)
        assertEquals(day0 + h(39), out[0].triggerAtMillis)
    }

    @Test
    fun fastComplete_suppressedByQuietHours_nonSpanning() {
        // Non-spanning quiet window 01:00–05:00; target 02:00 is inside → suppressed.
        val prefs = ReminderPrefs(
            fastCompleteEnabled = true,
            quietHoursEnabled = true,
            quietStartMinutes = 60,
            quietEndMinutes = 300,
        )
        val out = ReminderPlanner.plan(listOf(day0 + h(10)), settings, prefs, day0 + h(11))
        assertTrue(out.isEmpty())
    }

    @Test
    fun fastComplete_notSuppressed_nonSpanningWindowMissesTarget() {
        // Single meal 14:00 → fast from 15:00 (meal + 1h) → target 07:00, outside 01:00–05:00.
        val prefs = ReminderPrefs(
            fastCompleteEnabled = true,
            quietHoursEnabled = true,
            quietStartMinutes = 60,
            quietEndMinutes = 300,
        )
        val out = ReminderPlanner.plan(listOf(day0 + h(14)), settings, prefs, day0 + h(15))
        assertEquals(day0 + h(31), out[0].triggerAtMillis)
    }

    @Test
    fun bothReminders_whenEatingAndEnabled() {
        val prefs = ReminderPrefs(windowClosingEnabled = true, fastCompleteEnabled = true)
        val out = ReminderPlanner.plan(listOf(day0 + h(10)), settings, prefs, day0 + h(11))

        val types = out.map { it.type }.toSet()
        assertEquals(setOf(ReminderType.WINDOW_CLOSING, ReminderType.FAST_COMPLETE), types)
    }
}
