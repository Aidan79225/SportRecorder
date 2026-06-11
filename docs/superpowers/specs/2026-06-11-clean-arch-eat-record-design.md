# Clean Architecture — eat-time/diet 垂直切片 設計文件

- 日期：2026-06-11
- 範圍：以 **Clean Architecture** 為 SportRecorder 導入 domain / data / presentation 分層，
  並**只**把 **eat-time（飲食紀錄）這條垂直切片**端到端改寫完成：domain models、repository 介面、
  mappers、repository 實作、use cases，並重構 `DietViewModel` / `EatTimeEditorViewModel` /
  `DietRecordViewModel` 改依賴 use cases。先證明 pattern（CI 綠燈的小 PR），後續再以同樣 pattern
  推進 fasting-type 與 prefs 寫入路徑。
- 不在本案：multi-module 切割、DataStore 遷移、fasting-type / `SelectFastingTypeViewModel` 重構、
  ViewModel/Flow 測試（Turbine）、把 `entity/`/`dao/`/`database/` 實體搬到 `data/local`。

## 背景與目的

現況**沒有任何 repository 抽象**：5 個 ViewModel 直接注入 Room DAO / `AppDatabase` /
`DietPreference`（SharedPreferences）/ `Context`，而且 Room `@Entity`（`EatTime`、`Photo`、
`EatTimeWithPhotos`）直接洩漏進 `UiState`（`EatTimeEditorUiState.existingPhotos: List<Photo>`）與
畫面參數（`RecordScreen(records: List<EatTimeWithPhotos>, onDelete: (EatTime))`）。這讓 ViewModel
無法在不碰 Room 的情況下測試，也讓 DB schema 變動會直接波及 UI。

使用者已選定的架構決策：
- **strictness**：Full Clean —— 加入 use-case / interactor 層。
- **structure**：package-based，維持單一 `:app` 模組（不切 multi-module，沿用先前 YAGNI 決定）。
- **models**：pure domain models + mappers（ViewModel/UI 不再見到 `@Entity`）。
- **prefs**：先用 repository 介面包住 `DietPreference`，**暫不**遷移 DataStore（之後再換實作，不動 VM）。
- **sequencing**：先做 eat-time 垂直切片（本案）。

已知環境：單一 `:app`、KTS + version catalog、Hilt、Room v6、Compose、detekt（無 baseline）+ Android
Lint（有 baseline）、CI 閘門 = `assembleDebug testDebugUnitTest :app:detekt :app:lintDebug`。
既有純邏輯單元 `ui/diet/DietWindow.kt`（已被 `DietWindowTest` 覆蓋）是「乾淨、可測、無框架依賴」的範本。

## 1. 套件結構（單一 `:app`，package-based 分層）

於 `com.crazystudio.sportrecorder` 新增：

```
domain/
  model/        EatRecord, EatPhoto, GeoPoint, DietSettings        (無框架依賴)
  diet/         DietWindow（由 ui/diet 移入）, DietHistoryCalculator（從 VM 抽出、純函式）
  repository/   EatRecordRepository, DietSettingsRepository        (介面)
  usecase/      ObserveEatRecordsUseCase, LoadEatRecordUseCase, SaveEatRecordUseCase,
                DeleteEatRecordUseCase, ObserveDietStateUseCase
data/
  mapper/       EatRecordMappers（Room entity ↔ domain）
  repository/   EatRecordRepositoryImpl, DietSettingsRepositoryImpl
```

既有 `entity/`、`dao/`、`database/`、`util/PhotoStorage|LocationProvider|DietPreference`
**維持原位**——它們是 impl 依賴的 data source。（日後可再把它們實體移到 `data/local`；現在搬只增加
import 變動、無行為收益，故刻意不搬，於此明確記載。）

## 2. Domain models（無 `@Entity`、無 Android）

```kotlin
data class EatRecord(
    val id: Int,                 // 0 = 尚未持久化
    val time: Long,              // epoch millis（與 DB 一致、無框架依賴）
    val location: GeoPoint?,
    val note: String?,
    val photos: List<EatPhoto>,
)
data class GeoPoint(val lat: Double, val lng: Double)
data class EatPhoto(val id: Int, val fileName: String, val createdAt: Long)
data class DietSettings(val fastingHours: Long, val eatingHours: Long)
```

## 3. Repository 介面（domain）

