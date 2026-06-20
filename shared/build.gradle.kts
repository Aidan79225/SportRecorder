plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
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
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
