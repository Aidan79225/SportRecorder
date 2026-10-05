package com.crazystudio.sportrecorder.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazystudio.sportrecorder.data.repository.VenueRepositoryImpl
import com.crazystudio.sportrecorder.entity.EatTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs against real Room: the merge is SQL, so a fake would prove nothing. */
@RunWith(AndroidJUnit4::class)
class VenueRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: VenueRepositoryImpl

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext()).build()
        repo = VenueRepositoryImpl(db, db.getVenueDao())
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
}