```kotlin
interface EatRecordRepository {
    fun observeAll(): Flow<List<EatRecord>>                              // Record 畫面（含照片）
    fun observeInWindow(after: Long, before: Long): Flow<List<EatRecord>> // Diet 歷史 + 當前 window
    suspend fun findById(id: Int): EatRecord?                            // Editor 載入
    suspend fun save(
        id: Int,                       // 0 = 新增；>0 = 更新
        time: Long,
        location: GeoPoint?,
        note: String?,
        newPhotoFileNames: List<String>, // 新拍待寫入的 webp 檔名
        removedPhotos: List<EatPhoto>,   // 編輯時要刪的既有照片（rows + 檔案）
    ): Int                              // 回傳 record id
    suspend fun delete(recordId: Int)  // 刪 record + 其照片 rows + 檔案
}

interface DietSettingsRepository {
    val settings: Flow<DietSettings>   // 本案只讀；寫入路徑屬 prefs/fasting 切片
}
```

- `save` / `delete` 內含 Room transaction **與** 照片檔案生命週期（impl 依賴 `PhotoStorage`）——
  即把今日 `EatTimeEditorViewModel.save()` / `DietRecordViewModel.deleteEatTime()` 的邏輯原樣移入。
- `DietSettingsRepositoryImpl` 內以 `callbackFlow` 包住 `DietPreference` 的
  `OnSharedPreferenceChangeListener`（即今日 `DietViewModel.prefsFlow`），讀出
  `DIET_FASTING_TIME_INTERVAL` / `DIET_EATING_TIME_INTERVAL`（預設 16 / 8）對應到 `DietSettings`。

### 明確的 seam（照片）

照片在「拍照當下」的暫存轉檔（`PhotoStorage.convertToWebp`）與「顯示用」的路徑解析
（`PhotoStorage.fileFor`，於 Composable 內供 Coil 載圖）**維持為輕量 util 呼叫**，不經 repository：
它們綁定相機 intent 流程與圖片載入，屬 UI/工具性質，無業務邏輯。Repository 擁有的是**持久化邊界**
（存檔時寫入、刪除時清檔）。此切片刻意不把 FileProvider/`Uri` 透過 DI 串接，於此明確記載。

## 4. Use cases（使用者選的「full Clean」層）

皆為 `@Inject constructor`、單一職責、薄：
- `ObserveEatRecordsUseCase` → `repo.observeAll()`（供 `DietRecordViewModel`）。
- `LoadEatRecordUseCase(id)` → `repo.findById(id)`（供 Editor `init`）。
- `SaveEatRecordUseCase` → **業務規則在此**：拒絕「時間在未來」的紀錄（今日 VM 內
  `currentCalendar > now` 的檢查），通過後委派 `repo.save(...)`。回傳成功 / 失敗（Boolean）。
- `DeleteEatRecordUseCase(id)` → `repo.delete(id)`。
- `ObserveDietStateUseCase` → 以 `combine` 合併 `repo.observeInWindow(after, before)` 與
  `settings`，呼叫 `DietHistoryCalculator`（從 `DietViewModel` 抽出的歷史聚合：`mergeIntervalWithFourHours`
  / `effectiveProgress` / `computeHistory`），輸出 `DietSnapshot(eatTimesAsc, settings, historyBars)`。

`DietViewModel` 再把 `DietSnapshot` 與自身的「每秒 ticker」`combine`，呼叫**已是純函式且已被測**的
`DietWindow.compute(now)`，最後映射成 `DietUiState`（字串格式化與 `R.string` 資源 id 屬 presentation，
留在 VM）。

## 5. Mappers + DI

- `data/mapper/EatRecordMappers.kt`（extension 函式）：`EatTimeWithPhotos.toDomain(): EatRecord`、
  `Photo.toDomain(): EatPhoto`、以及 `save` 時 domain → `EatTime` entity 的組裝。
- 新增 Hilt `RepositoryModule`（`@Binds` 抽象 module）：
  `EatRecordRepository` → `EatRecordRepositoryImpl`、`DietSettingsRepository` → `DietSettingsRepositoryImpl`。
  use cases 以建構子注入、不需 module。impl 以 `@Inject constructor` 注入既有 `EatTimeDao` /
  `PhotoDao` / `AppDatabase` /（`@ApplicationContext`）`Context`（給 `PhotoStorage`）/ `DietPreference`。

## 6. ViewModel / UI 變更（修正邊界換來的好處）

- **DietRecordViewModel**：注入 `ObserveEatRecordsUseCase` + `DeleteEatRecordUseCase`；
  移除 `EatTimeDao`、`PhotoDao`、`Context`。`records: StateFlow<List<EatRecord>>`。
- **EatTimeEditorViewModel**：注入 `LoadEatRecordUseCase` + `SaveEatRecordUseCase`
  （拍照暫存仍保留 `PhotoStorage` + `Context`）；移除 `AppDatabase`、`EatTimeDao`、`PhotoDao`。
- **DietViewModel**：注入 `ObserveDietStateUseCase`；移除 `EatTimeDao` 與
  `DietPreference`/`SharedPreferences`（prefs 監聽移入 `DietSettingsRepositoryImpl`）。
