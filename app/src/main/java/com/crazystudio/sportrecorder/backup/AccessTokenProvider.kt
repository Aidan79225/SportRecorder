package com.crazystudio.sportrecorder.backup

/** Supplies a live Drive access token. Split from [GoogleBackupAuth] so the store is testable without Play services. */
fun interface AccessTokenProvider {
    suspend fun accessToken(): String
}
