# Eat-Record Editor + Notes + Card Record Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `note` to eat records, turn the create-only sheet into a create/edit sheet (renamed `EatTimeEditor*`) reachable by a pencil on each Record card, and redesign the Record screen as cards with full-width swipeable photos — no DB id shown.

**Architecture:** Room v5→v6 adds `eat_time.note`; `Route.EatTimeEditor(eatTimeId)` drives one sheet (id `0` = create, `>0` = edit, loading existing data + photos and UPDATE-ing on save); the Record screen becomes a `LazyColumn` of cards with a `HorizontalPager` photo carousel and a pencil that opens the editor.

**Tech Stack:** Room, Hilt (+ `SavedStateHandle` for the route arg), Jetpack Compose + Navigation-Compose (type-safe routes), Coil, `HorizontalPager`.

**Verification model:** Gate per task is `.\gradlew.bat assembleDebug` green, plus emulator run for the UI/migration tasks. Existing emulator DB is at **v5** — migrations must apply in place.

**Environment (project memory):** `java` not on PATH — before every Gradle command: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` then `.\gradlew.bat <tasks>` from `C:\Users\Aidan\SportRecorder`. Emulator `Pixel_10_Pro` (API 37); adb at `C:\Users\Aidan\AppData\Local\Android\Sdk\platform-tools\adb.exe`; screenshots to `%TEMP%` (screencap + adb pull, never `> file`). **Commit hygiene:** stage only named files (never `git add -A`); no screenshots/artifacts in the repo. Commit trailer on every commit: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.

**Branch:** `feat/eat-record-editor` (spec already committed here).

---

## Phase 1 — Schema + DAO (Room v5 → v6)

### Task 1.1: Add `note` column, `update`, `findWithPhotosById`, migration

**Files:**
- Modify: `entity/EatTime.kt`, `database/AppDatabase.kt`, `database/Migrations.kt`, `dao/EatTimeDao.kt`

- [ ] **Step 1: Add `note` to `entity/EatTime.kt`** — replace the data class with:

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

    @ColumnInfo(name = "note")
    val note: String? = null,
) {
    companion object {
        const val tableName = "eat_time"
    }
}
```

- [ ] **Step 2: Add DAO methods to `dao/EatTimeDao.kt`** — add `import androidx.room.Update` and `import androidx.room.Transaction` (if missing), and inside the interface:

```kotlin
    @Update
    suspend fun update(eatTime: EatTime)

    @Transaction
    @Query("SELECT * FROM ${EatTime.tableName} WHERE id = :id LIMIT 1")
    suspend fun findWithPhotosById(id: Int): com.crazystudio.sportrecorder.entity.EatTimeWithPhotos?
```

- [ ] **Step 3: Bump DB version in `database/AppDatabase.kt`** — change `version = 5` to `version = 6` (leave entities `[EatTime::class, FastingType::class, Photo::class]` unchanged).

- [ ] **Step 4: Add Migration 5→6 to `database/Migrations.kt`** — append a new object to the array returned by `getMigrations()` (read the current file; it already has 1→2 … 4→5):

```kotlin
            object : Migration(5, 6) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.runInTransaction {
                        execSQL("ALTER TABLE `eat_time` ADD COLUMN `note` TEXT")
                    }
                }
            }
```

- [ ] **Step 5: Build + run migration** — `.\gradlew.bat assembleDebug` → BUILD SUCCESSFUL; then `.\gradlew.bat installDebug` (do NOT uninstall) and launch `adb shell am start -n com.crazystudio.sportrecorder/.MainActivity`; `adb logcat -d -t 200` must show no Room/migration error. Existing records still show on the Record tab.

- [ ] **Step 6: Commit** — `git add app/src/main/java/com/crazystudio/sportrecorder/entity/EatTime.kt app/src/main/java/com/crazystudio/sportrecorder/dao/EatTimeDao.kt app/src/main/java/com/crazystudio/sportrecorder/database/AppDatabase.kt app/src/main/java/com/crazystudio/sportrecorder/database/Migrations.kt` → `git commit -m "[Feat] Room v6: eat_time.note + update/findWithPhotosById DAO"`.

---

## Phase 2 — Pure rename `CreateEatTime*` → `EatTimeEditor*`

### Task 2.1: Mechanical rename (no behavior change)

