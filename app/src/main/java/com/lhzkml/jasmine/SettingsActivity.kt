package com.lhzkml.jasmine

import android.content.Intent
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
import com.lhzkml.jasmine.mnn.MnnManagementActivity
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
                    onClick = {
                        context.startActivity(Intent(context, ProviderListActivity::class.java))
                    }
                )
                SettingsItem(
                    title = "本地 MNN",
                    subtitle = "管理本地 LLM 推理模型",
                    value = "进入管理",
                    onClick = {
                        context.startActivity(Intent(context, MnnManagementActivity::class.java))
                    }
                )

                // ─── 二、模型参数 ───
                SettingsItem(
                    title = "Token 管理",
                    subtitle = "限制每条回复的长度",
                    value = getMaxTokensInfo(context),
                    onClick = {
                        context.startActivity(Intent(context, TokenManagementActivity::class.java))
                    }
                )
                SettingsItem(
                    title = "采样参数",
                    subtitle = "控制 AI 回复的随机性和多样性",
                    value = getSamplingParamsInfo(context),
                    onClick = {
                        context.startActivity(Intent(context, SamplingParamsConfigActivity::class.java))
                    }
                )
                SettingsItem(
                    title = "系统提示词",
                    value = getSystemPromptPreview(context),
                    onClick = {
                        context.startActivity(Intent(context, SystemPromptConfigActivity::class.java))
                    }
                )

                // ─── Rules ───
                RulesSettingsSection(context, onRulesChanged = { refreshTrigger++ })
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
                        onClick = {
                            context.startActivity(Intent(context, ToolConfigActivity::class.java).apply {
                                putExtra(ToolConfigActivity.EXTRA_AGENT_PRESET, true)
                            })
                        }
                    )
                    SettingsItem(
                        title = "Agent 策略",
                        value = getAgentStrategyInfo(context),
                        onClick = {
                            context.startActivity(Intent(context, AgentStrategyActivity::class.java))
                        }
                    )
                    SettingsItem(
                        title = "MCP 工具",
                        value = getMcpInfo(context),
                        onClick = {
                            context.startActivity(Intent(context, McpServerActivity::class.java))
                        }
                    )
                    SettingsItem(
                        title = "Shell 命令策略",
                        value = getShellPolicyInfo(context),
                        onClick = {
                            context.startActivity(Intent(context, ShellPolicyActivity::class.java))
                        }
                    )
                }
            }

            // ─── 四、执行与调试 ───
            key(refreshTrigger) {
                SettingsItem(
                    title = "智能上下文压缩",
                    value = getCompressionInfo(context),
                    onClick = {
                        context.startActivity(Intent(context, CompressionConfigActivity::class.java))
                    }
                )
                SettingsItem(
                    title = "超时与续传",
                    value = getTimeoutInfo(context),
                    onClick = {
                        context.startActivity(Intent(context, TimeoutConfigActivity::class.java))
                    }
                )
                SettingsItem(
                    title = "执行追踪",
                    value = getTraceInfo(context),
                    onClick = {
                        context.startActivity(Intent(context, TraceConfigActivity::class.java))
                    }
                )
                SettingsItem(
                    title = "任务规划",
                    value = getPlannerInfo(context),
                    onClick = {
                        context.startActivity(Intent(context, PlannerConfigActivity::class.java))
                    }
                )
                SettingsItem(
                    title = "执行快照",
                    value = getSnapshotInfo(context),
                    onClick = {
                        context.startActivity(Intent(context, SnapshotConfigActivity::class.java))
                    }
                )
                SettingsItem(
                    title = "事件处理器",
                    value = getEventHandlerInfo(context),
                    onClick = {
                        context.startActivity(Intent(context, EventHandlerConfigActivity::class.java))
                    }
                )
            }

            // ─── 五、关于 ───
            SettingsItem(
                title = "关于",
                subtitle = "应用与依赖版本信息",
                value = "查看",
                onClick = {
                    context.startActivity(Intent(context, AboutActivity::class.java))
                }
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

private fun formatNumber(n: Int): String =
    com.lhzkml.jasmine.core.config.FormatUtils.formatTokensWithUnit(n)

// ========== Rules Compose UI ==========

@Composable
fun RulesSettingsSection(context: android.content.Context, onRulesChanged: () -> Unit) {
    var showPersonalDialog by remember { mutableStateOf(false) }
    var showProjectDialog by remember { mutableStateOf(false) }
    var showNoWorkspaceDialog by remember { mutableStateOf(false) }

    val personalRules = ProviderManager.getPersonalRules(context)
    val personalPreview = if (personalRules.isBlank()) "未设置"
    else if (personalRules.length > 30) personalRules.substring(0, 30) + "..." else personalRules

    val wsPath = ProviderManager.getWorkspacePath(context)
    val projectPreview = if (wsPath.isEmpty()) "未设置工作区"
    else {
        val rules = ProviderManager.getProjectRules(context, wsPath)
        if (rules.isBlank()) "未设置" else if (rules.length > 30) rules.substring(0, 30) + "..." else rules
    }

    SettingsItem(
        title = "个人 Rules",
        subtitle = "全局行为规则，切换项目依然生效",
        value = personalPreview,
        onClick = { showPersonalDialog = true }
    )
    SettingsItem(
        title = "项目 Rules",
        subtitle = "当前项目专属规则，仅此工作区生效",
        value = projectPreview,
        onClick = {
            if (wsPath.isEmpty()) showNoWorkspaceDialog = true
            else showProjectDialog = true
        }
    )

    if (showPersonalDialog) {
        RulesEditorDialog(
            title = "个人 Rules",
            description = "定义全局行为规则，切换项目后依然生效。每行一条规则。",
            placeholder = "每行一条规则，例如：\nAlways respond in Chinese\n代码生成时添加注释",
            initialValue = personalRules,
            onSave = { rules ->
                ProviderManager.setPersonalRules(context, rules)
                onRulesChanged()
            },
            onClear = {
                ProviderManager.setPersonalRules(context, "")
                onRulesChanged()
            },
            onDismiss = { showPersonalDialog = false }
        )
    }

    if (showProjectDialog) {
        val currentProjectRules = ProviderManager.getProjectRules(context, wsPath)
        RulesEditorDialog(
            title = "项目 Rules",
            description = "当前项目: ${wsPath.substringAfterLast("/")}\n仅在此工作区下生效。每行一条规则。",
            placeholder = "每行一条规则，例如：\n使用 Kotlin 协程而非 RxJava\n遵循 MVVM 架构模式",
            initialValue = currentProjectRules,
            onSave = { rules ->
                ProviderManager.setProjectRules(context, wsPath, rules)
                onRulesChanged()
            },
            onClear = {
                ProviderManager.setProjectRules(context, wsPath, "")
                onRulesChanged()
            },
            onDismiss = { showProjectDialog = false }
        )
    }

    if (showNoWorkspaceDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showNoWorkspaceDialog = false },
            title = { CustomText("项目 Rules", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = { CustomText("请先在 Agent 模式下设置工作区路径，然后才能配置项目 Rules。", fontSize = 14.sp) },
            confirmButton = {
                CustomTextButton(onClick = { showNoWorkspaceDialog = false }) {
                    CustomText("确定", color = Accent)
                }
            }
        )
    }
}

@Composable
fun RulesEditorDialog(
    title: String,
    description: String,
    placeholder: String,
    initialValue: String,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { CustomText(title, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CustomText(description, fontSize = 13.sp, color = TextSecondary)
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { CustomText(placeholder, fontSize = 13.sp, color = TextSecondary) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp),
                    minLines = 5,
                    maxLines = 12
                )
            }
        },
        confirmButton = {
            CustomTextButton(onClick = {
                onSave(text.trim())
                onDismiss()
            }) {
                CustomText("保存", color = Accent)
            }
        },
        dismissButton = {
            Row {
                CustomTextButton(onClick = {
                    onClear()
                    onDismiss()
                }) {
                    CustomText("清空", color = TextSecondary)
                }
                CustomTextButton(onClick = onDismiss) {
                    CustomText("取消", color = TextSecondary)
                }
            }
        }
    )
}
