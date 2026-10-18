package com.crazystudio.sportrecorder.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room row for a venue. The domain type is `domain.model.Venue`. The name index is for lookup
 * only: branches of a chain share a name (#89).
 */
@Entity(
    tableName = VenueEntity.tableName,
    indices = [Index(value = ["name"])],
)
data class VenueEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "lat")
    val lat: Double? = null,

    @ColumnInfo(name = "lng")
    val lng: Double? = null,

    @ColumnInfo(name = "last_used_at")
    val lastUsedAt: Long,

    @ColumnInfo(name = "address")
    val address: String? = null,
) {
    companion object {
        const val tableName = "venue"
    }
}
