plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidLibrary {
        namespace = "com.crazystudio.sportrecorder.shared"
        compileSdk = 36
        minSdk = 24
        // Required so Compose Multiplatform resources (.cvr) are packaged into Android assets
        // with the com.android.kotlin.multiplatform.library plugin (CMP-9547). Without this the
        // app crashes at runtime with MissingResourceException.
        androidResources.enable = true
    }

    jvm()
    // iosX64 (Intel simulator) dropped: Compose Multiplatform no longer publishes it, and both
    // our CI simulator and modern Macs are Apple Silicon. Device = iosArm64, simulator = arm64.
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // api: the repository interfaces expose Flow, and calculators expose TimeZone.
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
            // api: DataStore-backed repos expose DataStore<Preferences> in their constructors.
            api(libs.androidx.datastore.preferences.core)
            // Room (multiplatform) + the bundled SQLite driver used by JVM/iOS.
            implementation(libs.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
            // Compose Multiplatform — shared UI rendered on Android + iOS.
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            // Multiplatform ViewModel + SavedStateHandle (JetBrains lifecycle) for the shared VMs.
            // api: :app references the VM types (which extend androidx.lifecycle.ViewModel).
            api(libs.jetbrains.lifecycle.viewmodel)
            api(libs.jetbrains.lifecycle.viewmodel.savedstate)
            // Generated Res class + stringResource() for shared string/image resources.
            // api: shared UI state (e.g. DietUiState) exposes DrawableResource/StringResource,
            // so :app needs these types on its compile classpath too.
            api(compose.components.resources)
            // Coil 3 (multiplatform) — AsyncImage for shared photo UI.
            implementation(libs.coil3.compose)
            // kotlinx.serialization for backup DTOs.
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

compose.resources {
    // Generated accessors live at com.crazystudio.sportrecorder.shared.resources.Res
    publicResClass = true
    packageOfResClass = "com.crazystudio.sportrecorder.shared.resources"
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // Room's KSP processor must run per Kotlin target.
    add("kspAndroid", libs.room.compiler)
    add("kspJvm", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}
