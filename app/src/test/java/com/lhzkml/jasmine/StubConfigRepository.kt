package com.lhzkml.jasmine

import com.lhzkml.jasmine.core.agent.tools.ShellPolicy
import com.lhzkml.jasmine.core.agent.tools.event.EventCategory
import com.lhzkml.jasmine.core.agent.tools.snapshot.RollbackStrategy
import com.lhzkml.jasmine.core.agent.tools.trace.TraceEventCategory
import com.lhzkml.jasmine.core.config.*
import com.lhzkml.jasmine.core.prompt.llm.CompressionStrategyType

/**
 * 纯内存 ConfigRepository 实现，用于单元测试
 */
class StubConfigRepository : ConfigRepository {
    override fun getActiveProviderId(): String? = null
    override fun setActiveProviderId(id: String) {}
    override fun getApiKey(providerId: String): String? = null
    override fun saveProviderCredentials(providerId: String, apiKey: String, baseUrl: String?, model: String?) {}
    override fun getBaseUrl(providerId: String): String = ""
    override fun getModel(providerId: String): String = ""
    override fun getSelectedModels(providerId: String): List<String> = emptyList()
    override fun setSelectedModels(providerId: String, models: List<String>) {}
    override fun getChatPath(providerId: String): String? = null
    override fun saveChatPath(providerId: String, path: String) {}
    override fun isVertexAIEnabled(providerId: String): Boolean = false
    override fun setVertexAIEnabled(providerId: String, enabled: Boolean) {}
    override fun getVertexProjectId(providerId: String): String = ""
    override fun setVertexProjectId(providerId: String, projectId: String) {}
    override fun getVertexLocation(providerId: String): String = "global"
    override fun setVertexLocation(providerId: String, location: String) {}
    override fun getVertexServiceAccountJson(providerId: String): String = ""
    override fun setVertexServiceAccountJson(providerId: String, json: String) {}
    override fun loadCustomProviders(): List<ProviderConfig> = emptyList()
    override fun saveCustomProviders(providers: List<ProviderConfig>) {}
    override fun getDefaultSystemPrompt(): String = ""
    override fun setDefaultSystemPrompt(prompt: String) {}
    override fun getMaxTokens(): Int = 4096
    override fun setMaxTokens(maxTokens: Int) {}
    override fun getTemperature(): Float = 0.7f
    override fun setTemperature(value: Float) {}
    override fun getTopP(): Float = 1.0f
    override fun setTopP(value: Float) {}
    override fun getTopK(): Int = 40
    override fun setTopK(value: Int) {}
    override fun getRequestTimeout(): Int = 60
    override fun setRequestTimeout(seconds: Int) {}
    override fun getSocketTimeout(): Int = 60
    override fun setSocketTimeout(seconds: Int) {}
    override fun getConnectTimeout(): Int = 30
    override fun setConnectTimeout(seconds: Int) {}
    override fun isStreamResumeEnabled(): Boolean = false
    override fun setStreamResumeEnabled(enabled: Boolean) {}
    override fun getStreamResumeMaxRetries(): Int = 3
    override fun setStreamResumeMaxRetries(value: Int) {}
    override fun isToolsEnabled(): Boolean = false
    override fun setToolsEnabled(enabled: Boolean) {}
    override fun getEnabledTools(): Set<String> = emptySet()
    override fun setEnabledTools(tools: Set<String>) {}
    override fun getAgentToolPreset(): Set<String> = emptySet()
    override fun setAgentToolPreset(tools: Set<String>) {}
    override fun getBrightDataKey(): String = ""
    override fun setBrightDataKey(key: String) {}
    override fun getShellPolicy(): ShellPolicy = ShellPolicy.MANUAL
    override fun setShellPolicy(policy: ShellPolicy) {}
    override fun getShellBlacklist(): List<String> = emptyList()
    override fun setShellBlacklist(list: List<String>) {}
    override fun getShellWhitelist(): List<String> = emptyList()
    override fun setShellWhitelist(list: List<String>) {}
    override fun isMcpEnabled(): Boolean = false
    override fun setMcpEnabled(enabled: Boolean) {}
    override fun getMcpServers(): List<McpServerConfig> = emptyList()
    override fun setMcpServers(servers: List<McpServerConfig>) {}
    override fun addMcpServer(server: McpServerConfig) {}
    override fun removeMcpServer(index: Int) {}
    override fun updateMcpServer(index: Int, server: McpServerConfig) {}
    override fun getAgentStrategy(): AgentStrategyType = AgentStrategyType.SIMPLE_LOOP
    override fun setAgentStrategy(strategy: AgentStrategyType) {}
    override fun getGraphToolCallMode(): GraphToolCallMode = GraphToolCallMode.SEQUENTIAL
    override fun setGraphToolCallMode(mode: GraphToolCallMode) {}
    override fun getToolSelectionStrategy(): ToolSelectionStrategyType = ToolSelectionStrategyType.ALL
    override fun setToolSelectionStrategy(strategy: ToolSelectionStrategyType) {}
    override fun getToolSelectionNames(): Set<String> = emptySet()
    override fun setToolSelectionNames(names: Set<String>) {}
    override fun getToolSelectionTaskDesc(): String = ""
    override fun setToolSelectionTaskDesc(desc: String) {}
    override fun getToolChoiceMode(): ToolChoiceMode = ToolChoiceMode.AUTO
    override fun setToolChoiceMode(mode: ToolChoiceMode) {}
    override fun getToolChoiceNamedTool(): String = ""
    override fun setToolChoiceNamedTool(name: String) {}
    override fun isTraceEnabled(): Boolean = false
    override fun setTraceEnabled(enabled: Boolean) {}
    override fun isTraceFileEnabled(): Boolean = false
    override fun setTraceFileEnabled(enabled: Boolean) {}
    override fun getTraceEventFilter(): Set<TraceEventCategory> = emptySet()
    override fun setTraceEventFilter(categories: Set<TraceEventCategory>) {}
    override fun isPlannerEnabled(): Boolean = false
    override fun setPlannerEnabled(enabled: Boolean) {}
    override fun getPlannerMaxIterations(): Int = 5
    override fun setPlannerMaxIterations(value: Int) {}
    override fun isPlannerCriticEnabled(): Boolean = false
    override fun setPlannerCriticEnabled(enabled: Boolean) {}
    override fun isSnapshotEnabled(): Boolean = false
    override fun setSnapshotEnabled(enabled: Boolean) {}
    override fun getSnapshotStorage(): SnapshotStorageType = SnapshotStorageType.MEMORY
    override fun setSnapshotStorage(storage: SnapshotStorageType) {}
    override fun isSnapshotAutoCheckpoint(): Boolean = false
    override fun setSnapshotAutoCheckpoint(enabled: Boolean) {}
    override fun getSnapshotRollbackStrategy(): RollbackStrategy = RollbackStrategy.RESTART_FROM_NODE
    override fun setSnapshotRollbackStrategy(strategy: RollbackStrategy) {}
    override fun isEventHandlerEnabled(): Boolean = false
    override fun setEventHandlerEnabled(enabled: Boolean) {}
    override fun getEventHandlerFilter(): Set<EventCategory> = emptySet()
    override fun setEventHandlerFilter(categories: Set<EventCategory>) {}
    override fun isCompressionEnabled(): Boolean = false
    override fun setCompressionEnabled(enabled: Boolean) {}
    override fun getCompressionStrategy(): CompressionStrategyType = CompressionStrategyType.WHOLE_HISTORY
    override fun setCompressionStrategy(strategy: CompressionStrategyType) {}
    override fun getCompressionMaxTokens(): Int = 4096
    override fun setCompressionMaxTokens(value: Int) {}
    override fun getCompressionThreshold(): Int = 75
    override fun setCompressionThreshold(value: Int) {}
    override fun getCompressionLastN(): Int = 4
    override fun setCompressionLastN(value: Int) {}
    override fun getCompressionChunkSize(): Int = 3
    override fun setCompressionChunkSize(value: Int) {}
    override fun isAgentMode(): Boolean = false
    override fun setAgentMode(enabled: Boolean) {}
    override fun getWorkspacePath(): String = ""
    override fun setWorkspacePath(path: String) {}
    override fun getWorkspaceUri(): String = ""
    override fun setWorkspaceUri(uri: String) {}
    override fun getLastConversationId(): String = ""
    override fun setLastConversationId(id: String) {}
    override fun hasLastSession(): Boolean = false
    override fun setLastSession(active: Boolean) {}
}
