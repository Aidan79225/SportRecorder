package com.crazystudio.sportrecorder.entity

import androidx.room.Embedded
import androidx.room.Relation

data class EatTimeWithPhotos(
    @Embedded val eatTime: EatTime,
    @Relation(parentColumn = "id", entityColumn = "eat_time_id")
    val photos: List<Photo>,
)
