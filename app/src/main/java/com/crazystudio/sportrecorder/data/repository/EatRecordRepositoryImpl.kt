package com.crazystudio.sportrecorder.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.crazystudio.sportrecorder.dao.EatTimeDao
import com.crazystudio.sportrecorder.dao.PhotoDao
import com.crazystudio.sportrecorder.data.mapper.toDomain
import com.crazystudio.sportrecorder.database.AppDatabase
import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.repository.EatRecordRepository
import com.crazystudio.sportrecorder.entity.EatTime
import com.crazystudio.sportrecorder.entity.Photo
import com.crazystudio.sportrecorder.util.PhotoStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class EatRecordRepositoryImpl constructor(
    private val appContext: Context,
    private val appDatabase: AppDatabase,
    private val eatTimeDao: EatTimeDao,
    private val photoDao: PhotoDao,
) : EatRecordRepository {

    override fun observeAll(): Flow<List<EatRecord>> =
        eatTimeDao.flowAllWithPhotos().map { list -> list.map { it.toDomain() } }

    override fun observeInWindow(after: Long, before: Long): Flow<List<EatRecord>> =
        eatTimeDao.flowByTimeInterval(before = before, after = after)
            .map { list -> list.map { it.toDomain() } }

    override suspend fun findById(id: Int): EatRecord? =
        eatTimeDao.findWithPhotosById(id)?.toDomain()

    override suspend fun save(
        record: EatRecord,
        newPhotoFileNames: List<String>,
        removedPhotos: List<EatPhoto>,
    ): Int {
        val recordId = appDatabase.withTransaction {
            val id = if (record.id > 0) {
                eatTimeDao.update(
                    EatTime(
                        id = record.id,
                        time = record.time,
                        lat = record.location?.lat,
                        lng = record.location?.lng,
                        note = record.note,
                    ),
                )
                removedPhotos.forEach {
                    photoDao.delete(
                        Photo(id = it.id, eatTimeId = record.id, fileName = it.fileName, createdAt = it.createdAt),
                    )
                }
                record.id
            } else {
                eatTimeDao.insert(
                    EatTime(
                        time = record.time,
                        lat = record.location?.lat,
                        lng = record.location?.lng,
                        note = record.note,
                    ),
                ).toInt()
            }
            val now = System.currentTimeMillis()
            newPhotoFileNames.forEach { name ->
                photoDao.insert(Photo(eatTimeId = id, fileName = name, createdAt = now))
            }
            id
        }
        // File deletes happen only after the DB transaction succeeds (non-transactional).
        removedPhotos.forEach { PhotoStorage.deleteByName(appContext, it.fileName) }
        return recordId
    }

    override suspend fun delete(recordId: Int) {
        val photos = photoDao.findByEatTimeId(recordId)
        appDatabase.withTransaction {
            photoDao.deleteByEatTimeId(recordId)
            eatTimeDao.deleteById(recordId)
        }
        // File deletes happen only after the DB transaction succeeds (non-transactional).
        photos.forEach { PhotoStorage.deleteByName(appContext, it.fileName) }
    }
}
