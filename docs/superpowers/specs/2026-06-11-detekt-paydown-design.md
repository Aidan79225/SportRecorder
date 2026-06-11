# 清償 detekt baseline（200 → ~0）設計文件

- 日期：2026-06-11
- 範圍：把 `app/detekt-baseline.xml` 內 200 筆既有 detekt findings 清掉,讓 baseline 趨近於空,
  之後新問題直接由 detekt 擋住。手法:自動格式修正 + 設定抑制誤報 + 手動修真實 smell。
- 不更動 app 行為:所有修改皆為等價（格式/命名/抽常數/例外處理/設定),不改邏輯。

## 背景與目的

PR #8 導入 detekt 時,把當時 200 筆 finding 全進了 baseline。本案逐層清償,使 baseline 趨近 0,
之後任何新 detekt 問題都會讓 `:app:check` 失敗。findings 分類(已實測):
- **~110 格式類(ktlint/formatting)**:可由 `--auto-correct` 全自動修(FinalNewline、
  NewLineAtEndOfFile、ArgumentListWrapping、MultiLineIfElse、NoUnusedImports、Wrapping、
  spacing/import-ordering/indentation 等)。
- **~62 誤報**:`FunctionNaming` 21(幾乎都是 @Composable,PascalCase 本來就對)、
  `MagicNumber` 41(多為 Compose UI 的 dp/sp 字面值)。
- **~30 真實 smell**:UnusedPrivateMember/Parameter、SwallowedException/TooGenericExceptionCaught、
  ImplicitDefaultLocale、MaxLineLength、MatchingDeclarationName/Filename、LongMethod/
  LongParameterList/TooManyFunctions、UseCheckOrError 等。

## 解法（三層,依序）

### 第 1 層:自動格式修正
`.\gradlew.bat detekt --auto-correct`(formatting 規則已啟用 autoCorrect)會就地修好所有
ktlint 格式問題。純格式、等價。修完重生 baseline,該類 finding 全消。

### 第 2 層:設定抑制誤報(`app/detekt.yml`)
- `FunctionNaming`:加 `ignoreAnnotated: ['Composable']` → 21 筆 @Composable 命名誤報消失。
- `MagicNumber`:放寬 + 排除 Compose UI:
  - `ignorePropertyDeclaration: true`、`ignoreLocalVariableDeclaration: true`、
    `ignoreNamedArgument: true`、`ignoreAnnotation: true`;
  - `excludes: ['**/ui/**']`(Compose 畫面/元件的 dp/sp 字面值不報)。
  - **邏輯層(非 ui/)的真實 magic number**(如 DietViewModel 的時間/百分比計算)不在排除範圍,
    於第 3 層抽成具名常數。

### 第 3 層:手動修真實 smell（等價修改,分群處理）
- **未用程式碼**:刪除 `UnusedPrivateMember`、移除/標註 `UnusedParameter`。
- **在地化**:`ImplicitDefaultLocale` → `String.format` 補 `Locale`(顯示格式維持)。
- **例外處理**:`SwallowedException`/`TooGenericExceptionCaught` → 至少記錄或縮小捕捉型別,
  維持原本「失敗時安全降級」的行為。
- **行長**:`MaxLineLength`/`MaximumLineLength` → 手動換行(不可自動)。
- **命名/檔名**:`MatchingDeclarationName`、`Filename`(例如 `CreateFoodRecordViewMode.kt`
  類的檔名與宣告不符) → 重新命名檔案/宣告。
- **常數**:邏輯層 `MagicNumber` → 抽成 `private const val`。
- **大型宣告**:`LongMethod`/`LongParameterList`/`TooManyFunctions` → 便宜可拆者拆;
  不值得拆者(如多參數 Composable、VM 的 combine)用 `@Suppress("RuleName")` 並加一行理由註解。
- **其他**:`UseCheckOrError`(用 `check`/`error` 取代 `throw IllegalStateException`)等小修。

## 結束狀態

每層之後重生 baseline。目標把 baseline 清空:
- 若清到 0:**刪除 `app/detekt-baseline.xml`** 並移除 `app/build.gradle.kts` 的 `baseline = …` 行。
- 若剩極少數(已用 `@Suppress` 標註理由者不會進 baseline),則 baseline 留下的應為 0;
  任何刻意保留者改用 inline `@Suppress` + 理由,而非 baseline。
全程 `:app:check` 維持綠燈,app 仍可建置/執行、行為不變。

## 分階段（單一 spec、分階段 plan;每階段可建置）

1. **自動 + 設定**:`detekt --auto-correct`;改 `detekt.yml`(FunctionNaming、MagicNumber);
   重生 baseline。`:app:check`/`assembleDebug` 綠燈。預期 baseline 由 200 降到 ~30。
2. **手動修真實 smell**:分群修掉剩餘 findings(可細分多個 commit),逐步重生 baseline 至 ~0;
   能清空就刪 baseline 檔 + 移除 build 設定行。`:app:check`、`assembleRelease`、
   `testDebugUnitTest`、`installDebug` 全綠且 app 行為不變。

## 風險與注意

- **auto-correct 之後務必重建**:格式修正可能動到很多檔,需 `assembleDebug` 確認可編譯、
  並眼睛掃過 diff 確認只是格式。
- **例外處理修改要保守**:`SwallowedException` 等不可改變「失敗時不崩、安全降級」的既有行為
  (例如 LocationProvider 取不到位置回 null)——只補日誌或縮小捕捉,不改控制流。
- **檔名重命名**:`CreateFoodRecordViewMode.kt` 等重命名需同步更新所有 import/引用(grep 確認)。
- **MagicNumber excludes 範圍**:`**/ui/**` 僅排除 Compose UI;勿把邏輯層也排除掉。
- **@Suppress 要有理由**:每個保留的 `@Suppress` 附一行說明,避免變成隱形技術債。

## 驗證

- 環境見專案記憶(JAVA_HOME 指向 Android Studio JBR、`gradlew.bat`、`Pixel_10_Pro` AVD/API 37)。
- 每階段:`.\gradlew.bat assembleDebug :app:check` 綠燈;baseline finding 數逐步下降。
- 最終:baseline 為空(或檔案已刪);`.\gradlew.bat clean assembleDebug assembleRelease
  testDebugUnitTest :app:check` 全綠;`installDebug` 後 app 正常啟動、主要流程行為不變。
- 反向驗證:故意加一個 detekt 違規 → `:app:detekt` 失敗(證明沒有 baseline 後新問題仍被擋)→ 還原。

## 明確排除（YAGNI）

- 不改任何 app 邏輯/UI 行為(純品質清理)。
- 不調 detekt 預設嚴格度(除了本案明列的 FunctionNaming/MagicNumber 兩條誤報設定)。
- 不處理 Lint baseline(那是另一條線)。
