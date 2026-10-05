package com.crazystudio.sportrecorder.data.mapper

import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.domain.model.Venue
import com.crazystudio.sportrecorder.entity.EatTime
import com.crazystudio.sportrecorder.entity.EatTimeWithPhotos
import com.crazystudio.sportrecorder.entity.Photo
import com.crazystudio.sportrecorder.entity.VenueEntity

fun Photo.toDomain(): EatPhoto = EatPhoto(id = id, fileName = fileName, createdAt = createdAt)

fun VenueEntity.toDomain(): Venue =
    Venue(id = id, name = name, lat = lat, lng = lng, lastUsedAt = lastUsedAt)

fun EatTime.toDomain(photos: List<EatPhoto> = emptyList(), venue: Venue? = null): EatRecord = EatRecord(
    id = id,
    time = time,
    location = if (lat != null && lng != null) GeoPoint(lat, lng) else null,
    note = note,
    photos = photos,
    venue = venue,
)

fun EatTimeWithPhotos.toDomain(): EatRecord = eatTime.toDomain(photos.map { it.toDomain() }, venue?.toDomain())
