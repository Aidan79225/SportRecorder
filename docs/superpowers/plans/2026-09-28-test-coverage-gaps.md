# Test Coverage Gaps Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close six coverage gaps — Room migrations, Room DAO/repository contracts, the meal editor's Compose UI, the photo bitmap pipeline, the Android side of reminders, and three backup holes — with local-only instrumented tests plus JVM tests that join the CI gate.

**Architecture:** New instrumented classes under `app/src/androidTest/java/com/crazystudio/sportrecorder/{database,data,ui,util,reminder}/`, sharing a `RoomHarness` fixture (in-memory `AppDatabase` + real repositories + recording `PhotoFileStore` + a throwing `PhotoDao` decorator). Backup additions go to `shared/src/commonTest`. Exactly two production seams: `exportSchema = true` (+ committed schema JSON) and, if the race test proves it, `BackupViewModel` bumping its list generation on account change.

**Tech Stack:** AndroidJUnit4, Compose `ui-test-junit4`, `android.database.sqlite.SQLiteDatabase` (hand-built old DBs), Room `inMemoryDatabaseBuilder`, `androidx.exifinterface`, `AlarmManager`/`PendingIntent`, Koin `loadKoinModules`, kotlinx.coroutines test.

**Spec:** `docs/superpowers/specs/2026-09-28-test-coverage-gaps-design.md`

## Global Constraints

- Branch `claude/test-coverage-gaps` off `master` (`2ba2856`). Commits end with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
- Production changes allowed ONLY: (a) `exportSchema = true` in `shared/.../database/AppDatabase.kt` + the generated `shared/schemas/**/7.json`; (b) the `BackupViewModel` account-change generation bump in Task 6, only if the new test fails first. Anything else in `app/src/main` or `shared/src/commonMain` is out of scope; a failing test that reveals a production bug is reported, not patched. No `gradle/libs.versions.toml` changes.
- Emulator: `emulator-5556` (API 35). PowerShell: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; $env:ANDROID_SERIAL = "emulator-5556"` in the SAME call as `.\gradlew.bat`. Single class: `.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=<fqcn>"` — the `-P` argument MUST be quoted. Max tool timeout.
- JVM gate before every commit that touches `shared/src` or `app/src/main` or JVM tests: `.\gradlew.bat assembleDebug testDebugUnitTest :app:detekt :app:lintDebug :shared:jvmTest`. Test-APK compile check: `:app:assembleDebugAndroidTest`.
- Compose Resources strings via `org.jetbrains.compose.resources.getString` (suspend, wrap in `runBlocking`); never hard-code copy. Lines ≤ 120 chars.
- Instrumented fixtures from the backup suite are reusable: `awaitUntil(timeoutMs, stepMs, what, condition)` and `Context.activeNotification(id)` in `app/src/androidTest/.../backup/BackupTestFixtures.kt`.
- Existing production facts to rely on: DAOs are `interface`s (`EatTimeDao`, `PhotoDao`, `FastingTypeDao` in `com.crazystudio.sportrecorder.dao`); entities in `com.crazystudio.sportrecorder.entity`; `EatRecordRepositoryImpl(appDatabase, eatTimeDao, photoDao, photoFileStore)`; `FastingTypeRepositoryImpl(appDatabase, fastingTypeDao)`; `EatRecordRepository.save(record, newPhotoFileNames, removedPhotos): Int`; `PhotoFileStore { fun delete(fileName) }` (plain interface); `decodeScaleEncode(photosDir: File, openStream: () -> InputStream): String` is `internal` in `app/.../util/BitmapPipeline.kt` (MAX_EDGE 1280); `AlarmReminderScheduler(context, ReminderNotifier(context))` uses request codes `1000 + ReminderType.ordinal` and `Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_FIRE).putExtra(ReminderReceiver.EXTRA_TYPE, type.name)`; `ReminderReceiver` posts notification id 2001 (WINDOW_CLOSING) / 2002 (FAST_COMPLETE) and calls the Koin `RemindersRescheduler`.

---

## File map

- Modify: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/database/AppDatabase.kt` (exportSchema)
- Create (generated, commit): `shared/schemas/com.crazystudio.sportrecorder.database.AppDatabase/7.json`
- Create: `app/src/androidTest/java/com/crazystudio/sportrecorder/database/RoomMigrationTest.kt`
- Create: `app/src/androidTest/java/com/crazystudio/sportrecorder/data/RoomHarness.kt`, `EatRecordRepositoryRoomTest.kt`, `FastingTypeRepositoryRoomTest.kt`
- Create: `app/src/androidTest/java/com/crazystudio/sportrecorder/ui/diet/editor/EatTimeEditorSheetTest.kt`
- Create: `app/src/androidTest/java/com/crazystudio/sportrecorder/util/BitmapPipelineTest.kt`
- Create: `app/src/androidTest/java/com/crazystudio/sportrecorder/reminder/AlarmReminderSchedulerTest.kt`, `ReminderReceiverTest.kt`
- Create: `shared/src/commonTest/kotlin/com/crazystudio/sportrecorder/backup/BackupDocumentGoldenTest.kt`
- Modify: `shared/src/commonTest/.../backup/fakes/FakeRepositories.kt` (`replaceAllGate`), `backup/BackupJobRunnerTest.kt`, `ui/backup/BackupViewModelTest.kt`; possibly `shared/src/commonMain/.../ui/backup/BackupViewModel.kt`
- Modify: `docs/DEVELOPMENT.md`

---

### Task 1: Room migrations — export schema + `RoomMigrationTest`

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/database/AppDatabase.kt`
- Create: `shared/schemas/com.crazystudio.sportrecorder.database.AppDatabase/7.json` (generated by KSP on the next build)
- Create: `app/src/androidTest/java/com/crazystudio/sportrecorder/database/RoomMigrationTest.kt`