- **UiState / 畫面** 由 Room 型別改為 domain 型別：
  - `EatTimeEditorUiState.existingPhotos: List<EatPhoto>`、`onRemoveExistingPhoto(EatPhoto)`。
  - `RecordScreen(records: List<EatRecord>, onDelete: (EatRecord), onEditRecord: (Int))`、
    `RecordCard(record: EatRecord, ...)`。
  - 完成後 `EatTimeDao` / `PhotoDao` **只**被 `EatRecordRepositoryImpl` 引用。

## 7. 測試（本案做 proof-of-seam，完整套件留後續）

於 version catalog 新增 `kotlinx-coroutines-test`（對齊既有 `coroutines = "1.7.1"`）。撰寫此次重構**解鎖的
高價值純測試**（不需 Android runtime）：
- `DietHistoryCalculatorTest` —— 先前埋在 VM、無法測的歷史聚合（含空資料、跨日合併、4 小時下限）。
- `SaveEatRecordUseCaseTest` —— 以手寫 fake `EatRecordRepository` 驗證「未來時間被拒」與「合法時間委派 repo」。
- `EatRecordMappersTest` —— entity ↔ domain round-trip（含 `note`/`location` 為 null、多張照片）。

完整的 ViewModel/Flow 測試（Turbine + `MainDispatcherRule`）屬**下一個 spec（「real tests」）**，不在本案。

## 8. 分階段（單一 spec、分階段 plan；每階段可建置）

1. **Scaffold**：domain models + repo 介面 + mappers + impls + `RepositoryModule` + 把 `DietWindow`
   移入 `domain/diet/`、抽出 `DietHistoryCalculator`。app 仍接舊 VM，build 綠燈。新增 `coroutines-test`
   與 §7 三支純測試。
2. **Use cases**：新增 §4 的 5 個 use case。build 綠燈。
3. **重構 VM + UI**：三個 VM 改依賴 use cases / domain models；刪除已無用的 DAO/DB/prefs/Context 注入；
   更新 `UiState` 與畫面簽章。`assembleDebug` + `:app:check` 綠燈；emulator 巡 Home / Record / Editor 三畫面。

## 9. 風險與注意

- **行為等價**：`save` / `delete` 的 transaction 與檔案刪除順序須與現況一致（DB 成功後才刪檔；
  未提交即離開時只刪「新拍未存」的暫存檔）。重構為搬移而非改寫，逐字對照。
- **DietViewModel 視窗**：歷史 bar 與當前 window 共用同一個 8 天查詢；抽 `DietHistoryCalculator` 時
  須保留 `flowByTimeInterval` 的 ASC 排序語意（`eatTimesAsc`）。
- **detekt 無 baseline**：新檔需過 detekt（避免 wildcard import、`MagicNumber`、`TooManyFunctions` 視情況
  以註解說明）。
- **Hilt `@Binds`**：`RepositoryModule` 用 `abstract class` + `@Binds`；勿與既有 `@Provides` object module 混用同一類。
- **照片 seam**：`PhotoStorage.fileFor` 仍在 Composable 內以 `LocalContext` 呼叫——維持不變，僅資料型別換 domain。

## 10. 驗證

- 環境見專案記憶（JAVA_HOME 指向 Android Studio JBR、`gradlew.bat`、`Pixel_10_Pro` AVD）。
- 每階段：`.\gradlew.bat assembleDebug :app:check` 綠燈（含 detekt 無 baseline、lint、單元測試）。
- `installDebug` 後逐畫面驗證行為不變：Home（圓環/狀態/時間資訊/歷史長條）、Record（卡片/刪除/全螢幕照片/
  編輯）、Editor（新增與編輯：日期/時間/note/location 重抓與清除/既有與新拍照片/SAVE 與 CREATE）。
- 反向確認：全專案 grep，`EatTimeDao` / `PhotoDao` 僅被 `EatRecordRepositoryImpl` 引用；
  ViewModel/UiState/畫面不再 import `com.crazystudio.sportrecorder.entity.*`（domain 型別取代之）。
- 新增三支純測試綠燈；CI 閘門（`assembleDebug testDebugUnitTest :app:detekt :app:lintDebug`）綠燈。

## 11. 明確排除（YAGNI）

- 不切 multi-module；不遷移 DataStore（prefs 維持 SharedPreferences，藏在介面後）。
- 不重構 fasting-type / `SelectFastingTypeViewModel`（下一切片）；不加 `DietSettingsRepository` 寫入方法。
- 不導入 `Result` 包裝框架（僅 `SaveEatRecordUseCase` 一處回傳 Boolean）。
- 不加 Turbine / ViewModel / 儀器化測試（屬「real tests」spec）。
