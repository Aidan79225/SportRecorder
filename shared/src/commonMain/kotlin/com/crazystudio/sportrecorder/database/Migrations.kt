package com.crazystudio.sportrecorder.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.crazystudio.sportrecorder.entity.FastingType

private const val VERSION_1 = 1
private const val VERSION_2 = 2
private const val VERSION_3 = 3
private const val VERSION_4 = 4
private const val VERSION_5 = 5
private const val VERSION_6 = 6
private const val VERSION_7 = 7

// SQL is unchanged from the Android version — only the API moves from SupportSQLiteDatabase to the
// multiplatform SQLiteConnection. Room runs each migrate() inside its own transaction.
object Migrations {
    fun getMigrations(): Array<Migration> {
        return arrayOf(
            object : Migration(VERSION_1, VERSION_2) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL(
                        "CREATE TABLE IF NOT EXISTS `${FastingType.tableName}` " +
                            "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`fasting_hours` INTEGER NOT NULL DEFAULT 0, " +
                            "`eating_hours` INTEGER NOT NULL DEFAULT 0, " +
                            "`timestamp` INTEGER NOT NULL DEFAULT 0)"
                    )
                }
            },
            object : Migration(VERSION_2, VERSION_3) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL(
                        "CREATE TABLE IF NOT EXISTS `food_record` " +
                            "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`eat_time_id` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                            "`carbohydrate` REAL NOT NULL DEFAULT 0.0, " +
                            "`protein` REAL NOT NULL DEFAULT 0.0, " +
                            "`fat` REAL NOT NULL DEFAULT 0.0)"
                    )
                }
            },
            object : Migration(VERSION_3, VERSION_4) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL("ALTER TABLE `eat_time` ADD COLUMN `lat` REAL")
                    connection.execSQL("ALTER TABLE `eat_time` ADD COLUMN `lng` REAL")
                    connection.execSQL(
                        "CREATE TABLE IF NOT EXISTS `photo` " +
                            "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`eat_time_id` INTEGER NOT NULL, `file_name` TEXT NOT NULL, " +
                            "`created_at` INTEGER NOT NULL DEFAULT 0)"
                    )
                }
            },
            object : Migration(VERSION_4, VERSION_5) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL("DROP TABLE IF EXISTS `food_record`")
                }
            },
            object : Migration(VERSION_5, VERSION_6) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL("ALTER TABLE `eat_time` ADD COLUMN `note` TEXT")
                }
            },
            object : Migration(VERSION_6, VERSION_7) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL("ALTER TABLE `${FastingType.tableName}` ADD COLUMN `name` TEXT")
                }
            }
        )
    }
}
