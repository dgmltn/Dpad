plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

// See :protocol's build.gradle.kts for why the KMP-native `com.android.kotlin.multiplatform.library`
// plugin + `kotlin { android { ... } }` block is used instead of `androidLibrary { ... }`/top-level
// `android {}` under AGP 9.x.
kotlin {
    jvmToolchain(providers.gradleProperty("dpad.jdk").get().toInt())
    jvm()
    android {
        namespace = "com.dgmltn.dpad.data"
        compileSdk = providers.gradleProperty("dpad.compileSdk").get().toInt()
        minSdk = providers.gradleProperty("dpad.minSdk").get().toInt()
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":domain"))
            implementation(project(":protocol"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.androidx.datastore.preferences.core)
            implementation(libs.okio)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.core)
            implementation(libs.kermit)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            implementation(libs.koin.test)
            implementation(libs.okio.fakefilesystem)
        }
    }
}
