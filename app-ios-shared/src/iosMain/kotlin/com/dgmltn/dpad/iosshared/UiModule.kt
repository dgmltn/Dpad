package com.dgmltn.dpad.iosshared

import com.dgmltn.dpad.ui.devices.DevicesViewModel
import com.dgmltn.dpad.ui.remote.RemoteViewModel
import com.dgmltn.dpad.ui.shortcuts.ShortcutsViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Binds the three `:ui` ViewModels via koin-compose-viewmodel's `viewModel { }` DSL — the iOS
 * counterpart of `app-android`'s `UiModule.kt`. `:ui` cannot own this binding itself because it
 * must not depend on `:data`'s ViewModel constructor arguments transitively pulling in a specific
 * platform DI graph; each app-layer composition root (here, `:app-ios-shared`) defines its own.
 */
val uiModule = module {
    viewModel { RemoteViewModel(get(), get(), get()) }
    viewModel { DevicesViewModel(get(), get(), get(), get()) }
    viewModel { ShortcutsViewModel(get()) }
}
