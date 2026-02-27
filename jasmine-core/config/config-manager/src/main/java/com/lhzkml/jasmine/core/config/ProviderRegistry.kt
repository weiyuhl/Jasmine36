package com.lhzkml.jasmine.core.config

import com.lhzkml.jasmine.core.prompt.executor.ApiType

/**
 * 供应商注册表
 *
 * 管理所有已注册的供应商（内置 + 自定义），提供注册/注销/查询能力。
 * 与平台无关，持久化通过 ConfigRepository 接口完成。
 */
class ProviderRegistry(private val configRepo: ConfigRepository) {

    private val _providers = mutableListOf(
        ProviderConfig("openai", "OpenAI", "https://api.openai.com", "", ApiType.OPENAI),
        ProviderConfig("claude", "Claude", "https://api.anthropic.com", "", ApiType.CLAUDE),
        ProviderConfig("gemini", "Gemini", "https://generativelanguage.googleapis.com", "", ApiType.GEMINI),
        ProviderConfig("deepseek", "DeepSeek", "https://api.deepseek.com", "", ApiType.OPENAI),
        ProviderConfig("siliconflow", "硅基流动", "https://api.siliconflow.cn", "", ApiType.OPENAI),
    )

    private var isInitialized = false

    val providers: List<ProviderConfig>
        get() = _providers.toList()

    fun initialize() {
        if (isInitialized) return
        isInitialized = true
        val custom = configRepo.loadCustomProviders()
        custom.forEach { p ->
            if (_providers.none { it.id == p.id }) {
                _providers.add(p)
            }
        }
    }

    fun registerProvider(provider: ProviderConfig): Boolean {
        if (_providers.any { it.id == provider.id }) return false
        _providers.add(provider)
        return true
    }

    fun registerProviderPersistent(provider: ProviderConfig): Boolean {
        if (!registerProvider(provider)) return false
        saveCustomProviders()
        return true
    }

    fun unregisterProvider(id: String): Boolean {
        return _providers.removeAll { it.id == id }
    }

    fun unregisterProviderPersistent(id: String): Boolean {
        if (!unregisterProvider(id)) return false
        saveCustomProviders()
        return true
    }

    fun getProvider(id: String): ProviderConfig? {
        return _providers.find { it.id == id }
    }

    /**
     * 获取供应商的 Base URL，如果用户未配置则返回默认值
     */
    fun getBaseUrl(id: String): String {
        val saved = configRepo.getBaseUrl(id)
        if (saved.isNotEmpty()) return saved
        val provider = _providers.find { it.id == id }
        return provider?.defaultBaseUrl ?: ""
    }

    /**
     * 获取供应商的模型，如果用户未配置则返回默认值
     */
    fun getModel(id: String): String {
        val saved = configRepo.getModel(id)
        if (saved.isNotEmpty()) return saved
        val provider = _providers.find { it.id == id }
        return provider?.defaultModel ?: ""
    }

    private fun saveCustomProviders() {
        configRepo.saveCustomProviders(_providers.filter { it.isCustom })
    }

    /**
     * 获取当前激活的完整配置
     */
    fun getActiveConfig(): ActiveProviderConfig? {
        val id = configRepo.getActiveProviderId() ?: return null
        val provider = _providers.find { it.id == id } ?: return null
        val vertexEnabled = configRepo.isVertexAIEnabled(id)
        val key = if (vertexEnabled) {
            configRepo.getApiKey(id) ?: ""
        } else {
            configRepo.getApiKey(id) ?: return null
        }

        // 使用带默认值的方法获取 baseUrl 和 model
        val baseUrl = getBaseUrl(id)
        val model = getModel(id)

        return ActiveProviderConfig(
            providerId = id,
            baseUrl = baseUrl,
            model = model,
            apiKey = key,
            apiType = provider.apiType,
            chatPath = configRepo.getChatPath(id),
            vertexEnabled = vertexEnabled,
            vertexProjectId = configRepo.getVertexProjectId(id),
            vertexLocation = configRepo.getVertexLocation(id),
            vertexServiceAccountJson = configRepo.getVertexServiceAccountJson(id)
        )
    }
}
