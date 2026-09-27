package com.crazystudio.sportrecorder.backup

/**
 * Thrown by a restore when the safety snapshot of the device's current data could not be uploaded
 * (Drive full, a local photo file missing…). Nothing was downloaded or applied: local data is
 * exactly as it was, and the user is told why the restore did not happen.
 */
class SafetyBackupFailedException(cause: Throwable) :
    RuntimeException("Could not back up the current device data before restoring", cause)
