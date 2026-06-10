# Eat-Record Photos + GPS (drop food model) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the `FoodRecord` layer with multi-photo + single-GPS eat records: photos captured via the system camera, converted to webp in an app-private folder (DB stores relative filenames), and one location stored per eat record — structured so a future Google Drive backup is just "DB file + photos folder".

**Architecture:** Room v3→v4 adds a `photo` table + `eat_time.lat/lng` (additive, keeps food); the CreateEatTime sheet and Record screen are rewritten to capture/show photos+GPS; then v4→v5 drops `food_record` and all food code is removed. Photos: `ACTION_IMAGE_CAPTURE` + `FileProvider` → downscale 1280 / `WEBP` q80 in `getExternalFilesDir("photos")`. Location: `FusedLocationProviderClient`.

**Tech Stack:** Room, Hilt, Jetpack Compose + Navigation-Compose, Coil (thumbnails), play-services-location, FileProvider, kotlinx coroutines.

**Verification model:** UI/integration feature — gate per task is `.\gradlew.bat assembleDebug` green, plus emulator run for capture/display tasks. The emulator camera returns a test image; inject a mock GPS fix via Android Studio Extended Controls (Location) before testing.

**Environment (project memory):** `java` not on PATH — before every Gradle command: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` then `.\gradlew.bat <tasks>` from `C:\Users\Aidan\SportRecorder`. Emulator `Pixel_10_Pro` (API 37) is available; adb at `C:\Users\Aidan\AppData\Local\Android\Sdk\platform-tools\adb.exe`; screenshots via `adb shell screencap -p /sdcard/x.png` + `adb pull` to `%TEMP%` (never `> file`). **Commit hygiene:** stage only named files (never `git add -A`); no screenshots/artifacts in the repo.

**Branch:** `feat/eat-record-photos-gps` (spec already committed here).

**App package:** `com.crazystudio.sportrecorder` (applicationId same).

---

## Phase 1 — Dependencies, manifest, storage & location utilities

### Task 1.1: Add dependencies

**Files:** Modify `app/build.gradle`

- [ ] **Step 1: Add deps to the `dependencies { }` block**

```groovy
    implementation 'com.google.android.gms:play-services-location:21.3.0'
    implementation 'io.coil-kt:coil-compose:2.7.0'
    implementation 'androidx.exifinterface:exifinterface:1.3.7'
```

- [ ] **Step 2: Build** — `.\gradlew.bat assembleDebug` → BUILD SUCCESSFUL.

- [ ] **Step 3: Commit** — `git add app/build.gradle` then `git commit -m "[Feat] Add play-services-location, coil, exifinterface deps"` (trailer `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`).

### Task 1.2: FileProvider + permissions

**Files:** Create `app/src/main/res/xml/file_paths.xml`; Modify `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create `res/xml/file_paths.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <external-files-path name="captures" path="captures/" />
    <external-files-path name="photos" path="photos/" />
</paths>
```

- [ ] **Step 2: Add permissions + provider to `AndroidManifest.xml`** (add the two `<uses-permission>` before `<application>`, and the `<provider>` inside `<application>`):

```xml
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```
```xml
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
```

- [ ] **Step 3: Build** — `.\gradlew.bat assembleDebug` → BUILD SUCCESSFUL.

- [ ] **Step 4: Commit** — `git add app/src/main/AndroidManifest.xml app/src/main/res/xml/file_paths.xml` → `git commit -m "[Feat] Add location permissions and FileProvider"`.

### Task 1.3: PhotoStorage utility (capture temp + webp conversion + delete)

**Files:** Create `app/src/main/java/com/crazystudio/sportrecorder/util/PhotoStorage.kt`

- [ ] **Step 1: Create `PhotoStorage.kt`**

