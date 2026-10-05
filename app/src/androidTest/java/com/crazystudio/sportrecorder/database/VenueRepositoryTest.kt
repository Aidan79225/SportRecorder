package com.crazystudio.sportrecorder.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazystudio.sportrecorder.data.PhotoFileStore
import com.crazystudio.sportrecorder.data.repository.EatRecordRepositoryImpl
import com.crazystudio.sportrecorder.data.repository.VenueRepositoryImpl
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.entity.EatTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs against real Room: the merge is SQL, so a fake would prove nothing. */
@RunWith(AndroidJUnit4::class)
class VenueRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: VenueRepositoryImpl
    private lateinit var eatRepo: EatRecordRepositoryImpl

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext()).build()
        repo = VenueRepositoryImpl(db, db.getVenueDao())
        eatRepo = EatRecordRepositoryImpl(
            db,
            db.getEatTimeDao(),
            db.getPhotoDao(),
            object : PhotoFileStore { override fun delete(fileName: String) = Unit },
        )
    }

    @After fun tearDown() = db.close()

    @Test fun findOrCreate_isIdempotentAcrossCaseAndSpacing() = runBlocking {
        val a = repo.findOrCreate("Starbucks", lat = 25.0, lng = 121.0, now = 1L)
        val b = repo.findOrCreate("  starbucks ", lat = null, lng = null, now = 2L)

        assertEquals(a.id, b.id)
        assertEquals(1, repo.observeAll().first().size)
        // The first position wins; a later mention without coordinates must not erase it.
        assertEquals(25.0, repo.observeAll().first().single().lat!!, 0.0001)
    }

    @Test fun findOrCreate_foldsNonAsciiCaseToo() = runBlocking {
        // SQLite NOCASE folds ASCII only; VenueName (the spec's definition) folds all of Unicode.
        val a = repo.findOrCreate("Café", null, null, now = 1L)
        val b = repo.findOrCreate("CAFÉ", null, null, now = 2L)

        assertEquals(a.id, b.id)
        assertEquals(1, repo.observeAll().first().size)
    }

    @Test fun observeAll_putsMostRecentlyUsedFirst() = runBlocking {
        repo.findOrCreate("A", null, null, now = 1L)
        val b = repo.findOrCreate("B", null, null, now = 2L)
        repo.touch(b.id, now = 9L)

        assertEquals(listOf("B", "A"), repo.observeAll().first().map { it.name })
    }

    @Test fun rename_toAFreeName_keepsTheSameVenue() = runBlocking {
        val v = repo.findOrCreate("Starbuck", null, null, now = 1L)

        val renamed = repo.rename(v.id, "Starbucks", now = 2L)

        assertEquals(v.id, renamed.id)
        assertEquals("Starbucks", repo.observeAll().first().single().name)
    }

    @Test fun rename_ontoAnExistingName_mergesAndMovesRecords() = runBlocking {
        val typo = repo.findOrCreate("大戶屋 信義店", null, null, now = 1L)
        val good = repo.findOrCreate("大戶屋", null, null, now = 2L)
        db.getEatTimeDao().insert(EatTime(time = 100L, venueId = typo.id))
        assertEquals(1, repo.recordCount(typo.id))

        val survivor = repo.rename(typo.id, "大戶屋", now = 3L)

        assertEquals(good.id, survivor.id)
        assertEquals(1, repo.recordCount(good.id))
        assertEquals(listOf("大戶屋"), repo.observeAll().first().map { it.name })
        assertNull(db.getVenueDao().findById(typo.id))
    }

    @Test fun aSavedRecordComesBackWithItsVenue() = runBlocking {
        val venue = repo.findOrCreate("大戶屋", lat = 25.0, lng = 121.0, now = 1L)
        db.getEatTimeDao().insert(
            EatTime(time = 100L, venueId = venue.id),
        )

        val loaded = db.getEatTimeDao().flowAllWithPhotos().first().single()

        assertEquals("大戶屋", loaded.venue?.name)
        // The record's own position is untouched by the venue — two different facts.
        assertNull(loaded.eatTime.lat)
    }

    // Takeaway eaten at home: the record is where you were (home), the venue is where the food
    // came from (the restaurant). The coordinates are deliberately far apart so a mix-up shows.
    private val home = GeoPoint(lat = 24.1, lng = 120.6)

    private suspend fun restaurant() = repo.findOrCreate("大戶屋", lat = 25.0, lng = 121.5, now = 1L)

    private fun assertHomeAndVenue(record: EatRecord?, venueId: Int) {
        assertNotNull(record)
        assertEquals(venueId, record!!.venue?.id)
        assertEquals("the venue's position must not leak onto the record", home, record.location)
    }

    @Test fun save_insert_persistsVenue_andKeepsRecordLocationSeparate() = runBlocking {
        val v = restaurant()

        val id = eatRepo.save(EatRecord(0, 100L, home, null, emptyList(), venue = v), emptyList(), emptyList())

        assertHomeAndVenue(eatRepo.findById(id), v.id)
    }

    @Test fun save_update_keepsTheVenue_whenOnlyTheNoteChanges() = runBlocking {
        val v = restaurant()
        val id = eatRepo.save(EatRecord(0, 100L, home, null, emptyList(), venue = v), emptyList(), emptyList())

        // What the editor does: load, change the note, save again.
        val loaded = eatRepo.findById(id)!!
        eatRepo.save(loaded.copy(note = "edited"), emptyList(), emptyList())

        val reloaded = eatRepo.findById(id)
        assertEquals("edited", reloaded?.note)
        assertHomeAndVenue(reloaded, v.id)
    }

    @Test fun save_update_canClearTheVenue() = runBlocking {
        val v = restaurant()
        val id = eatRepo.save(EatRecord(0, 100L, home, null, emptyList(), venue = v), emptyList(), emptyList())

        eatRepo.save(eatRepo.findById(id)!!.copy(venue = null), emptyList(), emptyList())

        assertNull(eatRepo.findById(id)?.venue)
    }

    @Test fun replaceAll_restoresTheVenue() = runBlocking {
        val v = restaurant()

        eatRepo.replaceAll(listOf(EatRecord(0, 100L, home, null, emptyList(), venue = v)))

        assertHomeAndVenue(eatRepo.observeAll().first().single(), v.id)
    }
}
