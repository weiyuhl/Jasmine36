package com.lhzkml.jasmine.core.config

import com.lhzkml.jasmine.core.agent.tools.ShellPolicy
import com.lhzkml.jasmine.core.agent.observe.snapshot.RollbackStrategy
import com.lhzkml.jasmine.core.agent.observe.trace.TraceEventCategory
import com.lhzkml.jasmine.core.agent.observe.event.EventCategory
import com.lhzkml.jasmine.core.prompt.llm.CompressionStrategyType

/**
 * 配置仓库接口
 *
 * 定义所有应用配置的读写契约，core 层通过此接口访问配置，
 * app 层提供基于 SharedPreferences 的实现。
 */
interface ConfigRepository {

    // ========== 供应商管理 ==========

    fun getActiveProviderId(): String?
    fun setActiveProviderId(id: String)
    fun getApiKey(providerId: String): String?
    fun saveProviderCredentials(providerId: String, apiKey: String, baseUrl: String? = null, model: String? = null)
    fun getBaseUrl(providerId: String): String
    fun getModel(providerId: String): String
    fun getSelectedModels(providerId: String): List<String>
    fun setSelectedModels(providerId: String, models: List<String>)
    fun getChatPath(providerId: String): String?
    fun saveChatPath(providerId: String, path: String)

    // Vertex AI
    fun isVertexAIEnabled(providerId: String): Boolean
    fun setVertexAIEnabled(providerId: String, enabled: Boolean)
    fun getVertexProjectId(providerId: String): String
    fun setVertexProjectId(providerId: String, projectId: String)
    fun getVertexLocation(providerId: String): String
    fun setVertexLocation(providerId: String, location: String)
    fun getVertexServiceAccountJson(providerId: String): String
    fun setVertexServiceAccountJson(providerId: String, json: String)

    // 自定义供应商持久化
    fun loadCustomProviders(): List<ProviderConfig>
    fun saveCustomProviders(providers: List<ProviderConfig>)

    // ========== LLM 参数 ==========

    fun getDefaultSystemPrompt(): String
    fun setDefaultSystemPrompt(prompt: String)
    fun getMaxTokens(): Int
    fun setMaxTokens(maxTokens: Int)
    fun getTemperature(): Float
    fun setTemperature(value: Float)
    fun getTopP(): Float
    fun setTopP(value: Float)
    fun getTopK(): Int
    fun setTopK(value: Int)

    // ========== 超时设置 ==========

    fun getRequestTimeout(): Int
    fun setRequestTimeout(seconds: Int)
    fun getSocketTimeout(): Int
    fun setSocketTimeout(seconds: Int)
    fun getConnectTimeout(): Int
    fun setConnectTimeout(seconds: Int)
    fun isStreamResumeEnabled(): Boolean
    fun setStreamResumeEnabled(enabled: Boolean)
    fun getStreamResumeMaxRetries(): Int
    fun setStreamResumeMaxRetries(value: Int)

    // ========== 工具设置 ==========

    fun isToolsEnabled(): Boolean
    fun setToolsEnabled(enabled: Boolean)
    fun getEnabledTools(): Set<String>
    fun setEnabledTools(tools: Set<String>)
    fun getAgentToolPreset(): Set<String>
    fun setAgentToolPreset(tools: Set<String>)
    fun getBrightDataKey(): String
    fun setBrightDataKey(key: String)

    // ========== Shell 策略 ==========

    fun getShellPolicy(): ShellPolicy
    fun setShellPolicy(policy: ShellPolicy)
    fun getShellBlacklist(): List<String>
    fun setShellBlacklist(list: List<String>)
    fun getShellWhitelist(): List<String>
    fun setShellWhitelist(list: List<String>)

    // ========== MCP 设置 ==========

