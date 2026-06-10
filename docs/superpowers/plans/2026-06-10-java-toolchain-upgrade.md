# Java 21 + Android Toolchain Modernization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把專案語言等級升到 Java 21，並把整條 Android build 工具鏈（Gradle / AGP / Kotlin / KSP / Compose compiler / Hilt / Room / Navigation）一次升到 2026/6 對齊的最新穩定版，build 以 Gradle Java toolchain 在 JDK 21 上可重現。

**Architecture:** 純 build 設定遷移，不動任何 app 業務邏輯／UI 程式碼。先做兩個可獨立驗證的小步（移除 Jetifier、Java 21 語言等級＋toolchain，皆在現有 AGP 8.3 工具鏈上完成並建置通過），再做一次不可分割的大跳躍（AGP 9 / Gradle 9 / Kotlin 2.3 / KSP2 / Compose plugin / Hilt / Room / Navigation 同批升級），最後處理 compileSdk/targetSdk 36 的行為變更。

**Tech Stack:** Android Gradle Plugin 9.2.0、Gradle 9.4.1、Kotlin 2.3.0（AGP 內建）、KSP2、Jetpack Compose（compose-bom）、Hilt/Dagger、Room、Navigation safe-args、Groovy `build.gradle`。

**驗證說明：** 本計畫無單元測試可寫；每個 task 的「測試」是 Gradle 建置成功 + 必要時手動冒煙執行 app。`<JDK21_HOME>` 代表本機 JDK 21 的安裝路徑。

---

## 前置條件

- 本機已安裝 **JDK 21**（記下安裝路徑，下面以 `<JDK21_HOME>` 表示）。
- 在 `feat/java-toolchain-upgrade` 分支上作業（規格 commit 已在此分支）。
- Android Studio 的 Gradle JDK 之後需指向可執行對應 Gradle 版本的 JDK；命令列驗證時用
  `org.gradle.java.home` 或 `JAVA_HOME` 指定。

---

## Task 1: 確認並釘死實際版本號

這些版本必須互相精確對齊，否則 build 會失敗；本 task 不改任何檔案，只查官方 release notes
並把最終版本填進下表，後續 task 一律引用這張表。

**Files:** 無（僅記錄；可把結果寫進本檔此區塊）。

- [ ] **Step 1: 查 AGP 9.2.0 release notes，確認其要求的 Gradle 版本、內建 Kotlin (KGP) 版本、build-tools、max compileSdk**

開啟並閱讀：
- `https://developer.android.com/build/releases/gradle-plugin`（AGP↔Gradle↔JDK↔compileSdk 對照）
- `https://developer.android.com/build/releases/agp-9-0-0-release-notes`（內建 Kotlin、破壞性變更）

- [ ] **Step 2: 確認 KSP2、Compose compiler plugin、Hilt/Dagger、Room、Navigation、compose-bom 與該 Kotlin 版本相容的最新版**

開啟並閱讀：
- KSP：`https://github.com/google/ksp/releases`（找對齊所選 Kotlin 版本的 `com.google.devtools.ksp` 版本字串）
- Dagger/Hilt：`https://github.com/google/dagger/releases`
- Room：`https://developer.android.com/jetpack/androidx/releases/room`
- Navigation：`https://developer.android.com/jetpack/androidx/releases/navigation`
- Compose BOM：`https://developer.android.com/jetpack/compose/bom/bom-mapping`

- [ ] **Step 3: 填入並鎖定版本表**

已於 2026-06-10 對官方來源確認的鎖定版本（取代原工作預設值）：

| 用途 | 變數 | 鎖定版本 | 來源確認重點 |
|---|---|---|---|
| Gradle wrapper | `GRADLE_VERSION` | `9.4.1` | AGP 9.2 最低且預設 Gradle |
| AGP classpath | `AGP_VERSION` | `9.2.0` | 最新 stable |
| Kotlin (KGP / compose plugin) | `KOTLIN_VERSION` | `2.3.10` | AGP 9.2 內建 Kotlin |
| KSP plugin | `KSP_VERSION` | `2.3.9` | KSP 2.3.0 起改純 semver、與 Kotlin 版本解耦，跨 Kotlin 2.3.x 通用 |
| Hilt / Dagger | `HILT_VERSION` | `2.59.2` | 須 2.59.2（修正 AGP 9 incremental build 與 jetifier 編譯錯誤），非 2.59/2.59.1 |
| Room | `ROOM_VERSION` | `2.8.4` | 最新 stable，支援 KSP2 / Kotlin 2.x（勿用 alpha 的 3.0） |
| Navigation（runtime + safe-args） | `NAV_VERSION` | `2.9.8` | safe-args 2.9.7+ 已不需 `android.useAndroidX` 屬性 |
| Compose BOM | `COMPOSE_BOM` | `2026.06.00` | 與 Kotlin/compiler 版本無關，獨立管控 Compose 函式庫 |
| compileSdk / targetSdk | `SDK` | `36` | AGP 9.2 max API 36（36.1 為 preview） |
| build-tools | `BUILD_TOOLS` | `36.0.0` | AGP 9.2 預設 |

