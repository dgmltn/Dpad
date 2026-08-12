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
        namespace = "com.dgmltn.dpad.design"
        compileSdk = providers.gradleProperty("dpad.compileSdk").get().toInt()
        minSdk = providers.gradleProperty("dpad.minSdk").get().toInt()
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(libs.compose.ui.tooling.preview)
        }
    }
}
