package com.crazystudio.sportrecorder.backup

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

/**
 * The decision the background job asks before doing anything. It exists so that "should we back up
 * right now" is answerable without Android, a network or a clock — and so the job can be retried by
 * the system without quietly backing up over and over.
 */
class AutoBackupTest {

    private val now = 1_767_000_000_000L
    private val on = AutoBackupPrefs(enabled = true)

    private fun shouldRun(prefs: AutoBackupPrefs, signedIn: Boolean = true, at: Long = now) =
        AutoBackup.shouldRun(prefs, signedIn = signedIn, now = at)

    @Test
    fun offByDefault() {
        assertFalse(AutoBackupPrefs().enabled)
        assertFalse(shouldRun(AutoBackupPrefs()))
    }

    @Test
    fun doesNotRunWhenSignedOut() {
        // Signing out must not nag or fail repeatedly in the background; it simply stops.
        assertFalse(shouldRun(on, signedIn = false))
    }

    @Test
    fun runsWhenEnabledAndNeverRunBefore() {
        assertTrue(shouldRun(on))
    }

    @Test
    fun doesNotRunTwiceInTheSameDay() {
        val justBackedUp = on.copy(lastSuccessAt = now - 1.hours.inWholeMilliseconds)

        assertFalse(shouldRun(justBackedUp))
    }

    @Test
    fun runsAgainOnceTheDayHasPassed() {
        val yesterday = on.copy(lastSuccessAt = now - 25.hours.inWholeMilliseconds)

        assertTrue(shouldRun(yesterday))
    }

    /** The window is a little under a day, so a job that drifts later never skips a whole day. */
    @Test
    fun theGuardIsALittleUnderADay() {
        val almostADay = on.copy(lastSuccessAt = now - 21.hours.inWholeMilliseconds)

        assertTrue(shouldRun(almostADay))
    }

    /** A failure does not hold the next attempt back — only a success starts the quiet period. */
    @Test
    fun aRecentFailureDoesNotBlockTheNextAttempt() {
        val failedMinutesAgo = on.copy(lastFailureAt = now - 1.hours.inWholeMilliseconds)

        assertTrue(shouldRun(failedMinutesAgo))
    }
}
