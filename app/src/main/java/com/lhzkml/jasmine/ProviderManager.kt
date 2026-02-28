package com.lhzkml.jasmine

import android.content.Context
import com.lhzkml.jasmine.core.agent.tools.ShellPolicy
import com.lhzkml.jasmine.core.agent.tools.event.EventCategory
import com.lhzkml.jasmine.core.agent.tools.snapshot.RollbackStrategy
import com.lhzkml.jasmine.core.agent.tools.trace.TraceEventCategory
import com.lhzkml.jasmine.core.config.*
import com.lhzkml.jasmine.core.prompt.executor.ApiType
import com.lhzkml.jasmine.core.prompt.llm.CompressionStrategyType

/**
 * ProviderManager - 配置管理门面
 *
 * 这是一个薄薄的委托层，所有调用转发给 ConfigRepository 和 ProviderRegistry。
 */
object ProviderManager {

    private lateinit var configRepo: ConfigRepository
    private lateinit var registry: ProviderRegistry

    fun initialize(context: Context) {
        configRepo = SharedPreferencesConfigRepository(context)
        registry = ProviderRegistry(configRepo)
        registry.initialize()
    }

    // ========== 供应商管理 ==========

    fun getAllProviders(): List<ProviderConfig> = registry.providers

    fun getProvider(id: String): ProviderConfig? = registry.getProvider(id)

    fun getActiveProvider(): String? = configRepo.getActiveProviderId()

    fun setActiveProvider(id: String) = configRepo.setActiveProviderId(id)

    fun getActiveId(): String? = configRepo.getActiveProviderId()

    fun getActiveConfig(): ActiveProviderConfig? = registry.getActiveConfig()

    fun saveConfig(context: Context, providerId: String, apiKey: String, baseUrl: String, model: String) {
        configRepo.saveProviderCredentials(providerId, apiKey, baseUrl, model)
    }

    fun registerProvider(provider: ProviderConfig): Boolean = registry.registerProviderPersistent(provider)

    fun unregisterProvider(id: String): Boolean = registry.unregisterProviderPersistent(id)

    // ========== 供应商凭证 ==========

    fun getApiKey(context: Context, providerId: String? = null): String? {
        val id = providerId ?: configRepo.getActiveProviderId() ?: return null
        return configRepo.getApiKey(id)
    }

    fun saveProviderCredentials(
        context: Context,
        providerId: String,
        apiKey: String,
        baseUrl: String? = null,
        model: String? = null
    ) {
        configRepo.saveProviderCredentials(providerId, apiKey, baseUrl, model)
    }

    fun getBaseUrl(context: Context, providerId: String? = null): String {
        val id = providerId ?: configRepo.getActiveProviderId() ?: return ""
        return configRepo.getBaseUrl(id)
    }

    fun getModel(context: Context, providerId: String? = null): String {
        val id = providerId ?: configRepo.getActiveProviderId() ?: return ""
        return configRepo.getModel(id)
    }

    fun getSelectedModels(context: Context, providerId: String): List<String> =
        configRepo.getSelectedModels(providerId)

    fun setSelectedModels(context: Context, providerId: String, models: List<String>) =
        configRepo.setSelectedModels(providerId, models)

    fun getChatPath(context: Context, providerId: String): String? =
        configRepo.getChatPath(providerId)

    fun saveChatPath(context: Context, providerId: String, path: String) =
        configRepo.saveChatPath(providerId, path)

    // ========== Vertex AI ==========

    fun isVertexAIEnabled(context: Context, providerId: String): Boolean =
        configRepo.isVertexAIEnabled(providerId)

    fun setVertexAIEnabled(context: Context, providerId: String, enabled: Boolean) =
        configRepo.setVertexAIEnabled(providerId, enabled)

    fun getVertexProjectId(context: Context, providerId: String): String =
        configRepo.getVertexProjectId(providerId)

    fun setVertexProjectId(context: Context, providerId: String, projectId: String) =
        configRepo.setVertexProjectId(providerId, projectId)

