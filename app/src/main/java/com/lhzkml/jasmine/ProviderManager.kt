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

    // ========== 采样参数 ==========

    /** 获取 temperature，-1 表示使用默认值 */
    fun getTemperature(ctx: Context): Float =
        prefs(ctx).getFloat("sampling_temperature", -1f)

    fun setTemperature(ctx: Context, value: Float) {
        prefs(ctx).edit().putFloat("sampling_temperature", value).apply()
    }

    /** 获取 top_p，-1 表示使用默认值 */
    fun getTopP(ctx: Context): Float =
        prefs(ctx).getFloat("sampling_top_p", -1f)

    fun setTopP(ctx: Context, value: Float) {
        prefs(ctx).edit().putFloat("sampling_top_p", value).apply()
    }

    /** 获取 top_k，-1 表示使用默认值（仅 Claude/Gemini 支持） */
    fun getTopK(ctx: Context): Int =
        prefs(ctx).getInt("sampling_top_k", -1)

    fun setTopK(ctx: Context, value: Int) {
        prefs(ctx).edit().putInt("sampling_top_k", value).apply()
    }

    // ========== 工具设置 ==========

    /** 是否启用工具调用（Agent 模式） */
    fun isToolsEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("tools_enabled", false)

    fun setToolsEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("tools_enabled", enabled).apply()
    }

    /** 获取已启用的工具集合，空集合表示全部启用 */
    fun getEnabledTools(ctx: Context): Set<String> {
        val raw = prefs(ctx).getString("enabled_tools", null) ?: return emptySet()
        return raw.split(",").filter { it.isNotBlank() }.toSet()
    }

    fun setEnabledTools(ctx: Context, tools: Set<String>) {
        prefs(ctx).edit().putString("enabled_tools", tools.joinToString(",")).apply()
    }

    /** BrightData API Key（用于网络搜索工具） */
    fun getBrightDataKey(ctx: Context): String =
        prefs(ctx).getString("brightdata_api_key", null) ?: ""

    fun setBrightDataKey(ctx: Context, key: String) {
        prefs(ctx).edit().putString("brightdata_api_key", key).apply()
    }

    // ========== MCP 服务器设置 ==========

    /** 是否启用 MCP 工具 */
    fun isMcpEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("mcp_enabled", false)

    fun setMcpEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("mcp_enabled", enabled).apply()
    }

    /** MCP 传输类型 */
    enum class McpTransportType {
        STREAMABLE_HTTP,
        SSE
    }

    /**
     * MCP 服务器配置
     * @param name 显示名称
     * @param url 服务器 URL
     * @param transportType 传输类型（Streamable HTTP 或 SSE）
     * @param headerName 请求头名称（如 Authorization）
     * @param headerValue 请求头值（如 Bearer xxx）
     * @param enabled 是否启用
     */
    data class McpServerConfig(
        val name: String,
        val url: String,
        val transportType: McpTransportType = McpTransportType.STREAMABLE_HTTP,
        val headerName: String = "",
        val headerValue: String = "",
        val enabled: Boolean = true
    )

    /**
     * 获取所有 MCP 服务器配置
     * v2 格式：name:::url:::transportType:::headerName:::headerValue:::enabled
     */
    fun getMcpServers(ctx: Context): List<McpServerConfig> {
        val raw = prefs(ctx).getString("mcp_servers_v2", null)
        if (raw != null) {
            return try {
                raw.split("|||").filter { it.isNotBlank() }.map { entry ->
                    val parts = entry.split(":::")
                    McpServerConfig(
                        name = parts.getOrElse(0) { "" },
                        url = parts.getOrElse(1) { "" },
                        transportType = try {
                            McpTransportType.valueOf(parts.getOrElse(2) { "STREAMABLE_HTTP" })
                        } catch (_: Exception) { McpTransportType.STREAMABLE_HTTP },
                        headerName = parts.getOrElse(3) { "" },
                        headerValue = parts.getOrElse(4) { "" },
                        enabled = parts.getOrElse(5) { "true" }.toBooleanStrictOrNull() ?: true
                    )
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

        // 兼容旧版 v1 格式迁移
        val oldRaw = prefs(ctx).getString("mcp_servers", null) ?: return emptyList()
        return try {
            val migrated = oldRaw.split("|||").filter { it.isNotBlank() }.map { entry ->
                val parts = entry.split(":::")
                val oldHeaders = parts.getOrElse(2) { "" }
                // 尝试从旧的 key=value 格式提取第一个 header
                val firstHeader = oldHeaders.lines().firstOrNull { it.contains('=') }
                val hName = firstHeader?.substringBefore('=')?.trim() ?: ""
                val hValue = firstHeader?.substringAfter('=')?.trim() ?: ""
                McpServerConfig(
                    name = parts.getOrElse(0) { "" },
                    url = parts.getOrElse(1) { "" },
                    transportType = McpTransportType.STREAMABLE_HTTP,
                    headerName = hName,
                    headerValue = hValue,
                    enabled = parts.getOrElse(3) { "true" }.toBooleanStrictOrNull() ?: true
                )
            }
            // 保存为新格式
            setMcpServers(ctx, migrated)
            migrated
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 保存所有 MCP 服务器配置 */
    fun setMcpServers(ctx: Context, servers: List<McpServerConfig>) {
        val raw = servers.joinToString("|||") { s ->
            "${s.name}:::${s.url}:::${s.transportType.name}:::${s.headerName}:::${s.headerValue}:::${s.enabled}"
        }
        prefs(ctx).edit().putString("mcp_servers_v2", raw).apply()
    }

    /** 添加 MCP 服务器 */
    fun addMcpServer(ctx: Context, server: McpServerConfig) {
        val servers = getMcpServers(ctx).toMutableList()
        servers.add(server)
        setMcpServers(ctx, servers)
    }

    /** 删除 MCP 服务器 */
    fun removeMcpServer(ctx: Context, index: Int) {
        val servers = getMcpServers(ctx).toMutableList()
        if (index in servers.indices) {
            servers.removeAt(index)
            setMcpServers(ctx, servers)
        }
    }

    /** 更新 MCP 服务器 */
    fun updateMcpServer(ctx: Context, index: Int, server: McpServerConfig) {
        val servers = getMcpServers(ctx).toMutableList()
        if (index in servers.indices) {
            servers[index] = server
            setMcpServers(ctx, servers)
        }
    }

    // ========== 跨对话记忆设置 ==========

    /** 是否启用跨对话记忆 */
    fun isMemoryEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("memory_enabled", false)

    fun setMemoryEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("memory_enabled", enabled).apply()
    }

    /** 记忆作用域名称（Agent 级别） */
    fun getMemoryAgentName(ctx: Context): String =
        prefs(ctx).getString("memory_agent_name", null) ?: "jasmine"

    fun setMemoryAgentName(ctx: Context, name: String) {
        prefs(ctx).edit().putString("memory_agent_name", name).apply()
    }

    /** 是否在每轮对话后自动提取事实 */
    fun isMemoryAutoExtract(ctx: Context): Boolean =
        prefs(ctx).getBoolean("memory_auto_extract", true)

    fun setMemoryAutoExtract(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("memory_auto_extract", enabled).apply()
    }

    // ========== 执行追踪设置 ==========

    /** 是否启用执行追踪 */
    fun isTraceEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("trace_enabled", false)

    fun setTraceEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("trace_enabled", enabled).apply()
    }

    /** 是否在聊天中内联显示追踪事件 */
    fun isTraceInlineDisplay(ctx: Context): Boolean =
        prefs(ctx).getBoolean("trace_inline_display", true)

    fun setTraceInlineDisplay(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("trace_inline_display", enabled).apply()
    }

    // ========== 任务规划设置 ==========

    /** 是否启用任务规划（Agent 模式下） */
    fun isPlannerEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("planner_enabled", false)

    fun setPlannerEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("planner_enabled", enabled).apply()
    }

    // ========== 智能上下文压缩设置 ==========

    /** 是否启用智能上下文压缩 */
    fun isCompressionEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("compression_enabled", false)

    fun setCompressionEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("compression_enabled", enabled).apply()
    }

    /**
     * 压缩策略类型
     * - TOKEN_BUDGET: 基于 token 预算自动触发（推荐）
     * - WHOLE_HISTORY: 整个历史生成 TLDR
     * - LAST_N: 只保留最后 N 条消息生成 TLDR
     * - CHUNKED: 按固定大小分块压缩
     */
    enum class CompressionStrategy {
        TOKEN_BUDGET, WHOLE_HISTORY, LAST_N, CHUNKED
    }

    /** 获取压缩策略，默认 TOKEN_BUDGET */
    fun getCompressionStrategy(ctx: Context): CompressionStrategy {
        val name = prefs(ctx).getString("compression_strategy", null) ?: return CompressionStrategy.TOKEN_BUDGET
        return try { CompressionStrategy.valueOf(name) } catch (_: Exception) { CompressionStrategy.TOKEN_BUDGET }
    }

    fun setCompressionStrategy(ctx: Context, strategy: CompressionStrategy) {
        prefs(ctx).edit().putString("compression_strategy", strategy.name).apply()
    }

    /** TokenBudget 的最大 token 数，默认 0 表示跟随模型上下文窗口 */
    fun getCompressionMaxTokens(ctx: Context): Int =
        prefs(ctx).getInt("compression_max_tokens", 0)

    fun setCompressionMaxTokens(ctx: Context, value: Int) {
        prefs(ctx).edit().putInt("compression_max_tokens", value).apply()
    }

    /** TokenBudget 触发阈值（百分比 1~99），默认 75 */
    fun getCompressionThreshold(ctx: Context): Int =
        prefs(ctx).getInt("compression_threshold", 75)

    fun setCompressionThreshold(ctx: Context, value: Int) {
        prefs(ctx).edit().putInt("compression_threshold", value).apply()
    }

    /** FromLastNMessages 的 N 值，默认 10 */
    fun getCompressionLastN(ctx: Context): Int =
        prefs(ctx).getInt("compression_last_n", 10)

    fun setCompressionLastN(ctx: Context, value: Int) {
        prefs(ctx).edit().putInt("compression_last_n", value).apply()
    }

    /** Chunked 的块大小，默认 20 */
    fun getCompressionChunkSize(ctx: Context): Int =
        prefs(ctx).getInt("compression_chunk_size", 20)

    fun setCompressionChunkSize(ctx: Context, value: Int) {
        prefs(ctx).edit().putInt("compression_chunk_size", value).apply()
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