**Files (rename/move + update references):**
- Move/rename:
  - `ui/diet/create/eating/CreateEatTimeViewModel.kt` → `ui/diet/editor/EatTimeEditorViewModel.kt`
  - `ui/diet/create/eating/CreateEatTimeUiState.kt` → `ui/diet/editor/EatTimeEditorUiState.kt`
  - `ui/diet/create/eating/CreateEatTimeSheet.kt` → `ui/diet/editor/EatTimeEditorSheet.kt`
- Modify: `ui/nav/Routes.kt`, `ui/AppRoot.kt`, `ui/diet/DietScreen.kt` (if it references the route; it uses the `onAddEatTime` callback, so likely only AppRoot changes)

- [ ] **Step 1: Grep** the project for `CreateEatTime` to enumerate every reference.

- [ ] **Step 2: Move the three files** into a new package `com.crazystudio.sportrecorder.ui.diet.editor` and rename the classes:
  - `CreateEatTimeViewModel` → `EatTimeEditorViewModel`
  - `CreateEatTimeUiState` → `EatTimeEditorUiState` (and its nested `LatLng`/`LocationStatus` stay)
  - `CreateEatTimeSheet` → `EatTimeEditorSheet`
  Update each file's `package` line to `...ui.diet.editor` and all internal references. Keep ALL existing logic/method names exactly (this task is a pure rename — `createEatingTime()`, `addCapturedPhoto`, etc. unchanged).

- [ ] **Step 3: Update `ui/nav/Routes.kt`** — change the create route to a data class carrying the id:

```kotlin
    @Serializable data class EatTimeEditor(val eatTimeId: Int = 0) : Route
```
(Remove the old `@Serializable data object CreateEatTime : Route`.)

- [ ] **Step 4: Update `ui/AppRoot.kt`** — rename imports (`...ui.diet.editor.*`), change `bottomSheet<Route.CreateEatTime>` to `bottomSheet<Route.EatTimeEditor>`, change the VM type to `EatTimeEditorViewModel`, and change the Diet FAB navigation from `navigate(Route.CreateEatTime)` to `navigate(Route.EatTimeEditor())`. The Record `onEditRecord` is added in Phase 5 — for now leave Record wiring as-is. Keep the sheet's create-only callbacks unchanged (`onAddPhoto`/`onRemovePhoto`/`onConfirm` calling `vm.createEatingTime()` etc.).

- [ ] **Step 5: Build + run** — `.\gradlew.bat assembleDebug` → BUILD SUCCESSFUL with NO remaining `CreateEatTime` references (grep again). `.\gradlew.bat installDebug`; the create flow (FAB) must work exactly as before. No FATAL EXCEPTION.

- [ ] **Step 6: Commit** — stage the 3 renamed files (git detects the move) + `ui/nav/Routes.kt ui/AppRoot.kt` → `git commit -m "[Refactor] Rename CreateEatTime* to EatTimeEditor*; route carries eatTimeId"`.

---

## Phase 3 — Editor ViewModel (create + edit)

### Task 3.1: Rewrite `EatTimeEditorUiState` + `EatTimeEditorViewModel` for edit mode

**Files:** Modify `ui/diet/editor/EatTimeEditorUiState.kt`, `ui/diet/editor/EatTimeEditorViewModel.kt`, `ui/AppRoot.kt`

- [ ] **Step 1: Replace `EatTimeEditorUiState.kt`**

```kotlin
package com.crazystudio.sportrecorder.ui.diet.editor

import com.crazystudio.sportrecorder.entity.Photo
import java.util.Calendar

data class EatTimeEditorUiState(
    val date: Calendar,
    val isEditMode: Boolean = false,
    val note: String = "",
    val existingPhotos: List<Photo> = emptyList(),   // already-saved photos (edit mode)
    val pendingPhotos: List<String> = emptyList(),    // newly captured webp file names
    val location: LatLng? = null,
    val locationStatus: LocationStatus = LocationStatus.IDLE,
) {
    data class LatLng(val lat: Double, val lng: Double)
    enum class LocationStatus { IDLE, LOADING, AVAILABLE, UNAVAILABLE }
}
```

- [ ] **Step 2: Replace `EatTimeEditorViewModel.kt`**

