package com.lhzkml.jasmine

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.lhzkml.jasmine.repository.*
import org.koin.android.ext.android.inject
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.ui.components.*
import com.lhzkml.jasmine.ui.navigation.Routes
import com.lhzkml.jasmine.core.prompt.executor.ApiType
import com.lhzkml.jasmine.core.conversation.storage.ConversationRepository
import com.lhzkml.jasmine.core.prompt.llm.SystemPromptManager
import com.lhzkml.jasmine.ui.theme.Accent
import com.lhzkml.jasmine.ui.theme.BgInput
import com.lhzkml.jasmine.ui.theme.BgPrimary
import com.lhzkml.jasmine.ui.theme.TextPrimary
import com.lhzkml.jasmine.ui.theme.TextSecondary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : ComponentActivity() {

    private lateinit var conversationRepo: ConversationRepository
    private var refreshCallback: (() -> Unit)? = null
    
    // Repository 注入
    private val toolSettingsRepository: ToolSettingsRepository by inject()
    private val ragConfigRepository: RagConfigRepository by inject()
    private val mcpRepository: McpRepository by inject()
    private val shellPolicyRepository: ShellPolicyRepository by inject()
    private val compressionSettingsRepository: CompressionSettingsRepository by inject()
    private val traceSettingsRepository: TraceSettingsRepository by inject()
    private val plannerSettingsRepository: PlannerSettingsRepository by inject()
    private val snapshotSettingsRepository: SnapshotSettingsRepository by inject()
    private val eventHandlerSettingsRepository: EventHandlerSettingsRepository by inject()
    private val timeoutSettingsRepository: TimeoutSettingsRepository by inject()
    private val llmSettingsRepository: LlmSettingsRepository by inject()
    private val agentStrategyRepository: AgentStrategyRepository by inject()
    private val rulesRepository: RulesRepository by inject()
    private val providerRepository: ProviderRepository by inject()
    private val sessionRepository: SessionRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        conversationRepo = ConversationRepository(this)
        
        setContent {
            SettingsScreen(
                onBack = { finish() },
                conversationRepo = conversationRepo,
                onRefreshCallbackSet = { callback ->
                    refreshCallback = callback
                },
                toolSettingsRepository = toolSettingsRepository,
                ragConfigRepository = ragConfigRepository,
                mcpRepository = mcpRepository,
                shellPolicyRepository = shellPolicyRepository,
                compressionSettingsRepository = compressionSettingsRepository,
                traceSettingsRepository = traceSettingsRepository,
                plannerSettingsRepository = plannerSettingsRepository,
                snapshotSettingsRepository = snapshotSettingsRepository,
                eventHandlerSettingsRepository = eventHandlerSettingsRepository,
                timeoutSettingsRepository = timeoutSettingsRepository,
                llmSettingsRepository = llmSettingsRepository,
                agentStrategyRepository = agentStrategyRepository,
                rulesRepository = rulesRepository,
                providerRepository = providerRepository,
                sessionRepository = sessionRepository
            )
        }
    }
    
    override fun onResume() {
        super.onResume()
        // 从其他页面返回时刷新状态
        refreshCallback?.invoke()
    }

}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    conversationRepo: ConversationRepository,
    onRefreshCallbackSet: ((()->Unit) -> Unit)? = null,
    onNavigate: (String) -> Unit = {},
    toolSettingsRepository: ToolSettingsRepository,
    ragConfigRepository: RagConfigRepository,
    mcpRepository: McpRepository,
    shellPolicyRepository: ShellPolicyRepository,
    compressionSettingsRepository: CompressionSettingsRepository,
    traceSettingsRepository: TraceSettingsRepository,
    plannerSettingsRepository: PlannerSettingsRepository,
    snapshotSettingsRepository: SnapshotSettingsRepository,
    eventHandlerSettingsRepository: EventHandlerSettingsRepository,
    timeoutSettingsRepository: TimeoutSettingsRepository,
    llmSettingsRepository: LlmSettingsRepository,
    agentStrategyRepository: AgentStrategyRepository,
    rulesRepository: RulesRepository,
    providerRepository: ProviderRepository,
    sessionRepository: SessionRepository
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    
    var toolsEnabled by remember { mutableStateOf(toolSettingsRepository.isToolsEnabled()) }
    
    // 状态刷新触发器
    var refreshTrigger by remember { mutableStateOf(0) }
    
    // 刷新函数
    val refresh: () -> Unit = {
        toolsEnabled = toolSettingsRepository.isToolsEnabled()
        refreshTrigger++
    }
    
    // 设置刷新回调
    LaunchedEffect(Unit) {
        onRefreshCallbackSet?.invoke(refresh)
    }
    
    // 监听生命周期，在 onResume 时自动刷新
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
    ) {
        // 顶部栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(Color.White)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CustomTextButton(
                onClick = onBack,
                contentColor = TextPrimary
            ) {
                CustomText("← 返回", fontSize = 14.sp, color = TextPrimary)
            }
            
            CustomText(
                text = "设置",
                fontSize = 17.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            
            Spacer(modifier = Modifier.width(56.dp))
        }
        
        CustomHorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp)
        
        // 滚动内容
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 使用 key 让所有设置项在 refreshTrigger 变化时重新读取状态
            key(refreshTrigger) {
                // ─── 一、模型与供应商 ───
                SettingsItem(
                    title = "模型供应商",
                    value = getProviderStatusRepo(providerRepository),
                    onClick = { onNavigate(Routes.PROVIDER_LIST) }
                )
                SettingsItem(
                    title = "本地 MNN",
                    subtitle = "管理本地 LLM 推理模型",
                    value = "进入管理",
                    onClick = { onNavigate(Routes.MNN_MANAGEMENT) }
                )

                // ─── 二、模型参数 ───
                SettingsItem(
                    title = "Token 管理",
                    subtitle = "限制每条回复的长度",
                    value = getMaxTokensInfoRepo(llmSettingsRepository),
                    onClick = { onNavigate(Routes.TOKEN_MANAGEMENT) }
                )
                SettingsItem(
                    title = "采样参数",
                    subtitle = "控制 AI 回复的随机性和多样性",
                    value = getSamplingParamsInfoRepo(llmSettingsRepository),
                    onClick = { onNavigate(Routes.SAMPLING_PARAMS) }
                )
                SettingsItem(
                    title = "系统提示词",
                    value = getSystemPromptPreviewRepo(llmSettingsRepository),
                    onClick = { onNavigate(Routes.SYSTEM_PROMPT) }
                )
                SettingsItem(
                    title = "RAG 知识库",
                    subtitle = "向量检索增强上下文",
                    value = getRagInfoRepo(ragConfigRepository),
                    onClick = { onNavigate(Routes.RAG_CONFIG) }
                )

                // ─── Rules ───
                SettingsItem(
                    title = "Rules 规则",
                    subtitle = "个人规则 · 项目规则",
                    value = getRulesPreviewRepo(rulesRepository, sessionRepository),
                    onClick = { onNavigate(Routes.RULES) }
                )
            }

            // ─── 三、Agent 与工具 ───
            SettingsSwitchItem(
                title = "工具调用",
                subtitle = "启用 Agent 模式，AI 可调用工具",
                checked = toolsEnabled,
                onCheckedChange = { checked ->
                    toolsEnabled = checked
                    toolSettingsRepository.setToolsEnabled(checked)
                    refreshTrigger++
                }
            )
            if (toolsEnabled) {
                key(refreshTrigger) {
                    SettingsItem(
                        title = "Agent 工具预设",
                        value = getAgentToolPresetInfoRepo(toolSettingsRepository),
                        onClick = { onNavigate(Routes.TOOL_CONFIG_AGENT) }
                    )
                    SettingsItem(
                        title = "Agent 策略",
                        value = getAgentStrategyInfoRepo(agentStrategyRepository),
                        onClick = { onNavigate(Routes.AGENT_STRATEGY) }
                    )
                    SettingsItem(
                        title = "MCP 工具",
                        value = getMcpInfoRepo(mcpRepository),
                        onClick = { onNavigate(Routes.MCP_SERVER) }
                    )
                    SettingsItem(
                        title = "Shell 命令策略",
                        value = getShellPolicyInfoRepo(shellPolicyRepository),
                        onClick = { onNavigate(Routes.SHELL_POLICY) }
                    )
                }
            }

            // ─── 四、执行与调试 ───
            key(refreshTrigger) {
                SettingsItem(
                    title = "智能上下文压缩",
                    value = getCompressionInfoRepo(compressionSettingsRepository),
                    onClick = { onNavigate(Routes.COMPRESSION_CONFIG) }
                )
                SettingsItem(
                    title = "超时与续传",
                    value = getTimeoutInfoRepo(timeoutSettingsRepository),
                    onClick = { onNavigate(Routes.TIMEOUT_CONFIG) }
                )
                SettingsItem(
                    title = "执行追踪",
                    value = getTraceInfoRepo(traceSettingsRepository),
                    onClick = { onNavigate(Routes.TRACE_CONFIG) }
                )
                SettingsItem(
                    title = "任务规划",
                    value = getPlannerInfoRepo(plannerSettingsRepository),
                    onClick = { onNavigate(Routes.PLANNER_CONFIG) }
                )
                SettingsItem(
                    title = "执行快照",
                    value = getSnapshotInfoRepo(snapshotSettingsRepository),
                    onClick = { onNavigate(Routes.SNAPSHOT_CONFIG) }
                )
                SettingsItem(
                    title = "事件处理器",
                    value = getEventHandlerInfoRepo(eventHandlerSettingsRepository),
                    onClick = { onNavigate(Routes.EVENT_HANDLER_CONFIG) }
                )
            }

            // ─── 五、关于 ───
            SettingsItem(
                title = "关于",
                subtitle = "应用与依赖版本信息",
                value = "查看",
                onClick = { onNavigate(Routes.ABOUT) }
            )
            
        }
    }
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String? = null,
    value: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                CustomText(
                    text = title,
                    fontSize = 15.sp,
                    color = TextPrimary
                )
                if (subtitle != null) {
                    CustomText(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
            
            CustomText(
                text = value,
                fontSize = 13.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 150.dp)
                    .padding(end = 8.dp)
            )
            
            CustomText(
                text = "›",
                fontSize = 18.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun SettingsSwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                CustomText(
                    text = title,
                    fontSize = 15.sp,
                    color = TextPrimary
                )
                CustomText(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            
            CustomSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                checkedThumbColor = Color.White,
                checkedTrackColor = Accent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = TextSecondary.copy(alpha = 0.5f)
            )
        }
    }
}