```kotlin
package com.crazystudio.sportrecorder.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/** App-private photo storage: webp files in getExternalFilesDir("photos"). DB stores only file names. */
object PhotoStorage {
    private const val MAX_EDGE = 1280
    private const val WEBP_QUALITY = 80

    fun photosDir(context: Context): File =
        File(context.getExternalFilesDir(null), "photos").apply { mkdirs() }

    fun capturesDir(context: Context): File =
        File(context.getExternalFilesDir(null), "captures").apply { mkdirs() }

    fun fileFor(context: Context, fileName: String): File = File(photosDir(context), fileName)

    /** Create a temp capture file + content Uri for ACTION_IMAGE_CAPTURE EXTRA_OUTPUT. */
    fun newCaptureTarget(context: Context): Pair<File, Uri> {
        val file = File(capturesDir(context), "capture_${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return file to uri
    }

    /**
     * Decode [tempFile], correct EXIF rotation, downscale to MAX_EDGE long edge, encode webp,
     * write into photosDir, delete the temp file, and return the saved file name.
     */
    fun convertToWebp(context: Context, tempFile: File): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(tempFile.absolutePath, bounds)
        val sample = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_EDGE * 2)
        val decoded = BitmapFactory.decodeFile(
            tempFile.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: throw IllegalStateException("Failed to decode capture: ${tempFile.absolutePath}")

        val rotated = applyExifRotation(tempFile, decoded)
        val scaled = scaleToMaxEdge(rotated, MAX_EDGE)

        val name = "${UUID.randomUUID()}.webp"
        FileOutputStream(File(photosDir(context), name)).use { out ->
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                Bitmap.CompressFormat.WEBP_LOSSY else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
            scaled.compress(format, WEBP_QUALITY, out)
        }
        if (scaled !== decoded) scaled.recycle()
        if (rotated !== decoded) rotated.recycle()
        decoded.recycle()
        tempFile.delete()
        return name
    }

    fun deleteByName(context: Context, fileName: String) {
        File(photosDir(context), fileName).delete()
    }

    private fun sampleSizeFor(w: Int, h: Int, target: Int): Int {
        var sample = 1
        var longEdge = max(w, h)
        while (longEdge / 2 >= target) { longEdge /= 2; sample *= 2 }
        return sample
    }

    private fun scaleToMaxEdge(src: Bitmap, maxEdge: Int): Bitmap {
        val longEdge = max(src.width, src.height)
        if (longEdge <= maxEdge) return src
        val ratio = maxEdge.toFloat() / longEdge
        return Bitmap.createScaledBitmap(src, (src.width * ratio).roundToInt(), (src.height * ratio).roundToInt(), true)
    }

    private fun applyExifRotation(file: File, bitmap: Bitmap): Bitmap {
        val orientation = ExifInterface(file.absolutePath)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val m = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }
}
```

- [ ] **Step 2: Build** — `.\gradlew.bat assembleDebug` → BUILD SUCCESSFUL.

- [ ] **Step 3: Commit** — `git add app/src/main/java/com/crazystudio/sportrecorder/util/PhotoStorage.kt` → `git commit -m "[Feat] Add PhotoStorage (capture temp + webp conversion)"`.

### Task 1.4: LocationProvider utility

**Files:** Create `app/src/main/java/com/crazystudio/sportrecorder/util/LocationProvider.kt`

- [ ] **Step 1: Create `LocationProvider.kt`**

```kotlin
package com.crazystudio.sportrecorder.util

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await

object LocationProvider {
    /** Returns the current (lat, lng) or null if unavailable. Caller must hold a location permission. */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(context: Context): Pair<Double, Double>? {
        return try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val loc = client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                ?: client.lastLocation.await()
            loc?.let { it.latitude to it.longitude }
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
    }
}
```

