package com.crazystudio.sportrecorder.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val BackupJson: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }

@Serializable
data class BackupDocument(
    val schemaVersion: Int,
    val createdAt: Long,
    val appVersionName: String,
    val meals: List<BackupMeal>,
    val fastingTypes: List<BackupFastingType>,
    val dietSettings: BackupDietSettings,
    val reminderPrefs: BackupReminderPrefs,
    /**
     * Added without a [SCHEMA_VERSION] bump: the default lets a snapshot written before venues
     * existed decode, and `ignoreUnknownKeys` lets an older app restore a newer snapshot minus
     * its venues. Bumping would make that older app refuse the whole snapshot.
     */
    val venues: List<BackupVenue> = emptyList(),
) {
    companion object { const val SCHEMA_VERSION = 1 }
}

@Serializable
data class BackupMeal(
    val id: Int,
    val time: Long,
    val note: String?,
    val location: BackupGeoPoint?,
    val photos: List<BackupPhoto>,
    /**
     * The venue's NAME, never its id: a restore does not preserve ids (Room assigns fresh ones),
     * so the name is the only key that is stable across installs. Null for no venue, and the
     * default keeps pre-venue snapshots decodable.
     */
    val venueName: String? = null,
)

@Serializable
data class BackupVenue(
    val name: String,
    val lat: Double? = null,
    val lng: Double? = null,
    val lastUsedAt: Long = 0L,
)

@Serializable data class BackupGeoPoint(val lat: Double, val lng: Double)
@Serializable data class BackupPhoto(val id: Int, val fileName: String, val createdAt: Long)
@Serializable data class BackupFastingType(val fastingHours: Long, val eatingHours: Long, val name: String?)
@Serializable data class BackupDietSettings(val fastingHours: Long, val eatingHours: Long)

@Serializable
data class BackupReminderPrefs(
    val windowClosingEnabled: Boolean,
    val fastCompleteEnabled: Boolean,
    val leadMinutes: Long,
    val quietHoursEnabled: Boolean,
    val quietStartMinutes: Int,
    val quietEndMinutes: Int,
)
