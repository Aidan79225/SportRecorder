# Clean Architecture — fasting-type 垂直切片 設計文件

- 日期：2026-06-12
- 範圍：以 eat-record 切片建立的同一套 Clean Architecture pattern，把 **fasting-type（斷食類型）** 這條
  垂直切片改寫完成：`SelectFastingTypeViewModel` 與 `CreateFastingTypeViewModel` 改依賴 use cases，
  並把 prefs 寫入路徑（`saveSelection`）**收進 `DietSettingsRepository`**。
- 不在本案：改動 `FastingItem`（presentation model）、三個畫面（`SelectFastingTypeScreen`/
  `CreateFastingTypeScreen`/`AppRoot`）、`DietViewModel`（已讀 settings）、DataStore 遷移、multi-module。

## 背景與目的

eat-record 切片（PR #14，已併入 master）已建立 `domain/`+`data/` 分層與 repository/use-case pattern，
但 fasting-type 的兩個 ViewModel 仍直接相依基礎設施：
- `SelectFastingTypeViewModel` 注入 `AppDatabase`（取 `FastingTypeDao`）與 `DietPreference`，
  `saveSelection` 直接寫 SharedPreferences。
- `CreateFastingTypeViewModel` 注入 `FastingTypeDao`，`createCustomFastingType` 內含業務規則
  （不可與 DB 既有重複、不可與內建預設重複）後 `insert`。

本案把這兩個 VM 移到 use-case 邊界，並補上 `DietSettingsRepository` 的寫入方法。沿用 eat-record 切片
已定案的架構決策：**Full Clean（含 use cases）**、**package-based 單一 `:app`**、**pure domain models +
mappers**、**prefs 藏在介面後（暫不遷 DataStore）**。

本切片**已選定**的兩個 slice-specific 決策：
- **domain 形狀**：單一 `FastingWindow(fastingHours, eatingHours)` value，內建預設與自訂類型共用。
- **預設 catalog 落點**：新增 domain `DefaultFastingWindows`（hours-only）作 dedup 業務規則的單一真相；
  UI `FastingItem` 保留 R.string 顯示名稱；以一支一致性測試鎖兩者不漂移。

已知環境：單一 `:app`、KTS + version catalog、Hilt、Room v6、Compose、detekt（無 baseline，
`MagicNumber` 在 `**/ui/**` 排除但於 `domain/`/`data/` 啟用）+ Android Lint（baseline）、
CI 閘門 = `assembleDebug testDebugUnitTest :app:detekt :app:lintDebug`。

## 1. Domain

- `domain/model/FastingWindow.kt`
  ```kotlin
  data class FastingWindow(val fastingHours: Long, val eatingHours: Long)
  ```
  同一個 value 用於：內建預設 catalog、使用者自訂類型清單、以及選取的視窗。UI 只顯示時數、dedup 也只比
  時數，故不需要 id/timestamp（YAGNI）。

- `domain/diet/DefaultFastingWindows.kt`
  ```kotlin
  @Suppress("MagicNumber") // declarative reference data; naming each hour as a const adds no clarity
  object DefaultFastingWindows {
      val all: List<FastingWindow> = listOf(
          FastingWindow(14, 10),
          FastingWindow(16, 8),
          FastingWindow(20, 4),
          FastingWindow(23, 1),
          FastingWindow(47, 1),
      )
  }
  ```
  dedup-vs-defaults 業務規則的單一真相。`@Suppress("MagicNumber")` 是刻意決定：這是宣告式參考資料，
  逐一具名（`const FOURTEEN = 14`）只會更糟。

- `domain/repository/FastingTypeRepository.kt`
  ```kotlin
  interface FastingTypeRepository {
      fun observeRecentCustomWindows(): Flow<List<FastingWindow>>  // 最近 N 筆，保留現有 recency 順序
      suspend fun exists(window: FastingWindow): Boolean           // dedup vs DB
      suspend fun add(window: FastingWindow)                       // 以 timestamp = now 寫入
  }
  ```

