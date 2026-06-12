# DietPreference → Jetpack DataStore 遷移 設計文件

- 日期：2026-06-12
- 範圍：把 `DietSettingsRepositoryImpl` 背後的 SharedPreferences（`DietPreference`）換成
  **Jetpack Preferences DataStore**，並以 `SharedPreferencesMigration` 保留既有使用者的選取視窗。
  介面 `DietSettingsRepository` 不變、ViewModel 與 UI 完全不動。
- 不在本案：Proto DataStore、新增任何設定鍵、改 `DietSettingsRepository` 介面、改任何 ViewModel/畫面、
  Robolectric/儀器化測試。

## 背景與目的

eat-record 與 fasting-type 兩個切片已把 SharedPreferences 完全藏在 `DietSettingsRepository` 後面。現況
`DietPreference`/`SharedPreferences` 僅出現在三處：
1. `data/repository/DietSettingsRepositoryImpl.kt` —— 唯一消費者（read flow + `setSelection` 寫入）。
2. `dagger/DatabaseModule.kt` —— `provideDietPreference` DI 提供者。
3. `util/DietPreference.kt` —— 包裝類別本身。

沒有任何 ViewModel 直接相依它。因此本案是「介面後的實作替換」：只需重現兩個行為 —— `settings: Flow<DietSettings>`
與 `suspend fun setSelection(window)`。儲存的只有兩個 `Long`（`diet_fasting_time_interval`、
`diet_eating_time_interval`，預設 16/8）。

使用者已選定：**Preferences DataStore**（非 Proto；兩個 Long 不需 protobuf）、**含 `SharedPreferencesMigration`**
（保留既有 Play 0.1.0 使用者的選取，不重置）、**JVM round-trip 測試 + emulator 遷移驗證**。

DataStore 的好處（順帶解決既有疑慮）：`dataStore.data` 為原生 reactive flow（取代手刻 `callbackFlow` +
`OnSharedPreferenceChangeListener`）；`dataStore.edit {}` 為 suspend、交易式、持久化且內部走 IO dispatcher
（乾淨地解決先前 `apply()` vs `commit()` 的取捨）。

## 1. 相依

於 `gradle/libs.versions.toml` 新增 `androidx.datastore:datastore-preferences`（最新穩定版，約 1.1.x；
plan 階段以 Google Maven 確認確切版號），並於 `app/build.gradle.kts` 以 `implementation(...)` 引入。

## 2. DI —— 新增 `DataStoreModule`，移除 `DietPreference` 提供者

把 prefs 提供者移出 `DatabaseModule`（讓它回歸只管 Room），改放在小巧的 `dagger/DataStoreModule.kt`，
提供一個 singleton `DataStore<Preferences>` 並接上遷移：

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    private const val DIET_PREFERENCES_NAME = "diet_preference"

    @Provides
    @Singleton
    fun provideDietDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            migrations = listOf(SharedPreferencesMigration(context, DIET_PREFERENCES_NAME)),
            produceFile = { context.preferencesDataStoreFile(DIET_PREFERENCES_NAME) },
        )
}
```

- `SharedPreferencesMigration(context, "diet_preference")` 於首次讀取時，把舊 `diet_preference.xml` 的所有鍵
  匯入 DataStore（鍵名相同 → 自動對應），匯入成功後刪除舊 `.xml`。
- DataStore 檔為 `datastore/diet_preference.preferences_pb`；與舊 SharedPreferences 的 `.xml` 不同檔，
  由遷移橋接。
- 必須是 singleton（每個程序對同一檔只能有一個 DataStore 實例）。
- 同時從 `DatabaseModule` 移除 `provideDietPreference` 與其 `DietPreference` import。

## 3. 改寫 `DietSettingsRepositoryImpl`（介面不變）

`Constants.DIET_*` 字串維持為鍵名（**遷移依賴鍵名一致**，加註解說明）；預設 16/8 維持：

```kotlin
private val FASTING_KEY = longPreferencesKey(Constants.DIET_FASTING_TIME_INTERVAL)
private val EATING_KEY = longPreferencesKey(Constants.DIET_EATING_TIME_INTERVAL)
private const val DEFAULT_FASTING_HOURS = 16L
private const val DEFAULT_EATING_HOURS = 8L

class DietSettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : DietSettingsRepository {

