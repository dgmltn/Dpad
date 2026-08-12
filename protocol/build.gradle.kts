plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.wire)
}

// AGP 9.x rejects the classic `com.android.library` + `androidTarget()` combination inside a KMP
// module ("not compatible with the 'org.jetbrains.kotlin.multiplatform' plugin since AGP 9.0").
// Using the KMP-native `com.android.kotlin.multiplatform.library` plugin instead; the Android
// target is configured via `kotlin { android { ... } }` below rather than a top-level `android {}`
// block. Targets and behavior are otherwise identical to the brief.
kotlin {
    applyDefaultHierarchyTemplate()
    jvmToolchain(providers.gradleProperty("dpad.jdk").get().toInt())
    jvm()
    android {
        namespace = "com.dgmltn.dpad.protocol"
        compileSdk = providers.gradleProperty("dpad.compileSdk").get().toInt()
        minSdk = providers.gradleProperty("dpad.minSdk").get().toInt()
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(libs.wire.runtime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kermit)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
        // JVM and Android share actuals via this intermediate source set
        val jvmSharedMain by creating { dependsOn(commonMain.get()) }
        jvmMain.get().dependsOn(jvmSharedMain)
        androidMain.get().dependsOn(jvmSharedMain)
        jvmSharedMain.dependencies {
            implementation(libs.bouncycastle.bcpkix)
        }
    }
}

wire {
    kotlin {}
    sourcePath { srcDir("src/commonMain/proto") }
}
