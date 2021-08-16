package com.crazystudio.sportrecorder.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = FoodRecord.tableName)
data class FoodRecord(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "eat_time_id")
    val eatTimeId: Int,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "carbohydrate")
    val carbohydrate: Float,

    @ColumnInfo(name = "protein")
    val protein: Float,

    @ColumnInfo(name = "fat")
    val fat: Float,

    ) {

    fun getCalories(): Float {
        return carbohydrate * 4.0f + protein * 4.0f + fat * 9.0f
    }

    companion object {
        const val tableName = "food_record"
    }
}