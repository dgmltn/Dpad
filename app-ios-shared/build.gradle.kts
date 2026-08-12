plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// :app-ios-shared is the iOS analog of :app-android: the app-layer composition root that is
// allowed to see both :ui and :data (unlike :ui itself, which must not depend on :data). It has
// NO jvm()/android() targets — it exists solely to be linked as a static framework consumed by
// the Xcode project in app-ios/. Compose Multiplatform is applied so this module can host
// ComposeUIViewController (from compose.ui's iOS-specific `androidx.compose.ui.window` APIs) and
// render `:design`'s DpadTheme + `:ui`'s AppNavHost.
kotlin {
    jvmToolchain(providers.gradleProperty("dpad.jdk").get().toInt())

    val iosTargets = listOf(iosArm64(), iosSimulatorArm64())
    iosTargets.forEach { target ->
        target.binaries.framework {
            baseName = "DpadShared"
            isStatic = true
        }
    }

    sourceSets {
        iosMain.dependencies {
            implementation(project(":ui"))
            implementation(project(":design"))
            implementation(project(":domain"))
            // :app-ios-shared is the ONLY iOS module that sees :data directly (it wires the
            // platform singletons :data's Koin module requires); :protocol is needed directly for
            // MdnsBrowser's no-arg iOS constructor.
            implementation(project(":data"))
            implementation(project(":protocol"))

            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.androidx.datastore.preferences.core)
            implementation(libs.okio)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
        }
    }
}