> 注意：Compose compiler plugin 版本必須等於 Kotlin 版本（`2.3.10`）。KSP `2.3.9` 已與
> Kotlin 編譯器版本解耦（純 semver），不要寫成舊的 `<kotlin>-<ksp>` 格式。
> 唯一待現場眼睛確認的點：AGP 9.2 release notes 相容性表的「Kotlin」列是否確為 2.3.10
> （研究時該列未被完整回讀，但 2.3.10 為一致且最新的結論）。

- [ ] **Step 4: Commit 版本表**

```bash
git add docs/superpowers/plans/2026-06-10-java-toolchain-upgrade.md
git commit -m "[Docs] Pin exact versions for toolchain upgrade"
```

---

## Task 2: 移除 Jetifier（現有工具鏈上，安全小步）

專案全部使用 AndroidX，`enableJetifier` 不需要。先在 AGP 8.3 上移除並確認 build 不受影響。

**Files:**
- Modify: `gradle.properties:19`

- [ ] **Step 1: 移除 Jetifier 設定**

把 `gradle.properties` 中這一行刪掉：

```properties
android.enableJetifier=true
```

- [ ] **Step 2: 建置驗證（仍用現有 JDK 17 / AGP 8.3）**

Run:
```bash
./gradlew clean assembleDebug
```
Expected: `BUILD SUCCESSFUL`，且 build log 不再出現 Jetifier 相關訊息。

- [ ] **Step 3: Commit**

```bash
git add gradle.properties
git commit -m "[Build] Remove unused Jetifier flag"
```

---

## Task 3: App 語言等級升到 Java 21 + Gradle Java toolchain（現有工具鏈上）

Kotlin 1.9.23 已支援 `jvmTarget 21`、Gradle 8.4 支援以 toolchain 用 JDK 21 編譯（即使
Gradle daemon 仍跑在 JDK 17）。先把「升 Java」這件事獨立完成並驗證，再做大跳躍。

**Files:**
- Modify: `app/build.gradle:54-60`（`compileOptions` + `kotlinOptions`）

- [ ] **Step 1: 把 compileOptions 改成 Java 21，並以 jvmToolchain 取代 kotlinOptions.jvmTarget**

把 `app/build.gradle` 中這段：

```groovy
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = '17'
    }
```

改成：

```groovy
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_21
        targetCompatibility JavaVersion.VERSION_21
    }
    kotlin {
        jvmToolchain(21)
    }
```

> 註：在 AGP 8.3（Kotlin 1.9.x）下 `android { kotlin { jvmToolchain(21) } }` 寫法可用。
> 若此處 DSL 在 8.3 報錯，改用頂層 `kotlin { jvmToolchain(21) }` 並保留 `kotlinOptions`；
> 最終在 Task 4（Kotlin 2.3）後一定可用頂層 `kotlin { jvmToolchain(21) }`。

- [ ] **Step 2: 用 JDK 21 toolchain 建置驗證**

Run（讓 Gradle 能找到 JDK 21 toolchain；命令列指定 `JAVA_HOME` 為 JDK 21 最簡單）：
```bash
JAVA_HOME="<JDK21_HOME>" ./gradlew clean assembleDebug
```
Expected: `BUILD SUCCESSFUL`，且無 `jvmTarget`/`sourceCompatibility` 相關警告。

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle
git commit -m "[Build] Raise language level to Java 21 with Gradle toolchain"
```

---

## Task 4: 大跳躍 — AGP 9 / Gradle 9 / Kotlin 2.3 / KSP2 / Hilt / Room / Navigation / compose-bom

這是不可分割的核心：AGP 9 需要 Gradle 9.1+、內建 Kotlin（必須移除 `kotlin-android`）、
Compose 改用 plugin、KSP 必須是 KSP2，而 KSP2 + Kotlin 2.3 又要求 Hilt/Room 同批升級。
全部一起改，改完一次驗證。下面所有 `<XXX_VERSION>` 一律使用 Task 1 鎖定的值。

**Files:**
- Modify: `gradle/wrapper/gradle-wrapper.properties:3`
- Modify: `build.gradle`（根，整個 buildscript / plugins 區塊）
- Modify: `app/build.gradle`（plugins、composeOptions、dependencies）

- [ ] **Step 1: 升級 Gradle wrapper**

把 `gradle/wrapper/gradle-wrapper.properties` 的 `distributionUrl` 改成：

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-9.4.1-bin.zip
```
（`9.4.1` = Task 1 的 `GRADLE_VERSION`。）