- `DietSettingsRepository`（既有介面）新增 prefs 寫入（即收進來的 `saveSelection`）：
  ```kotlin
  suspend fun setSelection(window: FastingWindow)
  ```
  寫入既有的 `DIET_FASTING_TIME_INTERVAL` / `DIET_EATING_TIME_INTERVAL` key；既有的 `settings`
  `callbackFlow` 會因 prefs 變更重新發射 → Home（`DietViewModel`）自動更新（已接好）。

## 2. Data

- `data/mapper/FastingTypeMappers.kt`：`fun FastingType.toDomain(): FastingWindow`。
- `data/repository/FastingTypeRepositoryImpl.kt`（`@Inject constructor(fastingTypeDao)`）：
  - `observeRecentCustomWindows()` = `fastingTypeDao.flowLast(RECENT_CUSTOM_LIMIT).map { it.map(FastingType::toDomain) }`，
    其中 `private const val RECENT_CUSTOM_LIMIT = 10`（對齊現況 `flowLast(10)`）。
  - `exists(window)` = `fastingTypeDao.findByHours(window.fastingHours, window.eatingHours).isNotEmpty()`。
  - `add(window)` = `fastingTypeDao.insert(FastingType(fastingHours = …, eatingHours = …, timestamp = System.currentTimeMillis()))`。
- `data/repository/DietSettingsRepositoryImpl.setSelection(window)` =
  `dietPreference.preference.edit().putLong(FASTING, window.fastingHours).putLong(EATING, window.eatingHours).apply()`。
- `dagger/RepositoryModule` 新增 `@Binds @Singleton FastingTypeRepository ← FastingTypeRepositoryImpl`。

## 3. Use cases（`domain/usecase/`）

- `ObserveCustomFastingTypesUseCase` → `repo.observeRecentCustomWindows()`（供 SelectFastingType 清單）。
- `CreateCustomFastingTypeUseCase(window): Boolean` —— **業務規則在此**：
  ```kotlin
  suspend operator fun invoke(window: FastingWindow): Boolean {
      if (window in DefaultFastingWindows.all || repository.exists(window)) return false
      repository.add(window)
      return true
  }
  ```
  恰 2 個 return（符合 detekt `ReturnCount` max 2）。回傳 Boolean 與現況一致。
- `SaveFastingSelectionUseCase(window)` → `dietSettingsRepository.setSelection(window)`（折入的 prefs 寫入）。

## 4. ViewModels（只改內部；對外 public API 不變 → 畫面/AppRoot 不動）

- `SelectFastingTypeViewModel`：注入 `ObserveCustomFastingTypesUseCase` + `SaveFastingSelectionUseCase`，
  移除 `AppDatabase`、`DietPreference`。
  - `fastingItemFlow: Flow<List<FastingItem>>` = `observeCustomFastingTypes().map { windows ->
    FastingItem.defaultFastingItems + windows.map { FastingItem.CustomFastingItem(it.fastingHours, it.eatingHours) }.reversed() }`
    —— 與現況相同的順序/反轉語意。
  - `saveSelection(fastingHours: Long, eatingHours: Long)` 簽章不變；內部
    `viewModelScope.launch { saveFastingSelection(FastingWindow(fastingHours, eatingHours)) }`。
- `CreateFastingTypeViewModel`：注入 `CreateCustomFastingTypeUseCase`，移除 `FastingTypeDao`。
  - `suspend fun createCustomFastingType(fastingHours: Long, eatingHours: Long): Boolean` 簽章不變；內部
    委派 `createCustomFastingType(FastingWindow(fastingHours, eatingHours))`。原本的 inline dedup 與
    `Dispatchers.Default` 包裝移除（Room suspend 函式 main-safe；defaults 比對為瑣碎計算）。
- 完成後 `FastingTypeDao` 只被 `FastingTypeRepositoryImpl` 引用。
- `AppRoot` 對這兩個 bottomSheet 的呼叫（`vm.fastingItemFlow`、`vm.saveSelection(f, e)`、
  `vm.createCustomFastingType(f, e)`）維持不變 → 不需修改。