// 辅助函数
fun getRagInfoRepo(ragRepo: RagConfigRepository): String {
    if (!ragRepo.isRagEnabled()) return "已关闭"
    val topK = ragRepo.getRagTopK()
    val useLocal = ragRepo.getRagEmbeddingUseLocal()
    val hasConfig = if (useLocal) {
        ragRepo.getRagEmbeddingModelPath().isNotBlank()
    } else {
        ragRepo.getRagEmbeddingBaseUrl().isNotBlank() &&
            ragRepo.getRagEmbeddingApiKey().isNotBlank()
    }
    return if (hasConfig) "TopK $topK · 已配置" else "未配置"
}

fun getProviderStatusRepo(providerRepo: ProviderRepository): String {
    val activeId = providerRepo.getActiveProviderId()
    return if (activeId != null) {
        val provider = providerRepo.getProvider(activeId)
        provider?.name ?: activeId
    } else {
        "未配置"
    }
}

fun getAgentToolPresetInfoRepo(toolRepo: ToolSettingsRepository): String {
    val preset = toolRepo.getEnabledTools()
    return if (preset.isEmpty()) "全部工具已启用（默认）" else "已启用 ${preset.size} 个工具"
}

fun getAgentStrategyInfoRepo(strategyRepo: AgentStrategyRepository): String {
    val strategy = strategyRepo.getAgentStrategy()
    return when (strategy) {
        com.lhzkml.jasmine.core.config.AgentStrategyType.SIMPLE_LOOP -> "简单循环（ToolExecutor）"
        com.lhzkml.jasmine.core.config.AgentStrategyType.SINGLE_RUN_GRAPH -> "图策略（GraphAgent）"
    }
}

