package com.lhzkml.jasmine.core.config

import com.lhzkml.jasmine.core.agent.tools.ShellPolicy
import com.lhzkml.jasmine.core.agent.observe.event.EventCategory
import com.lhzkml.jasmine.core.agent.observe.snapshot.RollbackStrategy
import com.lhzkml.jasmine.core.agent.observe.trace.TraceEventCategory
import com.lhzkml.jasmine.core.prompt.executor.ApiType
import com.lhzkml.jasmine.core.prompt.llm.CompressionStrategyType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ProviderRegistryTest {

    private lateinit var configRepo: FakeConfigRepository
    private lateinit var registry: ProviderRegistry

    @Before
    fun setUp() {
        configRepo = FakeConfigRepository()
        registry = ProviderRegistry(configRepo)
    }

    @Test
    fun `has 5 built-in providers`() {
        assertEquals(5, registry.providers.size)
        assertNotNull(registry.getProvider("openai"))
        assertNotNull(registry.getProvider("claude"))
        assertNotNull(registry.getProvider("gemini"))
        assertNotNull(registry.getProvider("deepseek"))
        assertNotNull(registry.getProvider("siliconflow"))
    }

    @Test
    fun `registerProvider adds new provider`() {
        val custom = ProviderConfig("custom1", "Custom", "https://custom.api", "model-1", ApiType.OPENAI, isCustom = true)
        assertTrue(registry.registerProvider(custom))
        assertEquals(6, registry.providers.size)
        assertEquals("Custom", registry.getProvider("custom1")!!.name)
    }

    @Test
    fun `registerProvider rejects duplicate id`() {
        assertFalse(registry.registerProvider(
            ProviderConfig("openai", "Dup", "url", "m", ApiType.OPENAI)
        ))
        assertEquals(5, registry.providers.size)
    }

    @Test
    fun `unregisterProvider removes provider`() {
        registry.registerProvider(
            ProviderConfig("custom1", "Custom", "url", "m", ApiType.OPENAI, isCustom = true)
        )
        assertTrue(registry.unregisterProvider("custom1"))
        assertNull(registry.getProvider("custom1"))
    }

    @Test
    fun `unregisterProvider returns false for unknown`() {
        assertFalse(registry.unregisterProvider("nonexistent"))
    }

    @Test
    fun `initialize loads custom providers from config`() {
        configRepo.customProviders = listOf(
            ProviderConfig("saved1", "Saved", "url", "m", ApiType.OPENAI, isCustom = true)
        )
        registry.initialize()
        assertEquals(6, registry.providers.size)
        assertNotNull(registry.getProvider("saved1"))
    }

    @Test
    fun `initialize is idempotent`() {
        configRepo.customProviders = listOf(
            ProviderConfig("saved1", "Saved", "url", "m", ApiType.OPENAI, isCustom = true)
        )
        registry.initialize()
        registry.initialize()
        assertEquals(6, registry.providers.size)
    }

    @Test
    fun `getBaseUrl returns saved value or default`() {
        configRepo.baseUrls["openai"] = "https://custom-openai.com"
        assertEquals("https://custom-openai.com", registry.getBaseUrl("openai"))
        // For provider with no saved URL, returns default
        assertEquals("https://api.anthropic.com", registry.getBaseUrl("claude"))
    }

    @Test
    fun `getModel returns saved value or default`() {
        configRepo.models["openai"] = "gpt-4o"
        assertEquals("gpt-4o", registry.getModel("openai"))
    }

    @Test
    fun `getActiveConfig returns null when no active provider`() {
        assertNull(registry.getActiveConfig())
    }

    @Test
    fun `getActiveConfig returns config for active provider`() {
        configRepo.setActive("openai")
        configRepo.apiKeys["openai"] = "sk-test"
        configRepo.baseUrls["openai"] = "https://api.openai.com"
        configRepo.models["openai"] = "gpt-4"

        val config = registry.getActiveConfig()
        assertNotNull(config)
        assertEquals("openai", config!!.providerId)
        assertEquals("sk-test", config.apiKey)
        assertEquals("gpt-4", config.model)
        assertEquals(ApiType.OPENAI, config.apiType)
    }

    @Test
    fun `getActiveConfig returns null when no api key`() {
        configRepo.setActive("openai")
        // No API key set
        assertNull(registry.getActiveConfig())
    }
}

