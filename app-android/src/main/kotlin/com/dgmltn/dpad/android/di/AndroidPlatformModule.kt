package com.dgmltn.dpad.android.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.dgmltn.dpad.data.store.createDataStore
import com.dgmltn.dpad.protocol.discovery.MdnsBrowser
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import okio.Path.Companion.toPath
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Provides the four platform singletons `:data`'s `dataModule` requires: the Preferences
 * DataStore, the Android NSD-backed [MdnsBrowser], the shared session scope, and the
 * app-in-foreground signal.
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
    // True while any activity is started. ProcessLifecycleOwner delays ON_STOP ~700ms so rotation
    // doesn't flicker it.
    single<StateFlow<Boolean>>(named("appInForeground")) {
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        lifecycle.currentStateFlow
            .map { it.isAtLeast(Lifecycle.State.STARTED) }
            .stateIn(get(named("session")), SharingStarted.Eagerly, lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
}
