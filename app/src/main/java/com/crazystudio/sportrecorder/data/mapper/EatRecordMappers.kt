package com.crazystudio.sportrecorder.data.mapper

import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.entity.EatTime
import com.crazystudio.sportrecorder.entity.EatTimeWithPhotos
import com.crazystudio.sportrecorder.entity.Photo

fun Photo.toDomain(): EatPhoto = EatPhoto(id = id, fileName = fileName, createdAt = createdAt)

fun EatTime.toDomain(photos: List<EatPhoto> = emptyList()): EatRecord = EatRecord(
    id = id,
    time = time,
    location = if (lat != null && lng != null) GeoPoint(lat, lng) else null,
    note = note,
    photos = photos,
)

fun EatTimeWithPhotos.toDomain(): EatRecord = eatTime.toDomain(photos.map { it.toDomain() })
