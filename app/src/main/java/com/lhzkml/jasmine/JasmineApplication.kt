package com.lhzkml.jasmine

import android.app.Application

class JasmineApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppConfig.initialize(this)
        ProviderManager.initialize(this)
    }
}