- [ ] **Step 1: Turn on schema export**

In `AppDatabase.kt` change `exportSchema = false` to `exportSchema = true`. Run `.\gradlew.bat :shared:kspDebugKotlinAndroid` (or simply `:app:assembleDebug`) and confirm `shared/schemas/com.crazystudio.sportrecorder.database.AppDatabase/7.json` exists. If the Room plugin writes it under a different path, find it with `Get-ChildItem -Recurse shared -Filter 7.json` and note the path in the report; the file must be committed.

- [ ] **Step 2: Write the migration test**

```kotlin
package com.crazystudio.sportrecorder.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.useReaderConnection
import androidx.sqlite.execSQL
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
            ddl = listOf("CREATE TABLE IF NOT EXISTS `eat_time` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `time` INTEGER NOT NULL)"),
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
                "CREATE TABLE IF NOT EXISTS `eat_time` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `time` INTEGER NOT NULL)",
                "CREATE TABLE IF NOT EXISTS `fasting_type` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`fasting_hours` INTEGER NOT NULL DEFAULT 0, `eating_hours` INTEGER NOT NULL DEFAULT 0, `timestamp` INTEGER NOT NULL DEFAULT 0)",
                "CREATE TABLE IF NOT EXISTS `food_record` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `eat_time_id` INTEGER NOT NULL, " +
                    "`name` TEXT NOT NULL, `carbohydrate` REAL NOT NULL DEFAULT 0.0, `protein` REAL NOT NULL DEFAULT 0.0, `fat` REAL NOT NULL DEFAULT 0.0)",
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
                "CREATE TABLE IF NOT EXISTS `eat_time` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `time` INTEGER NOT NULL, `lat` REAL, `lng` REAL)",
                "CREATE TABLE IF NOT EXISTS `fasting_type` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`fasting_hours` INTEGER NOT NULL DEFAULT 0, `eating_hours` INTEGER NOT NULL DEFAULT 0, `timestamp` INTEGER NOT NULL DEFAULT 0)",
                "CREATE TABLE IF NOT EXISTS `food_record` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `eat_time_id` INTEGER NOT NULL, " +
                    "`name` TEXT NOT NULL, `carbohydrate` REAL NOT NULL DEFAULT 0.0, `protein` REAL NOT NULL DEFAULT 0.0, `fat` REAL NOT NULL DEFAULT 0.0)",
                "CREATE TABLE IF NOT EXISTS `photo` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `eat_time_id` INTEGER NOT NULL, " +
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
```

Implementer notes: `useReaderConnection` / `usePrepared` are Room KMP APIs (`androidx.room.useReaderConnection`, `androidx.sqlite.SQLiteConnection.usePrepared`); if the import names differ in Room 2.8.4, use what the IDE resolves — the intent is `PRAGMA user_version` and a `sqlite_master` query. Room's schema validation on open is the key assertion; if `openMigrated()` throws `IllegalStateException: Migration didn't properly handle…`, that is a REAL production migration bug — stop and report it (the spec forbids patching production here beyond `exportSchema`).

- [ ] **Step 3: Run**

`:app:assembleDebugAndroidTest`, then the class on the emulator. Expected 3/3. Then the JVM gate (production file touched).

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/database/AppDatabase.kt shared/schemas app/src/androidTest/java/com/crazystudio/sportrecorder/database/RoomMigrationTest.kt
git commit -m "test(db): export the Room schema and prove migrations 1/3/4 → 7 on real SQLite

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: `RoomHarness` + repository contract tests on real Room

**Files:**
- Create: `app/src/androidTest/java/com/crazystudio/sportrecorder/data/RoomHarness.kt`
- Create: `app/src/androidTest/java/com/crazystudio/sportrecorder/data/EatRecordRepositoryRoomTest.kt`
- Create: `app/src/androidTest/java/com/crazystudio/sportrecorder/data/FastingTypeRepositoryRoomTest.kt`

- [ ] **Step 1: Harness**

```kotlin
package com.crazystudio.sportrecorder.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.crazystudio.sportrecorder.dao.PhotoDao
import com.crazystudio.sportrecorder.data.repository.EatRecordRepositoryImpl
import com.crazystudio.sportrecorder.data.repository.FastingTypeRepositoryImpl
import com.crazystudio.sportrecorder.database.AppDatabase
import com.crazystudio.sportrecorder.entity.Photo

