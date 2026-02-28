package com.lhzkml.jasmine

import android.content.Context
import android.content.SharedPreferences
import com.lhzkml.jasmine.core.agent.tools.ShellPolicy
import com.lhzkml.jasmine.core.agent.tools.ShellPolicyConfig
import com.lhzkml.jasmine.core.agent.tools.event.EventCategory
import com.lhzkml.jasmine.core.agent.tools.snapshot.RollbackStrategy
import com.lhzkml.jasmine.core.agent.tools.trace.TraceEventCategory
import com.lhzkml.jasmine.core.config.*
import com.lhzkml.jasmine.core.prompt.executor.ApiType
import com.lhzkml.jasmine.core.prompt.llm.CompressionStrategyType

/**
 * 基于 SharedPreferences 的 ConfigRepository 实现
 *
 * 这是 ConfigRepository 接口的 Android 平台实现，
 * 将所有配置持久化到 SharedPreferences。
 */
class SharedPreferencesConfigRepository(private val ctx: Context) : ConfigRepository {

    private fun prefs(): SharedPreferences =
        ctx.getSharedPreferences("jasmine_providers", Context.MODE_PRIVATE)

    // ========== 供应商管理 ==========

    override fun getActiveProviderId(): String? = prefs().getString("active_provider", null)

    override fun setActiveProviderId(id: String) {
        prefs().edit().putString("active_provider", id).apply()
    }

    override fun getApiKey(providerId: String): String? {
        val key = prefs().getString("${providerId}_api_key", null)
        return if (key.isNullOrBlank()) null else key
    }

    override fun saveProviderCredentials(providerId: String, apiKey: String, baseUrl: String?, model: String?) {
        prefs().edit().apply {
            putString("${providerId}_api_key", apiKey)
            if (baseUrl != null) putString("${providerId}_base_url", baseUrl)
            if (model != null) putString("${providerId}_model", model)
            apply()
        }
    }

    override fun getBaseUrl(providerId: String): String =
        prefs().getString("${providerId}_base_url", null) ?: ""

    override fun getModel(providerId: String): String =
        prefs().getString("${providerId}_model", null) ?: ""

    override fun getSelectedModels(providerId: String): List<String> {
        val raw = prefs().getString("${providerId}_selected_models", null) ?: return emptyList()
        return raw.split(",").filter { it.isNotBlank() }
    }

    override fun setSelectedModels(providerId: String, models: List<String>) {
        prefs().edit().putString("${providerId}_selected_models", models.joinToString(",")).apply()
    }

    override fun getChatPath(providerId: String): String? =
        prefs().getString("${providerId}_chat_path", null)

    override fun saveChatPath(providerId: String, path: String) {
        prefs().edit().putString("${providerId}_chat_path", path).apply()
    }

    // Vertex AI
    override fun isVertexAIEnabled(providerId: String): Boolean =
        prefs().getBoolean("${providerId}_vertex_enabled", false)

    override fun setVertexAIEnabled(providerId: String, enabled: Boolean) {
        prefs().edit().putBoolean("${providerId}_vertex_enabled", enabled).apply()
    }

    override fun getVertexProjectId(providerId: String): String =
        prefs().getString("${providerId}_vertex_project_id", null) ?: ""

    override fun setVertexProjectId(providerId: String, projectId: String) {
        prefs().edit().putString("${providerId}_vertex_project_id", projectId).apply()
    }

    override fun getVertexLocation(providerId: String): String =
        prefs().getString("${providerId}_vertex_location", null) ?: "global"

    override fun setVertexLocation(providerId: String, location: String) {
        prefs().edit().putString("${providerId}_vertex_location", location).apply()
    }

    override fun getVertexServiceAccountJson(providerId: String): String =
        prefs().getString("${providerId}_vertex_sa_json", null) ?: ""

    override fun setVertexServiceAccountJson(providerId: String, json: String) {
        prefs().edit().putString("${providerId}_vertex_sa_json", json).apply()
    }

    // 自定义供应商持久化
    override fun loadCustomProviders(): List<ProviderConfig> {
        val json = prefs().getString("custom_providers", null) ?: return emptyList()
        if (json.isEmpty()) return emptyList()
        return json.split("|").mapNotNull { entry ->
            val parts = entry.split("::")
            if (parts.size >= 4) {
                val apiType = if (parts.size >= 5) {
                    try { ApiType.valueOf(parts[4]) } catch (_: Exception) { ApiType.OPENAI }
                } else ApiType.OPENAI
                ProviderConfig(
                    id = parts[0], name = parts[1],
                    defaultBaseUrl = parts[2], defaultModel = parts[3],
                    apiType = apiType, isCustom = true
                )
            } else null
        }
    }

