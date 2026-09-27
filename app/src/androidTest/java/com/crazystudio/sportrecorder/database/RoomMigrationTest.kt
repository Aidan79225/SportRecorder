package com.crazystudio.sportrecorder.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.useReaderConnection
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Opens databases built by hand at old schema versions (the DDL Room generated back then, recovered
 * from git history) with the real migrations. Room validates the migrated schema against the
 * entities on open and throws on any mismatch, so "opens, reads, and old rows survive" is the test.
 */
@RunWith(AndroidJUnit4::class)
class RoomMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-test.db"
    private var db: AppDatabase? = null

    @Before fun setUp() { context.deleteDatabase(dbName) }

    @After fun tearDown() {
        db?.close()
        context.deleteDatabase(dbName)
    }

    /** Create the DB file at [version] with [ddl] and [seed] statements, exactly as an old app build left it. */
    private fun createLegacy(version: Int, ddl: List<String>, seed: List<String>) {
        val file: File = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { legacy ->
            (ddl + seed).forEach { legacy.execSQL(it) }
            legacy.version = version
        }
    }

    private fun openMigrated(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*Migrations.getMigrations())
            .build()
            .also { db = it }

    private suspend fun AppDatabase.userVersion(): Int = useReaderConnection { conn ->
        conn.usePrepared("PRAGMA user_version") { stmt -> stmt.step(); stmt.getLong(0).toInt() }
    }

    private suspend fun AppDatabase.tableNames(): Set<String> = useReaderConnection { conn ->
        conn.usePrepared("SELECT name FROM sqlite_master WHERE type='table'") { stmt ->
            buildSet { while (stmt.step()) add(stmt.getText(0)) }
        }
    }

    @Test fun v1_toCurrent_keepsEatTimes_andAddsTables() = runBlocking {
        createLegacy(
            version = 1,
            ddl = listOf(
                "CREATE TABLE IF NOT EXISTS `eat_time` " +
                    "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `time` INTEGER NOT NULL)"
            ),
            seed = listOf("INSERT INTO `eat_time` (`time`) VALUES (1000)"),
        )

        val migrated = openMigrated()
        val records = migrated.getEatTimeDao().flowAllWithPhotos().first()

        assertEquals(1, records.size)
        assertEquals(1000L, records[0].eatTime.time)
        assertNull(records[0].eatTime.lat)
        assertNull(records[0].eatTime.note)
        assertTrue(records[0].photos.isEmpty())
        assertTrue(migrated.getFastingTypeDao().flowLast(10).first().isEmpty())
        assertEquals(7, migrated.userVersion())
    }

    @Test fun v3_toCurrent_dropsFoodRecord_andAddsFastingTypeName() = runBlocking {
        createLegacy(
            version = 3,
            ddl = listOf(
                "CREATE TABLE IF NOT EXISTS `eat_time` " +
                    "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `time` INTEGER NOT NULL)",
                "CREATE TABLE IF NOT EXISTS `fasting_type` " +
                    "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`fasting_hours` INTEGER NOT NULL DEFAULT 0, `eating_hours` INTEGER NOT NULL DEFAULT 0, " +
                    "`timestamp` INTEGER NOT NULL DEFAULT 0)",
                "CREATE TABLE IF NOT EXISTS `food_record` " +
                    "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `eat_time_id` INTEGER NOT NULL, " +
                    "`name` TEXT NOT NULL, `carbohydrate` REAL NOT NULL DEFAULT 0.0, " +
                    "`protein` REAL NOT NULL DEFAULT 0.0, `fat` REAL NOT NULL DEFAULT 0.0)",
            ),
            seed = listOf(
                "INSERT INTO `eat_time` (`time`) VALUES (2000)",
                "INSERT INTO `fasting_type` (`fasting_hours`, `eating_hours`, `timestamp`) VALUES (18, 6, 5)",
                "INSERT INTO `food_record` (`eat_time_id`, `name`) VALUES (1, 'rice')",
            ),
        )

        val migrated = openMigrated()

        assertFalse("food_record" in migrated.tableNames())
        val types = migrated.getFastingTypeDao().flowLast(10).first()
        assertEquals(1, types.size)
        assertEquals(18L, types[0].fastingHours)
        assertNull(types[0].name)
        assertEquals(2000L, migrated.getEatTimeDao().flowAll().first().single().time)
        assertEquals(7, migrated.userVersion())
    }

    @Test fun v4_toCurrent_keepsPhotoRelation() = runBlocking {
        createLegacy(
            version = 4,
            ddl = listOf(
                "CREATE TABLE IF NOT EXISTS `eat_time` " +
                    "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `time` INTEGER NOT NULL, " +
                    "`lat` REAL, `lng` REAL)",
                "CREATE TABLE IF NOT EXISTS `fasting_type` " +
                    "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`fasting_hours` INTEGER NOT NULL DEFAULT 0, `eating_hours` INTEGER NOT NULL DEFAULT 0, " +
                    "`timestamp` INTEGER NOT NULL DEFAULT 0)",
                "CREATE TABLE IF NOT EXISTS `food_record` " +
                    "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `eat_time_id` INTEGER NOT NULL, " +
                    "`name` TEXT NOT NULL, `carbohydrate` REAL NOT NULL DEFAULT 0.0, " +
                    "`protein` REAL NOT NULL DEFAULT 0.0, `fat` REAL NOT NULL DEFAULT 0.0)",
                "CREATE TABLE IF NOT EXISTS `photo` " +
                    "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `eat_time_id` INTEGER NOT NULL, " +
                    "`file_name` TEXT NOT NULL, `created_at` INTEGER NOT NULL DEFAULT 0)",
            ),
            seed = listOf(
                "INSERT INTO `eat_time` (`time`, `lat`, `lng`) VALUES (3000, 25.0, 121.5)",
                "INSERT INTO `photo` (`eat_time_id`, `file_name`, `created_at`) VALUES (1, 'a.webp', 7)",
            ),
        )

        val migrated = openMigrated()
        val record = migrated.getEatTimeDao().flowAllWithPhotos().first().single()

        assertEquals(25.0, record.eatTime.lat!!, 0.0)
        assertNull(record.eatTime.note)
        assertEquals(listOf("a.webp"), record.photos.map { it.fileName })
        assertEquals(7L, record.photos.single().createdAt)
        assertEquals(7, migrated.userVersion())
    }
}
