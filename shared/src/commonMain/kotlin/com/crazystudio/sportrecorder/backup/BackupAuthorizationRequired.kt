package com.crazystudio.sportrecorder.backup

/**
 * Marks a failure that only means "the user needs to grant access again" — not a real error.
 * Platform auth implementations tag their consent-required exception with this so the shared
 * layer can offer a way back in without knowing about platform consent APIs.
 */
interface BackupAuthorizationRequired
