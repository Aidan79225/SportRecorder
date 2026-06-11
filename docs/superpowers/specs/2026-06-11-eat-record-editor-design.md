# 進食紀錄可編輯 + 備註 + Record 卡片化 設計文件

- 日期:2026-06-11
- 範圍:(1) `eat_time` 新增「備註(note)」欄位;(2) 既有的建立 sheet 改為「建立 + 編輯」共用;
  (3) Record 頁面改為卡片式顯示。同步把 `CreateEatTime*` 更名為 `EatTimeEditor*`。
- 不在本案:反向地理編碼、照片排序、編輯斷食紀錄、富文字備註。

## 背景與目的

目前一筆進食紀錄 `EatTime(id, time, lat, lng)` 底下掛多張 `Photo`。建立流程是
`CreateEatTime` bottom sheet(日期/時間/位置/加照片 → CREATE),**只能建立、不能編輯**;
`EatTimeDao` 也沒有 update。Record 頁是表格式列(id | date | time + 縮圖 + 座標)。

使用者要:
1. 進食紀錄可輸入**備註**。
2. 點紀錄可**編輯**(日期/時間/備註/照片增刪、GPS 重新擷取或清除)。
3. Record 頁改成**卡片式**:上方日期/時間,下方備註、滿版且保持比例可左右滑動的照片、位置;
   **拿掉給使用者看的原始 DB id**。

## 1. 資料模型(Room v5 → v6)

`EatTime` 新增一個可空欄位:
```kotlin
@Entity(tableName = EatTime.tableName)
data class EatTime(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Int = 0,
    @ColumnInfo(name = "time") val time: Long,
    @ColumnInfo(name = "lat") val lat: Double? = null,
    @ColumnInfo(name = "lng") val lng: Double? = null,
    @ColumnInfo(name = "note") val note: String? = null,
) { companion object { const val tableName = "eat_time" } }
```
Migration 5→6:`ALTER TABLE eat_time ADD COLUMN note TEXT`。

`EatTimeDao` 新增:
```kotlin
@Update suspend fun update(eatTime: EatTime)

@Transaction
@Query("SELECT * FROM eat_time WHERE id = :id LIMIT 1")
suspend fun findWithPhotosById(id: Int): EatTimeWithPhotos?
```
(其餘 query / flowAllWithPhotos 不變。)

## 2. 建立 + 編輯共用一個 sheet(更名 `EatTimeEditor*`)

更名(降低混淆,使用者指定):
- 套件 `ui.diet.create.eating` → `ui.diet.editor`
- `CreateEatTimeSheet` → `EatTimeEditorSheet`
- `CreateEatTimeViewModel` → `EatTimeEditorViewModel`
- `CreateEatTimeUiState` → `EatTimeEditorUiState`
- 路由 `Route.CreateEatTime`(data object)→ `Route.EatTimeEditor(val eatTimeId: Int = 0)`
  - `eatTimeId == 0` → 建立模式;`> 0` → 編輯模式。
  - Diet FAB:`navigate(Route.EatTimeEditor())`(建立)。
  - Record 卡片鉛筆:`navigate(Route.EatTimeEditor(eatTimeId = id))`(編輯)。

ViewModel 行為:
- **建立模式**(同現況):開啟時自動擷取定位,confirm 時 `INSERT` eat_time + 新照片列。
- **編輯模式**:`init` 以 `findWithPhotosById(id)` 載入既有 日期/時間/備註/lat-lng 與既有照片到
  state;confirm 時 `UPDATE` eat_time + 新增/刪除照片(見 §3)。
- 確認鈕文字:建立 = `CREATE`,編輯 = `SAVE`。
- 路由參數透過 `entry.toRoute<Route.EatTimeEditor>().eatTimeId` 傳入,VM 由 `SavedStateHandle`
  取得(或在 AppRoot 用 `hiltViewModel()` 後呼叫 `vm.load(eatTimeId)`;以 `SavedStateHandle` 為佳)。

## 3. 照片暫存(支援編輯)

`EatTimeEditorUiState` 把照片分兩類:
- `existingPhotos: List<Photo>` — 編輯模式載入的、DB 已存在的列。
- `pendingPhotos: List<String>` — 本次新拍、檔案已寫入 photos 但尚無 DB 列。

操作:
- 移除 **既有照片** → 從 `existingPhotos` 移除並記入 `photosToDelete`(存 fileName/或 Photo),
  **存檔時**才真正刪列 + 刪檔。
- 移除 **新拍照片** → 立即刪檔 + 從 `pendingPhotos` 移除(同現況)。
- **存檔(編輯)**:單一交易內 `UPDATE` eat_time;為 `pendingPhotos` `INSERT` Photo 列;
  為 `photosToDelete` 刪列 + 刪檔。
- **建立**:同現況(INSERT eat_time + pendingPhotos 列)。
- **未存檔就關閉**:只刪本次 `pendingPhotos` 的檔(`onCleared` 內,`committed == false` 時);
  既有照片絕不動,`photosToDelete` 因未執行而保持原狀 → 既有照片完好。

