package com.crazystudio.sportrecorder.backup

import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.model.Venue
import com.crazystudio.sportrecorder.domain.model.VenueName
import com.crazystudio.sportrecorder.domain.reminder.RemindersRescheduler
import com.crazystudio.sportrecorder.domain.repository.DietSettingsRepository
import com.crazystudio.sportrecorder.domain.repository.EatRecordRepository
import com.crazystudio.sportrecorder.domain.repository.FastingTypeRepository
import com.crazystudio.sportrecorder.domain.repository.ReminderPreferencesRepository
import com.crazystudio.sportrecorder.domain.repository.VenueRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * Orchestrates backup. Reads the current data via the repositories, builds a [BackupDocument],
 * and hands the JSON + referenced photo names to the [store]. The store owns the manifest-last
 * commit and incremental photo skip; this service just snapshots and prunes.
 */
class BackupService(
    private val eatRepo: EatRecordRepository,
    private val venueRepo: VenueRepository,
    private val fastingRepo: FastingTypeRepository,
    private val settingsRepo: DietSettingsRepository,
    private val prefsRepo: ReminderPreferencesRepository,
    private val store: BackupStore,
    private val rescheduler: RemindersRescheduler,
    private val appVersionName: String,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    /** Build a snapshot from current data, upload it, prune to the last [KEEP_LAST]. */
    suspend fun backup(progress: BackupProgress = BackupProgress.None): SnapshotInfo =
        backupInternal(prune = true, progress = progress)

    private suspend fun backupInternal(prune: Boolean, progress: BackupProgress): SnapshotInfo {
        progress.report(BackupStep.Preparing, 0, 0)
        val meals = eatRepo.observeAll().first()
        val venues = venueRepo.observeAll().first()
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
            venues = venues.map { it.toBackup() },
        )
        val json = BackupJson.encodeToString(BackupDocument.serializer(), doc)
        val photoNames = meals.flatMap { meal -> meal.photos.map { it.fileName } }.distinct()

        val info = store.uploadSnapshot(json, photoNames, progress)
        if (prune) {
            progress.report(BackupStep.Pruning, 0, 0)
            store.prune(KEEP_LAST)
        }
        return info
    }

    /** Committed snapshots, newest-first. */
    suspend fun listSnapshots(): List<SnapshotInfo> = store.listSnapshots()

    /**
     * Replace local data with snapshot [snapshotId]. Validates the schema, **backs up the current
     * device data first** (so the restore is reversible), downloads everything, and only then swaps
     * local data — so a failed or cancelled download leaves the device's current data untouched.
     *
     * @throws BackupSchemaTooNewException if the snapshot is from a newer app version.
     * @throws SafetyBackupFailedException if the current data could not be backed up first.
     */
    suspend fun restore(snapshotId: String, progress: BackupProgress = BackupProgress.None) {
        progress.report(BackupStep.DownloadingManifest, 0, 0)
        val json = store.downloadManifest(snapshotId)
        val doc = BackupJson.decodeFromString(BackupDocument.serializer(), json)
        if (doc.schemaVersion > BackupDocument.SCHEMA_VERSION) {
            throw BackupSchemaTooNewException(doc.schemaVersion)
        }

        // Safety net: keep what is on the device as its own snapshot before overwriting it.
        // Never prune here — with KEEP_LAST snapshots present, pruning could delete the very
        // snapshot we are about to restore. The next regular backup prunes as usual.
        // If the safety net cannot be made, stop here with a distinct error: nothing has been
        // downloaded or applied yet, so the device's data is exactly as it was.
        if (eatRepo.observeAll().first().isNotEmpty()) {
            runCatching {
                backupInternal(prune = false) { _, done, total ->
                    progress.report(BackupStep.SafetyBackup, done, total)
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                throw SafetyBackupFailedException(error)
            }
        }

        // Download-all-then-swap: a throw or cancel here leaves local data intact.
        val photoNames = doc.meals.flatMap { meal -> meal.photos.map { it.fileName } }.distinct()
        store.downloadPhotos(snapshotId, photoNames, progress)

        // Once we start writing, finish: a half-applied restore is the one state we must never leave.
        withContext(NonCancellable) {
            progress.report(BackupStep.Applying, 0, 0)
            apply(doc)
        }
    }

    private suspend fun apply(doc: BackupDocument) {
        // Venues first. Meals reference their venue by NAME because a restore does not preserve
        // ids: Room hands out fresh ones. So insert the venues, read them back to learn the ids
        // they have on THIS device, and attach those objects to the meals before they are written
        // — the venueId stored on each meal then always points at a venue that exists here.
        venueRepo.replaceAll(doc.venues.map { Venue(0, it.name, it.lat, it.lng, it.lastUsedAt) })
        val local = venueRepo.observeAll().first()
        eatRepo.replaceAll(
            doc.meals.map { meal ->
                // A name the snapshot's venues do not list yields no venue, never a dangling id.
                val venue = meal.venueName?.let { name -> local.firstOrNull { VenueName.sameAs(it.name, name) } }
                meal.toDomain().copy(venue = venue)
            },
        )
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
        /**
         * Retain the newest N snapshots so an accidental empty backup can't destroy the only good
         * one. Raised from 3 when automatic backup landed: a daily job would otherwise push every
         * older snapshot out within three days, and「還原到上週」would stop being possible. Seven
         * gives a week of history whether the snapshots came from the job or from the user.
         */
        const val KEEP_LAST = 7
    }
}
