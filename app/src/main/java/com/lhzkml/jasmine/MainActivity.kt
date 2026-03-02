package com.lhzkml.jasmine

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.lhzkml.jasmine.core.prompt.llm.ChatClient
import com.lhzkml.jasmine.core.prompt.llm.ChatClientRouter
import com.lhzkml.jasmine.core.prompt.llm.CompressionEventListener
import com.lhzkml.jasmine.core.prompt.llm.ContextManager
import com.lhzkml.jasmine.core.prompt.llm.HistoryCompressionStrategy
import com.lhzkml.jasmine.core.prompt.llm.LLMWriteSession
import com.lhzkml.jasmine.core.prompt.llm.ModelRegistry
import com.lhzkml.jasmine.core.prompt.llm.SystemContextCollector
import com.lhzkml.jasmine.core.prompt.llm.replaceHistoryWithTLDR
import com.lhzkml.jasmine.core.prompt.executor.ChatClientConfig
import com.lhzkml.jasmine.core.prompt.executor.ChatClientFactory
import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.Prompt
import com.lhzkml.jasmine.core.prompt.model.Usage
import com.lhzkml.jasmine.core.conversation.storage.ConversationRepository
import com.lhzkml.jasmine.core.agent.tools.*
import com.lhzkml.jasmine.core.agent.observe.event.EventHandler
import com.lhzkml.jasmine.core.agent.observe.snapshot.Persistence
import com.lhzkml.jasmine.core.agent.observe.trace.Tracing
import com.lhzkml.jasmine.core.config.ActiveProviderConfig
import com.lhzkml.jasmine.core.agent.runtime.AgentRuntimeBuilder
import com.lhzkml.jasmine.core.agent.runtime.CompressionStrategyBuilder
import com.lhzkml.jasmine.core.agent.runtime.McpConnectionManager
import com.lhzkml.jasmine.core.agent.runtime.ToolRegistryBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CONVERSATION_ID = "conversation_id"
    }

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var mainContent: LinearLayout
    private lateinit var etInput: EditText
    private lateinit var btnSend: MaterialButton
    private lateinit var rvChat: RecyclerView
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var chatStateManager: ChatStateManager
    private lateinit var chatLayoutManager: LinearLayoutManager
    private var pendingScroll: Runnable? = null
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
    private lateinit var checkpointRecovery: CheckpointRecovery
    /** 预加载的 MCP 工具（APP 启动时后台连接，全局共享实例） */
    private val mcpConnectionManager get() = AppConfig.mcpConnectionManager()
    /** 用户是否手动向上滚动（流式回复期间暂停自动滚动） */
    private var userScrolledUp = false
    /** 系统上下文收集器 — 自动拼接环境信息到 system prompt */
    private var contextCollector = SystemContextCollector()
    /** Agent 运行时构建器 */
    private val runtimeBuilder = AgentRuntimeBuilder(AppConfig.configRepo())
    /** 工具注册表构建器 */
    private val toolRegistryBuilder = ToolRegistryBuilder(AppConfig.configRepo())

    /**
     * 刷新系统上下文收集器
     * 委托给 AgentRuntimeBuilder，根据当前设置注册/注销上下文提供者。
     */
    private fun refreshContextCollector() {
        val isAgent = ProviderManager.isAgentMode(this)
        val wsPath = ProviderManager.getWorkspacePath(this)
        
        // 获取当前模型信息
        val activeId = ProviderManager.getActiveId()
        val modelName = if (activeId != null) {
            overrideModel ?: ProviderManager.getModel(this, activeId)
        } else {
            ""
        }
        
        contextCollector = runtimeBuilder.buildSystemContext(
            isAgentMode = isAgent,
            workspacePath = wsPath,
            modelName = modelName
        )
    }

    /**
     * 根据设置构建工具注册表
     * 委托给 ToolRegistryBuilder，平台相关回调（Shell 确认）在此提供。
     */
    private fun buildToolRegistry(): ToolRegistry {
        toolRegistryBuilder.workspacePath = ProviderManager.getWorkspacePath(this)
        toolRegistryBuilder.fallbackBasePath = getExternalFilesDir(null)?.absolutePath
        DialogHandlers.register(this, toolRegistryBuilder)
        return toolRegistryBuilder.build(ProviderManager.isAgentMode(this))
    }

    /**
     * APP 启动时后台预连接 MCP 服务器
     * 委托给 McpConnectionManager，UI 反馈通过 listener 回调。
     */
    private fun preconnectMcpServers() {
        mcpConnectionManager.listener = object : McpConnectionManager.ConnectionListener {
            override fun onConnected(serverName: String, transportType: com.lhzkml.jasmine.core.config.McpTransportType, toolCount: Int) {
                val transportLabel = when (transportType) {
                    com.lhzkml.jasmine.core.config.McpTransportType.STREAMABLE_HTTP -> "HTTP"
                    com.lhzkml.jasmine.core.config.McpTransportType.SSE -> "SSE"
                }
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(this@MainActivity, "MCP: $serverName 已连接 [$transportLabel] ($toolCount 个工具)", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onConnectionFailed(serverName: String, error: String) {
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(this@MainActivity, "MCP: $serverName 连接失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
        CoroutineScope(Dispatchers.IO).launch {
            mcpConnectionManager.preconnect()
        }
    }

    /**
     * 加载 MCP 工具到注册表
     * 委托给 McpConnectionManager。
     */
    private suspend fun loadMcpToolsInto(registry: ToolRegistry) {
        mcpConnectionManager.loadToolsInto(registry)
    }

    /**
     * 构建追踪系统
     * 委托给 AgentRuntimeBuilder。
     */
    private fun buildTracing(): Tracing? {
        return runtimeBuilder.buildTracing(getExternalFilesDir("traces"))
    }

    /**
     * 构建事件处理器
     * 委托给 AgentRuntimeBuilder，UI 回调在此提供。
     */
    private fun buildEventHandler(): EventHandler? {
        return runtimeBuilder.buildEventHandler { line ->
            withContext(Dispatchers.Main) {
                chatStateManager.handleSystemLog(line)
            }
        }
    }

    /**
     * 构建快照/持久化系统
     * 委托给 AgentRuntimeBuilder。
     */
    private fun buildPersistence(): Persistence? {
        return runtimeBuilder.buildPersistence(getExternalFilesDir("snapshots"))
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
        rvChat = findViewById(R.id.rvChat)
        chatAdapter = ChatAdapter()
        chatStateManager = ChatStateManager(chatAdapter) { autoScrollToBottom() }
        checkpointRecovery = CheckpointRecovery(
            activity = this,
            chatStateManager = chatStateManager,
            messageHistory = messageHistory,
            conversationRepo = conversationRepo,
            autoScroll = { autoScrollToBottom() },
            sendMessage = { sendMessage(it) }
        )
        chatLayoutManager = LinearLayoutManager(this)
        rvChat.layoutManager = chatLayoutManager
        rvChat.adapter = chatAdapter
        rvChat.itemAnimator = null
        chatAdapter.onStreamLayoutComplete = { autoScrollToBottom() }

        rvChat.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                etInput.clearFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(etInput.windowToken, 0)
            }
            false
        }

        rvChat.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (!isGenerating) return
                when (newState) {
                    RecyclerView.SCROLL_STATE_DRAGGING -> {
                        userScrolledUp = true
                    }
                    RecyclerView.SCROLL_STATE_IDLE -> {
                        if (!recyclerView.canScrollVertically(1)) {
                            userScrolledUp = false
                        }
                    }
                }
            }
        })

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
        mcpConnectionManager.close()
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
        val activeId = ProviderManager.getActiveId()
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
        val activeId = ProviderManager.getActiveId() ?: return
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
        chatStateManager.clearAll()
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
                messageHistory.addAll(messages.filter { it.role != "agent_log" })

                chatStateManager.clearAll()
                var usageIndex = 0
                for (msg in timedMessages) {
                    val time = formatTime(msg.createdAt)
                    when (msg.role) {
                        "user" -> {
                            chatStateManager.addUserMessage(msg.content, time)
                        }
                        "agent_log" -> {
                            val logText = if (msg.content.endsWith("\n")) msg.content else msg.content + "\n"
                            chatStateManager.addHistoryLogBlocks(listOf(ContentBlock.SystemLog(logText)))
                        }
                        "assistant" -> {
                            val usage = usageList.getOrNull(usageIndex)
                            val usageLine = if (usage != null) {
                                "[提示: ${usage.promptTokens} | 回复: ${usage.completionTokens} | 总计: ${usage.totalTokens}]"
                            } else ""
                            chatStateManager.addHistoryAiMessage(
                                blocks = listOf(ContentBlock.Text(msg.content)),
                                usageLine = usageLine,
                                time = time
                            )
                            usageIndex++
                        }
                    }
                }
                autoScrollToBottom()
            }

            // 启动恢复：检查是否有未完成的检查点（非墓碑），提示用户恢复
            if (ProviderManager.isSnapshotEnabled(this@MainActivity)
                && ProviderManager.getSnapshotStorage(this@MainActivity) == com.lhzkml.jasmine.core.config.SnapshotStorageType.FILE) {
                tryOfferStartupRecovery(conversationId)
            }
        }
    }

    private fun getOrCreateClient(config: ActiveProviderConfig): ChatClient {
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
        val config = ProviderManager.getActiveConfig()
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
        val now = ChatExecutor.formatTime(System.currentTimeMillis())
        chatStateManager.addUserMessage(message, now)
        etInput.text.clear()

        val client = getOrCreateClient(config)
        val userMsg = ChatMessage.user(message)

        val executor = ChatExecutor(
            context = this,
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
            tryOfferCheckpointRecovery = { e, msg -> tryOfferCheckpointRecovery(e, msg) },
            tryCompressHistory = { c, m -> tryCompressHistory(c, m) },
            onUpdateButtonState = { isCompressing ->
                if (isCompressing) updateSendButtonState(ButtonState.COMPRESSING)
                else {
                    updateSendButtonState(ButtonState.IDLE)
                    currentJob = null
                }
            }
        )

        currentJob = CoroutineScope(Dispatchers.IO).launch {
            executor.execute(message, userMsg, client, config)
        }
    }

    private suspend fun tryOfferCheckpointRecovery(error: Exception, originalMessage: String) {
        checkpointRecovery.tryOfferCheckpointRecovery(
            persistence, currentConversationId, error, originalMessage
        )
    }

    private suspend fun tryOfferStartupRecovery(conversationId: String) {
        checkpointRecovery.tryOfferStartupRecovery(conversationId)
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
                    chatStateManager.handleSystemLog("[Compress] 开始压缩上下文 [策略: $strategyName, 原始消息: ${originalMessageCount} 条]\n")
                }
            }
            override suspend fun onSummaryChunk(chunk: String) {
                withContext(Dispatchers.Main) {
                    chatStateManager.handleSystemLog(chunk)
                }
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
            withContext(Dispatchers.Main) {
                chatStateManager.handleSystemLog("\n[WARN] 压缩失败: ${e.message}]\n\n")
            }
        } finally {
            session.close()
        }
    }

    /**
     * 根据设置构建压缩策略
     * 委托给 CompressionStrategyBuilder。
     */
    private fun buildCompressionStrategy(): HistoryCompressionStrategy? {
        return CompressionStrategyBuilder.build(AppConfig.configRepo(), contextManager)
    }

    /** 自动滚动到底部（用户手动向上滚动时跳过，自带 debounce 防闪烁） */
    private fun autoScrollToBottom() {
        if (userScrolledUp) return
        pendingScroll?.let { rvChat.removeCallbacks(it) }
        val runnable = Runnable {
            pendingScroll = null
            val count = chatAdapter.itemCount
            if (count <= 0) return@Runnable
            val lastVisiblePos = chatLayoutManager.findLastVisibleItemPosition()
            if (lastVisiblePos >= count - 1) {
                val lastView = chatLayoutManager.findViewByPosition(count - 1) ?: return@Runnable
                val recyclerBottom = rvChat.height - rvChat.paddingBottom
                val dy = lastView.bottom - recyclerBottom
                if (dy > 0) rvChat.scrollBy(0, dy)
            } else {
                chatLayoutManager.scrollToPositionWithOffset(count - 1, 0)
            }
        }
        pendingScroll = runnable
        rvChat.postDelayed(runnable, 30)
    }

    private fun formatTime(timestamp: Long): String {
        return ChatExecutor.formatTime(timestamp)
    }
}
