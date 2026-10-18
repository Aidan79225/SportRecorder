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
     * Added in schema 1 without a bump: the default lets a snapshot written before venues existed
     * decode. Schema 2 gives each venue a [BackupVenue.key] and an address (venue names are no
     * longer unique, so a name alone can no longer tell two branches apart); a restore accepts 1
     * (venues re-attached by name) and 2 (by key).
     */
    val venues: List<BackupVenue> = emptyList(),
) {
    companion object { const val SCHEMA_VERSION = 2 }
}

@Serializable
data class BackupMeal(
    val id: Int,
    val time: Long,
    val note: String?,
    val location: BackupGeoPoint?,
    val photos: List<BackupPhoto>,
    /**
     * Which venue this meal was at, never by the local venue id itself: a restore does not
     * preserve ids (Room assigns fresh ones). [venueKey] is the venue's [BackupVenue.key], a
     * per-snapshot handle that tells two same-name branches apart and takes precedence. [venueName]
     * is the fallback for schema-1 snapshots (no keys) and is still written so the JSON stays
     * readable. Both null means no venue; a key that names no venue in the snapshot also means no
     * venue, never a dangling id. The defaults keep older snapshots decodable.
     */
    val venueName: String? = null,
    val venueKey: String? = null,
)

@Serializable
data class BackupVenue(
    val name: String,
    val lat: Double? = null,
    val lng: Double? = null,
    val lastUsedAt: Long = 0L,
    val address: String? = null,
    /** The venue's local id when the snapshot was taken, as a string; only meaningful within it. */
    val key: String? = null,
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
