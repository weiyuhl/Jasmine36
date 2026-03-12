package com.lhzkml.jasmine.repository

import com.lhzkml.jasmine.core.config.ActiveProviderConfig
import com.lhzkml.jasmine.core.config.ConfigRepository
import com.lhzkml.jasmine.core.config.ProviderConfig
import com.lhzkml.jasmine.core.config.ProviderRegistry

/**
 * Provider Repository
 *
 * 负责：
 * - 获取所有 provider
 * - 当前激活 provider
 * - 保存 provider 配置
 * - API key / baseUrl / model / chatPath
 * - 自定义 provider 的增删改
 * - Vertex AI 相关配置
 *
 * 对应页面/功能：
 * - ProviderListActivity
 * - ProviderConfigActivity
 * - AddCustomProviderActivity
 * - SettingsActivity 中 Provider 摘要
 */
interface ProviderRepository {
    
    // Provider 列表
    fun getAllProviders(): List<ProviderConfig>
    fun getProvider(id: String): ProviderConfig?
    
    // 激活 Provider
    fun getActiveProviderId(): String?
    fun setActiveProviderId(id: String)
    fun getActiveConfig(): ActiveProviderConfig?
    
    // Provider 凭证
    fun getApiKey(providerId: String): String?
    fun getBaseUrl(providerId: String): String
    fun getModel(providerId: String): String
    fun saveProviderCredentials(providerId: String, apiKey: String, baseUrl: String?, model: String?)
    
    // Selected Models
    fun getSelectedModels(providerId: String): List<String>
    fun setSelectedModels(providerId: String, models: List<String>)
    
    // Chat Path
    fun getChatPath(providerId: String): String?
    fun saveChatPath(providerId: String, path: String)
    
    // 自定义 Provider
    fun registerProvider(provider: ProviderConfig): Boolean
    fun unregisterProvider(id: String): Boolean
    
    // Vertex AI
    fun isVertexAIEnabled(providerId: String): Boolean
    fun setVertexAIEnabled(providerId: String, enabled: Boolean)
    fun getVertexProjectId(providerId: String): String
    fun setVertexProjectId(providerId: String, projectId: String)
    fun getVertexLocation(providerId: String): String
    fun setVertexLocation(providerId: String, location: String)
    fun getVertexServiceAccountJson(providerId: String): String
    fun setVertexServiceAccountJson(providerId: String, json: String)
}

class DefaultProviderRepository(
    private val configRepo: ConfigRepository,
    private val providerRegistry: ProviderRegistry
) : ProviderRepository {
    
    override fun getAllProviders(): List<ProviderConfig> = providerRegistry.providers
    
    override fun getProvider(id: String): ProviderConfig? = providerRegistry.getProvider(id)
    
    override fun getActiveProviderId(): String? = configRepo.getActiveProviderId()
    
    override fun setActiveProviderId(id: String) {
        configRepo.setActiveProviderId(id)
    }
    
    override fun getActiveConfig(): ActiveProviderConfig? = providerRegistry.getActiveConfig()
    
    override fun getApiKey(providerId: String): String? = configRepo.getApiKey(providerId)
    
    override fun getBaseUrl(providerId: String): String = configRepo.getBaseUrl(providerId)
    
    override fun getModel(providerId: String): String = providerRegistry.getModel(providerId)
    
    override fun saveProviderCredentials(
        providerId: String,
        apiKey: String,
        baseUrl: String?,
        model: String?
    ) {
        configRepo.saveProviderCredentials(providerId, apiKey, baseUrl, model)
    }
    
    override fun getSelectedModels(providerId: String): List<String> = 
        configRepo.getSelectedModels(providerId)
    
    override fun setSelectedModels(providerId: String, models: List<String>) {
        configRepo.setSelectedModels(providerId, models)
    }
    
    override fun getChatPath(providerId: String): String? = configRepo.getChatPath(providerId)
    
    override fun saveChatPath(providerId: String, path: String) {
        configRepo.saveChatPath(providerId, path)
    }
    
    override fun registerProvider(provider: ProviderConfig): Boolean = 
        providerRegistry.registerProviderPersistent(provider)
    
    override fun unregisterProvider(id: String): Boolean = 
        providerRegistry.unregisterProviderPersistent(id)
    
    override fun isVertexAIEnabled(providerId: String): Boolean = 
        configRepo.isVertexAIEnabled(providerId)
    
    override fun setVertexAIEnabled(providerId: String, enabled: Boolean) {
        configRepo.setVertexAIEnabled(providerId, enabled)
    }
    
    override fun getVertexProjectId(providerId: String): String = 
        configRepo.getVertexProjectId(providerId)
    
    override fun setVertexProjectId(providerId: String, projectId: String) {
        configRepo.setVertexProjectId(providerId, projectId)
    }
    
    override fun getVertexLocation(providerId: String): String = 
        configRepo.getVertexLocation(providerId)
    
    override fun setVertexLocation(providerId: String, location: String) {
        configRepo.setVertexLocation(providerId, location)
    }
    
    override fun getVertexServiceAccountJson(providerId: String): String = 
        configRepo.getVertexServiceAccountJson(providerId)
    
    override fun setVertexServiceAccountJson(providerId: String, json: String) {
        configRepo.setVertexServiceAccountJson(providerId, json)
    }
}
