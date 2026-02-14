package com.lhzkml.jasmine

import android.content.Context
import android.content.SharedPreferences

/**
 * API 渠道类型
 */
enum class ApiType {
    /** OpenAI 兼容格式（DeepSeek、硅基流动等） */
    OPENAI,
    /** Anthropic Claude 原生 Messages API */
    CLAUDE,
    /** Google Gemini 原生 generateContent API */
    GEMINI
}

/**
 * 供应商配置
 * @param id 唯一标识
 * @param name 显示名称
 * @param defaultBaseUrl 默认 API 地址
 * @param defaultModel 默认模型名
 * @param apiType API 渠道类型
 * @param isCustom 是否为自定义供应商（可删除）
 */
data class Provider(
    val id: String,
    val name: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val apiType: ApiType = ApiType.OPENAI,
    val isCustom: Boolean = false
)

object ProviderManager {

    private val _providers = mutableListOf(
        // 三大渠道供应商
        Provider("openai", "OpenAI", "https://api.openai.com", "gpt-4o", ApiType.OPENAI),
        Provider("claude", "Claude", "https://api.anthropic.com", "claude-sonnet-4-20250514", ApiType.CLAUDE),
        Provider("gemini", "Gemini", "https://generativelanguage.googleapis.com", "gemini-2.5-flash", ApiType.GEMINI),
        // 其他供应商
        Provider("deepseek", "DeepSeek", "https://api.deepseek.com", "deepseek-chat", ApiType.OPENAI),
        Provider("siliconflow", "硅基流动", "https://api.siliconflow.cn", "deepseek-ai/DeepSeek-V3", ApiType.OPENAI),
    )

    private var isInitialized = false

    /** 获取所有已注册的供应商（只读） */
    val providers: List<Provider>
        get() = _providers.toList()

    /**
     * 注册新供应商
     * @param provider 供应商配置
     * @return 是否注册成功（如果 ID 已存在则返回 false）
     */
    fun registerProvider(provider: Provider): Boolean {
        if (_providers.any { it.id == provider.id }) {
            return false
        }
        _providers.add(provider)
        return true
    }

    /**
     * 注册新供应商并持久化
     * @param ctx 上下文
     * @param provider 供应商配置
     * @return 是否注册成功（如果 ID 已存在则返回 false）
     */
    fun registerProviderPersistent(ctx: Context, provider: Provider): Boolean {
        if (!registerProvider(provider)) {
            return false
        }
        saveCustomProviders(ctx)
        return true
    }

    /**
     * 取消注册供应商
     * @param id 供应商 ID
     * @return 是否成功移除
     */
    fun unregisterProvider(id: String): Boolean {
        return _providers.removeIf { it.id == id }
    }

    /**
     * 取消注册供应商并持久化
     * @param ctx 上下文
     * @param id 供应商 ID
     * @return 是否成功移除
     */
    fun unregisterProviderPersistent(ctx: Context, id: String): Boolean {
        if (!unregisterProvider(id)) {
            return false
        }
        saveCustomProviders(ctx)
        return true
    }

    /**
     * 获取指定供应商
     */
    fun getProvider(id: String): Provider? {
        return _providers.find { it.id == id }
    }

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("jasmine_providers", Context.MODE_PRIVATE)

    /**
     * 初始化：从持久化存储加载自定义供应商
     */
    fun initialize(ctx: Context) {
        if (isInitialized) return
        isInitialized = true
        loadCustomProviders(ctx)
    }

    /**
     * 保存自定义供应商到持久化存储
     */
    private fun saveCustomProviders(ctx: Context) {
        val customProviders = _providers.filter { it.isCustom }
        val json = customProviders.joinToString("|") { provider ->
            "${provider.id}::${provider.name}::${provider.defaultBaseUrl}::${provider.defaultModel}::${provider.apiType.name}"
        }
        prefs(ctx).edit().putString("custom_providers", json).apply()
    }

    /**
     * 从持久化存储加载自定义供应商
     */
    private fun loadCustomProviders(ctx: Context) {
        val json = prefs(ctx).getString("custom_providers", null) ?: return
        if (json.isEmpty()) return
        
        val customProviders = json.split("|").mapNotNull { entry ->
            val parts = entry.split("::")
            if (parts.size >= 4) {
                val apiType = if (parts.size >= 5) {
                    try { ApiType.valueOf(parts[4]) } catch (_: Exception) { ApiType.OPENAI }
                } else ApiType.OPENAI
                Provider(
                    id = parts[0],
                    name = parts[1],
                    defaultBaseUrl = parts[2],
                    defaultModel = parts[3],
                    apiType = apiType,
                    isCustom = true
                )
            } else null
        }
        
        customProviders.forEach { provider ->
            if (_providers.none { it.id == provider.id }) {
                _providers.add(provider)
            }
        }
    }

    /** 获取当前启用的供应商 ID */
    fun getActiveId(ctx: Context): String? = prefs(ctx).getString("active_provider", null)

    /** 设置启用的供应商 */
    fun setActive(ctx: Context, id: String) {
        prefs(ctx).edit().putString("active_provider", id).apply()
    }

