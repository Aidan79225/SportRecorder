package com.crazystudio.sportrecorder.backup

import kotlin.test.Test
import kotlin.test.assertEquals

class BackupDocumentTest {
    @Test
    fun roundTrips_throughJson() {
        val doc = BackupDocument(
            schemaVersion = BackupDocument.SCHEMA_VERSION,
            createdAt = 1_700_000_000_000,
            appVersionName = "0.6.2",
            meals = listOf(
                BackupMeal(
                    id = 1, time = 1_700_000_000_000, note = "lunch",
                    location = BackupGeoPoint(25.0, 121.5),
                    photos = listOf(BackupPhoto(id = 7, fileName = "a.webp", createdAt = 1_700_000_000_001)),
                ),
            ),
            fastingTypes = listOf(BackupFastingType(16, 8, "normal")),
            dietSettings = BackupDietSettings(16, 8),
            reminderPrefs = BackupReminderPrefs(true, false, 30, true, 1320, 480),
        )
        val json = BackupJson.encodeToString(BackupDocument.serializer(), doc)
        val parsed = BackupJson.decodeFromString(BackupDocument.serializer(), json)
        assertEquals(doc, parsed)
    }
}
