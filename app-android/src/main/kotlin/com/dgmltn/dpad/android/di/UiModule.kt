package com.dgmltn.dpad.android.di

import com.dgmltn.dpad.ui.devices.DevicesViewModel
import com.dgmltn.dpad.ui.remote.RemoteViewModel
import com.dgmltn.dpad.ui.shortcuts.ShortcutsViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.dsl.module

/** Binds the three `:ui` ViewModels via koin-compose-viewmodel's `viewModel { }` DSL. */
val uiModule = module {
    viewModel { RemoteViewModel(get(), get(), get()) }
    viewModel { DevicesViewModel(get(), get(), get(), get()) }
    viewModel { ShortcutsViewModel(get()) }
}