/** Recording [PhotoFileStore]: which files the repository asked to delete, in order. */
class RecordingPhotoFileStore : PhotoFileStore {
    val deleted = mutableListOf<String>()
    override fun delete(fileName: String) { deleted.add(fileName) }
}

/** Delegating [PhotoDao] that throws on the [failOnInsertNumber]-th insert (1-based); 0 = never. */
class ThrowingPhotoDao(private val real: PhotoDao, var failOnInsertNumber: Int = 0) : PhotoDao by real {
    private var inserts = 0
    override suspend fun insert(photo: Photo): Long {
        inserts++
        if (inserts == failOnInsertNumber) throw IllegalStateException("simulated photo insert failure")
        return real.insert(photo)
    }
}

/** In-memory Room with the real repositories on top; only the file store is a recorder. */
class RoomHarness {
    val context: Context = ApplicationProvider.getApplicationContext()
    val db: AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    val files = RecordingPhotoFileStore()
    val photoDao = ThrowingPhotoDao(db.getPhotoDao())
    val eatRepo = EatRecordRepositoryImpl(db, db.getEatTimeDao(), photoDao, files)
    val fastingRepo = FastingTypeRepositoryImpl(db, db.getFastingTypeDao())
    fun close() = db.close()
}
```

If `PhotoDao by real` delegation does not compile because the DAO has non-suspend members or Room-generated defaults, implement each of the five `PhotoDao` methods explicitly, forwarding to `real`.

- [ ] **Step 2: Eat record tests**

```kotlin
package com.crazystudio.sportrecorder.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.GeoPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EatRecordRepositoryRoomTest {
    private lateinit var h: RoomHarness

    @Before fun setUp() { h = RoomHarness() }
    @After fun tearDown() { h.close() }

    private suspend fun add(time: Long, note: String? = null, photos: List<String> = emptyList()): Int =
        h.eatRepo.save(EatRecord(0, time, null, note, emptyList()), photos, emptyList())

    @Test fun observeAll_isNewestFirst() = runBlocking {
        add(1_000); add(3_000); add(2_000)
        assertEquals(listOf(3_000L, 2_000L, 1_000L), h.eatRepo.observeAll().first().map { it.time })
    }

    @Test fun observeInWindow_isAscending_andStrictlyExclusive() = runBlocking {
        listOf(100L, 200L, 300L, 400L, 500L).forEach { add(it) }
        val inWindow = h.eatRepo.observeInWindow(after = 200, before = 400).first().map { it.time }
        assertEquals(listOf(300L), inWindow) // 200 and 400 are excluded on both bounds
        assertEquals(listOf(200L, 300L, 400L), h.eatRepo.observeInWindow(after = 100, before = 500).first().map { it.time })
    }

    @Test fun save_insert_assignsId_andStampsPhotos() = runBlocking {
        val id = add(1_000, "lunch", listOf("a.webp", "b.webp"))
        assertTrue(id > 0)
        val record = h.eatRepo.findById(id)!!
        assertEquals(setOf("a.webp", "b.webp"), record.photos.map { it.fileName }.toSet())
        assertTrue(record.photos.all { it.createdAt > 0 })
        assertTrue(record.photos.all { it.id > 0 })
    }

    @Test fun save_update_removesOnlyGivenPhotos_andDeletesTheirFiles() = runBlocking {
        val id = add(1_000, "lunch", listOf("a.webp", "b.webp"))
        val before = h.eatRepo.findById(id)!!
        val toRemove = before.photos.first { it.fileName == "a.webp" }

        h.eatRepo.save(before.copy(note = "late lunch", location = GeoPoint(25.0, 121.5)), listOf("c.webp"), listOf(toRemove))

        val after = h.eatRepo.findById(id)!!
        assertEquals("late lunch", after.note)
        assertEquals(GeoPoint(25.0, 121.5), after.location)
        assertEquals(setOf("b.webp", "c.webp"), after.photos.map { it.fileName }.toSet())
        assertEquals(listOf("a.webp"), h.files.deleted)
    }

    @Test fun delete_removesRecordPhotosAndFiles() = runBlocking {
        val id = add(1_000, "lunch", listOf("a.webp", "b.webp"))
        add(2_000)

        h.eatRepo.delete(id)

        assertEquals(listOf(2_000L), h.eatRepo.observeAll().first().map { it.time })
        assertTrue(h.photoDao.findByEatTimeId(id).isEmpty())
        assertEquals(setOf("a.webp", "b.webp"), h.files.deleted.toSet())
    }

    @Test fun replaceAll_rollsBackWhenAPhotoInsertFails() = runBlocking {
        add(1_000, "keep", listOf("k.webp"))
        val before = h.eatRepo.observeAll().first()
        h.photoDao.failOnInsertNumber = 2 // first photo of the new data lands, second throws

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                h.eatRepo.replaceAll(
                    listOf(
                        EatRecord(0, 5_000, null, "new-1", listOf(EatPhoto(0, "n1.webp", 1L))),
                        EatRecord(0, 6_000, null, "new-2", listOf(EatPhoto(0, "n2.webp", 1L))),
                    ),
                )
            }
        }

        assertTrue(error.message!!.contains("simulated"))
        assertEquals(before, h.eatRepo.observeAll().first()) // transaction rolled back
        assertEquals(listOf("k.webp"), h.photoDao.findByEatTimeId(before.single().id).map { it.fileName })
        assertTrue(h.files.deleted.isEmpty())
    }

    @Test fun replaceAll_neverDeletesFiles() = runBlocking {
        add(1_000, "old", listOf("old.webp"))
        h.eatRepo.replaceAll(listOf(EatRecord(0, 9_000, null, "new", listOf(EatPhoto(0, "new.webp", 42L)))))
        val after = h.eatRepo.observeAll().first().single()
        assertEquals("new", after.note)
        assertEquals(42L, after.photos.single().createdAt) // replaceAll keeps the backed-up timestamp
        assertTrue(h.files.deleted.isEmpty())
    }
}
```

- [ ] **Step 3: Fasting type tests**

```kotlin
package com.crazystudio.sportrecorder.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazystudio.sportrecorder.domain.model.CustomFastingType
import com.crazystudio.sportrecorder.domain.model.FastingWindow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FastingTypeRepositoryRoomTest {
    private lateinit var h: RoomHarness

    @Before fun setUp() { h = RoomHarness() }
    @After fun tearDown() { h.close() }

    @Test fun observeRecentCustomTypes_isNewestFirst_andCappedAtTen() = runBlocking {
        (1..12).forEach { h.fastingRepo.add(FastingWindow(10L + it, 14L - it), "t$it") }
        val types = h.fastingRepo.observeRecentCustomTypes().first()
        assertEquals(10, types.size)
        assertEquals("t12", types.first().name)
        assertEquals("t3", types.last().name)
    }

    @Test fun exists_matchesHoursOnly() = runBlocking {
        h.fastingRepo.add(FastingWindow(18, 6), "named")
        assertTrue(h.fastingRepo.exists(FastingWindow(18, 6)))
        assertFalse(h.fastingRepo.exists(FastingWindow(16, 8)))
    }

    @Test fun replaceAllCustom_swapsTheTable() = runBlocking {
        h.fastingRepo.add(FastingWindow(18, 6), "old")
        h.fastingRepo.replaceAllCustom(listOf(CustomFastingType(20, 4, "new-a"), CustomFastingType(14, 10, null)))
        val names = h.fastingRepo.observeRecentCustomTypes().first().map { it.name }
        assertEquals(2, names.size)
        assertTrue("old" !in names)
        assertTrue("new-a" in names)
    }
}
```

`FastingTypeRepositoryImpl.replaceAllCustom` ordering after replace depends on its timestamps; assert on the set, as above. If `observeRecentCustomTypes` uses a cap other than 10, read the impl and adjust the expected count (the spec says "capped" — the number comes from production).

- [ ] **Step 4: Run both classes on the emulator (filtered), then commit**

```bash
git add app/src/androidTest/java/com/crazystudio/sportrecorder/data
git commit -m "test(data): repository contracts on real Room, including replaceAll rollback

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 3: `EatTimeEditorSheetTest` (Compose UI)

