package com.crazystudio.sportrecorder.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = FastingType.tableName)
data class FastingType(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "fasting_hours")
    val fastingHours: Long,

    @ColumnInfo(name = "eating_hours")
    val eatingHours: Long,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long
) {
    companion object {
        const val tableName = "fasting_type"
    }
}
