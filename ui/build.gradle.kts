plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// See :protocol's build.gradle.kts for why the KMP-native `com.android.kotlin.multiplatform.library`
// plugin + `kotlin { android { ... } }` block is used instead of `androidLibrary { ... }`/top-level
// `android {}` under AGP 9.x.
kotlin {
    jvmToolchain(providers.gradleProperty("dpad.jdk").get().toInt())
    jvm()
    android {
        namespace = "com.dgmltn.dpad.ui"
        compileSdk = providers.gradleProperty("dpad.compileSdk").get().toInt()
        minSdk = providers.gradleProperty("dpad.minSdk").get().toInt()
        // Enables the `androidHostTest` source set so ViewModel tests run on the JVM (Robolectric-free).
        withHostTest {}
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":design"))
            api(project(":domain"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.navigation3.runtime)
            implementation(libs.navigation3.ui)
            implementation(libs.kotlinx.collections.immutable)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
