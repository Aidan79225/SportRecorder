# 開發現況總覽 · Development status

> 最後整理:2026-09-21(對應 `master` @ `360ca2a`,PR #57 合併後)
>
> 這份文件是**目前開發資訊的索引**:專案長什麼樣、做到哪裡、下一步是什麼、怎麼建置與發布。
> 設計文件(spec)與實作計畫(plan)仍住在 `docs/superpowers/`;初衷與設計原則見 `README.md` 與 `CLAUDE.md`。

## 1. 專案快照

| 項目 | 現況 |
| --- | --- |
| App | SportRecorder(`com.crazystudio.sportrecorder`)— 從斷食出發的飲食紀錄 app |
| 版本 | `versionName 0.6.2` / `versionCode 20`(最新 release tag:`0.6.2`) |
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
  + `model/`、`repository/`(介面)、`usecase/`(9 個)
- `data/` — `repository/`(Room + DataStore 實作)、`mapper/`;`dao/`、`entity/`、`database/`
- `backup/` — `BackupService`、`BackupDocument`(含 `SCHEMA_VERSION`)、`BackupMappers`、
  `BackupStore`/`BackupAuth` 介面
- `ui/` — Compose Multiplatform 畫面:`diet/`(home、record、editor、select、create/fasting)、
  `insights/`、`settings/`、`theme/`、`component/`;**7 個 ViewModel 全部在 commonMain**
- `platform/` — `expect`/介面形式的平台抽象(`LocationProvider`、`PhotoImporter`;照片檔案存取的介面在 `data/`)

**`:app`(Android 專屬,只剩薄薄一層)**

`MainActivity`、`SportApplication`、`di/AppModule`(Koin)、`ui/AppRoot`+`nav/Route`、
`reminder/`(AlarmManager、通知、BootReceiver)、`platform/` 與 `data/` 的 Android actuals、
`backup/GoogleBackupAuth`+`GoogleDriveBackupStore`、`util/PhotoStorage` 等。

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

## 4. 進行中 / 下一步

1. **Google Drive 備份 Phase 3 — PR #58 `claude/drive-backup-phase3`(open,尚未合併)**
   內容:`BackupViewModel`(commonMain)+ Koin wiring + Settings「備份與還原」畫面與 `BackupRoute`。
   合併前/後還缺:
   - OAuth consent screen 設定與審核(`drive.appdata` 屬 sensitive scope,有審核前置時間)
   - 實機端到端測試(PR 內附 10 項 checklist:登入 → 備份 → 重裝 → 還原)
   - PR 上目前沒有 CI 檢查結果,合併前需確認 `ci` 與 `ios-shared` 跑過
2. **備份引擎已知取捨(Phase 3 PR 明列,尚未處理)**
   - 自訂斷食類型備份上限 ≤ 10 筆
   - Drive 檔案列表未分頁(>1000 檔案有風險,已在程式碼註解)
   - `sizeBytes` 只存在 manifest,UI 不顯示
3. **iOS**:host app(Xcode 專案 / iOS Koin graph / DataStore 建立 / 通知)尚未開始;
   目前僅由 CI 的 `ios-shared` job 以 `:shared:iosSimulatorArm64Test` 守住可編譯性。
4. **文件狀態過期**:部分 spec 的 `Status` 沒跟上實作(見第 6 節標註),下次動到相關功能時順手更新。

## 5. 建置、測試與發布

```bash
# 完整本地 gate(與 CI 相同)
./gradlew assembleDebug testDebugUnitTest :app:detekt :app:lintDebug :shared:jvmTest
```

- `JAVA_HOME` 需指向 Android Studio JBR(本機 PATH 上沒有 Java)。
- 動到 `commonMain` 時,另外需要 iOS 驗證:`./gradlew :shared:iosSimulatorArm64Test`(需 macOS;CI 有 `macos-latest` job)。
- 測試現況:22 個測試檔;純邏輯計算機(`DietWindow`、`InsightsAggregator`、`ReminderPlanner`)與
  備份引擎在 `:shared` 的 `commonTest`,ViewModel / mapper / repository 測試在 `:app` 的 `test`。
- **CI**(`.github/workflows/ci.yml`,PR 與 push to `master` 觸發)
  - `build`(ubuntu):assemble + unit test + detekt + lint + `:shared:jvmTest`
  - `ios-shared`(macOS):`:shared:iosSimulatorArm64Test`
- **發布**(`.github/workflows/release.yml`):bump 版號 → 更新 `distribution/whatsnew/*` →
  推 `x.y.z` tag → 自動建置簽章 AAB 並上傳 Google Play(細節見 `.claude/skills/release`)。
  簽章金鑰與 Play service account 走 repo secrets,repo 內不放 keystore(`debug.keystore` 除外)。

> 註:tag `0.12.0` 是早期(2026-06-10)版號規則不同時留下的孤兒 tag,指向舊 commit;
> 目前的版號線是 `0.0.x → 0.6.2`。

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
| 06-23 | Google Drive 備份 | ✓ | ✓ | Phase 1–2 已合併;Phase 3 在 PR #58 |

> Phase 3b(Room → commonMain)、Phase 4(Compose Multiplatform UI)、Phase 5(use case + VM)
> 是照著 KMP roadmap 一路以 PR #34–#53 逐步落地的,沒有各自獨立的 spec 檔。