    override fun saveCustomProviders(providers: List<ProviderConfig>) {
        val json = providers.joinToString("|") { p ->
            "${p.id}::${p.name}::${p.defaultBaseUrl}::${p.defaultModel}::${p.apiType.name}"
        }
        prefs().edit().putString("custom_providers", json).apply()
    }

    // ========== LLM 参数 ==========

    override fun getDefaultSystemPrompt(): String =
        prefs().getString("default_system_prompt", null) ?: "You are a helpful assistant."

    override fun setDefaultSystemPrompt(prompt: String) {
        prefs().edit().putString("default_system_prompt", prompt).apply()
    }

    override fun getMaxTokens(): Int = prefs().getInt("max_tokens", 0)

    override fun setMaxTokens(maxTokens: Int) {
        prefs().edit().putInt("max_tokens", maxTokens).apply()
    }

    override fun getTemperature(): Float = prefs().getFloat("sampling_temperature", -1f)

    override fun setTemperature(value: Float) {
        prefs().edit().putFloat("sampling_temperature", value).apply()
    }

    override fun getTopP(): Float = prefs().getFloat("sampling_top_p", -1f)

    override fun setTopP(value: Float) {
        prefs().edit().putFloat("sampling_top_p", value).apply()
    }

    override fun getTopK(): Int = prefs().getInt("sampling_top_k", -1)

    override fun setTopK(value: Int) {
        prefs().edit().putInt("sampling_top_k", value).apply()
    }

    // ========== 超时设置 ==========

    override fun getRequestTimeout(): Int = prefs().getInt("timeout_request", 0)
    override fun setRequestTimeout(seconds: Int) { prefs().edit().putInt("timeout_request", seconds).apply() }
    override fun getSocketTimeout(): Int = prefs().getInt("timeout_socket", 0)
    override fun setSocketTimeout(seconds: Int) { prefs().edit().putInt("timeout_socket", seconds).apply() }
    override fun getConnectTimeout(): Int = prefs().getInt("timeout_connect", 0)
    override fun setConnectTimeout(seconds: Int) { prefs().edit().putInt("timeout_connect", seconds).apply() }
    override fun isStreamResumeEnabled(): Boolean = prefs().getBoolean("stream_resume_enabled", true)
    override fun setStreamResumeEnabled(enabled: Boolean) { prefs().edit().putBoolean("stream_resume_enabled", enabled).apply() }
    override fun getStreamResumeMaxRetries(): Int = prefs().getInt("stream_resume_max_retries", 3)
    override fun setStreamResumeMaxRetries(value: Int) { prefs().edit().putInt("stream_resume_max_retries", value.coerceIn(1, 10)).apply() }

    // ========== 工具设置 ==========

    override fun isToolsEnabled(): Boolean = prefs().getBoolean("tools_enabled", false)
    override fun setToolsEnabled(enabled: Boolean) { prefs().edit().putBoolean("tools_enabled", enabled).apply() }

    override fun getEnabledTools(): Set<String> {
        val raw = prefs().getString("enabled_tools", null) ?: return emptySet()
        return raw.split(",").filter { it.isNotBlank() }.toSet()
    }
    override fun setEnabledTools(tools: Set<String>) {
        prefs().edit().putString("enabled_tools", tools.joinToString(",")).apply()
    }

    override fun getAgentToolPreset(): Set<String> {
        val raw = prefs().getString("agent_tool_preset", null) ?: return emptySet()
        return raw.split(",").filter { it.isNotBlank() }.toSet()
    }
    override fun setAgentToolPreset(tools: Set<String>) {
        prefs().edit().putString("agent_tool_preset", tools.joinToString(",")).apply()
    }

    override fun getBrightDataKey(): String = prefs().getString("brightdata_api_key", null) ?: ""
    override fun setBrightDataKey(key: String) { prefs().edit().putString("brightdata_api_key", key).apply() }

    // ========== Shell 策略 ==========