## 4. 備註與位置編輯(sheet 內)

- **備註**:多行文字輸入(`OutlinedTextField`,選填、無長度限制),值進 `uiState.note`,
  存檔寫入 `eat_time.note`。
- **位置**:
  - 建立模式:維持開啟時自動擷取。
  - 編輯模式:位置列提供兩個動作 — **重新擷取**(重發定位請求、更新 lat/lng)與
    **清除**(lat/lng 設為 null)。需要時才請求定位權限。

## 5. Record 頁面 → 卡片式

每筆紀錄一張卡(移除 id):
```
┌──────────────────────────────────────┐
│  2026/06/11   11:19           ✏️       │  日期/時間 + 鉛筆(編輯)
│  「lunch at the park」                 │  備註(有才顯示)
│ ┌────────────────────────────────────┐│
│ │   滿版照片(保持比例)               ││  HorizontalPager,左右滑動
│ │            ● ○ ○                    ││  頁點;點照片 → 全螢幕
│ └────────────────────────────────────┘│
│  📍 25.0330, 121.5654                  │  位置(有才顯示)
└──────────────────────────────────────┘
```
- 照片用 `androidx.compose.foundation.pager.HorizontalPager` + `AsyncImage(ContentScale.FillWidth)`
  → 滿版寬度、保持比例、可左右滑;下方頁點(只有 1 張時可不顯示)。
- 手勢:**鉛筆 → 編輯**、**長按卡片 → 刪除**(沿用現有確認對話框)、**點照片 → 全螢幕檢視**(沿用)。
- 卡片以 `bg_black2` 之類做出區隔(圓角 + 間距),整體仍是 `LazyColumn`。

## 6. 導覽接線

- `ui/AppRoot.kt`:
  - `bottomSheet<Route.EatTimeEditor>`:取 `eatTimeId`,`EatTimeEditorViewModel` 以 `hiltViewModel()`
    取得(VM 由 `SavedStateHandle` 讀 id 決定建立/編輯);相機 + 位置 launcher 與現況相同。
  - `composable<Route.Record>`:`RecordScreen` 新增 `onEditRecord: (Int) -> Unit` 參數,
    接 `navController.navigate(Route.EatTimeEditor(eatTimeId = id))`。
  - Diet FAB 改成 `navigate(Route.EatTimeEditor())`。

## 7. 分階段(單一 spec、分階段 plan;每階段可建置 + 模擬器驗證)

1. **Schema/DAO**:`EatTime.note`、Migration 5→6、`update`、`findWithPhotosById`。跑 migration。
2. **VM 建立/編輯**:更名 `EatTimeEditor*`、`SavedStateHandle` 取 id、編輯載入、備註、
   位置重擷取/清除、編輯感知的照片暫存、`UPDATE` 存檔。
3. **Sheet UI**:備註輸入、`CREATE`/`SAVE` 文字、位置兩動作、既有 + 新照片顯示與刪除。
4. **Record 卡片化**:卡片版面、`HorizontalPager` 滿版照片 + 頁點、鉛筆→編輯接線、保留長按刪除 / 點照片全螢幕。

## 8. 風險與注意

- **HorizontalPager 高度**:滿版且「保持比例」→ 每張圖比例不同時,pager 高度需處理(可固定卡片內
  照片區為螢幕寬 × 該圖比例,或限定最大高度避免過長)。實作時以螢幕寬計算高度。
- **編輯模式照片刪除一致性**:既有照片刪除延後到存檔交易;未存檔關閉不可誤刪既有檔。
- **位置重擷取權限**:編輯時若使用者先前未授權,需再走一次權限請求;拒絕則維持原值或清除由使用者決定。
- **更名波及面**:`Route` / import / FAB / AppRoot 接線都要一起改;以 grep 確認無殘留 `CreateEatTime`。
- **路由型別變更**:`Route.EatTimeEditor` 為 `@Serializable data class`,沿用 type-safe routes。

## 9. 驗證

- 環境見專案記憶(JAVA_HOME 指向 Android Studio JBR、`gradlew.bat`、`Pixel_10_Pro` AVD/API 37)。
- `assembleDebug` 綠燈;Migration 5→6 在既有 DB 上就地升級不崩。
- 流程:建立一筆(含備註 + 照片 + GPS)→ Record 卡片正確顯示(日期時間、備註、滿版可滑照片、座標,無 id)→
  點鉛筆進編輯 → 改備註、改時間、刪一張既有照片、加一張新照片、清除位置 → SAVE → 卡片即時更新且正確 →
  長按刪除整筆連同照片檔 → 點照片可全螢幕。App 重啟後資料/照片仍正確。
- `assembleRelease` 與 `testDebugUnitTest` 綠燈。

## 10. 明確排除(YAGNI)

- 反向地理編碼(座標轉地址)。
- 照片排序 / 拖曳。
- 編輯斷食紀錄、富文字備註、備註長度上限 UI。
