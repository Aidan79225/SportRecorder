package com.crazystudio.sportrecorder.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.crazystudio.sportrecorder.entity.FastingType

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
                        execSQL("CREATE TABLE IF NOT EXISTS `food_record` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `eat_time_id` INTEGER NOT NULL, `name` TEXT NOT NULL, `carbohydrate` REAL NOT NULL DEFAULT 0.0, `protein` REAL NOT NULL DEFAULT 0.0, `fat` REAL NOT NULL DEFAULT 0.0)")
                    }
                }

            },
            object : Migration(3, 4) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.runInTransaction {
                        execSQL("ALTER TABLE `eat_time` ADD COLUMN `lat` REAL")
                        execSQL("ALTER TABLE `eat_time` ADD COLUMN `lng` REAL")
                        execSQL("CREATE TABLE IF NOT EXISTS `photo` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `eat_time_id` INTEGER NOT NULL, `file_name` TEXT NOT NULL, `created_at` INTEGER NOT NULL DEFAULT 0)")
                    }
                }
            },
            object : Migration(4, 5) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.runInTransaction {
                        execSQL("DROP TABLE IF EXISTS `food_record`")
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