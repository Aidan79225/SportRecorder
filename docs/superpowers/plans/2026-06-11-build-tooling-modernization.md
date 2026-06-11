# Build Tooling Modernization (KTS + Version Catalog + detekt) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert the Groovy Gradle scripts to Kotlin DSL, move all dependencies/plugins into a `gradle/libs.versions.toml` version catalog, and add detekt (core + ktlint formatting) wired into `check` with a baseline — with zero change to app behavior or dependency versions.

**Architecture:** Phase 1 is an atomic swap: create the catalog + the three `.kts` files + delete the three `.gradle` files (they're interdependent and Gradle can't have both). Phase 2 applies detekt to the app module, generates a config + baseline, and verifies new findings fail `check`.

**Tech Stack:** Gradle Kotlin DSL, Gradle version catalog (TOML), detekt + detekt-formatting (ktlint), AGP 9.2 / Kotlin 2.3.10 (unchanged).

**Verification model:** `assembleDebug` / `assembleRelease` / `testDebugUnitTest` stay green (proves the conversion is behavior-preserving, signing intact); then detekt: baseline grandfathers existing findings, a deliberate violation must fail.

**Environment (project memory):** `java` not on PATH — before every Gradle command: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` then `.\gradlew.bat <tasks>` from `C:\Users\Aidan\SportRecorder`. **Commit hygiene:** stage only named paths (never `git add -A`); commit trailer `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.

**Branch:** `feat/gradle-kts-catalog-detekt` (spec already committed here).

---

## Phase 1 — Version catalog + Kotlin DSL (atomic)

### Task 1: Catalog + convert all build scripts to `.kts`

**Files:**
- Create: `gradle/libs.versions.toml`, `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`
- Delete: `settings.gradle`, `build.gradle`, `app/build.gradle`

- [ ] **Step 1: Create `gradle/libs.versions.toml`**

```toml
[versions]
agp = "9.2.0"
kotlin = "2.3.10"
ksp = "2.3.9"
hilt = "2.59.2"
room = "2.8.4"
composeBom = "2026.05.01"
coroutines = "1.7.1"
lifecycle = "2.7.0"
activityCompose = "1.8.2"
navigationCompose = "2.9.8"
hiltNavigationCompose = "1.2.0"
serializationJson = "1.7.3"
coil = "2.7.0"
playServicesLocation = "21.3.0"
exifinterface = "1.3.7"
coreKtx = "1.12.0"
appcompat = "1.6.1"
constraintlayout = "2.1.4"
junit = "4.13.2"
androidxJunit = "1.1.5"
espresso = "3.5.1"
detekt = "1.23.8"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-appcompat = { group = "androidx.appcompat", name = "appcompat", version.ref = "appcompat" }
androidx-constraintlayout = { group = "androidx.constraintlayout", name = "constraintlayout", version.ref = "constraintlayout" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
androidx-hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-play-services = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-play-services", version.ref = "coroutines" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serializationJson" }
androidx-lifecycle-livedata-ktx = { group = "androidx.lifecycle", name = "lifecycle-livedata-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-ktx = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material3-window-size = { group = "androidx.compose.material3", name = "material3-window-size-class" }
androidx-compose-material-navigation = { group = "androidx.compose.material", name = "material-navigation" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }
play-services-location = { group = "com.google.android.gms", name = "play-services-location", version.ref = "playServicesLocation" }
androidx-exifinterface = { group = "androidx.exifinterface", name = "exifinterface", version.ref = "exifinterface" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-test-ext-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidxJunit" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espresso" }
detekt-formatting = { group = "io.gitlab.arturbosch.detekt", name = "detekt-formatting", version.ref = "detekt" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
```

- [ ] **Step 2: Create `settings.gradle.kts`** (then delete `settings.gradle`)

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SportRecorder"
include(":app")
```

- [ ] **Step 3: Create root `build.gradle.kts`** (then delete root `build.gradle`)

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

- [ ] **Step 4: Create `app/build.gradle.kts`** (then delete `app/build.gradle`)

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.crazystudio.sportrecorder"
    compileSdk = 36

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            storeFile = rootProject.file("release.keystore")
            storePassword = "aa555051"
            keyAlias = "Sport"
            keyPassword = "aa555051"
        }
    }

    defaultConfig {
        applicationId = "com.crazystudio.sportrecorder"
        minSdk = 24
        targetSdk = 36
        versionCode = 12
        versionName = "0.0.10"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    testImplementation(libs.room.testing)

    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window.size)
    implementation(libs.androidx.compose.material.navigation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.coil.compose)
    implementation(libs.play.services.location)
    implementation(libs.androidx.exifinterface)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
```

- [ ] **Step 5: Delete the old Groovy scripts**

```
git rm settings.gradle build.gradle app/build.gradle
```

- [ ] **Step 6: Build & verify behavior-preserving**

```
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat clean assembleDebug assembleRelease testDebugUnitTest
```
Expected: all `BUILD SUCCESSFUL`. If a catalog accessor is unresolved (e.g. `libs.androidx.compose.material3.window.size`), the error names the bad alias — fix the alias in the toml / call site so they match (dashes → dots). `assembleRelease` succeeding proves the signing configs converted correctly.

- [ ] **Step 7: Install smoke** — `.\gradlew.bat installDebug`; launch the app; confirm it runs (no behavior change). `adb logcat -d -t 150` no FATAL EXCEPTION.

- [ ] **Step 8: Commit**

