package com.crazystudio.sportrecorder.backup

/** Thrown when a snapshot's schemaVersion ([found]) is newer than this app understands. */
class BackupSchemaTooNewException(val found: Int) :
    Exception("Backup schema v$found is newer than supported (${BackupDocument.SCHEMA_VERSION}); update the app to restore it.")
