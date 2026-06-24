package com.crazystudio.sportrecorder.backup.fakes

import com.crazystudio.sportrecorder.domain.model.CustomFastingType
import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.reminder.ReminderPrefs
import com.crazystudio.sportrecorder.domain.reminder.RemindersRescheduler
import com.crazystudio.sportrecorder.domain.repository.DietSettingsRepository
import com.crazystudio.sportrecorder.domain.repository.EatRecordRepository
import com.crazystudio.sportrecorder.domain.repository.FastingTypeRepository
import com.crazystudio.sportrecorder.domain.repository.ReminderPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeEatRecordRepository(initial: List<EatRecord> = emptyList()) : EatRecordRepository {
    val state = MutableStateFlow(initial)
    override fun observeAll(): Flow<List<EatRecord>> = state
    override fun observeInWindow(after: Long, before: Long): Flow<List<EatRecord>> = state
    override suspend fun findById(id: Int): EatRecord? = state.value.firstOrNull { it.id == id }
    override suspend fun save(
        record: EatRecord,
        newPhotoFileNames: List<String>,
        removedPhotos: List<EatPhoto>,
    ): Int = record.id
    override suspend fun delete(recordId: Int) { state.value = state.value.filterNot { it.id == recordId } }
    override suspend fun replaceAll(records: List<EatRecord>) { state.value = records }
}

class FakeFastingTypeRepository(initial: List<CustomFastingType> = emptyList()) : FastingTypeRepository {
    val state = MutableStateFlow(initial)
    override fun observeRecentCustomTypes(): Flow<List<CustomFastingType>> = state
    override suspend fun exists(window: FastingWindow): Boolean =
        state.value.any { it.fastingHours == window.fastingHours && it.eatingHours == window.eatingHours }
    override suspend fun add(window: FastingWindow, name: String?) {
        state.value = state.value + CustomFastingType(window.fastingHours, window.eatingHours, name)
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
