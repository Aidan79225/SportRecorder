package com.crazystudio.sportrecorder.backup

import kotlin.time.Duration.Companion.hours

/**
 * What the user chose about automatic backup, and how the last attempt went.
 *
 * The failure fields exist so the Settings screen can say what happened **when the user next looks**
 * — a background failure never interrupts anyone. [lastFailureNeedsSignIn] separates "Google no
 * longer trusts this token" (which the user can fix) from a network hiccup (which fixes itself).
 */
data class AutoBackupPrefs(
    val enabled: Boolean = false,
    val lastSuccessAt: Long? = null,
    val lastFailureAt: Long? = null,
    val lastFailureNeedsSignIn: Boolean = false,
)

/** The decision the background job asks before touching the network. Pure, so it is unit-tested. */
object AutoBackup {

    /**
     * A little under a day: the scheduler may run the job late, and an exact 24 h guard would then
     * push the next backup a further day out every time it drifted.
     */
    val MIN_INTERVAL = 20.hours

    fun shouldRun(prefs: AutoBackupPrefs, signedIn: Boolean, now: Long): Boolean {
        if (!prefs.enabled || !signedIn) return false
        val last = prefs.lastSuccessAt ?: return true
        return now - last >= MIN_INTERVAL.inWholeMilliseconds
    }
}
