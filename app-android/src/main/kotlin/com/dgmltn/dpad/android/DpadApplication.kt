package com.dgmltn.dpad.android

import android.app.Application
import com.dgmltn.dpad.android.connection.ConnectionServiceStarter
import com.dgmltn.dpad.android.connection.createConnectionChannel
import com.dgmltn.dpad.android.di.androidPlatformModule
import com.dgmltn.dpad.android.di.uiModule
import com.dgmltn.dpad.data.AutoDisconnectPolicy
import com.dgmltn.dpad.data.di.dataModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named

class DpadApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@DpadApplication)
            modules(androidPlatformModule, dataModule, uiModule)
        }
        val sessionScope: CoroutineScope = get(named("session"))
        val policy: AutoDisconnectPolicy = get()
        sessionScope.launch { policy.run() }
        createConnectionChannel(this)
        val starter = ConnectionServiceStarter(this, get(), get(named("appInForeground")))
        sessionScope.launch { starter.run() }
    }
}