    fun isMcpEnabled(): Boolean
    fun setMcpEnabled(enabled: Boolean)
    fun getMcpServers(): List<McpServerConfig>
    fun setMcpServers(servers: List<McpServerConfig>)
    fun addMcpServer(server: McpServerConfig)
    fun removeMcpServer(index: Int)
    fun updateMcpServer(index: Int, server: McpServerConfig)

    // ========== Agent 策略 ==========

    fun getAgentStrategy(): AgentStrategyType
    fun setAgentStrategy(strategy: AgentStrategyType)
    fun getGraphToolCallMode(): GraphToolCallMode
    fun setGraphToolCallMode(mode: GraphToolCallMode)
    fun getToolSelectionStrategy(): ToolSelectionStrategyType
    fun setToolSelectionStrategy(strategy: ToolSelectionStrategyType)
    fun getToolSelectionNames(): Set<String>
    fun setToolSelectionNames(names: Set<String>)
    fun getToolSelectionTaskDesc(): String
    fun setToolSelectionTaskDesc(desc: String)
    fun getToolChoiceMode(): ToolChoiceMode
    fun setToolChoiceMode(mode: ToolChoiceMode)
    fun getToolChoiceNamedTool(): String
    fun setToolChoiceNamedTool(name: String)

    // ========== 追踪设置 ==========

    fun isTraceEnabled(): Boolean
    fun setTraceEnabled(enabled: Boolean)
    fun isTraceFileEnabled(): Boolean
    fun setTraceFileEnabled(enabled: Boolean)
    fun getTraceEventFilter(): Set<TraceEventCategory>
    fun setTraceEventFilter(categories: Set<TraceEventCategory>)

    // ========== 规划设置 ==========

    fun isPlannerEnabled(): Boolean
    fun setPlannerEnabled(enabled: Boolean)
    fun getPlannerMaxIterations(): Int
    fun setPlannerMaxIterations(value: Int)
    fun isPlannerCriticEnabled(): Boolean
    fun setPlannerCriticEnabled(enabled: Boolean)

    // ========== 快照设置 ==========

    fun isSnapshotEnabled(): Boolean
    fun setSnapshotEnabled(enabled: Boolean)
    fun getSnapshotStorage(): SnapshotStorageType
    fun setSnapshotStorage(storage: SnapshotStorageType)
    fun isSnapshotAutoCheckpoint(): Boolean
    fun setSnapshotAutoCheckpoint(enabled: Boolean)
    fun getSnapshotRollbackStrategy(): RollbackStrategy
    fun setSnapshotRollbackStrategy(strategy: RollbackStrategy)

    // ========== 事件处理器 ==========

    fun isEventHandlerEnabled(): Boolean
    fun setEventHandlerEnabled(enabled: Boolean)
    fun getEventHandlerFilter(): Set<EventCategory>
    fun setEventHandlerFilter(categories: Set<EventCategory>)

    // ========== 压缩设置 ==========

    fun isCompressionEnabled(): Boolean
    fun setCompressionEnabled(enabled: Boolean)
    fun getCompressionStrategy(): CompressionStrategyType
    fun setCompressionStrategy(strategy: CompressionStrategyType)
    fun getCompressionMaxTokens(): Int
    fun setCompressionMaxTokens(value: Int)
    fun getCompressionThreshold(): Int
    fun setCompressionThreshold(value: Int)
    fun getCompressionLastN(): Int
    fun setCompressionLastN(value: Int)
    fun getCompressionChunkSize(): Int
    fun setCompressionChunkSize(value: Int)

    // ========== Agent 模式 ==========

    fun isAgentMode(): Boolean
    fun setAgentMode(enabled: Boolean)
    fun getWorkspacePath(): String
    fun setWorkspacePath(path: String)
    fun getWorkspaceUri(): String
    fun setWorkspaceUri(uri: String)
    fun getLastConversationId(): String
    fun setLastConversationId(id: String)
    fun hasLastSession(): Boolean
    fun setLastSession(active: Boolean)
}