    fun getVertexLocation(context: Context, providerId: String): String =
        configRepo.getVertexLocation(providerId)

    fun setVertexLocation(context: Context, providerId: String, location: String) =
        configRepo.setVertexLocation(providerId, location)

    fun getVertexServiceAccountJson(context: Context, providerId: String): String =
        configRepo.getVertexServiceAccountJson(providerId)

    fun setVertexServiceAccountJson(context: Context, providerId: String, json: String) =
        configRepo.setVertexServiceAccountJson(providerId, json)

    // ========== LLM 参数 ==========

    fun getDefaultSystemPrompt(context: Context): String =
        configRepo.getDefaultSystemPrompt()

    fun setDefaultSystemPrompt(context: Context, prompt: String) =
        configRepo.setDefaultSystemPrompt(prompt)

    fun getMaxTokens(context: Context): Int = configRepo.getMaxTokens()

    fun setMaxTokens(context: Context, maxTokens: Int) = configRepo.setMaxTokens(maxTokens)

    fun getTemperature(context: Context): Float = configRepo.getTemperature()

    fun setTemperature(context: Context, value: Float) = configRepo.setTemperature(value)

    fun getTopP(context: Context): Float = configRepo.getTopP()

    fun setTopP(context: Context, value: Float) = configRepo.setTopP(value)

    fun getTopK(context: Context): Int = configRepo.getTopK()

    fun setTopK(context: Context, value: Int) = configRepo.setTopK(value)

    // ========== 超时设置 ==========

    fun getRequestTimeout(context: Context): Int = configRepo.getRequestTimeout()
    fun setRequestTimeout(context: Context, seconds: Int) = configRepo.setRequestTimeout(seconds)
    fun getSocketTimeout(context: Context): Int = configRepo.getSocketTimeout()
    fun setSocketTimeout(context: Context, seconds: Int) = configRepo.setSocketTimeout(seconds)
    fun getConnectTimeout(context: Context): Int = configRepo.getConnectTimeout()
    fun setConnectTimeout(context: Context, seconds: Int) = configRepo.setConnectTimeout(seconds)
    fun isStreamResumeEnabled(context: Context): Boolean = configRepo.isStreamResumeEnabled()
    fun setStreamResumeEnabled(context: Context, enabled: Boolean) = configRepo.setStreamResumeEnabled(enabled)
    fun getStreamResumeMaxRetries(context: Context): Int = configRepo.getStreamResumeMaxRetries()
    fun setStreamResumeMaxRetries(context: Context, value: Int) = configRepo.setStreamResumeMaxRetries(value)

    // ========== 工具设置 ==========

    fun isToolsEnabled(context: Context): Boolean = configRepo.isToolsEnabled()
    fun setToolsEnabled(context: Context, enabled: Boolean) = configRepo.setToolsEnabled(enabled)
    fun getEnabledTools(context: Context): Set<String> = configRepo.getEnabledTools()
    fun setEnabledTools(context: Context, tools: Set<String>) = configRepo.setEnabledTools(tools)
    fun getAgentToolPreset(context: Context): Set<String> = configRepo.getAgentToolPreset()
    fun setAgentToolPreset(context: Context, tools: Set<String>) = configRepo.setAgentToolPreset(tools)
    fun getBrightDataKey(context: Context): String = configRepo.getBrightDataKey()
    fun setBrightDataKey(context: Context, key: String) = configRepo.setBrightDataKey(key)

    // ========== Shell 策略 ==========

    fun getShellPolicy(context: Context): ShellPolicy = configRepo.getShellPolicy()
    fun setShellPolicy(context: Context, policy: ShellPolicy) = configRepo.setShellPolicy(policy)
    fun getShellBlacklist(context: Context): List<String> = configRepo.getShellBlacklist()
    fun setShellBlacklist(context: Context, list: List<String>) = configRepo.setShellBlacklist(list)
    fun getShellWhitelist(context: Context): List<String> = configRepo.getShellWhitelist()
    fun setShellWhitelist(context: Context, list: List<String>) = configRepo.setShellWhitelist(list)

