package com.lhzkml.jasmine

import android.content.Context
import com.lhzkml.jasmine.core.config.ConfigRepository
import com.lhzkml.jasmine.core.config.ProviderRegistry

/**
 * 应用配置管理器
 * 
 * 提供全局的 ConfigRepository 和 ProviderRegistry 实例。
 */
object AppConfig {
    
    private var _configRepo: ConfigRepository? = null
    private var _providerRegistry: ProviderRegistry? = null
    
    fun initialize(context: Context) {
        if (_configRepo == null) {
            _configRepo = SharedPreferencesConfigRepository(context.applicationContext)
        }
        if (_providerRegistry == null) {
            _providerRegistry = ProviderRegistry(configRepo()).apply {
                initialize()
            }
        }
    }
    
    fun configRepo(): ConfigRepository {
        return _configRepo ?: throw IllegalStateException("AppConfig not initialized. Call initialize() first.")
    }
    
    fun providerRegistry(): ProviderRegistry {
        return _providerRegistry ?: throw IllegalStateException("AppConfig not initialized. Call initialize() first.")
    }
}
