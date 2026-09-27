package com.crazystudio.sportrecorder.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.crazystudio.sportrecorder.dao.PhotoDao
import com.crazystudio.sportrecorder.data.repository.EatRecordRepositoryImpl
import com.crazystudio.sportrecorder.data.repository.FastingTypeRepositoryImpl
import com.crazystudio.sportrecorder.database.AppDatabase
import com.crazystudio.sportrecorder.entity.Photo

/** Recording [PhotoFileStore]: which files the repository asked to delete, in order. */
class RecordingPhotoFileStore : PhotoFileStore {
    val deleted = mutableListOf<String>()
    override fun delete(fileName: String) { deleted.add(fileName) }
}

/** Delegating [PhotoDao] that throws on the [failOnInsertNumber]-th insert (1-based); 0 = never. */
class ThrowingPhotoDao(private val real: PhotoDao, var failOnInsertNumber: Int = 0) : PhotoDao by real {
    private var inserts = 0
    override suspend fun insert(photo: Photo): Long {
        inserts++
        if (inserts == failOnInsertNumber) throw IllegalStateException("simulated photo insert failure")
        return real.insert(photo)
    }
}

/** In-memory Room with the real repositories on top; only the file store is a recorder. */
class RoomHarness {
    val context: Context = ApplicationProvider.getApplicationContext()
    val db: AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    val files = RecordingPhotoFileStore()
    val photoDao = ThrowingPhotoDao(db.getPhotoDao())
    val eatRepo = EatRecordRepositoryImpl(db, db.getEatTimeDao(), photoDao, files)
    val fastingRepo = FastingTypeRepositoryImpl(db, db.getFastingTypeDao())
    fun close() = db.close()
}
