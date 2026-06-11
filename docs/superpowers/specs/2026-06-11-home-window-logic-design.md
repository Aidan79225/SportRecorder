# Home 進食/禁食 window 邏輯與顯示優化 設計文件

- 日期：2026-06-11
- 範圍:把首頁(Diet)的「進食 window / 禁食 window」邏輯釐清並改正確,抽成純函式以利單元測試,
  並讓圓環進度、狀態文字、時間資訊更清楚 + 小幅版面調整。
- 不在本案:5 日歷史長條(維持現狀)、斷食排程資料模型、新增資料表。

## 背景與目的

目前 `DietViewModel.buildState` 的 window 判定混亂:進食用「now − 第一口」、禁食用「now − 最後一口」
兩個不同錨點;群聚用 **寫死的 8 小時** gap(與使用者選的 eatingHours 無關);進食期間圓環卻顯示
禁食進度。使用者要先釐清定義,再優化首頁。

**已釐清的模型**(F = 該次進食 window 第一口時間、L = 最後一口、Eh = eatingHours、Fh = fastingHours):
- 進食 window = `[F, F + Eh]`。即使 L 早於 F+Eh,只要 `now < F+Eh` 都算「進食中」。
- `now ≥ F+Eh` 之後 = 禁食;**禁食時間從最後一口算**:`now − L`(故切換瞬間就已是 `Eh − (L−F)`)。
- Success = `now − L ≥ Fh`。
- 進食期間圓環 = `(now − F)/Eh`;禁食期間圓環 = `(now − L)/Fh`。
- 群聚規則:依時間升序走訪,當某口落在 `currentWindowFirstEat + Eh` **之後**,即開啟新 window;
  目前 window = 最近一個 cluster。(取代寫死 8h。)

## 1. 抽出純函式計算器 `DietWindow`(可單元測試)

新增 `ui/diet/DietWindow.kt`(純 Kotlin,無 Android / 無時鐘 / 無 prefs 相依):
```kotlin
enum class DietPhase { IDLE, EATING, FASTING, SUCCESS }

data class DietWindowState(
    val phase: DietPhase,
    val ringProgress: Float,      // 0f..1f
    val elapsedMillis: Long,      // 進食:剩餘;禁食/Success:已禁食
    val windowStart: Long?,       // F
    val windowEnd: Long?,         // F + Eh
    val lastEat: Long?,           // L
    val fastTargetAt: Long?,      // L + Fh(預計完成)
)

object DietWindow {
    /** eatTimesAsc: 升序的進食時間戳(毫秒);hours 為小時;now 由呼叫端注入。 */
    fun compute(
        eatTimesAsc: List<Long>,
        eatingHours: Long,
        fastingHours: Long,
        now: Long,
    ): DietWindowState
}
```
- `currentWindow(eatTimesAsc, Eh)`:回傳最近 cluster 的 `(firstEat, lastEat)`(群聚規則如上)。
- 計算邏輯:
  - 無資料 → `IDLE`(progress 0、其餘 null)。
  - `now < F+Eh` → `EATING`,ring=`((now−F)/EhMillis).coerceIn(0,1)`,elapsed=`(F+Eh)−now`,
    windowStart=F、windowEnd=F+Eh、lastEat=L、fastTargetAt=L+Fh。
  - `now ≥ F+Eh` 且 `now−L < Fh` → `FASTING`,ring=`((now−L)/FhMillis).coerceIn(0,1)`,elapsed=`now−L`。
  - `now−L ≥ Fh` → `SUCCESS`,ring=1f,elapsed=`now−L`。
- 時間格式化("%02d:%02d:%02d")維持在呼叫端或 UI 端,計算器只回毫秒(利於測試)。

## 2. `DietViewModel` 改為薄包裝

`DietViewModel` 保留 ticker(每秒)、prefsFlow、DAO 流;`buildState` 改為:
- 取目前(滾動視窗內)的 `eat_time` 升序時間戳清單;
- `val s = DietWindow.compute(times, eatingHours, fastingHours, System.currentTimeMillis())`;
- 依 `s.phase` 映射 `DietUiState`(statusIcon / statusTextRes / promptTextRes / progress=ring*100 /
  elapsedText=format(s.elapsedMillis) / 新增的時間資訊字串)。
