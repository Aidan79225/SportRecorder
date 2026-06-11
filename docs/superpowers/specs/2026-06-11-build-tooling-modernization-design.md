# Build 工具鏈現代化：KTS + Version Catalog + detekt 設計文件

- 日期：2026-06-11
- 範圍：(1) Groovy Gradle 腳本改 Kotlin DSL（`.kts`）；(2) 依賴改用 version catalog
  （`gradle/libs.versions.toml`）；(3) 加入 detekt（核心 + formatting/ktlint），接進 `check`、
  baseline 圈住現有問題、新問題擋 build。
- 不更動 app 行為：AGP 9.2 / Kotlin 2.3.10 / 既有依賴版本全部維持,只改「表達方式」。
- 不在本案：升級任何依賴版本、CI 設定、模組拆分。

## 背景與目的

目前 build 腳本是 Groovy（`build.gradle` root + app、`settings.gradle`），依賴硬寫在
`app/build.gradle`,沒有 version catalog,也沒有靜態分析。本案把 build 工具鏈現代化:
KTS + catalog 讓版本集中、型別安全、IDE 友善;detekt 提供 Kotlin 靜態分析與格式檢查。

KTS 轉換與 catalog 互相依賴（`.kts` 會引用 `libs.*`），故**同一階段一起完成**;detekt 為第二階段。

## 1. `gradle/libs.versions.toml`（新增）

集中所有版本、函式庫、外掛。重點:

- `[versions]`:agp=9.2.0、kotlin=2.3.10、ksp=2.3.9、hilt=2.59.2、room=2.8.4、
  composeBom=2026.05.01、coroutines=1.7.1、lifecycle=2.7.0、activityCompose=1.8.2、
  navigationCompose=2.9.8、hiltNavigationCompose=1.2.0、serializationJson=1.7.3、
  coil=2.7.0、playServicesLocation=21.3.0、exifinterface=1.3.7、coreKtx=1.12.0、
  appcompat=1.6.1、constraintlayout=2.1.4、junit=4.13.2、androidxJunit=1.1.5、
  espresso=3.5.1、detekt=<最新支援 Kotlin 2.3 的版本,impl 時釘>。
- `[libraries]`:全部約 25 個依賴。**BOM 控管、無顯式版本者**(如
  `androidx.compose.material:material-navigation`、`androidx.lifecycle:lifecycle-runtime-compose`)
  在 catalog 中只列 `group` + `name`、不給 version（靠 compose-bom 約束）。compose-bom 用
  `androidx.compose:compose-bom` 一筆,各 compose 子庫(material3、ui-tooling 等)無版本。
- `[plugins]`:android-application、hilt、ksp、kotlin-compose、kotlin-serialization、detekt。
  （AGP 9 內建 Kotlin,無 `org.jetbrains.kotlin.android`。）

## 2. `settings.gradle.kts`（由 `settings.gradle` 轉換）

```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "SportRecorder"
include(":app")
```
version catalog 在預設路徑 `gradle/libs.versions.toml`,Gradle 自動偵測,不需額外宣告。

## 3. `build.gradle.kts`（root,由 `build.gradle` 轉換）