**Files:**
- Create: `app/src/androidTest/java/com/crazystudio/sportrecorder/ui/diet/editor/EatTimeEditorSheetTest.kt`

Read `shared/.../ui/diet/editor/EatTimeEditorSheet.kt` fully first. Facts: the sheet takes `(state, photoModel, onPickDate, onPickTime, onNoteChange, onAddPhoto, onSelectPhoto, onRemovePendingPhoto, onRemoveExistingPhoto, onRecaptureLocation, onClearLocation, onConfirm)`. `HeaderRow`s (date, time, add photo, select photo) render `title` + `content` texts and an action icon whose `contentDescription` is `""` and is the clickable — locate it as the last child of the row that contains the title text: `onNodeWithText(title).onParent().onChildren().onLast().performClick()` (use `useUnmergedTree = true` if needed). Photo tiles use `contentDescription = fileName` for the image and `diet_eat_remove_photo` for the remove control. Location row shows `diet_eat_location_loading` / `diet_eat_location_none` / a formatted `lat, lng` (via the private `fmt5`; replicate: 5 decimals). Find the confirm control by reading the sheet (it is the only control wired to `onConfirm`); if it has no text or content description at all, use its position (`onAllNodes(hasClickAction())`) and say so in the report — do not add a testTag to production.

- [ ] **Step 1: Write the test**

