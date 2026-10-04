package com.crazystudio.sportrecorder.domain.usecase

import com.crazystudio.sportrecorder.domain.model.EatRecord
import kotlin.time.Clock

/**
 * Logs「現在吃了一餐」with nothing attached — the whole point is that it costs one tap from
 * outside the app. A fast only needs the *time*; the photo, note and place can be added later
 * from the record itself, and nothing here marks the record as incomplete.
 *
 * It goes through [SaveEatRecordUseCase] so a quick capture is the same save as any other:
 * the same validation, and the reminders re-armed around the new meal.
 */
class QuickRecordMealUseCase(
    private val saveEatRecord: SaveEatRecordUseCase,
) {
    /** Returns the time recorded, or null if the save was refused. */
    suspend operator fun invoke(now: Long = Clock.System.now().toEpochMilliseconds()): Long? {
        val saved = saveEatRecord(
            record = EatRecord(id = 0, time = now, location = null, note = null, photos = emptyList()),
            newPhotoFileNames = emptyList(),
            removedPhotos = emptyList(),
            now = now,
        )
        return now.takeIf { saved }
    }
}
