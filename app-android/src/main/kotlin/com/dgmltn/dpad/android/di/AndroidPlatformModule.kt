package com.dgmltn.dpad.android.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.dgmltn.dpad.data.store.createDataStore
import com.dgmltn.dpad.protocol.discovery.MdnsBrowser
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okio.Path.Companion.toPath
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Provides the three platform singletons `:data`'s `dataModule` requires: the Preferences
 * DataStore, the Android NSD-backed [MdnsBrowser], and the shared session scope.
 *
 * The session scope is BOTH single-threaded-confined (`Dispatchers.Main.immediate`, which backs
 * RemoteSession's generation guard in `:data`) AND a `SupervisorJob` (so a rare DataStore/identity
 * failure in one child doesn't cancel the whole shared scope). Do not swap in a plain `Job()` or a
 * background dispatcher here — both properties are load-bearing.
 */
val androidPlatformModule = module {
    single<DataStore<Preferences>> {
        createDataStore(File(get<Context>().filesDir, "dpad.preferences_pb").absolutePath.toPath())
    }
    single { MdnsBrowser(get<Context>()) }
    single<CoroutineScope>(named("session")) { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
}
