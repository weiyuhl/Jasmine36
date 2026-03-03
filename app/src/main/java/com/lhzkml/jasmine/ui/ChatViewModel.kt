package com.lhzkml.jasmine.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lhzkml.jasmine.AppConfig
import com.lhzkml.jasmine.ChatExecutor
import com.lhzkml.jasmine.ChatItem
import com.lhzkml.jasmine.ChatStateManager
import com.lhzkml.jasmine.CheckpointRecovery
import com.lhzkml.jasmine.ContentBlock
import com.lhzkml.jasmine.DialogHandlers
import com.lhzkml.jasmine.ProviderManager
import com.lhzkml.jasmine.ProviderConfigActivity
import com.lhzkml.jasmine.SettingsActivity
import com.lhzkml.jasmine.LauncherActivity
import com.lhzkml.jasmine.MainActivity
import com.lhzkml.jasmine.StreamProcessor
import com.lhzkml.jasmine.StreamUpdate
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
import com.lhzkml.jasmine.core.prompt.llm.replaceHistoryWithTLDR
import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.Prompt
import com.lhzkml.jasmine.core.prompt.model.Usage
import com.lhzkml.jasmine.core.agent.tools.WebSearchTool
import com.lhzkml.jasmine.core.agent.tools.FetchUrlTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx: Context get() = getApplication()

    val chatItems = mutableStateListOf<ChatItem>()
    var isGenerating by mutableStateOf(false)
    var currentModelDisplay by mutableStateOf("")
    var modelList by mutableStateOf<List<String>>(emptyList())
    var currentModel by mutableStateOf("")
    var isAgentMode by mutableStateOf(false)
    var workspacePath by mutableStateOf("")
    var workspaceLabel by mutableStateOf("")
    var modeLabelText by mutableStateOf("Chat")
    var showFileTree by mutableStateOf(false)

    val drawerConversations = mutableStateListOf<ConversationInfo>()
    var drawerEmpty by mutableStateOf(true)

    lateinit var chatStateManager: ChatStateManager
        private set

    private val clientRouter = ChatClientRouter()
    private var currentProviderId: String? = null
    var overrideModel: String? = null
    private lateinit var conversationRepo: ConversationRepository
    var currentConversationId: String? = null
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
    private val runtimeBuilder = AgentRuntimeBuilder(AppConfig.configRepo())
    private val toolRegistryBuilder = ToolRegistryBuilder(AppConfig.configRepo())
    private val mcpConnectionManager get() = AppConfig.mcpConnectionManager()
    var userScrolledUp by mutableStateOf(false)

    /** 检查点恢复选择对话框（执行失败时选择恢复轮次） */
    var checkpointRecoveryDialog by mutableStateOf<CheckpointRecoveryDialogState?>(null)
        private set

    /** 启动恢复确认对话框 */
    var startupRecoveryDialog by mutableStateOf<StartupRecoveryDialogState?>(null)
        private set

    data class CheckpointRecoveryDialogState(val title: String, val message: String, val labels: List<String>, val onSelect: (Int?) -> Unit)
    data class StartupRecoveryDialogState(val title: String, val message: String, val onConfirm: (Boolean) -> Unit)

    private suspend fun showCheckpointRecoveryDialog(title: String, message: String, labels: List<String>): Int? {
        return suspendCancellableCoroutine { cont ->
            checkpointRecoveryDialog = CheckpointRecoveryDialogState(title, message, labels) { index ->
                checkpointRecoveryDialog = null
                cont.resume(index)
            }
        }
    }

    private suspend fun showStartupRecoveryDialog(title: String, message: String): Boolean {
        return suspendCancellableCoroutine { cont ->
            startupRecoveryDialog = StartupRecoveryDialogState(title, message) { yes ->
                startupRecoveryDialog = null
                cont.resume(yes)
            }
        }
    }

    var scrollToBottomTrigger by mutableStateOf(0)
        private set

    private var _activity: MainActivity? = null

    fun initialize(activity: MainActivity) {
        _activity = activity
        ProviderManager.initialize(activity)
        conversationRepo = ConversationRepository(activity)

        chatStateManager = ChatStateManager(chatItems) { requestScrollToBottom() }

        refreshAgentModeUI()
        refreshModelSelector()
        subscribeConversations()

        val intentConvId = activity.intent.getStringExtra(MainActivity.EXTRA_CONVERSATION_ID)
        if (intentConvId != null) {
            loadConversation(intentConvId)
        } else {
            val lastId = ProviderManager.getLastConversationId(activity)
            if (lastId.isNotEmpty()) {
                val currentWs = if (ProviderManager.isAgentMode(activity))
                    ProviderManager.getWorkspacePath(activity) else ""
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
        intent.getStringExtra(MainActivity.EXTRA_CONVERSATION_ID)?.let { loadConversation(it) }
    }

    fun onResume() {
        refreshAgentModeUI()
        refreshModelSelector()
    }

    fun onPause() {
        val activity = _activity ?: return
        ProviderManager.setLastConversationId(activity, currentConversationId ?: "")
    }

    fun onCleared2() {
        clientRouter.close()
        webSearchTool?.close()
        fetchUrlTool?.close()
        tracing?.close()
        mcpConnectionManager.close()
    }

    override fun onCleared() {
        super.onCleared()
        onCleared2()
    }

    fun requestScrollToBottom() {
        if (userScrolledUp) return
        scrollToBottomTrigger++
    }

    private fun refreshContextCollector() {
        val activity = _activity ?: return
        val isAgent = ProviderManager.isAgentMode(activity)
        val wsPath = ProviderManager.getWorkspacePath(activity)
        val activeId = ProviderManager.getActiveId()
        val modelName = if (activeId != null) {
            overrideModel ?: ProviderManager.getModel(activity, activeId)
        } else ""
        contextCollector = runtimeBuilder.buildSystemContext(
            isAgentMode = isAgent, workspacePath = wsPath, modelName = modelName
        )
    }

    private fun buildToolRegistry(): ToolRegistry {
        val activity = _activity ?: throw IllegalStateException("Activity not set")
        toolRegistryBuilder.workspacePath = ProviderManager.getWorkspacePath(activity)
        toolRegistryBuilder.fallbackBasePath = activity.getExternalFilesDir(null)?.absolutePath
        DialogHandlers.register(activity, toolRegistryBuilder)
        return toolRegistryBuilder.build(ProviderManager.isAgentMode(activity))
    }

    private fun preconnectMcpServers() {
        val activity = _activity ?: return
        mcpConnectionManager.listener = object : McpConnectionManager.ConnectionListener {
            override fun onConnected(serverName: String, transportType: com.lhzkml.jasmine.core.config.McpTransportType, toolCount: Int) {
                val transportLabel = when (transportType) {
                    com.lhzkml.jasmine.core.config.McpTransportType.STREAMABLE_HTTP -> "HTTP"
                    com.lhzkml.jasmine.core.config.McpTransportType.SSE -> "SSE"
                }
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(activity, "MCP: $serverName 已连接 [$transportLabel] ($toolCount 个工具)", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onConnectionFailed(serverName: String, error: String) {
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(activity, "MCP: $serverName 连接失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) { mcpConnectionManager.preconnect() }
    }

    private suspend fun loadMcpToolsInto(registry: ToolRegistry) {
        mcpConnectionManager.loadToolsInto(registry)
    }

    private fun buildTracing(): Tracing? {
        val activity = _activity ?: return null
        return runtimeBuilder.buildTracing(activity.getExternalFilesDir("traces"))
    }

    private fun buildEventHandler(): EventHandler? {
        return runtimeBuilder.buildEventHandler { line ->
            withContext(Dispatchers.Main) {
                chatStateManager.handleSystemLog(line)
            }
        }
    }

    private fun buildPersistence(): Persistence? {
        val activity = _activity ?: return null
        return runtimeBuilder.buildPersistence(activity.getExternalFilesDir("snapshots"))
    }

    fun subscribeConversations() {
        val activity = _activity ?: return
        conversationObserverJob?.cancel()
        val isAgent = ProviderManager.isAgentMode(activity)
        val wp = if (isAgent) ProviderManager.getWorkspacePath(activity) else ""
        conversationObserverJob = viewModelScope.launch {
            conversationRepo.observeConversationsByWorkspace(wp).collectLatest { list ->
                drawerConversations.clear()
                drawerConversations.addAll(list)
                drawerEmpty = list.isEmpty()
            }
        }
    }

    fun refreshAgentModeUI() {
        val activity = _activity ?: return
        val isAgent = ProviderManager.isAgentMode(activity)
        isAgentMode = isAgent
        if (isAgent) {
            modeLabelText = "Agent"
            val path = ProviderManager.getWorkspacePath(activity)
            workspacePath = path
            showFileTree = true
            workspaceLabel = if (path.isNotEmpty()) path else "未选择工作区"
        } else {
            modeLabelText = "Chat"
            showFileTree = false
            workspaceLabel = "普通聊天"
            workspacePath = ""
        }
        val currentWs = if (isAgent) ProviderManager.getWorkspacePath(activity) else ""
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

    fun refreshModelSelector() {
        val activity = _activity ?: return
        val activeId = ProviderManager.getActiveId()
        if (activeId == null) {
            currentModelDisplay = "未配置"
            modelList = emptyList()
            currentModel = ""
            return
        }
        val model = overrideModel ?: ProviderManager.getModel(activity, activeId)
        currentModel = model
        currentModelDisplay = "${shortenModelName(model).ifEmpty { "未选择模型" }} \u02C7"
        val selectedModels = ProviderManager.getSelectedModels(activity, activeId)
        modelList = if (selectedModels.isEmpty()) {
            if (model.isNotEmpty()) listOf(model) else emptyList()
        } else {
            if (model.isNotEmpty() && model !in selectedModels) {
                listOf(model) + selectedModels
            } else selectedModels
        }
    }

    fun selectModel(model: String) {
        val activity = _activity ?: return
        val activeId = ProviderManager.getActiveId() ?: return
        overrideModel = model
        val key = ProviderManager.getApiKey(activity, activeId) ?: ""
        val baseUrl = ProviderManager.getBaseUrl(activity, activeId)
        ProviderManager.saveConfig(activity, activeId, key, baseUrl, model)
        refreshModelSelector()
    }

    fun shortenModelName(model: String): String = model.substringAfterLast("/")

    fun closeWorkspace() {
        val activity = _activity ?: return
        if (ProviderManager.isAgentMode(activity)) {
            ProviderManager.setLastConversationId(activity, currentConversationId ?: "")
            val uriStr = ProviderManager.getWorkspaceUri(activity)
            if (uriStr.isNotEmpty()) {
                try {
                    val uri = android.net.Uri.parse(uriStr)
                    activity.contentResolver.releasePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: Exception) {}
            }
            ProviderManager.setWorkspacePath(activity, "")
            ProviderManager.setWorkspaceUri(activity, "")
        }
        ProviderManager.setAgentMode(activity, false)
        ProviderManager.setLastSession(activity, false)
        val intent = Intent(activity, LauncherActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivity(intent)
        activity.finish()
    }

    fun startNewConversation() {
        currentConversationId = null
        messageHistory.clear()
        chatStateManager.clearAll()
    }

    fun loadConversation(conversationId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val info = conversationRepo.getConversation(conversationId)
            val timedMessages = conversationRepo.getTimedMessages(conversationId)
            val messages = conversationRepo.getMessages(conversationId)
            val usageList = conversationRepo.getUsageList(conversationId)
            withContext(Dispatchers.Main) {
                if (info == null) {
                    val activity = _activity ?: return@withContext
                    Toast.makeText(activity, "对话不存在", Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                currentConversationId = conversationId
                messageHistory.clear()
                messageHistory.addAll(messages.filter { it.role != "agent_log" })
                chatStateManager.clearAll()
                var usageIndex = 0
                var pendingLogBlocks: MutableList<ContentBlock>? = null
                for (msg in timedMessages) {
                    val time = ChatExecutor.formatTime(msg.createdAt)
                    when (msg.role) {
                        "user" -> {
                            if (pendingLogBlocks != null) {
                                chatStateManager.addHistoryLogBlocks(pendingLogBlocks!!)
                                pendingLogBlocks = null
                            }
                            chatStateManager.addUserMessage(msg.content, time)
                        }
                        "agent_log" -> {
                            pendingLogBlocks = parseLogBlocks(msg.content)
                        }
                        "assistant" -> {
                            val usage = usageList.getOrNull(usageIndex)
                            val usageLine = if (usage != null) {
                                "[提示: ${usage.promptTokens} | 回复: ${usage.completionTokens} | 总计: ${usage.totalTokens}]"
                            } else ""
                            val blocks = mutableListOf<ContentBlock>()
                            if (pendingLogBlocks != null) {
                                blocks.addAll(pendingLogBlocks!!)
                                pendingLogBlocks = null
                            }
                            if (msg.content.isNotEmpty()) {
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
                if (pendingLogBlocks != null) {
                    chatStateManager.addHistoryLogBlocks(pendingLogBlocks!!)
                }
                requestScrollToBottom()
            }
            val activity = _activity ?: return@launch
            if (ProviderManager.isSnapshotEnabled(activity)
                && ProviderManager.getSnapshotStorage(activity) == com.lhzkml.jasmine.core.config.SnapshotStorageType.FILE
            ) {
                tryOfferStartupRecovery(conversationId)
            }
        }
    }

    private fun parseLogBlocks(logContent: String): MutableList<ContentBlock> {
        val blocks = mutableListOf<ContentBlock>()
        val lines = logContent.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.startsWith("[Think] ") -> {
                    val thinkText = StringBuilder(line.removePrefix("[Think] "))
                    i++
                    while (i < lines.size && !lines[i].startsWith("[") && lines[i].isNotBlank()) {
                        thinkText.append("\n").append(lines[i])
                        i++
                    }
                    blocks.add(ContentBlock.Thinking(thinkText.toString()))
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
                line.isBlank() -> {
                    i++
                }
                else -> {
                    blocks.add(ContentBlock.SystemLog(line))
                    i++
                }
            }
        }
        return blocks
    }

    fun deleteConversation(info: ConversationInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            conversationRepo.deleteConversation(info.id)
            if (info.id == currentConversationId) {
                withContext(Dispatchers.Main) { startNewConversation() }
            }
        }
    }

    private fun getOrCreateClient(config: ActiveProviderConfig): ChatClient {
        val existing = clientRouter.getClient(config.providerId)
        if (existing != null && currentProviderId == config.providerId) return existing
        if (currentProviderId != null && currentProviderId != config.providerId) {
            clientRouter.unregister(currentProviderId!!)
        }
        val provider = ProviderManager.getProvider(config.providerId)
        val activity = _activity!!
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
            requestTimeoutMs = ProviderManager.getRequestTimeout(activity).toLong() * 1000,
            connectTimeoutMs = ProviderManager.getConnectTimeout(activity).toLong() * 1000,
            socketTimeoutMs = ProviderManager.getSocketTimeout(activity).toLong() * 1000
        )
        val client = ChatClientFactory.create(clientConfig)
        clientRouter.register(config.providerId, client)
        currentProviderId = config.providerId
        val llmProvider = client.provider
        val modelMeta = ModelRegistry.find(config.model)
        contextManager = if (modelMeta != null) ContextManager.fromModel(modelMeta)
        else ContextManager.forModel(config.model, llmProvider)
        return client
    }

    fun sendMessage(message: String) {
        val activity = _activity ?: return
        val config = ProviderManager.getActiveConfig()
        if (config == null) {
            Toast.makeText(activity, "请先在设置中配置模型供应商", Toast.LENGTH_SHORT).show()
            activity.startActivity(Intent(activity, SettingsActivity::class.java))
            return
        }
        val actualModel = overrideModel ?: config.model
        if (actualModel.isEmpty()) {
            Toast.makeText(activity, "请先选择模型", Toast.LENGTH_SHORT).show()
            activity.startActivity(Intent(activity, ProviderConfigActivity::class.java).apply {
                putExtra("provider_id", config.providerId)
                putExtra("tab", 1)
            })
            return
        }

        isGenerating = true
        userScrolledUp = false
        val now = ChatExecutor.formatTime(System.currentTimeMillis())
        chatStateManager.addUserMessage(message, now)

        val client = getOrCreateClient(config)
        val userMsg = ChatMessage.user(message)

        val checkpointRecovery = CheckpointRecovery(
            activity = activity,
            chatStateManager = chatStateManager,
            messageHistory = messageHistory,
            conversationRepo = conversationRepo,
            autoScroll = { requestScrollToBottom() },
            sendMessage = { sendMessage(it) },
            showCheckpointRecoveryDialog = { t, m, labels -> showCheckpointRecoveryDialog(t, m, labels) },
            showStartupRecoveryDialog = { t, m -> showStartupRecoveryDialog(t, m) }
        )

        val executor = ChatExecutor(
            context = activity,
            chatStateManager = chatStateManager,
            conversationRepo = conversationRepo,
            contextCollector = { contextCollector },
            contextManager = { contextManager },
            currentConversationId = { currentConversationId },
            setConversationId = { currentConversationId = it },
            messageHistory = messageHistory,
            buildToolRegistry = { buildToolRegistry() },
            loadMcpTools = { loadMcpToolsInto(it) },
            refreshContextCollector = { refreshContextCollector() },
            buildTracing = { buildTracing() },
            setTracing = { tracing = it },
            getTracing = { tracing },
            buildEventHandler = { buildEventHandler() },
            buildPersistence = { buildPersistence() },
            getPersistence = { persistence },
            setPersistence = { persistence = it },
            tryOfferCheckpointRecovery = { e, msg ->
                checkpointRecovery.tryOfferCheckpointRecovery(persistence, currentConversationId, e, msg)
            },
            tryCompressHistory = { c, m -> tryCompressHistory(c, m) },
            onUpdateButtonState = { isCompressing ->
                if (!isCompressing) {
                    isGenerating = false
                    currentJob = null
                }
            }
        )

        currentJob = viewModelScope.launch(Dispatchers.IO) {
            executor.execute(message, userMsg, client, config)
        }
    }

    fun stopGenerating() {
        currentJob?.cancel()
        isGenerating = false
    }

    private suspend fun tryOfferStartupRecovery(conversationId: String) {
        val activity = _activity ?: return
        val checkpointRecovery = CheckpointRecovery(
            activity = activity,
            chatStateManager = chatStateManager,
            messageHistory = messageHistory,
            conversationRepo = conversationRepo,
            autoScroll = { requestScrollToBottom() },
            sendMessage = { sendMessage(it) },
            showCheckpointRecoveryDialog = { t, m, labels -> showCheckpointRecoveryDialog(t, m, labels) },
            showStartupRecoveryDialog = { t, m -> showStartupRecoveryDialog(t, m) }
        )
        checkpointRecovery.tryOfferStartupRecovery(conversationId)
    }

    private suspend fun tryCompressHistory(client: ChatClient, model: String) {
        val activity = _activity ?: return
        val strategy = CompressionStrategyBuilder.build(AppConfig.configRepo(), contextManager) ?: return
        if (strategy is HistoryCompressionStrategy.TokenBudget) {
            if (!strategy.shouldCompress(messageHistory)) return
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
                    "assistant" -> assistant(msg.content)
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
                chatStateManager.handleSystemLog("\n[WARN] 压缩失败: ${e.message}]\n\n")
            }
        } finally {
            session.close()
        }
    }

    fun openSettings() {
        val activity = _activity ?: return
        activity.startActivity(Intent(activity, SettingsActivity::class.java))
    }
}