    override val settings: Flow<DietSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e } // 損毀 → 回預設
        .map { prefs ->
            DietSettings(
                fastingHours = prefs[FASTING_KEY] ?: DEFAULT_FASTING_HOURS,
                eatingHours = prefs[EATING_KEY] ?: DEFAULT_EATING_HOURS,
            )
        }
        .distinctUntilChanged()

    override suspend fun setSelection(window: FastingWindow) {
        dataStore.edit { prefs ->
            prefs[FASTING_KEY] = window.fastingHours
            prefs[EATING_KEY] = window.eatingHours
        }
    }
}
```

- 以 `dataStore.data` 取代 `callbackFlow` + 監聽器；以 `dataStore.edit {}` 取代 `edit().apply()`。
- `?: DEFAULT_*` 處理空/缺鍵；`.catch { IOException → emptyPreferences() }` 為 DataStore 標準健壯性
  （讀取損毀時回退預設）；`.distinctUntilChanged()` 避免相同 `DietSettings` 重複下游重算（行為更佳、UI 無差）。
- 預設值與鍵名不變 → 行為保留；遷移後既有使用者的值由 DataStore 提供。

## 4. 刪除 `util/DietPreference.kt`

DI 替換後不再被引用 —— 連同 `DatabaseModule` 內的提供者/ import 一併移除。以 grep 確認無殘留引用。

## 5. 測試

- **JVM round-trip**（`data/repository/DietSettingsRepositoryTest.kt`）：以 JUnit `TemporaryFolder` +
  `runTest`/`TestScope` 建一個真實 temp-file Preferences DataStore（本測試**不**接遷移），驗證：
  1. 空 store → `settings.first()` == `DietSettings(16, 8)`（預設）。
  2. `setSelection(FastingWindow(20, 4))` 後 → `settings.first()` == `DietSettings(20, 4)`（round-trip）。
  使用既有 `kotlinx-coroutines-test`。**備援**：若 `datastore-preferences` artifact 無法在 headless JVM 跑，
  改抽出純 `Preferences → DietSettings` mapper 並只測它（plan 會在第一支測試失敗時改採此備援）。
- **Emulator 遷移驗證**（phase 2）：
  1. 先以**現行 master build**（SharedPreferences）把選取改成非預設（如 20:4）。
  2. 切到本分支 build，**覆蓋安裝**（`installDebug`，**不要**先解除安裝 —— 保留舊 `.xml`）。
  3. 啟動 → Home 顯示 **20:4**（已遷移），而非 16:8 預設。證明 `SharedPreferencesMigration` 保住既有選取。

## 6. 分階段（單一 spec、分階段 plan；每階段可建置）

1. **替換**：加相依、`DataStoreModule`、改寫 `DietSettingsRepositoryImpl`、刪 `DietPreference` 與舊提供者、
   加 JVM round-trip 測試。`.\gradlew.bat assembleDebug :app:check` 綠燈。
2. **遷移驗證**：emulator 覆蓋安裝情境（§5），確認既有選取被保留。

## 7. 風險與注意

- **鍵名一致**：遷移以鍵名對應；DataStore 鍵必須用與舊 SharedPreferences 完全相同的字串
  （`Constants.DIET_FASTING_TIME_INTERVAL`/`DIET_EATING_TIME_INTERVAL`）。型別亦須一致（皆 `Long`）。
- **覆蓋安裝**：遷移只在保留舊 `.xml` 時可驗證；驗證時切勿先解除安裝。
- **singleton**：`DataStore<Preferences>` 必須 `@Singleton`；同檔多實例會在執行期丟錯。
- **JVM 測試相依**：`PreferenceDataStoreFactory.create(produceFile = …)`（無 Context 多載）應可於 JVM 跑；
  若不行採 §5 備援。
- **detekt**：新檔需過（`MagicNumber` 於 `data/` 啟用 —— 16/8 已具名 const；無 wildcard import）。
- **首次讀取的遷移時機**：`SharedPreferencesMigration` 在 DataStore 第一次被讀時執行；`settings` 首個發射即為
  遷移後的值，Home 不會閃一下預設再跳（DataStore 保證遷移先於首次資料發射）。

## 8. 驗證

- 環境見專案記憶（JAVA_HOME 指向 Android Studio JBR、`gradlew.bat`、`Pixel_10_Pro` AVD）。
- 每階段：`.\gradlew.bat assembleDebug :app:check` 綠燈（含 detekt、lint、單元測試）。
- JVM round-trip 測試綠燈；CI 閘門（`assembleDebug testDebugUnitTest :app:detekt :app:lintDebug`）綠燈。
- Emulator 覆蓋安裝：既有非預設選取（20:4）於更新後仍在；新安裝（解除後安裝）則為預設 16:8。
- 反向確認：全專案 grep，無任何 `DietPreference`/`getSharedPreferences` 殘留；`util/DietPreference.kt` 已刪。

## 9. 明確排除（YAGNI）

- 不用 Proto DataStore；不新增設定鍵或新行為。
- 不改 `DietSettingsRepository` 介面、不動任何 ViewModel/UI。
- 不加 Robolectric/儀器化測試（遷移路徑以 emulator 驗證）。