移除 `buildscript { classpath … }` 與 `allprojects { repositories … }`（repos 移到 settings),
改為:
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.detekt) apply false
}
```
移除手寫的 `clean` task（base plugin 已提供 `clean`）。

## 4. `app/build.gradle.kts`（由 `app/build.gradle` 轉換）

- `plugins { alias(libs.plugins.android.application); alias(libs.plugins.hilt);
  alias(libs.plugins.kotlin.compose); alias(libs.plugins.kotlin.serialization);
  alias(libs.plugins.ksp); alias(libs.plugins.detekt) }`
- `android { }` 完整轉 KTS 語法:`compileSdk = 36`、`signingConfigs`（debug/release，
  keystore 路徑/密碼/別名**逐字保留**)、`defaultConfig { applicationId = …; minSdk = 24;
  targetSdk = 36; versionCode = 12; versionName = "0.0.10"; testInstrumentationRunner = … }`、
  `buildTypes { debug { … signingConfig = signingConfigs.getByName("debug") }; release { … } }`、
  `buildFeatures { compose = true }`、`compileOptions { sourceCompatibility = JavaVersion.VERSION_21;
  targetCompatibility = JavaVersion.VERSION_21 }`、`namespace = "com.crazystudio.sportrecorder"`。
- `kotlin { jvmToolchain(21) }`。
- `dependencies { }` 全部改 catalog 別名:`implementation(libs.androidx.core.ktx)`、
  `implementation(platform(libs.androidx.compose.bom))`、`ksp(libs.room.compiler)`、
  `implementation(libs.hilt.android)`、`ksp(libs.hilt.compiler)`、各 compose / navigation /
  coil / play-services-location / exifinterface / coroutines / serialization-json 等對應別名,
  `debugImplementation`、`testImplementation`、`androidTestImplementation` 範疇維持不變。

## 5. detekt（第二階段）

- 在 app 模組套用 detekt plugin;設定:
  ```kotlin
  detekt {
      buildUponDefaultConfig = true
      config.setFrom(files("$projectDir/detekt.yml"))
      baseline = file("$projectDir/detekt-baseline.xml")
  }
  dependencies { detektPlugins(libs.detekt.formatting) }   // ktlint 規則
  ```
- 產生設定:`./gradlew detektGenerateConfig`(產 `detekt.yml`,可保留預設或精簡)。
- 產生 baseline:`./gradlew detektBaseline`(產 `detekt-baseline.xml`,把現有所有 finding
  圈住,既有程式碼不會讓 build 失敗)。
- 接進 check:detekt plugin 預設已把 `detekt` task 設為 `check` 的相依,故 `./gradlew check`
  會跑 detekt;**baseline 之外的新問題會讓 build 失敗**。

## 6. 關鍵風險:detekt 對 Kotlin 2.3 的相容性

detekt 內嵌的分析器版本落後 Kotlin。對策:
- 釘**最新且能解析 Kotlin 2.3 語法**的 detekt 版本(impl 時查 detekt releases / Kotlin 相容性;
  若最新 stable 無法解析 2.3,改用最新 RC/alpha)。
- 接進 `check` 的是**預設 `detekt` task(不做完整 type resolution)**——會做完整型別解析的
  rule 才最容易被新 Kotlin 卡住,預設 task 較穩。formatting(ktlint)規則不需 type resolution。
- 驗證時若 detekt 在解析某些 2.3 語法檔案時報錯,於 `detekt.yml` 局部關閉該規則或排除該檔,
  並記錄。

## 7. 其他注意

- **KTS 轉換正確性**:signingConfigs / buildTypes / compileOptions 必須等價轉換;
  以 `assembleRelease` 驗證 release 簽章與設定仍正確。
- **catalog 別名命名**:含點的別名會變巢狀 accessor(如 `libs.plugins.android.application`、
  `libs.androidx.core.ktx`);命名需與引用一致。
- **repos 搬移**:root `allprojects` repos 移到 settings `dependencyResolutionManagement` 後,
  舊 root 不應再宣告 repos(否則 Gradle 會警告/衝突,視 `repositoriesMode` 而定)。

## 8. 分階段（單一 spec、分階段 plan;每階段可建置）

1. **KTS + catalog**:新增 `gradle/libs.versions.toml`;`settings.gradle`→`.kts`、
   `build.gradle`→`.kts`、`app/build.gradle`→`.kts`(同一次,互相依賴)。
   刪除原 `.gradle` 檔。驗證 `assembleDebug`/`assembleRelease`/`testDebugUnitTest` 全綠、
   且設定與轉換前等價。
2. **detekt**:加 plugin（catalog）+ formatting + `detekt.yml` + baseline,接進 check。
   驗證:`./gradlew detekt` 過(baseline 圈住現有);故意加一個違規確認**會失敗**,再還原。

## 9. 驗證

- 環境見專案記憶(JAVA_HOME 指向 Android Studio JBR、`gradlew.bat`、`Pixel_10_Pro` AVD/API 37)。
- 階段 1:`.\gradlew.bat assembleDebug`、`assembleRelease`、`testDebugUnitTest` 皆 BUILD SUCCESSFUL;
  `installDebug` 後 app 仍正常啟動(設定等價、簽章正確)。
- 階段 2:`.\gradlew.bat detektBaseline` 產生 baseline;`.\gradlew.bat detekt` 通過;
  在某 .kt 檔故意製造一個 detekt 違規 → `.\gradlew.bat detekt`(或 `check`)**失敗** → 還原 → 再次通過。
- 全程不改任何 app 原始碼行為(除了驗證用、會還原的故意違規)。

## 10. 明確排除（YAGNI）

- 不升級任何依賴版本(純表達方式轉換 + 工具)。
- 不設定 CI、不加 pre-commit hook、不拆多模組。
- 不在本案修既有 detekt findings(全部進 baseline,日後再逐步清)。