/**
 * Minimal fake ConfigRepository for ProviderRegistry tests
 */
private class FakeConfigRepository : ConfigRepository {
    private var _activeProviderId: String? = null
    var apiKeys = mutableMapOf<String, String>()
    var baseUrls = mutableMapOf<String, String>()
    var models = mutableMapOf<String, String>()
    var customProviders = listOf<ProviderConfig>()

    fun setActive(id: String?) { _activeProviderId = id }

    override fun getActiveProviderId() = _activeProviderId
    override fun setActiveProviderId(id: String) { _activeProviderId = id }
    override fun getApiKey(providerId: String) = apiKeys[providerId]
    override fun saveProviderCredentials(providerId: String, apiKey: String, baseUrl: String?, model: String?) {}
    override fun getBaseUrl(providerId: String) = baseUrls[providerId] ?: ""
    override fun getModel(providerId: String) = models[providerId] ?: ""
    override fun getSelectedModels(providerId: String) = emptyList<String>()
    override fun setSelectedModels(providerId: String, models: List<String>) {}
    override fun getChatPath(providerId: String): String? = null
    override fun saveChatPath(providerId: String, path: String) {}
    override fun isVertexAIEnabled(providerId: String) = false
    override fun setVertexAIEnabled(providerId: String, enabled: Boolean) {}
    override fun getVertexProjectId(providerId: String) = ""
    override fun setVertexProjectId(providerId: String, projectId: String) {}
    override fun getVertexLocation(providerId: String) = "global"
    override fun setVertexLocation(providerId: String, location: String) {}
    override fun getVertexServiceAccountJson(providerId: String) = ""
    override fun setVertexServiceAccountJson(providerId: String, json: String) {}
    override fun loadCustomProviders() = customProviders
    override fun saveCustomProviders(providers: List<ProviderConfig>) { customProviders = providers }
    override fun getDefaultSystemPrompt() = ""
    override fun setDefaultSystemPrompt(prompt: String) {}
    override fun getMaxTokens() = 4096
    override fun setMaxTokens(maxTokens: Int) {}
    override fun getTemperature() = 0.7f
    override fun setTemperature(value: Float) {}
    override fun getTopP() = 1.0f
    override fun setTopP(value: Float) {}
    override fun getTopK() = 40
    override fun setTopK(value: Int) {}
    override fun getRequestTimeout() = 60
    override fun setRequestTimeout(seconds: Int) {}
    override fun getSocketTimeout() = 60
    override fun setSocketTimeout(seconds: Int) {}
    override fun getConnectTimeout() = 30
    override fun setConnectTimeout(seconds: Int) {}
    override fun isStreamResumeEnabled() = false
    override fun setStreamResumeEnabled(enabled: Boolean) {}
    override fun getStreamResumeMaxRetries() = 3
    override fun setStreamResumeMaxRetries(value: Int) {}
    override fun isToolsEnabled() = true
    override fun setToolsEnabled(enabled: Boolean) {}
    override fun getEnabledTools() = emptySet<String>()
    override fun setEnabledTools(tools: Set<String>) {}
    override fun getAgentToolPreset() = emptySet<String>()
    override fun setAgentToolPreset(tools: Set<String>) {}
    override fun getBrightDataKey() = ""
    override fun setBrightDataKey(key: String) {}
    override fun getShellPolicy() = ShellPolicy.MANUAL
    override fun setShellPolicy(policy: ShellPolicy) {}
    override fun getShellBlacklist() = emptyList<String>()
    override fun setShellBlacklist(list: List<String>) {}
    override fun getShellWhitelist() = emptyList<String>()
    override fun setShellWhitelist(list: List<String>) {}
    override fun isMcpEnabled() = false
    override fun setMcpEnabled(enabled: Boolean) {}
    override fun getMcpServers() = emptyList<McpServerConfig>()
    override fun setMcpServers(servers: List<McpServerConfig>) {}
    override fun addMcpServer(server: McpServerConfig) {}
    override fun removeMcpServer(index: Int) {}
    override fun updateMcpServer(index: Int, server: McpServerConfig) {}
    override fun getAgentStrategy() = AgentStrategyType.SIMPLE_LOOP
    override fun setAgentStrategy(strategy: AgentStrategyType) {}
    override fun getGraphToolCallMode() = GraphToolCallMode.SEQUENTIAL
    override fun setGraphToolCallMode(mode: GraphToolCallMode) {}
    override fun getToolSelectionStrategy() = ToolSelectionStrategyType.ALL
    override fun setToolSelectionStrategy(strategy: ToolSelectionStrategyType) {}
    override fun getToolSelectionNames() = emptySet<String>()
    override fun setToolSelectionNames(names: Set<String>) {}
    override fun getToolSelectionTaskDesc() = ""
    override fun setToolSelectionTaskDesc(desc: String) {}
    override fun getToolChoiceMode() = ToolChoiceMode.DEFAULT
    override fun setToolChoiceMode(mode: ToolChoiceMode) {}
    override fun getToolChoiceNamedTool() = ""
    override fun setToolChoiceNamedTool(name: String) {}
    override fun isTraceEnabled() = false
    override fun setTraceEnabled(enabled: Boolean) {}
    override fun isTraceFileEnabled() = false
    override fun setTraceFileEnabled(enabled: Boolean) {}
    override fun getTraceEventFilter() = emptySet<TraceEventCategory>()
    override fun setTraceEventFilter(categories: Set<TraceEventCategory>) {}
    override fun isPlannerEnabled() = false
    override fun setPlannerEnabled(enabled: Boolean) {}
    override fun getPlannerMaxIterations() = 50
    override fun setPlannerMaxIterations(value: Int) {}
    override fun isPlannerCriticEnabled() = false
    override fun setPlannerCriticEnabled(enabled: Boolean) {}
    override fun isSnapshotEnabled() = false
    override fun setSnapshotEnabled(enabled: Boolean) {}
    override fun getSnapshotStorage() = SnapshotStorageType.MEMORY
    override fun setSnapshotStorage(storage: SnapshotStorageType) {}
    override fun isSnapshotAutoCheckpoint() = true
    override fun setSnapshotAutoCheckpoint(enabled: Boolean) {}
    override fun getSnapshotRollbackStrategy() = RollbackStrategy.RESTART_FROM_NODE
    override fun setSnapshotRollbackStrategy(strategy: RollbackStrategy) {}
    override fun isEventHandlerEnabled() = false
    override fun setEventHandlerEnabled(enabled: Boolean) {}
    override fun getEventHandlerFilter() = emptySet<EventCategory>()
    override fun setEventHandlerFilter(categories: Set<EventCategory>) {}
    override fun isCompressionEnabled() = false
    override fun setCompressionEnabled(enabled: Boolean) {}
    override fun getCompressionStrategy() = CompressionStrategyType.WHOLE_HISTORY
    override fun setCompressionStrategy(strategy: CompressionStrategyType) {}
    override fun getCompressionMaxTokens() = 2000
    override fun setCompressionMaxTokens(value: Int) {}
    override fun getCompressionThreshold() = 10
    override fun setCompressionThreshold(value: Int) {}
    override fun getCompressionLastN() = 4
    override fun setCompressionLastN(value: Int) {}
    override fun getCompressionChunkSize() = 5
    override fun setCompressionChunkSize(value: Int) {}
    override fun isAgentMode() = false
    override fun setAgentMode(enabled: Boolean) {}
    override fun getWorkspacePath() = ""
    override fun setWorkspacePath(path: String) {}
    override fun getWorkspaceUri() = ""
    override fun setWorkspaceUri(uri: String) {}
    override fun getLastConversationId() = ""
    override fun setLastConversationId(id: String) {}
    override fun hasLastSession() = false
    override fun setLastSession(active: Boolean) {}
}
