package com.lhzkml.jasmine

import android.content.Context
import android.content.SharedPreferences

/**
 * 供应商配置
 * @param id 唯一标识
 * @param name 显示名称
 * @param defaultBaseUrl 默认 API 地址
 * @param defaultModel 默认模型名
 */
data class Provider(
    val id: String,
    val name: String,
    val defaultBaseUrl: String,
    val defaultModel: String
)

object ProviderManager {

    private val _providers = mutableListOf(
        Provider("deepseek", "DeepSeek", "https://api.deepseek.com", "deepseek-chat"),
        Provider("siliconflow", "硅基流动", "https://api.siliconflow.cn", "deepseek-ai/DeepSeek-V3"),
    )

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
     * 取消注册供应商
     * @param id 供应商 ID
     * @return 是否成功移除
     */
    fun unregisterProvider(id: String): Boolean {
        return _providers.removeIf { it.id == id }
    }

    /**
     * 获取指定供应商
     */
    fun getProvider(id: String): Provider? {
        return _providers.find { it.id == id }
    }

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("jasmine_providers", Context.MODE_PRIVATE)

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
    data class ActiveConfig(val providerId: String, val baseUrl: String, val model: String, val apiKey: String)

    fun getActiveConfig(ctx: Context): ActiveConfig? {
        val id = getActiveId(ctx) ?: return null
        val key = getApiKey(ctx, id) ?: return null
        providers.find { it.id == id } ?: return null
        return ActiveConfig(
            providerId = id,
            baseUrl = getBaseUrl(ctx, id),
            model = getModel(ctx, id),
            apiKey = key
        )
    }
}
