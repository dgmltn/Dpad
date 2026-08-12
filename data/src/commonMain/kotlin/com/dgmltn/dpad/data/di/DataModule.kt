package com.dgmltn.dpad.data.di

import com.dgmltn.dpad.data.*
import com.dgmltn.dpad.domain.*
import kotlinx.coroutines.CoroutineScope
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Wires :data implementations to :domain contracts. REQUIRES the platform module (Plan 3) to provide:
 *   single<DataStore<Preferences>> { ... platform path ... }
 *   single<MdnsBrowser> { ... Context on Android ... }
 *   single<CoroutineScope>(named("session")) { ... single-threaded-confined ... }
 */
val dataModule = module {
    single<DeviceRepository> { DeviceRepositoryImpl(get()) }
    single<ShortcutRepository> { ShortcutRepositoryImpl(get()) }
    single<ClientIdentityStore> { ClientIdentityStoreImpl(get()) }
    single<DeviceDiscovery> { DeviceDiscoveryImpl(get()) }
    factory<DevicePairer> { DevicePairerImpl(get(), get(), get(named("session"))) }
    single<RemoteController> { RemoteControllerImpl(get(), get(), get(named("session"))) }
}
