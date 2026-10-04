# 開發現況總覽 · Development status

> 最後整理:2026-10-04(對應 `master` @ `2c2a70b`,PR #73 合併後)
>
> 這份文件是**目前開發資訊的索引**:專案長什麼樣、做到哪裡、下一步是什麼、怎麼建置與發布。
> 設計文件(spec)與實作計畫(plan)仍住在 `docs/superpowers/`;初衷與設計原則見 `README.md` 與 `CLAUDE.md`。

## 1. 專案快照

| 項目 | 現況 |
| --- | --- |
| App | SportRecorder(`com.crazystudio.sportrecorder`)— 從斷食出發的飲食紀錄 app |
| 版本 | `versionName 0.10.0` / `versionCode 25`(最新 release tag:`0.10.0`,2026-10-02) |
| 平台 | Android 出貨中;iOS 尚未建立 host app(`:shared` 已可編到 iOS) |
| Modules | `:app`(Android host + platform actuals)、`:shared`(KMP:domain / data / UI / VM) |
| SDK | `minSdk 24`、`targetSdk 36`、`compileSdk 36`、Java 21 |
| 語言/框架 | Kotlin 2.3.10、AGP 9.2.0、Compose Multiplatform 1.11.1、Koin 4.1.0、Room 2.8.4、DataStore(MP core)、Coil 3.4.0、kotlinx-datetime 0.7.1、kotlinx-serialization、OkHttp(Android only) |
| 語系 | `en-US`、`zh-rTW`(Compose Resources 於 `:shared`) |

> KMP 依賴鐵律:任何新的 KMP 依賴必須是用 Kotlin ≤ 2.3.10 建置的,否則 iOS 的 klib ABI 會爆
> (例:Coil 3.5.0 已升到 Kotlin 2.4.0,因此我們釘在 3.4.0)。

## 2. 程式碼分層

**`:shared/commonMain`(平台無關,絕大多數程式碼在這裡)**

- `domain/` — 純邏輯:`diet/DietWindow`、`insights/InsightsAggregator`、`reminder/ReminderPlanner`
  、`diet/HomeTagline`(首頁標語挑選)+ `model/`、`repository/`(介面)、`usecase/`(9 個)
- `data/` — `repository/`(Room + DataStore 實作)、`mapper/`;`dao/`、`entity/`、`database/`
- `backup/` — `BackupService`、`BackupDocument`(含 `SCHEMA_VERSION`)、`BackupMappers`、
  `BackupStore`/`BackupAuth` 介面、`BackupJobRunner`、`BackupProgress`
- `ui/` — Compose Multiplatform 畫面:`diet/`(home、record、editor、select、create/fasting)、
  `insights/`、`settings/`、`backup/`、`theme/`、`component/`;**9 個 ViewModel 全部在 commonMain**
- `platform/` — `expect`/介面形式的平台抽象(`LocationProvider`、`PhotoImporter`;照片檔案存取的介面在 `data/`)

**`:app`(Android 專屬,只剩薄薄一層)**

`MainActivity`、`SportApplication`、`di/AppModule`(Koin)、`ui/AppRoot`+`nav/Route`、
`ui/nav/BottomSheetNavigator`(M3 sheet host;AndroidX 沒有 M3 版的 sheet navigator,自寫,仿 `DialogNavigator`)、
`reminder/`(AlarmManager、通知、BootReceiver)、`platform/` 與 `data/` 的 Android actuals、
`backup/GoogleBackupAuth`+`GoogleDriveBackupStore`、`backup/DriveRestClient`+`BackupForegroundService`、
`util/PhotoStorage` 等。

**`:shared/iosMain`** — 目前只有 `ui/shared/MainViewController.kt`(尚無 Xcode 專案)。

## 3. 已完成的里程碑

| 主題 | 狀態 | 備註 |
| --- | --- | --- |
| View → Compose 全面遷移 | ✅ | 舊 Fragment 只剩 `ui/base/BaseFragment` 殘件 |
| Clean architecture(EatRecord / FastingType) | ✅ | repository + use case 分層 |
| MD3 色彩系統、Typography roles、狀態頁改版 | ✅ | |
| DataStore 遷移(取代 SharedPreferences) | ✅ | 含 `SharedPreferencesMigration` |
| 回顧 / Insights 分頁 | ✅ | `InsightsAggregator` 純邏輯 + 單元測試 |
| 斷食提醒(進食視窗 / 斷食達標) | ✅ | `ReminderPlanner` 純邏輯 + AlarmManager actual |
| CI(GitHub Actions)、detekt、lint baseline、ViewModel 測試套件 | ✅ | |
| KMP 遷移 Phase 1–5 | ✅ | 1 domain → 2 Koin → 3a DataStore / 3b Room → 4 Compose Multiplatform UI → 5 use case + ViewModel |
| Google Drive 備份 Phase 1(commonMain 引擎) | ✅ PR #56 | `BackupService` backup/restore/listSnapshots,以 fake 做 TDD |
| Google Drive 備份 Phase 2(Google actuals) | ✅ PR #57 | `GoogleBackupAuth`(drive.appdata)、`GoogleDriveBackupStore`(Drive v3 REST) |
| Google Drive 備份 Phase 3(設定頁「備份與還原」) | ✅ PR #58,0.7.0 上架 | 之後 #62 修登入無反應、#63 修逾時與錯誤診斷 |
| 備份優化(issue #64) | ✅ PR #67,0.8.0 | 前景服務、進度與取消、照片並行、Drive 分頁、還原前安全快照 |
| 上架前外部待辦(issue #65) | ✅ 2026-10 關閉 | Play 資料安全表單、前景服務宣告、OAuth sensitive scope 驗證 |
| 回顧改善 A + B1、B3–B8 | ✅ PR #60 / #66 / #70 / #73 | 地點地圖(含合併與全螢幕縮放)、單一期間控制、日曆點擊 sheet、週節奏圖、可展開照片牆(`LazyColumn`);**B2 已放棄** |
| 測試覆蓋補洞、E2E flow 測試 | ✅ PR #61 / #68 | Room migration 與契約、編輯器 UI、照片管線、提醒 |
| Material 3 bottom-sheet navigator | ✅ PR #69 | 移除 Material 2 依賴 |
| 首頁動態標語 | ✅ PR #71,0.10.0 | 依時段與進度輪替、含名言出處 |
| 編輯器拍照跨 activity 重建保留 | ✅ PR #72,0.10.0 | |

## 4. 進行中 / 下一步

目前**沒有進行中的分支或 open PR**。候選方向都開成 issue 了(2026-10-04,尚未排優先序):

| Issue | 主題 | 循環 | 備註 |
| --- | --- | --- | --- |
| #74 | 自動備份(背景定期備份) | 守住紀錄 | 要先決定快照保留策略(`KEEP_LAST = 3`) |
| #75 | 快速記一餐(桌面小工具 / 快速設定按鈕) | Capture | |
| #76 | 去年的今天 | Reflect / Re-engage | |
| #77 | 回顧頁「月」檢視的節奏圖 | Insight | 週限定是刻意的,月需要另一種呈現 |
| #78 | iOS host app | — | 需要 macOS;目前僅由 CI 的 `ios-shared` 守住可編譯性 |

其他已知事項:

1. **備份引擎已知取捨**(PR #58 當時列出,之後未再處理):自訂斷食類型備份上限 ≤ 10 筆;
   `sizeBytes` 只存在 manifest,UI 不顯示。
2. **文件狀態過期**:部分舊 spec 的 `Status` 沒跟上實作(見第 6 節標註),下次動到相關功能時順手更新。

**已放棄(不再列為待辦)**

- **B2 每日歷史斷食目標**(2026-10-04,owner 決定):只存目前的目標,改目標時過去的日子會用新目標
  重新判斷、重新分組。這是已知且接受的行為。

## 5. 建置、測試與發布

```bash
# 完整本地 gate(與 CI 相同)
./gradlew assembleDebug testDebugUnitTest :app:detekt :app:lintDebug :shared:jvmTest
```

- `JAVA_HOME` 需指向 Android Studio JBR(本機 PATH 上沒有 Java)。
- 動到 `commonMain` 時,另外需要 iOS 驗證:`./gradlew :shared:iosSimulatorArm64Test`(需 macOS;CI 有 `macos-latest` job)。
- 測試現況:58 個測試檔(其中 13 個 instrumented);純邏輯計算機(`DietWindow`、`InsightsAggregator`、`ReminderPlanner`)與
  備份引擎在 `:shared` 的 `commonTest`,ViewModel / mapper / repository 測試在 `:app` 的 `test`;
  instrumented tests 現在也涵蓋 Room migration 與 repository 契約、餐點編輯器 UI、照片管線、
  提醒(AlarmManager slot 與 receiver)。
- `AppDatabase` 已開啟 `exportSchema`,`shared/schemas/` 下的 JSON 必須隨每次 schema 變更一起
  commit;migration 測試在 `app/src/androidTest/.../database/`。
- **Instrumented tests(本機、不進 CI)**:`app/src/androidTest/.../backup/` 用真的 Room、真的
  `BackupForegroundService` 與通知、真的 `BackupScreen`,只假造雲端。跑法:先開一台模擬器,然後
  `$env:ANDROID_SERIAL="emulator-5556"; .\gradlew.bat :app:connectedDebugAndroidTest`
  (單一類別:`.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=<fqcn>"`,
  在 PowerShell 裡 `-P` 參數一定要加引號)。報告在
  `app/build/reports/androidTests/connected/debug/index.html`。請用專門的測試模擬器:connected test
  跑完會解除安裝 app,也會對裝置上現有資料做一次真的備份流程。
- **CI**(`.github/workflows/ci.yml`,PR 與 push to `master` 觸發)
  - `build`(ubuntu):assemble + unit test + detekt + lint + `:shared:jvmTest`
  - `ios-shared`(macOS):`:shared:iosSimulatorArm64Test`
- **發布**(`.github/workflows/release.yml`):bump 版號 → 更新 `distribution/whatsnew/*` →
  推 `x.y.z` tag → 自動建置簽章 AAB 並上傳 Google Play(細節見 `.claude/skills/release`)。
  簽章金鑰與 Play service account 走 repo secrets,repo 內不放 keystore(`debug.keystore` 除外)。

> 註:tag `0.12.0` 是早期(2026-06-10)版號規則不同時留下的孤兒 tag,指向舊 commit;
> 目前的版號線是 `0.0.x → 0.10.0`。

## 6. 文件索引(`docs/superpowers/`)

每個主題都是 spec(設計)+ plan(實作步驟),spec 內含**初衷對照 / North-Star check**。

| 日期 | 主題 | spec | plan | 實作狀態 |
| --- | --- | --- | --- | --- |
| 06-10 | Java toolchain 升級 | ✓ | ✓ | 已完成 |
| 06-10 | View → Compose 遷移 | ✓ | ✓ | 已完成 |
| 06-10 | 餐點照片 + GPS | ✓ | ✓ | 已完成 |
| 06-11 | Build tooling 現代化 | ✓ | ✓ | 已完成 |
| 06-11 | CI(GitHub Actions) | ✓ | ✓ | 已完成 |
| 06-11 | Clean arch(EatRecord) | ✓ | ✓ | 已完成 |
| 06-11 | detekt paydown | ✓ | ✓ | 已完成 |
| 06-11 | 餐點編輯器 | ✓ | ✓ | 已完成 |
| 06-11 | Home 視窗邏輯 | ✓ | ✓ | 已完成 |
| 06-11 | MD3 色彩系統 | ✓ | ✓ | 已完成 |
| 06-11 | App icon 還原 | ✓ | ✓ | 已完成 |
| 06-12 | Clean arch(FastingType) | ✓ | ✓ | 已完成 |
| 06-12 | DataStore 遷移 | ✓ | ✓ | 已完成 |
| 06-13 | 狀態頁改版 | ✓ | ✓ | 已完成 |
| 06-13 | Typography roles | ✓ | ✓ | 已完成 |
| 06-14 | ViewModel 測試套件 | ✓ | ✓ | 已完成 |
| 06-16 | 回顧 / Insights | ✓ | ✓ | 已完成(spec Status 仍寫 pending,已過期) |
| 06-19 | 斷食提醒 | ✓ | — | 已完成(spec Status 仍寫 pending,已過期) |
| 06-20 | KMP / iOS 遷移總覽 | ✓ | — | roadmap;Phase 1–5 已完成,iOS host 未開始 |
| 06-20 | KMP Phase 1(shared domain) | ✓ | — | 已完成(spec Status 仍寫 implementing) |
| 06-20 | KMP Phase 2(Koin) | ✓ | — | 已完成 |
| 06-20 | KMP Phase 3a(DataStore) | ✓ | — | 已完成 |
| 06-23 | Google Drive 備份 | ✓ | ✓ | 已完成(PR #56–#58,0.7.0 上架) |
| 09-21 | 回顧 / Insights 改善(bucket A + B1) | ✓ | ✓(B4/B6/B7) | A + B1(PR #60)、B3–B8 已完成(#66 / #70 / #73);B2 已放棄 |
| 09-21 | E2E / flow 測試套件 | ✓ | — | 已完成(PR #61) |
| 09-27 | 回顧地點卡改為地圖(取代 B3) | ✓ | — | 已完成(PR #66) |
| 09-27 | 回顧單一期間控制、進食日分組、日曆點擊 sheet、週節奏圖(B4/B6/B7/B8) | ✓ | — | 已完成(PR #66) |
| 09-27 | 備份優化(進度、前景服務、並行、分頁、還原安全快照) | ✓ | ✓ | 已完成(PR #67) |
| 09-27 | 備份 instrumented tests(本機) | ✓ | ✓ | 已完成(同 PR #67) |
| 09-28 | 測試覆蓋缺口(migration、Room 契約、編輯器 UI、照片管線、提醒、備份小洞) | ✓ | ✓ | 已完成 |
| 09-28 | Material 3 bottom-sheet navigator(移除 M2 依賴) | ✓ | ✓ | 已完成(PR #69) |
| 09-28 | 回顧地圖:marker 合併 + 全螢幕縮放 | ✓ | ✓ | 已完成(PR #70) |
| 09-29 | Home 動態標語(依時段 / 進度輪替) | ✓ | — | 已完成(PR #71) |

> Phase 3b(Room → commonMain)、Phase 4(Compose Multiplatform UI)、Phase 5(use case + VM)
> 是照著 KMP roadmap 一路以 PR #34–#53 逐步落地的,沒有各自獨立的 spec 檔。
