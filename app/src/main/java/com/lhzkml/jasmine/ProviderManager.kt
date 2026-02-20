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
        Provider("openai", "OpenAI", "https://api.openai.com", "", ApiType.OPENAI),
        Provider("claude", "Claude", "https://api.anthropic.com", "", ApiType.CLAUDE),
        Provider("gemini", "Gemini", "https://generativelanguage.googleapis.com", "", ApiType.GEMINI),
        // 其他供应商
        Provider("deepseek", "DeepSeek", "https://api.deepseek.com", "", ApiType.OPENAI),
        Provider("siliconflow", "硅基流动", "https://api.siliconflow.cn", "", ApiType.OPENAI),
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

    /** 获取供应商已勾选的模型列表 */
    fun getSelectedModels(ctx: Context, id: String): List<String> {
        val raw = prefs(ctx).getString("${id}_selected_models", null) ?: return emptyList()
        return raw.split(",").filter { it.isNotBlank() }
    }

    /** 保存供应商已勾选的模型列表 */
    fun setSelectedModels(ctx: Context, id: String, models: List<String>) {
        prefs(ctx).edit().putString("${id}_selected_models", models.joinToString(",")).apply()
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

    // ========== Agent 策略设置 ==========

    /**
     * Agent 执行策略类型
     * - SIMPLE_LOOP: 简单 while 循环（ToolExecutor），默认
     * - SINGLE_RUN_GRAPH: 图策略（GraphAgent + singleRunStrategy），参考 koog
     */
    enum class AgentStrategyType {
        SIMPLE_LOOP,
        SINGLE_RUN_GRAPH
    }

    fun getAgentStrategy(ctx: Context): AgentStrategyType {
        val name = prefs(ctx).getString("agent_strategy", null) ?: return AgentStrategyType.SIMPLE_LOOP
        return try { AgentStrategyType.valueOf(name) } catch (_: Exception) { AgentStrategyType.SIMPLE_LOOP }
    }

    fun setAgentStrategy(ctx: Context, strategy: AgentStrategyType) {
        prefs(ctx).edit().putString("agent_strategy", strategy.name).apply()
    }

    // ========== 执行追踪设置 ==========

    /** 是否启用执行追踪 */
    fun isTraceEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("trace_enabled", false)

    fun setTraceEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("trace_enabled", enabled).apply()
    }

    /** 是否启用文件输出追踪 */
    fun isTraceFileEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("trace_file_enabled", false)

    fun setTraceFileEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("trace_file_enabled", enabled).apply()
    }

    /**
     * 追踪事件过滤：选择要追踪的事件类别
     * 空集合表示全部追踪
     */
    enum class TraceEventCategory {
        AGENT,      // Agent 生命周期
        LLM,        // LLM 调用
        TOOL,       // 工具调用
        STRATEGY,   // 策略执行
        NODE,       // 节点执行
        SUBGRAPH,   // 子图执行
        COMPRESSION // 压缩事件
    }

    fun getTraceEventFilter(ctx: Context): Set<TraceEventCategory> {
        val raw = prefs(ctx).getString("trace_event_filter", null) ?: return emptySet()
        return raw.split(",").filter { it.isNotBlank() }.mapNotNull {
            try { TraceEventCategory.valueOf(it) } catch (_: Exception) { null }
        }.toSet()
    }

    fun setTraceEventFilter(ctx: Context, categories: Set<TraceEventCategory>) {
        prefs(ctx).edit().putString("trace_event_filter", categories.joinToString(",") { it.name }).apply()
    }

    // ========== 任务规划设置 ==========

    /** 是否启用任务规划（Agent 模式下） */
    fun isPlannerEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("planner_enabled", false)

    fun setPlannerEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("planner_enabled", enabled).apply()
    }

    /** 规划器最大迭代次数，默认 1 */
    fun getPlannerMaxIterations(ctx: Context): Int =
        prefs(ctx).getInt("planner_max_iterations", 1)

    fun setPlannerMaxIterations(ctx: Context, value: Int) {
        prefs(ctx).edit().putInt("planner_max_iterations", value.coerceIn(1, 20)).apply()
    }

    /** 是否启用 Critic 评估（SimpleLLMWithCriticPlanner） */
    fun isPlannerCriticEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("planner_critic_enabled", false)

    fun setPlannerCriticEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("planner_critic_enabled", enabled).apply()
    }

    // ========== 快照/持久化设置 ==========

    /** 是否启用快照（Agent 模式下自动创建检查点） */
    fun isSnapshotEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("snapshot_enabled", false)

    fun setSnapshotEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("snapshot_enabled", enabled).apply()
    }

    /** 快照存储方式 */
    enum class SnapshotStorage {
        MEMORY,  // 内存存储（应用关闭后丢失）
        FILE     // 文件存储（持久化到本地）
    }

    fun getSnapshotStorage(ctx: Context): SnapshotStorage {
        val name = prefs(ctx).getString("snapshot_storage", null) ?: return SnapshotStorage.MEMORY
        return try { SnapshotStorage.valueOf(name) } catch (_: Exception) { SnapshotStorage.MEMORY }
    }

    fun setSnapshotStorage(ctx: Context, storage: SnapshotStorage) {
        prefs(ctx).edit().putString("snapshot_storage", storage.name).apply()
    }

    /** 是否启用自动检查点（每个节点执行后自动创建） */
    fun isSnapshotAutoCheckpoint(ctx: Context): Boolean =
        prefs(ctx).getBoolean("snapshot_auto_checkpoint", true)

    fun setSnapshotAutoCheckpoint(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("snapshot_auto_checkpoint", enabled).apply()
    }

    /** 回滚策略 */
    enum class SnapshotRollbackStrategy {
        RESTART_FROM_NODE,   // 从节点重新执行
        SKIP_NODE,           // 跳过该节点
        USE_DEFAULT_OUTPUT   // 使用默认输出
    }

    fun getSnapshotRollbackStrategy(ctx: Context): SnapshotRollbackStrategy {
        val name = prefs(ctx).getString("snapshot_rollback_strategy", null) ?: return SnapshotRollbackStrategy.RESTART_FROM_NODE
        return try { SnapshotRollbackStrategy.valueOf(name) } catch (_: Exception) { SnapshotRollbackStrategy.RESTART_FROM_NODE }
    }

    fun setSnapshotRollbackStrategy(ctx: Context, strategy: SnapshotRollbackStrategy) {
        prefs(ctx).edit().putString("snapshot_rollback_strategy", strategy.name).apply()
    }

    // ========== 事件处理器设置 ==========

    /** 是否启用事件处理器（Agent 生命周期事件回调） */
    fun isEventHandlerEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("event_handler_enabled", false)

    fun setEventHandlerEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("event_handler_enabled", enabled).apply()
    }

    /**
     * 事件处理器：选择要监听的事件类别
     * 空集合表示全部监听
     */
    enum class EventCategory {
        AGENT,      // Agent 开始/完成/失败
        TOOL,       // 工具调用开始/完成
        LLM,        // LLM 调用完成
        STRATEGY,   // 策略开始/完成
        NODE,       // 节点执行
        SUBGRAPH,   // 子图执行
        STREAMING   // LLM 流式事件
    }

    fun getEventHandlerFilter(ctx: Context): Set<EventCategory> {
        val raw = prefs(ctx).getString("event_handler_filter", null) ?: return emptySet()
        return raw.split(",").filter { it.isNotBlank() }.mapNotNull {
            try { EventCategory.valueOf(it) } catch (_: Exception) { null }
        }.toSet()
    }

    fun setEventHandlerFilter(ctx: Context, categories: Set<EventCategory>) {
        prefs(ctx).edit().putString("event_handler_filter", categories.joinToString(",") { it.name }).apply()
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

    // ========== Agent 模式 ==========

    /** 是否处于 Agent 模式 */
    fun isAgentMode(ctx: Context): Boolean =
        prefs(ctx).getBoolean("agent_mode", false)

    fun setAgentMode(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("agent_mode", enabled).apply()
    }

    /** 是否有上次的会话（用于启动时自动恢复） */
    fun hasLastSession(ctx: Context): Boolean =
        prefs(ctx).getBoolean("has_last_session", false)

    fun setLastSession(ctx: Context, active: Boolean) {
        prefs(ctx).edit().putBoolean("has_last_session", active).apply()
    }

    /** 上次打开的会话 ID（按工作区隔离存储） */
    fun getLastConversationId(ctx: Context): String {
        val ws = getWorkspacePath(ctx)
        val key = if (ws.isEmpty()) "last_conversation_id" else "last_conversation_id_$ws"
        return prefs(ctx).getString(key, "") ?: ""
    }

    fun setLastConversationId(ctx: Context, id: String) {
        val ws = getWorkspacePath(ctx)
        val key = if (ws.isEmpty()) "last_conversation_id" else "last_conversation_id_$ws"
        prefs(ctx).edit().putString(key, id).apply()
    }

    /** Agent 模式工作区路径（用户选择的本地文件夹） */
    fun getWorkspacePath(ctx: Context): String =
        prefs(ctx).getString("workspace_path", null) ?: ""

    fun setWorkspacePath(ctx: Context, path: String) {
        prefs(ctx).edit().putString("workspace_path", path).apply()
    }

    /** Agent 模式工作区 URI（SAF 持久化权限用） */
    fun getWorkspaceUri(ctx: Context): String =
        prefs(ctx).getString("workspace_uri", null) ?: ""

    fun setWorkspaceUri(ctx: Context, uri: String) {
        prefs(ctx).edit().putString("workspace_uri", uri).apply()
    }
}
