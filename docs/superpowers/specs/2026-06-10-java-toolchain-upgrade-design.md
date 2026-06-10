# Java 21 + Android 工具鏈現代化 設計文件

- 日期：2026-06-10
- 範圍：純 build 設定遷移，不更動任何 app 業務邏輯／UI 程式碼
- 模組：`app`（單一 application 模組）

## 背景與目的

需求起點是「升級 Java」。專案目前語言等級停在 Java 17，build 工具鏈也偏舊
（AGP 8.3 / Gradle 8.4 / Kotlin 1.9.23）。經討論後決定一次升到 2026/6 的最新穩定
工具鏈，把 Java 21 連同整條 Android build chain 一起對齊。Java 21 本身只是其中
最小的一塊；真正的工作是從「AGP 8.3 時代」搬到「AGP 9 / Kotlin 2.3 時代」，並以
Gradle Java toolchain 讓 build 在任何機器上都可重現。

預期成果：

- App 以 Java 21 語言等級編譯，Kotlin bytecode target 為 21。
- Build 使用 JDK 21（由 Gradle toolchain 自動 provision，不依賴開發者本機預設 JDK）。
- 整條工具鏈（Gradle / AGP / Kotlin / KSP / Compose compiler / Hilt / Navigation /
  Room）升到互相對齊的最新穩定版。
- App 仍可正常編譯、安裝、執行，行為與升級前一致（targetSdk 行為變更已納入測試範圍）。

## 目標版本集（互相對齊）

| 項目 | 現在 | 目標 |
|---|---|---|
| Build JDK | 17（本機） | 21（Gradle Java toolchain） |
| App 語言等級 | 17 | 21 |
| Gradle | 8.4 | 9.4.1 |
| AGP | 8.3.0 | 9.2.0 |
| Kotlin (KGP) | 1.9.23 | 2.3.0（AGP 9 內建 Kotlin） |
| KSP | 1.9.23-1.0.19 | 對齊 Kotlin 2.3 的 KSP2 版本（如 2.3.x-x.x.x） |
| Compose 編譯 | `composeOptions` 擴充 | `org.jetbrains.kotlin.plugin.compose`（版本 = Kotlin） |
| Hilt / Dagger | 2.51.1 | 2.59 |
| Navigation runtime | 2.7.7 | 2.9.8 |
| Navigation safe-args plugin | 2.5.3 | 2.9.8 |
| Room | 2.6.1 | 2.7.x（KSP2 相容） |
| compose-bom | 2024.04.00 | 最新 2026.x |
| compileSdk / build-tools | 34 | 36 / 36.0.0 |
| targetSdk | 34 | 36 |

> 實作時務必以 AGP 9.2.0 與 Kotlin 2.3.0 的官方 release notes 為準，把 KSP、
> Compose compiler plugin 的版本「精確」對齊所採用的 Kotlin 版本；版本不一致是這類
> 升級最常見的失敗點。

## 要修改的檔案

### 1. `gradle/wrapper/gradle-wrapper.properties`
- `distributionUrl` 由 `gradle-8.4-bin.zip` → `gradle-9.4.1-bin.zip`。

### 2. 根 `build.gradle`
- AGP classpath `com.android.tools.build:gradle` `8.3.0` → `9.2.0`。
- 移除 `org.jetbrains.kotlin:kotlin-gradle-plugin` classpath（AGP 9 內建 Kotlin，
  外加會衝突）。
- 新增 Compose compiler plugin 宣告：`org.jetbrains.kotlin.plugin.compose`
  （version = Kotlin 版本，`apply false`）。
- `androidx.navigation:navigation-safe-args-gradle-plugin` `2.5.3` → `2.9.8`。
- `com.google.dagger:hilt-android-gradle-plugin` `2.51.1` → `2.59`。
- KSP plugin（`com.google.devtools.ksp`）→ 對齊 Kotlin 2.3 的 KSP2 版本。
- 若需明確釘住 Kotlin 版本（而非沿用 AGP 內建預設），依 AGP 9「built-in Kotlin」
  指引設定其版本覆寫機制。

