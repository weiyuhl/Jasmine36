package com.lhzkml.jasmine

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.lhzkml.jasmine.core.prompt.llm.ChatClient
import com.lhzkml.jasmine.core.prompt.llm.ChatClientException
import com.lhzkml.jasmine.core.prompt.llm.ChatClientRouter
import com.lhzkml.jasmine.core.prompt.llm.chatStreamWithUsageAndThinking
import com.lhzkml.jasmine.core.prompt.llm.CompressionEventListener
import com.lhzkml.jasmine.core.prompt.llm.ContextManager
import com.lhzkml.jasmine.core.prompt.llm.ErrorType
import com.lhzkml.jasmine.core.prompt.llm.HistoryCompressionStrategy
import com.lhzkml.jasmine.core.prompt.llm.LLMWriteSession
import com.lhzkml.jasmine.core.prompt.llm.ModelRegistry
import com.lhzkml.jasmine.core.prompt.llm.TokenEstimator
import com.lhzkml.jasmine.core.prompt.llm.StreamResumeHelper
import com.lhzkml.jasmine.core.prompt.llm.replaceHistoryWithTLDR
import com.lhzkml.jasmine.core.prompt.llm.SystemContextCollector
import com.lhzkml.jasmine.core.prompt.llm.WorkspaceContextProvider
import com.lhzkml.jasmine.core.prompt.llm.SystemInfoContextProvider
import com.lhzkml.jasmine.core.prompt.llm.CurrentTimeContextProvider
import com.lhzkml.jasmine.core.prompt.llm.AgentPromptContextProvider
import com.lhzkml.jasmine.core.prompt.executor.ChatClientConfig
import com.lhzkml.jasmine.core.prompt.executor.ChatClientFactory
import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.Prompt
import com.lhzkml.jasmine.core.prompt.model.Usage
import com.lhzkml.jasmine.core.conversation.storage.ConversationInfo
import com.lhzkml.jasmine.core.conversation.storage.ConversationRepository
import com.lhzkml.jasmine.core.conversation.storage.TimedMessage
import com.lhzkml.jasmine.core.agent.tools.*
import com.lhzkml.jasmine.core.agent.tools.mcp.HttpMcpClient
import com.lhzkml.jasmine.core.agent.tools.mcp.McpClient
import com.lhzkml.jasmine.core.agent.tools.mcp.McpToolAdapter
import com.lhzkml.jasmine.core.agent.tools.mcp.McpToolDefinition
import com.lhzkml.jasmine.core.agent.tools.mcp.McpToolRegistryProvider
import com.lhzkml.jasmine.core.agent.tools.mcp.SseMcpClient
import com.lhzkml.jasmine.core.agent.tools.trace.LogTraceWriter
import com.lhzkml.jasmine.core.agent.tools.trace.Tracing
import com.lhzkml.jasmine.core.agent.tools.planner.SimpleLLMPlanner
import com.lhzkml.jasmine.core.agent.tools.planner.SimpleLLMWithCriticPlanner
import com.lhzkml.jasmine.core.agent.tools.graph.AgentGraphContext
import com.lhzkml.jasmine.core.agent.tools.graph.GenericAgentEnvironment
import com.lhzkml.jasmine.core.agent.tools.graph.GraphAgent
import com.lhzkml.jasmine.core.agent.tools.graph.PredefinedStrategies
import com.lhzkml.jasmine.core.agent.tools.graph.ToolCalls
import com.lhzkml.jasmine.core.agent.tools.graph.ToolSelectionStrategy
import com.lhzkml.jasmine.core.agent.tools.event.EventHandler
import com.lhzkml.jasmine.core.agent.tools.event.*
import com.lhzkml.jasmine.core.agent.tools.snapshot.Persistence
import com.lhzkml.jasmine.core.agent.tools.snapshot.AgentCheckpoint
import com.lhzkml.jasmine.core.agent.tools.snapshot.InMemoryPersistenceStorageProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CONVERSATION_ID = "conversation_id"

        /**
         * 全局 MCP 连接状态缓存
         * McpServerActivity 进入时读取此缓存，避免重复连接。
         * key = 服务器名称
         */
        data class McpServerStatus(
            val success: Boolean,
            val tools: List<McpToolDefinition> = emptyList(),
            val error: String? = null
        )

        val mcpConnectionCache = mutableMapOf<String, McpServerStatus>()
    }

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var mainContent: LinearLayout
    private lateinit var etInput: EditText
    private lateinit var btnSend: MaterialButton
    private lateinit var tvOutput: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var tvDrawerEmpty: TextView
    private lateinit var rvDrawerConversations: RecyclerView
    private lateinit var tvModeLabel: TextView
    private lateinit var layoutWorkspace: LinearLayout
    private lateinit var tvWorkspacePath: TextView
    private lateinit var btnCloseWorkspace: TextView
    private lateinit var btnFileTree: ImageButton
    private lateinit var fileTreePanel: LinearLayout
    private lateinit var tvFileTreeRoot: TextView
    private lateinit var rvFileTree: RecyclerView
    private val fileTreeAdapter = FileTreeAdapter()

    private val clientRouter = ChatClientRouter()
    private var currentProviderId: String? = null
    /** 当前选中的模型（可被底部模型选择器覆盖） */
    private var overrideModel: String? = null
    private lateinit var tvCurrentModel: TextView

    private lateinit var conversationRepo: ConversationRepository
    private var currentConversationId: String? = null
    private val messageHistory = mutableListOf<ChatMessage>()
    private val drawerAdapter = DrawerConversationAdapter()
    private var contextManager = ContextManager()
    private var webSearchTool: WebSearchTool? = null
    private var fetchUrlTool: FetchUrlTool? = null
    private var currentJob: Job? = null
    private var conversationObserverJob: Job? = null
    private var isGenerating = false
    private var tracing: Tracing? = null
    private var eventHandler: EventHandler? = null
    private var persistence: Persistence? = null
    private var mcpClients: MutableList<McpClient> = mutableListOf()
    /** 预加载的 MCP 工具（APP 启动时后台连接） */
    private var preloadedMcpTools: MutableList<McpToolAdapter> = mutableListOf()
    private var mcpPreloaded = false
    /** 中间过程日志收集器，用于持久化到对话历史 */
    private var agentLogBuilder = StringBuilder()
    /** 用户是否手动向上滚动（流式回复期间暂停自动滚动） */
    private var userScrolledUp = false
    /** 系统上下文收集器 — 自动拼接环境信息到 system prompt */
    private val contextCollector = SystemContextCollector()

    /**
     * 刷新系统上下文收集器
     * 根据当前设置注册/注销上下文提供者，发送消息时自动拼接到 system prompt。
     */
    private fun refreshContextCollector() {
        contextCollector.clear()

        val isAgent = ProviderManager.isAgentMode(this)
        val wsPath = ProviderManager.getWorkspacePath(this)

        // Agent 模式：注入结构化行为指引提示词（工具通过 API tools 参数发送，不在提示词中列出）
        if (isAgent) {
            contextCollector.register(AgentPromptContextProvider(
                agentName = "Jasmine",
                workspacePath = wsPath
            ))
        }

        // Agent 模式：注入工作区路径
        if (isAgent && wsPath.isNotEmpty()) {
            contextCollector.register(WorkspaceContextProvider(wsPath))
        }

        // 系统信息
        contextCollector.register(SystemInfoContextProvider())

        // 当前时间
        contextCollector.register(CurrentTimeContextProvider())
    }

    /**
     * 根据设置构建工具注册表
     */
    private fun buildToolRegistry(): ToolRegistry {
        // Agent 模式下优先使用 Agent 工具预设，否则使用普通工具配置
        val enabledTools = if (ProviderManager.isAgentMode(this)) {
            ProviderManager.getAgentToolPreset(this)
        } else {
            ProviderManager.getEnabledTools(this)
        }
        fun isEnabled(name: String) = enabledTools.isEmpty() || name in enabledTools

        return ToolRegistry.build {
            // 计算器
            if (isEnabled("calculator")) {
                CalculatorTool.allTools().forEach { register(it) }
            }

            // 获取当前时间
            if (isEnabled("get_current_time")) {
                register(GetCurrentTimeTool)
            }

            // 文件工具 — Agent 模式使用用户选择的工作区，否则使用 APP 沙箱
            val workspacePath = ProviderManager.getWorkspacePath(this@MainActivity)
            val basePath = if (ProviderManager.isAgentMode(this@MainActivity) && workspacePath.isNotEmpty()) {
                workspacePath
            } else {
                getExternalFilesDir(null)?.absolutePath
            }
            if (isEnabled("file_tools")) {
                register(ReadFileTool(basePath))
                register(WriteFileTool(basePath))
                register(EditFileTool(basePath))
                register(ListDirectoryTool(basePath))
                register(RegexSearchTool(basePath))
                register(FindFilesTool(basePath))
                register(DeleteFileTool(basePath))
                register(MoveFileTool(basePath))
                register(CopyFileTool(basePath))
                register(AppendFileTool(basePath))
                register(FileInfoTool(basePath))
                register(CreateDirectoryTool(basePath))
                register(CompressFilesTool(basePath))
                register(InsertContentTool(basePath))
                register(RenameFileTool(basePath))
                register(ReplaceInFileTool(basePath))
                register(CreateFileTool(basePath))
            }

            // Shell 命令（根据策略决定确认方式）
            if (isEnabled("execute_shell_command")) {
                val shellPolicy = ProviderManager.getShellPolicy(this@MainActivity)
                val blacklist = ProviderManager.getShellBlacklist(this@MainActivity)
                val whitelist = ProviderManager.getShellWhitelist(this@MainActivity)

                val policyConfig = ShellPolicyConfig(
                    policy = shellPolicy,
                    blacklist = blacklist,
                    whitelist = whitelist
                )

                register(ExecuteShellCommandTool(
                    confirmationHandler = { command, _ ->
                        val deferred = CompletableDeferred<Boolean>()
                        withContext(Dispatchers.Main) {
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("执行命令确认")
                                .setMessage("AI 请求执行以下命令：\n\n$command\n\n是否允许？")
                                .setPositiveButton("允许") { _, _ -> deferred.complete(true) }
                                .setNegativeButton("拒绝") { _, _ -> deferred.complete(false) }
                                .setCancelable(false)
                                .show()
                        }
                        deferred.await()
                    },
                    policyConfig = policyConfig,
                    basePath = basePath
                ))
            }

            // 网络搜索/抓取（BrightData API）
            if (isEnabled("web_search")) {
                val brightDataKey = ProviderManager.getBrightDataKey(this@MainActivity)
                if (brightDataKey.isNotEmpty()) {
                    webSearchTool?.close()
                    val wst = WebSearchTool(brightDataKey)
                    webSearchTool = wst
                    register(wst.search)
                    register(wst.scrape)
                }
            }

            // URL 抓取（本地直接请求）
            if (isEnabled("fetch_url")) {
                fetchUrlTool?.close()
                val ft = FetchUrlTool()
                fetchUrlTool = ft
                register(ft.fetchHtml)
                register(ft.fetchText)
                register(ft.fetchJson)
            }

            // Agent 显式完成工具
            if (isEnabled("attempt_completion")) register(AttemptCompletionTool)
        }
    }

    /**
     * APP 启动时后台预连接 MCP 服务器
     * 连接成功后缓存客户端和工具，发消息时直接复用。
     */
    private fun preconnectMcpServers() {
        if (!ProviderManager.isMcpEnabled(this)) return

        val servers = ProviderManager.getMcpServers(this).filter { it.enabled && it.url.isNotBlank() }
        if (servers.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            for (server in servers) {
                try {
                    val headers = mutableMapOf<String, String>()
                    if (server.headerName.isNotBlank() && server.headerValue.isNotBlank()) {
                        headers[server.headerName] = server.headerValue
                    }

                    val client: McpClient = when (server.transportType) {
                        ProviderManager.McpTransportType.SSE ->
                            SseMcpClient(server.url, customHeaders = headers)
                        ProviderManager.McpTransportType.STREAMABLE_HTTP ->
                            HttpMcpClient(server.url, headers)
                    }
                    client.connect()
                    mcpClients.add(client)

                    val mcpRegistry = McpToolRegistryProvider.fromClient(client)
                    for (descriptor in mcpRegistry.descriptors()) {
                        val mcpTool = mcpRegistry.findTool(descriptor.name) ?: continue
                        preloadedMcpTools.add(McpToolAdapter(mcpTool))
                    }

                    // 获取工具定义列表，缓存到全局状态
                    val toolDefs = client.listTools()
                    mcpConnectionCache[server.name] = McpServerStatus(
                        success = true,
                        tools = toolDefs
                    )

                    withContext(Dispatchers.Main) {
                        val transportLabel = when (server.transportType) {
                            ProviderManager.McpTransportType.STREAMABLE_HTTP -> "HTTP"
                            ProviderManager.McpTransportType.SSE -> "SSE"
                        }
                        Toast.makeText(this@MainActivity, "MCP: ${server.name} 已连接 [$transportLabel] (${mcpRegistry.size} 个工具)", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    mcpConnectionCache[server.name] = McpServerStatus(
                        success = false,
                        error = e.message ?: "未知错误"
                    )
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "MCP: ${server.name} 连接失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            mcpPreloaded = true
        }
    }

    /**
     * 加载 MCP 工具到注册表
     * 优先复用 APP 启动时预连接的工具，避免重复连接。
     */
    private suspend fun loadMcpToolsInto(registry: ToolRegistry) {
        if (!ProviderManager.isMcpEnabled(this)) return

        // 如果已经预加载了，直接复用
        if (mcpPreloaded && preloadedMcpTools.isNotEmpty()) {
            for (tool in preloadedMcpTools) {
                registry.register(tool)
            }
            return
        }

        // 还没预加载完成，等一下或者重新连接
        val servers = ProviderManager.getMcpServers(this).filter { it.enabled && it.url.isNotBlank() }
        if (servers.isEmpty()) return

        // 如果预加载还在进行中（mcpClients 不为空但 mcpPreloaded 还是 false），等待
        // 简单处理：如果已有客户端但工具为空，说明还在连接中，重新连接
        if (mcpClients.isNotEmpty() && preloadedMcpTools.isEmpty()) {
            // 预连接可能还在进行，等一小段时间
            var waited = 0
            while (!mcpPreloaded && waited < 10000) {
                kotlinx.coroutines.delay(200)
                waited += 200
            }
            if (preloadedMcpTools.isNotEmpty()) {
                for (tool in preloadedMcpTools) {
                    registry.register(tool)
                }
                return
            }
        }

        // 预连接失败或未启动，重新连接
        mcpClients.forEach { try { it.close() } catch (_: Exception) {} }
        mcpClients.clear()
        preloadedMcpTools.clear()

        for (server in servers) {
            try {
                val headers = mutableMapOf<String, String>()
                if (server.headerName.isNotBlank() && server.headerValue.isNotBlank()) {
                    headers[server.headerName] = server.headerValue
                }

                val client: McpClient = when (server.transportType) {
                    ProviderManager.McpTransportType.SSE ->
                        SseMcpClient(server.url, customHeaders = headers)
                    ProviderManager.McpTransportType.STREAMABLE_HTTP ->
                        HttpMcpClient(server.url, headers)
                }
                client.connect()
                mcpClients.add(client)

                val mcpRegistry = McpToolRegistryProvider.fromClient(client)
                for (descriptor in mcpRegistry.descriptors()) {
                    val mcpTool = mcpRegistry.findTool(descriptor.name) ?: continue
                    val adapter = McpToolAdapter(mcpTool)
                    preloadedMcpTools.add(adapter)
                    registry.register(adapter)
                }

                withContext(Dispatchers.Main) {
                    val transportLabel = when (server.transportType) {
                        ProviderManager.McpTransportType.STREAMABLE_HTTP -> "HTTP"
                        ProviderManager.McpTransportType.SSE -> "SSE"
                    }
                    Toast.makeText(this@MainActivity, "MCP: ${server.name} 已连接 [$transportLabel] (${mcpRegistry.size} 个工具)", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "MCP: ${server.name} 连接失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
        mcpPreloaded = true
    }

    /**
     * 构建追踪系统
     * Trace 专注于数据记录（日志文件、Android Log），不负责 UI 显示。
     * UI 显示由 EventHandler 负责。
     */
    private fun buildTracing(): Tracing? {
        if (!ProviderManager.isTraceEnabled(this)) return null

        return Tracing.build {
            addWriter(LogTraceWriter())
            if (ProviderManager.isTraceFileEnabled(this@MainActivity)) {
                val traceDir = getExternalFilesDir("traces")
                if (traceDir != null) {
                    traceDir.mkdirs()
                    val traceFile = java.io.File(traceDir, "trace_${System.currentTimeMillis()}.log")
                    addWriter(com.lhzkml.jasmine.core.agent.tools.trace.FileTraceWriter(traceFile))
                }
            }
        }
    }

    /**
     * 构建事件处理器
     * EventHandler 是 UI 通知系统，负责在聊天界面实时显示 Agent 执行过程。
     * 与 Trace（纯数据记录）职责分离：Trace 写日志/文件，EventHandler 更新 UI。
     */
    private fun buildEventHandler(): EventHandler? {
        if (!ProviderManager.isEventHandlerEnabled(this)) return null

        val filter = ProviderManager.getEventHandlerFilter(this)
        fun isEnabled(cat: com.lhzkml.jasmine.core.agent.tools.event.EventCategory) = filter.isEmpty() || cat in filter

        // 统一的事件输出方法
        suspend fun emitEvent(line: String) {
            agentLogBuilder.append(line)
            withContext(Dispatchers.Main) {
                appendRendered(line)
                autoScrollToBottom()
            }
        }

        return EventHandler.build {
            if (isEnabled(com.lhzkml.jasmine.core.agent.tools.event.EventCategory.AGENT)) {
                onAgentExecutionFailed { ctx -> emitEvent("[EVENT] Agent 失败: ${ctx.throwable.message}\n") }
            }
            if (isEnabled(com.lhzkml.jasmine.core.agent.tools.event.EventCategory.TOOL)) {
                onToolCallStarting { ctx -> emitEvent("[EVENT] 工具调用: ${ctx.toolName}(${ctx.toolArgs.take(60)})\n") }
                onToolCallCompleted { ctx -> emitEvent("[EVENT] 工具完成: ${ctx.toolName} -> ${(ctx.result ?: "").take(100)}\n") }
                onToolCallFailed { ctx -> emitEvent("[EVENT] 工具失败: ${ctx.toolName} - ${ctx.throwable.message}\n") }
                onToolValidationFailed { ctx -> emitEvent("[EVENT] 工具验证失败: ${ctx.toolName} - ${ctx.validationError}\n") }
            }
            if (isEnabled(com.lhzkml.jasmine.core.agent.tools.event.EventCategory.LLM)) {
                onLLMCallStarting { ctx -> emitEvent("[EVENT] LLM 请求 [消息: ${ctx.messageCount}, 工具: ${ctx.tools.size}]\n") }
                onLLMCallCompleted { ctx -> emitEvent("[EVENT] LLM 回复 [${ctx.totalTokens} tokens]\n") }
            }
            if (isEnabled(com.lhzkml.jasmine.core.agent.tools.event.EventCategory.STRATEGY)) {
                onStrategyStarting { ctx -> emitEvent("[EVENT] 策略开始: ${ctx.strategyName}\n") }
                onStrategyCompleted { ctx -> emitEvent("[EVENT] 策略完成: ${ctx.strategyName} -> ${ctx.result?.take(80) ?: ""}\n") }
            }
            if (isEnabled(com.lhzkml.jasmine.core.agent.tools.event.EventCategory.NODE)) {
                onNodeExecutionStarting { ctx -> emitEvent("[EVENT] 节点开始: ${ctx.nodeName}\n") }
                onNodeExecutionCompleted { ctx -> emitEvent("[EVENT] 节点完成: ${ctx.nodeName}\n") }
                onNodeExecutionFailed { ctx -> emitEvent("[EVENT] 节点失败: ${ctx.nodeName} - ${ctx.throwable.message}\n") }
            }
            if (isEnabled(com.lhzkml.jasmine.core.agent.tools.event.EventCategory.SUBGRAPH)) {
                onSubgraphExecutionStarting { ctx -> emitEvent("[EVENT] 子图开始: ${ctx.subgraphName}\n") }
                onSubgraphExecutionCompleted { ctx -> emitEvent("[EVENT] 子图完成: ${ctx.subgraphName}\n") }
                onSubgraphExecutionFailed { ctx -> emitEvent("[EVENT] 子图失败: ${ctx.subgraphName} - ${ctx.throwable.message}\n") }
            }
            if (isEnabled(com.lhzkml.jasmine.core.agent.tools.event.EventCategory.STREAMING)) {
                onLLMStreamingStarting { ctx -> emitEvent("[EVENT] LLM 流式开始 [模型: ${ctx.model}]\n") }
                onLLMStreamingCompleted { ctx -> emitEvent("[EVENT] LLM 流式完成 [${ctx.totalTokens} tokens]\n") }
                onLLMStreamingFailed { ctx -> emitEvent("[EVENT] LLM 流式失败: ${ctx.throwable.message}\n") }
            }
        }
    }

    /**
     * 构建快照/持久化系统
     */
    private fun buildPersistence(): Persistence? {
        if (!ProviderManager.isSnapshotEnabled(this)) return null

        val provider = when (ProviderManager.getSnapshotStorage(this)) {
            ProviderManager.SnapshotStorage.MEMORY -> InMemoryPersistenceStorageProvider()
            ProviderManager.SnapshotStorage.FILE -> {
                val snapshotDir = getExternalFilesDir("snapshots")
                if (snapshotDir != null) {
                    com.lhzkml.jasmine.core.agent.tools.snapshot.FilePersistenceStorageProvider(snapshotDir)
                } else {
                    InMemoryPersistenceStorageProvider()
                }
            }
        }

        val autoCheckpoint = ProviderManager.isSnapshotAutoCheckpoint(this)

        val persistence = Persistence(
            provider = provider,
            autoCheckpoint = autoCheckpoint
        )

        // 设置回滚策略
        persistence.rollbackStrategy = ProviderManager.getSnapshotRollbackStrategy(this)

        return persistence
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化 ProviderManager，加载自定义供应商
        ProviderManager.initialize(this)

        conversationRepo = ConversationRepository(this)

        // 动态注册自定义供应商示例（可选）
        // ProviderManager.registerProvider(
        //     Provider("openai", "OpenAI", "https://api.openai.com", "gpt-4")
        // )

        drawerLayout = findViewById(R.id.drawerLayout)
        mainContent = findViewById(R.id.mainContent)
        etInput = findViewById(R.id.etInput)
        btnSend = findViewById(R.id.btnSend)
        tvOutput = findViewById(R.id.tvOutput)
        scrollView = findViewById(R.id.scrollView)

        // 点击聊天区域时取消输入框焦点并隐藏键盘
        scrollView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                etInput.clearFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(etInput.windowToken, 0)
            }
            false
        }

        // 检测用户手动滚动：向上滚动时暂停自动滚动，滚到底部时恢复
        scrollView.viewTreeObserver.addOnScrollChangedListener {
            if (!isGenerating) return@addOnScrollChangedListener
            val diff = scrollView.getChildAt(0).bottom - (scrollView.height + scrollView.scrollY)
            userScrolledUp = diff > 50
        }

        tvDrawerEmpty = findViewById(R.id.tvDrawerEmpty)
        rvDrawerConversations = findViewById(R.id.rvDrawerConversations)

        // 模式标签（只读显示）
        tvModeLabel = findViewById(R.id.tvModeLabel)
        layoutWorkspace = findViewById(R.id.layoutWorkspace)
        tvWorkspacePath = findViewById(R.id.tvWorkspacePath)
        btnCloseWorkspace = findViewById(R.id.btnCloseWorkspace)

        btnCloseWorkspace.setOnClickListener { closeWorkspace() }

        // 底部模型选择器
        tvCurrentModel = findViewById(R.id.tvCurrentModel)
        tvCurrentModel.setOnClickListener { showModelPopup(it) }
        refreshModelSelector()

        // 文件树侧边栏（Agent 模式）
        btnFileTree = findViewById(R.id.btnFileTree)
        fileTreePanel = findViewById(R.id.fileTreePanel)
        tvFileTreeRoot = findViewById(R.id.tvFileTreeRoot)
        rvFileTree = findViewById(R.id.rvFileTree)
        rvFileTree.layoutManager = LinearLayoutManager(this)
        rvFileTree.adapter = fileTreeAdapter
        fileTreeAdapter.onFileClick = { file ->
            // 点击文件：显示文件路径提示
            Toast.makeText(this, file.absolutePath, Toast.LENGTH_SHORT).show()
        }
        btnFileTree.setOnClickListener {
            drawerLayout.openDrawer(Gravity.START)
        }

        // 所有控件初始化完成后再刷新 UI 状态
        refreshAgentModeUI()

        // DrawerLayout push 效果：侧边栏滑出时，主内容跟着平移
        val drawerPanel = findViewById<LinearLayout>(R.id.drawerPanel)
        drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                if (drawerView.id == R.id.drawerPanel) {
                    // 右侧侧边栏：主内容向左平移
                    mainContent.translationX = -(drawerPanel.width * slideOffset)
                } else if (drawerView.id == R.id.fileTreePanel) {
                    // 左侧文件树：主内容向右平移
                    mainContent.translationX = fileTreePanel.width * slideOffset
                }
            }

            override fun onDrawerClosed(drawerView: View) {
                mainContent.translationX = 0f
            }

            override fun onDrawerOpened(drawerView: View) {
                // 文件树抽屉打开时自动刷新
                if (drawerView.id == R.id.fileTreePanel) {
                    val path = ProviderManager.getWorkspacePath(this@MainActivity)
                    if (path.isNotEmpty()) {
                        fileTreeAdapter.loadRoot(path)
                    }
                }
            }
        })

        // 不显示阴影遮罩
        drawerLayout.setScrimColor(0x00000000)

        // 打开侧边栏
        findViewById<ImageButton>(R.id.btnDrawer).setOnClickListener {
            drawerLayout.openDrawer(Gravity.END)
        }

        // 设置
        findViewById<TextView>(R.id.btnSettings).setOnClickListener {
            drawerLayout.closeDrawer(Gravity.END)
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // 新对话
        findViewById<TextView>(R.id.btnNewChat).setOnClickListener {
            startNewConversation()
            drawerLayout.closeDrawer(Gravity.END)
        }

        // 历史对话列表
        rvDrawerConversations.layoutManager = LinearLayoutManager(this)
        rvDrawerConversations.adapter = drawerAdapter

        drawerAdapter.onItemClick = { info ->
            loadConversation(info.id)
            drawerLayout.closeDrawer(Gravity.END)
        }
        drawerAdapter.onDeleteClick = { info ->
            AlertDialog.Builder(this)
                .setMessage("确定删除这个对话吗？")
                .setPositiveButton("删除") { _, _ ->
                    CoroutineScope(Dispatchers.IO).launch {
                        conversationRepo.deleteConversation(info.id)
                        if (info.id == currentConversationId) {
                            withContext(Dispatchers.Main) { startNewConversation() }
                        }
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }

        // 实时观察对话列表（按工作区隔离）
        subscribeConversations()

        btnSend.setOnClickListener {
            if (isGenerating) {
                // 停止当前生成：取消协程，已渲染文字保留，按钮立即切回发送
                currentJob?.cancel()
                updateSendButtonState(ButtonState.IDLE)
            } else {
                val msg = etInput.text.toString().trim()
                if (msg.isNotEmpty()) sendMessage(msg)
            }
        }

        intent.getStringExtra(EXTRA_CONVERSATION_ID)?.let { loadConversation(it) }
            ?: run {
                // 恢复上次的会话（仅当对话属于当前工作区时才恢复）
                val lastId = ProviderManager.getLastConversationId(this)
                if (lastId.isNotEmpty()) {
                    val currentWs = if (ProviderManager.isAgentMode(this))
                        ProviderManager.getWorkspacePath(this) else ""
                    CoroutineScope(Dispatchers.IO).launch {
                        val info = conversationRepo.getConversation(lastId)
                        if (info != null && info.workspacePath == currentWs) {
                            withContext(Dispatchers.Main) { loadConversation(lastId) }
                        }
                    }
                }
            }

        // APP 启动时后台预连接 MCP 服务器
        preconnectMcpServers()
    }

    override fun onResume() {
        super.onResume()
        refreshAgentModeUI()
        refreshModelSelector()
    }

    override fun onPause() {
        super.onPause()
        // 保存当前会话 ID，下次恢复时自动加载
        ProviderManager.setLastConversationId(this, currentConversationId ?: "")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_CONVERSATION_ID)?.let { loadConversation(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        clientRouter.close()
        webSearchTool?.close()
        fetchUrlTool?.close()
        tracing?.close()
        mcpClients.forEach { try { it.close() } catch (_: Exception) {} }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(Gravity.START)) {
            drawerLayout.closeDrawer(Gravity.START)
        } else if (drawerLayout.isDrawerOpen(Gravity.END)) {
            drawerLayout.closeDrawer(Gravity.END)
        } else {
            super.onBackPressed()
        }
    }

    // ========== Agent 模式 ==========

    /**
     * 订阅对话列表（按工作区隔离）
     * Agent 模式：只显示当前工作区的对话
     * Chat 模式：只显示 workspacePath 为空的对话
     */
    private fun subscribeConversations() {
        conversationObserverJob?.cancel()
        val isAgent = ProviderManager.isAgentMode(this)
        val workspacePath = if (isAgent) ProviderManager.getWorkspacePath(this) else ""
        conversationObserverJob = CoroutineScope(Dispatchers.Main).launch {
            conversationRepo.observeConversationsByWorkspace(workspacePath).collectLatest { list ->
                drawerAdapter.submitList(list)
                tvDrawerEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun refreshAgentModeUI() {
        val isAgent = ProviderManager.isAgentMode(this)
        if (isAgent) {
            tvModeLabel.text = "Agent"
            tvModeLabel.setTextColor(resources.getColor(R.color.white, null))
            tvModeLabel.setBackgroundResource(R.drawable.bg_agent_mode)
            layoutWorkspace.visibility = View.VISIBLE
            btnFileTree.visibility = View.VISIBLE
            val path = ProviderManager.getWorkspacePath(this)
            if (path.isNotEmpty()) {
                tvWorkspacePath.text = path
                btnCloseWorkspace.text = "关闭"
                btnCloseWorkspace.visibility = View.VISIBLE
                // 加载文件树
                val folderName = java.io.File(path).name
                tvFileTreeRoot.text = folderName
                fileTreeAdapter.loadRoot(path)
            } else {
                tvWorkspacePath.text = "未选择工作区"
                btnCloseWorkspace.visibility = View.GONE
            }
        } else {
            tvModeLabel.text = "Chat"
            tvModeLabel.setTextColor(resources.getColor(R.color.text_secondary, null))
            tvModeLabel.setBackgroundResource(R.drawable.bg_input)
            layoutWorkspace.visibility = View.VISIBLE
            tvWorkspacePath.text = "普通聊天"
            btnCloseWorkspace.visibility = View.VISIBLE
            btnCloseWorkspace.text = "退出"
            btnFileTree.visibility = View.GONE
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.START)
        }
        // Agent 模式解锁左侧抽屉
        if (isAgent) {
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.START)
        }
        // 工作区切换时：如果当前对话不属于新工作区，关闭当前会话界面
        val currentWs = if (isAgent) ProviderManager.getWorkspacePath(this) else ""
        if (currentConversationId != null) {
            CoroutineScope(Dispatchers.IO).launch {
                val info = conversationRepo.getConversation(currentConversationId!!)
                if (info == null || info.workspacePath != currentWs) {
                    withContext(Dispatchers.Main) { startNewConversation() }
                }
            }
        }
        // 重新订阅对话列表（按当前工作区过滤）
        subscribeConversations()
    }

    /**
     * 刷新底部模型选择器：更新当前模型名显示
     */
    private fun refreshModelSelector() {
        val activeId = ProviderManager.getActiveId(this)
        if (activeId == null) {
            tvCurrentModel.text = "未配置"
            return
        }
        val currentModel = overrideModel ?: ProviderManager.getModel(this, activeId)
        tvCurrentModel.text = "${shortenModelName(currentModel).ifEmpty { "未选择模型" }} \u02C7"
    }

    /**
     * 在模型名上方弹出浮动列表（PopupWindow，不是 Dialog）
     */
    private fun showModelPopup(anchor: View) {
        val activeId = ProviderManager.getActiveId(this) ?: return
        val currentModel = overrideModel ?: ProviderManager.getModel(this, activeId)
        val selectedModels = ProviderManager.getSelectedModels(this, activeId)
        val modelList = if (selectedModels.isEmpty()) {
            if (currentModel.isNotEmpty()) listOf(currentModel) else return
        } else {
            if (currentModel.isNotEmpty() && currentModel !in selectedModels) {
                listOf(currentModel) + selectedModels
            } else {
                selectedModels
            }
        }
        if (modelList.size <= 1) return

        val displayNames = modelList.map { shortenModelName(it) }

        val listPopup = androidx.appcompat.widget.ListPopupWindow(this)
        listPopup.anchorView = anchor
        listPopup.isModal = true
        listPopup.setDropDownGravity(android.view.Gravity.END)
        listPopup.setBackgroundDrawable(resources.getDrawable(R.drawable.bg_popup_list, null))

        // 自适应宽度：取最宽的条目
        val adapter = object : android.widget.ArrayAdapter<String>(this, 0, displayNames) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val tv = (convertView as? TextView) ?: TextView(context).apply {
                    textSize = 14f
                    setPadding(dp(16), dp(10), dp(16), dp(10))
                }
                tv.text = displayNames[position]
                val isActive = modelList[position] == currentModel
                tv.setTextColor(resources.getColor(
                    if (isActive) R.color.accent else R.color.text_primary, null
                ))
                return tv
            }
        }
        listPopup.setAdapter(adapter)

        // 计算内容宽度
        var maxWidth = 0
        for (i in displayNames.indices) {
            val itemView = adapter.getView(i, null, android.widget.FrameLayout(this))
            itemView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            maxWidth = maxOf(maxWidth, itemView.measuredWidth)
        }
        listPopup.width = maxWidth + dp(8)

        // 向上弹出
        listPopup.setOnItemClickListener { _, _, position, _ ->
            val model = modelList[position]
            overrideModel = model
            val key = ProviderManager.getApiKey(this, activeId) ?: ""
            val baseUrl = ProviderManager.getBaseUrl(this, activeId)
            ProviderManager.saveConfig(this, activeId, key, baseUrl, model)
            refreshModelSelector()
            listPopup.dismiss()
        }

        // 在 anchor 上方显示
        val itemHeight = dp(40)
        listPopup.verticalOffset = -(modelList.size * itemHeight + anchor.height)
        listPopup.horizontalOffset = 0
        listPopup.show()
    }

    /** 缩短模型名用于显示 */
    private fun shortenModelName(model: String): String {
        return model.substringAfterLast("/")
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun closeWorkspace() {
        // Agent 模式：先保存当前会话到当前工作区，再清空路径
        if (ProviderManager.isAgentMode(this)) {
            ProviderManager.setLastConversationId(this, currentConversationId ?: "")

            val uriStr = ProviderManager.getWorkspaceUri(this)
            if (uriStr.isNotEmpty()) {
                try {
                    val uri = android.net.Uri.parse(uriStr)
                    contentResolver.releasePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: Exception) {}
            }
            ProviderManager.setWorkspacePath(this, "")
            ProviderManager.setWorkspaceUri(this, "")
        }
        ProviderManager.setAgentMode(this, false)
        ProviderManager.setLastSession(this, false)
        // 返回到启动页
        val intent = Intent(this, LauncherActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }

    // ========== 对话逻辑 ==========

    private fun startNewConversation() {
        currentConversationId = null
        messageHistory.clear()
        tvOutput.text = ""
    }

    private fun loadConversation(conversationId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val info = conversationRepo.getConversation(conversationId)
            val timedMessages = conversationRepo.getTimedMessages(conversationId)
            val messages = conversationRepo.getMessages(conversationId)
            val usageList = conversationRepo.getUsageList(conversationId)
            withContext(Dispatchers.Main) {
                if (info == null) {
                    Toast.makeText(this@MainActivity, "对话不存在", Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                currentConversationId = conversationId
                messageHistory.clear()
                // 只将 user/assistant/system/tool 消息加入 LLM 上下文，排除 agent_log
                messageHistory.addAll(messages.filter { it.role != "agent_log" })

                val sb = StringBuilder()
                var usageIndex = 0
                for (msg in timedMessages) {
                    val time = formatTime(msg.createdAt)
                    when (msg.role) {
                        "user" -> sb.append("You: ${msg.content}\n$time\n\n")
                        "agent_log" -> {
                            // 渲染中间过程日志（thinking, tool calls, trace, events 等）
                            sb.append(msg.content)
                            if (!msg.content.endsWith("\n")) sb.append("\n")
                        }
                        "assistant" -> {
                            sb.append("AI: ${msg.content}")
                            val usage = usageList.getOrNull(usageIndex)
                            if (usage != null) {
                                sb.append("\n[提示: ${usage.promptTokens} tokens | 回复: ${usage.completionTokens} tokens | 总计: ${usage.totalTokens} tokens]")
                            }
                            sb.append("\n$time\n\n")
                            usageIndex++
                        }
                    }
                }
                tvOutput.setText(sb.toString(), android.widget.TextView.BufferType.NORMAL)
                autoScrollToBottom()
            }

            // 启动恢复：检查是否有未完成的检查点（非墓碑），提示用户恢复
            if (ProviderManager.isSnapshotEnabled(this@MainActivity)
                && ProviderManager.getSnapshotStorage(this@MainActivity) == ProviderManager.SnapshotStorage.FILE) {
                tryOfferStartupRecovery(conversationId)
            }
        }
    }

    private fun getOrCreateClient(config: ProviderManager.ActiveConfig): ChatClient {
        // 如果 router 中已有该供应商的客户端，直接复用
        val existing = clientRouter.getClient(config.providerId)
        if (existing != null && currentProviderId == config.providerId) {
            return existing
        }

        // 配置变更，移除旧客户端
        if (currentProviderId != null && currentProviderId != config.providerId) {
            clientRouter.unregister(currentProviderId!!)
        }
        
        val provider = ProviderManager.getProvider(config.providerId)
        
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
            requestTimeoutMs = ProviderManager.getRequestTimeout(this).toLong() * 1000,
            connectTimeoutMs = ProviderManager.getConnectTimeout(this).toLong() * 1000,
            socketTimeoutMs = ProviderManager.getSocketTimeout(this).toLong() * 1000
        )
        val client = ChatClientFactory.create(clientConfig)

        clientRouter.register(config.providerId, client)
        currentProviderId = config.providerId

        // 根据模型元数据自动配置上下文窗口
        val llmProvider = client.provider
        val modelMeta = ModelRegistry.find(config.model)
        contextManager = if (modelMeta != null) {
            ContextManager.fromModel(modelMeta)
        } else {
            ContextManager.forModel(config.model, llmProvider)
        }

        return client
    }

    /** 更新发送按钮状态 */
    private fun updateSendButtonState(state: ButtonState) {
        when (state) {
            ButtonState.IDLE -> {
                isGenerating = false
                btnSend.text = "↑"
                btnSend.backgroundTintList = ColorStateList.valueOf(getColor(R.color.accent))
                btnSend.isEnabled = true
            }
            ButtonState.GENERATING, ButtonState.COMPRESSING -> {
                isGenerating = true
                btnSend.text = "■"
                btnSend.backgroundTintList = ColorStateList.valueOf(getColor(R.color.generating_green))
                btnSend.isEnabled = true
            }
        }
    }

    private enum class ButtonState { IDLE, GENERATING, COMPRESSING }

    private fun sendMessage(message: String) {
        val config = ProviderManager.getActiveConfig(this)
        if (config == null) {
            Toast.makeText(this, "请先在设置中配置模型供应商", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }

        val actualModel = overrideModel ?: config.model
        if (actualModel.isEmpty()) {
            Toast.makeText(this, "请先选择模型", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, ProviderConfigActivity::class.java).apply {
                putExtra("provider_id", config.providerId)
                putExtra("tab", 1)
            })
            return
        }

        updateSendButtonState(ButtonState.GENERATING)
        userScrolledUp = false
        val now = formatTime(System.currentTimeMillis())
        tvOutput.append("You: $message\n$now\n\n")
        etInput.text.clear()

        val client = getOrCreateClient(config)
        val userMsg = ChatMessage.user(message)

        currentJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val toolsEnabled = ProviderManager.isToolsEnabled(this@MainActivity)

                // Agent 模式：先构建工具注册表，再刷新上下文（确保工具列表包含在 system prompt 中）
                val registry = if (toolsEnabled) {
                    val r = buildToolRegistry()
                    loadMcpToolsInto(r)
                    r
                } else null

                // 刷新上下文收集器（工作区、系统信息、时间等，工具通过 API tools 参数发送）
                refreshContextCollector()

                if (currentConversationId == null) {
                    val title = if (message.length > 20) message.substring(0, 20) + "..." else message
                    val basePrompt = ProviderManager.getDefaultSystemPrompt(this@MainActivity)
                    // 自动拼接环境上下文到 system prompt（工作区路径、系统信息、时间等）
                    val systemPrompt = contextCollector.buildSystemPrompt(basePrompt)
                    currentConversationId = conversationRepo.createConversation(
                        title = title,
                        providerId = config.providerId,
                        model = config.model,
                        systemPrompt = systemPrompt,
                        workspacePath = if (ProviderManager.isAgentMode(this@MainActivity))
                            ProviderManager.getWorkspacePath(this@MainActivity) else ""
                    )
                    val systemMsg = ChatMessage.system(systemPrompt)
                    messageHistory.add(systemMsg)
                    conversationRepo.addMessage(currentConversationId!!, systemMsg)
                } else {
                    // 已有对话：更新 system prompt 中的动态上下文（工具列表、时间等）
                    val basePrompt = ProviderManager.getDefaultSystemPrompt(this@MainActivity)
                    val updatedSystemPrompt = contextCollector.buildSystemPrompt(basePrompt)
                    if (messageHistory.isNotEmpty() && messageHistory[0].role == "system") {
                        messageHistory[0] = ChatMessage.system(updatedSystemPrompt)
                    }
                }

                messageHistory.add(userMsg)
                conversationRepo.addMessage(currentConversationId!!, userMsg)

                // 构建追踪系统
                tracing?.close()
                tracing = buildTracing()

                // 上下文窗口裁剪，避免超出模型 token 限制
                val trimmedMessages = contextManager.trimMessages(messageHistory.toList())

                val maxTokensVal = ProviderManager.getMaxTokens(this@MainActivity)
                val maxTokens = if (maxTokensVal > 0) maxTokensVal else 8192

                // 采样参数
                val tempVal = ProviderManager.getTemperature(this@MainActivity)
                val topPVal = ProviderManager.getTopP(this@MainActivity)
                val topKVal = ProviderManager.getTopK(this@MainActivity)
                val samplingParams = com.lhzkml.jasmine.core.prompt.model.SamplingParams(
                    temperature = if (tempVal >= 0f) tempVal.toDouble() else null,
                    topP = if (topPVal >= 0f) topPVal.toDouble() else null,
                    topK = if (topKVal >= 0) topKVal else null
                )

                val result: String
                var usage: Usage? = null
                // 重置中间过程日志收集器
                agentLogBuilder = StringBuilder()

                // 构建快照/持久化（所有模式通用，每轮对话结束后创建检查点）
                persistence = buildPersistence()

                if (toolsEnabled && registry != null) {
                    // Agent 模式：使用 ToolExecutor 自动循环
                    val listener = object : AgentEventListener {
                        var thinkingStarted = false
                        override suspend fun onToolCallStart(toolName: String, arguments: String) {
                            withContext(Dispatchers.Main) {
                                if (thinkingStarted) {
                                    tvOutput.append("\n")
                                    agentLogBuilder.append("\n")
                                    thinkingStarted = false
                                }
                                val argsPreview = if (arguments.length > 80) arguments.take(80) + "…" else arguments
                                val line = "\n[Tool] 调用工具: $toolName($argsPreview)\n"
                                appendRendered(line)
                                agentLogBuilder.append(line)
                                autoScrollToBottom()
                            }
                        }
                        override suspend fun onToolCallResult(toolName: String, result: String) {
                            withContext(Dispatchers.Main) {
                                val preview = if (result.length > 200) result.take(200) + "…" else result
                                val line = "[Result] $toolName 结果: $preview\n\n"
                                appendRendered(line)
                                agentLogBuilder.append(line)
                                autoScrollToBottom()
                            }
                        }
                        override suspend fun onThinking(content: String) {
                            withContext(Dispatchers.Main) {
                                if (!thinkingStarted) {
                                    appendRendered("[Think] ")
                                    agentLogBuilder.append("[Think] ")
                                    thinkingStarted = true
                                }
                                tvOutput.append(content)
                                agentLogBuilder.append(content)
                                autoScrollToBottom()
                            }
                        }
                    }
                    val executor = ToolExecutor(client, registry, eventListener = listener, tracing = tracing)

                    // 构建事件处理器
                    eventHandler = buildEventHandler()

                    // 触发 Agent 开始事件
                    val agentRunId = tracing?.newRunId() ?: java.util.UUID.randomUUID().toString()
                    eventHandler?.fireAgentStarting(AgentStartingContext(
                        runId = agentRunId,
                        agentId = currentConversationId ?: "unknown",
                        model = config.model,
                        toolCount = registry.descriptors().size
                    ))

                    // 任务规划（Agent 模式下可选）— 使用 SimpleLLMPlanner 结构化输出
                    if (ProviderManager.isPlannerEnabled(this@MainActivity)) {
                        try {
                            val planPrompt = Prompt.build("planner") {
                                for (msg in trimmedMessages) {
                                    when (msg.role) {
                                        "system" -> system(msg.content)
                                        "user" -> user(msg.content)
                                        "assistant" -> assistant(msg.content)
                                    }
                                }
                            }
                            val planSession = LLMWriteSession(client, config.model, planPrompt)
                            val planReadSession = com.lhzkml.jasmine.core.prompt.llm.LLMReadSession(client, config.model, planPrompt)
                            val planContext = AgentGraphContext(
                                agentId = currentConversationId ?: "planner",
                                runId = agentRunId,
                                client = client,
                                model = config.model,
                                session = planSession,
                                readSession = planReadSession,
                                toolRegistry = registry,
                                environment = GenericAgentEnvironment(
                                    currentConversationId ?: "planner", registry
                                ),
                                tracing = tracing
                            )

                            val maxIter = ProviderManager.getPlannerMaxIterations(this@MainActivity)
                            val planner = if (ProviderManager.isPlannerCriticEnabled(this@MainActivity)) {
                                SimpleLLMWithCriticPlanner(maxIterations = maxIter)
                            } else {
                                SimpleLLMPlanner(maxIterations = maxIter)
                            }
                            val plan = planner.buildPlanPublic(planContext, message, null)
                            planSession.close()
                            planReadSession.close()

                            withContext(Dispatchers.Main) {
                                appendRendered("[Plan] 任务规划:\n")
                                appendRendered("[Goal] 目标: ${plan.goal}\n")
                                agentLogBuilder.append("[Plan] 任务规划:\n")
                                agentLogBuilder.append("[Goal] 目标: ${plan.goal}\n")
                                plan.steps.forEachIndexed { index, step ->
                                    val stepLine = "  ${index + 1}. ${step.description}\n"
                                    appendRendered(stepLine)
                                    agentLogBuilder.append(stepLine)
                                }
                                tvOutput.append("\n")
                                agentLogBuilder.append("\n")
                                autoScrollToBottom()
                            }

                            // 快照：规划完成后不再单独创建检查点，统一在对话轮次结束后创建
                        } catch (e: Exception) {
                            // 规划失败不影响正常执行
                            withContext(Dispatchers.Main) {
                                val line = "[Plan] [规划跳过: ${e.message}]\n\n"
                                appendRendered(line)
                                agentLogBuilder.append(line)
                                autoScrollToBottom()
                            }
                        }
                    }

                    val agentStrategy = ProviderManager.getAgentStrategy(this@MainActivity)

                    when (agentStrategy) {
                        ProviderManager.AgentStrategyType.SIMPLE_LOOP -> {
                            // 简单循环模式：使用 ToolExecutor（流式）
                            withContext(Dispatchers.Main) {
                                appendRendered("AI: ")
                            }
                            val streamResult = executor.executeStream(
                                trimmedMessages, config.model, maxTokens, samplingParams
                            ) { chunk ->
                                withContext(Dispatchers.Main) {
                                    tvOutput.append(chunk)
                                    autoScrollToBottom()
                                }
                            }
                            result = streamResult.content
                            usage = streamResult.usage
                            withContext(Dispatchers.Main) {
                                appendRendered(formatUsageLine(usage))
                                autoScrollToBottom()
                            }
                        }

                        ProviderManager.AgentStrategyType.SINGLE_RUN_GRAPH -> {
                            // 图策略模式：使用 GraphAgent + PredefinedStrategies（流式）
                            // 读取工具调用模式设置
                            val toolCallMode = when (ProviderManager.getGraphToolCallMode(this@MainActivity)) {
                                ProviderManager.GraphToolCallMode.SEQUENTIAL -> ToolCalls.SEQUENTIAL
                                ProviderManager.GraphToolCallMode.PARALLEL -> ToolCalls.PARALLEL
                                ProviderManager.GraphToolCallMode.SINGLE_RUN_SEQUENTIAL -> ToolCalls.SINGLE_RUN_SEQUENTIAL
                            }

                            // 读取工具选择策略设置
                            val toolSelStrategy = when (ProviderManager.getToolSelectionStrategy(this@MainActivity)) {
                                ProviderManager.ToolSelectionStrategyType.ALL -> ToolSelectionStrategy.ALL
                                ProviderManager.ToolSelectionStrategyType.NONE -> ToolSelectionStrategy.NONE
                                ProviderManager.ToolSelectionStrategyType.BY_NAME -> {
                                    val names = ProviderManager.getToolSelectionNames(this@MainActivity)
                                    if (names.isNotEmpty()) ToolSelectionStrategy.ByName(names) else ToolSelectionStrategy.ALL
                                }
                                ProviderManager.ToolSelectionStrategyType.AUTO_SELECT_FOR_TASK -> {
                                    val desc = ProviderManager.getToolSelectionTaskDesc(this@MainActivity)
                                    if (desc.isNotEmpty()) ToolSelectionStrategy.AutoSelectForTask(desc) else ToolSelectionStrategy.ALL
                                }
                            }

                            val strategy = PredefinedStrategies.singleRunStreamStrategy(toolCallMode, toolSelStrategy)

                            val graphAgent = GraphAgent(
                                client = client,
                                model = config.model,
                                strategy = strategy,
                                toolRegistry = registry,
                                tracing = tracing,
                                agentId = currentConversationId ?: "graph-agent"
                            )

                            // 构建初始 Prompt（系统提示 + 历史消息，不含最后一条 user）
                            val graphPrompt = Prompt.build("graph-agent") {
                                for (msg in trimmedMessages.dropLast(1)) {
                                    when (msg.role) {
                                        "system" -> system(msg.content)
                                        "user" -> user(msg.content)
                                        "assistant" -> assistant(msg.content)
                                    }
                                }
                            }.copy(maxTokens = maxTokens, samplingParams = samplingParams)

                            // 读取 ToolChoice 设置并应用到 Prompt
                            val toolChoiceMode = ProviderManager.getToolChoiceMode(this@MainActivity)
                            val toolChoice: com.lhzkml.jasmine.core.prompt.model.ToolChoice? = when (toolChoiceMode) {
                                ProviderManager.ToolChoiceMode.DEFAULT -> null
                                ProviderManager.ToolChoiceMode.AUTO -> com.lhzkml.jasmine.core.prompt.model.ToolChoice.Auto
                                ProviderManager.ToolChoiceMode.REQUIRED -> com.lhzkml.jasmine.core.prompt.model.ToolChoice.Required
                                ProviderManager.ToolChoiceMode.NONE -> com.lhzkml.jasmine.core.prompt.model.ToolChoice.None
                                ProviderManager.ToolChoiceMode.NAMED -> {
                                    val name = ProviderManager.getToolChoiceNamedTool(this@MainActivity)
                                    if (name.isNotEmpty()) com.lhzkml.jasmine.core.prompt.model.ToolChoice.Named(name) else null
                                }
                            }
                            val finalGraphPrompt = if (toolChoice != null) graphPrompt.withToolChoice(toolChoice) else graphPrompt

                            // 显示图策略流程头
                            withContext(Dispatchers.Main) {
                                val header = "┌─ [Graph] 图策略执行 ─────────────\n│ [>] Start\n"
                                appendRendered(header)
                                agentLogBuilder.append(header)
                                autoScrollToBottom()
                            }

                            withContext(Dispatchers.Main) {
                                appendRendered("└─────────────────────────\n\nAI: ")
                                agentLogBuilder.append("└─────────────────────────\n")
                            }

                            val chunkCallback: (suspend (String) -> Unit)? = { chunk: String ->
                                withContext(Dispatchers.Main) {
                                    tvOutput.append(chunk)
                                    autoScrollToBottom()
                                }
                            }

                            var graphThinkingStarted = false
                            val thinkingCallback: (suspend (String) -> Unit)? = { text: String ->
                                withContext(Dispatchers.Main) {
                                    if (!graphThinkingStarted) {
                                        appendRendered("[Think] ")
                                        agentLogBuilder.append("[Think] ")
                                        graphThinkingStarted = true
                                    }
                                    tvOutput.append(text)
                                    agentLogBuilder.append(text)
                                    autoScrollToBottom()
                                }
                            }

                            // 节点生命周期回调 — 在聊天中渲染可视化节点卡片
                            val nodeEnterCallback: suspend (String) -> Unit = { nodeName ->
                                val icon = when {
                                    nodeName.contains("LLM", true) -> "[LLM]"
                                    nodeName.contains("Tool", true) -> "[Tool]"
                                    nodeName.contains("Send", true) -> "[Send]"
                                    else -> "[Node]"
                                }
                                val line = "│ $icon $nodeName ...\n"
                                agentLogBuilder.append(line)
                                withContext(Dispatchers.Main) {
                                    appendRendered(line)
                                    autoScrollToBottom()
                                }
                            }

                            val nodeExitCallback: suspend (String, Boolean) -> Unit = { nodeName, success ->
                                val status = if (success) "[OK]" else "[FAIL]"
                                val line = "│ $status $nodeName 完成\n"
                                agentLogBuilder.append(line)
                                withContext(Dispatchers.Main) {
                                    appendRendered(line)
                                    autoScrollToBottom()
                                }
                            }

                            val edgeCallback: suspend (String, String, String) -> Unit = { from, to, label ->
                                val labelStr = if (label.isNotEmpty()) " ($label)" else ""
                                val line = "│  ↓ $from → $to$labelStr\n"
                                agentLogBuilder.append(line)
                                withContext(Dispatchers.Main) {
                                    appendRendered(line)
                                    autoScrollToBottom()
                                }
                            }

                            val graphResult = graphAgent.runWithCallbacks(
                                prompt = finalGraphPrompt,
                                input = message,
                                onChunk = chunkCallback,
                                onThinking = thinkingCallback,
                                onToolCallStart = { toolName, args ->
                                    val argsPreview = if (args.length > 80) args.take(80) + "…" else args
                                    val line = "│  [Tool] $toolName($argsPreview)\n"
                                    agentLogBuilder.append(line)
                                    withContext(Dispatchers.Main) {
                                        appendRendered(line)
                                        autoScrollToBottom()
                                    }
                                },
                                onToolCallResult = { toolName, toolResult ->
                                    val preview = if (toolResult.length > 200) toolResult.take(200) + "…" else toolResult
                                    val line = "│  [Result] $toolName -> $preview\n"
                                    agentLogBuilder.append(line)
                                    withContext(Dispatchers.Main) {
                                        appendRendered(line)
                                        autoScrollToBottom()
                                    }
                                },
                                onNodeEnter = nodeEnterCallback,
                                onNodeExit = nodeExitCallback,
                                onEdge = edgeCallback
                            )

                            result = graphResult ?: ""

                            withContext(Dispatchers.Main) {
                                appendRendered(formatUsageLine(null))
                                autoScrollToBottom()
                            }
                        }
                    }

                    // 触发 Agent 完成事件
                    eventHandler?.fireAgentCompleted(AgentCompletedContext(
                        runId = agentRunId,
                        agentId = currentConversationId ?: "unknown",
                        result = result.take(200),
                        totalIterations = 0
                    ))
                } else {
                    // 普通流式输出（支持超时续传）
                    withContext(Dispatchers.Main) {
                        appendRendered("AI: ")
                    }

                    var thinkingStarted = false
                    val resumeEnabled = ProviderManager.isStreamResumeEnabled(this@MainActivity)

                    val streamResult = if (resumeEnabled) {
                        val helper = StreamResumeHelper(
                            maxResumes = ProviderManager.getStreamResumeMaxRetries(this@MainActivity)
                        )
                        helper.streamWithResume(
                            client = client,
                            messages = trimmedMessages,
                            model = config.model,
                            maxTokens = maxTokens,
                            samplingParams = samplingParams,
                            onChunk = { chunk ->
                                withContext(Dispatchers.Main) {
                                    if (thinkingStarted) {
                                        appendRendered("\n\nAI: ")
                                        thinkingStarted = false
                                    }
                                    tvOutput.append(chunk)
                                    autoScrollToBottom()
                                }
                            },
                            onThinking = { text ->
                                withContext(Dispatchers.Main) {
                                    if (!thinkingStarted) {
                                        appendRendered("[Think] ")
                                        agentLogBuilder.append("[Think] ")
                                        thinkingStarted = true
                                    }
                                    tvOutput.append(text)
                                    agentLogBuilder.append(text)
                                    autoScrollToBottom()
                                }
                            },
                            onResumeAttempt = { attempt ->
                                withContext(Dispatchers.Main) {
                                    appendRendered("\n[网络超时，正在续传... 第${attempt}次]\n")
                                    autoScrollToBottom()
                                }
                            }
                        )
                    } else {
                        client.chatStreamWithUsageAndThinking(
                            trimmedMessages, config.model, maxTokens, samplingParams,
                            onChunk = { chunk ->
                                withContext(Dispatchers.Main) {
                                    if (thinkingStarted) {
                                        appendRendered("\n\nAI: ")
                                        thinkingStarted = false
                                    }
                                    tvOutput.append(chunk)
                                    autoScrollToBottom()
                                }
                            },
                            onThinking = { text ->
                                withContext(Dispatchers.Main) {
                                    if (!thinkingStarted) {
                                        appendRendered("[Think] ")
                                        agentLogBuilder.append("[Think] ")
                                        thinkingStarted = true
                                    }
                                    tvOutput.append(text)
                                    agentLogBuilder.append(text)
                                    autoScrollToBottom()
                                }
                            }
                        )
                    }

                    result = streamResult.content
                    usage = streamResult.usage

                    withContext(Dispatchers.Main) {
                        appendRendered(formatUsageLine(usage))
                        autoScrollToBottom()
                    }
                }

                val assistantMsg = ChatMessage.assistant(result)
                messageHistory.add(assistantMsg)

                // 快照：每轮完整对话（user + assistant）结束后创建检查点
                // 一个检查点 = 当轮的对话交互（user + assistant），恢复时拼接之前所有检查点重建历史
                persistence?.createCheckpoint(
                    agentId = currentConversationId ?: "unknown",
                    nodePath = "turn:${messageHistory.count { it.role == "user" }}",
                    lastInput = message,
                    messageHistory = listOf(userMsg, assistantMsg)
                )

                // 保存中间过程日志（thinking, tool calls, trace, events, graph flow 等）
                val logContent = agentLogBuilder.toString()
                if (logContent.isNotBlank()) {
                    conversationRepo.addMessage(currentConversationId!!, ChatMessage("agent_log", logContent))
                }

                conversationRepo.addMessage(currentConversationId!!, assistantMsg)

                // 记录 token 用量
                if (usage != null) {
                    conversationRepo.recordUsage(
                        conversationId = currentConversationId!!,
                        providerId = config.providerId,
                        model = config.model,
                        usage = usage
                    )
                }

                // 智能上下文压缩
                if (ProviderManager.isCompressionEnabled(this@MainActivity)) {
                    withContext(Dispatchers.Main) {
                        updateSendButtonState(ButtonState.COMPRESSING)
                    }
                    tryCompressHistory(client, config.model)
                }
            } catch (e: CancellationException) {
                // 用户主动停止，已渲染文字保留，不追加任何内容
            } catch (e: ChatClientException) {
                val errorMsg = when (e.errorType) {
                    ErrorType.NETWORK -> "网络错误: ${e.message}\n请检查网络连接后重试"
                    ErrorType.AUTHENTICATION -> "认证失败: ${e.message}\n请检查 API Key 是否正确"
                    ErrorType.RATE_LIMIT -> "请求过于频繁: ${e.message}\n请稍后再试"
                    ErrorType.MODEL_UNAVAILABLE -> "模型不可用: ${e.message}\n请检查模型名称或稍后重试"
                    ErrorType.INVALID_REQUEST -> "请求参数错误: ${e.message}"
                    ErrorType.SERVER_ERROR -> "服务器错误: ${e.message}\n请稍后重试"
                    else -> "错误: ${e.message}"
                }
                // 触发 Agent 失败事件
                eventHandler?.fireAgentExecutionFailed(AgentExecutionFailedContext(
                    runId = tracing?.newRunId() ?: "",
                    agentId = currentConversationId ?: "unknown",
                    throwable = e
                ))
                withContext(Dispatchers.Main) {
                    appendRendered("\n$errorMsg\n\n")
                    autoScrollToBottom()
                }
                // 快照恢复：检查是否有可用检查点
                tryOfferCheckpointRecovery(e, message)
            } catch (e: Exception) {
                // 触发 Agent 失败事件
                eventHandler?.fireAgentExecutionFailed(AgentExecutionFailedContext(
                    runId = tracing?.newRunId() ?: "",
                    agentId = currentConversationId ?: "unknown",
                    throwable = e
                ))
                withContext(Dispatchers.Main) {
                    appendRendered("\n未知错误: ${e.message}\n\n")
                    autoScrollToBottom()
                }
                // 快照恢复：检查是否有可用检查点
                tryOfferCheckpointRecovery(e, message)
            } finally {
                withContext(Dispatchers.Main + kotlinx.coroutines.NonCancellable) {
                    updateSendButtonState(ButtonState.IDLE)
                    currentJob = null
                }
            }
        }
    }

    /**
     * 快照恢复：执行失败时，检查是否有可用检查点，弹窗询问用户恢复到某一轮对话。
     * 每个检查点只保存当轮的 user + assistant，恢复时拼接 system prompt + 所有检查点到选中轮次。
     */
    private suspend fun tryOfferCheckpointRecovery(error: Exception, originalMessage: String) {
        val p = persistence ?: return
        val agentId = currentConversationId ?: return

        val allCheckpoints = p.getCheckpoints(agentId)
        if (allCheckpoints.isEmpty()) return

        val rollbackStrategy = ProviderManager.getSnapshotRollbackStrategy(this@MainActivity)
        val strategyName = when (rollbackStrategy) {
            com.lhzkml.jasmine.core.agent.tools.snapshot.RollbackStrategy.RESTART_FROM_NODE -> "重新执行"
            com.lhzkml.jasmine.core.agent.tools.snapshot.RollbackStrategy.SKIP_NODE -> "仅恢复上下文"
            com.lhzkml.jasmine.core.agent.tools.snapshot.RollbackStrategy.USE_DEFAULT_OUTPUT -> "使用默认输出"
        }

        val timeFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        val sortedCps = allCheckpoints.sortedBy { it.createdAt }
        val sortedCpsDesc = sortedCps.reversed()
        val checkpointLabels = sortedCpsDesc.map { cp ->
            val time = timeFormat.format(java.util.Date(cp.createdAt))
            val userMsg = cp.messageHistory.firstOrNull { it.role == "user" }?.content?.take(30) ?: ""
            "[$time] ${cp.nodePath} - $userMsg"
        }.toTypedArray()

        val deferred = CompletableDeferred<Int?>()
        withContext(Dispatchers.Main) {
            AlertDialog.Builder(this@MainActivity)
                .setTitle("恢复到历史对话轮次 [$strategyName]")
                .setMessage("执行失败: ${error.message?.take(100)}\n\n选择要恢复到的对话轮次:")
                .setItems(checkpointLabels) { _, which ->
                    deferred.complete(which)
                }
                .setNegativeButton("取消") { _, _ -> deferred.complete(null) }
                .setCancelable(false)
                .show()
        }

        val selectedIndex = deferred.await() ?: return
        val selectedCheckpoint = sortedCpsDesc[selectedIndex]

        // 重建消息历史：system prompt + 所有检查点到选中轮次的消息
        val selectedIndexInAsc = sortedCps.indexOf(selectedCheckpoint)
        val rebuiltHistory = rebuildHistoryFromCheckpoints(sortedCps.take(selectedIndexInAsc + 1))

        messageHistory.clear()
        messageHistory.addAll(rebuiltHistory)

        val totalMsgs = rebuiltHistory.size
        val line = "[Snapshot] 恢复到 ${selectedCheckpoint.nodePath} [$totalMsgs 条消息]\n"
        agentLogBuilder.append(line)
        withContext(Dispatchers.Main) {
            appendRendered(line)
            autoScrollToBottom()
        }

        when (rollbackStrategy) {
            com.lhzkml.jasmine.core.agent.tools.snapshot.RollbackStrategy.RESTART_FROM_NODE -> {
                withContext(Dispatchers.Main) {
                    sendMessage(originalMessage)
                }
            }
            com.lhzkml.jasmine.core.agent.tools.snapshot.RollbackStrategy.SKIP_NODE -> {
                val skipLine = "[Snapshot] 已恢复到该轮对话状态，可继续发送新消息。\n"
                agentLogBuilder.append(skipLine)
                withContext(Dispatchers.Main) {
                    appendRendered(skipLine)
                    autoScrollToBottom()
                }
                val logContent = agentLogBuilder.toString()
                if (logContent.isNotBlank() && currentConversationId != null) {
                    conversationRepo.addMessage(currentConversationId!!, ChatMessage("agent_log", logContent))
                }
            }
            com.lhzkml.jasmine.core.agent.tools.snapshot.RollbackStrategy.USE_DEFAULT_OUTPUT -> {
                val defaultReply = "抱歉，之前的处理过程中遇到了问题。已恢复到 [${selectedCheckpoint.nodePath}]。请重新描述您的需求。"
                val assistantMsg = ChatMessage.assistant(defaultReply)
                messageHistory.add(assistantMsg)

                val defaultLine = "[Snapshot] 使用默认输出恢复\n"
                agentLogBuilder.append(defaultLine)
                withContext(Dispatchers.Main) {
                    appendRendered(defaultLine)
                    appendRendered("AI: $defaultReply\n${formatTime(System.currentTimeMillis())}\n\n")
                    autoScrollToBottom()
                }
                if (currentConversationId != null) {
                    val logContent = agentLogBuilder.toString()
                    if (logContent.isNotBlank()) {
                        conversationRepo.addMessage(currentConversationId!!, ChatMessage("agent_log", logContent))
                    }
                    conversationRepo.addMessage(currentConversationId!!, assistantMsg)
                }
            }
        }
    }

    /**
     * 从检查点列表重建完整消息历史。
     * 委托给框架层 Persistence.rebuildHistoryFromCheckpoints()
     */
    private fun rebuildHistoryFromCheckpoints(checkpoints: List<AgentCheckpoint>): List<ChatMessage> {
        val systemPrompt = ProviderManager.getDefaultSystemPrompt(this)
        return Persistence.rebuildHistoryFromCheckpoints(checkpoints, systemPrompt)
    }

    /**
     * 启动恢复：加载对话时检查是否有检查点，提示用户可以恢复到某一轮对话。
     * 每个检查点只保存当轮 user + assistant，恢复时拼接重建完整历史。
     */
    private suspend fun tryOfferStartupRecovery(conversationId: String) {
        val snapshotDir = getExternalFilesDir("snapshots") ?: return
        val fp = com.lhzkml.jasmine.core.agent.tools.snapshot.FilePersistenceStorageProvider(snapshotDir)

        val allCheckpoints = fp.getCheckpoints(conversationId)
        if (allCheckpoints.isEmpty()) return

        // 检查点总轮数 vs 当前对话轮数
        val totalTurns = allCheckpoints.size
        val currentTurns = messageHistory.count { it.role == "user" }
        if (totalTurns <= currentTurns) return

        val timeFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        val latest = allCheckpoints.maxByOrNull { it.createdAt } ?: return
        val timeStr = timeFormat.format(java.util.Date(latest.createdAt))

        val deferred = CompletableDeferred<Boolean>()
        withContext(Dispatchers.Main) {
            AlertDialog.Builder(this@MainActivity)
                .setTitle("检测到可恢复的对话状态")
                .setMessage(
                    "此对话有更完整的检查点记录:\n\n" +
                    "共 $totalTurns 轮对话检查点\n" +
                    "最后轮次: ${latest.nodePath}\n" +
                    "时间: $timeStr\n\n" +
                    "是否从检查点恢复完整对话？"
                )
                .setPositiveButton("恢复") { _, _ -> deferred.complete(true) }
                .setNegativeButton("忽略") { _, _ -> deferred.complete(false) }
                .setCancelable(true)
                .setOnCancelListener { deferred.complete(false) }
                .show()
        }

        if (!deferred.await()) return

        val sortedCps = allCheckpoints.sortedBy { it.createdAt }
        val rebuilt = rebuildHistoryFromCheckpoints(sortedCps)

        withContext(Dispatchers.Main) {
            messageHistory.clear()
            messageHistory.addAll(rebuilt)

            val line = "[Snapshot] 启动恢复: 从 $totalTurns 个检查点重建对话历史 [${rebuilt.size} 条消息]\n\n"
            appendRendered(line)
            autoScrollToBottom()
        }
    }

    /**
     * 尝试执行智能上下文压缩
     * 根据用户选择的策略，在消息历史过长时自动压缩
     */
    private suspend fun tryCompressHistory(client: ChatClient, model: String) {
        val strategy = buildCompressionStrategy() ?: return

        // TokenBudget 策略需要先检查是否需要压缩
        if (strategy is HistoryCompressionStrategy.TokenBudget) {
            if (!strategy.shouldCompress(messageHistory)) return
        }

        // 创建压缩事件监听器，实时显示压缩过程
        val listener = object : CompressionEventListener {
            override suspend fun onCompressionStart(strategyName: String, originalMessageCount: Int) {
                val line = "[Compress] 开始压缩上下文 [策略: $strategyName, 原始消息: ${originalMessageCount} 条]\n"
                agentLogBuilder.append(line)
                withContext(Dispatchers.Main) {
                    appendRendered(line)
                    autoScrollToBottom()
                }
            }
            override suspend fun onSummaryChunk(chunk: String) {
                agentLogBuilder.append(chunk)
                withContext(Dispatchers.Main) {
                    tvOutput.append(chunk)
                    autoScrollToBottom()
                }
            }
            override suspend fun onBlockCompressed(blockIndex: Int, totalBlocks: Int) {
                val line = "\n[Block] 块 $blockIndex/$totalBlocks 压缩完成\n"
                agentLogBuilder.append(line)
                withContext(Dispatchers.Main) {
                    appendRendered(line)
                    autoScrollToBottom()
                }
            }
            override suspend fun onCompressionDone(compressedMessageCount: Int) {
                val line = "\n[OK] 上下文压缩完成 [压缩后: ${compressedMessageCount} 条消息]\n\n"
                agentLogBuilder.append(line)
                withContext(Dispatchers.Main) {
                    appendRendered(line)
                    autoScrollToBottom()
                }
            }
        }

        // 创建临时 LLMSession 执行压缩
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

            // 用压缩后的消息替换内存中的历史
            val compressed = session.prompt.messages
            messageHistory.clear()
            messageHistory.addAll(compressed)
        } catch (e: Exception) {
            // 压缩失败不影响正常对话
            val line = "\n[WARN] 压缩失败: ${e.message}]\n\n"
            agentLogBuilder.append(line)
            withContext(Dispatchers.Main) {
                appendRendered(line)
                autoScrollToBottom()
            }
        } finally {
            session.close()
        }
    }

    /**
     * 根据设置构建压缩策略
     */
    private fun buildCompressionStrategy(): HistoryCompressionStrategy? {
        return when (ProviderManager.getCompressionStrategy(this)) {
            com.lhzkml.jasmine.core.prompt.llm.CompressionStrategyType.TOKEN_BUDGET -> {
                val maxTokens = ProviderManager.getCompressionMaxTokens(this)
                val effectiveMaxTokens = if (maxTokens > 0) maxTokens else contextManager.maxTokens
                val threshold = ProviderManager.getCompressionThreshold(this) / 100.0
                HistoryCompressionStrategy.TokenBudget(
                    maxTokens = effectiveMaxTokens,
                    threshold = threshold,
                    tokenizer = TokenEstimator
                )
            }
            com.lhzkml.jasmine.core.prompt.llm.CompressionStrategyType.WHOLE_HISTORY ->
                HistoryCompressionStrategy.WholeHistory
            com.lhzkml.jasmine.core.prompt.llm.CompressionStrategyType.LAST_N -> {
                val n = ProviderManager.getCompressionLastN(this)
                HistoryCompressionStrategy.FromLastNMessages(n)
            }
            com.lhzkml.jasmine.core.prompt.llm.CompressionStrategyType.CHUNKED -> {
                val size = ProviderManager.getCompressionChunkSize(this)
                HistoryCompressionStrategy.Chunked(size)
            }
        }
    }

    /** 追加文本到聊天输出 */
    private fun appendRendered(text: String) {
        tvOutput.append(text)
    }

    /** 自动滚动到底部（用户手动向上滚动时跳过） */
    private fun autoScrollToBottom() {
        if (!userScrolledUp) {
            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun formatUsageLine(usage: Usage?): String {
        val time = formatTime(System.currentTimeMillis())
        if (usage == null) return "\n$time\n\n"
        return "\n[提示: ${usage.promptTokens} tokens | 回复: ${usage.completionTokens} tokens | 总计: ${usage.totalTokens} tokens]\n$time\n\n"
    }

    private fun formatTime(timestamp: Long): String {
        return SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    }

    /** 侧边栏对话列表适配器 */
    private class DrawerConversationAdapter : RecyclerView.Adapter<DrawerConversationAdapter.VH>() {
        private var items = listOf<ConversationInfo>()
        var onItemClick: ((ConversationInfo) -> Unit)? = null
        var onDeleteClick: ((ConversationInfo) -> Unit)? = null

        fun submitList(list: List<ConversationInfo>) {
            items = list
            notifyDataSetChanged()
        }

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_drawer_conversation, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val info = items[position]
            holder.tvTitle.text = info.title
            val providerName = ProviderManager.providers
                .find { it.id == info.providerId }?.name ?: info.providerId
            val dateStr = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                .format(Date(info.updatedAt))
            holder.tvMeta.text = "$providerName · $dateStr"
            holder.itemView.setOnClickListener { onItemClick?.invoke(info) }
            holder.btnDelete.setOnClickListener { onDeleteClick?.invoke(info) }
        }

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tvTitle)
            val tvMeta: TextView = view.findViewById(R.id.tvMeta)
            val btnDelete: TextView = view.findViewById(R.id.btnDelete)
        }
    }
}
