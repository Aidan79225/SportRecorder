# 狀態頁(首頁)重新設計 設計文件

- 日期：2026-06-13
- 範圍:重新設計 Home / Diet 狀態頁的版面與圓環呈現。四項改動:(1) 移除近日累積長條圖、
  (2) 顯示斷食起訖時間、(3) 圓環漸層改為深→亮、(4) 狀態字串移到圓環上方、原位置改顯示進度百分比。
  版面採「倒數為主(Option A)」。
- 不在本案:改 `DietWindow` 計算邏輯、改其他畫面(Record/Editor/Notifications/Select/Create)、
  改進食/斷食的時間運算規則(僅改呈現)。

## 背景與目的

現況 `DietScreen` 圓環中央堆疊 `Icon → 狀態字串 → prompt → 大倒數 → timeInfo`,圓環下方有
5 根近日 fasting 比例長條圖(`VerticalProgress`)。使用者要把狀態頁聚焦在「當下這一輪」:拿掉歷史長條、
明確標出斷食何時開始/結束、圓環視覺修正成深→亮漸層、並把版面重排(狀態移到上方、原位置補上百分比)。

已於 visual companion 確認:**版面 Option A(圓環中央以大倒數為主角、百分比為次要)**、
**斷食起訖時間一律顯示(進食中顯示即將到來的斷食視窗)**、**圓環起點 12 點鐘深綠 → 進度尾端最亮綠**。

`DietWindow`(domain,已被測)維持不變,持續提供 `phase` / `ringProgress` / `elapsedMillis` /
`windowStart` / `windowEnd` / `lastEat` / `fastTargetAt`。本案只改「如何呈現這些值」。

## 1. 移除近日長條圖

- `DietScreen`:刪掉長條圖 `Row`(`VerticalProgress` + 日期標籤)。
- `DietUiState`:移除 `history: List<HistoryBar>` 與巢狀 `HistoryBar`。
- `ObserveDietStateUseCase`:`DietSnapshot` 由 `(eatTimesAsc, settings, history)` 改為
  `(eatTimesAsc, settings)`;移除 `DietHistoryCalculator.compute(...)` 呼叫與相關 day-boundary 計算。
  仍保留 `observeInWindow(...)` 取得近期 eat times 餵給 `DietWindow.compute`(視窗界線可保留現值,
  僅不再用於歷史聚合;加註解說明)。
- `DietViewModel`:移除 history 對應、移除 `formatHistoryDate` 與 `HISTORY_DATE_FORMAT`(不再被引用)。
- **刪除**:`domain/diet/DietHistoryCalculator.kt` 與 `DietHistoryCalculatorTest.kt`(僅被 use case 引用);
  `ui/component/VerticalProgress.kt`(僅被 `DietScreen` 引用)。以 grep 確認無殘留後刪。

## 2. 斷食起訖時間(一律顯示)

- 斷食開始 = `windowEnd`(進食視窗結束 = firstEat + 進食時數);斷食結束 = `fastTargetAt`(lastEat + 斷食時數)。
- 各階段:
  - EATING:顯示「即將到來的斷食視窗」(`windowEnd` → `fastTargetAt`)。
  - FASTING / SUCCESS:顯示當輪斷食視窗(同上)。
  - IDLE(無紀錄,`windowEnd`/`fastTargetAt` 為 null):**不顯示**此行。
- 跨日:當結束(或起點)落在比起點更晚的日曆日時,結束時間前綴「隔日」。
- 位置:圓環**下方**(圓環與 16:8 chip 之間),非圓環內。

## 3. 圓環漸層(深 → 亮)

`CircleProgress` 重做進度弧的漸層,使其沿著弧線由**起點(12 點鐘)深綠 → 進度尾端(tip)最亮綠**。
- 現況 `Brush.sweepGradient(0f to gradientStart, 1f to gradientEnd)` 以整圈(3 點鐘起)映射,與從
  −90°(12 點鐘)起畫的弧不對齊,導致最亮色不落在 tip。
- 修正:以 `rotate(-90°, pivot = center)` 包住弧的繪製,sweepGradient 色停改為
  `0f → gradientStart(深)`、`(progress/100f) → gradientEnd(亮)`,使弧起點為深色、tip 為最亮。
  track(剩餘軌道)維持 `surfaceVariant` 深灰;起點圓點維持深色、tip 圓點維持最亮 + `tipColor`。
- 顏色維持 MD3 roles:`gradientStart = primaryContainer`、`gradientEnd = primary`(預設參數不變)。
- 簽章與呼叫端不變(`progress: Float 0..100`),純內部繪製調整。

## 4. 版面重排(Option A)

`DietScreen` 由上而下:
1. **狀態字串(header)**:`stringResource(statusTextRes)`,置於圓環**上方**,較大、`primary` 色、置中。
2. **圓環 Box**:`CircleProgress` 疊上中央 `Column`:`Icon → "75%"(由 progress 推導)→ prompt → 大倒數`。
   - 百分比落在「原狀態字串位置」(icon 之下);Option A 下百分比為次要字級(~20sp、`primary`),
     大倒數(`elapsedText`,~44–48sp 粗體)為主角。
3. **斷食視窗行**:圓環下方(見 §2)。
4. **16:8 chip**(維持現狀,可點擊編輯)。
5. **FAB**(維持現狀)。

### 倒數語意(維持現狀)

大倒數與其 prompt 維持各階段現有意義 —— EATING 顯示「剩餘進食時間」、FASTING 顯示「已斷食時間」
(SUCCESS 同 FASTING),沿用現有 prompt 字串。本案僅**新增**百分比與斷食視窗行,不改倒數計時規則。

## 5. State / VM(維持 `DietViewModel` 無 Context)

