package com.lhzkml.jasmine

import android.os.Bundle
import androidx.activity.ComponentActivity
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        conversationRepo = ConversationRepository(this)
        
        setContent {
            SettingsScreen(
                onBack = { finish() },
                conversationRepo = conversationRepo,
                onRefreshCallbackSet = { callback ->
                    refreshCallback = callback
                }
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
    onNavigate: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    
    var toolsEnabled by remember { mutableStateOf(ProviderManager.isToolsEnabled(context)) }
    
    // 状态刷新触发器
    var refreshTrigger by remember { mutableStateOf(0) }
    
    // 刷新函数
    val refresh: () -> Unit = {
        toolsEnabled = ProviderManager.isToolsEnabled(context)
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
                    value = getProviderStatus(context),
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
                    value = getMaxTokensInfo(context),
                    onClick = { onNavigate(Routes.TOKEN_MANAGEMENT) }
                )
                SettingsItem(
                    title = "采样参数",
                    subtitle = "控制 AI 回复的随机性和多样性",
                    value = getSamplingParamsInfo(context),
                    onClick = { onNavigate(Routes.SAMPLING_PARAMS) }
                )
                SettingsItem(
                    title = "系统提示词",
                    value = getSystemPromptPreview(context),
                    onClick = { onNavigate(Routes.SYSTEM_PROMPT) }
                )
                SettingsItem(
                    title = "RAG 知识库",
                    subtitle = "向量检索增强上下文",
                    value = getRagInfo(context),
                    onClick = { onNavigate(Routes.RAG_CONFIG) }
                )

                // ─── Rules ───
                SettingsItem(
                    title = "Rules 规则",
                    subtitle = "个人规则 · 项目规则",
                    value = getRulesPreview(context),
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
                    ProviderManager.setToolsEnabled(context, checked)
                    refreshTrigger++
                }
            )
            if (toolsEnabled) {
                key(refreshTrigger) {
                    SettingsItem(
                        title = "Agent 工具预设",
                        value = getAgentToolPresetInfo(context),
                        onClick = { onNavigate(Routes.TOOL_CONFIG_AGENT) }
                    )
                    SettingsItem(
                        title = "Agent 策略",
                        value = getAgentStrategyInfo(context),
                        onClick = { onNavigate(Routes.AGENT_STRATEGY) }
                    )
                    SettingsItem(
                        title = "MCP 工具",
                        value = getMcpInfo(context),
                        onClick = { onNavigate(Routes.MCP_SERVER) }
                    )
                    SettingsItem(
                        title = "Shell 命令策略",
                        value = getShellPolicyInfo(context),
                        onClick = { onNavigate(Routes.SHELL_POLICY) }
                    )
                }
            }

            // ─── 四、执行与调试 ───
            key(refreshTrigger) {
                SettingsItem(
                    title = "智能上下文压缩",
                    value = getCompressionInfo(context),
                    onClick = { onNavigate(Routes.COMPRESSION_CONFIG) }
                )
                SettingsItem(
                    title = "超时与续传",
                    value = getTimeoutInfo(context),
                    onClick = { onNavigate(Routes.TIMEOUT_CONFIG) }
                )
                SettingsItem(
                    title = "执行追踪",
                    value = getTraceInfo(context),
                    onClick = { onNavigate(Routes.TRACE_CONFIG) }
                )
                SettingsItem(
                    title = "任务规划",
                    value = getPlannerInfo(context),
                    onClick = { onNavigate(Routes.PLANNER_CONFIG) }
                )
                SettingsItem(
                    title = "执行快照",
                    value = getSnapshotInfo(context),
                    onClick = { onNavigate(Routes.SNAPSHOT_CONFIG) }
                )
                SettingsItem(
                    title = "事件处理器",
                    value = getEventHandlerInfo(context),
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
private fun getRagInfo(context: android.content.Context): String {
    if (!ProviderManager.isRagEnabled(context)) return "已关闭"
    val topK = ProviderManager.getRagTopK(context)
    val useLocal = ProviderManager.getRagEmbeddingUseLocal(context)
    val hasConfig = if (useLocal) {
        ProviderManager.getRagEmbeddingModelPath(context).isNotBlank()
    } else {
        ProviderManager.getRagEmbeddingBaseUrl(context).isNotBlank() &&
            ProviderManager.getRagEmbeddingApiKey(context).isNotBlank()
    }
    return if (hasConfig) "TopK $topK · 已配置" else "未配置"
}

private fun getProviderStatus(context: android.content.Context): String {
    val config = ProviderManager.getActiveConfig()
    return if (config != null) {
        val provider = ProviderManager.getAllProviders().find { it.id == config.providerId }
        provider?.name ?: config.providerId
    } else {
        "未配置"
    }
}

private fun getAgentToolPresetInfo(context: android.content.Context): String {
    val preset = ProviderManager.getAgentToolPreset(context)
    return if (preset.isEmpty()) "全部工具已启用（默认）" else "已启用 ${preset.size} 个工具"
}

private fun getAgentStrategyInfo(context: android.content.Context): String {
    val strategy = ProviderManager.getAgentStrategy(context)
    return when (strategy) {
        com.lhzkml.jasmine.core.config.AgentStrategyType.SIMPLE_LOOP -> "简单循环（ToolExecutor）"
        com.lhzkml.jasmine.core.config.AgentStrategyType.SINGLE_RUN_GRAPH -> "图策略（GraphAgent）"
    }
}

private fun getMcpInfo(context: android.content.Context): String {
    if (!ProviderManager.isMcpEnabled(context)) {
        return "已关闭"
    }
    val servers = ProviderManager.getMcpServers(context)
    val enabledCount = servers.count { it.enabled }
    return if (servers.isEmpty()) "已开启 · 未配置服务器" else "已配置 ${servers.size} 个 · 启用 $enabledCount 个"
}

private fun getShellPolicyInfo(context: android.content.Context): String {
    val policy = ProviderManager.getShellPolicy(context)
    return when (policy) {
        com.lhzkml.jasmine.core.agent.tools.ShellPolicy.MANUAL -> "手动确认"
        com.lhzkml.jasmine.core.agent.tools.ShellPolicy.BLACKLIST -> "黑名单模式"
        com.lhzkml.jasmine.core.agent.tools.ShellPolicy.WHITELIST -> "白名单模式"
    }
}

private fun getCompressionInfo(context: android.content.Context): String {
    if (!ProviderManager.isCompressionEnabled(context)) {
        return "已关闭"
    }
    val strategy = ProviderManager.getCompressionStrategy(context)
    return when (strategy) {
        com.lhzkml.jasmine.core.prompt.llm.CompressionStrategyType.TOKEN_BUDGET -> {
            val maxTokens = ProviderManager.getCompressionMaxTokens(context)
            val threshold = ProviderManager.getCompressionThreshold(context)
            val tokenStr = if (maxTokens > 0) "${maxTokens}" else "跟随模型"
            "Token 预算 · $tokenStr · 阈值 ${threshold}%"
        }
        com.lhzkml.jasmine.core.prompt.llm.CompressionStrategyType.WHOLE_HISTORY -> "整体压缩"
        com.lhzkml.jasmine.core.prompt.llm.CompressionStrategyType.LAST_N -> {
            val n = ProviderManager.getCompressionLastN(context)
            "保留最后 ${n} 条"
        }
        com.lhzkml.jasmine.core.prompt.llm.CompressionStrategyType.CHUNKED -> {
            val size = ProviderManager.getCompressionChunkSize(context)
            "分块压缩 · 每块 ${size} 条"
        }
        com.lhzkml.jasmine.core.prompt.llm.CompressionStrategyType.PROGRESSIVE -> {
            val rounds = ProviderManager.getCompressionKeepRecentRounds(context)
            val threshold = ProviderManager.getCompressionThreshold(context)
            "渐进式 · 保留 ${rounds} 轮 · 阈值 ${threshold}%"
        }
    }
}

private fun getTraceInfo(context: android.content.Context): String {
    if (!ProviderManager.isTraceEnabled(context)) {
        return "已关闭"
    }
    val file = ProviderManager.isTraceFileEnabled(context)
    val filter = ProviderManager.getTraceEventFilter(context)
    val outputParts = mutableListOf<String>()
    outputParts.add("Android Log")
    if (file) outputParts.add("文件输出")
    val filterStr = if (filter.isEmpty()) "全部事件" else "${filter.size} 类事件"
    return "${outputParts.joinToString(" · ")} · $filterStr"
}

private fun getPlannerInfo(context: android.content.Context): String {
    if (!ProviderManager.isPlannerEnabled(context)) {
        return "已关闭"
    }
    val maxIter = ProviderManager.getPlannerMaxIterations(context)
    val critic = ProviderManager.isPlannerCriticEnabled(context)
    val criticStr = if (critic) "Critic 评估" else "无 Critic"
    return "迭代 $maxIter 次 · $criticStr"
}

private fun getSnapshotInfo(context: android.content.Context): String {
    if (!ProviderManager.isSnapshotEnabled(context)) {
        return "已关闭"
    }
    val storage = ProviderManager.getSnapshotStorage(context)
    val auto = ProviderManager.isSnapshotAutoCheckpoint(context)
    val rollback = ProviderManager.getSnapshotRollbackStrategy(context)
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

private fun getEventHandlerInfo(context: android.content.Context): String {
    if (!ProviderManager.isEventHandlerEnabled(context)) {
        return "已关闭"
    }
    val filter = ProviderManager.getEventHandlerFilter(context)
    return if (filter.isEmpty()) "全部事件" else "${filter.size} 类事件"
}

private fun getTimeoutInfo(context: android.content.Context): String {
    val reqTimeout = ProviderManager.getRequestTimeout(context)
    val socketTimeout = ProviderManager.getSocketTimeout(context)
    val connectTimeout = ProviderManager.getConnectTimeout(context)
    val resumeEnabled = ProviderManager.isStreamResumeEnabled(context)
    
    val parts = mutableListOf<String>()
    if (reqTimeout > 0) parts.add("请求 ${reqTimeout}s")
    if (socketTimeout > 0) parts.add("读取 ${socketTimeout}s")
    if (connectTimeout > 0) parts.add("连接 ${connectTimeout}s")
    
    val timeoutStr = if (parts.isEmpty()) "默认" else parts.joinToString(" · ")
    val resumeStr = if (resumeEnabled) "续传开启" else "续传关闭"
    return "$timeoutStr · $resumeStr"
}

private fun getMaxTokensInfo(context: android.content.Context): String {
    val maxTokens = ProviderManager.getMaxTokens(context)
    return if (maxTokens > 0) "$maxTokens" else "不限制"
}

private fun getSystemPromptPreview(context: android.content.Context): String {
    val prompt = ProviderManager.getDefaultSystemPrompt(context)
    return if (prompt.length > 30) prompt.substring(0, 30) + "..." else prompt
}

private fun getSamplingParamsInfo(context: android.content.Context): String {
    val temperature = ProviderManager.getTemperature(context)
    val topP = ProviderManager.getTopP(context)
    val topK = ProviderManager.getTopK(context)
    
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

private fun formatNumber(n: Int): String =
    com.lhzkml.jasmine.core.config.FormatUtils.formatTokensWithUnit(n)

private fun getRulesPreview(context: android.content.Context): String {
    val personal = ProviderManager.getPersonalRules(context)
    val wsPath = ProviderManager.getWorkspacePath(context)
    val project = if (wsPath.isNotBlank()) ProviderManager.getProjectRules(context, wsPath) else ""

    val hasPersonal = personal.isNotBlank()
    val hasProject = project.isNotBlank()

    return when {
        hasPersonal && hasProject -> "个人 · 项目已配置"
        hasPersonal -> "个人已配置"
        hasProject -> "项目已配置"
        else -> "未设置"
    }
}