    override fun getShellPolicy(): ShellPolicy {
        val name = prefs().getString("shell_policy", null) ?: return ShellPolicy.MANUAL
        return try { ShellPolicy.valueOf(name) } catch (_: Exception) { ShellPolicy.MANUAL }
    }
    override fun setShellPolicy(policy: ShellPolicy) { prefs().edit().putString("shell_policy", policy.name).apply() }

    override fun getShellBlacklist(): List<String> {
        val raw = prefs().getString("shell_blacklist", null) ?: return ShellPolicyConfig.DEFAULT_BLACKLIST
        return raw.split("\n").filter { it.isNotBlank() }
    }
    override fun setShellBlacklist(list: List<String>) { prefs().edit().putString("shell_blacklist", list.joinToString("\n")).apply() }

    override fun getShellWhitelist(): List<String> {
        val raw = prefs().getString("shell_whitelist", null) ?: return ShellPolicyConfig.DEFAULT_WHITELIST
        return raw.split("\n").filter { it.isNotBlank() }
    }
    override fun setShellWhitelist(list: List<String>) { prefs().edit().putString("shell_whitelist", list.joinToString("\n")).apply() }

    // ========== MCP 设置 ==========

    override fun isMcpEnabled(): Boolean = prefs().getBoolean("mcp_enabled", false)
    override fun setMcpEnabled(enabled: Boolean) { prefs().edit().putBoolean("mcp_enabled", enabled).apply() }