fun getMcpInfoRepo(mcpRepo: McpRepository): String {
    val servers = mcpRepo.getMcpServers()
    val enabledCount = servers.count { it.enabled }
    return if (servers.isEmpty()) "已开启 · 未配置服务器" else "已配置 ${servers.size} 个 · 启用 $enabledCount 个"
}

fun getShellPolicyInfoRepo(shellRepo: ShellPolicyRepository): String {
    val policy = shellRepo.getShellPolicy()
    return when (policy) {
        com.lhzkml.jasmine.core.agent.tools.ShellPolicy.MANUAL -> "手动确认"
        com.lhzkml.jasmine.core.agent.tools.ShellPolicy.BLACKLIST -> "黑名单模式"
        com.lhzkml.jasmine.core.agent.tools.ShellPolicy.WHITELIST -> "白名单模式"
    }
}

fun getCompressionInfoRepo(compRepo: CompressionSettingsRepository): String {
    if (!compRepo.isCompressionEnabled()) return "已关闭"
    val strategy = compRepo.getCompressionStrategy()
    return when (strategy) {
        com.lhzkml.jasmine.core.prompt.llm.CompressionStrategyType.TOKEN_BUDGET -> {
            val maxTokens = compRepo.getCompressionMaxTokens()
            val threshold = compRepo.getCompressionThreshold()
            val tokenStr = if (maxTokens > 0) "${maxTokens}" else "跟随模型"
            "Token 预算 · $tokenStr · 阈值 ${threshold}%"
        }
        com.lhzkml.jasmine.core.prompt.llm.CompressionStrategyType.WHOLE_HISTORY -> "整体压缩"
        com.lhzkml.jasmine.core.prompt.llm.CompressionStrategyType.LAST_N -> {
            val n = compRepo.getCompressionLastN()
            "保留最后 ${n} 条"
        }
        com.lhzkml.jasmine.core.prompt.llm.CompressionStrategyType.CHUNKED -> {
            val size = compRepo.getCompressionChunkSize()
            "分块压缩 · 每块 ${size} 条"
        }
        com.lhzkml.jasmine.core.prompt.llm.CompressionStrategyType.PROGRESSIVE -> {
            val rounds = compRepo.getCompressionKeepRecentRounds()
            val threshold = compRepo.getCompressionThreshold()
            "渐进式 · 保留 ${rounds} 轮 · 阈值 ${threshold}%"
        }
    }
}

