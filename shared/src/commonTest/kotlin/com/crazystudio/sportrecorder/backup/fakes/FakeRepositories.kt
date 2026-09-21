package com.crazystudio.sportrecorder.backup.fakes

import com.crazystudio.sportrecorder.domain.model.CustomFastingType
import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.reminder.ReminderPrefs
import com.crazystudio.sportrecorder.domain.reminder.ReminderScheduler
import com.crazystudio.sportrecorder.domain.reminder.RemindersRescheduler
import com.crazystudio.sportrecorder.domain.reminder.ScheduledReminder
import com.crazystudio.sportrecorder.domain.repository.DietSettingsRepository
import com.crazystudio.sportrecorder.domain.repository.EatRecordRepository
import com.crazystudio.sportrecorder.domain.repository.FastingTypeRepository
import com.crazystudio.sportrecorder.domain.repository.ReminderPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [EatRecordRepository] that really persists, so whole journeys (save → observe →
 * edit → delete) can be written against it.
 *
 * It mirrors the contracts `EatRecordRepositoryImpl` documents:
 * - [observeAll] is newest-first (`ORDER BY time DESC`), [observeInWindow] is ascending;
 * - [save] inserts when `record.id == 0` (assigning the next id) and updates otherwise,
 *   reconciling photos from `newPhotoFileNames` / `removedPhotos` and ignoring `record.photos`;
 * - [replaceAll] swaps the whole table.
 *
 * [state] is the raw, unordered backing store — assert through the flows for ordering.
 */
class FakeEatRecordRepository(initial: List<EatRecord> = emptyList()) : EatRecordRepository {
    val state = MutableStateFlow(initial)

    private var nextRecordId: Int = (initial.maxOfOrNull { it.id } ?: 0) + 1
    private var nextPhotoId: Int =
        (initial.flatMap { it.photos }.maxOfOrNull { it.id } ?: 0) + 1

    /** Timestamp stamped onto photos inserted by [save]; the real repo uses the wall clock. */
    var photoCreatedAt: Long = 0L

    override fun observeAll(): Flow<List<EatRecord>> =
        state.map { records -> records.sortedByDescending { it.time } }

    override fun observeInWindow(after: Long, before: Long): Flow<List<EatRecord>> =
        state.map { records -> records.sortedBy { it.time } }

    override suspend fun findById(id: Int): EatRecord? = state.value.firstOrNull { it.id == id }

    override suspend fun save(
        record: EatRecord,
        newPhotoFileNames: List<String>,
        removedPhotos: List<EatPhoto>,
    ): Int {
        val newPhotos = newPhotoFileNames.map { EatPhoto(nextPhotoId++, it, photoCreatedAt) }
        return if (record.id > 0) update(record, newPhotos, removedPhotos) else insert(record, newPhotos)
    }

    private fun insert(record: EatRecord, newPhotos: List<EatPhoto>): Int {
        val id = nextRecordId++
        state.value = state.value + record.copy(id = id, photos = newPhotos)
        return id
    }

    private fun update(record: EatRecord, newPhotos: List<EatPhoto>, removed: List<EatPhoto>): Int {
        val removedIds = removed.map { it.id }.toSet()
        state.value = state.value.map { existing ->
            if (existing.id != record.id) {
                existing
            } else {
                record.copy(photos = existing.photos.filterNot { it.id in removedIds } + newPhotos)
            }
        }
        return record.id
    }

    override suspend fun delete(recordId: Int) {
        state.value = state.value.filterNot { it.id == recordId }
    }

    override suspend fun replaceAll(records: List<EatRecord>) {
        state.value = records
        nextRecordId = (records.maxOfOrNull { it.id } ?: 0) + 1
        nextPhotoId = (records.flatMap { it.photos }.maxOfOrNull { it.id } ?: 0) + 1
    }
}

/**
 * In-memory [FastingTypeRepository]. [add] prepends, so [observeRecentCustomTypes] is
 * newest-first exactly like `FastingTypeDao.flowLast` (`ORDER BY timestamp DESC`).
 */
class FakeFastingTypeRepository(initial: List<CustomFastingType> = emptyList()) : FastingTypeRepository {
    val state = MutableStateFlow(initial)
    override fun observeRecentCustomTypes(): Flow<List<CustomFastingType>> = state
    override suspend fun exists(window: FastingWindow): Boolean =
        state.value.any { it.fastingHours == window.fastingHours && it.eatingHours == window.eatingHours }
    override suspend fun add(window: FastingWindow, name: String?) {
        state.value = listOf(CustomFastingType(window.fastingHours, window.eatingHours, name)) + state.value
    }
    override suspend fun replaceAllCustom(types: List<CustomFastingType>) { state.value = types }
}

class FakeDietSettingsRepository(
    initial: DietSettings = DietSettings(fastingHours = 16, eatingHours = 8),
) : DietSettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: Flow<DietSettings> = state
    override suspend fun setSelection(window: FastingWindow) {
        state.value = DietSettings(window.fastingHours, window.eatingHours)
    }
}

class FakeReminderPreferencesRepository(
    initial: ReminderPrefs = ReminderPrefs(),
) : ReminderPreferencesRepository {
    val state = MutableStateFlow(initial)
    override val prefs: Flow<ReminderPrefs> = state
    override suspend fun setWindowClosingEnabled(enabled: Boolean) {
        state.value = state.value.copy(windowClosingEnabled = enabled)
    }
    override suspend fun setFastCompleteEnabled(enabled: Boolean) {
        state.value = state.value.copy(fastCompleteEnabled = enabled)
    }
    override suspend fun setLeadMinutes(minutes: Long) {
        state.value = state.value.copy(leadMinutes = minutes)
    }
    override suspend fun setQuietHoursEnabled(enabled: Boolean) {
        state.value = state.value.copy(quietHoursEnabled = enabled)
    }
    override suspend fun setQuietHours(startMinutes: Int, endMinutes: Int) {
        state.value = state.value.copy(quietStartMinutes = startMinutes, quietEndMinutes = endMinutes)
    }
}

class FakeRemindersRescheduler : RemindersRescheduler {
    var rescheduleCount = 0
        private set
    override suspend fun reschedule() { rescheduleCount++ }
}

/** Captures every plan handed to the scheduler; [lastScheduled] is the currently-armed set. */
class FakeReminderScheduler : ReminderScheduler {
    val plans = mutableListOf<List<ScheduledReminder>>()

    val lastScheduled: List<ScheduledReminder> get() = plans.lastOrNull() ?: emptyList()
    val scheduleCount: Int get() = plans.size

    override fun schedule(reminders: List<ScheduledReminder>) {
        plans.add(reminders)
    }
}
