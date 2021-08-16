package com.crazystudio.sportrecorder.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.crazystudio.sportrecorder.entity.FastingType
import com.crazystudio.sportrecorder.entity.FoodRecord

object Migrations {
    fun getMigrations(): Array<Migration> {
        return arrayOf(
            object : Migration(1, 2) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.runInTransaction {
                        execSQL("CREATE TABLE IF NOT EXISTS `${FastingType.tableName}` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fasting_hours` INTEGER NOT NULL DEFAULT 0, `eating_hours` INTEGER NOT NULL DEFAULT 0, `timestamp` INTEGER NOT NULL DEFAULT 0)")
                    }
                }
            },
            object : Migration(2, 3) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.runInTransaction {
                        execSQL("CREATE TABLE IF NOT EXISTS `${FoodRecord.tableName}` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `eat_time_id` INTEGER NOT NULL, `name` TEXT NOT NULL, `carbohydrate` REAL NOT NULL DEFAULT 0.0, `protein` REAL NOT NULL DEFAULT 0.0, `fat` REAL NOT NULL DEFAULT 0.0)")
                    }
                }

            }
        )
    }


}

fun SupportSQLiteDatabase.runInTransaction(runnable: SupportSQLiteDatabase.() -> Unit) {
    beginTransaction()
    try {
        runnable()
        setTransactionSuccessful()
    } finally {
        endTransaction()
    }
}