```kotlin
package com.crazystudio.sportrecorder.ui.diet.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.diet_create_eating_date_title
import com.crazystudio.sportrecorder.shared.resources.diet_create_eating_time_title
import com.crazystudio.sportrecorder.shared.resources.diet_eat_clear_location
import com.crazystudio.sportrecorder.shared.resources.diet_eat_location_loading
import com.crazystudio.sportrecorder.shared.resources.diet_eat_location_none
import com.crazystudio.sportrecorder.shared.resources.diet_eat_note
import com.crazystudio.sportrecorder.shared.resources.diet_eat_remove_photo
import com.crazystudio.sportrecorder.shared.resources.photo_add
import com.crazystudio.sportrecorder.shared.resources.photo_select
import com.crazystudio.sportrecorder.ui.theme.SportRecorderTheme
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EatTimeEditorSheetTest {
    @get:Rule val compose = createComposeRule()

    private var pickDate = 0; private var pickTime = 0; private var addPhoto = 0; private var selectPhoto = 0
    private var confirm = 0; private var clearLocation = 0; private var recapture = 0
    private val notes = mutableListOf<String>()
    private val removedPending = mutableListOf<String>()
    private val removedExisting = mutableListOf<EatPhoto>()

    private fun str(res: StringResource, vararg args: Any) = runBlocking { getString(res, *args) }

    private fun show(state: EatTimeEditorUiState) {
        compose.setContent {
            SportRecorderTheme {
                EatTimeEditorSheet(
                    state = state,
                    photoModel = { null },
                    onPickDate = { pickDate++ }, onPickTime = { pickTime++ },
                    onNoteChange = { notes.add(it) },
                    onAddPhoto = { addPhoto++ }, onSelectPhoto = { selectPhoto++ },
                    onRemovePendingPhoto = { removedPending.add(it) },
                    onRemoveExistingPhoto = { removedExisting.add(it) },
                    onRecaptureLocation = { recapture++ }, onClearLocation = { clearLocation++ },
                    onConfirm = { confirm++ },
                )
            }
        }
    }

    private fun clickRowAction(title: String) =
        compose.onNodeWithText(title).onParent().onChildren().onLast().performClick()

    @Test fun dateAndTimeRows_triggerPickers() {
        show(EatTimeEditorUiState(dateMillis = 1_700_000_000_000L))
        clickRowAction(str(Res.string.diet_create_eating_date_title)); assertEquals(1, pickDate)
        clickRowAction(str(Res.string.diet_create_eating_time_title)); assertEquals(1, pickTime)
    }

    @Test fun noteField_emitsChanges() {
        show(EatTimeEditorUiState())
        compose.onNodeWithText(str(Res.string.diet_eat_note)).performTextInput("ramen")
        assertEquals("ramen", notes.last())
    }

    @Test fun photoRows_triggerCaptureAndPick() {
        show(EatTimeEditorUiState())
        clickRowAction(str(Res.string.photo_add)); assertEquals(1, addPhoto)
        clickRowAction(str(Res.string.photo_select)); assertEquals(1, selectPhoto)
    }

    @Test fun photoTiles_removeTheRightPhoto() {
        val existing = EatPhoto(7, "old.webp", 1L)
        show(EatTimeEditorUiState(existingPhotos = listOf(existing), pendingPhotos = listOf("new.webp")))
        compose.onNodeWithContentDescription("old.webp").assertIsDisplayed()
        compose.onNodeWithContentDescription("new.webp").assertIsDisplayed()
        val removes = compose.onAllNodesWithContentDescription(str(Res.string.diet_eat_remove_photo))
        removes[0].performClick(); removes[1].performClick()
        assertEquals(listOf(existing), removedExisting)
        assertEquals(listOf("new.webp"), removedPending)
    }

    @Test fun locationRow_showsStatusAndClears() {
        show(EatTimeEditorUiState(locationStatus = EatTimeEditorUiState.LocationStatus.LOADING))
        compose.onNodeWithText(str(Res.string.diet_eat_location_loading)).assertIsDisplayed()

        show(EatTimeEditorUiState(location = EatTimeEditorUiState.LatLng(25.03396, 121.56454)))
        compose.onNodeWithText("25.03396, 121.56454", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription(str(Res.string.diet_eat_clear_location)).performClick()
        assertEquals(1, clearLocation)
    }

    @Test fun noLocation_showsNoneCopy() {
        show(EatTimeEditorUiState(location = null, locationStatus = EatTimeEditorUiState.LocationStatus.IDLE))
        compose.onNodeWithText(str(Res.string.diet_eat_location_none)).assertIsDisplayed()
    }

    @Test fun confirm_callsBack() {
        show(EatTimeEditorUiState())
        // Locate the confirm control per the sheet source (see task notes) and click it.
        TODO_CONFIRM_LOCATOR()
        assertEquals(1, confirm)
    }
}
```

`show()` twice in one test recomposes over the first content only if `setContent` is allowed twice — it is NOT; split `locationRow_showsStatusAndClears` into two tests (loading; set+clear). Replace `TODO_CONFIRM_LOCATOR()` with the real locator after reading the sheet (e.g. `compose.onNodeWithText(str(Res.string.<confirm key>)).performClick()` or a content-description lookup); the lat/lng text format must match the sheet's `fmt5` — read it and adjust the expected string (the sheet may render `"lat, lng"` with a different separator). The order of the two remove controls follows the sheet's layout (existing first or pending first) — read and adjust the assertions.

- [ ] **Step 2: Run the class on the emulator; commit**

