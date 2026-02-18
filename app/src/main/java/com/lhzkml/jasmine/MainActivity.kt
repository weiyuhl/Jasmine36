package com.lhzkml.jasmine

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.lhzkml.jasmine.core.prompt.llm.LLMSession
import com.lhzkml.jasmine.core.prompt.llm.ModelRegistry
import com.lhzkml.jasmine.core.prompt.llm.TokenEstimator
import com.lhzkml.jasmine.core.prompt.llm.replaceHistoryWithTLDR
import com.lhzkml.jasmine.core.prompt.executor.ClaudeClient
import com.lhzkml.jasmine.core.prompt.executor.DeepSeekClient
import com.lhzkml.jasmine.core.prompt.executor.GeminiClient
import com.lhzkml.jasmine.core.prompt.executor.GenericClaudeClient
import com.lhzkml.jasmine.core.prompt.executor.GenericGeminiClient
import com.lhzkml.jasmine.core.prompt.executor.GenericOpenAIClient
import com.lhzkml.jasmine.core.prompt.executor.OpenAIClient
import com.lhzkml.jasmine.core.prompt.executor.SiliconFlowClient
import com.lhzkml.jasmine.core.prompt.executor.VertexAIClient
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
import com.lhzkml.jasmine.core.agent.tools.graph.GraphAgent
import com.lhzkml.jasmine.core.agent.tools.graph.PredefinedStrategies
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

    private val clientRouter = ChatClientRouter()
    private var currentProviderId: String? = null

    private lateinit var conversationRepo: ConversationRepository
    private var currentConversationId: String? = null
    private val messageHistory = mutableListOf<ChatMessage>()
    private val drawerAdapter = DrawerConversationAdapter()
    private var contextManager = ContextManager()
    private var webSearchTool: WebSearchTool? = null
    private var currentJob: Job? = null
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

    /**
     * 根据设置构建工具注册表
     */
    private fun buildToolRegistry(): ToolRegistry {
        val enabledTools = ProviderManager.getEnabledTools(this)
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

            // 文件工具（Android 上使用外部存储作为沙箱）
            val basePath = getExternalFilesDir(null)?.absolutePath
            if (isEnabled("read_file")) register(ReadFileTool(basePath))
            if (isEnabled("write_file")) register(WriteFileTool(basePath))
            if (isEnabled("edit_file")) register(EditFileTool(basePath))
            if (isEnabled("list_directory")) register(ListDirectoryTool(basePath))
            if (isEnabled("search_by_regex")) register(RegexSearchTool(basePath))

            // Shell 命令（带确认对话框）
            if (isEnabled("execute_shell_command")) {
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
                    basePath = basePath
                ))
            }

            // 网络搜索
            val brightDataKey = ProviderManager.getBrightDataKey(this@MainActivity)
            if (brightDataKey.isNotEmpty() && (isEnabled("web_search") || isEnabled("web_scrape"))) {
                webSearchTool?.close()
                val wst = WebSearchTool(brightDataKey)
                webSearchTool = wst
                if (isEnabled("web_search")) register(wst.search)
                if (isEnabled("web_scrape")) register(wst.scrape)
            }
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
        fun isEnabled(cat: ProviderManager.EventCategory) = filter.isEmpty() || cat in filter

        return EventHandler.build {
            if (isEnabled(ProviderManager.EventCategory.AGENT)) {
                onAgentStarting { ctx ->
                    val line = "[EVENT] Agent 开始 [${ctx.agentId}]\n"
                    agentLogBuilder.append(line)
                    withContext(Dispatchers.Main) {
                        tvOutput.append(line)
                        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                    }
                }
                onAgentCompleted { ctx ->
                    val line = "[EVENT] Agent 完成 [${ctx.agentId}]\n"
                    agentLogBuilder.append(line)
                    withContext(Dispatchers.Main) {
                        tvOutput.append(line)
                        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                    }
                }
                onAgentExecutionFailed { ctx ->
                    val line = "[EVENT] Agent 失败: ${ctx.throwable.message}\n"
                    agentLogBuilder.append(line)
                    withContext(Dispatchers.Main) {
                        tvOutput.append(line)
                        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                    }
                }
            }
            if (isEnabled(ProviderManager.EventCategory.TOOL)) {
                onToolCallStarting { ctx ->
                    val line = "[EVENT] 工具调用: ${ctx.toolName}(${ctx.toolArgs.take(60)})\n"
                    agentLogBuilder.append(line)
                    withContext(Dispatchers.Main) {
                        tvOutput.append(line)
                        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                    }
                }
                onToolCallCompleted { ctx ->
                    val line = "[EVENT] 工具完成: ${ctx.toolName} -> ${(ctx.result ?: "").take(100)}\n"
                    agentLogBuilder.append(line)
                    withContext(Dispatchers.Main) {
                        tvOutput.append(line)
                        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                    }
                }
                onToolCallFailed { ctx ->
                    val line = "[EVENT] 工具失败: ${ctx.toolName} - ${ctx.throwable.message}\n"
                    agentLogBuilder.append(line)
                    withContext(Dispatchers.Main) {
                        tvOutput.append(line)
                        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                    }
                }
                onToolValidationFailed { ctx ->
                    val line = "[EVENT] 工具验证失败: ${ctx.toolName} - ${ctx.validationError}\n"
                    agentLogBuilder.append(line)
                    withContext(Dispatchers.Main) {
                        tvOutput.append(line)
                        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                    }
                }
            }
            if (isEnabled(ProviderManager.EventCategory.LLM)) {
                onLLMCallStarting { ctx ->
                    val line = "[EVENT] LLM 请求 [消息: ${ctx.messageCount}, 工具: ${ctx.tools.size}]\n"
                    agentLogBuilder.append(line)
                    withContext(Dispatchers.Main) {
                        tvOutput.append(line)
                        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                    }
                }
                onLLMCallCompleted { ctx ->
                    val line = "[EVENT] LLM 回复 [${ctx.totalTokens} tokens]\n"
                    agentLogBuilder.append(line)
                    withContext(Dispatchers.Main) {
                        tvOutput.append(line)
                        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                    }
                }
            }
            if (isEnabled(ProviderManager.EventCategory.STRATEGY)) {
                onStrategyStarting { ctx ->
                    val line = "[EVENT] 策略开始: ${ctx.strategyName}\n"
                    agentLogBuilder.append(line)
                    withContext(Dispatchers.Main) {
                        tvOutput.append(line)
                        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                    }
                }
                onStrategyCompleted { ctx ->
                    val line = "[EVENT] 策略完成: ${ctx.strategyName} -> ${ctx.result?.take(80) ?: ""}\n"
                    agentLogBuilder.append(line)
                    withContext(Dispatchers.Main) {
                        tvOutput.append(line)
                        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                    }
                }
            }
            if (isEnabled(ProviderManager.EventCategory.NODE)) {
                onNodeExecutionStarting { ctx ->
                    val line = "[EVENT] 节点开始: ${ctx.nodeName}\n"
                    agentLogBuilder.append(line)
                    withContext(Dispatchers.Main) {
                        tvOutput.append(line)
                        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                    }
                }
                onNodeExecutionCompleted { ctx ->
                    val line = "[EVENT] 节点完成: ${ctx.nodeName}\n"
                    agentLogBuilder.append(line)
                    withContext(Dispatchers.Main) {
                        tvOutput.append(line)
                        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                    }
                }
                onNodeExecutionFailed { ctx ->
                    val line = "[EVENT] 节点失败: ${ctx.nodeName} - ${ctx.throwable.message}\n"
                    agentLogBuilder.append(line)
                    withContext(Dispatchers.Main) {
                        tvOutput.append(line)
                        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                    }
                }
            }
            if (isEnabled(ProviderManager.EventCategory.SUBGRAPH)) {
                onSubgraphExecutionStarting { ctx ->
                    val line = "[EVENT] 子图开始: ${ctx.subgraphName}\n"
                    agentLogBuilder.append(line)
                    withContext(Dispatchers.Main) {
                        tvOutput.append(line)
                        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                    }
                }
                onSubgraphExecutionCompleted { ctx ->
                    val line = "[EVENT] 子图完成: ${ctx.subgraphName}\n"
                    agentLogBuilder.append(line)
                    withContext(Dispatchers.Main) {
                        tvOutput.append(line)
                        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                    }
                }
                onSubgraphExecutionFailed { ctx ->
                    val line = "[EVENT] 子图失败: ${ctx.subgraphName} - ${ctx.throwable.message}\n"
                    agentLogBuilder.append(line)
                    withContext(Dispatchers.Main) {
                        tvOutput.append(line)
                        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                    }
                }
            }
            if (isEnabled(ProviderManager.EventCategory.STREAMING)) {
                onLLMStreamingStarting { ctx ->
                    val line = "[EVENT] LLM 流式开始 [模型: ${ctx.model}]\n"
                    agentLogBuilder.append(line)
                    withContext(Dispatchers.Main) {
                        tvOutput.append(line)
                        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                    }
                }
                onLLMStreamingCompleted { ctx ->
                    val line = "[EVENT] LLM 流式完成 [${ctx.totalTokens} tokens]\n"
                    agentLogBuilder.append(line)
                    withContext(Dispatchers.Main) {
                        tvOutput.append(line)
                        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                    }
                }
                onLLMStreamingFailed { ctx ->
                    val line = "[EVENT] LLM 流式失败: ${ctx.throwable.message}\n"
                    agentLogBuilder.append(line)
                    withContext(Dispatchers.Main) {
                        tvOutput.append(line)
                        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                    }
                }
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
        persistence.rollbackStrategy = when (ProviderManager.getSnapshotRollbackStrategy(this)) {
            ProviderManager.SnapshotRollbackStrategy.RESTART_FROM_NODE ->
                com.lhzkml.jasmine.core.agent.tools.snapshot.RollbackStrategy.RESTART_FROM_NODE
            ProviderManager.SnapshotRollbackStrategy.SKIP_NODE ->
                com.lhzkml.jasmine.core.agent.tools.snapshot.RollbackStrategy.SKIP_NODE
            ProviderManager.SnapshotRollbackStrategy.USE_DEFAULT_OUTPUT ->
                com.lhzkml.jasmine.core.agent.tools.snapshot.RollbackStrategy.USE_DEFAULT_OUTPUT
        }

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
        tvDrawerEmpty = findViewById(R.id.tvDrawerEmpty)
        rvDrawerConversations = findViewById(R.id.rvDrawerConversations)

        // DrawerLayout push 效果：侧边栏滑出时，主内容跟着平移
        val drawerPanel = findViewById<LinearLayout>(R.id.drawerPanel)
        drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                // slideOffset: 0（关闭）→ 1（完全打开）
                // 主内容向左平移 = 侧边栏宽度 × slideOffset
                val offset = drawerPanel.width * slideOffset
                mainContent.translationX = -offset
            }

            override fun onDrawerClosed(drawerView: View) {
                mainContent.translationX = 0f
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

        // 实时观察对话列表
        CoroutineScope(Dispatchers.Main).launch {
            conversationRepo.observeConversations().collectLatest { list ->
                drawerAdapter.submitList(list)
                tvDrawerEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }

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

        // APP 启动时后台预连接 MCP 服务器
        preconnectMcpServers()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_CONVERSATION_ID)?.let { loadConversation(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        clientRouter.close()
        webSearchTool?.close()
        tracing?.close()
        mcpClients.forEach { try { it.close() } catch (_: Exception) {} }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(Gravity.END)) {
            drawerLayout.closeDrawer(Gravity.END)
        } else {
            super.onBackPressed()
        }
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
                tvOutput.text = sb.toString()
                scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
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
        
        val client: ChatClient = when (config.apiType) {
            ApiType.OPENAI -> {
                val chatPath = config.chatPath ?: "/v1/chat/completions"
                when (config.providerId) {
                    "openai" -> OpenAIClient(apiKey = config.apiKey, baseUrl = config.baseUrl, chatPath = chatPath)
                    "deepseek" -> DeepSeekClient(apiKey = config.apiKey, baseUrl = config.baseUrl, chatPath = chatPath)
                    "siliconflow" -> SiliconFlowClient(apiKey = config.apiKey, baseUrl = config.baseUrl, chatPath = chatPath)
                    else -> GenericOpenAIClient(
                        providerName = provider?.name ?: config.providerId,
                        apiKey = config.apiKey,
                        baseUrl = config.baseUrl,
                        chatPath = chatPath
                    )
                }
            }
            ApiType.CLAUDE -> when (config.providerId) {
                "claude" -> ClaudeClient(apiKey = config.apiKey, baseUrl = config.baseUrl)
                else -> GenericClaudeClient(
                    providerName = provider?.name ?: config.providerId,
                    apiKey = config.apiKey,
                    baseUrl = config.baseUrl
                )
            }
            ApiType.GEMINI -> {
                // Vertex AI 模式
                if (config.vertexEnabled && config.vertexServiceAccountJson.isNotEmpty()) {
                    VertexAIClient(
                        serviceAccountJson = config.vertexServiceAccountJson,
                        projectId = config.vertexProjectId,
                        location = config.vertexLocation
                    )
                } else {
                    val genPath = config.chatPath ?: GeminiClient.DEFAULT_GENERATE_PATH
                    val streamPath = genPath.replace(":generateContent", ":streamGenerateContent")
                    when (config.providerId) {
                        "gemini" -> GeminiClient(
                            apiKey = config.apiKey,
                            baseUrl = config.baseUrl,
                            generatePath = genPath,
                            streamPath = streamPath
                        )
                        else -> GenericGeminiClient(
                            providerName = provider?.name ?: config.providerId,
                            apiKey = config.apiKey,
                            baseUrl = config.baseUrl,
                            generatePath = genPath,
                            streamPath = streamPath
                        )
                    }
                }
            }
        }

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

        updateSendButtonState(ButtonState.GENERATING)
        val now = formatTime(System.currentTimeMillis())
        tvOutput.append("You: $message\n$now\n\n")
        etInput.text.clear()

        val client = getOrCreateClient(config)
        val userMsg = ChatMessage.user(message)

        currentJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                if (currentConversationId == null) {
                    val title = if (message.length > 20) message.substring(0, 20) + "..." else message
                    val systemPrompt = ProviderManager.getDefaultSystemPrompt(this@MainActivity)
                    currentConversationId = conversationRepo.createConversation(
                        title = title,
                        providerId = config.providerId,
                        model = config.model,
                        systemPrompt = systemPrompt
                    )
                    val systemMsg = ChatMessage.system(systemPrompt)
                    messageHistory.add(systemMsg)
                    conversationRepo.addMessage(currentConversationId!!, systemMsg)
                }

                messageHistory.add(userMsg)
                conversationRepo.addMessage(currentConversationId!!, userMsg)

                // 构建追踪系统
                tracing?.close()
                tracing = buildTracing()

                // 上下文窗口裁剪，避免超出模型 token 限制
                val trimmedMessages = contextManager.trimMessages(messageHistory.toList())

                val useStream = ProviderManager.isStreamEnabled(this@MainActivity)
                val maxTokensVal = ProviderManager.getMaxTokens(this@MainActivity)
                val maxTokens = if (maxTokensVal > 0) maxTokensVal else null

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

                val toolsEnabled = ProviderManager.isToolsEnabled(this@MainActivity)

                if (toolsEnabled) {
                    // Agent 模式：使用 ToolExecutor 自动循环
                    val registry = buildToolRegistry()
                    loadMcpToolsInto(registry)
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
                                tvOutput.append(line)
                                agentLogBuilder.append(line)
                                scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                            }
                        }
                        override suspend fun onToolCallResult(toolName: String, result: String) {
                            withContext(Dispatchers.Main) {
                                val preview = if (result.length > 200) result.take(200) + "…" else result
                                val line = "[Result] $toolName 结果: $preview\n\n"
                                tvOutput.append(line)
                                agentLogBuilder.append(line)
                                scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                            }
                            // 工具调用完成后创建检查点（细粒度恢复）
                            persistence?.onNodeCompleted(
                                agentId = currentConversationId ?: "unknown",
                                nodePath = "tool:$toolName",
                                lastInput = result.take(200),
                                messageHistory = messageHistory.toList()
                            )
                        }
                        override suspend fun onThinking(content: String) {
                            withContext(Dispatchers.Main) {
                                if (!thinkingStarted) {
                                    tvOutput.append("[Think] ")
                                    agentLogBuilder.append("[Think] ")
                                    thinkingStarted = true
                                }
                                tvOutput.append(content)
                                agentLogBuilder.append(content)
                                scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                            }
                        }
                    }
                    val executor = ToolExecutor(client, registry, eventListener = listener, tracing = tracing)

                    // 构建事件处理器
                    eventHandler = buildEventHandler()

                    // 构建快照/持久化
                    persistence = buildPersistence()

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
                            val planSession = LLMSession(client, config.model, planPrompt)
                            val planContext = AgentGraphContext(
                                agentId = currentConversationId ?: "planner",
                                runId = agentRunId,
                                client = client,
                                model = config.model,
                                session = planSession,
                                toolRegistry = registry,
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

                            withContext(Dispatchers.Main) {
                                tvOutput.append("[Plan] 任务规划:\n")
                                tvOutput.append("[Goal] 目标: ${plan.goal}\n")
                                agentLogBuilder.append("[Plan] 任务规划:\n")
                                agentLogBuilder.append("[Goal] 目标: ${plan.goal}\n")
                                plan.steps.forEachIndexed { index, step ->
                                    val stepLine = "  ${index + 1}. ${step.description}\n"
                                    tvOutput.append(stepLine)
                                    agentLogBuilder.append(stepLine)
                                }
                                tvOutput.append("\n")
                                agentLogBuilder.append("\n")
                                scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                            }

                            // 快照：规划完成后创建检查点
                            persistence?.onNodeCompleted(
                                agentId = currentConversationId ?: "unknown",
                                nodePath = "planner",
                                lastInput = message,
                                messageHistory = messageHistory.toList()
                            )
                        } catch (e: Exception) {
                            // 规划失败不影响正常执行
                            withContext(Dispatchers.Main) {
                                val line = "[Plan] [规划跳过: ${e.message}]\n\n"
                                tvOutput.append(line)
                                agentLogBuilder.append(line)
                                scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                            }
                        }
                    }

                    val agentStrategy = ProviderManager.getAgentStrategy(this@MainActivity)

                    when (agentStrategy) {
                        ProviderManager.AgentStrategyType.SIMPLE_LOOP -> {
                            // 简单循环模式：使用 ToolExecutor
                            if (useStream) {
                                withContext(Dispatchers.Main) {
                                    tvOutput.append("AI: ")
                                }
                                val streamResult = executor.executeStream(
                                    trimmedMessages, config.model, maxTokens, samplingParams
                                ) { chunk ->
                                    withContext(Dispatchers.Main) {
                                        tvOutput.append(chunk)
                                        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                                    }
                                }
                                result = streamResult.content
                                usage = streamResult.usage
                                withContext(Dispatchers.Main) {
                                    tvOutput.append(formatUsageLine(usage))
                                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                                }
                            } else {
                                val chatResult = executor.execute(
                                    trimmedMessages, config.model, maxTokens, samplingParams
                                )
                                result = chatResult.content
                                usage = chatResult.usage

                                val thinkingLine = chatResult.thinking?.let { thinking ->
                                    val preview = if (thinking.length > 500) thinking.take(500) + "…" else thinking
                                    val line = "\n[Think] 思考: $preview\n"
                                    agentLogBuilder.append(line)
                                    line
                                } ?: ""

                                withContext(Dispatchers.Main) {
                                    tvOutput.append("AI: $result$thinkingLine${formatUsageLine(usage)}")
                                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                                }
                            }
                        }

                        ProviderManager.AgentStrategyType.SINGLE_RUN_GRAPH -> {
                            // 图策略模式：使用 GraphAgent + PredefinedStrategies
                            val strategy = if (useStream) {
                                PredefinedStrategies.singleRunStreamStrategy()
                            } else {
                                PredefinedStrategies.singleRunStrategy()
                            }

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

                            // 显示图策略流程头
                            withContext(Dispatchers.Main) {
                                val header = "┌─ [Graph] 图策略执行 ─────────────\n│ [>] Start\n"
                                tvOutput.append(header)
                                agentLogBuilder.append(header)
                                scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                            }

                            if (useStream) {
                                withContext(Dispatchers.Main) {
                                    tvOutput.append("└─────────────────────────\n\nAI: ")
                                    agentLogBuilder.append("└─────────────────────────\n")
                                }
                            }

                            val chunkCallback: (suspend (String) -> Unit)? = if (useStream) {
                                { chunk: String ->
                                    withContext(Dispatchers.Main) {
                                        tvOutput.append(chunk)
                                        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                                    }
                                }
                            } else null

                            var graphThinkingStarted = false
                            val thinkingCallback: (suspend (String) -> Unit)? = if (useStream) {
                                { text: String ->
                                    withContext(Dispatchers.Main) {
                                        if (!graphThinkingStarted) {
                                            tvOutput.append("[Think] ")
                                            agentLogBuilder.append("[Think] ")
                                            graphThinkingStarted = true
                                        }
                                        tvOutput.append(text)
                                        agentLogBuilder.append(text)
                                        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                                    }
                                }
                            } else null

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
                                    tvOutput.append(line)
                                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                                }
                            }

                            val nodeExitCallback: suspend (String, Boolean) -> Unit = { nodeName, success ->
                                val status = if (success) "[OK]" else "[FAIL]"
                                val line = "│ $status $nodeName 完成\n"
                                agentLogBuilder.append(line)
                                withContext(Dispatchers.Main) {
                                    tvOutput.append(line)
                                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                                }
                            }

                            val edgeCallback: suspend (String, String, String) -> Unit = { from, to, label ->
                                val labelStr = if (label.isNotEmpty()) " ($label)" else ""
                                val line = "│  ↓ $from → $to$labelStr\n"
                                agentLogBuilder.append(line)
                                withContext(Dispatchers.Main) {
                                    tvOutput.append(line)
                                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                                }
                            }

                            val graphResult = graphAgent.runWithCallbacks(
                                prompt = graphPrompt,
                                input = message,
                                onChunk = chunkCallback,
                                onThinking = thinkingCallback,
                                onToolCallStart = { toolName, args ->
                                    val argsPreview = if (args.length > 80) args.take(80) + "…" else args
                                    val line = "│  [Tool] $toolName($argsPreview)\n"
                                    agentLogBuilder.append(line)
                                    withContext(Dispatchers.Main) {
                                        tvOutput.append(line)
                                        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                                    }
                                },
                                onToolCallResult = { toolName, toolResult ->
                                    val preview = if (toolResult.length > 200) toolResult.take(200) + "…" else toolResult
                                    val line = "│  [Result] $toolName -> $preview\n"
                                    agentLogBuilder.append(line)
                                    withContext(Dispatchers.Main) {
                                        tvOutput.append(line)
                                        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                                    }
                                },
                                onNodeEnter = nodeEnterCallback,
                                onNodeExit = nodeExitCallback,
                                onEdge = edgeCallback
                            )

                            result = graphResult ?: ""

                            if (!useStream) {
                                withContext(Dispatchers.Main) {
                                    val footer = "│ [x] Finish\n└─────────────────────────\n\n"
                                    tvOutput.append(footer)
                                    agentLogBuilder.append(footer)
                                    tvOutput.append("AI: $result${formatUsageLine(null)}")
                                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    tvOutput.append(formatUsageLine(null))
                                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                                }
                            }
                        }
                    }

                    // 快照：Agent 执行完成后创建检查点
                    persistence?.onNodeCompleted(
                        agentId = currentConversationId ?: "unknown",
                        nodePath = "agent_execution",
                        lastInput = message,
                        messageHistory = messageHistory.toList()
                    )
                    // 标记执行完成（墓碑检查点），防止下次误恢复
                    persistence?.markCompleted(currentConversationId ?: "unknown")

                    // 触发 Agent 完成事件
                    eventHandler?.fireAgentCompleted(AgentCompletedContext(
                        runId = agentRunId,
                        agentId = currentConversationId ?: "unknown",
                        result = result.take(200),
                        totalIterations = 0
                    ))
                } else if (useStream) {
                    // 普通流式输出
                    withContext(Dispatchers.Main) {
                        tvOutput.append("AI: ")
                    }

                    var thinkingStarted = false
                    val streamResult = client.chatStreamWithUsageAndThinking(
                        trimmedMessages, config.model, maxTokens, samplingParams,
                        onChunk = { chunk ->
                            withContext(Dispatchers.Main) {
                                // 如果之前在显示思考内容，先换行再显示正文
                                if (thinkingStarted) {
                                    tvOutput.append("\n\nAI: ")
                                    thinkingStarted = false
                                }
                                tvOutput.append(chunk)
                                scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                            }
                        },
                        onThinking = { text ->
                            withContext(Dispatchers.Main) {
                                if (!thinkingStarted) {
                                    tvOutput.append("[Think] ")
                                    agentLogBuilder.append("[Think] ")
                                    thinkingStarted = true
                                }
                                tvOutput.append(text)
                                agentLogBuilder.append(text)
                                scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                            }
                        }
                    )

                    result = streamResult.content
                    usage = streamResult.usage

                    withContext(Dispatchers.Main) {
                        tvOutput.append(formatUsageLine(usage))
                        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                    }
                } else {
                    // 普通非流式
                    val chatResult = client.chatWithUsage(trimmedMessages, config.model, maxTokens, samplingParams)
                    result = chatResult.content
                    usage = chatResult.usage

                    val thinkingLine = chatResult.thinking?.let { thinking ->
                        val preview = if (thinking.length > 500) thinking.take(500) + "…" else thinking
                        val line = "\n[Think] 思考: $preview\n"
                        agentLogBuilder.append(line)
                        line
                    } ?: ""

                    withContext(Dispatchers.Main) {
                        tvOutput.append("AI: $result$thinkingLine${formatUsageLine(usage)}")
                        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                    }
                }

                val assistantMsg = ChatMessage.assistant(result)
                messageHistory.add(assistantMsg)

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
                withContext(Dispatchers.Main) {
                    tvOutput.append("\n$errorMsg\n\n")
                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                }
                // 快照恢复：检查是否有可用检查点
                tryOfferCheckpointRecovery(e, message)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvOutput.append("\n未知错误: ${e.message}\n\n")
                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
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
     * 快照恢复：Agent 执行失败时，检查是否有可用检查点，弹窗询问用户是否恢复。
     * 根据配置的回滚策略执行不同的恢复行为：
     * - RESTART_FROM_NODE: 恢复消息历史并重新执行（从头开始）
     * - SKIP_NODE: 恢复消息历史，不重新执行（仅恢复上下文）
     * - USE_DEFAULT_OUTPUT: 恢复消息历史并添加默认回复
     */
    private suspend fun tryOfferCheckpointRecovery(error: Exception, originalMessage: String) {
        val p = persistence ?: return
        val agentId = currentConversationId ?: return

        // 获取所有非墓碑检查点
        val allCheckpoints = p.getCheckpoints(agentId).filter { !it.isTombstone() }
        if (allCheckpoints.isEmpty()) return

        val latest = allCheckpoints.maxByOrNull { it.createdAt } ?: return

        val rollbackStrategy = ProviderManager.getSnapshotRollbackStrategy(this@MainActivity)
        val strategyName = when (rollbackStrategy) {
            ProviderManager.SnapshotRollbackStrategy.RESTART_FROM_NODE -> "从节点重启"
            ProviderManager.SnapshotRollbackStrategy.SKIP_NODE -> "跳过节点 (仅恢复上下文)"
            ProviderManager.SnapshotRollbackStrategy.USE_DEFAULT_OUTPUT -> "使用默认输出"
        }

        // 构建检查点选择列表
        val timeFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        val checkpointLabels = allCheckpoints.sortedByDescending { it.createdAt }.map { cp ->
            val time = timeFormat.format(java.util.Date(cp.createdAt))
            "[$time] ${cp.nodePath} (${cp.messageHistory.size} 条消息)"
        }.toTypedArray()

        // 在主线程弹窗，让用户选择恢复到哪个检查点
        val deferred = CompletableDeferred<AgentCheckpoint?>()
        withContext(Dispatchers.Main) {
            val sortedCps = allCheckpoints.sortedByDescending { it.createdAt }
            AlertDialog.Builder(this@MainActivity)
                .setTitle("检查点恢复 [$strategyName]")
                .setMessage("Agent 执行失败: ${error.message?.take(100)}\n\n选择要恢复的检查点:")
                .setItems(checkpointLabels) { _, which ->
                    deferred.complete(sortedCps[which])
                }
                .setNegativeButton("取消") { _, _ -> deferred.complete(null) }
                .setCancelable(false)
                .show()
        }

        val selectedCheckpoint = deferred.await() ?: return

        // 恢复消息历史到检查点状态
        messageHistory.clear()
        messageHistory.addAll(selectedCheckpoint.messageHistory)

        val line = "[Snapshot] 从检查点恢复 [策略: $strategyName, 节点: ${selectedCheckpoint.nodePath}, 消息: ${selectedCheckpoint.messageHistory.size}]\n"
        agentLogBuilder.append(line)
        withContext(Dispatchers.Main) {
            tvOutput.append(line)
            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }

        // 根据回滚策略执行不同行为
        when (rollbackStrategy) {
            ProviderManager.SnapshotRollbackStrategy.RESTART_FROM_NODE -> {
                // 恢复消息历史后重新发送消息
                withContext(Dispatchers.Main) {
                    sendMessage(originalMessage)
                }
            }
            ProviderManager.SnapshotRollbackStrategy.SKIP_NODE -> {
                // 仅恢复上下文，不重新执行
                val skipLine = "[Snapshot] 已恢复到检查点状态，跳过失败节点。可继续发送新消息。\n"
                agentLogBuilder.append(skipLine)
                withContext(Dispatchers.Main) {
                    tvOutput.append(skipLine)
                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                }
                // 保存恢复日志到对话
                val logContent = agentLogBuilder.toString()
                if (logContent.isNotBlank() && currentConversationId != null) {
                    conversationRepo.addMessage(currentConversationId!!, ChatMessage("agent_log", logContent))
                }
            }
            ProviderManager.SnapshotRollbackStrategy.USE_DEFAULT_OUTPUT -> {
                // 恢复上下文并添加默认回复
                val defaultReply = "抱歉，之前的处理过程中遇到了问题。已从检查点 [${selectedCheckpoint.nodePath}] 恢复。请重新描述您的需求，我会重新处理。"
                val assistantMsg = ChatMessage.assistant(defaultReply)
                messageHistory.add(assistantMsg)

                val defaultLine = "[Snapshot] 使用默认输出恢复\n"
                agentLogBuilder.append(defaultLine)
                withContext(Dispatchers.Main) {
                    tvOutput.append(defaultLine)
                    tvOutput.append("AI: $defaultReply\n${formatTime(System.currentTimeMillis())}\n\n")
                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                }
                // 保存到对话
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
                    tvOutput.append(line)
                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                }
            }
            override suspend fun onSummaryChunk(chunk: String) {
                agentLogBuilder.append(chunk)
                withContext(Dispatchers.Main) {
                    tvOutput.append(chunk)
                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                }
            }
            override suspend fun onBlockCompressed(blockIndex: Int, totalBlocks: Int) {
                val line = "\n[Block] 块 $blockIndex/$totalBlocks 压缩完成\n"
                agentLogBuilder.append(line)
                withContext(Dispatchers.Main) {
                    tvOutput.append(line)
                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                }
            }
            override suspend fun onCompressionDone(compressedMessageCount: Int) {
                val line = "\n[OK] 上下文压缩完成 [压缩后: ${compressedMessageCount} 条消息]\n\n"
                agentLogBuilder.append(line)
                withContext(Dispatchers.Main) {
                    tvOutput.append(line)
                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
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

        val session = LLMSession(client, model, prompt)
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
                tvOutput.append(line)
                scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
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
            ProviderManager.CompressionStrategy.TOKEN_BUDGET -> {
                val maxTokens = ProviderManager.getCompressionMaxTokens(this)
                val effectiveMaxTokens = if (maxTokens > 0) maxTokens else contextManager.maxTokens
                val threshold = ProviderManager.getCompressionThreshold(this) / 100.0
                HistoryCompressionStrategy.TokenBudget(
                    maxTokens = effectiveMaxTokens,
                    threshold = threshold,
                    tokenizer = TokenEstimator
                )
            }
            ProviderManager.CompressionStrategy.WHOLE_HISTORY ->
                HistoryCompressionStrategy.WholeHistory
            ProviderManager.CompressionStrategy.LAST_N -> {
                val n = ProviderManager.getCompressionLastN(this)
                HistoryCompressionStrategy.FromLastNMessages(n)
            }
            ProviderManager.CompressionStrategy.CHUNKED -> {
                val size = ProviderManager.getCompressionChunkSize(this)
                HistoryCompressionStrategy.Chunked(size)
            }
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