    // ========== MCP 设置 ==========

    fun isMcpEnabled(context: Context): Boolean = configRepo.isMcpEnabled()
    fun setMcpEnabled(context: Context, enabled: Boolean) = configRepo.setMcpEnabled(enabled)
    fun getMcpServers(context: Context): List<McpServerConfig> = configRepo.getMcpServers()
    fun setMcpServers(context: Context, servers: List<McpServerConfig>) = configRepo.setMcpServers(servers)
    fun addMcpServer(context: Context, server: McpServerConfig) = configRepo.addMcpServer(server)
    fun removeMcpServer(context: Context, index: Int) = configRepo.removeMcpServer(index)
    fun updateMcpServer(context: Context, index: Int, server: McpServerConfig) = configRepo.updateMcpServer(index, server)

    // ========== Agent 策略 ==========

    fun getAgentStrategy(context: Context): AgentStrategyType = configRepo.getAgentStrategy()
    fun setAgentStrategy(context: Context, strategy: AgentStrategyType) = configRepo.setAgentStrategy(strategy)
    fun getGraphToolCallMode(context: Context): GraphToolCallMode = configRepo.getGraphToolCallMode()
    fun setGraphToolCallMode(context: Context, mode: GraphToolCallMode) = configRepo.setGraphToolCallMode(mode)
    fun getToolSelectionStrategy(context: Context): ToolSelectionStrategyType = configRepo.getToolSelectionStrategy()
    fun setToolSelectionStrategy(context: Context, strategy: ToolSelectionStrategyType) = configRepo.setToolSelectionStrategy(strategy)
    fun getToolSelectionNames(context: Context): Set<String> = configRepo.getToolSelectionNames()
    fun setToolSelectionNames(context: Context, names: Set<String>) = configRepo.setToolSelectionNames(names)
    fun getToolSelectionTaskDesc(context: Context): String = configRepo.getToolSelectionTaskDesc()
    fun setToolSelectionTaskDesc(context: Context, desc: String) = configRepo.setToolSelectionTaskDesc(desc)
    fun getToolChoiceMode(context: Context): ToolChoiceMode = configRepo.getToolChoiceMode()
    fun setToolChoiceMode(context: Context, mode: ToolChoiceMode) = configRepo.setToolChoiceMode(mode)
    fun getToolChoiceNamedTool(context: Context): String = configRepo.getToolChoiceNamedTool()
    fun setToolChoiceNamedTool(context: Context, name: String) = configRepo.setToolChoiceNamedTool(name)

    // ========== 追踪设置 ==========

    fun isTraceEnabled(context: Context): Boolean = configRepo.isTraceEnabled()
    fun setTraceEnabled(context: Context, enabled: Boolean) = configRepo.setTraceEnabled(enabled)
    fun isTraceFileEnabled(context: Context): Boolean = configRepo.isTraceFileEnabled()
    fun setTraceFileEnabled(context: Context, enabled: Boolean) = configRepo.setTraceFileEnabled(enabled)
    fun getTraceEventFilter(context: Context): Set<TraceEventCategory> = configRepo.getTraceEventFilter()
    fun setTraceEventFilter(context: Context, categories: Set<TraceEventCategory>) = configRepo.setTraceEventFilter(categories)

    // ========== 规划设置 ==========

    fun isPlannerEnabled(context: Context): Boolean = configRepo.isPlannerEnabled()
    fun setPlannerEnabled(context: Context, enabled: Boolean) = configRepo.setPlannerEnabled(enabled)
    fun getPlannerMaxIterations(context: Context): Int = configRepo.getPlannerMaxIterations()
    fun setPlannerMaxIterations(context: Context, value: Int) = configRepo.setPlannerMaxIterations(value)
    fun isPlannerCriticEnabled(context: Context): Boolean = configRepo.isPlannerCriticEnabled()
    fun setPlannerCriticEnabled(context: Context, enabled: Boolean) = configRepo.setPlannerCriticEnabled(enabled)