- [ ] **Step 2: 改根 `build.gradle`**

把現有 `buildscript { ... }` 與 `plugins { ... }` 區塊改成下列內容（AGP 升 9.2.0；移除
獨立的 kotlin-gradle-plugin classpath，改由 AGP 內建 Kotlin；新增 Compose compiler plugin；
safe-args 與 Hilt plugin 升版；KSP 升 KSP2 版本）：

```groovy
// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:9.2.0'
        def nav_version = '2.9.8'
        classpath "androidx.navigation:navigation-safe-args-gradle-plugin:$nav_version"
        classpath 'com.google.dagger:hilt-android-gradle-plugin:2.59.2'
        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
    }
}

plugins {
    id 'com.google.devtools.ksp' version '2.3.9' apply false
    id 'org.jetbrains.kotlin.plugin.compose' version '2.3.10' apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

task clean(type: Delete) {
    delete rootProject.buildDir
}
```

> 版本字串 `9.2.0` / `2.9.8` / `2.59` / `2.3.0-2.0.2` / `2.3.0` 全部以 Task 1 鎖定值為準。
> 若要明確釘住 AGP 內建的 Kotlin 版本（而非沿用 AGP 9.2 預設），依 AGP「built-in Kotlin」
> 文件（`https://developer.android.com/build/migrate-to-built-in-kotlin`）加上版本覆寫；
> 預設情況下沿用內建版本即可，但 KSP/Compose plugin 的版本必須與其一致。

- [ ] **Step 3: 改 `app/build.gradle` 的 plugins 區塊**

把：

```groovy
plugins {
    id 'com.android.application'
    id 'kotlin-android'
    id 'androidx.navigation.safeargs.kotlin'
    id 'dagger.hilt.android.plugin'
    id 'org.jetbrains.kotlin.android'
    id 'com.google.devtools.ksp'
}
```

改成（移除 `kotlin-android` 與 `org.jetbrains.kotlin.android`；新增 compose plugin）：

```groovy
plugins {
    id 'com.android.application'
    id 'androidx.navigation.safeargs.kotlin'
    id 'dagger.hilt.android.plugin'
    id 'org.jetbrains.kotlin.plugin.compose'
    id 'com.google.devtools.ksp'
}
```

- [ ] **Step 4: 移除 `app/build.gradle` 的 composeOptions 區塊**

刪掉整段（Kotlin 2.x 改由 compose plugin 提供，保留會衝突）：

```groovy
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }
```

- [ ] **Step 5: 升級 `app/build.gradle` 的依賴版本**

把 Hilt、Room、Navigation 版本字串升到 Task 1 鎖定值，並把 compose-bom 升到 `COMPOSE_BOM`：

- `def room_version = "2.6.1"` → `def room_version = "2.8.4"`
- `'com.google.dagger:hilt-android:2.51.1'` → `'com.google.dagger:hilt-android:2.59.2'`
- `ksp 'com.google.dagger:hilt-compiler:2.51.1'` → `ksp 'com.google.dagger:hilt-compiler:2.59.2'`
- `'androidx.navigation:navigation-fragment-ktx:2.7.7'` → `...:2.9.8`
- `'androidx.navigation:navigation-ui-ktx:2.7.7'` → `...:2.9.8`
- `platform('androidx.compose:compose-bom:2024.04.00')` → `platform('androidx.compose:compose-bom:2026.06.00')`

> Room 的 `room-runtime` / `room-ktx` / `room-compiler` / `room-testing` 都吃同一個
> `room_version`，改一處即可。compose 相關依賴沿用 BOM 管控、不個別釘版本。

- [ ] **Step 6: 乾淨建置驗證（JDK 21）**

Run：
```bash
JAVA_HOME="<JDK21_HOME>" ./gradlew clean assembleDebug --stacktrace
```
Expected: `BUILD SUCCESSFUL`。
特別檢查：無「kotlin-android plugin 與 built-in Kotlin 衝突」、無「kotlinCompilerExtensionVersion
已失效」、無「KSP 版本與 KGP 不一致」、無 Hilt/Room 的 KSP2 metadata 錯誤。

