# View → Jetpack Compose 全面遷移 設計文件

- 日期：2026-06-10
- 範圍：把 SportRecorder 剩餘的 View-based UI（XML + ViewBinding + Fragment + Navigation
  component）全面改寫為 Jetpack Compose，採單一 Activity + Navigation-Compose 架構。
- 不更動：Room schema / DAO / entity、業務邏輯演算法（時間區間合併、歷史計算等），
  僅替換 UI 與其狀態層。

## 背景與目的

專案先前已把工具鏈升到 AGP 9.2 / Kotlin 2.3 / Compose BOM 2026.05.01，並已將
`SelectFastingType`、`CreateFastingType` 兩個 dialog 以 `ComposeView` 改成 Compose。
其餘畫面仍是 View-based。本專案要「彻底」改成 Compose：移除 Fragment / nav graph XML /
safe-args / ViewBinding / XML layout / RecyclerView adapter / 自訂 Canvas View，改為
單一 Activity 的 Navigation-Compose 架構，狀態層全面改用 `StateFlow<UiState>`。

預期成果：

- App 以單一 `MainActivity` + `setContent` 啟動，UI 全部為 `@Composable`。
- 導覽改用 `androidx.navigation:navigation-compose` 的 **type-safe routes**（`@Serializable`），
  底部導覽改 Compose `NavigationBar` + `Scaffold`。
- 每個畫面一個 `data class UiState` + `StateFlow`，以 `collectAsStateWithLifecycle()` 收集。
- 功能與外觀 **1:1 忠實移植**，不做重新設計、不加新功能（YAGNI）。
- 舊的 View 世界檔案全部刪除，`buildFeatures.viewBinding` 關閉。

## 目標架構

```
MainActivity (@AndroidEntryPoint)
  └─ setContent { SportRecorderTheme { AppRoot() } }
        └─ AppRoot: Scaffold(
              bottomBar = NavigationBar( Diet / Record / Notifications ),
              content   = NavHost(startDestination = Diet) { … }
           )
```

- **單一 Activity**：保留 `MainActivity`（仍需 `@AndroidEntryPoint` 供 Hilt），body 改為
  `setContent`。`activity_main.xml`、`NavHostFragment`、`BottomNavigationView` 移除。
- **Navigation-Compose**：新增 `navigation-compose`、`hilt-navigation-compose`
  （ViewModel 以 `hiltViewModel()` 取得）。
- **Type-safe routes**：以 `@Serializable` 物件/資料類別定義每個目的地，取代 safe-args。
  需新增 `org.jetbrains.kotlin.plugin.serialization`（版本對齊 Kotlin 2.3.10）與
  `kotlinx-serialization-json`。
- **底部三個頂層目的地**：Diet（首頁）、Record、Notifications。
- **四個 bottom sheet**（Select/Create FastingType、CreateEatTime、CreateFoodRecord）：
  作為 NavHost 目的地，以 Material3 `ModalBottomSheet` 呈現，`onDismiss → popBackStack()`。

## 狀態層（每畫面一個 UiState）

- 每個畫面：`data class XxxUiState(...)`，ViewModel 內 `private val _uiState = MutableStateFlow(...)`、
  對外 `val uiState: StateFlow<XxxUiState>`；Compose 端 `collectAsStateWithLifecycle()`
  （新增 `androidx.lifecycle:lifecycle-runtime-compose`）。
- 現有 `MutableLiveData` 全部改成 `MutableStateFlow`；DAO 回傳的 `LiveData` 改用 `Flow`
  （Room 兩者皆支援）。`SelectFastingTypeViewModel` 已是 Flow，作為範本。
- 最複雜的 `DietViewModel`：
  - 現有「每秒更新的 timer 迴圈」改為 VM 內每秒 emit 的 flow（或 `flow{}` + `delay`）併入 UiState。
  - 現有 SharedPreferences listener（選定的 fasting type）改為包成 Flow 併入 UiState。
  - 區間合併 / 5 日歷史計算邏輯**沿用**，只是輸出到 `DietUiState`。

## 自訂 Canvas View → Compose

- `CircleProgressBar`（首頁圓形進度）→ `@Composable` 用 `Canvas`：`drawArc` + SweepGradient
  漸層 + 起點/進度端點圓（沿用原本的三角函式定位邏輯）。
- `VerticalProgressBar`（5 日歷史長條）→ `Canvas` composable：垂直漸層線 + 兩端圓點。
- `SquareLinearLayout`：未被使用，直接刪除（需要正方形時用 `Modifier.aspectRatio(1f)`）。

## 畫面對應表

