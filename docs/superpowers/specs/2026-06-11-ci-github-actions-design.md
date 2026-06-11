# GitHub Actions CI（PR 品質閘門）設計文件

- 日期：2026-06-11
- 範圍:為 Android app 建立 GitHub Actions CI,在每個 PR / push to master 自動跑
  build + unit tests + detekt + lint,把這季建立的品質鎖進自動化閘門。
- 不在本案:CD(release AAB / Play 自動上傳)、instrumented(emulator)測試、`assembleRelease`
  於 CI、模組化/快取以外的 build 調整。

## 背景與目的

專案目前**沒有任何 CI**(`.github/` 不存在)。detekt(無 baseline)、Android Lint(有 baseline)、
單元測試(`DietWindowTest`)、build 全靠手動執行。本案加一條 GitHub Actions 工作流程,讓每個 PR
自動驗證,避免回歸、讓品質閘門自動強制。使用者已選:**先只做 CI**、閘門為
**build + unit test + detekt + lint**(跳過 instrumented 與 release build)。

已知環境:單一 `:app` 模組、KTS + version catalog、Gradle wrapper 9.4.1、AGP 9.2、Kotlin 2.3.10、
`jvmToolchain(21)`、compileSdk 36。`local.properties` 未追蹤(CI 用 runner 的 `ANDROID_HOME`)。
**Linux `gradlew` 目前 git mode 為 `100644`(非可執行)** —— 需修正。

## 1. 工作流程檔 `.github/workflows/ci.yml`

- **name**: `CI`
- **觸發**:
  - `pull_request`:`branches: [master]`
  - `push`:`branches: [master]`
  - `workflow_dispatch`(手動)
- **concurrency**:`group: ci-${{ github.ref }}`、`cancel-in-progress: true`(同 ref 重推時取消舊跑)。
- 單一 job `build`,`runs-on: ubuntu-latest`。

## 2. Job `build` 步驟

1. `actions/checkout@v4`
2. `actions/setup-java@v4`:`distribution: temurin`、`java-version: '21'`(Gradle 跑在 JDK 21;
   `jvmToolchain(21)` 解析到它)。
3. `gradle/actions/setup-gradle@v4`:Gradle 依賴/建置快取(自動)。
4. `android-actions/setup-android@v3`:備妥 cmdline-tools 並接受授權;接著確保
   `platforms;android-36` 與 `build-tools;36.0.0` 已安裝(AGP 9.2 / compileSdk 36),
   避免首跑因缺 SDK component 失敗(`sdkmanager` 明確安裝較穩;AGP 自動下載為備援)。
5. 確保 `gradlew` 可執行:`chmod +x ./gradlew`(防禦性;主修正見 §3)。
6. **跑閘門**:
   ```
   ./gradlew assembleDebug testDebugUnitTest :app:detekt :app:lintDebug --stacktrace
   ```
   - `assembleDebug`:編譯。
   - `testDebugUnitTest`:單元測試(含 `DietWindowTest`)。
   - `:app:detekt`:靜態分析(無 baseline → 新問題即失敗)。
   - `:app:lintDebug`:Android Lint(使用既有 `lint-baseline.xml`)。
7. **失敗時上傳報告**(`if: failure()`,用 `actions/upload-artifact@v4`):
   `app/build/reports/`(tests / detekt / lint*)、`app/build/test-results/`,方便看失敗原因。

## 3. 修正 `gradlew` 可執行位元

把追蹤的 git mode 由 `100644` 改為 `100755`:`git update-index --chmod=+x gradlew`(提交此變更),
讓 repo 本身正確、任何 Linux runner 不需額外 chmod。工作流程仍保留 §2.5 的 `chmod +x` 作雙保險。

## 4. 分階段（單一 spec、單一 plan;產出可驗證的綠燈 CI）

1. 新增 `.github/workflows/ci.yml`、修正 `gradlew` 可執行位元 → 開 PR → 觀察 Actions 跑綠燈
   (build + unit test + detekt + lint 全過,與本機一致)。
（範圍小,不需多階段;以「PR 上 CI 綠燈」為完成定義。）

## 5. 完成後的後續（非本案檔案變更,屬 repo 設定）

CI 第一次綠燈後,於 GitHub → Settings → Branches → `master` 把 **CI check 設為 required status check**,
讓紅燈 PR 無法合併。(此為 GitHub 設定,由使用者操作;plan 會在最後提醒。)

## 6. 風險與注意

- **Android SDK 授權/版本**:runner 預裝的 SDK 未必含 36;以 `setup-android` + 明確 `sdkmanager`
  安裝 `platforms;android-36`、`build-tools;36.0.0` 確保可重現。
- **首跑時間**:無快取首次較慢(下載 Gradle 9.4.1 + 依賴);`setup-gradle` 之後會快取。
- **detekt/lint 設定一致**:CI 用與本機相同的 `:app:detekt`(無 baseline)、`:app:lintDebug`
  (lint baseline),行為與本機一致 → 本機綠則 CI 綠。
- **`gradlew` mode**:Windows 端看不出差異,但 Linux runner 必須可執行;§3 修正後即可。

## 7. 驗證

- 推送分支 / 開 PR → GitHub Actions `CI` 工作流程觸發並**綠燈**(build、unit test、detekt、lint 皆過)。
- 以 `gh run list` / `gh run watch` 觀察該次 run 結果為 success。
- 反向確認(選):在分支故意製造一個 detekt 違規或失敗測試,確認 CI **變紅**並擋住,再還原。

## 8. 明確排除（YAGNI）

- 不做 CD(release/Play 上傳)、不跑 instrumented(emulator)測試、不在 CI 跑 `assembleRelease`。
- 不導入 test-report 註解 action、screenshot 測試、coverage(可日後再加)。
- 不改 branch protection 之外的 repo 設定。