```kotlin
package com.crazystudio.sportrecorder.ui.diet.editor

import android.content.Context
import androidx.lifecycle.SavedStateHandle
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
class EatTimeEditorViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val appDatabase: AppDatabase,
    private val eatTimeDao: EatTimeDao,
    private val photoDao: PhotoDao,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // Route arg: type-safe route field name "eatTimeId". 0 = create, >0 = edit.
    private val eatTimeId: Int = savedStateHandle.get<Int>("eatTimeId") ?: 0
    private val isEditMode: Boolean = eatTimeId > 0

    val currentCalendar: Calendar = Calendar.getInstance()
    private var committed = false
    private val photosToDelete = mutableListOf<Photo>()

    private val _uiState = MutableStateFlow(
        EatTimeEditorUiState(date = currentCalendar.clone() as Calendar, isEditMode = isEditMode)
    )
    val uiState: StateFlow<EatTimeEditorUiState> = _uiState.asStateFlow()

    init {
        if (isEditMode) {
            viewModelScope.launch {
                val record = eatTimeDao.findWithPhotosById(eatTimeId) ?: return@launch
                currentCalendar.timeInMillis = record.eatTime.time
                val loc = if (record.eatTime.lat != null && record.eatTime.lng != null)
                    EatTimeEditorUiState.LatLng(record.eatTime.lat, record.eatTime.lng) else null
                _uiState.update {
                    it.copy(
                        date = currentCalendar.clone() as Calendar,
                        note = record.eatTime.note.orEmpty(),
                        existingPhotos = record.photos,
                        location = loc,
                        locationStatus = if (loc != null)
                            EatTimeEditorUiState.LocationStatus.AVAILABLE
                        else EatTimeEditorUiState.LocationStatus.IDLE,
                    )
                }
            }
        }
    }

    fun setNote(value: String) = _uiState.update { it.copy(note = value) }

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

    /** Mark an already-saved photo for deletion; actual delete happens on save(). */
    fun removeExistingPhoto(photo: Photo) {
        photosToDelete.add(photo)
        _uiState.update { it.copy(existingPhotos = it.existingPhotos - photo) }
    }

    /** Re-capture (or first capture) the current location. */
    fun requestLocation() {
        _uiState.update { it.copy(locationStatus = EatTimeEditorUiState.LocationStatus.LOADING) }
        viewModelScope.launch {
            val result = LocationProvider.currentLocation(appContext)
            _uiState.update {
                if (result == null) it.copy(location = null, locationStatus = EatTimeEditorUiState.LocationStatus.UNAVAILABLE)
                else it.copy(
                    location = EatTimeEditorUiState.LatLng(result.first, result.second),
                    locationStatus = EatTimeEditorUiState.LocationStatus.AVAILABLE,
                )
            }
        }
    }

    fun clearLocation() = _uiState.update {
        it.copy(location = null, locationStatus = EatTimeEditorUiState.LocationStatus.IDLE)
    }

    fun locationDenied() = _uiState.update {
        it.copy(locationStatus = EatTimeEditorUiState.LocationStatus.UNAVAILABLE)
    }

    /** INSERT (create) or UPDATE (edit) the eat record and reconcile photos. */
    suspend fun save(): Boolean {
        if (currentCalendar.timeInMillis > Calendar.getInstance().timeInMillis) return false
        val state = _uiState.value
        val note = state.note.ifBlank { null }
        appDatabase.withTransaction {
            val id: Int = if (isEditMode) {
                eatTimeDao.update(
                    EatTime(id = eatTimeId, time = currentCalendar.timeInMillis,
                        lat = state.location?.lat, lng = state.location?.lng, note = note)
                )
                photosToDelete.forEach { photoDao.delete(it) }
                eatTimeId
            } else {
                eatTimeDao.insert(
                    EatTime(time = currentCalendar.timeInMillis,
                        lat = state.location?.lat, lng = state.location?.lng, note = note)
                ).toInt()
            }
            val now = System.currentTimeMillis()
            state.pendingPhotos.forEach { name ->
                photoDao.insert(Photo(eatTimeId = id, fileName = name, createdAt = now))
            }
        }
        // File deletes are not transactional — only after the DB transaction succeeds.
        photosToDelete.forEach { PhotoStorage.deleteByName(appContext, it.fileName) }
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

    private fun publishDate() = _uiState.update { it.copy(date = currentCalendar.clone() as Calendar) }

    override fun onCleared() {
        super.onCleared()
        if (!committed) {
            // Dismissed without saving — delete only the newly-captured (unsaved) files.
            _uiState.value.pendingPhotos.forEach { PhotoStorage.deleteByName(appContext, it) }
        }
    }
}
```