## 5. 測試（三支純測試，免 Android runtime）

於既有 `kotlinx-coroutines-test` 之上：
- `FastingTypeMappersTest` —— entity → `FastingWindow`。
- `CreateCustomFastingTypeUseCaseTest` —— 手寫 fake `FastingTypeRepository`：拒絕內建預設窗、拒絕 DB 既有、
  接受新窗（並驗證 `add` 有/無被呼叫）。
- `DefaultFastingWindowsConsistencyTest` —— 鎖
  `FastingItem.defaultFastingItems.map { FastingWindow(it.fastingHours, it.eatingHours) } == DefaultFastingWindows.all`
  （順序一致），確保 UI 名稱目錄與 domain dedup 目錄不漂移。

## 6. 分階段（單一 spec、分階段 plan；每階段可建置）

1. **Scaffold**：`FastingWindow`、`DefaultFastingWindows`、`FastingTypeRepository` 介面/impl、mapper、
   `DietSettingsRepository.setSelection`（介面+impl）、`RepositoryModule` 綁定。加 mapper 與一致性測試。build 綠燈。
2. **Use cases**：三個 use case（+ `CreateCustomFastingTypeUseCaseTest`）。build 綠燈。
3. **重構兩個 VM**：改依賴 use cases；移除 DAO/DB/prefs 注入。`assembleDebug` + `:app:check` 綠燈；
   emulator 巡：開 Select sheet → 選一個窗 → Home 更新；開 Create sheet → 新增自訂窗 → 出現在 Select 清單。

## 7. 風險與注意

- **行為等價**：自訂清單順序（現況 `flowLast(10)` DESC 後 VM `.reversed()`）須保留；`saveSelection` 改為
  `viewModelScope.launch`（現況 `.apply()` 本即非阻塞）—— Home 透過 settings `callbackFlow` 更新，時序無感差異。
- **detekt `MagicNumber`**：`domain/`/`data/` 啟用。`DefaultFastingWindows` 用 `@Suppress("MagicNumber")`
  （宣告式資料）；`RECENT_CUSTOM_LIMIT` 用具名 const。
- **detekt `ReturnCount` max 2**：`CreateCustomFastingTypeUseCase.invoke` 維持 2 個 return。
- **domain 命名**：避免與 Room entity `FastingType` 撞名 —— domain 值取名 `FastingWindow`。
- **無 wildcard import**（僅 `java.util.*`）。

## 8. 驗證

- 環境見專案記憶（JAVA_HOME 指向 Android Studio JBR、`gradlew.bat`、`Pixel_10_Pro` AVD）。
- 每階段：`.\gradlew.bat assembleDebug :app:check` 綠燈（含 detekt 無 baseline、lint、單元測試）。
- `installDebug` 後驗證行為不變：Select sheet（內建 5 項 + 自訂項、選取後 Home 的 16:8 標籤/圓環依設定更新）、
  Create sheet（新增自訂窗成功 → 出現在 Select；輸入既有或內建窗 → 被拒、不重複）。
- 反向確認：全專案 grep，`FastingTypeDao` 僅被 `FastingTypeRepositoryImpl` 引用；兩個 fasting VM 不再 import
  `AppDatabase`/`DietPreference`/`entity.FastingType`/`FastingTypeDao`。
- 三支純測試綠燈；CI 閘門綠燈。

## 9. 明確排除（YAGNI）

- 不改 `FastingItem`、`SelectFastingTypeScreen`、`CreateFastingTypeScreen`、`AppRoot`、`DietViewModel`。
- domain 模型不帶 `id`/`timestamp`。
- 不遷 DataStore；`DietSettingsRepository` 僅補 `setSelection` 寫入。
- 不導入 Result 包裝（use case 回傳 Boolean）；不加 Turbine/VM 測試（屬 real-tests spec）。
