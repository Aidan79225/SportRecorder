package com.crazystudio.sportrecorder.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = Photo.tableName)
data class Photo(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "eat_time_id")
    val eatTimeId: Int,

    @ColumnInfo(name = "file_name")
    val fileName: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
) {
    companion object {
        const val tableName = "photo"
    }
}
