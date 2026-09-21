package com.crazystudio.sportrecorder.flow

import com.crazystudio.sportrecorder.backup.fakes.FakeEatRecordRepository
import com.crazystudio.sportrecorder.backup.fakes.FakeRemindersRescheduler
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.domain.usecase.DeleteEatRecordUseCase
import com.crazystudio.sportrecorder.domain.usecase.LoadEatRecordUseCase
import com.crazystudio.sportrecorder.domain.usecase.ObserveEatRecordsUseCase
import com.crazystudio.sportrecorder.domain.usecase.SaveEatRecordUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Capture journey — 新增 / 編輯 / 刪除一筆進食紀錄, driven through the real use cases over an
 * in-memory repository. Pins that a captured moment comes back exactly as it was recorded, and
 * that every accepted mutation re-arms the reminders.
 */
class CaptureFlowTest {

    private val f = 1_700_000_000_000L
    private fun h(n: Long) = n * 3_600_000L

    private class Harness {
        val repo = FakeEatRecordRepository()
        val rescheduler = FakeRemindersRescheduler()
        val save = SaveEatRecordUseCase(repo, rescheduler)
        val delete = DeleteEatRecordUseCase(repo, rescheduler)
        val load = LoadEatRecordUseCase(repo)
        val observe = ObserveEatRecordsUseCase(repo)
    }

    private fun draft(
        time: Long,
        note: String? = null,
        location: GeoPoint? = null,
    ) = EatRecord(id = 0, time = time, location = location, note = note, photos = emptyList())

    @Test
    fun emptyState_hasNoRecordsAndNoReminderWork() = runTest {
        val app = Harness()

        assertEquals(emptyList<EatRecord>(), app.observe().first())
        assertEquals(0, app.rescheduler.rescheduleCount)
    }

    @Test
    fun save_capturesTimeNoteLocationAndPhoto() = runTest {
        val app = Harness()

        val ok = app.save(
            draft(time = f, note = "午餐", location = GeoPoint(25.033, 121.565)),
            listOf("meal-1.webp"),
            emptyList(),
            now = f + h(1),
        )

        assertTrue(ok)
        val saved = app.observe().first().single()
        assertEquals(f, saved.time)
        assertEquals("午餐", saved.note)
        assertEquals(GeoPoint(25.033, 121.565), saved.location)
        assertEquals(listOf("meal-1.webp"), saved.photos.map { it.fileName })
        assertEquals(1, app.rescheduler.rescheduleCount)
    }

    @Test
    fun save_bareMeal_needsNothingButATime() = runTest {
        // 記錄不該是負擔: note / photo / location are all optional by design.
        val app = Harness()

        assertTrue(app.save(draft(time = f), emptyList(), emptyList(), now = f))

        val saved = app.observe().first().single()
        assertNull(saved.note)
        assertNull(saved.location)
        assertTrue(saved.photos.isEmpty())
    }

    @Test
    fun observe_listsNewestFirst() = runTest {
        val app = Harness()

        app.save(draft(time = f, note = "早餐"), emptyList(), emptyList(), now = f + h(9))
        app.save(draft(time = f + h(6), note = "晚餐"), emptyList(), emptyList(), now = f + h(9))

        assertEquals(listOf("晚餐", "早餐"), app.observe().first().map { it.note })
    }

    @Test
    fun edit_updatesFieldsAndReconcilesPhotos() = runTest {
        val app = Harness()
        app.save(draft(time = f, note = "午餐"), listOf("old.webp", "keep.webp"), emptyList(), now = f + h(1))
        val original = app.observe().first().single()
        val toRemove = original.photos.single { it.fileName == "old.webp" }

        val ok = app.save(
            original.copy(time = f + h(1), note = "午餐(補記)"),
            listOf("new.webp"),
            listOf(toRemove),
            now = f + h(2),
        )

        assertTrue(ok)
        val edited = app.observe().first().single()
        assertEquals(original.id, edited.id)
        assertEquals(f + h(1), edited.time)
        assertEquals("午餐(補記)", edited.note)
        assertEquals(listOf("keep.webp", "new.webp"), edited.photos.map { it.fileName })
        assertEquals(2, app.rescheduler.rescheduleCount)
    }

    @Test
    fun edit_clearsOptionalFields() = runTest {
        val app = Harness()
        app.save(draft(time = f, note = "n", location = GeoPoint(1.0, 2.0)), emptyList(), emptyList(), now = f)
        val original = app.observe().first().single()

        app.save(original.copy(note = null, location = null), emptyList(), emptyList(), now = f)

        val edited = app.observe().first().single()
        assertNull(edited.note)
        assertNull(edited.location)
    }

    @Test
    fun load_unknownId_isNull() = runTest {
        val app = Harness()
        app.save(draft(time = f), emptyList(), emptyList(), now = f)

        assertNull(app.load(9_999))
    }

    @Test
    fun load_returnsTheStoredRecord() = runTest {
        val app = Harness()
        app.save(draft(time = f, note = "n"), listOf("p.webp"), emptyList(), now = f)
        val id = app.observe().first().single().id

        val loaded = app.load(id)

        assertEquals("n", loaded?.note)
        assertEquals(listOf("p.webp"), loaded?.photos?.map { it.fileName })
    }

    @Test
    fun save_futureTime_isRejectedAndChangesNothing() = runTest {
        val app = Harness()

        val ok = app.save(draft(time = f + h(1)), listOf("nope.webp"), emptyList(), now = f)

        assertFalse(ok)
        assertEquals(emptyList<EatRecord>(), app.observe().first())
        assertEquals(0, app.rescheduler.rescheduleCount)
    }

    @Test
    fun save_exactlyNow_isAccepted() = runTest {
        val app = Harness()

        assertTrue(app.save(draft(time = f), emptyList(), emptyList(), now = f))
        assertEquals(1, app.observe().first().size)
    }

    @Test
    fun delete_removesTheRecordAndRearmsReminders() = runTest {
        val app = Harness()
        app.save(draft(time = f, note = "a"), emptyList(), emptyList(), now = f)
        app.save(draft(time = f + h(1), note = "b"), emptyList(), emptyList(), now = f + h(1))
        val first = app.observe().first().single { it.note == "a" }

        app.delete(first.id)

        assertEquals(listOf("b"), app.observe().first().map { it.note })
        assertEquals(3, app.rescheduler.rescheduleCount) // 2 saves + 1 delete
    }

    @Test
    fun delete_unknownId_leavesDataIntact() = runTest {
        val app = Harness()
        app.save(draft(time = f, note = "a"), emptyList(), emptyList(), now = f)

        app.delete(9_999)

        assertEquals(listOf("a"), app.observe().first().map { it.note })
    }

    @Test
    fun deletingEverything_returnsToEmptyState() = runTest {
        val app = Harness()
        app.save(draft(time = f), emptyList(), emptyList(), now = f)
        val only = app.observe().first().single()

        app.delete(only.id)

        assertEquals(emptyList<EatRecord>(), app.observe().first())
    }
}
