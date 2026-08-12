plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
}

kotlin {
    jvmToolchain(providers.gradleProperty("dpad.jdk").get().toInt())
    jvm()
    android {
        namespace = "com.dgmltn.dpad.domain"
        compileSdk = providers.gradleProperty("dpad.compileSdk").get().toInt()
        minSdk = providers.gradleProperty("dpad.minSdk").get().toInt()
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
