package com.crazystudio.sportrecorder.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.crazystudio.sportrecorder.domain.model.MealDuration

@Entity(tableName = EatTime.tableName)
data class EatTime(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "time")
    val time: Long,

    @ColumnInfo(name = "lat")
    val lat: Double? = null,

    @ColumnInfo(name = "lng")
    val lng: Double? = null,

    @ColumnInfo(name = "note")
    val note: String? = null,

    @ColumnInfo(name = "venue_id")
    val venueId: Int? = null,

    @ColumnInfo(name = "duration_minutes", defaultValue = "60")
    val durationMinutes: Int = MealDuration.DEFAULT_MINUTES,
) {
    companion object {
        const val tableName = "eat_time"
    }
}