```bash
git add app/src/androidTest/java/com/crazystudio/sportrecorder/ui
git commit -m "test(ui): Compose tests for the meal editor sheet

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 4: `BitmapPipelineTest`

**Files:**
- Create: `app/src/androidTest/java/com/crazystudio/sportrecorder/util/BitmapPipelineTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.crazystudio.sportrecorder.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BitmapPipelineTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dir: File

    @Before fun setUp() { dir = File(context.cacheDir, "pipeline-${UUID.randomUUID()}").apply { mkdirs() } }
    @After fun tearDown() { dir.deleteRecursively() }

    /** A solid JPEG of [w]×[h] with an optional EXIF orientation tag. */
    private fun jpeg(w: Int, h: Int, orientation: Int? = null): File {
        val file = File(dir, "src-${UUID.randomUUID()}.jpg")
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }.let { bmp ->
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bmp.recycle()
        }
        if (orientation != null) {
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                saveAttributes()
            }
        }
        return file
    }

    private fun decode(name: String): Triple<Int, Int, String?> {
        val opts = BitmapFactory.Options()
        val bmp = BitmapFactory.decodeFile(File(dir, name).absolutePath, opts)!!
        return Triple(bmp.width, bmp.height, opts.outMimeType).also { bmp.recycle() }
    }

    @Test fun rotate90_isAppliedThenScaledToLongEdge1280() {
        val src = jpeg(3000, 1500, ExifInterface.ORIENTATION_ROTATE_90)
        val name = decodeScaleEncode(dir) { src.inputStream() }
        val (w, h, mime) = decode(name)
        assertEquals(640, w); assertEquals(1280, h)
        assertEquals("image/webp", mime)
        assertTrue(name.endsWith(".webp"))
    }

    @Test fun smallImage_isNotUpscaled() {
        val src = jpeg(800, 600)
        val (w, h, _) = decode(decodeScaleEncode(dir) { src.inputStream() })
        assertEquals(800, w); assertEquals(600, h)
    }

    @Test fun rotate180_keepsAspect_andScales() {
        val src = jpeg(1500, 3000, ExifInterface.ORIENTATION_ROTATE_180)
        val (w, h, _) = decode(decodeScaleEncode(dir) { src.inputStream() })
        assertEquals(640, w); assertEquals(1280, h)
    }

    @Test fun outputLandsInGivenDirectory() {
        val src = jpeg(100, 100)
        val name = decodeScaleEncode(dir) { src.inputStream() }
        assertTrue(File(dir, name).let { it.exists() && it.length() > 0 })
    }
}
```

Implementer note: `decodeScaleEncode` uses `inSampleSize` when the long edge ≥ 2×1280, so a 3000-px source is sampled to 1500 first, then scaled — the final size is still 640×1280 (rounding via `roundToInt`; if you get 639/641 anywhere, assert with a ±1 tolerance and say so).

- [ ] **Step 2: Run the class on the emulator; commit**

```bash
git add app/src/androidTest/java/com/crazystudio/sportrecorder/util
git commit -m "test(photo): bitmap pipeline rotates by EXIF, downscales to 1280 and writes webp

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 5: Reminders on Android — scheduler PendingIntents + receiver broadcast

**Files:**
- Create: `app/src/androidTest/java/com/crazystudio/sportrecorder/reminder/AlarmReminderSchedulerTest.kt`
- Create: `app/src/androidTest/java/com/crazystudio/sportrecorder/reminder/ReminderReceiverTest.kt`

- [ ] **Step 1: Scheduler test**

```kotlin
package com.crazystudio.sportrecorder.reminder

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazystudio.sportrecorder.domain.reminder.ReminderType
import com.crazystudio.sportrecorder.domain.reminder.ScheduledReminder
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmReminderSchedulerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var scheduler: AlarmReminderScheduler

    @Before fun setUp() {
        scheduler = AlarmReminderScheduler(context, ReminderNotifier(context))
        scheduler.schedule(emptyList())
    }

    @After fun tearDown() { scheduler.schedule(emptyList()) }

    /** Same shape as production's slot: request code 1000 + ordinal, explicit ReminderReceiver intent + action. */
    private fun slot(type: ReminderType): PendingIntent? = PendingIntent.getBroadcast(
        context,
        1000 + type.ordinal,
        Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_FIRE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
    )

    @Test fun schedule_armsOnlyTheGivenTypes() {
        val soon = System.currentTimeMillis() + 60_000
        scheduler.schedule(listOf(ScheduledReminder(ReminderType.WINDOW_CLOSING, soon)))
        assertNotNull(slot(ReminderType.WINDOW_CLOSING))
        assertNull(slot(ReminderType.FAST_COMPLETE))
    }

    @Test fun schedule_bothTypes_thenEmpty_cancelsEverything() {
        val soon = System.currentTimeMillis() + 60_000
        scheduler.schedule(
            listOf(ScheduledReminder(ReminderType.WINDOW_CLOSING, soon), ScheduledReminder(ReminderType.FAST_COMPLETE, soon + 1)),
        )
        assertNotNull(slot(ReminderType.WINDOW_CLOSING)); assertNotNull(slot(ReminderType.FAST_COMPLETE))

        scheduler.schedule(emptyList())
        assertNull(slot(ReminderType.WINDOW_CLOSING)); assertNull(slot(ReminderType.FAST_COMPLETE))
    }

    @Test fun reschedule_replacesTheSlot() {
        val soon = System.currentTimeMillis() + 60_000
        scheduler.schedule(listOf(ScheduledReminder(ReminderType.FAST_COMPLETE, soon)))
        scheduler.schedule(listOf(ScheduledReminder(ReminderType.WINDOW_CLOSING, soon)))
        assertNull(slot(ReminderType.FAST_COMPLETE))
        assertNotNull(slot(ReminderType.WINDOW_CLOSING))
    }
}
```

