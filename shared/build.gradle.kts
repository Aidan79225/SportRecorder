plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    androidLibrary {
        namespace = "com.crazystudio.sportrecorder.shared"
        compileSdk = 36
        minSdk = 24
    }

    jvm()
    iosX64()
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
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // Room's KSP processor must run per Kotlin target.
    add("kspAndroid", libs.room.compiler)
    add("kspJvm", libs.room.compiler)
    add("kspIosX64", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}
