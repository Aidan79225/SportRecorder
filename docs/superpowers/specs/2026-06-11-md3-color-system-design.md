# MD3 色彩系統 + role-based UI 設計文件

- 日期：2026-06-11
- 範圍:把首頁與全 app 的顏色使用方式改成符合 MD3 精神 —— 建立完整的 MD3 dark `ColorScheme`
  (以品牌綠為 seed),並把 UI 各處硬寫的 raw 顏色改成引用 `MaterialTheme.colorScheme.<role>`。
- 不在本案:light theme、Material You 動態色、字體/形狀(shape)主題化、任何版面/邏輯改動。僅顏色。

## 背景與目的

現況 UI 約 **72 處直接用 raw 品牌色**(`light_green`×17、`white`×16、`grey_1`×15、`bg_black2`×12、
`bg_black`×7、`dark_green`×5,加少數 `colorResource`),而 `MaterialTheme.colorScheme.*` 只用約 9 處。
也就是說 `SportRecorderTheme` 雖定義了 colorScheme,但 UI 幾乎都繞過它直接塗 raw 色 —— 這正是不符合
MD3 精神之處(MD3 的核心是元件引用 **語意 roles**,而非字面 hex)。此外現有 scheme 是手刻、dark-only、
light==dark、只設了少數 roles。

使用者已選定:**由品牌綠 #38D69F seed 生成完整 tonal palette**、**維持深色 only**、**關閉動態色**。

## 1. 完整 dark `ColorScheme`(seed 錨定品牌)

以 `darkColorScheme(...)` 設滿所有 roles,值以品牌綠為 seed 衍生,但為保留品牌識別度做兩點錨定:
- `primary` = 品牌綠 `#38D69F`;`primaryContainer` ≈ 現有 `dark_green` `#177252`;
  `onPrimary` / `onPrimaryContainer` 為對應深/淺 on 色。
- `surface` / `background` 維持在現有深灰家族(`#2B2B2B` 附近,確認決定 (a)),
  `surfaceVariant` / `surfaceContainer*` 取 `bg_black2` `#3D3D3D` 家族;
  `onSurface` / `onBackground` ≈ `white`;`onSurfaceVariant` ≈ `grey_1` `#CCCCCC`(次要文字/icon)。
- `secondary` / `onSecondary` / `secondaryContainer…`、`tertiary…`、`outline` / `outlineVariant`、
  `error` / `onError` / `errorContainer` / `onErrorContainer`、`inverseSurface` 等其餘 roles 一併由 seed
  衍生補齊(完整性)。
- 確切 hex 值於 plan 提供「可直接貼上」的一組;使用者若要 pixel-exact M3 tones,可用 Material Theme
  Builder 以 seed `#38D69F` 重新產生並覆蓋(結構相同,只換值)。

## 2. UI 改用語意 roles(約 72 處)

逐檔把 raw 色換成 `MaterialTheme.colorScheme.<role>`,對應表:
| raw | → MD3 role |
|---|---|
| `light_green` | `primary` |
| `white` | `onSurface` / `onBackground`(依情境) |
| `grey_1` | `onSurfaceVariant` |
| `bg_black` / `colorResource(R.color.bg_black)` | `surface` / `background` |
| `bg_black2` | `surfaceVariant` / `surfaceContainer` |
| `dark_green` | `primaryContainer`(或保留作 canvas 漸層藝術用) |

- 元件改為在 composable 內讀 `MaterialTheme.colorScheme.X`,移除對 `ui.theme` raw `Color` val 的 import。
- **自訂 Canvas 藝術**(`CircleProgress` / `VerticalProgress` 的 綠→深綠 漸層):改為由呼叫端把
  `primary` / `primaryContainer` 當參數傳入(維持品牌漸層,但改由主題驅動,確認決定 (b))。
  其餘 canvas 用色(背景軌 `bg_black2`、起點/終點圓)亦由參數帶入對應 role。

## 3. `Theme.kt` 清理

- 改成 **dark-only**:移除未用的 `LightColorScheme`、被註解掉的動態色/狀態列 SideEffect cruft、
  以及 `dynamicColor` 參數(或保留無作用但建議移除)。
- `SportRecorderTheme { }` 直接包單一 dark scheme;`createTypography(colorScheme)` 維持。
- 移除 `@Suppress("UnusedParameter")`(參數移除後即不需要)。

## 4. 清除已死的 raw 顏色

migration 後,`Color.kt` 內不再被引用的 val 與 `res/values` 對應的 `R.color.*`(若有)一併移除;
僅保留仍被使用者(例如 canvas 參數預設、或 launcher 用的 `ic_launcher_background` 色)。
以 grep 確認無殘留引用再刪。

## 5. 分階段（單一 spec、分階段 plan;每階段可建置）

1. **新 scheme + Theme 清理**:`Color.kt` 補上完整 dark roles 值、`Theme.kt` 改 dark-only。
   build 綠燈(此時 UI 多數仍用 raw 色,視覺幾乎不變)。
2. **UI 改 roles**:逐檔(AppRoot、DietScreen、RecordScreen、EatTimeEditorSheet、SelectFastingType、
   CreateFastingType、Notifications、CircleProgress、VerticalProgress…)把 raw 色換成 colorScheme roles
   + canvas 參數化。每改幾檔就截圖確認品牌仍清楚。
3. **清死色 + 收尾**:刪無用 raw val / `R.color`;`:app:check`、`assembleDebug`、emulator 全畫面巡一遍。

## 6. 風險與注意

- **視覺變動**:seed 衍生的 secondary/tertiary/container/outline 可能與舊版略不同;以每階段截圖控管,
  確保品牌綠與深色底維持識別度(surface 已錨定現有深灰)。
- **on 色對比**:換 role 時注意文字/圖示落在正確的 onX role 上(例如卡片 `surfaceVariant` 上的文字用
  `onSurfaceVariant`),避免對比不足(detekt/lint 不會抓,需肉眼 + 截圖)。
- **canvas 參數化**:`CircleProgress`/`VerticalProgress` 簽章新增顏色參數(有預設或必填),呼叫端傳入
  roles;`@Preview` 也要給值。
- **detekt 無 baseline**:改動檔需通過 detekt;移除 import、避免 wildcard。
- **不要動到 launcher icon 的 `ic_launcher_background` 顏色**(那是 App 圖示,非 UI 主題)。

## 7. 驗證

- 環境見專案記憶(JAVA_HOME 指向 Android Studio JBR、`gradlew.bat`、`Pixel_10_Pro` AVD/API 37)。
- 每階段:`.\gradlew.bat assembleDebug :app:check` 綠燈(含 detekt 無 baseline、lint)。
- `installDebug` 後逐畫面截圖:首頁(圓環/狀態/時間資訊/歷史長條)、Record 卡片、建立/編輯 sheet、
  選擇/建立 fasting type、Notifications、底部導覽 —— 確認顏色一致、對比足夠、品牌綠正確。
- `testDebugUnitTest`、`assembleRelease` 綠燈(顏色改動不影響邏輯/測試)。
- 反向確認:全專案 grep 不再有 UI(theme 以外)直接 import/使用 `bg_black/bg_black2/white/grey_1/
  light_green/dark_green` 的殘留(canvas 參數預設與必要例外除外,且有註記)。

## 8. 明確排除（YAGNI）

- 不加 light theme、不開動態色。
- 不改字體/形狀主題、不改任何版面或邏輯。
- 不重新設計品牌色(primary 維持 #38D69F);僅把「用法」改成 MD3 role-based + 補齊 scheme。