- `DietUiState`:
  - 移除 `history`、`HistoryBar`、`timeInfoRes`、`timeInfoArg1`、`timeInfoArg2`。
  - 新增 `fastStart: String = ""`、`fastEnd: String = ""`、`fastEndsNextDay: Boolean = false`
    (`fastStart` 為空字串 ⇒ 隱藏斷食視窗行)。
  - 保留 `elapsedText`、`progress`、`fastingLabel`、`selectedFastingItem`、`statusIcon`、
    `statusTextRes`、`promptTextRes`。
- `DietViewModel`:以既有 `HM_FORMAT`(`HH:mm`,java.util,無需 Context)格式化 `windowEnd`/`fastTargetAt`
  為 `fastStart`/`fastEnd`;以 `Calendar` 比較兩者日曆日決定 `fastEndsNextDay`。IDLE 時 `fastStart=""`。
  百分比不入 state,由畫面以 `progress` 推導(`"${progress.roundToInt()}%"`)。
- `DietScreen`(可用 `stringResource`):斷食視窗行 =
  `stringResource(R.string.diet_fast_window, fastStart, if (fastEndsNextDay) stringResource(R.string.diet_next_day, fastEnd) else fastEnd)`。

## 6. 字串資源

- 新增(`values/`、`values-zh-rTW/` 皆需):
  - `diet_fast_window` = `"斷食 %1$s → %2$s"`(en:`"Fast %1$s → %2$s"`)。
  - `diet_next_day` = `"隔日 %1$s"`(en:`"next day %1$s"`)。
- 移除已不再被引用者:`diet_eating_window`、`diet_fast_target`、`diet_fast_done`(grep 確認無殘留後刪,兩語系皆刪)。
- 維持:`diet_status_eating/fasting/success`、`diet_remaining_time`、`diet_fasting_time`、`diet_no_record`、`diet_time_format`。

## 7. 測試

- **純單元測試**:斷食視窗跨日格式邏輯。把 `fastEndsNextDay` 的判斷抽成可測的純函式
  (給定 start/end millis + 時區固定,回傳是否跨日),測同日與隔日兩情境。其餘(版面、漸層)以 emulator 截圖驗證。
- 既有測試:`DietWindowTest` 不受影響;`DietHistoryCalculatorTest` 隨檔刪除。

## 8. 分階段(單一 spec、分階段 plan;每階段可建置)

1. **Logic / state**:改 `ObserveDietStateUseCase`(去 history)、`DietViewModel`(去 history、加
   `fastStart`/`fastEnd`/`fastEndsNextDay` + 跨日純函式 + 測試)、`DietUiState`(調欄位);刪
   `DietHistoryCalculator` + 測試;加/刪字串資源。build 綠燈(此時 `DietScreen` 仍引用舊欄位 → 一併在本階段
   更新 `DietScreen` 對 state 的讀取以維持可編譯,或將 `DietScreen` 改動歸到 Phase 2 並在 Phase 1 暫留最小相容)。
   —— 註:`DietUiState` 欄位變更會使 `DietScreen` 無法編譯,故 Phase 1 與 Phase 2 需一起讓專案可建置;
   plan 會明確排序(state + VM + 最小 `DietScreen` 調整在前,完整版面/圓環在後),確保每個 commit 綠燈。
2. **UI**:`DietScreen` 完整重排(移長條圖、狀態 header、中央 icon+%+prompt+倒數、斷食視窗行)、
   `CircleProgress` 漸層重做、刪 `VerticalProgress`。`assembleDebug` + `:app:check` 綠燈;
   emulator 截圖巡 EATING / FASTING / SUCCESS / IDLE 四態,確認版面、漸層、起訖時間(含隔日)正確。

## 9. 風險與注意

- **可編譯排序**:`DietUiState` 欄位異動牽動 `DietViewModel` 與 `DietScreen`,三者需於同一階段內協同更新
  (plan 細排每個 commit 的綠燈點)。
- **圓環漸層**:`rotate` + 色停位置需與弧的 `startAngle = -90` / sweep 對齊;`@Preview` 給值驗證;
  `progress > 99f`(滿圈)與小角度兩分支都要正確。
- **detekt**:`CircleProgress` 在 `ui/`(MagicNumber 排除);新純函式若放 `domain/` 需具名 const。無 wildcard import。
- **跨日 >1 天**(如 47:1):以「隔日」近似標示即可(非逐日計);於 spec 記為可接受。
- **i18n**:`diet_fast_window` / `diet_next_day` 兩語系都要;移除字串前以 grep 確認無程式碼/版面引用。

## 10. 驗證

- 環境見專案記憶(JAVA_HOME 指向 Android Studio JBR、`gradlew.bat`、`Pixel_10_Pro` AVD)。
- 每階段:`.\gradlew.bat assembleDebug :app:check` 綠燈(含 detekt、lint、單元測試)。
- 跨日格式純測試綠燈;CI 閘門綠燈。
- emulator:逐態截圖確認 —— 狀態字串在圓環上方、中央為 icon/百分比/prompt/大倒數、圓環下方斷食起訖
  (進食中顯示即將斷食、跨日加「隔日」)、無長條圖、圓環深→亮且最亮在 tip。
- 反向確認:grep 無 `VerticalProgress` / `DietHistoryCalculator` / `history`(state)/ 已刪字串 之殘留。

## 11. 明確排除（YAGNI）

- 不改 `DietWindow` 計算、不改倒數計時規則(僅呈現)。
- 不動其他畫面;不加歷史/統計的替代視圖(長條圖直接移除)。
- 不加 Turbine/VM 測試(屬 real-tests spec);跨日邏輯以純函式單測涵蓋。
