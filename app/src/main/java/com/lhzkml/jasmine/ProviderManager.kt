package com.lhzkml.jasmine

import android.content.Context
import android.content.SharedPreferences

/**
 * 供应商配置
 * @param id 唯一标识
 * @param name 显示名称
 * @param isBuiltIn 是否框架内置供应商
 * @param defaultBaseUrl 默认 API 地址（内置供应商不需要填）
 * @param defaultModel 默认模型名
 * @param needsBaseUrl 是否需要用户填写 API 地址
 */
data class Provider(
    val id: String,
    val name: String,
    val isBuiltIn: Boolean,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val needsBaseUrl: Boolean = !isBuiltIn
)

object ProviderManager {

    val providers = listOf(
        Provider("deepseek", "DeepSeek", true, "https://api.deepseek.com", "deepseek-chat"),
        Provider("openai", "OpenAI", true, "https://api.openai.com", "gpt-4o"),
        Provider("google", "Google Gemini", true, "https://generativelanguage.googleapis.com", "gemini-2.5-pro"),
        Provider("anthropic", "Anthropic Claude", true, "https://api.anthropic.com", "claude-sonnet-4-20250514"),
        Provider("openrouter", "OpenRouter", true, "https://openrouter.ai", "openai/gpt-4o"),
        Provider("siliconflow", "硅基流动", false, "https://api.siliconflow.cn", "deepseek-ai/DeepSeek-V3"),
    )

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

    /** 获取当前启用的完整配置 */
    data class ActiveConfig(val providerId: String, val baseUrl: String, val model: String, val apiKey: String, val isBuiltIn: Boolean)

    fun getActiveConfig(ctx: Context): ActiveConfig? {
        val id = getActiveId(ctx) ?: return null
        val key = getApiKey(ctx, id) ?: return null
        val provider = providers.find { it.id == id } ?: return null
        return ActiveConfig(
            providerId = id,
            baseUrl = getBaseUrl(ctx, id),
            model = getModel(ctx, id),
            apiKey = key,
            isBuiltIn = provider.isBuiltIn
        )
    }
}
