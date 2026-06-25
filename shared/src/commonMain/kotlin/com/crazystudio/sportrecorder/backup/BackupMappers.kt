package com.crazystudio.sportrecorder.backup

import com.crazystudio.sportrecorder.domain.model.CustomFastingType
import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.domain.reminder.ReminderPrefs

fun EatRecord.toBackup() = BackupMeal(id, time, note,
    location?.let { BackupGeoPoint(it.lat, it.lng) },
    photos.map { BackupPhoto(it.id, it.fileName, it.createdAt) })

fun BackupMeal.toDomain() = EatRecord(id, time,
    location?.let { GeoPoint(it.lat, it.lng) }, note,
    photos.map { EatPhoto(it.id, it.fileName, it.createdAt) })

fun CustomFastingType.toBackup() = BackupFastingType(fastingHours, eatingHours, name)
fun BackupFastingType.toDomain() = CustomFastingType(fastingHours, eatingHours, name)
fun DietSettings.toBackup() = BackupDietSettings(fastingHours, eatingHours)
fun BackupDietSettings.toDomain() = DietSettings(fastingHours, eatingHours)

fun ReminderPrefs.toBackup() = BackupReminderPrefs(
    windowClosingEnabled, fastCompleteEnabled, leadMinutes, quietHoursEnabled, quietStartMinutes, quietEndMinutes)
fun BackupReminderPrefs.toDomain() = ReminderPrefs(
    windowClosingEnabled, fastCompleteEnabled, leadMinutes, quietHoursEnabled, quietStartMinutes, quietEndMinutes)
