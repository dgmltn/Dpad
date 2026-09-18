package com.dgmltn.dpad.iosshared

import androidx.compose.ui.window.ComposeUIViewController
import com.dgmltn.dpad.data.AutoDisconnectPolicy
import com.dgmltn.dpad.data.di.dataModule
import com.dgmltn.dpad.design.DpadTheme
import com.dgmltn.dpad.ui.nav.AppNavHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import platform.UIKit.UIViewController

/**
 * Starts Koin with the iOS platform singletons + `:data`'s repository/session bindings + the
 * ViewModel bindings, wiring the same DI graph `app-android`'s `DpadApplication` wires on Android.
 * Called once from `DpadApp.swift`'s `init`, before any Compose content is created.
 */
fun startDpadKoin() {
    val koin = startKoin {
        modules(iosPlatformModule, dataModule, uiModule)
    }.koin
    val sessionScope: CoroutineScope = koin.get(named("session"))
    val policy: AutoDisconnectPolicy = koin.get()
    sessionScope.launch { policy.run() }
}

/**
 * The single Kotlin entry point Swift needs: a `UIViewController` hosting the whole Compose UI
 * (`AppNavHost`, themed by `DpadTheme`). `ContentView.swift` wraps this in a
 * `UIViewControllerRepresentable`.
 */
fun MainViewController(): UIViewController = ComposeUIViewController {
    DpadTheme {
        AppNavHost()
    }
}