### 3. `app/build.gradle`
- plugins：移除 `id 'kotlin-android'`；新增 `id 'org.jetbrains.kotlin.plugin.compose'`。
- 移除整個 `composeOptions { kotlinCompilerExtensionVersion = ... }` 區塊。
- `compileSdk 34` → `36`；`targetSdkVersion 34` → `36`（明確保留宣告，因 AGP 9 預設
  `targetSdk = compileSdk`，明寫可避免日後誤動）。
- `compileOptions`：`sourceCompatibility` / `targetCompatibility`
  `VERSION_17` → `VERSION_21`。
- 新增 `kotlin { jvmToolchain(21) }`；移除舊的 `kotlinOptions { jvmTarget = '17' }`
  （toolchain 已決定 jvmTarget）。
- 依賴升級：
  - Hilt `com.google.dagger:hilt-android` / `hilt-compiler` `2.51.1` → `2.59`。
  - Room `2.6.1` → `2.7.x`（runtime / ktx / compiler / testing 全部一致）。
  - Navigation `navigation-fragment-ktx` / `navigation-ui-ktx` `2.7.7` → `2.9.8`。
  - `compose-bom` `2024.04.00` → 最新 2026.x（其餘 compose 依賴沿用 BOM 管控、不釘版本）。
  - 視需要對齊 `activity-compose` / `lifecycle-*` 至與新 BOM 相容的版本。

### 4. `gradle.properties`
- 移除 `android.enableJetifier=true`（專案全 AndroidX，不需要 Jetifier）。
- 保留現有 `android.nonTransitiveRClass` / `nonFinalResIds` 設定；AGP 9 預設 R class
  non-final，先不主動更動，編譯出現問題再處理。

## 風險與緩解

- **AGP 9 內建 Kotlin**：`kotlin-android` plugin 必須移除，Kotlin 版本改由 AGP 管理；
  KSP 與 Compose compiler plugin 必須釘成同一 Kotlin 版本，否則 build 失敗。
- **Compose compiler 遷移**：漏移除 `kotlinCompilerExtensionVersion` 會編譯失敗；新機制
  改用 `org.jetbrains.kotlin.plugin.compose`。
- **KSP1 → KSP2**：Room / Hilt 皆走 ksp，舊 KSP 在 AGP 9 會壞，故 Room 與 Hilt 必須
  同批升級到支援 KSP2 的版本。
- **targetSdk 34 → 36**：屬執行期行為變更（如 SDK 35 edge-to-edge 強制、權限行為），
  需實機測試（見驗證）。
- **R class non-final**：AGP 9 預設行為；專案以 Kotlin `when`（非 Java `switch`）使用 R，
  預期不受影響，但需以編譯結果確認。
- **Hilt 與 KSP2 相容性**：以 Dagger/Hilt 2.59 為準（已支援 Kotlin 2.3 / KSP2 / AGP 9）。

## 驗證

1. **環境**：本機備妥 JDK 21；Android Studio 的 Gradle JDK 與 `JAVA_HOME` 指向可執行
   Gradle 9.4.1 的 JDK（17+ 皆可，toolchain 會另外 provision JDK 21 做編譯）。
2. **乾淨建置**：`./gradlew clean assembleDebug` 必須成功，且無 KSP / Compose / Kotlin
   版本不一致的警告或錯誤。
3. **Lint / 編譯警告**：確認沒有出現「kotlin-android plugin 已被內建」「composeOptions 已
   失效」「KSP 自動升級」等遷移殘留警告。
4. **安裝執行**：在實機或模擬器（建議含 Android 15 / API 35 與 API 36）安裝 debug APK，
   手動走過主要流程：Diet 列表、建立 / 選擇 Fasting Type（已轉 Compose 的畫面）、
   建立 Eat Time / Food Record、Timer 計時、Room 資料讀寫存活。
5. **targetSdk 36 行為**：特別檢查 edge-to-edge 版面（status / navigation bar 不遮擋內容）
   與任何 runtime 權限流程在 API 35+ 上的表現。
6. **Release 建置（選擇性）**：`./gradlew assembleRelease` 確認 release signing 與
   minify 設定在新工具鏈下仍可產出。

## 明確排除（YAGNI）

- 不導入 Version Catalog（`libs.versions.toml`）；維持現有 Groovy `build.gradle` 風格，
  降低本次改動面。
- 不重構 app 業務邏輯、UI、或 DI 結構。
- 不把 Fragment 導航改成 Compose type-safe navigation（safe-args 仍保留）。
