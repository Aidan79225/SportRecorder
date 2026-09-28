package com.crazystudio.sportrecorder.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazystudio.sportrecorder.domain.model.CustomFastingType
import com.crazystudio.sportrecorder.domain.model.FastingWindow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FastingTypeRepositoryRoomTest {
    private lateinit var h: RoomHarness

    @Before fun setUp() { h = RoomHarness() }
    @After fun tearDown() { h.close() }

    @Test fun observeRecentCustomTypes_isNewestFirst_andCappedAtTen() = runBlocking {
        (1..12).forEach {
            h.fastingRepo.add(FastingWindow(10L + it, 14L - it), "t$it")
            // `add` stamps timestamp = now-millis and the query orders by timestamp DESC with no
            // tiebreaker; sleep so back-to-back adds land in strictly increasing milliseconds and
            // the positional assertions below never flake on fast hardware.
            Thread.sleep(2)
        }
        val types = h.fastingRepo.observeRecentCustomTypes().first()
        assertEquals(10, types.size)
        assertEquals("t12", types.first().name)
        assertEquals("t3", types.last().name)
    }

    @Test fun exists_matchesHoursOnly() = runBlocking {
        h.fastingRepo.add(FastingWindow(18, 6), "named")
        assertTrue(h.fastingRepo.exists(FastingWindow(18, 6)))
        assertFalse(h.fastingRepo.exists(FastingWindow(16, 8)))
    }

    @Test fun replaceAllCustom_swapsTheTable() = runBlocking {
        h.fastingRepo.add(FastingWindow(18, 6), "old")
        h.fastingRepo.replaceAllCustom(listOf(CustomFastingType(20, 4, "new-a"), CustomFastingType(14, 10, null)))
        val names = h.fastingRepo.observeRecentCustomTypes().first().map { it.name }
        assertEquals(2, names.size)
        assertTrue("old" !in names)
        assertTrue("new-a" in names)
    }
}