    override fun getMcpServers(): List<McpServerConfig> {
        val raw = prefs().getString("mcp_servers_v2", null)
        if (raw == null) return emptyList()
        return try {
            raw.split("|||").filter { it.isNotBlank() }.map { entry ->
                val parts = entry.split(":::")
                McpServerConfig(
                    name = parts.getOrElse(0) { "" },
                    url = parts.getOrElse(1) { "" },
                    transportType = try { McpTransportType.valueOf(parts.getOrElse(2) { "STREAMABLE_HTTP" }) } catch (_: Exception) { McpTransportType.STREAMABLE_HTTP },
                    headerName = parts.getOrElse(3) { "" },
                    headerValue = parts.getOrElse(4) { "" },
                    enabled = parts.getOrElse(5) { "true" }.toBooleanStrictOrNull() ?: true
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    override fun setMcpServers(servers: List<McpServerConfig>) {
        val raw = servers.joinToString("|||") { s ->
            "${s.name}:::${s.url}:::${s.transportType.name}:::${s.headerName}:::${s.headerValue}:::${s.enabled}"
        }
        prefs().edit().putString("mcp_servers_v2", raw).apply()
    }

    override fun addMcpServer(server: McpServerConfig) {
        val servers = getMcpServers().toMutableList()
        servers.add(server)
        setMcpServers(servers)
    }

    override fun removeMcpServer(index: Int) {
        val servers = getMcpServers().toMutableList()
        if (index in servers.indices) { servers.removeAt(index); setMcpServers(servers) }
    }

    override fun updateMcpServer(index: Int, server: McpServerConfig) {
        val servers = getMcpServers().toMutableList()
        if (index in servers.indices) { servers[index] = server; setMcpServers(servers) }
    }

    // ========== Agent 策略 ==========

    override fun getAgentStrategy(): AgentStrategyType {
        val name = prefs().getString("agent_strategy", null) ?: return AgentStrategyType.SIMPLE_LOOP
        return try { AgentStrategyType.valueOf(name) } catch (_: Exception) { AgentStrategyType.SIMPLE_LOOP }
    }
    override fun setAgentStrategy(strategy: AgentStrategyType) { prefs().edit().putString("agent_strategy", strategy.name).apply() }

    override fun getGraphToolCallMode(): GraphToolCallMode {
        val name = prefs().getString("graph_tool_call_mode", null) ?: return GraphToolCallMode.SEQUENTIAL
        return try { GraphToolCallMode.valueOf(name) } catch (_: Exception) { GraphToolCallMode.SEQUENTIAL }
    }
    override fun setGraphToolCallMode(mode: GraphToolCallMode) { prefs().edit().putString("graph_tool_call_mode", mode.name).apply() }

    override fun getToolSelectionStrategy(): ToolSelectionStrategyType {
        val name = prefs().getString("tool_selection_strategy", null) ?: return ToolSelectionStrategyType.ALL
        return try { ToolSelectionStrategyType.valueOf(name) } catch (_: Exception) { ToolSelectionStrategyType.ALL }
    }
    override fun setToolSelectionStrategy(strategy: ToolSelectionStrategyType) { prefs().edit().putString("tool_selection_strategy", strategy.name).apply() }

    override fun getToolSelectionNames(): Set<String> {
        val raw = prefs().getString("tool_selection_names", null) ?: return emptySet()
        return raw.split(",").filter { it.isNotBlank() }.toSet()
    }
    override fun setToolSelectionNames(names: Set<String>) { prefs().edit().putString("tool_selection_names", names.joinToString(",")).apply() }

    override fun getToolSelectionTaskDesc(): String = prefs().getString("tool_selection_task_desc", null) ?: ""
    override fun setToolSelectionTaskDesc(desc: String) { prefs().edit().putString("tool_selection_task_desc", desc).apply() }

    override fun getToolChoiceMode(): ToolChoiceMode {
        val name = prefs().getString("tool_choice_mode", null) ?: return ToolChoiceMode.DEFAULT
        return try { ToolChoiceMode.valueOf(name) } catch (_: Exception) { ToolChoiceMode.DEFAULT }
    }
    override fun setToolChoiceMode(mode: ToolChoiceMode) { prefs().edit().putString("tool_choice_mode", mode.name).apply() }

    override fun getToolChoiceNamedTool(): String = prefs().getString("tool_choice_named_tool", null) ?: ""
    override fun setToolChoiceNamedTool(name: String) { prefs().edit().putString("tool_choice_named_tool", name).apply() }

    // ========== 追踪设置 ==========

    override fun isTraceEnabled(): Boolean = prefs().getBoolean("trace_enabled", false)
    override fun setTraceEnabled(enabled: Boolean) { prefs().edit().putBoolean("trace_enabled", enabled).apply() }
    override fun isTraceFileEnabled(): Boolean = prefs().getBoolean("trace_file_enabled", false)
    override fun setTraceFileEnabled(enabled: Boolean) { prefs().edit().putBoolean("trace_file_enabled", enabled).apply() }

    override fun getTraceEventFilter(): Set<TraceEventCategory> {
        val raw = prefs().getString("trace_event_filter", null) ?: return emptySet()
        return raw.split(",").filter { it.isNotBlank() }.mapNotNull {
            try { TraceEventCategory.valueOf(it) } catch (_: Exception) { null }
        }.toSet()
    }
    override fun setTraceEventFilter(categories: Set<TraceEventCategory>) {
        prefs().edit().putString("trace_event_filter", categories.joinToString(",") { it.name }).apply()
    }

    // ========== 规划设置 ==========

    override fun isPlannerEnabled(): Boolean = prefs().getBoolean("planner_enabled", false)
    override fun setPlannerEnabled(enabled: Boolean) { prefs().edit().putBoolean("planner_enabled", enabled).apply() }
    override fun getPlannerMaxIterations(): Int = prefs().getInt("planner_max_iterations", 1)
    override fun setPlannerMaxIterations(value: Int) { prefs().edit().putInt("planner_max_iterations", value.coerceIn(1, 20)).apply() }
    override fun isPlannerCriticEnabled(): Boolean = prefs().getBoolean("planner_critic_enabled", false)
    override fun setPlannerCriticEnabled(enabled: Boolean) { prefs().edit().putBoolean("planner_critic_enabled", enabled).apply() }

    // ========== 快照设置 ==========

    override fun isSnapshotEnabled(): Boolean = prefs().getBoolean("snapshot_enabled", false)
    override fun setSnapshotEnabled(enabled: Boolean) { prefs().edit().putBoolean("snapshot_enabled", enabled).apply() }

    override fun getSnapshotStorage(): SnapshotStorageType {
        val name = prefs().getString("snapshot_storage", null) ?: return SnapshotStorageType.MEMORY
        return try { SnapshotStorageType.valueOf(name) } catch (_: Exception) { SnapshotStorageType.MEMORY }
    }
    override fun setSnapshotStorage(storage: SnapshotStorageType) { prefs().edit().putString("snapshot_storage", storage.name).apply() }

    override fun isSnapshotAutoCheckpoint(): Boolean = prefs().getBoolean("snapshot_auto_checkpoint", true)
    override fun setSnapshotAutoCheckpoint(enabled: Boolean) { prefs().edit().putBoolean("snapshot_auto_checkpoint", enabled).apply() }

    override fun getSnapshotRollbackStrategy(): RollbackStrategy {
        val name = prefs().getString("snapshot_rollback_strategy", null) ?: return RollbackStrategy.RESTART_FROM_NODE
        return try { RollbackStrategy.valueOf(name) } catch (_: Exception) { RollbackStrategy.RESTART_FROM_NODE }
    }
    override fun setSnapshotRollbackStrategy(strategy: RollbackStrategy) { prefs().edit().putString("snapshot_rollback_strategy", strategy.name).apply() }

    // ========== 事件处理器 ==========

    override fun isEventHandlerEnabled(): Boolean = prefs().getBoolean("event_handler_enabled", false)
    override fun setEventHandlerEnabled(enabled: Boolean) { prefs().edit().putBoolean("event_handler_enabled", enabled).apply() }

    override fun getEventHandlerFilter(): Set<EventCategory> {
        val raw = prefs().getString("event_handler_filter", null) ?: return emptySet()
        return raw.split(",").filter { it.isNotBlank() }.mapNotNull {
            try { EventCategory.valueOf(it) } catch (_: Exception) { null }
        }.toSet()
    }
    override fun setEventHandlerFilter(categories: Set<EventCategory>) {
        prefs().edit().putString("event_handler_filter", categories.joinToString(",") { it.name }).apply()
    }

    // ========== 压缩设置 ==========

    override fun isCompressionEnabled(): Boolean = prefs().getBoolean("compression_enabled", false)
    override fun setCompressionEnabled(enabled: Boolean) { prefs().edit().putBoolean("compression_enabled", enabled).apply() }

    override fun getCompressionStrategy(): CompressionStrategyType {
        val name = prefs().getString("compression_strategy", null) ?: return CompressionStrategyType.TOKEN_BUDGET
        return try { CompressionStrategyType.valueOf(name) } catch (_: Exception) { CompressionStrategyType.TOKEN_BUDGET }
    }
    override fun setCompressionStrategy(strategy: CompressionStrategyType) { prefs().edit().putString("compression_strategy", strategy.name).apply() }

    override fun getCompressionMaxTokens(): Int = prefs().getInt("compression_max_tokens", 0)
    override fun setCompressionMaxTokens(value: Int) { prefs().edit().putInt("compression_max_tokens", value).apply() }
    override fun getCompressionThreshold(): Int = prefs().getInt("compression_threshold", 75)
    override fun setCompressionThreshold(value: Int) { prefs().edit().putInt("compression_threshold", value).apply() }
    override fun getCompressionLastN(): Int = prefs().getInt("compression_last_n", 10)
    override fun setCompressionLastN(value: Int) { prefs().edit().putInt("compression_last_n", value).apply() }
    override fun getCompressionChunkSize(): Int = prefs().getInt("compression_chunk_size", 20)
    override fun setCompressionChunkSize(value: Int) { prefs().edit().putInt("compression_chunk_size", value).apply() }

    // ========== Agent 模式 ==========

    override fun isAgentMode(): Boolean = prefs().getBoolean("agent_mode", false)
    override fun setAgentMode(enabled: Boolean) { prefs().edit().putBoolean("agent_mode", enabled).apply() }
    override fun getWorkspacePath(): String = prefs().getString("workspace_path", null) ?: ""
    override fun setWorkspacePath(path: String) { prefs().edit().putString("workspace_path", path).apply() }
    override fun getWorkspaceUri(): String = prefs().getString("workspace_uri", null) ?: ""
    override fun setWorkspaceUri(uri: String) { prefs().edit().putString("workspace_uri", uri).apply() }

    override fun getLastConversationId(): String {
        val ws = getWorkspacePath()
        val key = if (ws.isEmpty()) "last_conversation_id" else "last_conversation_id_$ws"
        return prefs().getString(key, "") ?: ""
    }
    override fun setLastConversationId(id: String) {
        val ws = getWorkspacePath()
        val key = if (ws.isEmpty()) "last_conversation_id" else "last_conversation_id_$ws"
        prefs().edit().putString(key, id).apply()
    }

    override fun hasLastSession(): Boolean = prefs().getBoolean("has_last_session", false)
    override fun setLastSession(active: Boolean) { prefs().edit().putBoolean("has_last_session", active).apply() }
}