    /** 获取某个供应商的 API Key */
    fun getApiKey(ctx: Context, id: String): String? {
        val key = prefs(ctx).getString("${id}_api_key", null)
        return if (key.isNullOrBlank()) null else key
    }

    /** 保存供应商配置 */
    fun saveConfig(ctx: Context, id: String, apiKey: String, baseUrl: String? = null, model: String? = null) {
        prefs(ctx).edit().apply {
            putString("${id}_api_key", apiKey)
            if (baseUrl != null) putString("${id}_base_url", baseUrl)
            if (model != null) putString("${id}_model", model)
            apply()
        }
    }

    /** 获取供应商的 base URL */
    fun getBaseUrl(ctx: Context, id: String): String {
        val provider = providers.find { it.id == id }
        return prefs(ctx).getString("${id}_base_url", null) ?: provider?.defaultBaseUrl ?: ""
    }

    /** 获取供应商的模型名 */
    fun getModel(ctx: Context, id: String): String {
        val provider = providers.find { it.id == id }
        return prefs(ctx).getString("${id}_model", null) ?: provider?.defaultModel ?: ""
    }

    /** 获取供应商的 API 路径 */
    fun getChatPath(ctx: Context, id: String): String? =
        prefs(ctx).getString("${id}_chat_path", null)

    /** 保存供应商的 API 路径 */
    fun saveChatPath(ctx: Context, id: String, path: String) {
        prefs(ctx).edit().putString("${id}_chat_path", path).apply()
    }

    // ========== Vertex AI 配置 ==========

    /** 是否启用 Vertex AI（仅 Gemini 类型供应商） */
    fun isVertexAIEnabled(ctx: Context, id: String): Boolean =
        prefs(ctx).getBoolean("${id}_vertex_enabled", false)

    fun setVertexAIEnabled(ctx: Context, id: String, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("${id}_vertex_enabled", enabled).apply()
    }

    fun getVertexProjectId(ctx: Context, id: String): String =
        prefs(ctx).getString("${id}_vertex_project_id", null) ?: ""

    fun setVertexProjectId(ctx: Context, id: String, projectId: String) {
        prefs(ctx).edit().putString("${id}_vertex_project_id", projectId).apply()
    }

    fun getVertexLocation(ctx: Context, id: String): String =
        prefs(ctx).getString("${id}_vertex_location", null) ?: "global"

    fun setVertexLocation(ctx: Context, id: String, location: String) {
        prefs(ctx).edit().putString("${id}_vertex_location", location).apply()
    }

    fun getVertexServiceAccountJson(ctx: Context, id: String): String =
        prefs(ctx).getString("${id}_vertex_sa_json", null) ?: ""

    fun setVertexServiceAccountJson(ctx: Context, id: String, json: String) {
        prefs(ctx).edit().putString("${id}_vertex_sa_json", json).apply()
    }

    /** 是否启用流式输出 */
    fun isStreamEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("stream_enabled", true)

    /** 设置流式输出开关 */
    fun setStreamEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("stream_enabled", enabled).apply()
    }

    /** 获取默认系统提示词 */
    fun getDefaultSystemPrompt(ctx: Context): String =
        prefs(ctx).getString("default_system_prompt", null) ?: "You are a helpful assistant."

    /** 设置默认系统提示词 */
    fun setDefaultSystemPrompt(ctx: Context, prompt: String) {
        prefs(ctx).edit().putString("default_system_prompt", prompt).apply()
    }

    /** 获取最大回复 token 数，0 表示不限制 */
    fun getMaxTokens(ctx: Context): Int =
        prefs(ctx).getInt("max_tokens", 0)

    /** 设置最大回复 token 数，0 表示不限制 */
    fun setMaxTokens(ctx: Context, maxTokens: Int) {
        prefs(ctx).edit().putInt("max_tokens", maxTokens).apply()
    }

    /** 获取当前启用的完整配置 */
    data class ActiveConfig(
        val providerId: String,
        val baseUrl: String,
        val model: String,
        val apiKey: String,
        val apiType: ApiType,
        val chatPath: String? = null,
        val vertexEnabled: Boolean = false,
        val vertexProjectId: String = "",
        val vertexLocation: String = "global",
        val vertexServiceAccountJson: String = ""
    )

    fun getActiveConfig(ctx: Context): ActiveConfig? {
        val id = getActiveId(ctx) ?: return null
        val provider = providers.find { it.id == id } ?: return null
        val vertexEnabled = isVertexAIEnabled(ctx, id)

        // Vertex AI 模式不需要 API Key
        val key = if (vertexEnabled) {
            getApiKey(ctx, id) ?: ""
        } else {
            getApiKey(ctx, id) ?: return null
        }

        return ActiveConfig(
            providerId = id,
            baseUrl = getBaseUrl(ctx, id),
            model = getModel(ctx, id),
            apiKey = key,
            apiType = provider.apiType,
            chatPath = getChatPath(ctx, id),
            vertexEnabled = vertexEnabled,
            vertexProjectId = getVertexProjectId(ctx, id),
            vertexLocation = getVertexLocation(ctx, id),
            vertexServiceAccountJson = getVertexServiceAccountJson(ctx, id)
        )
    }
}