| 現況（View） | 目標（Compose） | 重點 |
|---|---|---|
| `DietFragment` + CircleProgressBar + 歷史長條 + timer + FAB | `DietScreen` | 圓形進度、5 日長條、每秒計時、FAB 開 bottom sheet |
| `DietRecordFragment` + `DietRecordAdapter` | `RecordScreen` | `LazyColumn`；長按刪除 → M3 `AlertDialog` 確認 |
| `CreateEatTimeDialogFragment` + 多型別 adapter + Date/Time picker | `CreateEatTimeSheet` | `ModalBottomSheet` + `LazyColumn`；M3 DatePicker/TimePicker；新增/刪除 food |
| `CreateFoodRecordDialogFragment` | `CreateFoodRecordSheet` | 表單（name/carbs/protein/fat） |
| `NotificationsFragment`（佔位） | `NotificationsScreen` | 單一文字，最簡單 |
| `SelectFastingTypeFragment` / `CreateFastingTypeFragment`（已是 @Composable） | 沿用既有 composable，移除 Fragment 外殼、改掛 NavHost | VM 已是 Flow / suspend |

## 分階段（單一 spec、分階段 plan；每階段可建置且可執行）

0. **Compose 外殼**：新增依賴（navigation-compose、hilt-navigation-compose、
   lifecycle-runtime-compose、serialization 外掛 + json）；`MainActivity` 改 `setContent`；
   建立 `AppRoot`（Scaffold + NavigationBar + NavHost、type-safe routes）；把已是 Compose 的
   Select/CreateFastingType 掛進來，其餘畫面先放 placeholder。**App 可跑 Compose 導覽。**
1. **自訂 View 移植**：`CircleProgressBar`、`VerticalProgressBar` → Compose Canvas（含預覽）。
2. **Diet 首頁**：`DietScreen` + `DietViewModel` 改 StateFlow（timer / 歷史 / FAB）。
3. **Record**：`RecordScreen`（LazyColumn）+ VM StateFlow + 刪除確認。
4. **CreateEatTime**：`CreateEatTimeSheet`（ModalBottomSheet、清單、Date/Time picker）+ VM。
5. **CreateFoodRecord**：`CreateFoodRecordSheet` 表單 + VM。
6. **Notifications**：`NotificationsScreen`。
7. **清理**：刪除所有 Fragment、XML layout、nav graph、menu、adapter、自訂 View、
   safe-args 外掛、`navigation-fragment/ui` 依賴；關閉 `viewBinding`；最終驗證。

## 依賴增刪

- 新增：`androidx.navigation:navigation-compose`、`androidx.hilt:hilt-navigation-compose`、
  `androidx.lifecycle:lifecycle-runtime-compose`、`org.jetbrains.kotlin.plugin.serialization`
  外掛 + `org.jetbrains.kotlinx:kotlinx-serialization-json`（版本對齊現有 BOM / Kotlin 2.3.10）。
- 移除（最後階段）：`androidx.navigation:navigation-fragment-ktx`、`navigation-ui-ktx`、
  `androidx.navigation.safeargs.kotlin` 外掛、`androidx.constraintlayout`（若無殘留使用）。
- `buildFeatures.viewBinding` 於清理階段關閉；`compose` 維持開啟。

## Theme

沿用既有 `SportRecorderTheme`（已是 Material3）、`Color.kt`、`Type.kt`。`res/values/themes.xml`
的 `Theme.SportRecorder` 仍保留作為 Activity 視窗主題（啟動背景 / status bar），UI 本身為 Compose。

## 風險與注意

- **DietViewModel 計時 / SharedPreferences**：是全案最複雜的狀態移植；需確保每秒更新與
  選定 fasting type 變更能正確反映到 `DietUiState`，且不外洩 coroutine（綁 `viewModelScope`）。
- **Bottom sheet 導覽**：以 NavHost 目的地 + `ModalBottomSheet` 呈現，需處理 dismiss 與
  返回鍵 → `popBackStack()` 的一致性。
- **Date/Time picker**：改用 Material3 `DatePicker`/`TimePicker`（或必要時沿用 Android 對話框）。
- **Type-safe routes**：需確實加入 serialization 外掛，否則 `@Serializable` route 無法編譯。
- **既有 Compose 畫面**：Select/CreateFastingType 的 composable 直接重用，避免重寫。

## 驗證

- 環境見專案記憶（JAVA_HOME 指向 Android Studio JBR、`gradlew.bat`、`Pixel_10_Pro` AVD/API 37）。
- 每階段：`.\gradlew.bat assembleDebug` 綠燈 + `installDebug` 安裝到 API 37 模擬器，
  逐畫面截圖比對「移植前 vs 移植後」外觀與行為一致。
- 重點流程冒煙：首頁計時與歷史長條、選擇/建立 fasting type、建立/刪除 eat time 與 food record、
  Record 列表與刪除、底部三個 tab 切換、bottom sheet 開關與返回鍵。
- Room 既有資料在移植後仍可正確讀寫顯示。

## 明確排除（YAGNI）

- 不重新設計任何畫面、不調整視覺、不加新功能。
- 不導入 Version Catalog、不改 DI 結構、不動 Room schema。
- 不在本案處理 Gradle 10 deprecation 警告（屬另一項工具鏈待辦）。
