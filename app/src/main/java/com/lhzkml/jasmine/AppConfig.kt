package com.lhzkml.jasmine

import android.content.Context
import com.lhzkml.jasmine.core.agent.runtime.CheckpointService
import com.lhzkml.jasmine.core.agent.runtime.FileCheckpointService
import com.lhzkml.jasmine.core.agent.runtime.McpConnectionManager
import com.lhzkml.jasmine.core.config.ConfigRepository
import com.lhzkml.jasmine.core.config.ProviderRegistry

/**
 * 应用配置管理器
 * 
 * 提供全局的 ConfigRepository、ProviderRegistry、McpConnectionManager、CheckpointService 实例。
 */
object AppConfig {
    
    private var _configRepo: ConfigRepository? = null
    private var _providerRegistry: ProviderRegistry? = null
    private var _mcpConnectionManager: McpConnectionManager? = null
    private var _checkpointService: CheckpointService? = null
    
    fun initialize(context: Context) {
        if (_configRepo == null) {
            _configRepo = SharedPreferencesConfigRepository(context.applicationContext)
        }
        if (_providerRegistry == null) {
            _providerRegistry = ProviderRegistry(configRepo()).apply {
                initialize()
            }
        }
        if (_mcpConnectionManager == null) {
            _mcpConnectionManager = McpConnectionManager(configRepo())
        }
        if (_checkpointService == null) {
            val snapshotDir = context.applicationContext.getExternalFilesDir("snapshots")
            if (snapshotDir != null) {
                _checkpointService = FileCheckpointService(snapshotDir)
            }
        }
    }
    
    fun configRepo(): ConfigRepository {
        return _configRepo ?: throw IllegalStateException("AppConfig not initialized. Call initialize() first.")
    }
    
    fun providerRegistry(): ProviderRegistry {
        return _providerRegistry ?: throw IllegalStateException("AppConfig not initialized. Call initialize() first.")
    }

    fun mcpConnectionManager(): McpConnectionManager {
        return _mcpConnectionManager ?: throw IllegalStateException("AppConfig not initialized. Call initialize() first.")
    }

    fun checkpointService(): CheckpointService? {
        return _checkpointService
    }
}