- [ ] **Step 3: Update `ui/AppRoot.kt` `bottomSheet<Route.EatTimeEditor>`** — the VM now reads the id from `SavedStateHandle` automatically (no change needed to obtain it), but the confirm callback must call `vm.save()` instead of `vm.createEatingTime()`. Change:
  - `onConfirm = { scope.launch { if (vm.createEatingTime()) navController.popBackStack() } }` → `... if (vm.save()) ...`.
  - Do NOT auto-request location on open in edit mode. Replace the unconditional `LaunchedEffect(Unit) { locationPermLauncher.launch(...) }` with one that only auto-requests when creating:
    ```kotlin
    val state by vm.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        if (!state.isEditMode) locationPermLauncher.launch(
            arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }
    ```
  - Leave the sheet's other callbacks as they are for now (the full sheet UI lands in Phase 4). The sheet still compiles because the new UiState fields have defaults.

- [ ] **Step 4: Build + run** — `.\gradlew.bat assembleDebug` → BUILD SUCCESSFUL. `.\gradlew.bat installDebug`; create flow still works (FAB → capture → CREATE → record appears). Edit isn't reachable yet (pencil added in Phase 5) — that's expected.

- [ ] **Step 5: Commit** — `git add app/src/main/java/com/crazystudio/sportrecorder/ui/diet/editor/EatTimeEditorUiState.kt app/src/main/java/com/crazystudio/sportrecorder/ui/diet/editor/EatTimeEditorViewModel.kt app/src/main/java/com/crazystudio/sportrecorder/ui/AppRoot.kt` → `git commit -m "[Feat] EatTimeEditor VM: create + edit (note, location re-capture/clear, photo staging)"`.

---

## Phase 4 — Editor sheet UI (note, SAVE, location actions, existing photos)

### Task 4.1: Rebuild `EatTimeEditorSheet` and wire new callbacks

**Files:** Modify `ui/diet/editor/EatTimeEditorSheet.kt`, `ui/AppRoot.kt`

- [ ] **Step 1: Update the sheet signature + body.** Read the current `EatTimeEditorSheet.kt` (the renamed create-only sheet) and keep its `HeaderRow` helper + date/time/add-photo/pending-photo blocks. New signature:

```kotlin
@Composable
fun EatTimeEditorSheet(
    state: EatTimeEditorUiState,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
    onNoteChange: (String) -> Unit,
    onAddPhoto: () -> Unit,
    onRemovePendingPhoto: (String) -> Unit,
    onRemoveExistingPhoto: (com.crazystudio.sportrecorder.entity.Photo) -> Unit,
    onRecaptureLocation: () -> Unit,
    onClearLocation: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
)
```
Build the column:
  1. DATE row + TIME row (unchanged).
  2. **NOTE field**: an `OutlinedTextField(value = state.note, onValueChange = onNoteChange, label = { Text("Note") }, modifier = Modifier.fillMaxWidth(), minLines = 2)` with dark-theme colors (`OutlinedTextFieldDefaults.colors` using `white`/`light_green`).
  3. **LOCATION row**: show the same status text as today; replace the single inert action with two tappable actions — a re-capture icon (`R.drawable.ic_baseline_add_24` or a refresh drawable if present) calling `onRecaptureLocation`, and a clear icon (`R.drawable.ic_baseline_delete_24`) calling `onClearLocation` (only show clear when `state.location != null`).
  4. **ADD PHOTO row** (unchanged) → `onAddPhoto`.
  5. **EXISTING photos** `LazyRow` (edit mode): for `state.existingPhotos`, 80dp Coil `AsyncImage(model = PhotoStorage.fileFor(context, photo.fileName))` each with a delete overlay calling `onRemoveExistingPhoto(photo)`.
  6. **PENDING photos** `LazyRow` (unchanged): `state.pendingPhotos` with delete → `onRemovePendingPhoto`.
  7. **Confirm button**: label `if (state.isEditMode) "SAVE" else "CREATE"`.

