package com.crazystudio.sportrecorder.backup

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the on-disk shape of a schema-1 manifest. If this test fails, the wire format changed:
 * decide whether the change is additive (a new field WITH a default, which an older app ignores —
 * no bump, as venues were) or breaking (bump BackupDocument.SCHEMA_VERSION). Either way keep
 * decoding the old fixtures (older backups must restore) and add a new one.
 *
 * [golden] is the shape written before venues existed and must keep decoding forever;
 * [goldenWithVenues] is what the app writes today.
 */
class BackupDocumentGoldenTest {
    private val golden = """{"schemaVersion":1,"createdAt":1700000000000,"appVersionName":"0.7.1",""" +
        """"meals":[{"id":1,"time":1699999000000,"note":"lunch","location":{"lat":25.03,"lng":121.56},""" +
        """"photos":[{"id":7,"fileName":"a.webp","createdAt":1699999001000}]},""" +
        """{"id":2,"time":1699990000000,"note":null,"location":null,"photos":[]}],""" +
        """"fastingTypes":[{"fastingHours":18,"eatingHours":6,"name":"my"}],""" +
        """"dietSettings":{"fastingHours":16,"eatingHours":8},""" +
        """"reminderPrefs":{"windowClosingEnabled":true,"fastCompleteEnabled":false,"leadMinutes":30,""" +
        """"quietHoursEnabled":true,"quietStartMinutes":1320,"quietEndMinutes":480}}"""

    private val expected = BackupDocument(
        schemaVersion = 1, createdAt = 1_700_000_000_000L, appVersionName = "0.7.1",
        meals = listOf(
            BackupMeal(
                1, 1_699_999_000_000L, "lunch", BackupGeoPoint(25.03, 121.56),
                listOf(BackupPhoto(7, "a.webp", 1_699_999_001_000L)),
            ),
            BackupMeal(2, 1_699_990_000_000L, null, null, emptyList()),
        ),
        fastingTypes = listOf(BackupFastingType(18, 6, "my")),
        dietSettings = BackupDietSettings(16, 8),
        reminderPrefs = BackupReminderPrefs(true, false, 30, true, 1320, 480),
    )

    private val goldenWithVenues = """{"schemaVersion":1,"createdAt":1700000000000,"appVersionName":"0.7.1",""" +
        """"meals":[{"id":1,"time":1699999000000,"note":"lunch","location":{"lat":25.03,"lng":121.56},""" +
        """"photos":[{"id":7,"fileName":"a.webp","createdAt":1699999001000}],"venueName":"大戶屋"},""" +
        """{"id":2,"time":1699990000000,"note":null,"location":null,"photos":[],"venueName":null}],""" +
        """"fastingTypes":[{"fastingHours":18,"eatingHours":6,"name":"my"}],""" +
        """"dietSettings":{"fastingHours":16,"eatingHours":8},""" +
        """"reminderPrefs":{"windowClosingEnabled":true,"fastCompleteEnabled":false,"leadMinutes":30,""" +
        """"quietHoursEnabled":true,"quietStartMinutes":1320,"quietEndMinutes":480},""" +
        """"venues":[{"name":"大戶屋","lat":25.03,"lng":121.56,"lastUsedAt":1699999000000}]}"""

    private val expectedWithVenues = expected.copy(
        meals = listOf(expected.meals[0].copy(venueName = "大戶屋"), expected.meals[1]),
        venues = listOf(BackupVenue("大戶屋", 25.03, 121.56, 1_699_999_000_000L)),
    )

    @Test fun schemaVersion_isStillOne() = assertEquals(1, BackupDocument.SCHEMA_VERSION)

    @Test fun golden_decodes() =
        assertEquals(expected, BackupJson.decodeFromString(BackupDocument.serializer(), golden))

    @Test fun goldenWithVenues_decodes() =
        assertEquals(expectedWithVenues, BackupJson.decodeFromString(BackupDocument.serializer(), goldenWithVenues))

    @Test fun goldenWithVenues_roundTrips() =
        assertEquals(goldenWithVenues, BackupJson.encodeToString(BackupDocument.serializer(), expectedWithVenues))
}
