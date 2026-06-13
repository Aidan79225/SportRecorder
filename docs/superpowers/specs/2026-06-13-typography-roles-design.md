# App-wide Typography → MD3 type-scale roles 設計文件

- 日期：2026-06-13
- 範圍:把全 app 剩餘「硬寫 `fontSize`/`fontWeight`」的文字改用 `MaterialTheme.typography.<role>`,
  並把 type scale 本身整理乾淨 —— 移除 4 個 legacy role 寫死的 `color`、修掉 `headlineLarge` 的
  lineHeight bug、補上 Record 卡片所需的 `bodySmall`/`labelSmall`。**字級全部維持原樣(零視覺變動)**。
- 不在本案:重新設計 type scale 比例、改任何版面/邏輯、改 Diet/狀態頁(已是 role-based)。

## 背景與目的

狀態頁(PR #18)已改用 MD3 type roles,但其餘畫面仍混用兩種反模式:
1. **硬寫 `fontSize`**:`EatTimeEditorSheet`(4 處 18sp)、`RecordScreen`(日期/時間/note 14sp、location 12sp)。
2. **type scale 把 `color` 寫死**:`createTypography(colorScheme)` 的 4 個 legacy role
   (`headlineLarge`/`titleLarge`/`titleMedium`/`bodyLarge`)在 `TextStyle` 裡綁了 `color = colorScheme.primary`
   —— 顏色不該綁在字體上(MD3 的顏色該在呼叫端用 color role 決定)。`Select`/`Create` 的標題就是靠這個寫死色
   渲染成綠色。此外 `headlineLarge` 的 `lineHeight = 24 < fontSize 36`(會擠行,既有 bug)。

eat-record/fasting/datastore 與狀態頁那幾輪已把該頁做對;本案把同一套「typography → 語意 role、顏色解耦」
推到全 app(對照 PR #12 的「色彩 → 語意 role」)。使用者選定:**Full proper pass**(轉硬寫 + 解耦顏色 +
修 lineHeight,字級維持)。

## 1. Type scale 整理(`ui/theme/Type.kt`)

- **解耦顏色**:移除 4 個 legacy role 的 `color = colorScheme.primary`。完成後**沒有任何 role 帶顏色**
  (與我先前為狀態頁新增的 7 個 role 一致),顏色一律由呼叫端 `color = colorScheme.<role>` 決定。
- **移除參數**:`colorScheme` 在 `createTypography` 內僅用於寫死顏色;解耦後不再被引用 ⇒ 簽章由
  `createTypography(colorScheme: ColorScheme)` 改為 `createTypography()`;`Theme.kt` 的呼叫端
  `typography = createTypography(DarkColors)` 改為 `createTypography()`。
- **修 lineHeight bug**:`headlineLarge` 的 `lineHeight` 由 `24.sp` 改為 `44.sp`(> 36sp fontSize)。
- **補兩個 role**(對應 Record 卡片現有字級,維持不變):
  - `bodySmall` = 14sp、Normal(Record 日期/時間/note)。
  - `labelSmall` = 12sp、Normal(Record location)。
  - body 家族成為乾淨的 14 / 16 / 18(bodySmall / bodyMedium / bodyLarge)。
- 其餘 role(displayMedium 52、displaySmall 44、headlineMedium 26、headlineSmall 20、titleSmall 15、
  bodyMedium 16、labelMedium 13、以及 legacy 的 headlineLarge 36 / titleLarge 18 / titleMedium 16 /
  bodyLarge 18)維持現值,僅 legacy 4 個移除 color。

## 2. 重新指定 legacy-role 呼叫端的顏色(維持外觀)

移除寫死色後,**每個原本「靠寫死 primary」渲染的呼叫端**都要顯式補回顏色,確保外觀不變。逐一稽核並補
`color = colorScheme.primary`(或當下應有的色):
- `SelectFastingTypeScreen`:`headlineLarge` 標題(無顯式 color)→ 補 `color = colorScheme.primary`。
- `CreateFastingTypeScreen`:`CANCEL` 按鈕(`titleLarge`,無顯式 color)、`TimeSelectRow` 標題
  (`bodyLarge`,無顯式 color)→ 補回對應顏色;`OK` 按鈕(已有 `onPrimary`)、grid 項目
  (已有 `onBackground`)等已顯式設色者不受影響。
- 全面 grep `MaterialTheme.typography` 的呼叫端,凡未顯式 `color =` 且原本依賴寫死 primary 者,補上。

## 3. 轉換剩餘硬寫 `fontSize` → role + 顯式 color

| 畫面 | 現況 | → role + color |
|---|---|---|
| `EatTimeEditorSheet`(HeaderRow 標題/內容、Location 標籤/值,共 4 處) | 18sp Normal | `bodyLarge` + `onSurface` |
| `RecordScreen` 日期 / 時間 / note | 14sp | `bodySmall` + `onSurface` |
| `RecordScreen` location | 12sp | `labelSmall` + `onSurfaceVariant` |
| `NotificationsScreen`「Coming soon」 | 預設 | `bodyLarge`(一致性) |

移除這些 `Text` 的 `fontSize`/`fontWeight`,改 `style = MaterialTheme.typography.<role>` + `color = ...`。
字級與顏色皆對齊現況 → 視覺零變動。移除因此不再使用的 import(如 `androidx.compose.ui.unit.sp`、
`FontWeight`,視各檔而定;`NoUnusedImports` 會抓)。

## 4. 分階段(單一 spec、分階段 plan;每階段可建置)

1. **Type scale + legacy 呼叫端(原子變更)**:`Type.kt`(解耦色、修 lineHeight、加 `bodySmall`/`labelSmall`、
   去參數)+ `Theme.kt`(改呼叫)+ `SelectFastingTypeScreen`/`CreateFastingTypeScreen` 補顯式顏色。
   這些**必須一起改**(移除寫死色會讓 Select/Create 的標題失色,直到呼叫端補回)。`assembleDebug :app:check`
   綠燈;emulator 截 Select/Create 確認外觀不變。
2. **轉硬寫**:`EatTimeEditorSheet` + `RecordScreen` + `NotificationsScreen` 改 role。`:app:check` 綠燈;
   emulator 截 Record / Editor sheet / Notifications 確認外觀不變。

## 5. 測試

- typography 為視覺改動、無邏輯 → 不加單元測試。以 **emulator 截圖**逐畫面比對(Home / Record /
  Editor sheet / Select / Create / Notifications)確認與改動前一致。
- detekt:`Type.kt` 變長已有 `@Suppress("LongMethod")`;新增 role 沿用;`ui/` 的 `MagicNumber` 排除。
- 反向確認:全專案 grep `ui/` 內不再有 `fontSize` / `fontWeight`(僅 `Type.kt` 保留 —— 那是 type scale
  定義本身)。

## 6. 風險與注意

- **外觀回歸風險集中在「解耦顏色」**:任何漏補顏色的呼叫端會從綠色掉成預設(`onSurface`)。Phase 1 以
  Select/Create 截圖把關;審查時逐一對照「原本無顯式 color 的 role 呼叫端」清單。
- `bodyLarge` 已被狀態頁(DietScreen)以顯式 color 使用 → 解耦其寫死色對 DietScreen 無影響(已 override)。
- `bodySmall`/`labelSmall` 取 14/12sp 是刻意對齊 Record 現有字級(非 MD3 預設的 12/11);與本 app 既有
  「依設計值自訂 type scale」一致。
- 移除 `createTypography` 參數後,確認無其他呼叫端(僅 `Theme.kt`)。
- detekt:無 wildcard import、無 semicolon;移除的 import 要清乾淨。

## 7. 驗證

- 環境見專案記憶(JAVA_HOME 指向 Android Studio JBR、`gradlew.bat`、`Pixel_10_Pro` AVD)。
- 每階段:`.\gradlew.bat assembleDebug :app:check` 綠燈(detekt、lint、既有單元測試)。
- emulator 逐畫面截圖:Home(已 role-based,不應變)、Record 卡片、Editor sheet(日期/時間/note/location/
  SAVE)、Select(標題綠、grid 項)、Create(CANCEL/OK、time-select)、Notifications —— 全部與改前一致。
- CI 閘門(`assembleDebug testDebugUnitTest :app:detekt :app:lintDebug`)綠燈。

## 8. 明確排除（YAGNI）

- 不重新設計 type scale 比例(字級維持);不改版面/邏輯/顏色語意。
- 不動 DietScreen(已 role-based);Notifications 維持 stub(僅該行 Text 給 role)。
- 不加單元測試(視覺改動以截圖驗證)。
