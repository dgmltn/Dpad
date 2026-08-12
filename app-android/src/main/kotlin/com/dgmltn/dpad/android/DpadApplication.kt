package com.dgmltn.dpad.android

import android.app.Application
import com.dgmltn.dpad.android.di.androidPlatformModule
import com.dgmltn.dpad.android.di.uiModule
import com.dgmltn.dpad.data.di.dataModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class DpadApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@DpadApplication)
            modules(androidPlatformModule, dataModule, uiModule)
        }
    }
}
