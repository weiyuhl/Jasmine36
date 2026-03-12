package com.lhzkml.jasmine

import android.app.Application
import com.lhzkml.jasmine.config.AppConfig
import com.lhzkml.jasmine.di.appModule
import com.lhzkml.jasmine.di.repositoryModule
import com.lhzkml.jasmine.di.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class JasmineApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppConfig.initialize(this)
        // 注意：ProviderManager 已被 Repository 替代，无需在此初始化
        RagStore.initialize(this)
        startKoin {
            androidContext(this@JasmineApplication)
            modules(appModule, repositoryModule, viewModelModule)
        }
    }
}
