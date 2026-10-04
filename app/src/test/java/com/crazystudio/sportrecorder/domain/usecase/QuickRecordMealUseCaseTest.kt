package com.crazystudio.sportrecorder.domain.usecase

import com.crazystudio.sportrecorder.fake.FakeEatRecordRepository
import com.crazystudio.sportrecorder.fake.FakeRemindersRescheduler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The quick-capture write path, exercised with no UI at all — which is the point: the quick
 * settings tile calls exactly this, so a meal logged from the notification shade is the same
 * record, saved the same way, as one logged inside the app.
 */
class QuickRecordMealUseCaseTest {

    private val repo = FakeEatRecordRepository()
    private val rescheduler = FakeRemindersRescheduler()
    private val quickRecord = QuickRecordMealUseCase(SaveEatRecordUseCase(repo, rescheduler))

    private val now = 1_767_000_000_000L // 2026-01-01, a fixed instant

    @Test
    fun recordsTheMomentWithNothingAttached() = runTest {
        val at = quickRecord(now)

        assertEquals(now, at)
        val saved = repo.stored.single()
        assertEquals(now, saved.time)
        assertNull(saved.note)
        assertNull(saved.location)
        assertTrue(saved.photos.isEmpty())
    }

    /** A meal shifts the eating window, so the reminders must be re-armed — same as in-app saves. */
    @Test
    fun remindersAreRescheduled() = runTest {
        quickRecord(now)

        assertEquals(1, rescheduler.rescheduleCount)
    }

    @Test
    fun eachTapIsItsOwnMeal() = runTest {
        quickRecord(now)
        quickRecord(now + 60_000)

        assertEquals(listOf(now, now + 60_000), repo.stored.map { it.time })
    }
}
