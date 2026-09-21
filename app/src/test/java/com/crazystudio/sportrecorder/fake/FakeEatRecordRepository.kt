package com.crazystudio.sportrecorder.fake

import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.repository.EatRecordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory fake that really persists, so whole capture journeys can be driven through it.
 *
 * [save] mirrors `EatRecordRepositoryImpl`: it inserts when `record.id == 0` (assigning the next
 * id) and updates otherwise, reconciling photos from `newPhotoFileNames` / `removedPhotos` and
 * ignoring `record.photos`. [delete] removes the row **and** records the id in [deletedIds].
 *
 * [observeAll] and [observeInWindow] both surface the backing list in insertion order — the real
 * repository's `ORDER BY` is a Room concern and is pinned in `:shared`'s flow tests instead.
 */
class FakeEatRecordRepository(
    initial: List<EatRecord> = emptyList(),
) : EatRecordRepository {
    private val state = MutableStateFlow(initial)
    val deletedIds = mutableListOf<Int>()

    private var nextRecordId: Int = (initial.maxOfOrNull { it.id } ?: 0) + 1
    private var nextPhotoId: Int = (initial.flatMap { it.photos }.maxOfOrNull { it.id } ?: 0) + 1

    /** Timestamp stamped onto photos inserted by [save]; the real repository uses the wall clock. */
    var photoCreatedAt: Long = 0L

    /** The records currently stored, for direct assertions. */
    val stored: List<EatRecord> get() = state.value

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
    ): Int {
        val newPhotos = newPhotoFileNames.map { EatPhoto(nextPhotoId++, it, photoCreatedAt) }
        return if (record.id > 0) {
            update(record, newPhotos, removedPhotos)
        } else {
            insert(record, newPhotos)
        }
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
        deletedIds.add(recordId)
        state.value = state.value.filterNot { it.id == recordId }
    }

    override suspend fun replaceAll(records: List<EatRecord>) {
        state.value = records
        nextRecordId = (records.maxOfOrNull { it.id } ?: 0) + 1
        nextPhotoId = (records.flatMap { it.photos }.maxOfOrNull { it.id } ?: 0) + 1
    }
}
