package com.lhzkml.jasmine

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lhzkml.jasmine.core.prompt.executor.ApiType
import com.lhzkml.jasmine.core.conversation.storage.ConversationRepository
import com.lhzkml.jasmine.core.prompt.llm.SystemPromptManager
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

    private fun formatNumber(n: Int): String {
        return when {
            n >= 1_000_000 -> String.format("%.1fM tokens", n / 1_000_000.0)
            n >= 1_000 -> String.format("%.1fK tokens", n / 1_000.0)
            else -> "$n tokens"
        }
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    conversationRepo: ConversationRepository,
    onRefreshCallbackSet: ((()->Unit) -> Unit)? = null
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
            TextButton(
                onClick = onBack,
                colors = ButtonDefaults.textButtonColors(contentColor = TextPrimary)
            ) {
                Text("← 返回", fontSize = 14.sp)
            }
            
            Text(
                text = "设置",
                fontSize = 17.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            
            Spacer(modifier = Modifier.width(56.dp))
        }
        
        HorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp)
        
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
                // 模型供应商
                SettingsItem(
                    title = "模型供应商",
                    value = getProviderStatus(context),
                    onClick = {
                        context.startActivity(Intent(context, ProviderListActivity::class.java))
                    }
                )
            }
            
            // 工具调用开关
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
            
            // Agent 工具预设（仅工具开启时显示）
            if (toolsEnabled) {
                key(refreshTrigger) {
                    SettingsItem(
                        title = "Agent 工具预设",
                        value = getAgentToolPresetInfo(context),
                        onClick = {
                            context.startActivity(Intent(context, ToolConfigActivity::class.java).apply {
                                putExtra(ToolConfigActivity.EXTRA_AGENT_PRESET, true)
                            })
                        }
                    )
                }
            }
            
            // Agent 策略（仅工具开启时显示）
            if (toolsEnabled) {
                key(refreshTrigger) {
                    SettingsItem(
                        title = "Agent 策略",
                        value = getAgentStrategyInfo(context),
                        onClick = {
                            context.startActivity(Intent(context, AgentStrategyActivity::class.java))
                        }
                    )
                }
            }
            
            // MCP 工具
            key(refreshTrigger) {
                SettingsItem(
                    title = "MCP 工具",
                    value = getMcpInfo(context),
                    onClick = {
                        context.startActivity(Intent(context, McpServerActivity::class.java))
                    }
                )
            }
            
            // Shell 命令策略
            key(refreshTrigger) {
                SettingsItem(
                    title = "Shell 命令策略",
                    value = getShellPolicyInfo(context),
                    onClick = {
                        context.startActivity(Intent(context, ShellPolicyActivity::class.java))
                    }
                )
            }
            
            // 智能上下文压缩
            key(refreshTrigger) {
                SettingsItem(
                    title = "智能上下文压缩",
                    value = getCompressionInfo(context),
                    onClick = {
                        context.startActivity(Intent(context, CompressionConfigActivity::class.java))
                    }
                )
            }
            
            // 执行追踪
            key(refreshTrigger) {
                SettingsItem(
                    title = "执行追踪",
                    value = getTraceInfo(context),
                    onClick = {
                        context.startActivity(Intent(context, TraceConfigActivity::class.java))
                    }
                )
            }
            
            // 任务规划
            key(refreshTrigger) {
                SettingsItem(
                    title = "任务规划",
                    value = getPlannerInfo(context),
                    onClick = {
                        context.startActivity(Intent(context, PlannerConfigActivity::class.java))
                    }
                )
            }
            
            // 执行快照
            key(refreshTrigger) {
                SettingsItem(
                    title = "执行快照",
                    value = getSnapshotInfo(context),
                    onClick = {
                        context.startActivity(Intent(context, SnapshotConfigActivity::class.java))
                    }
                )
            }
            
            // 事件处理器
            key(refreshTrigger) {
                SettingsItem(
                    title = "事件处理器",
                    value = getEventHandlerInfo(context),
                    onClick = {
                        context.startActivity(Intent(context, EventHandlerConfigActivity::class.java))
                    }
                )
            }
            
            // 超时与续传
            key(refreshTrigger) {
                SettingsItem(
                    title = "超时与续传",
                    value = getTimeoutInfo(context),
                    onClick = {
                        context.startActivity(Intent(context, TimeoutConfigActivity::class.java))
                    }
                )
            }
            
            // Token 管理
            key(refreshTrigger) {
                SettingsItem(
                    title = "Token 管理",
                    subtitle = "限制每条回复的长度",
                    value = getMaxTokensInfo(context),
                    onClick = {
                        val intent = Intent(context, TokenManagementActivity::class.java)
                        context.startActivity(intent)
                    }
                )
            }
            
            // 采样参数
            key(refreshTrigger) {
                SettingsItem(
                    title = "采样参数",
                    subtitle = "控制 AI 回复的随机性和多样性",
                    value = getSamplingParamsInfo(context),
                    onClick = {
                        val intent = Intent(context, SamplingParamsConfigActivity::class.java)
                        context.startActivity(intent)
                    }
                )
            }
            
            // 系统提示词
            key(refreshTrigger) {
                SettingsItem(
                    title = "系统提示词",
                    value = getSystemPromptPreview(context),
                    onClick = {
                        val intent = Intent(context, SystemPromptConfigActivity::class.java)
                        context.startActivity(intent)
                    }
                )
            }
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.White,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    color = TextPrimary
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
            
            Text(
                text = value,
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier.padding(end = 8.dp)
            )
            
            Text(
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = TextPrimary,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = TextSecondary.copy(alpha = 0.5f),
                    uncheckedBorderColor = TextSecondary.copy(alpha = 0.5f)
                )
            )
        }
    }
}

// 辅助函数
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

private fun formatNumber(n: Int): String {
    return when {
        n >= 1_000_000 -> String.format("%.1fM tokens", n / 1_000_000.0)
        n >= 1_000 -> String.format("%.1fK tokens", n / 1_000.0)
        else -> "$n tokens"
    }
}