fun getTraceInfoRepo(traceRepo: TraceSettingsRepository): String {
    if (!traceRepo.isTraceEnabled()) return "已关闭"
    val file = traceRepo.isTraceFileEnabled()
    val filter = traceRepo.getTraceEventFilter()
    val outputParts = mutableListOf<String>()
    outputParts.add("Android Log")
    if (file) outputParts.add("文件输出")
    val filterStr = if (filter.isEmpty()) "全部事件" else "${filter.size} 类事件"
    return "${outputParts.joinToString(" · ")} · $filterStr"
}

fun getPlannerInfoRepo(plannerRepo: PlannerSettingsRepository): String {
    if (!plannerRepo.isPlannerEnabled()) return "已关闭"
    val maxIter = plannerRepo.getPlannerMaxIterations()
    val critic = plannerRepo.isPlannerCriticEnabled()
    val criticStr = if (critic) "Critic 评估" else "无 Critic"
    return "迭代 $maxIter 次 · $criticStr"
}

fun getSnapshotInfoRepo(snapshotRepo: SnapshotSettingsRepository): String {
    if (!snapshotRepo.isSnapshotEnabled()) return "已关闭"
    val storage = snapshotRepo.getSnapshotStorage()
    val auto = snapshotRepo.isSnapshotAutoCheckpoint()
    val rollback = snapshotRepo.getSnapshotRollbackStrategy()
    val storageName = when (storage) {
        com.lhzkml.jasmine.core.config.SnapshotStorageType.MEMORY -> "内存存储"
        com.lhzkml.jasmine.core.config.SnapshotStorageType.FILE -> "文件存储"
    }
    val autoStr = if (auto) "自动检查点" else "手动检查点"
    val rollbackName = when (rollback) {
        com.lhzkml.jasmine.core.agent.observe.snapshot.RollbackStrategy.RESTART_FROM_NODE -> "从节点重启"
        com.lhzkml.jasmine.core.agent.observe.snapshot.RollbackStrategy.SKIP_NODE -> "跳过节点"
        com.lhzkml.jasmine.core.agent.observe.snapshot.RollbackStrategy.USE_DEFAULT_OUTPUT -> "默认输出"
    }
    return "$storageName · $autoStr · $rollbackName"
}

