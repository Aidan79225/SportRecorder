package com.crazystudio.sportrecorder.backup

import android.app.PendingIntent

/** Thrown when Drive authorization needs user consent; [pendingIntent] launches the consent UI. */
class BackupAuthorizationRequiredException(val pendingIntent: PendingIntent) :
    Exception("Google Drive authorization required")