```
git add gradle/libs.versions.toml settings.gradle.kts build.gradle.kts app/build.gradle.kts
git commit -m "[Build] Convert Gradle scripts to Kotlin DSL + version catalog"
```
(The `git rm`'d `.gradle` files are already staged for deletion and ride in this commit.)

---

## Phase 2 — detekt (core + ktlint formatting)

### Task 2: Apply detekt, generate config + baseline, wire into check

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/detekt.yml`, `app/detekt-baseline.xml`

- [ ] **Step 1: Confirm the detekt version supports Kotlin 2.3**

Check the latest detekt release (Maven Central `io.gitlab.arturbosch.detekt:detekt-gradle-plugin`, or detekt GitHub releases). The catalog pins `detekt = "1.23.8"`. If a newer version exists that explicitly supports Kotlin 2.3.x, bump the `detekt` version in `gradle/libs.versions.toml` to it. (The real test is Step 5 — if detekt can't parse Kotlin 2.3 source, bump to the newest release/RC.)

- [ ] **Step 2: Apply the detekt plugin + formatting + extension in `app/build.gradle.kts`**

Add `alias(libs.plugins.detekt)` to the `plugins { }` block (after the ksp line):
```kotlin
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
```
Add a `detekt { }` extension after the `kotlin { jvmToolchain(21) }` block:
```kotlin
detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$projectDir/detekt.yml"))
    baseline = file("$projectDir/detekt-baseline.xml")
}
```
Add the formatting ruleset to `dependencies { }` (anywhere in the block):
```kotlin
    detektPlugins(libs.detekt.formatting)
```

- [ ] **Step 3: Generate the detekt config**

```
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:detektGenerateConfig
```
This creates `app/detekt.yml`. Open it and ensure the `formatting:` section has `active: true` (the ktlint ruleset) — `detektGenerateConfig` with the formatting plugin on the classpath includes it; if `active` is `false` there, set it to `true`.

- [ ] **Step 4: Generate the baseline (grandfather existing findings)**

```
.\gradlew.bat :app:detektBaseline
```
This creates `app/detekt-baseline.xml` capturing all current findings so existing code does not fail the build.

- [ ] **Step 5: Verify detekt passes (baseline absorbs existing)**

```
.\gradlew.bat :app:detekt
```
Expected: `BUILD SUCCESSFUL` (all current findings are in the baseline). If detekt throws a **parse/analysis error on Kotlin 2.3 syntax** (not a rule finding), bump the `detekt` version (Step 1) to the newest release/RC and re-run; if a single file still can't be parsed, exclude it under `detekt { … }` via `config`/excludes and record it.

- [ ] **Step 6: Confirm `check` runs detekt and that a NEW finding fails the build**

Run `.\gradlew.bat :app:check` → it should run detekt (the plugin registers `detekt` as a `check` dependency) and pass.
Then introduce a deliberate violation to prove new issues fail: append to any existing Kotlin file (e.g. the end of `app/src/main/java/com/crazystudio/sportrecorder/util/StringUtils.kt`) a function with a magic number and bad formatting, e.g.:
```kotlin
fun detektCanary(): Int { val x=12345 ; return x }
```
Run `.\gradlew.bat :app:detekt` → expected: **BUILD FAILED** with detekt findings (e.g. `MagicNumber`, formatting). Then **revert** the canary edit (restore the file) and re-run `.\gradlew.bat :app:detekt` → `BUILD SUCCESSFUL`. Do not commit the canary.

- [ ] **Step 7: Commit**

```
git add app/build.gradle.kts app/detekt.yml app/detekt-baseline.xml gradle/libs.versions.toml
git commit -m "[Build] Add detekt (core + ktlint formatting) with baseline, wired into check"
```
(Include `gradle/libs.versions.toml` only if Step 1 bumped the detekt version.)

---

## Phase 3 — Final verification

### Task 3: Whole-build green

**Files:** none.

- [ ] **Step 1:** `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat clean assembleDebug assembleRelease testDebugUnitTest :app:check` → all `BUILD SUCCESSFUL` (build + tests + detekt all green; baseline in place, no canary left).
- [ ] **Step 2:** `git status` clean; confirm the three old `.gradle` files are gone and the `.kts` + toml + detekt files are tracked.
- [ ] **Step 3:** No commit (verification only).

---

## Self-Review (author check vs. spec)

- **Spec coverage:** catalog → Task 1 Step 1; settings/root/app KTS + repos moved + clean dropped → Task 1 Steps 2–4; delete `.gradle` → Step 5; behavior-preserving verify (incl. release signing) → Steps 6–7; detekt plugin + formatting + config + baseline + wired-to-check + new-issue-fails → Task 2; detekt/Kotlin-2.3 risk handling → Task 2 Steps 1 & 5; phased (KTS+catalog atomic, then detekt) → Phases 1/2. All spec sections map to tasks.
- **Placeholder scan:** full file contents for the catalog and all three `.kts` files; concrete detekt commands and a concrete canary snippet. The only deferred value is the exact detekt version (pinned `1.23.8`, with an explicit verify/bump step) — a real decision with a resolution path, not a vague placeholder.
- **Consistency:** every `dependencies { implementation(libs.*) }` accessor in Task 1 Step 4 maps to a `[libraries]` alias in Step 1 (dashes↔dots), and every `alias(libs.plugins.*)` maps to a `[plugins]` entry. `detekt-formatting` library + `detekt` plugin share the `detekt` version. Phase 2 adds to (not rewrites) the Phase 1 `app/build.gradle.kts`.
- **Residual note:** modern Gradle defaults `repositoriesMode` to fail on project repos — handled by putting repos only in `settings.gradle.kts` `dependencyResolutionManagement` and none in the root script.
