package com.crazystudio.sportrecorder.domain.diet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class HomeTaglinePickerTest {
    private fun h(n: Long) = n * 3_600_000L
    private val idle = DietWindowState(phase = DietPhase.IDLE)

    @Test fun idle_followsTheTimeOfDay() {
        assertEquals(TaglineMood.IDLE_MORNING, HomeTaglinePicker.moodOf(idle, 5))
        assertEquals(TaglineMood.IDLE_MORNING, HomeTaglinePicker.moodOf(idle, 10))
        assertEquals(TaglineMood.IDLE_AFTERNOON, HomeTaglinePicker.moodOf(idle, 11))
        assertEquals(TaglineMood.IDLE_EVENING, HomeTaglinePicker.moodOf(idle, 17))
        assertEquals(TaglineMood.IDLE_NIGHT, HomeTaglinePicker.moodOf(idle, 22))
        assertEquals(TaglineMood.IDLE_NIGHT, HomeTaglinePicker.moodOf(idle, 0))
        assertEquals(TaglineMood.IDLE_NIGHT, HomeTaglinePicker.moodOf(idle, 4))
    }

    @Test fun eating_windsDownInTheLastHour() {
        val eating = DietWindowState(phase = DietPhase.EATING, elapsedMillis = h(3))
        assertEquals(TaglineMood.EATING, HomeTaglinePicker.moodOf(eating, 12))
        assertEquals(
            TaglineMood.EATING_WINDING_DOWN,
            HomeTaglinePicker.moodOf(eating.copy(elapsedMillis = h(1)), 12),
        )
    }

    @Test fun fasting_followsProgress() {
        fun at(p: Float) = HomeTaglinePicker.moodOf(DietWindowState(DietPhase.FASTING, ringProgress = p), 12)
        assertEquals(TaglineMood.FASTING_JUST_STARTED, at(0.05f))
        assertEquals(TaglineMood.FASTING, at(0.5f))
        assertEquals(TaglineMood.FASTING_ALMOST_THERE, at(0.9f))
    }

    @Test fun success_isSuccess() {
        assertEquals(TaglineMood.SUCCESS, HomeTaglinePicker.moodOf(DietWindowState(DietPhase.SUCCESS), 12))
    }

    @Test fun sameSeed_isStable_differentSeeds_vary() {
        val a = HomeTaglinePicker.pick(idle, 12, seed = 3)
        assertEquals(a, HomeTaglinePicker.pick(idle, 12, seed = 3))
        assertNotEquals(a.variant, HomeTaglinePicker.pick(idle, 12, seed = 4).variant)
    }

    @Test fun variant_isNeverNegative() {
        assertTrue(HomeTaglinePicker.pick(idle, 12, seed = Int.MIN_VALUE).variant >= 0)
        assertTrue(HomeTaglinePicker.pick(idle, 12, seed = -1).variant >= 0)
    }
}
