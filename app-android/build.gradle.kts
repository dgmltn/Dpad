plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// This is the ONLY module that isn't a Kotlin Multiplatform library: it's the Android app, so a
// plain `com.android.application` module (rather than the `com.android.kotlin.multiplatform.library`
// pattern used by :protocol/:domain/:data/:design/:ui) is correct here. AGP 9.x has built-in Kotlin
// support, so `org.jetbrains.kotlin.android` is neither needed nor allowed alongside it. The Compose
// Multiplatform plugin is still applied so this module can consume the shared `compose.*` artifacts
// (runtime/foundation/material3/ui) the same way :ui and :design do, alongside
// `org.jetbrains.kotlin.plugin.compose` for the Compose compiler itself.
android {
    namespace = providers.gradleProperty("dpad.appId").get()
    compileSdk = providers.gradleProperty("dpad.compileSdk").get().toInt()

    defaultConfig {
        applicationId = providers.gradleProperty("dpad.appId").get()
        minSdk = providers.gradleProperty("dpad.minSdk").get().toInt()
        targetSdk = providers.gradleProperty("dpad.compileSdk").get().toInt()
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    // :protocol pulls in bcpkix/bcutil/bcprov (bouncycastle) for TLS pairing; all three jars ship
    // identical META-INF license/notice files, which the resource merger otherwise rejects.
    packaging {
        resources {
            excludes += "/META-INF/{LICENSE.md,LICENSE.txt,NOTICE.md,NOTICE.txt,LICENSE,NOTICE,DEPENDENCIES}"
        }
    }
}

kotlin {
    jvmToolchain(providers.gradleProperty("dpad.jdk").get().toInt())
}

dependencies {
    implementation(project(":ui"))
    implementation(project(":design"))
    implementation(project(":domain"))
    // :app-android is the ONLY module that sees :data directly (it wires the platform singletons
    // :data's Koin module requires); :protocol is needed transitively for MdnsBrowser's Android ctor.
    implementation(project(":data"))
    implementation(project(":protocol"))

    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences.core)
    implementation(libs.okio)

    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
}