fun getEventHandlerInfoRepo(eventRepo: EventHandlerSettingsRepository): String {
    if (!eventRepo.isEventHandlerEnabled()) return "已关闭"
    val filter = eventRepo.getEventHandlerFilter()
    return if (filter.isEmpty()) "全部事件" else "${filter.size} 类事件"
}

fun getTimeoutInfoRepo(timeoutRepo: TimeoutSettingsRepository): String {
    val reqTimeout = timeoutRepo.getRequestTimeout()
    val socketTimeout = timeoutRepo.getSocketTimeout()
    val connectTimeout = timeoutRepo.getConnectTimeout()
    val resumeEnabled = timeoutRepo.isStreamResumeEnabled()
    
    val parts = mutableListOf<String>()
    if (reqTimeout > 0) parts.add("请求 ${reqTimeout}s")
    if (socketTimeout > 0) parts.add("读取 ${socketTimeout}s")
    if (connectTimeout > 0) parts.add("连接 ${connectTimeout}s")
    
    val timeoutStr = if (parts.isEmpty()) "默认" else parts.joinToString(" · ")
    val resumeStr = if (resumeEnabled) "续传开启" else "续传关闭"
    return "$timeoutStr · $resumeStr"
}

fun getMaxTokensInfoRepo(llmRepo: LlmSettingsRepository): String {
    val maxTokens = llmRepo.getMaxTokens()
    return if (maxTokens > 0) "$maxTokens" else "不限制"
}

fun getSystemPromptPreviewRepo(llmRepo: LlmSettingsRepository): String {
    val prompt = llmRepo.getDefaultSystemPrompt()
    return if (prompt.length > 30) prompt.substring(0, 30) + "..." else prompt
}

fun getSamplingParamsInfoRepo(llmRepo: LlmSettingsRepository): String {
    val temperature = llmRepo.getTemperature()
    val topP = llmRepo.getTopP()
    val topK = llmRepo.getTopK()
    
    val parts = mutableListOf<String>()
    if (temperature >= 0f) {
        parts.add("T: ${String.format("%.2f", temperature)}")
    }
    if (topP >= 0f) {
        parts.add("P: ${String.format("%.2f", topP)}")
    }
    if (topK >= 0) {
        parts.add("K: $topK")
    }
    
    return if (parts.isEmpty()) "全部使用默认值" else parts.joinToString(" · ")
}

fun getRulesPreviewRepo(rulesRepo: RulesRepository, sessionRepo: SessionRepository): String {
    val personal = rulesRepo.getPersonalRules()
    val wsPath = sessionRepo.getWorkspacePath()
    val project = if (wsPath.isNotBlank()) rulesRepo.getProjectRules(wsPath) else ""

    val hasPersonal = personal.isNotBlank()
    val hasProject = project.isNotBlank()

    return when {
        hasPersonal && hasProject -> "个人 · 项目已配置"
        hasPersonal -> "个人已配置"
        hasProject -> "项目已配置"
        else -> "未设置"
    }
}