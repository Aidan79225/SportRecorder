package com.crazystudio.sportrecorder.domain.usecase

import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.repository.EatRecordRepository
import com.crazystudio.sportrecorder.fake.FakeRemindersRescheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeEatRecordRepository : EatRecordRepository {
    var savedRecord: EatRecord? = null
    var saveCount = 0
    override fun observeAll(): Flow<List<EatRecord>> = TODO()
    override fun observeInWindow(after: Long, before: Long): Flow<List<EatRecord>> = TODO()
    override suspend fun findById(id: Int): EatRecord? = TODO()
    override suspend fun save(
        record: EatRecord,
        newPhotoFileNames: List<String>,
        removedPhotos: List<EatPhoto>,
    ): Int {
        savedRecord = record
        saveCount++
        return record.id
    }
    override suspend fun delete(recordId: Int) = TODO()
    override suspend fun replaceAll(records: List<EatRecord>) = TODO()
}

private fun record(time: Long) = EatRecord(id = 0, time = time, location = null, note = null, photos = emptyList())

class SaveEatRecordUseCaseTest {
    @Test fun futureTime_isRejected_andRepoNotCalled() = runTest {
        val repo = FakeEatRecordRepository()
        val rescheduler = FakeRemindersRescheduler()
        val useCase = SaveEatRecordUseCase(repo, rescheduler)
        val ok = useCase(record(time = 2_000L), emptyList(), emptyList(), now = 1_000L)
        assertFalse(ok)
        assertEquals(0, repo.saveCount)
        assertNull(repo.savedRecord)
        assertEquals(0, rescheduler.rescheduleCount)
    }

    @Test fun validTime_isSaved_andRemindersRescheduled() = runTest {
        val repo = FakeEatRecordRepository()
        val rescheduler = FakeRemindersRescheduler()
        val useCase = SaveEatRecordUseCase(repo, rescheduler)
        val ok = useCase(record(time = 500L), listOf("a.webp"), emptyList(), now = 1_000L)
        assertTrue(ok)
        assertEquals(1, repo.saveCount)
        assertEquals(500L, repo.savedRecord?.time)
        assertEquals(1, rescheduler.rescheduleCount)
    }

    @Test fun exactNowTime_isSaved() = runTest {
        // Guard is `time > now` (strict), so time == now must be accepted (boundary lock).
        val repo = FakeEatRecordRepository()
        val ok = SaveEatRecordUseCase(repo, FakeRemindersRescheduler())(
            record(time = 1_000L),
            emptyList(),
            emptyList(),
            now = 1_000L,
        )
        assertTrue(ok)
        assertEquals(1, repo.saveCount)
    }
}
