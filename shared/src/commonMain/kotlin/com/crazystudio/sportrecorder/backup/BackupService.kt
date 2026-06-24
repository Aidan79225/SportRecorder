package com.crazystudio.sportrecorder.backup

import com.crazystudio.sportrecorder.domain.repository.DietSettingsRepository
import com.crazystudio.sportrecorder.domain.repository.EatRecordRepository
import com.crazystudio.sportrecorder.domain.repository.FastingTypeRepository
import com.crazystudio.sportrecorder.domain.repository.ReminderPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlin.time.Clock

/**
 * Orchestrates backup. Reads the current data via the repositories, builds a [BackupDocument],
 * and hands the JSON + referenced photo names to the [store]. The store owns the manifest-last
 * commit and incremental photo skip; this service just snapshots and prunes.
 */
class BackupService(
    private val eatRepo: EatRecordRepository,
    private val fastingRepo: FastingTypeRepository,
    private val settingsRepo: DietSettingsRepository,
    private val prefsRepo: ReminderPreferencesRepository,
    private val store: BackupStore,
    private val appVersionName: String,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    /** Build a snapshot from current data, upload it, prune to the last [KEEP_LAST]. */
    suspend fun backup(): SnapshotInfo {
        val meals = eatRepo.observeAll().first()
        val fastingTypes = fastingRepo.observeRecentCustomTypes().first()
        val settings = settingsRepo.settings.first()
        val prefs = prefsRepo.prefs.first()

        val doc = BackupDocument(
            schemaVersion = BackupDocument.SCHEMA_VERSION,
            createdAt = now(),
            appVersionName = appVersionName,
            meals = meals.map { it.toBackup() },
            fastingTypes = fastingTypes.map { it.toBackup() },
            dietSettings = settings.toBackup(),
            reminderPrefs = prefs.toBackup(),
        )
        val json = BackupJson.encodeToString(BackupDocument.serializer(), doc)
        val photoNames = meals.flatMap { meal -> meal.photos.map { it.fileName } }.distinct()

        val info = store.uploadSnapshot(json, photoNames)
        store.prune(KEEP_LAST)
        return info
    }

    companion object {
        /** Retain the newest N snapshots so an accidental empty backup can't destroy the only good one. */
        const val KEEP_LAST = 3
    }
}