- 移除舊的 `mergeInterval` / 雙錨點 when 區塊(歷史用的 `mergeIntervalWithFourHours` 維持不動)。

狀態文字對應(沿用既有 string resource):
- EATING → `diet_status_eating`、prompt `diet_remaining_time`(剩餘)。
- FASTING → `diet_status_fasting`、prompt `diet_fasting_time`。
- SUCCESS → `diet_status_success`、prompt `diet_fasting_time`。
- IDLE → 空白計時 + 提示(新增字串,如 `diet_no_record` =「新增第一餐」/英文 "Add your first meal")。

## 3. 更清楚的時間資訊(圓環下方新增一行)

`DietUiState` 增加一個 `timeInfo: String`(可空/空字串):
- EATING:`進食視窗 HH:mm–HH:mm`(F .. F+Eh)。
- FASTING / SUCCESS:`預計完成 HH:mm`(L + Fh);Success 可顯示「已達標」。
- IDLE:空。
UI 在 `DietScreen` 圓環/狀態下方多一個小字 `Text` 顯示 `state.timeInfo`(有才顯示)。

## 4. 單元測試 `DietWindowTest`(JUnit,純函式)

對 `DietWindow.compute(...)` 注入固定 `now` 測各情境(Eh=8h、Fh=16h 為主):
1. 無資料 → IDLE。
2. 單一口、now 在進食視窗內 → EATING,ring/elapsed 正確。
3. **關鍵案例**:F、L=F+4h;now=F+5h → 仍 EATING;now=F+9h → FASTING 且 elapsed=now−L=5h、
   ring=(5h/16h)(切換瞬間跳到真實最後一口禁食時長)。
4. 禁食進行中(F+Eh < now,now−L < Fh) → FASTING,ring/elapsed 正確。
5. Success:now−L ≥ Fh → SUCCESS,ring=1。
6. 視窗關閉後又有一口(eat > F+Eh) → 開新 window,以新 F/L 計算。
7. 邊界:now 恰為 F+Eh(切 EATING→FASTING)、now−L 恰為 Fh(切 FASTING→SUCCESS)。
8. 多口落在視窗內(群聚同一 window,L=最後一口)。
共約 10 個案例,皆以注入 now 斷言 phase / ringProgress / elapsedMillis / windowStart/End。

## 5. 版面 / 視覺(保守調整)

維持既有圓形計時結構;主要:(a) 圓環依 phase 顯示正確進度(進食/禁食兩段各自);(b) 圓環下方加
`timeInfo` 小字;(c) 微調間距使狀態/時間層次清楚。不做大改版(除非後續另議)。

## 6. 風險與注意

- **每秒重算**:`compute` 為純函式,每秒由 ticker 帶入新 now 重算,效能無虞。
- **群聚效能**:eat_time 數量小(首頁滾動視窗內),線性走訪即可。
- **時區/午夜**:`compute` 只用毫秒運算,不涉日界;時間字串格式化用裝置時區(`HH:mm`)。
- **歷史不變**:`mergeIntervalWithFourHours` / `computeHistory` 與 5 日長條維持原樣,避免擴大範圍。
- **既有行為差異**:新模型在「進食期間圓環顯示進食進度」「群聚改用 eatingHours」上與舊版不同 ——
  這正是要修正的點;以單元測試固定預期行為。

## 7. 驗證

- 環境見專案記憶(JAVA_HOME 指向 Android Studio JBR、`gradlew.bat`、`Pixel_10_Pro` AVD/API 37)。
- `:app:testDebugUnitTest` 跑 `DietWindowTest` 全綠(各情境)。
- `.\gradlew.bat assembleDebug :app:check` 綠燈(含 detekt;新檔需符合規則)。
- `installDebug` 後實機驗證:建立一筆進食 → 進食期間圓環走進食進度、顯示進食視窗時間;
  過 eatingHours 後切換為禁食、圓環走禁食進度、計時 = now−最後一口、顯示預計完成時間;
  長時間後顯示 Success。可用 mock 時間或調整裝置時間/建立過去時間的紀錄輔助驗證。
- `assembleRelease` 綠燈。

## 8. 明確排除（YAGNI）

- 不動 5 日歷史長條邏輯/顯示。
- 不加斷食排程、提醒、或新資料表/欄位。
- 不做首頁大改版(僅依本案的進度/狀態/時間資訊 + 微調)。