> 若出現 KSP/Compose/Kotlin 版本不一致錯誤：回 Task 1 重新對齊版本字串再重跑本步。
> 若 Hilt 或 Room 報 KSP2 不相容：確認 Step 5 的版本確實已升到支援 KSP2 的版本。

- [ ] **Step 7: 手動冒煙執行**

安裝並啟動 app（模擬器或實機）：
```bash
JAVA_HOME="<JDK21_HOME>" ./gradlew installDebug
```
手動走過主要流程，確認沒有 crash：
- Diet 列表開啟
- 建立 / 選擇 Fasting Type（已轉 Compose 的畫面）
- 建立 Eat Time、Food Record（Room 寫入）
- Timer 計時畫面
- 重新進入 app，確認 Room 資料仍在（讀取）

- [ ] **Step 8: Commit**

```bash
git add gradle/wrapper/gradle-wrapper.properties build.gradle app/build.gradle
git commit -m "[Build] Upgrade to AGP 9.2 / Gradle 9.4 / Kotlin 2.3 with KSP2, Hilt, Room, Navigation"
```

---

## Task 5: compileSdk / targetSdk / build-tools 升到 36

AGP 9 預設 `targetSdk = compileSdk`；本 task 明確把兩者都設為 36 並做行為驗證。

**Files:**
- Modify: `app/build.gradle:26`（`compileSdk`）
- Modify: `app/build.gradle:33-41`（`defaultConfig` 內 `targetSdkVersion`）

- [ ] **Step 1: 升 compileSdk 與 targetSdk**

把 `app/build.gradle`：
- `compileSdk 34` → `compileSdk 36`
- `targetSdkVersion 34` → `targetSdkVersion 36`

（`minSdkVersion 24` 維持不變。）

- [ ] **Step 2: 建置驗證**

Run：
```bash
JAVA_HOME="<JDK21_HOME>" ./gradlew clean assembleDebug
```
Expected: `BUILD SUCCESSFUL`。若報缺 build-tools，安裝 `36.0.0`（SDK Manager 或
`sdkmanager "build-tools;36.0.0"`）後重跑。

- [ ] **Step 3: targetSdk 36 行為驗證（API 35+ 實機/模擬器）**

在 Android 15（API 35）或更新的裝置安裝執行，重點檢查：
- **Edge-to-edge**：status bar / navigation bar 不會遮住內容，版面正常（API 35 起強制 edge-to-edge）。
- **執行期權限**：app 內任何權限請求流程在 API 35+ 表現正常。
- 重跑 Task 4 Step 7 的主要流程冒煙，確認無 crash。

> 若出現 edge-to-edge 遮擋：在對應畫面加上 system bar insets 處理（這是 UI 修正，
> 屬本 task 範圍內的必要收尾）。

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle
git commit -m "[Build] Raise compileSdk and targetSdk to 36"
```

---

## Task 6: 收尾驗證（選擇性 release 建置）

**Files:** 無。

- [ ] **Step 1: Release 建置確認**

Run：
```bash
JAVA_HOME="<JDK21_HOME>" ./gradlew assembleRelease
```
Expected: `BUILD SUCCESSFUL`，release signing 與 proguard 設定在新工具鏈下仍能產出 APK。
（若 release keystore 不在本機，可略過並記錄為待辦。）

- [ ] **Step 2: 更新 Android Studio Gradle JDK 設定提醒**

在 Android Studio：Settings → Build, Execution, Deployment → Build Tools → Gradle，
把 Gradle JDK 設為可執行 Gradle 9.4.1 的 JDK（17+），確保 IDE 內建置與命令列一致。
（此為環境設定，不需 commit。）

---

## Self-Review（撰寫者自查結果）

- **Spec coverage：** 規格目標版本表的每一列都對應到 task —— Gradle/AGP/Kotlin/KSP/Compose
  plugin/Hilt/Navigation 在 Task 4；compileSdk/targetSdk/build-tools 在 Task 5；Java 21
  語言等級＋toolchain 在 Task 3；移除 Jetifier 在 Task 2；版本精確對齊在 Task 1；
  驗證清單（乾淨建置、冒煙、edge-to-edge、release）分散於 Task 4/5/6。
- **Placeholder scan：** 無 TODO/TBD；版本「工作預設值」由 Task 1 明確鎖定，非空泛佔位。
- **Type/名稱一致：** 各版本變數（`GRADLE_VERSION` 等）在 Task 1 定義、Task 4/5 引用，命名一致。
- **已知風險殘留：** KSP/compose-bom 的確切版本字串依官方頁面為準（Task 1 處理），這是此類
  升級無法避免、且已用獨立 task 收斂的不確定性。
