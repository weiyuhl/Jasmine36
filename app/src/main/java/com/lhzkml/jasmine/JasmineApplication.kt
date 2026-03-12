package com.lhzkml.jasmine

import android.app.Application
import com.lhzkml.jasmine.config.AppConfig
import com.lhzkml.jasmine.config.ProviderManager
import com.lhzkml.jasmine.di.appModule
import com.lhzkml.jasmine.di.repositoryModule
import com.lhzkml.jasmine.di.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class JasmineApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppConfig.initialize(this)
        ProviderManager.initialize(this)
        RagStore.initialize(this)
        startKoin {
            androidContext(this@JasmineApplication)
            modules(appModule, repositoryModule, viewModelModule)
        }
    }
}