`PendingIntent` matching ignores extras, so the probe intent needs only the component and action (production adds `EXTRA_TYPE`; that does not affect `FLAG_NO_CREATE` lookup).

- [ ] **Step 2: Receiver test**

```kotlin
package com.crazystudio.sportrecorder.reminder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.crazystudio.sportrecorder.backup.activeNotification
import com.crazystudio.sportrecorder.backup.awaitUntil
import com.crazystudio.sportrecorder.domain.reminder.RemindersRescheduler
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module

/** A [RemindersRescheduler] that records calls and lets a test await the first one. */
class RecordingRescheduler : RemindersRescheduler {
    val called = CompletableDeferred<Unit>()
    var count = 0
    override suspend fun reschedule() { count++; called.complete(Unit) }
}

@RunWith(AndroidJUnit4::class)
class ReminderReceiverTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var rescheduler: RecordingRescheduler

    @Before fun setUp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        NotificationManagerCompat.from(context).cancelAll()
        rescheduler = RecordingRescheduler()
        // Overrides the app's RemindersRescheduler for the rest of the instrumentation process.
        loadKoinModules(module { single<RemindersRescheduler> { rescheduler } })
    }

    @After fun tearDown() { NotificationManagerCompat.from(context).cancelAll() }

    private fun fire(typeName: String) = context.sendBroadcast(
        Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_FIRE)
            .putExtra(ReminderReceiver.EXTRA_TYPE, typeName),
    )

    @Test fun fastComplete_postsNotification_andReschedules() {
        fire("FAST_COMPLETE")
        awaitUntil(what = "fast-complete notification") { context.activeNotification(2002) != null }
        awaitUntil(what = "reschedule called") { rescheduler.called.isCompleted }
    }

    @Test fun unknownType_doesNothing() {
        fire("NOT_A_TYPE")
        Thread.sleep(1_500) // give a wrongly-behaving receiver time to act
        assertNull(context.activeNotification(2001)); assertNull(context.activeNotification(2002))
        assertFalse(rescheduler.called.isCompleted)
    }
}
```

The receiver is `exported="false"`; an explicit intent from the same package is delivered anyway. The notification ids come from `ReminderNotifier`'s `ReminderChannel` (2001/2002) — read the file to confirm before asserting. A later Koin override in the same process is fine because `loadKoinModules` replaces the definition; no other test in the suite resolves `RemindersRescheduler`.

- [ ] **Step 3: Run both classes on the emulator; commit**

```bash
git add app/src/androidTest/java/com/crazystudio/sportrecorder/reminder
git commit -m "test(reminders): alarm slots are armed and cancelled; the receiver notifies and reschedules

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 6: Backup small holes (commonTest) — golden JSON, cancel during apply, account-switch race

**Files:**
- Create: `shared/src/commonTest/kotlin/com/crazystudio/sportrecorder/backup/BackupDocumentGoldenTest.kt`
- Modify: `shared/src/commonTest/kotlin/com/crazystudio/sportrecorder/backup/fakes/FakeRepositories.kt` (`FakeEatRecordRepository.replaceAllGate`)
- Modify: `shared/src/commonTest/kotlin/com/crazystudio/sportrecorder/backup/BackupJobRunnerTest.kt`
- Modify: `shared/src/commonTest/kotlin/com/crazystudio/sportrecorder/ui/backup/BackupViewModelTest.kt`
- Possibly modify: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/ui/backup/BackupViewModel.kt`

- [ ] **Step 1: Golden fixture**

```kotlin
package com.crazystudio.sportrecorder.backup

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the on-disk shape of a schema-1 manifest. If this test fails, the wire format changed:
 * bump BackupDocument.SCHEMA_VERSION, keep decoding this fixture (older backups must restore), and
 * add a new fixture for the new version.
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
            BackupMeal(1, 1_699_999_000_000L, "lunch", BackupGeoPoint(25.03, 121.56), listOf(BackupPhoto(7, "a.webp", 1_699_999_001_000L))),
            BackupMeal(2, 1_699_990_000_000L, null, null, emptyList()),
        ),
        fastingTypes = listOf(BackupFastingType(18, 6, "my")),
        dietSettings = BackupDietSettings(16, 8),
        reminderPrefs = BackupReminderPrefs(true, false, 30, true, 1320, 480),
    )

    @Test fun schemaVersion_isStillOne() = assertEquals(1, BackupDocument.SCHEMA_VERSION)

    @Test fun golden_decodes() = assertEquals(expected, BackupJson.decodeFromString(BackupDocument.serializer(), golden))

    @Test fun golden_roundTrips() =
        assertEquals(golden, BackupJson.encodeToString(BackupDocument.serializer(), expected))
}
```

