package com.crazystudio.sportrecorder.fake

import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.repository.EatRecordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory fake. [observeAll] and [observeInWindow] both surface the same backing
 * list (window filtering is exercised in use-case tests, not here). [delete] is recorded.
 */
class FakeEatRecordRepository(
    initial: List<EatRecord> = emptyList(),
) : EatRecordRepository {
    private val state = MutableStateFlow(initial)
    val deletedIds = mutableListOf<Int>()

    fun setRecords(records: List<EatRecord>) {
        state.value = records
    }

    override fun observeAll(): Flow<List<EatRecord>> = state

    override fun observeInWindow(after: Long, before: Long): Flow<List<EatRecord>> = state

    override suspend fun findById(id: Int): EatRecord? = state.value.firstOrNull { it.id == id }

    override suspend fun save(
        record: EatRecord,
        newPhotoFileNames: List<String>,
        removedPhotos: List<EatPhoto>,
    ): Int = record.id

    override suspend fun delete(recordId: Int) {
        deletedIds.add(recordId)
    }

    override suspend fun replaceAll(records: List<EatRecord>) {
        state.value = records
    }
}
