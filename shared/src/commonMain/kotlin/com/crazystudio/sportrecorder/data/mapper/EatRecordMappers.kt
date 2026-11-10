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
    Venue(id = id, name = name, lat = lat, lng = lng, lastUsedAt = lastUsedAt, address = address)

fun EatTime.toDomain(photos: List<EatPhoto> = emptyList(), venue: Venue? = null): EatRecord = EatRecord(
    id = id,
    time = time,
    location = if (lat != null && lng != null) GeoPoint(lat, lng) else null,
    note = note,
    photos = photos,
    venue = venue,
    durationMinutes = durationMinutes,
)

/**
 * The row for [this] record (photos live in their own table). Pass [id] = 0 to let Room assign a
 * new one. Every path that writes a record goes through here, so no field is silently defaulted.
 */
fun EatRecord.toEntity(id: Int = this.id): EatTime = EatTime(
    id = id,
    time = time,
    lat = location?.lat,
    lng = location?.lng,
    note = note,
    venueId = venue?.id,
    durationMinutes = durationMinutes,
)

fun EatTimeWithPhotos.toDomain(): EatRecord = eatTime.toDomain(photos.map { it.toDomain() }, venue?.toDomain())
