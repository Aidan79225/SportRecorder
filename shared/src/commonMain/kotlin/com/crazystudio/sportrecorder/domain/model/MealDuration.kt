package com.crazystudio.sportrecorder.domain.model

private const val MINUTES_PER_HOUR = 60

/**
 * How long a meal lasts. Every way of recording gets [DEFAULT_MINUTES] without asking — the same
 * hour the app has always assumed — and the editor offers [CHOICES] for when it was different.
 */
object MealDuration {
    const val DEFAULT_MINUTES = 60
    const val MILLIS_PER_MINUTE = 60_000L

    /** The editor's menu, ascending. */
    val CHOICES: List<Int> = listOf(15, 30, 45, 60, 90, 120, 180)

    /** (hours, remaining minutes): 90 → (1, 30); 60 → (1, 0); 45 → (0, 45). */
    fun parts(minutes: Int): Pair<Int, Int> = minutes / MINUTES_PER_HOUR to minutes % MINUTES_PER_HOUR
}
