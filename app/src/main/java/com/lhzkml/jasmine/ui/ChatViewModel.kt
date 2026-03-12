package com.lhzkml.jasmine.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lhzkml.jasmine.ChatExecutor
import com.lhzkml.jasmine.ChatExecutorConfig
import com.lhzkml.jasmine.ChatItem
import com.lhzkml.jasmine.ChatStopSignal
import com.lhzkml.jasmine.ChatStateManager
import com.lhzkml.jasmine.CheckpointRecovery
import com.lhzkml.jasmine.ContentBlock
import com.lhzkml.jasmine.DialogHandlers
import com.lhzkml.jasmine.core.agent.observe.event.EventHandler
import com.lhzkml.jasmine.core.agent.observe.snapshot.Persistence
import com.lhzkml.jasmine.core.agent.observe.trace.Tracing
import com.lhzkml.jasmine.core.agent.runtime.AgentRuntimeBuilder
import com.lhzkml.jasmine.core.agent.runtime.CompressionStrategyBuilder
import com.lhzkml.jasmine.core.agent.runtime.McpConnectionManager
import com.lhzkml.jasmine.core.agent.runtime.ToolRegistryBuilder
import com.lhzkml.jasmine.core.agent.tools.ToolRegistry
import com.lhzkml.jasmine.core.config.ActiveProviderConfig
import com.lhzkml.jasmine.core.conversation.storage.ConversationInfo
import com.lhzkml.jasmine.core.conversation.storage.ConversationRepository
import com.lhzkml.jasmine.core.prompt.executor.ApiType
import com.lhzkml.jasmine.core.prompt.executor.ChatClientConfig
import com.lhzkml.jasmine.core.prompt.executor.ChatClientFactory
import com.lhzkml.jasmine.core.prompt.llm.ChatClient
import com.lhzkml.jasmine.core.prompt.llm.ChatClientRouter
import com.lhzkml.jasmine.core.prompt.llm.CompressionEventListener
import com.lhzkml.jasmine.core.prompt.llm.ContextManager
import com.lhzkml.jasmine.core.prompt.llm.HistoryCompressionStrategy
import com.lhzkml.jasmine.core.prompt.llm.LLMWriteSession
import com.lhzkml.jasmine.core.prompt.llm.ModelRegistry
import com.lhzkml.jasmine.core.prompt.llm.SystemContextCollector
import com.lhzkml.jasmine.core.prompt.llm.SystemContextProvider
import com.lhzkml.jasmine.core.prompt.llm.replaceHistoryWithTLDR
import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.Prompt
import com.lhzkml.jasmine.core.agent.tools.WebSearchTool
import com.lhzkml.jasmine.core.agent.tools.FetchUrlTool
import com.lhzkml.jasmine.core.config.ConfigRepository
import com.lhzkml.jasmine.mnn.MnnChatClient
import com.lhzkml.jasmine.mnn.MnnModelManager
import com.lhzkml.jasmine.RagStore
import com.lhzkml.jasmine.StreamUpdate
import com.lhzkml.jasmine.core.rag.RagConfig
import com.lhzkml.jasmine.wakelock.WakeLockManager
import com.lhzkml.jasmine.wakelock.WakeLockStateListener
import com.lhzkml.jasmine.wakelock.BatteryOptimizationHelper
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class ChatViewModel(
    application: Application,
    private val conversationRepo: ConversationRepository,
    private val sessionRepository: com.lhzkml.jasmine.repository.SessionRepository,
    private val providerRepository: com.lhzkml.jasmine.repository.ProviderRepository,
    private val modelSelectionRepository: com.lhzkml.jasmine.repository.ModelSelectionRepository,
    private val llmSettingsRepository: com.lhzkml.jasmine.repository.LlmSettingsRepository,
    private val timeoutSettingsRepository: com.lhzkml.jasmine.repository.TimeoutSettingsRepository,
    private val toolSettingsRepository: com.lhzkml.jasmine.repository.ToolSettingsRepository,
    private val agentStrategyRepository: com.lhzkml.jasmine.repository.AgentStrategyRepository,
    private val ragConfigRepository: com.lhzkml.jasmine.repository.RagConfigRepository,
    private val mcpRepository: com.lhzkml.jasmine.repository.McpRepository,
    private val compressionSettingsRepository: com.lhzkml.jasmine.repository.CompressionSettingsRepository,
    private val snapshotSettingsRepository: com.lhzkml.jasmine.repository.SnapshotSettingsRepository,
    private val plannerSettingsRepository: com.lhzkml.jasmine.repository.PlannerSettingsRepository,
    private val checkpointRepository: com.lhzkml.jasmine.repository.CheckpointRepository,
    private val configRepo: ConfigRepository
) : AndroidViewModel(application) {

    private val ctx: Context get() = getApplication()

    // ── 统一 UI 状态 ──────────────────────────────────────────────
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /**
     * ChatStateManager 内部使用 mutableStateListOf，在流式输出时高频更新。
     * 这是消息列表的唯一数据源（Single Source of Truth），UI 直接观察此列表。
     */
    val chatItems = mutableStateListOf<ChatItem>()

    lateinit var chatStateManager: ChatStateManager
        private set

    // ── 内部基础设施 ──────────────────────────────────────────────
    private val clientRouter = ChatClientRouter()
    private var currentProviderId: String? = null
    private var overrideModel: String? = null
    private var currentConversationId: String? = null
    val messageHistory = mutableListOf<ChatMessage>()
    private var contextManager = ContextManager()
    private var webSearchTool: WebSearchTool? = null
    private var fetchUrlTool: FetchUrlTool? = null
    private var currentJob: Job? = null
    private var conversationObserverJob: Job? = null
    private var tracing: Tracing? = null
    private var eventHandler: EventHandler? = null
    private var persistence: Persistence? = null
    private var contextCollector = SystemContextCollector()
    private val runtimeBuilder = AgentRuntimeBuilder(configRepo)
    private val toolRegistryBuilder = ToolRegistryBuilder(configRepo)
    private val mcpConnectionManager get() = mcpRepository.getConnectionManager()

    private var currentLocalModelId: String? = null
    /** Channel 模式下持有当前 executor，供 savePartial 获取 getLogContent */
    private var activeChatExecutor: ChatExecutor? = null

    // ── WakeLock 管理 ─────────────────────────────────────────────
    private val wakeLockManager = WakeLockManager(ctx)
    private val wakeLockListener = object : WakeLockStateListener {
        override fun onWakeLockStateChanged(isHeld: Boolean) {
            _uiState.update { it.copy(wakeLockHeld = isHeld) }
        }
    }

    interface LifecycleCallbacks {
        fun finishAndLaunch(intent: Intent)
    }

    private var lifecycleCallbacks: LifecycleCallbacks? = null

    // ── 公开 API ─────────────────────────────────────────────────

    fun onEvent(event: ChatUiEvent) {
        when (event) {
            is ChatUiEvent.SendMessage -> sendMessage(event.text)
            is ChatUiEvent.StopGeneration -> stopGenerating()
            is ChatUiEvent.SelectModel -> selectModel(event.model)
            is ChatUiEvent.SetThinkingMode -> setThinkingMode(event.enabled)
            is ChatUiEvent.LoadConversation -> loadConversation(event.id)
            is ChatUiEvent.NewConversation -> startNewConversation()
            is ChatUiEvent.DeleteConversation -> deleteConversation(event.info)
            is ChatUiEvent.CloseWorkspace -> closeWorkspace()
            is ChatUiEvent.ToggleWakeLock -> toggleWakeLock()
            is ChatUiEvent.RequestBatteryOptimization -> requestBatteryOptimization()
            is ChatUiEvent.OpenSettings -> _uiState.update { it.copy(navigationEvent = NavigationEvent.Settings) }
            is ChatUiEvent.OpenDrawerEnd -> _uiState.update { it.copy(requestOpenDrawerEnd = true) }
            is ChatUiEvent.OpenDrawerStart -> _uiState.update { it.copy(requestOpenDrawerStart = true) }
            is ChatUiEvent.ClearDrawerRequestEnd -> _uiState.update { it.copy(requestOpenDrawerEnd = false) }
            is ChatUiEvent.ClearDrawerRequestStart -> _uiState.update { it.copy(requestOpenDrawerStart = false) }
            is ChatUiEvent.UserScrolledUp -> _uiState.update { it.copy(userScrolledUp = event.scrolledUp) }
            is ChatUiEvent.ClearNavigationEvent -> _uiState.update { it.copy(navigationEvent = null) }
            is ChatUiEvent.ClearToastMessage -> _uiState.update { it.copy(toastMessage = null) }
        }
    }

    fun initialize(context: Context, callbacks: LifecycleCallbacks) {
        lifecycleCallbacks = callbacks

        chatStateManager = ChatStateManager(chatItems) { requestScrollToBottom() }
        
        // 初始化 WakeLock 监听器
        wakeLockManager.addListener(wakeLockListener)

        refreshAgentModeUI()
        refreshModelSelector()
        subscribeConversations()

        val intentConvId = (context as? android.app.Activity)?.intent
            ?.getStringExtra("conversation_id")
        if (intentConvId != null) {
            loadConversation(intentConvId)
        } else {
            val lastId = sessionRepository.getLastConversationId()
            if (!lastId.isNullOrEmpty()) {
                val currentWs = if (sessionRepository.isAgentMode())
                    sessionRepository.getWorkspacePath() else ""
                viewModelScope.launch(Dispatchers.IO) {
                    val info = conversationRepo.getConversation(lastId)
                    if (info != null && info.workspacePath == currentWs) {
                        withContext(Dispatchers.Main) { loadConversation(lastId) }
                    }
                }
            }
        }

        preconnectMcpServers()
    }

    fun handleNewIntent(intent: Intent) {
        intent.getStringExtra("conversation_id")?.let { loadConversation(it) }
    }

    fun onResume() {
        refreshAgentModeUI()
        refreshModelSelector()
        // 修复问题 2：每次从设置页面返回时刷新模型选择器
        // 这样可以确保在设置页面选择的模型能立即显示在聊天页面
    }

    fun onPause() {
        savePartialIfGenerating()
        sessionRepository.setLastConversationId(currentConversationId)
    }

    override fun onCleared() {
        super.onCleared()
        wakeLockManager.cleanup()
        clientRouter.close()
        webSearchTool?.close()
        fetchUrlTool?.close()
        tracing?.close()
        mcpConnectionManager.close()
        lifecycleCallbacks = null
    }

    // ── WakeLock 相关方法 ─────────────────────────────────────────

    private fun toggleWakeLock() {
        if (!wakeLockManager.isHeld) {
            // 首次获取时检查电池优化
            if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(ctx)) {
                _uiState.update { 
                    it.copy(
                        showBatteryOptimizationDialog = true,
                        toastMessage = "建议豁免电池优化以保证后台任务稳定运行"
                    ) 
                }
            }
        }
        wakeLockManager.toggle()
    }

    private fun requestBatteryOptimization() {
        BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(ctx)
        _uiState.update { it.copy(showBatteryOptimizationDialog = false, toastMessage = null) }
    }

    fun shortenModelName(model: String): String = model.substringAfterLast("/")

    // ── 私有实现 ─────────────────────────────────────────────────

    private fun requestScrollToBottom() {
        if (_uiState.value.userScrolledUp) return
        _uiState.update { it.copy(scrollToBottomTrigger = it.scrollToBottomTrigger + 1) }
    }

    private fun showToast(message: String) {
        _uiState.update { it.copy(toastMessage = message) }
    }

    private suspend fun showCheckpointRecoveryDialog(title: String, message: String, labels: List<String>): Int? {
        return suspendCancellableCoroutine { cont ->
            _uiState.update {
                it.copy(checkpointRecoveryDialog = CheckpointRecoveryDialogState(title, message, labels) { index ->
                    _uiState.update { s -> s.copy(checkpointRecoveryDialog = null) }
                    cont.resume(index)
                })
            }
        }
    }

    private suspend fun showStartupRecoveryDialog(title: String, message: String): Boolean {
        return suspendCancellableCoroutine { cont ->
            _uiState.update {
                it.copy(startupRecoveryDialog = StartupRecoveryDialogState(title, message) { yes ->
                    _uiState.update { s -> s.copy(startupRecoveryDialog = null) }
                    cont.resume(yes)
                })
            }
        }
    }

    private fun refreshContextCollector() {
        val isAgent = sessionRepository.isAgentMode()
        val wsPath = sessionRepository.getWorkspacePath()
        val activeId = providerRepository.getActiveProviderId()
        val modelName = if (activeId != null) {
            overrideModel ?: providerRepository.getModel(activeId)
        } else ""
        val additionalProviders: List<SystemContextProvider> = buildList {
            val ragProvider = RagStore.buildRagContextProvider {
                val rawActive = ragConfigRepository.getRagActiveLibraryIds()
                val libs = ragConfigRepository.getRagLibraries()
                val effectiveActive = if (rawActive.isEmpty() && libs.isNotEmpty()) libs.map { it.id }.toSet() else rawActive
                RagConfig(
                    enabled = ragConfigRepository.isRagEnabled(),
                    topK = ragConfigRepository.getRagTopK(),
                    embeddingBaseUrl = ragConfigRepository.getRagEmbeddingBaseUrl(),
                    embeddingApiKey = ragConfigRepository.getRagEmbeddingApiKey(),
                    embeddingModel = ragConfigRepository.getRagEmbeddingModel(),
                    useLocalEmbedding = ragConfigRepository.getRagEmbeddingUseLocal(),
                    embeddingModelPath = ragConfigRepository.getRagEmbeddingModelPath(),
                    activeLibraryIds = effectiveActive
                )
            }
            if (ragProvider != null) add(ragProvider)
        }
        contextCollector = runtimeBuilder.buildSystemContext(
            isAgentMode = isAgent, workspacePath = wsPath, modelName = modelName,
            additionalProviders = additionalProviders
        )
    }

    private fun buildToolRegistry(client: ChatClient, model: String): ToolRegistry {
        toolRegistryBuilder.workspacePath = sessionRepository.getWorkspacePath()
        toolRegistryBuilder.fallbackBasePath = ctx.getExternalFilesDir(null)?.absolutePath
        DialogHandlers.register(_uiState, toolRegistryBuilder)
        toolRegistryBuilder.subAgentClientProvider = { client }
        toolRegistryBuilder.subAgentModelProvider = { model }
        toolRegistryBuilder.subAgentEventListener = object : com.lhzkml.jasmine.core.agent.tools.AgentEventListener {
            override suspend fun onToolCallStart(toolName: String, arguments: String) {
                withContext(Dispatchers.Main) {
                    chatStateManager.handleToolCall(toolName, arguments)
                }
            }
            override suspend fun onToolCallResult(toolName: String, result: String) {
                withContext(Dispatchers.Main) {
                    chatStateManager.handleToolResult(toolName, result)
                }
            }
            override suspend fun onThinking(content: String) {
                withContext(Dispatchers.Main) {
                    chatStateManager.handleThinking(content)
                }
            }
        }
        toolRegistryBuilder.onSubAgentStart = { purpose, type ->
            withContext(Dispatchers.Main) {
                chatStateManager.handleSubAgentStart(purpose, type)
            }
        }
        toolRegistryBuilder.onSubAgentResult = { purpose, result ->
            withContext(Dispatchers.Main) {
                chatStateManager.handleSubAgentResult(purpose, result)
            }
        }
        return toolRegistryBuilder.build(sessionRepository.isAgentMode())
    }

    private fun preconnectMcpServers() {
        mcpConnectionManager.listener = object : McpConnectionManager.ConnectionListener {
            override fun onConnected(serverName: String, transportType: com.lhzkml.jasmine.core.config.McpTransportType, toolCount: Int) {
                val transportLabel = when (transportType) {
                    com.lhzkml.jasmine.core.config.McpTransportType.STREAMABLE_HTTP -> "HTTP"
                    com.lhzkml.jasmine.core.config.McpTransportType.SSE -> "SSE"
                }
                CoroutineScope(Dispatchers.Main).launch {
                    showToast("MCP: $serverName 已连接 [$transportLabel] ($toolCount 个工具)")
                }
            }
            override fun onConnectionFailed(serverName: String, error: String) {
                CoroutineScope(Dispatchers.Main).launch {
                    showToast("MCP: $serverName 连接失败")
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) { mcpConnectionManager.preconnect() }
    }

    private suspend fun loadMcpToolsInto(registry: ToolRegistry) {
        mcpConnectionManager.loadToolsInto(registry)
    }

    private fun buildTracing(): Tracing? {
        return runtimeBuilder.buildTracing(ctx.getExternalFilesDir("traces"))
    }

    private fun buildEventHandler(emitter: AgentRuntimeBuilder.EventEmitter): EventHandler? {
        return runtimeBuilder.buildEventHandler(emitter)
    }

    private fun buildPersistence(): Persistence? {
        return runtimeBuilder.buildPersistence(ctx.getExternalFilesDir("snapshots"))
    }

    private fun subscribeConversations() {
        conversationObserverJob?.cancel()
        val isAgent = sessionRepository.isAgentMode()
        val wp = if (isAgent) sessionRepository.getWorkspacePath() else ""
        conversationObserverJob = viewModelScope.launch {
            conversationRepo.observeConversationsByWorkspace(wp).collectLatest { list ->
                _uiState.update { it.copy(conversations = list, conversationsEmpty = list.isEmpty()) }
            }
        }
    }

    private fun refreshAgentModeUI() {
        val isAgent = sessionRepository.isAgentMode()
        if (isAgent) {
            val path = sessionRepository.getWorkspacePath()
            _uiState.update {
                it.copy(
                    isAgentMode = true,
                    workspacePath = path,
                    showFileTree = true,
                    workspaceLabel = path.ifEmpty { "未选择工作区" }
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    isAgentMode = false,
                    showFileTree = false,
                    workspaceLabel = "普通聊天",
                    workspacePath = ""
                )
            }
        }
        val currentWs = if (isAgent) sessionRepository.getWorkspacePath() else ""
        if (currentConversationId != null) {
            viewModelScope.launch(Dispatchers.IO) {
                val info = conversationRepo.getConversation(currentConversationId!!)
                if (info == null || info.workspacePath != currentWs) {
                    withContext(Dispatchers.Main) { startNewConversation() }
                }
            }
        }
        subscribeConversations()
    }

    private fun refreshModelSelector() {
        val activeId = providerRepository.getActiveProviderId()
        if (activeId == null) {
            _uiState.update {
                it.copy(currentModelDisplay = "未配置", modelList = emptyList(), currentModel = "", supportsThinkingMode = false)
            }
            return
        }

        val provider = providerRepository.getProvider(activeId)
        if (provider?.apiType == ApiType.LOCAL) {
            val localModels = MnnModelManager.getLocalModels(ctx)
            val localModelIds = localModels.map { it.modelId }
            val model = overrideModel ?: providerRepository.getModel(activeId)
            val selectedModel = if (model.isNotEmpty() && model in localModelIds) model
                else localModelIds.firstOrNull() ?: ""
            if (selectedModel != model && selectedModel.isNotEmpty()) {
                overrideModel = selectedModel
            }
            _uiState.update {
                it.copy(
                    modelList = localModelIds,
                    currentModel = selectedModel,
                    currentModelDisplay = "${shortenModelName(selectedModel).ifEmpty { "请下载模型" }} \u02C7",
                    supportsThinkingMode = MnnModelManager.isSupportThinkingSwitch(ctx, selectedModel),
                    isThinkingModeEnabled = modelSelectionRepository.isThinkingEnabled(selectedModel)
                )
            }
            return
        }

        val model = overrideModel ?: providerRepository.getModel(activeId)
        val selectedModels = providerRepository.getSelectedModels(activeId)
        val list = if (selectedModels.isEmpty()) {
            if (model.isNotEmpty()) listOf(model) else emptyList()
        } else {
            if (model.isNotEmpty() && model !in selectedModels) listOf(model) + selectedModels
            else selectedModels
        }
        _uiState.update {
            it.copy(
                supportsThinkingMode = false,
                currentModel = model,
                currentModelDisplay = "${shortenModelName(model).ifEmpty { "未选择模型" }} \u02C7",
                modelList = list
            )
        }
    }

    private fun selectModel(model: String) {
        val activeId = providerRepository.getActiveProviderId() ?: return
        overrideModel = model
        val key = providerRepository.getApiKey(activeId) ?: ""
        val baseUrl = providerRepository.getBaseUrl(activeId)
        providerRepository.saveProviderCredentials(activeId, key, baseUrl, model)
        refreshModelSelector()
    }

    private fun setThinkingMode(enabled: Boolean) {
        val state = _uiState.value
        if (!state.supportsThinkingMode || state.currentModel.isEmpty()) return
        _uiState.update { it.copy(isThinkingModeEnabled = enabled) }
        modelSelectionRepository.setThinkingEnabled(state.currentModel, enabled)
        val client = clientRouter.getClient(MnnChatClient.PROVIDER_ID) as? MnnChatClient
        client?.updateThinking(enabled)
    }

    private fun closeWorkspace() {
        if (sessionRepository.isAgentMode()) {
            sessionRepository.setLastConversationId(currentConversationId)
            val uriStr = sessionRepository.getWorkspaceUri()
            if (uriStr.isNotEmpty()) {
                try {
                    val uri = android.net.Uri.parse(uriStr)
                    ctx.contentResolver.releasePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: Exception) {}
            }
            sessionRepository.setWorkspacePath("")
            sessionRepository.setWorkspaceUri("")
        }
        sessionRepository.setAgentMode(false)
        sessionRepository.setLastSession(false)
        _uiState.update { it.copy(navigationEvent = NavigationEvent.Launcher) }
    }

    private fun startNewConversation() {
        savePartialIfGenerating()
        currentConversationId = null
        messageHistory.clear()
        chatStateManager.clearAll()
    }

    private fun loadConversation(conversationId: String) {
        savePartialIfGenerating()
        viewModelScope.launch(Dispatchers.IO) {
            val info = conversationRepo.getConversation(conversationId)
            val timedMessages = conversationRepo.getTimedMessages(conversationId)
            val messages = conversationRepo.getMessages(conversationId)
            val usageList = conversationRepo.getUsageList(conversationId)
            withContext(Dispatchers.Main) {
                if (info == null) {
                    showToast("对话不存在")
                    return@withContext
                }
                currentConversationId = conversationId
                messageHistory.clear()
                messageHistory.addAll(messages.filter { it.role != "agent_log" })
                chatStateManager.clearAll()
                var usageIndex = 0
                for (msg in timedMessages) {
                    val time = ChatExecutor.formatTime(msg.createdAt)
                    when (msg.role) {
                        "user" -> chatStateManager.addUserMessage(msg.content, time)
                        "assistant" -> {
                            val usage = usageList.getOrNull(usageIndex)
                            val usageLine = if (usage != null) {
                                "[提示: ${usage.promptTokens} | 回复: ${usage.completionTokens} | 总计: ${usage.totalTokens}]"
                            } else ""
                            val blocks = mutableListOf<ContentBlock>()
                            if (msg.content.contains(ChatExecutor.BLOCK_TEXT_SEPARATOR)) {
                                val parts = msg.content.split(ChatExecutor.BLOCK_TEXT_SEPARATOR, limit = 2)
                                val logPart = parts.getOrElse(0) { "" }
                                val textPart = parts.getOrElse(1) { "" }
                                if (logPart.isNotBlank()) {
                                    blocks.addAll(parseLogBlocks(logPart))
                                }
                                if (textPart.isNotEmpty()) {
                                    blocks.add(ContentBlock.Text(textPart))
                                }
                            } else if (msg.content.isNotEmpty()) {
                                blocks.add(ContentBlock.Text(msg.content))
                            }
                            chatStateManager.addHistoryAiMessage(
                                blocks = blocks,
                                usageLine = usageLine,
                                time = time
                            )
                            usageIndex++
                        }
                    }
                }
                requestScrollToBottom()
            }
            if (snapshotSettingsRepository.isSnapshotEnabled()
                && snapshotSettingsRepository.getSnapshotStorage() == com.lhzkml.jasmine.core.config.SnapshotStorageType.FILE
            ) {
                tryOfferStartupRecovery(conversationId)
            }
        }
    }

    private val logMarkerPrefixes = listOf(
        "[Text] ", "[Think] ", "[Tool] ", "[Result] ",
        "[Plan] ", "[SubAgent] ", "[SubAgent Result] ", "[Goal] "
    )

    private fun isLogMarkerLine(line: String): Boolean =
        logMarkerPrefixes.any { line.startsWith(it) }

    private fun parseLogBlocks(logContent: String): MutableList<ContentBlock> {
        val blocks = mutableListOf<ContentBlock>()
        val lines = logContent.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.startsWith("[Text] ") -> {
                    val textContent = StringBuilder(line.removePrefix("[Text] "))
                    i++
                    while (i < lines.size && !isLogMarkerLine(lines[i])) {
                        textContent.append("\n").append(lines[i])
                        i++
                    }
                    val text = textContent.toString().trimEnd()
                    if (text.isNotEmpty()) blocks.add(ContentBlock.Text(text))
                }
                line.startsWith("[Think] ") -> {
                    val thinkText = StringBuilder(line.removePrefix("[Think] "))
                    i++
                    while (i < lines.size && !isLogMarkerLine(lines[i])) {
                        thinkText.append("\n").append(lines[i])
                        i++
                    }
                    blocks.add(ContentBlock.Thinking(thinkText.toString().trimEnd()))
                }
                line.startsWith("[Tool] 调用工具: ") -> {
                    val toolDesc = line.removePrefix("[Tool] 调用工具: ")
                    val parenIdx = toolDesc.indexOf('(')
                    if (parenIdx > 0) {
                        val name = toolDesc.substring(0, parenIdx)
                        val args = toolDesc.substring(parenIdx + 1).trimEnd(')')
                        blocks.add(ContentBlock.ToolCall(name, args))
                    } else {
                        blocks.add(ContentBlock.ToolCall(toolDesc, ""))
                    }
                    i++
                }
                line.startsWith("[Result] ") -> {
                    val resultPart = line.removePrefix("[Result] ")
                    val colonIdx = resultPart.indexOf(" 结果: ")
                    if (colonIdx > 0) {
                        val name = resultPart.substring(0, colonIdx)
                        val result = resultPart.substring(colonIdx + " 结果: ".length)
                        blocks.add(ContentBlock.ToolResult(name, result))
                    } else {
                        blocks.add(ContentBlock.SystemLog(line))
                    }
                    i++
                }
                line.startsWith("[Plan] 任务规划:") || line.startsWith("[Plan] ") -> {
                    i++
                    var goal = ""
                    val steps = mutableListOf<String>()
                    if (i < lines.size && lines[i].startsWith("[Goal] 目标: ")) {
                        goal = lines[i].removePrefix("[Goal] 目标: ")
                        i++
                    }
                    while (i < lines.size && lines[i].trimStart().matches(Regex("^\\d+\\.\\s+.*"))) {
                        steps.add(lines[i].trimStart().replace(Regex("^\\d+\\.\\s+"), ""))
                        i++
                    }
                    blocks.add(ContentBlock.Plan(goal, steps))
                }
                line.startsWith("[SubAgent] ") -> {
                    val info = line.removePrefix("[SubAgent] ")
                    val typeMatch = Regex("\\(type=(.+?)\\)$").find(info)
                    val type = typeMatch?.groupValues?.getOrNull(1) ?: "general"
                    val purpose = if (typeMatch != null) info.substring(0, typeMatch.range.first).trim() else info
                    blocks.add(ContentBlock.SubAgentStart(purpose, type))
                    i++
                }
                line.startsWith("[SubAgent Result] ") -> {
                    val resultPart = line.removePrefix("[SubAgent Result] ")
                    val colonIdx = resultPart.indexOf(": ")
                    if (colonIdx > 0) {
                        val purpose = resultPart.substring(0, colonIdx)
                        val result = resultPart.substring(colonIdx + 2)
                        blocks.add(ContentBlock.SubAgentResult(purpose, result))
                    } else {
                        blocks.add(ContentBlock.SystemLog(line))
                    }
                    i++
                }
                line.isBlank() -> i++
                else -> {
                    blocks.add(ContentBlock.SystemLog(line))
                    i++
                }
            }
        }
        return blocks
    }

    private fun deleteConversation(info: ConversationInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            conversationRepo.deleteConversation(info.id)
            if (info.id == currentConversationId) {
                withContext(Dispatchers.Main) { startNewConversation() }
            }
        }
    }

    private fun getOrCreateClient(config: ActiveProviderConfig): ChatClient {
        if (config.apiType == ApiType.LOCAL) {
            val modelId = overrideModel ?: config.model
            val existing = clientRouter.getClient(config.providerId)
            if (existing != null && currentProviderId == config.providerId && currentLocalModelId == modelId) {
                return existing
            }
            if (currentProviderId != null) {
                clientRouter.unregister(currentProviderId!!)
            }
            val client = MnnChatClient(ctx, modelId)
            clientRouter.register(config.providerId, client)
            currentProviderId = config.providerId
            currentLocalModelId = modelId
            contextManager = ContextManager()
            return client
        }

        val existing = clientRouter.getClient(config.providerId)
        if (existing != null && currentProviderId == config.providerId) return existing
        if (currentProviderId != null && currentProviderId != config.providerId) {
            clientRouter.unregister(currentProviderId!!)
        }
        currentLocalModelId = null

        val provider = providerRepository.getProvider(config.providerId)
        val clientConfig = ChatClientConfig(
            providerId = config.providerId,
            providerName = provider?.name ?: config.providerId,
            apiKey = config.apiKey,
            baseUrl = config.baseUrl,
            apiType = config.apiType,
            chatPath = config.chatPath,
            vertexEnabled = config.vertexEnabled,
            vertexProjectId = config.vertexProjectId,
            vertexLocation = config.vertexLocation,
            vertexServiceAccountJson = config.vertexServiceAccountJson,
            requestTimeoutMs = timeoutSettingsRepository.getRequestTimeout().toLong() * 1000,
            connectTimeoutMs = timeoutSettingsRepository.getConnectTimeout().toLong() * 1000,
            socketTimeoutMs = timeoutSettingsRepository.getSocketTimeout().toLong() * 1000
        )
        val client = ChatClientFactory.create(clientConfig)
        val llmProvider = client.provider
        val modelMeta = ModelRegistry.find(config.model)
        contextManager = if (modelMeta != null) ContextManager.fromModel(modelMeta)
        else ContextManager.forModel(config.model, llmProvider)

        clientRouter.register(config.providerId, client)
        currentProviderId = config.providerId
        return client
    }

    private fun sendMessage(message: String) {
        val config = providerRepository.getActiveConfig()
        if (config == null) {
            showToast("请先在设置中配置模型供应商")
            _uiState.update { it.copy(navigationEvent = NavigationEvent.Settings) }
            return
        }
        val actualModel = overrideModel ?: config.model
        if (actualModel.isEmpty()) {
            if (config.apiType == ApiType.LOCAL) {
                showToast("请先下载并选择本地模型")
            } else {
                showToast("请先选择模型")
                _uiState.update {
                    it.copy(navigationEvent = NavigationEvent.ProviderConfig(config.providerId, tab = 1))
                }
            }
            return
        }

        _uiState.update { it.copy(isGenerating = true, userScrolledUp = false) }
        ChatStopSignal.reset()
        val now = ChatExecutor.formatTime(System.currentTimeMillis())
        chatStateManager.addUserMessage(message, now)

        val client = getOrCreateClient(config)
        val userMsg = ChatMessage.user(message)

        // I/O 线程分离：Channel 将 StreamUpdate 从 IO 传至主线程，减少 withContext 切换
        val streamChannel = Channel<StreamUpdate>(Channel.UNLIMITED)
        viewModelScope.launch(Dispatchers.Main.immediate) {
            streamChannel.consumeEach { update ->
                chatStateManager.processStreamUpdate(update)
            }
        }

        val checkpointRecovery = CheckpointRecovery(
            activity = ctx,
            chatStateManager = chatStateManager,
            messageHistory = messageHistory,
            conversationRepo = conversationRepo,
            checkpointRepository = checkpointRepository,
            snapshotSettingsRepository = snapshotSettingsRepository,
            llmSettingsRepository = llmSettingsRepository,
            autoScroll = { requestScrollToBottom() },
            sendMessage = { sendMessage(it) },
            showCheckpointRecoveryDialog = { t, m, labels -> showCheckpointRecoveryDialog(t, m, labels) },
            showStartupRecoveryDialog = { t, m -> showStartupRecoveryDialog(t, m) }
        )

        // 构建 ChatExecutor 配置
        val executorConfig = ChatExecutorConfig(
            toolsEnabled = toolSettingsRepository.isToolsEnabled(),
            defaultSystemPrompt = llmSettingsRepository.getDefaultSystemPrompt(),
            maxTokens = llmSettingsRepository.getMaxTokens(),
            temperature = llmSettingsRepository.getTemperature(),
            topP = llmSettingsRepository.getTopP(),
            topK = llmSettingsRepository.getTopK(),
            isAgentMode = sessionRepository.isAgentMode(),
            workspacePath = sessionRepository.getWorkspacePath(),
            compressionEnabled = compressionSettingsRepository.isCompressionEnabled(),
            agentStrategy = agentStrategyRepository.getAgentStrategy(),
            agentMaxIterations = agentStrategyRepository.getAgentMaxIterations(),
            maxToolResultLength = agentStrategyRepository.getMaxToolResultLength(),
            toolChoiceMode = agentStrategyRepository.getToolChoiceMode(),
            toolChoiceNamedTool = agentStrategyRepository.getToolChoiceNamedTool(),
            graphToolCallMode = agentStrategyRepository.getGraphToolCallMode(),
            toolSelectionStrategy = agentStrategyRepository.getToolSelectionStrategy(),
            toolSelectionNames = agentStrategyRepository.getToolSelectionNames(),
            toolSelectionTaskDesc = agentStrategyRepository.getToolSelectionTaskDesc(),
            plannerEnabled = plannerSettingsRepository.isPlannerEnabled(),
            plannerMaxIterations = plannerSettingsRepository.getPlannerMaxIterations(),
            plannerCriticEnabled = plannerSettingsRepository.isPlannerCriticEnabled(),
            streamResumeEnabled = timeoutSettingsRepository.isStreamResumeEnabled(),
            streamResumeMaxRetries = timeoutSettingsRepository.getStreamResumeMaxRetries()
        )

        val executor = ChatExecutor(
            context = ctx,
            config = executorConfig,
            chatStateManager = chatStateManager,
            conversationRepo = conversationRepo,
            streamUpdateChannel = streamChannel,
            contextCollector = { contextCollector },
            contextManager = { contextManager },
            currentConversationId = { currentConversationId },
            setConversationId = { currentConversationId = it },
            messageHistory = messageHistory,
            buildToolRegistry = { c, m -> buildToolRegistry(c, m) },
            loadMcpTools = { loadMcpToolsInto(it) },
            refreshContextCollector = { refreshContextCollector() },
            buildTracing = { buildTracing() },
            setTracing = { tracing = it },
            getTracing = { tracing },
            buildEventHandler = { emitter -> buildEventHandler(emitter) },
            buildPersistence = { buildPersistence() },
            getPersistence = { persistence },
            setPersistence = { persistence = it },
            tryOfferCheckpointRecovery = { e, msg ->
                checkpointRecovery.tryOfferCheckpointRecovery(persistence, currentConversationId, e, msg)
            },
            tryCompressHistory = { c, m ->
                viewModelScope.launch(Dispatchers.IO) {
                    try { tryCompressHistory(c, m) } catch (_: Exception) { }
                }
            },
            onUpdateButtonState = { isCompressing ->
                if (!isCompressing) {
                    _uiState.update { it.copy(isGenerating = false) }
                    currentJob = null
                }
            }
        )

        activeChatExecutor = executor
        currentJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                executor.execute(message, userMsg, client, config)
            } finally {
                streamChannel.close()
                activeChatExecutor = null
            }
        }
    }

    private fun stopGenerating() {
        ChatStopSignal.requestStop()
        currentJob?.cancel()
        _uiState.update { it.copy(isGenerating = false) }
    }

    private fun savePartialIfGenerating() {
        if (!_uiState.value.isGenerating) return
        val convId = currentConversationId ?: return
        val partialText = chatStateManager.getPartialContent()
        val logContent = activeChatExecutor?.getLogContent() ?: chatStateManager.getLogContent()
        val bufferedText = activeChatExecutor?.getBufferedText() ?: chatStateManager.getBufferedText()
        if (partialText.isEmpty()) return

        stopGenerating()

        val contentToSave = if (logContent.isNotBlank()) {
            logContent + ChatExecutor.BLOCK_TEXT_SEPARATOR + bufferedText
        } else {
            partialText
        }
        messageHistory.add(ChatMessage.assistant(partialText))
        viewModelScope.launch(Dispatchers.IO) {
            conversationRepo.addMessage(convId, ChatMessage.assistant(contentToSave))
        }
    }

    private suspend fun tryOfferStartupRecovery(conversationId: String) {
        val checkpointRecovery = CheckpointRecovery(
            activity = ctx,
            chatStateManager = chatStateManager,
            messageHistory = messageHistory,
            conversationRepo = conversationRepo,
            checkpointRepository = checkpointRepository,
            snapshotSettingsRepository = snapshotSettingsRepository,
            llmSettingsRepository = llmSettingsRepository,
            autoScroll = { requestScrollToBottom() },
            sendMessage = { sendMessage(it) },
            showCheckpointRecoveryDialog = { t, m, labels -> showCheckpointRecoveryDialog(t, m, labels) },
            showStartupRecoveryDialog = { t, m -> showStartupRecoveryDialog(t, m) }
        )
        checkpointRecovery.tryOfferStartupRecovery(conversationId)
    }

    private suspend fun tryCompressHistory(client: ChatClient, model: String) {
        val strategy = CompressionStrategyBuilder.build(configRepo, contextManager) ?: return

        when (strategy) {
            is HistoryCompressionStrategy.TokenBudget -> {
                if (!strategy.shouldCompress(messageHistory)) return
            }
            is HistoryCompressionStrategy.Progressive -> {
                if (!strategy.shouldCompress(messageHistory)) return
            }
            else -> { }
        }

        val listener = object : CompressionEventListener {
            override suspend fun onCompressionStart(strategyName: String, originalMessageCount: Int) {
                withContext(Dispatchers.Main) {
                    chatStateManager.handleSystemLog("[Compress] 开始压缩上下文 [策略: $strategyName, 原始消息: ${originalMessageCount} 条]\n")
                }
            }
            override suspend fun onSummaryChunk(chunk: String) {
                withContext(Dispatchers.Main) { chatStateManager.handleSystemLog(chunk) }
            }
            override suspend fun onBlockCompressed(blockIndex: Int, totalBlocks: Int) {
                withContext(Dispatchers.Main) {
                    chatStateManager.handleSystemLog("\n[Block] 块 $blockIndex/$totalBlocks 压缩完成\n")
                }
            }
            override suspend fun onCompressionDone(compressedMessageCount: Int) {
                withContext(Dispatchers.Main) {
                    chatStateManager.handleSystemLog("\n[OK] 上下文压缩完成 [压缩后: ${compressedMessageCount} 条消息]\n\n")
                }
            }
        }
        val prompt = Prompt.build("compression") {
            for (msg in messageHistory) {
                when (msg.role) {
                    "system" -> system(msg.content)
                    "user" -> user(msg.content)
                    "assistant" -> {
                        if (msg.toolCalls != null) {
                            assistantWithToolCalls(msg.toolCalls!!, msg.content)
                        } else {
                            assistant(msg.content)
                        }
                    }
                    "tool" -> message(msg)
                }
            }
        }
        val session = LLMWriteSession(client, model, prompt)
        try {
            session.replaceHistoryWithTLDR(strategy, listener = listener)
            val compressed = session.prompt.messages
            messageHistory.clear()
            messageHistory.addAll(compressed)
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                chatStateManager.handleSystemLog("\n[WARN] 压缩失败: ${e.message}\n\n")
            }
        } finally {
            session.close()
        }
    }
}