- [ ] **Step 2: Wire the new callbacks in `ui/AppRoot.kt`** — in `bottomSheet<Route.EatTimeEditor>`, pass:
  - `onNoteChange = vm::setNote`
  - `onRemovePendingPhoto = vm::removePendingPhoto`
  - `onRemoveExistingPhoto = vm::removeExistingPhoto`
  - `onRecaptureLocation = { locationPermLauncher.launch(arrayOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)) }` (the existing launcher already calls `vm.requestLocation()` on grant)
  - `onClearLocation = vm::clearLocation`
  - keep `onPickDate/onPickTime/onAddPhoto/onConfirm` as wired in Phase 3.

- [ ] **Step 3: Build + run** — `.\gradlew.bat assembleDebug` → BUILD SUCCESSFUL. `.\gradlew.bat installDebug`; open the create sheet (FAB): the note field, location re-capture/clear, and CREATE label all render and creating still works (now with an optional note). Screenshot to `%TEMP%\sr_editor.png`. No FATAL EXCEPTION.

- [ ] **Step 4: Commit** — `git add app/src/main/java/com/crazystudio/sportrecorder/ui/diet/editor/EatTimeEditorSheet.kt app/src/main/java/com/crazystudio/sportrecorder/ui/AppRoot.kt` → `git commit -m "[Feat] EatTimeEditor sheet: note field, SAVE label, location actions, existing photos"`.

---

## Phase 5 — Record cards + pencil → edit

### Task 5.1: Card layout, HorizontalPager photos, pencil edit wiring

**Files:** Modify `ui/diet/record/RecordScreen.kt`, `ui/AppRoot.kt`

- [ ] **Step 1: Add `onEditRecord` + redesign cards in `RecordScreen.kt`.** Change the signature to:

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordScreen(
    records: List<EatTimeWithPhotos>,
    onDelete: (EatTime) -> Unit,
    onEditRecord: (Int) -> Unit,
    modifier: Modifier = Modifier,
)
```
Keep the `recordToDelete` delete `AlertDialog` and the `fullScreenPhoto` viewer exactly as they are. Replace `RecordRow` with a `RecordCard` rendered inside the `LazyColumn` (with vertical spacing/padding so cards read as separate). The card (drop the id):
  - A header `Row`: left = date (`yyyy/MM/dd`) + time (`HH:mm`) `Text`s; right = a pencil `Image`/`Icon` (`R.drawable.ic_baseline_edit_24` if present, else reuse an existing edit/pencil drawable — check `res/drawable`) with `.clickable { onEditRecord(record.eatTime.id) }`.
  - **Note** `Text` (only if `!record.eatTime.note.isNullOrBlank()`).
  - **Photo carousel** (only if `record.photos.isNotEmpty()`): a `HorizontalPager`:
    ```kotlin
    val pagerState = rememberPagerState(pageCount = { record.photos.size })
    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
        val photo = record.photos[page]
        AsyncImage(
            model = PhotoStorage.fileFor(context, photo.fileName),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,   // full width, keeps aspect ratio
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)               // cap very-tall images
                .clickable { onThumbnailClick(photo.fileName) },
        )
    }
    ```
    Below it, page dots when `record.photos.size > 1` (a `Row` of small `Box` circles, the active index from `pagerState.currentPage` tinted `light_green`, others `bg_black2`).
  - **Location** `Text` "📍 %.4f, %.4f" (only if lat/lng non-null) — unchanged.
  - Whole card: `combinedClickable(onClick = {}, onLongClick = { recordToDelete = record.eatTime })` for delete; a rounded `bg_black2`-tinted surface or a `Divider` between cards. (Pencil handles edit; long-press handles delete; photo tap handles full-screen — no gesture conflict.)
  Add imports: `androidx.compose.foundation.pager.HorizontalPager`, `androidx.compose.foundation.pager.rememberPagerState`, `androidx.compose.foundation.layout.heightIn`, etc.

- [ ] **Step 2: Wire `onEditRecord` in `ui/AppRoot.kt`** — in `composable<Route.Record>`:
```kotlin
                composable<Route.Record> {
                    val vm: DietRecordViewModel = hiltViewModel()
                    val records by vm.records.collectAsStateWithLifecycle()
                    RecordScreen(
                        records = records,
                        onDelete = vm::deleteEatTime,
                        onEditRecord = { id -> navController.navigate(Route.EatTimeEditor(eatTimeId = id)) },
                    )
                }
