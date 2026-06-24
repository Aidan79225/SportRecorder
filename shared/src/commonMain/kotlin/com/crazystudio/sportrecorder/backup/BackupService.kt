package com.crazystudio.sportrecorder.backup

import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.reminder.RemindersRescheduler
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
    private val rescheduler: RemindersRescheduler,
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

    /** Committed snapshots, newest-first. */
    suspend fun listSnapshots(): List<SnapshotInfo> = store.listSnapshots()

    /**
     * Replace local data with snapshot [snapshotId]. Validates the schema, downloads everything,
     * and only then swaps local data — so a failed download or an unreadable schema leaves the
     * device's current data untouched.
     */
    suspend fun restore(snapshotId: String) {
        val json = store.downloadManifest(snapshotId)
        val doc = BackupJson.decodeFromString(BackupDocument.serializer(), json)
        if (doc.schemaVersion > BackupDocument.SCHEMA_VERSION) {
            throw BackupSchemaTooNewException(doc.schemaVersion)
        }
        // Download-all-then-swap: a throw here leaves local data intact.
        store.downloadPhotos(snapshotId)

        eatRepo.replaceAll(doc.meals.map { it.toDomain() })
        fastingRepo.replaceAllCustom(doc.fastingTypes.map { it.toDomain() })
        settingsRepo.setSelection(
            FastingWindow(doc.dietSettings.fastingHours, doc.dietSettings.eatingHours),
        )
        val prefs = doc.reminderPrefs
        prefsRepo.setWindowClosingEnabled(prefs.windowClosingEnabled)
        prefsRepo.setFastCompleteEnabled(prefs.fastCompleteEnabled)
        prefsRepo.setLeadMinutes(prefs.leadMinutes)
        prefsRepo.setQuietHoursEnabled(prefs.quietHoursEnabled)
        prefsRepo.setQuietHours(prefs.quietStartMinutes, prefs.quietEndMinutes)

        rescheduler.reschedule()
    }

    companion object {
        /** Retain the newest N snapshots so an accidental empty backup can't destroy the only good one. */
        const val KEEP_LAST = 3
    }
}