> `await()` comes from `kotlinx-coroutines-play-services`. If it is unresolved at build time, add `implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.1'` to `app/build.gradle` (matches the project's coroutines 1.7.1) and commit it with this task.

- [ ] **Step 2: Build** — `.\gradlew.bat assembleDebug` → BUILD SUCCESSFUL (add the coroutines-play-services dep if needed).

- [ ] **Step 3: Commit** — `git add app/src/main/java/com/crazystudio/sportrecorder/util/LocationProvider.kt app/build.gradle` → `git commit -m "[Feat] Add LocationProvider (FusedLocationProviderClient)"`.

---

## Phase 2 — Data model (additive Room v3 → v4)

### Task 2.1: Photo entity/DAO, EatTime lat/lng, relation, DB v4, migration

**Files:**
- Create: `entity/Photo.kt`, `dao/PhotoDao.kt`, `entity/EatTimeWithPhotos.kt`
- Modify: `entity/EatTime.kt`, `database/AppDatabase.kt`, `database/Migrations.kt`, `dagger/DatabaseModule.kt`

- [ ] **Step 1: Add lat/lng to `entity/EatTime.kt`** — replace the data class body with:

```kotlin
@Entity(tableName = EatTime.tableName)
data class EatTime(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "time")
    val time: Long,

    @ColumnInfo(name = "lat")
    val lat: Double? = null,

    @ColumnInfo(name = "lng")
    val lng: Double? = null,
) {
    companion object {
        const val tableName = "eat_time"
    }
}
```

- [ ] **Step 2: Create `entity/Photo.kt`**

```kotlin
package com.crazystudio.sportrecorder.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = Photo.tableName)
data class Photo(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "eat_time_id")
    val eatTimeId: Int,

    @ColumnInfo(name = "file_name")
    val fileName: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
) {
    companion object {
        const val tableName = "photo"
    }
}
```

- [ ] **Step 3: Create `entity/EatTimeWithPhotos.kt`** (Room relation POJO for the Record screen)

```kotlin
package com.crazystudio.sportrecorder.entity

import androidx.room.Embedded
import androidx.room.Relation

data class EatTimeWithPhotos(
    @Embedded val eatTime: EatTime,
    @Relation(parentColumn = "id", entityColumn = "eat_time_id")
    val photos: List<Photo>,
)
```

- [ ] **Step 4: Create `dao/PhotoDao.kt`**

```kotlin
package com.crazystudio.sportrecorder.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.crazystudio.sportrecorder.entity.Photo

@Dao
interface PhotoDao {
    @Insert
    suspend fun insert(photo: Photo): Long

    @Delete
    suspend fun delete(photo: Photo)

    @Query("SELECT * FROM photo WHERE eat_time_id = :eatTimeId")
    suspend fun findByEatTimeId(eatTimeId: Int): List<Photo>

    @Query("DELETE FROM photo WHERE eat_time_id = :eatTimeId")
    suspend fun deleteByEatTimeId(eatTimeId: Int)
}
```

- [ ] **Step 5: Update `database/AppDatabase.kt`** (bump to 4, add Photo entity + dao; KEEP FoodRecord for now):

```kotlin
@Database(entities = [EatTime::class, FastingType::class, FoodRecord::class, Photo::class], version = 4, exportSchema = false)
abstract class AppDatabase: RoomDatabase() {
    abstract fun getEatTimeDao(): EatTimeDao
    abstract fun getFastingTypeDao(): FastingTypeDao
    abstract fun getFoodRecordDao(): FoodRecordDao
    abstract fun getPhotoDao(): PhotoDao
}
```
(Add imports for `Photo` and `PhotoDao`.)

- [ ] **Step 6: Add Migration 3→4 to `database/Migrations.kt`** (additive only — do NOT drop food_record yet). Add a third `object : Migration(3, 4)` to the array:

```kotlin
            object : Migration(3, 4) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.runInTransaction {
                        execSQL("ALTER TABLE `eat_time` ADD COLUMN `lat` REAL")
                        execSQL("ALTER TABLE `eat_time` ADD COLUMN `lng` REAL")
                        execSQL("CREATE TABLE IF NOT EXISTS `photo` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `eat_time_id` INTEGER NOT NULL, `file_name` TEXT NOT NULL, `created_at` INTEGER NOT NULL DEFAULT 0)")
                    }
                }
            }
```

- [ ] **Step 7: Provide `PhotoDao` in `dagger/DatabaseModule.kt`** — add:

```kotlin
    @Provides
    @Singleton
    fun providePhotoDao(appDatabase: AppDatabase): PhotoDao {
        return appDatabase.getPhotoDao()
    }
```
(Add `import com.crazystudio.sportrecorder.dao.PhotoDao`.)

- [ ] **Step 8: Build + run migration** — `.\gradlew.bat assembleDebug` then `.\gradlew.bat installDebug`; launch the app on the existing DB (which is at v3). Expected: no crash (migration 3→4 applies cleanly). `adb logcat -d -t 200` shows no `IllegalStateException`/Room migration error. Existing eat records still appear on the Record tab.

- [ ] **Step 9: Commit** — stage the 3 new entity/dao files + the 4 modified files → `git commit -m "[Feat] Room v4: photo table + eat_time lat/lng (additive)"`.

---

## Phase 3 — Capture UI (CreateEatTime sheet → photos + GPS)

### Task 3.1: Rewrite CreateEatTimeUiState + CreateEatTimeViewModel

**Files:** Modify `ui/diet/create/eating/CreateEatTimeUiState.kt`, `ui/diet/create/eating/CreateEatTimeViewModel.kt`

- [ ] **Step 1: Replace `CreateEatTimeUiState.kt`**

```kotlin
package com.crazystudio.sportrecorder.ui.diet.create.eating

import java.util.Calendar

data class CreateEatTimeUiState(
    val date: Calendar,
    val pendingPhotos: List<String> = emptyList(), // webp file names already written to photosDir
    val location: LatLng? = null,
    val locationStatus: LocationStatus = LocationStatus.IDLE,
) {
    data class LatLng(val lat: Double, val lng: Double)
    enum class LocationStatus { IDLE, LOADING, AVAILABLE, UNAVAILABLE }
}
```

- [ ] **Step 2: Replace `CreateEatTimeViewModel.kt`** (drop all FoodRecord usage; in-memory pending photos; location; cleanup on cleared)

```kotlin
package com.crazystudio.sportrecorder.ui.diet.create.eating

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.crazystudio.sportrecorder.dao.EatTimeDao
import com.crazystudio.sportrecorder.dao.PhotoDao
import com.crazystudio.sportrecorder.database.AppDatabase
import com.crazystudio.sportrecorder.entity.EatTime
import com.crazystudio.sportrecorder.entity.Photo
import com.crazystudio.sportrecorder.util.LocationProvider
import com.crazystudio.sportrecorder.util.PhotoStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class CreateEatTimeViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val appDatabase: AppDatabase,
    private val eatTimeDao: EatTimeDao,
    private val photoDao: PhotoDao,
) : ViewModel() {

    val currentCalendar: Calendar = Calendar.getInstance()
    private var committed = false

    private val _uiState = MutableStateFlow(CreateEatTimeUiState(date = currentCalendar.clone() as Calendar))
    val uiState: StateFlow<CreateEatTimeUiState> = _uiState.asStateFlow()

    /** Convert a temp capture file to webp (off the main thread) and stage its file name. */
    fun addCapturedPhoto(tempFile: File) {
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { PhotoStorage.convertToWebp(appContext, tempFile) }
            _uiState.update { it.copy(pendingPhotos = it.pendingPhotos + name) }
        }
    }

    fun removePendingPhoto(fileName: String) {
        viewModelScope.launch(Dispatchers.IO) { PhotoStorage.deleteByName(appContext, fileName) }
        _uiState.update { it.copy(pendingPhotos = it.pendingPhotos - fileName) }
    }

    /** Call once the location permission is granted. */
    fun requestLocation() {
        _uiState.update { it.copy(locationStatus = CreateEatTimeUiState.LocationStatus.LOADING) }
        viewModelScope.launch {
            val result = LocationProvider.currentLocation(appContext)
            _uiState.update {
                if (result == null) it.copy(location = null, locationStatus = CreateEatTimeUiState.LocationStatus.UNAVAILABLE)
                else it.copy(
                    location = CreateEatTimeUiState.LatLng(result.first, result.second),
                    locationStatus = CreateEatTimeUiState.LocationStatus.AVAILABLE,
                )
            }
        }
    }

    fun locationDenied() {
        _uiState.update { it.copy(locationStatus = CreateEatTimeUiState.LocationStatus.UNAVAILABLE) }
    }

    suspend fun createEatingTime(): Boolean {
        if (currentCalendar.timeInMillis > Calendar.getInstance().timeInMillis) return false
        val state = _uiState.value
        appDatabase.withTransaction {
            val eatTimeId = eatTimeDao.insert(
                EatTime(time = currentCalendar.timeInMillis, lat = state.location?.lat, lng = state.location?.lng)
            ).toInt()
            val now = System.currentTimeMillis()
            state.pendingPhotos.forEach { name ->
                photoDao.insert(Photo(eatTimeId = eatTimeId, fileName = name, createdAt = now))
            }
        }
        committed = true
        return true
    }

    fun updateDate(year: Int, month: Int, dayOfMonth: Int) {
        currentCalendar.set(Calendar.YEAR, year)
        currentCalendar.set(Calendar.MONTH, month)
        currentCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
        publishDate()
    }

    fun updateTime(hourOfDay: Int, minute: Int) {
        currentCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
        currentCalendar.set(Calendar.MINUTE, minute)
        publishDate()
    }

    private fun publishDate() {
        _uiState.update { it.copy(date = currentCalendar.clone() as Calendar) }
    }

    override fun onCleared() {
        super.onCleared()
        if (!committed) {
            // Sheet dismissed without creating — delete the staged webp files.
            _uiState.value.pendingPhotos.forEach { PhotoStorage.deleteByName(appContext, it) }
        }
    }
}
```

- [ ] **Step 3: Build** — `.\gradlew.bat assembleDebug`. This will FAIL to compile `AppRoot.kt` / `CreateEatTimeSheet.kt` (old food params). That is expected — Tasks 3.2 and 3.3 fix them. Do NOT commit yet; commit at the end of Task 3.3 once the build is green. (This task's code is correct in isolation; the build gate is deferred to 3.3.)

### Task 3.2: Rewrite CreateEatTimeSheet (location + add-photo + thumbnails)

**Files:** Modify `ui/diet/create/eating/CreateEatTimeSheet.kt`

- [ ] **Step 1: Replace the file.** Keep the date/time `HeaderRow`s (copy them verbatim from the current file — same icons/strings/format). Replace the food section with: a location-status `HeaderRow` (icon `R.drawable.ic_baseline_location_on_24` if present, else reuse an existing icon — check `res/drawable`; show "Locating…", "lat, lng" to 5 decimals, or "No location" per `state.locationStatus`/`state.location`); an "Add photo" `HeaderRow` (icon `R.drawable.ic_baseline_add_24`, action launches `onAddPhoto`); a horizontal `LazyRow` of thumbnails for `state.pendingPhotos` using Coil `AsyncImage(model = PhotoStorage.fileFor(LocalContext.current, name))` 80dp squares, each with a small delete overlay calling `onRemovePhoto(name)`; then the CREATE `Button` calling `onConfirm`. New signature:

```kotlin
@Composable
fun CreateEatTimeSheet(
    state: CreateEatTimeUiState,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
    onAddPhoto: () -> Unit,
    onRemovePhoto: (String) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
)
```
Remove the `FoodRecord` import and the food rows. Keep the private `HeaderRow` composable. For the location text format use `String.format("%.5f, %.5f", lat, lng)`.

- [ ] **Step 2: (compiles after Task 3.3)** — no standalone build; verified in Task 3.3 Step 3.

### Task 3.3: Wire camera + location in AppRoot; build + verify

**Files:** Modify `ui/AppRoot.kt`

- [ ] **Step 1: Replace the `bottomSheet<Route.CreateEatTime>` block** with the camera + location wiring (keep the date/time picker dialogs exactly as they are now):

```kotlin
                bottomSheet<Route.CreateEatTime> {
                    val vm: CreateEatTimeViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    val context = LocalContext.current
                    val scope = rememberCoroutineScope()

                    // Hold the pending capture file across the camera launch.
                    var captureFile by remember { mutableStateOf<java.io.File?>(null) }
                    val cameraLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.TakePicture()
                    ) { success ->
                        val file = captureFile
                        if (success && file != null) vm.addCapturedPhoto(file)
                        else file?.delete()
                        captureFile = null
                    }
                    val locationPermLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions()
                    ) { result ->
                        if (result.values.any { it }) vm.requestLocation() else vm.locationDenied()
                    }
                    LaunchedEffect(Unit) {
                        locationPermLauncher.launch(
                            arrayOf(
                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                            )
                        )
                    }

                    CreateEatTimeSheet(
                        state = state,
                        onPickDate = { /* keep the existing DatePickerDialog block unchanged */ },
                        onPickTime = { /* keep the existing TimePickerDialog block unchanged */ },
                        onAddPhoto = {
                            val (file, uri) = PhotoStorage.newCaptureTarget(context)
                            captureFile = file
                            cameraLauncher.launch(uri)
                        },
                        onRemovePhoto = { name -> vm.removePendingPhoto(name) },
                        onConfirm = {
                            scope.launch { if (vm.createEatingTime()) navController.popBackStack() }
                        },
                    )
                }
```
Add imports: `androidx.activity.compose.rememberLauncherForActivityResult`, `androidx.activity.result.contract.ActivityResultContracts`, `androidx.compose.runtime.LaunchedEffect`, `androidx.compose.runtime.mutableStateOf`, `androidx.compose.runtime.remember`, `androidx.compose.runtime.setValue`, `com.crazystudio.sportrecorder.util.PhotoStorage`. Paste the real existing `DatePickerDialog`/`TimePickerDialog` bodies (from the current file) into `onPickDate`/`onPickTime`. Remove the `vm.foodCreateEatTimeId` / `onAddFood` / `onDeleteFood` references.

- [ ] **Step 2: Build** — `.\gradlew.bat assembleDebug` → BUILD SUCCESSFUL (Tasks 3.1–3.3 now compile together). The `CreateFoodRecord` route/sheet is still present and harmless (removed in Phase 5).

- [ ] **Step 3: Run + verify capture & GPS** — In Android Studio, set a mock location (Extended Controls → Location → set + Send). `.\gradlew.bat installDebug`; Home → FAB → grant the location permission → status shows coordinates. Tap "Add photo" → the emulator camera opens → take the test shot → a thumbnail appears. Tap CREATE → sheet dismisses. Verify the file exists and lat/lng saved:
  ```
  adb shell run-as com.crazystudio.sportrecorder ls files/photos
  ```
  (or `adb shell ls /sdcard/Android/data/com.crazystudio.sportrecorder/files/photos`) — expect a `<uuid>.webp`. Check `adb logcat -d -t 300` for no FATAL EXCEPTION.

- [ ] **Step 4: Commit** (Tasks 3.1–3.3 together) — `git add ui/diet/create/eating/CreateEatTimeUiState.kt ui/diet/create/eating/CreateEatTimeViewModel.kt ui/diet/create/eating/CreateEatTimeSheet.kt ui/AppRoot.kt` (full paths) → `git commit -m "[Feat] CreateEatTime: capture photos + GPS instead of food"`.

---

## Phase 4 — Record screen shows photos + location

### Task 4.1: DietRecordViewModel → EatTimeWithPhotos; RecordScreen thumbnails + viewer; cascade delete

**Files:** Modify `dao/EatTimeDao.kt`, `ui/diet/record/DietRecordViewModel.kt`, `ui/diet/record/RecordScreen.kt`

- [ ] **Step 1: Add a relation query to `EatTimeDao.kt`** (Room runs the @Relation automatically; add `@Transaction`):

```kotlin
    @androidx.room.Transaction
    @Query("SELECT * FROM ${EatTime.tableName} ORDER BY time DESC")
    fun flowAllWithPhotos(): kotlinx.coroutines.flow.Flow<List<com.crazystudio.sportrecorder.entity.EatTimeWithPhotos>>
```

- [ ] **Step 2: Update `DietRecordViewModel`** — expose `records: StateFlow<List<EatTimeWithPhotos>>` from `flowAllWithPhotos()` (`.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())`); inject `PhotoDao` + `@ApplicationContext Context`; change `deleteEatTime` to cascade:

```kotlin
    suspend fun deleteEatTime(eatTime: EatTime) {
        val photos = photoDao.findByEatTimeId(eatTime.id)
        photos.forEach { PhotoStorage.deleteByName(appContext, it.fileName) }
        photoDao.deleteByEatTimeId(eatTime.id)
        eatTimeDao.delete(eatTime)
    }
```
(Read the current file to keep its Hilt constructor style; add the needed imports/params.)

- [ ] **Step 3: Update `RecordScreen`** — change the signature to `records: List<EatTimeWithPhotos>` and `onDelete: (EatTime) -> Unit`. Each row: keep id/date/time columns (use `record.eatTime`), and below/beside them a `LazyRow` of Coil `AsyncImage` thumbnails (64dp) for `record.photos` (`model = PhotoStorage.fileFor(LocalContext.current, photo.fileName)`), plus a 📍 indicator + `String.format("%.4f, %.4f", lat, lng)` when `eatTime.lat != null`. Tapping a thumbnail sets `fullScreenPhoto by remember { mutableStateOf<String?>(null) }` and shows a full-screen `Dialog` with a large `AsyncImage` (tap to dismiss). Long-press still opens the existing delete `AlertDialog` → `onDelete(record.eatTime)`.

- [ ] **Step 4: Update the `composable<Route.Record>` call in `AppRoot.kt`** — it already passes `records` + `onDelete = vm::deleteEatTime`; types now flow through as `EatTimeWithPhotos`. No change needed beyond what compiles; adjust if the `key = { it.id }` needs `it.eatTime.id`.

- [ ] **Step 5: Build** — `.\gradlew.bat assembleDebug` → BUILD SUCCESSFUL.

- [ ] **Step 6: Run + verify** — `.\gradlew.bat installDebug`; create a record with a photo + GPS (Phase 3 flow), open the Record tab → the row shows the thumbnail + coordinates; tap the thumbnail → full-screen viewer; long-press → delete → row gone and the webp file removed (re-check `files/photos`). Screenshot to `%TEMP%`. No FATAL EXCEPTION.

- [ ] **Step 7: Commit** — `git add dao/EatTimeDao.kt ui/diet/record/DietRecordViewModel.kt ui/diet/record/RecordScreen.kt ui/AppRoot.kt` → `git commit -m "[Feat] Record screen shows photo thumbnails + GPS; cascade delete"`.

---

## Phase 5 — Remove the food model (Room v4 → v5)

### Task 5.1: Delete all FoodRecord code + drop the table

**Files:**
- Delete: `entity/FoodRecord.kt`, `dao/FoodRecordDao.kt`, `ui/diet/create/food/CreateFoodRecordSheet.kt`, `ui/diet/create/food/CreateFoodRecordViewMode.kt`
- Modify: `database/AppDatabase.kt`, `database/Migrations.kt`, `dagger/DatabaseModule.kt`, `ui/nav/Routes.kt`, `ui/AppRoot.kt`

- [ ] **Step 1: Grep first** — search `app/src/main` for `FoodRecord`, `food_record`, `CreateFoodRecord`, `Route.CreateFoodRecord`, `getFoodRecordDao`, `provideFoodRecordDao`. Every hit must be removed/updated in this task.

- [ ] **Step 2: Remove the route + AppRoot destination** — delete `@Serializable data class CreateFoodRecord(val eatTimeId: Long) : Route` from `ui/nav/Routes.kt`; delete the entire `bottomSheet<Route.CreateFoodRecord> { ... }` block and the `CreateFoodRecordSheet`/`CreateFoodRecordViewModel` imports from `ui/AppRoot.kt`.

- [ ] **Step 3: Delete food files**
```
git rm app/src/main/java/com/crazystudio/sportrecorder/entity/FoodRecord.kt app/src/main/java/com/crazystudio/sportrecorder/dao/FoodRecordDao.kt app/src/main/java/com/crazystudio/sportrecorder/ui/diet/create/food/CreateFoodRecordSheet.kt app/src/main/java/com/crazystudio/sportrecorder/ui/diet/create/food/CreateFoodRecordViewMode.kt
```

- [ ] **Step 4: Update `AppDatabase.kt`** — entities `[EatTime::class, FastingType::class, Photo::class]`, version `5`, remove `getFoodRecordDao()` and the `FoodRecord`/`FoodRecordDao` imports.

- [ ] **Step 5: Remove `provideFoodRecordDao` from `DatabaseModule.kt`** + its imports.

- [ ] **Step 6: Add Migration 4→5 to `Migrations.kt`** (drop the table now that the entity is gone):
```kotlin
            object : Migration(4, 5) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.runInTransaction {
                        execSQL("DROP TABLE IF EXISTS `food_record`")
                    }
                }
            }
```
(Remove the now-unused `FoodRecord` import from `Migrations.kt` if present.)

- [ ] **Step 7: Build** — `.\gradlew.bat clean assembleDebug` → BUILD SUCCESSFUL with zero `FoodRecord` references remaining.

- [ ] **Step 8: Run migration** — `.\gradlew.bat installDebug`; launch on the existing DB (now at v4) → migrates 4→5, no crash; app works (Home/Record/create flow). `adb logcat -d -t 200`: no Room error.

- [ ] **Step 9: Commit** — stage the deletions + 5 modified files → `git commit -m "[Feat] Remove FoodRecord model; drop food_record table (Room v5)"`.

---

## Phase 6 — Final verification

### Task 6.1: End-to-end smoke + release build

**Files:** none.

- [ ] **Step 1: Fresh-install migration check** — keep app data (do NOT uninstall) so the on-device DB exercises 3→4→5. `.\gradlew.bat installDebug`; launch; confirm existing eat records still listed, no crash.

- [ ] **Step 2: Full flow** — set a mock GPS; FAB → grant location → capture 2 photos → CREATE; Record tab shows the row with 2 thumbnails + coords; open full-screen viewer; long-press delete removes row + both webp files (`adb shell ls .../files/photos`). Diet home timer/ring still works. Restart the app → data + photos persist.

- [ ] **Step 3: Storage-layout check (backup readiness)** — confirm `files/photos/` holds only `<uuid>.webp` files and the DB rows reference them by name (so "DB + photos folder" is a self-contained backup unit).

- [ ] **Step 4: Release build** — `.\gradlew.bat assembleRelease` → BUILD SUCCESSFUL.

- [ ] **Step 5: Unit tests** — `.\gradlew.bat testDebugUnitTest` → BUILD SUCCESSFUL.

- [ ] **Step 6:** No commit (verification only); fix-and-commit per screen if a gap is found.

---

## Self-Review (author check vs. spec)

- **Spec coverage:** model change → Tasks 2.1 (add) + 5.1 (drop food, v5); storage layout → 1.3 (photos dir + relative names) + 6.1 Step 3; image pipeline → 1.3 (FileProvider temp, downscale 1280 / webp 80, EXIF); location → 1.4 + 3.1/3.3 (FusedLocation, permission, per-record); UI → 3.1–3.3 (sheet) + 4.1 (record thumbnails + viewer); remove CreateFoodRecord → 5.1; staging/cleanup → 3.1 (`onCleared`); permissions/manifest → 1.2; out-of-scope (Drive/geocoding) untouched. All spec sections map to tasks.
- **Placeholder scan:** infra/data/utils have full code; UI tasks reference the exact current files (provided in context) with concrete signatures + element checklists, not vague "handle it" steps. The `onPickDate/onPickTime` bodies are explicitly "keep the existing block".
- **Type/name consistency:** `Photo(eatTimeId, fileName, createdAt)`, `EatTime(…, lat, lng)`, `EatTimeWithPhotos(eatTime, photos)`, `CreateEatTimeUiState(date, pendingPhotos, location, locationStatus)`, `PhotoStorage.{photosDir,fileFor,newCaptureTarget,convertToWebp,deleteByName}`, `LocationProvider.currentLocation` are used identically across tasks. Room version steps 3→4 (Task 2.1) then 4→5 (Task 5.1) are monotonic.
- **Buildability:** every task ends green except the intentional 3.1→3.3 trio (committed together at 3.3), which is called out explicitly.
- **Residual uncertainty (flagged in-task):** the location-status drawable name (3.2 — check `res/drawable`), and whether `kotlinx-coroutines-play-services` must be added for `await()` (1.4).
