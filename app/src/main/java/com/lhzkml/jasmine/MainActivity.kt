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
import com.google.android.material.snackbar.Snackbar
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
import com.lhzkml.jasmine.core.agent.tools.mcp.McpToolRegistryProvider
import com.lhzkml.jasmine.core.agent.tools.mcp.SseMcpClient
import com.lhzkml.jasmine.core.agent.tools.trace.CallbackTraceWriter
import com.lhzkml.jasmine.core.agent.tools.trace.LogTraceWriter
import com.lhzkml.jasmine.core.agent.tools.trace.TraceEvent
import com.lhzkml.jasmine.core.agent.tools.trace.Tracing
import com.lhzkml.jasmine.core.agent.tools.planner.SimpleLLMPlanner
import com.lhzkml.jasmine.core.agent.tools.graph.AgentGraphContext
import com.lhzkml.jasmine.core.prompt.llm.AgentMemory
import com.lhzkml.jasmine.core.prompt.llm.LocalFileMemoryProvider
import com.lhzkml.jasmine.core.prompt.model.MemoryScope
import com.lhzkml.jasmine.core.prompt.model.MemoryScopeType
import com.lhzkml.jasmine.core.prompt.model.MemoryScopesProfile
import com.lhzkml.jasmine.core.prompt.model.MemorySubject
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
    private var agentMemory: AgentMemory? = null
    private var tracing: Tracing? = null
    private var mcpClients: MutableList<McpClient> = mutableListOf()
    /** 预加载的 MCP 工具（APP 启动时后台连接） */
    private var preloadedMcpTools: MutableList<McpToolAdapter> = mutableListOf()
    private var mcpPreloaded = false

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

                    withContext(Dispatchers.Main) {
                        val transportLabel = when (server.transportType) {
                            ProviderManager.McpTransportType.STREAMABLE_HTTP -> "HTTP"
                            ProviderManager.McpTransportType.SSE -> "SSE"
                        }
                        Snackbar.make(mainContent, "MCP: ${server.name} 已连接 [$transportLabel] (${mcpRegistry.size} 个工具)", Snackbar.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Snackbar.make(mainContent, "MCP: ${server.name} 连接失败", Snackbar.LENGTH_SHORT).show()
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
                    Snackbar.make(mainContent, "MCP: ${server.name} 已连接 [$transportLabel] (${mcpRegistry.size} 个工具)", Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Snackbar.make(mainContent, "MCP: ${server.name} 连接失败", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
        mcpPreloaded = true
    }

    /**
     * 初始化/刷新记忆系统
     */
    private fun ensureMemory(): AgentMemory? {
        if (!ProviderManager.isMemoryEnabled(this)) {
            agentMemory = null
            return null
        }
        if (agentMemory != null) return agentMemory

        val rootDir = getExternalFilesDir(null) ?: return null
        val provider = LocalFileMemoryProvider(rootDir)
        val agentName = ProviderManager.getMemoryAgentName(this)
        val profile = MemoryScopesProfile(
            MemoryScopeType.AGENT to agentName
        )
        agentMemory = AgentMemory(provider, profile)
        return agentMemory
    }

    /**
     * 构建追踪系统
     */
    private fun buildTracing(): Tracing? {
        if (!ProviderManager.isTraceEnabled(this)) return null

        return Tracing.build {
            addWriter(LogTraceWriter())
            if (ProviderManager.isTraceInlineDisplay(this@MainActivity)) {
                addWriter(CallbackTraceWriter(callback = { event ->
                    val msg = formatTraceEvent(event) ?: return@CallbackTraceWriter
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        tvOutput.append(msg)
                        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                    }
                }))
            }
        }
    }

    /**
     * 格式化追踪事件为用户可读文本
     */
    private fun formatTraceEvent(event: TraceEvent): String? = when (event) {
        is TraceEvent.AgentStarting -> "📊 Agent 启动 [模型: ${event.model}, 工具: ${event.toolCount}]\n"
        is TraceEvent.AgentCompleted -> "📊 Agent 完成 [迭代: ${event.totalIterations}]\n"
        is TraceEvent.AgentFailed -> "📊 Agent 失败: ${event.error.message}\n"
        is TraceEvent.LLMCallStarting -> "📊 LLM 请求 [消息: ${event.messageCount}, 工具: ${event.tools.size}]\n"
        is TraceEvent.LLMCallCompleted -> "📊 LLM 回复 [提示: ${event.promptTokens}, 回复: ${event.completionTokens}]\n"
        is TraceEvent.ToolCallStarting -> null // 已有 AgentEventListener 显示
        is TraceEvent.ToolCallCompleted -> null
        is TraceEvent.CompressionStarting -> "📊 压缩开始 [原始: ${event.originalMessageCount} 条]\n"
        is TraceEvent.CompressionCompleted -> "📊 压缩完成 [压缩后: ${event.compressedMessageCount} 条]\n"
        else -> null
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
                // 停止当前生成
                currentJob?.cancel()
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
                messageHistory.addAll(messages)

                val sb = StringBuilder()
                var usageIndex = 0
                for (msg in timedMessages) {
                    val time = formatTime(msg.createdAt)
                    when (msg.role) {
                        "user" -> sb.append("You: ${msg.content}\n$time\n\n")
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

                // 加载记忆事实到上下文
                val memory = ensureMemory()
                if (memory != null) {
                    try {
                        val tempPrompt = Prompt.build("memory-load") {
                            for (msg in messageHistory) {
                                when (msg.role) {
                                    "system" -> system(msg.content)
                                    "user" -> user(msg.content)
                                    "assistant" -> assistant(msg.content)
                                }
                            }
                        }
                        val tempSession = LLMSession(client, config.model, tempPrompt)
                        memory.loadAllFactsToAgent(tempSession)
                        // 如果有记忆消息被注入，同步到 messageHistory
                        val injected = tempSession.prompt.messages
                        if (injected.size > messageHistory.size) {
                            val newMsgs = injected.subList(messageHistory.size, injected.size)
                            messageHistory.addAll(newMsgs)
                            withContext(Dispatchers.Main) {
                                tvOutput.append("🧠 已加载 ${newMsgs.size} 条记忆事实\n\n")
                                scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                            }
                        }
                        tempSession.close()
                    } catch (e: Exception) {
                        // 记忆加载失败不影响正常对话
                    }
                }

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

                val toolsEnabled = ProviderManager.isToolsEnabled(this@MainActivity)

                if (toolsEnabled) {
                    // Agent 模式：使用 ToolExecutor 自动循环
                    val registry = buildToolRegistry()
                    loadMcpToolsInto(registry)
                    val listener = object : AgentEventListener {
                        override suspend fun onToolCallStart(toolName: String, arguments: String) {
                            withContext(Dispatchers.Main) {
                                val argsPreview = if (arguments.length > 80) arguments.take(80) + "…" else arguments
                                tvOutput.append("\n🔧 调用工具: $toolName($argsPreview)\n")
                                scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                            }
                        }
                        override suspend fun onToolCallResult(toolName: String, result: String) {
                            withContext(Dispatchers.Main) {
                                val preview = if (result.length > 200) result.take(200) + "…" else result
                                tvOutput.append("📋 $toolName 结果: $preview\n\n")
                                scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                            }
                        }
                        override suspend fun onThinking(content: String) {
                            withContext(Dispatchers.Main) {
                                tvOutput.append("💭 $content")
                                scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                            }
                        }
                    }
                    val executor = ToolExecutor(client, registry, eventListener = listener, tracing = tracing)

                    // 任务规划（Agent 模式下可选）
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
                            planSession.appendPrompt {
                                user(buildString {
                                    appendLine("Before executing, create a brief plan for the task.")
                                    appendLine("Format: GOAL: <goal>")
                                    appendLine("Then list steps with '- ' prefix.")
                                    appendLine("Keep it concise (3-5 steps max).")
                                })
                            }
                            val planResult = planSession.requestLLMWithoutTools()
                            planSession.close()

                            withContext(Dispatchers.Main) {
                                tvOutput.append("📋 任务规划:\n${planResult.content}\n\n")
                                scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                            }
                        } catch (e: Exception) {
                            // 规划失败不影响正常执行
                            withContext(Dispatchers.Main) {
                                tvOutput.append("📋 [规划跳过: ${e.message}]\n\n")
                                scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                            }
                        }
                    }

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
                            "\n💭 思考: $preview\n"
                        } ?: ""

                        withContext(Dispatchers.Main) {
                            tvOutput.append("AI: $result$thinkingLine${formatUsageLine(usage)}")
                            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                        }
                    }
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
                                    tvOutput.append("💭 ")
                                    thinkingStarted = true
                                }
                                tvOutput.append(text)
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
                        "\n💭 思考: $preview\n"
                    } ?: ""

                    withContext(Dispatchers.Main) {
                        tvOutput.append("AI: $result$thinkingLine${formatUsageLine(usage)}")
                        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                    }
                }

                val assistantMsg = ChatMessage.assistant(result)
                messageHistory.add(assistantMsg)
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

                // 自动提取记忆事实
                if (memory != null && ProviderManager.isMemoryAutoExtract(this@MainActivity)) {
                    try {
                        val memPrompt = Prompt.build("memory-extract") {
                            for (msg in messageHistory) {
                                when (msg.role) {
                                    "system" -> system(msg.content)
                                    "user" -> user(msg.content)
                                    "assistant" -> assistant(msg.content)
                                }
                            }
                        }
                        val memSession = LLMSession(client, config.model, memPrompt)
                        memory.saveAutoDetectedFacts(
                            session = memSession,
                            scopes = listOf(MemoryScopeType.AGENT)
                        )
                        memSession.close()
                        withContext(Dispatchers.Main) {
                            tvOutput.append("🧠 记忆已更新\n")
                            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                        }
                    } catch (e: Exception) {
                        // 记忆提取失败不影响正常对话
                    }
                }

                // 智能上下文压缩
                if (ProviderManager.isCompressionEnabled(this@MainActivity)) {
                    withContext(Dispatchers.Main) {
                        updateSendButtonState(ButtonState.COMPRESSING)
                    }
                    tryCompressHistory(client, config.model)
                }
            } catch (e: CancellationException) {
                withContext(Dispatchers.Main) {
                    tvOutput.append("\n[⏹ 已停止生成]\n\n")
                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                }
            } catch (e: ChatClientException) {
                withContext(Dispatchers.Main) {
                    val errorMsg = when (e.errorType) {
                        ErrorType.NETWORK -> "网络错误: ${e.message}\n请检查网络连接后重试"
                        ErrorType.AUTHENTICATION -> "认证失败: ${e.message}\n请检查 API Key 是否正确"
                        ErrorType.RATE_LIMIT -> "请求过于频繁: ${e.message}\n请稍后再试"
                        ErrorType.MODEL_UNAVAILABLE -> "模型不可用: ${e.message}\n请检查模型名称或稍后重试"
                        ErrorType.INVALID_REQUEST -> "请求参数错误: ${e.message}"
                        ErrorType.SERVER_ERROR -> "服务器错误: ${e.message}\n请稍后重试"
                        else -> "错误: ${e.message}"
                    }
                    tvOutput.append("\n$errorMsg\n\n")
                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvOutput.append("\n未知错误: ${e.message}\n\n")
                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    updateSendButtonState(ButtonState.IDLE)
                    currentJob = null
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
                withContext(Dispatchers.Main) {
                    tvOutput.append("🗜️ 开始压缩上下文 [策略: $strategyName, 原始消息: ${originalMessageCount} 条]\n")
                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                }
            }
            override suspend fun onSummaryChunk(chunk: String) {
                withContext(Dispatchers.Main) {
                    tvOutput.append(chunk)
                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                }
            }
            override suspend fun onBlockCompressed(blockIndex: Int, totalBlocks: Int) {
                withContext(Dispatchers.Main) {
                    tvOutput.append("\n📦 块 $blockIndex/$totalBlocks 压缩完成\n")
                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                }
            }
            override suspend fun onCompressionDone(compressedMessageCount: Int) {
                withContext(Dispatchers.Main) {
                    tvOutput.append("\n✅ 上下文压缩完成 [压缩后: ${compressedMessageCount} 条消息]\n\n")
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
            withContext(Dispatchers.Main) {
                tvOutput.append("\n[⚠️ 压缩失败: ${e.message}]\n\n")
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
