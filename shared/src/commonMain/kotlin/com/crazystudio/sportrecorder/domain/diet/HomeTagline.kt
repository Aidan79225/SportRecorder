package com.crazystudio.sportrecorder.domain.diet

import kotlin.time.Duration.Companion.hours

/**
 * Which pool of Home taglines fits the current moment. Each mood maps to a string-array of
 * gentle, non-judging lines in the UI layer; [HomeTagline.variant] picks one of them.
 */
enum class TaglineMood {
    IDLE_MORNING,
    IDLE_AFTERNOON,
    IDLE_EVENING,
    IDLE_NIGHT,
    EATING,
    EATING_WINDING_DOWN,
    FASTING_JUST_STARTED,
    FASTING,
    FASTING_ALMOST_THERE,
    SUCCESS,
}

/** A Home tagline choice: the [mood] pool, and a [variant] the UI reduces modulo the pool size. */
data class HomeTagline(val mood: TaglineMood, val variant: Int)

/**
 * Pure (Android-free) tagline picker for the Home headline.
 *
 * The mood follows the phase plus a little context (time of day, how far into the window/fast),
 * so the line changes as the day moves. The [seed] is fixed per Home visit, so the line doesn't
 * flicker with the per-second ticker but differs from one visit to the next.
 */
object HomeTaglinePicker {

    private val WINDING_DOWN_MILLIS = 1.hours.inWholeMilliseconds
    private const val JUST_STARTED_PROGRESS = 0.15f
    private const val ALMOST_THERE_PROGRESS = 0.85f

    private const val MORNING_START_HOUR = 5
    private const val AFTERNOON_START_HOUR = 11
    private const val EVENING_START_HOUR = 17
    private const val NIGHT_START_HOUR = 22

    /** Offsets each mood's index so switching pools doesn't always keep the same position. */
    private const val MOOD_SPREAD = 7

    fun pick(state: DietWindowState, hourOfDay: Int, seed: Int): HomeTagline {
        val mood = moodOf(state, hourOfDay)
        return HomeTagline(mood = mood, variant = (seed + mood.ordinal * MOOD_SPREAD) and Int.MAX_VALUE)
    }

    fun moodOf(state: DietWindowState, hourOfDay: Int): TaglineMood = when (state.phase) {
        DietPhase.IDLE -> when (hourOfDay) {
            in MORNING_START_HOUR until AFTERNOON_START_HOUR -> TaglineMood.IDLE_MORNING
            in AFTERNOON_START_HOUR until EVENING_START_HOUR -> TaglineMood.IDLE_AFTERNOON
            in EVENING_START_HOUR until NIGHT_START_HOUR -> TaglineMood.IDLE_EVENING
            else -> TaglineMood.IDLE_NIGHT
        }
        DietPhase.EATING ->
            // elapsedMillis is the time left in the window while eating.
            if (state.elapsedMillis <= WINDING_DOWN_MILLIS) TaglineMood.EATING_WINDING_DOWN else TaglineMood.EATING
        DietPhase.FASTING -> when {
            state.ringProgress < JUST_STARTED_PROGRESS -> TaglineMood.FASTING_JUST_STARTED
            state.ringProgress >= ALMOST_THERE_PROGRESS -> TaglineMood.FASTING_ALMOST_THERE
            else -> TaglineMood.FASTING
        }
        DietPhase.SUCCESS -> TaglineMood.SUCCESS
    }
}
