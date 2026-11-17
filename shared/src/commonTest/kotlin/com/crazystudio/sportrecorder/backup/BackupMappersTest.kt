package com.crazystudio.sportrecorder.backup

import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.domain.model.TasteRating
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BackupMappersTest {
    @Test fun meal_roundTrips() {
        val r = EatRecord(1, 1_700L, GeoPoint(25.0, 121.5), "n",
            listOf(EatPhoto(7, "a.webp", 1_701L)))
        assertEquals(r, r.toBackup().toDomain())
    }

    @Test fun aMealsDuration_isWrittenToTheBackupAndReadBack() {
        val meal = EatRecord(1, 1_700L, null, null, emptyList(), durationMinutes = 90)

        assertEquals(90, meal.toBackup().durationMinutes)
        assertEquals(meal, meal.toBackup().toDomain())
    }

    @Test fun aMealWithoutADuration_comesBackAsOneHour() {
        val beforeDurations = BackupMeal(1, 1_700L, null, null, emptyList())

        assertEquals(60, beforeDurations.toDomain().durationMinutes)
    }

    @Test fun aDurationThatCannotBeReal_comesBackAsOneHour() {
        listOf(0, -1, -90).forEach { minutes ->
            val meal = BackupMeal(1, 1_700L, null, null, emptyList(), durationMinutes = minutes)

            assertEquals(60, meal.toDomain().durationMinutes, "durationMinutes = $minutes")
        }
    }

    // The editor keeps a stored value that is not in its menu, so a backup must not tidy it away.
    @Test fun aDurationOutsideTheEditorsMenu_isKept() {
        val meal = BackupMeal(1, 1_700L, null, null, emptyList(), durationMinutes = 100)

        assertEquals(100, meal.toDomain().durationMinutes)
    }

    @Test fun aMealsTasteRating_isWrittenToTheBackupAndReadBack() {
        TasteRating.LEVELS.forEach { level ->
            val meal = EatRecord(1, 1_700L, null, null, emptyList(), tasteRating = level)

            assertEquals(level, meal.toBackup().tasteRating)
            assertEquals(meal, meal.toBackup().toDomain())
        }
    }

    @Test fun anUnratedMeal_staysUnrated() {
        val meal = EatRecord(1, 1_700L, null, null, emptyList())

        assertNull(meal.toBackup().tasteRating)
        assertNull(meal.toBackup().toDomain().tasteRating)
    }

    @Test fun aMealWithoutATasteRating_comesBackUnrated() {
        val beforeRatings = BackupMeal(1, 1_700L, null, null, emptyList(), durationMinutes = 90)

        assertNull(beforeRatings.toDomain().tasteRating)
    }

    // A backup is a file from outside: a rating that is not on the scale must never reach the database.
    @Test fun aTasteRatingThatIsNotOnTheScale_comesBackUnrated() {
        listOf(0, 6, -1, 99).forEach { rating ->
            val meal = BackupMeal(1, 1_700L, null, null, emptyList(), tasteRating = rating)

            assertNull(meal.toDomain().tasteRating, "tasteRating = $rating")
        }
    }
}
