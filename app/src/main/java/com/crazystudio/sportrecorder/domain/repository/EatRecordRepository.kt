package com.crazystudio.sportrecorder.domain.repository

import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import kotlinx.coroutines.flow.Flow

interface EatRecordRepository {
    /** All records, newest first, each with its photos. */
    fun observeAll(): Flow<List<EatRecord>>

    /** Records with `after < time < before`, ascending, WITHOUT photos (history/window use). */
    fun observeInWindow(after: Long, before: Long): Flow<List<EatRecord>>

    suspend fun findById(id: Int): EatRecord?

    /**
     * Insert (record.id == 0) or update (record.id > 0) the row and reconcile photos:
     * insert [newPhotoFileNames], delete [removedPhotos] (rows + files). Returns the record id.
     * The record's own `photos` field is ignored here.
     */
    suspend fun save(
        record: EatRecord,
        newPhotoFileNames: List<String>,
        removedPhotos: List<EatPhoto>,
    ): Int

    /** Delete the record, its photo rows, and its photo files. */
    suspend fun delete(recordId: Int)
}