If `golden_roundTrips` fails only on key order or `null` emission, the fixture string must be adjusted to what `BackupJson` (`encodeDefaults = true`) actually emits — the point is to pin the real output, so paste the encoder's output as the golden and keep `golden_decodes` as the semantic check.

- [ ] **Step 2: Cancel during apply**

In `FakeEatRecordRepository` add `var replaceAllGate: CompletableDeferred<Unit>? = null` and make `replaceAll` start with `replaceAllGate?.await()`. In `BackupJobRunnerTest` add:

```kotlin
    @Test fun cancel_duringApply_stillCompletesTheRestore() = runTest(dispatcher) {
        val store = FakeBackupStore()
        store.seedSnapshot(SnapshotInfo("s", 1L, "0.7.1", 1L), emptyDocJson())
        val eat = FakeEatRecordRepository(listOf(EatRecord(9, 9L, null, "old", emptyList())))
        eat.replaceAllGate = CompletableDeferred()
        val runner = runner(service(store, eat))

        runner.startRestore("s")
        testScheduler.advanceUntilIdle() // parked inside the NonCancellable apply
        runner.cancel()
        testScheduler.advanceUntilIdle()
        assertIs<BackupJobState.Running>(runner.state.value) // apply is not cancellable

        eat.replaceAllGate!!.complete(Unit)
        testScheduler.advanceUntilIdle()
        assertEquals(BackupJobState.Finished(BackupJobKind.Restore, BackupOutcome.Completed), runner.state.value)
        assertEquals(emptyList(), eat.state.value) // the (empty) snapshot was applied
    }
```

Note the device has meals, so `restore` uploads a safety snapshot first (the fake store handles it); the gate is inside `replaceAll`, which runs inside `withContext(NonCancellable)`.

- [ ] **Step 3: Account-switch race (TDD; production fix only if RED)**

In `BackupViewModelTest` add:

```kotlin
    @Test fun accountChange_discardsListingStartedForThePreviousAccount() = runTest(dispatcher) {
        val store = FakeBackupStore()
        store.seedSnapshot(SnapshotInfo("old-acct", 1L, "0.7.1", 1L), emptyDocJson())
        store.listGate = CompletableDeferred()
        val auth = FakeBackupAuth(BackupAccount("me@x.com"))
        val vm = vm(store, auth = auth)
        testScheduler.advanceUntilIdle()

        vm.refreshSnapshots()
        testScheduler.advanceUntilIdle() // parked on listGate
        auth.accountState.value = BackupAccount("other@x.com")
        testScheduler.advanceUntilIdle()
        store.listGate!!.complete(Unit)
        testScheduler.advanceUntilIdle()

        assertEquals(emptyList<SnapshotInfo>(), vm.uiState.value.snapshots) // stale list must not land
        assertEquals(false, vm.uiState.value.isLoadingSnapshots)
    }
```

Run it. If it FAILS (stale list lands), fix `BackupViewModel`'s account collector: when the account differs, also `listGeneration++` so the in-flight refresh's result is discarded, and make sure `isLoadingSnapshots` is reset to `false` in that branch (the discarded refresh never resets it). Re-run: GREEN. If it PASSES without a change, no production edit — say so in the report.

- [ ] **Step 4: Run `:shared:jvmTest`, then the JVM gate; commit**

```bash
git add shared/src/commonTest shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/ui/backup/BackupViewModel.kt
git commit -m "test(backup): golden manifest fixture, cancel-during-apply, account-switch listing race

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```
(omit the ViewModel path if it did not change).

---

### Task 7: Full instrumented run, gate, docs, push

- [ ] **Step 1:** `:app:connectedDebugAndroidTest` on emulator-5556 → expect 13 (backup) + new tests, 0 failures. Record the count.
- [ ] **Step 2:** JVM gate green.
- [ ] **Step 3:** `docs/DEVELOPMENT.md` §5: update the 「測試現況」 bullet's file count and mention the new instrumented areas (Room migrations / repositories, 編輯器 UI, 照片管線, 提醒); note `exportSchema = true` and that `shared/schemas/` must be committed with every schema change. §6: add a row `| 09-28 | 測試覆蓋缺口(migration、Room 契約、編輯器 UI、照片管線、提醒、備份小洞) | ✓ | ✓ | 已完成 |`.
- [ ] **Step 4:** Commit docs; the controller pushes and opens the PR.

## Self-review

Spec §1 → T1; §2 → T2; §3 → T3; §4 → T4; §5 → T5; §6 → T6; docs → T7. Fixture names (`RoomHarness.eatRepo/fastingRepo/files/photoDao`, `ThrowingPhotoDao.failOnInsertNumber`, `RecordingPhotoFileStore.deleted`) are used consistently in T2. T5 reuses `awaitUntil`/`activeNotification` from the backup fixtures by import. Known placeholders the implementer must resolve by reading production: the editor's confirm locator, `fmt5` format and remove-control order (T3); the fasting-type cap (T2); `useReaderConnection` import names (T1); the golden string's exact encoder output (T6).