```

- [ ] **Step 3: Build + run + verify** — `.\gradlew.bat assembleDebug` → BUILD SUCCESSFUL. `.\gradlew.bat installDebug`. Create a record with a note + 2 photos + GPS; on the Record tab confirm the **card**: date/time + pencil, the note, a full-width swipeable carousel (swipe between the 2 photos, page dots update), location, no id. Tap the pencil → editor opens **pre-filled** (date/time/note/location/existing photos). Edit the note + time, remove one existing photo, add a new one, clear the location, tap **SAVE** → card updates correctly; the removed photo's file is gone (`adb shell ls /sdcard/Android/data/com.crazystudio.sportrecorder/files/photos`). Tap a photo → full-screen; long-press card → delete. Screenshot the card to `%TEMP%\sr_card.png`. No FATAL EXCEPTION.

- [ ] **Step 4: Commit** — `git add app/src/main/java/com/crazystudio/sportrecorder/ui/diet/record/RecordScreen.kt app/src/main/java/com/crazystudio/sportrecorder/ui/AppRoot.kt` → `git commit -m "[Feat] Record cards with swipeable photos; pencil opens editor"`.

---

## Phase 6 — Final verification

### Task 6.1: End-to-end smoke + release build

**Files:** none.

- [ ] **Step 1: Migration chain** — without uninstalling, `.\gradlew.bat installDebug`; the on-device DB migrates 5→6; existing records still show, no crash.
- [ ] **Step 2: Full flow** — create (with note + photos + GPS) → card shows correctly (swipe photos, no id) → pencil edit (change date/time/note, add/remove photos, re-capture then clear location) → SAVE → card reflects changes; removed photo files deleted; dismissing an edit without SAVE leaves existing photos intact (verify files) and discards a newly-captured one. Long-press delete removes the record + its photo files. Restart → data/photos persist.
- [ ] **Step 3: Release build** — `.\gradlew.bat assembleRelease` → BUILD SUCCESSFUL.
- [ ] **Step 4: Unit tests** — `.\gradlew.bat testDebugUnitTest` → BUILD SUCCESSFUL.
- [ ] **Step 5:** No commit (verification only); fix-and-commit per file if a gap is found.

---

## Self-Review (author check vs. spec)

- **Spec coverage:** note column + migration + DAO → Task 1.1; rename `CreateEatTime*`→`EatTimeEditor*` + route id → Task 2.1; create/edit VM (load, note, location re-capture/clear, existing/pending/toDelete staging, UPDATE) → Task 3.1; sheet UI (note, SAVE, location actions, existing photos) → Task 4.1; card layout (date/time+pencil, note, full-width swipeable HorizontalPager, location, no id) + pencil→edit + keep long-press delete & photo full-screen → Task 5.1; verification → Task 6.1. All spec sections map to tasks.
- **Placeholder scan:** schema/DAO/VM/UiState have full code; sheet + card tasks give concrete signatures + element checklists + the key `HorizontalPager`/`OutlinedTextField` snippets, referencing the exact current files to modify. No vague "handle it" steps.
- **Type/name consistency:** `EatTimeEditorUiState(date, isEditMode, note, existingPhotos, pendingPhotos, location, locationStatus)`, `EatTimeEditorViewModel.{setNote, addCapturedPhoto, removePendingPhoto, removeExistingPhoto, requestLocation, clearLocation, save}`, `Route.EatTimeEditor(eatTimeId)`, `RecordScreen(records, onDelete, onEditRecord)` are used identically across tasks. SavedStateHandle key `"eatTimeId"` matches the route field name. Room version 5→6 monotonic.
- **Buildability:** every task ends green; Phase 2 is a pure rename, Phase 3 leaves a working create flow with edit-load-only (full edit UI in Phase 4, reachability in Phase 5) — called out explicitly.
- **Residual uncertainty (flagged in-task):** exact pencil/refresh drawable names (5.1 / 4.1 — check `res/drawable`), and `HorizontalPager` height behavior for mixed-ratio photos (5.1 — capped at 360dp, verify visually).