    // ========== 快照设置 ==========

    fun isSnapshotEnabled(context: Context): Boolean = configRepo.isSnapshotEnabled()
    fun setSnapshotEnabled(context: Context, enabled: Boolean) = configRepo.setSnapshotEnabled(enabled)
    fun getSnapshotStorage(context: Context): SnapshotStorageType = configRepo.getSnapshotStorage()
    fun setSnapshotStorage(context: Context, storage: SnapshotStorageType) = configRepo.setSnapshotStorage(storage)
    fun isSnapshotAutoCheckpoint(context: Context): Boolean = configRepo.isSnapshotAutoCheckpoint()
    fun setSnapshotAutoCheckpoint(context: Context, enabled: Boolean) = configRepo.setSnapshotAutoCheckpoint(enabled)
    fun getSnapshotRollbackStrategy(context: Context): RollbackStrategy = configRepo.getSnapshotRollbackStrategy()
    fun setSnapshotRollbackStrategy(context: Context, strategy: RollbackStrategy) = configRepo.setSnapshotRollbackStrategy(strategy)

    // ========== 事件处理器 ==========

    fun isEventHandlerEnabled(context: Context): Boolean = configRepo.isEventHandlerEnabled()
    fun setEventHandlerEnabled(context: Context, enabled: Boolean) = configRepo.setEventHandlerEnabled(enabled)
    fun getEventHandlerFilter(context: Context): Set<EventCategory> = configRepo.getEventHandlerFilter()
    fun setEventHandlerFilter(context: Context, categories: Set<EventCategory>) = configRepo.setEventHandlerFilter(categories)

    // ========== 压缩设置 ==========

    fun isCompressionEnabled(context: Context): Boolean = configRepo.isCompressionEnabled()
    fun setCompressionEnabled(context: Context, enabled: Boolean) = configRepo.setCompressionEnabled(enabled)
    fun getCompressionStrategy(context: Context): CompressionStrategyType = configRepo.getCompressionStrategy()
    fun setCompressionStrategy(context: Context, strategy: CompressionStrategyType) = configRepo.setCompressionStrategy(strategy)
    fun getCompressionMaxTokens(context: Context): Int = configRepo.getCompressionMaxTokens()
    fun setCompressionMaxTokens(context: Context, value: Int) = configRepo.setCompressionMaxTokens(value)
    fun getCompressionThreshold(context: Context): Int = configRepo.getCompressionThreshold()
    fun setCompressionThreshold(context: Context, value: Int) = configRepo.setCompressionThreshold(value)
    fun getCompressionLastN(context: Context): Int = configRepo.getCompressionLastN()
    fun setCompressionLastN(context: Context, value: Int) = configRepo.setCompressionLastN(value)
    fun getCompressionChunkSize(context: Context): Int = configRepo.getCompressionChunkSize()
    fun setCompressionChunkSize(context: Context, value: Int) = configRepo.setCompressionChunkSize(value)

    // ========== Agent 模式 ==========

    fun isAgentMode(context: Context): Boolean = configRepo.isAgentMode()
    fun setAgentMode(context: Context, enabled: Boolean) = configRepo.setAgentMode(enabled)
    fun getWorkspacePath(context: Context): String = configRepo.getWorkspacePath()
    fun setWorkspacePath(context: Context, path: String) = configRepo.setWorkspacePath(path)
    fun getWorkspaceUri(context: Context): String = configRepo.getWorkspaceUri()
    fun setWorkspaceUri(context: Context, uri: String) = configRepo.setWorkspaceUri(uri)
    fun getLastConversationId(context: Context): String = configRepo.getLastConversationId()
    fun setLastConversationId(context: Context, id: String) = configRepo.setLastConversationId(id)
    fun hasLastSession(context: Context): Boolean = configRepo.hasLastSession()
    fun setLastSession(context: Context, active: Boolean) = configRepo.setLastSession(active)
}
