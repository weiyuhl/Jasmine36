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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        conversationRepo = ConversationRepository(this)
        
        setContent {
            SettingsScreen(
                onBack = { finish() },
                conversationRepo = conversationRepo
            )
        }
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
    conversationRepo: ConversationRepository
) {
    val context = LocalContext.current
    var toolsEnabled by remember { mutableStateOf(ProviderManager.isToolsEnabled(context)) }
    var showMaxTokensDialog by remember { mutableStateOf(false) }
    var showSystemPromptDialog by remember { mutableStateOf(false) }
    
    // 状态刷新
    var refreshTrigger by remember { mutableStateOf(0) }
    
    LaunchedEffect(Unit) {
        // 初始加载
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
            // 模型供应商
            SettingsItem(
                title = "模型供应商",
                value = getProviderStatus(context),
                onClick = {
                    context.startActivity(Intent(context, ProviderListActivity::class.java))
                }
            )
            
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
            
            // Agent 策略（仅工具开启时显示）
            if (toolsEnabled) {
                SettingsItem(
                    title = "Agent 策略",
                    value = getAgentStrategyInfo(context),
                    onClick = {
                        context.startActivity(Intent(context, AgentStrategyActivity::class.java))
                    }
                )
            }
            
            // MCP 工具
            SettingsItem(
                title = "MCP 工具",
                value = getMcpInfo(context),
                onClick = {
                    context.startActivity(Intent(context, McpServerActivity::class.java))
                }
            )
            
            // Shell 命令策略
            SettingsItem(
                title = "Shell 命令策略",
                value = getShellPolicyInfo(context),
                onClick = {
                    context.startActivity(Intent(context, ShellPolicyActivity::class.java))
                }
            )
            
            // 智能上下文压缩
            SettingsItem(
                title = "智能上下文压缩",
                value = getCompressionInfo(context),
                onClick = {
                    context.startActivity(Intent(context, CompressionConfigActivity::class.java))
                }
            )
            
            // 执行追踪
            SettingsItem(
                title = "执行追踪",
                value = getTraceInfo(context),
                onClick = {
                    context.startActivity(Intent(context, TraceConfigActivity::class.java))
                }
            )
            
            // 任务规划
            SettingsItem(
                title = "任务规划",
                value = getPlannerInfo(context),
                onClick = {
                    context.startActivity(Intent(context, PlannerConfigActivity::class.java))
                }
            )
            
            // 执行快照
            SettingsItem(
                title = "执行快照",
                value = getSnapshotInfo(context),
                onClick = {
                    context.startActivity(Intent(context, SnapshotConfigActivity::class.java))
                }
            )
            
            // 事件处理器
            SettingsItem(
                title = "事件处理器",
                value = getEventHandlerInfo(context),
                onClick = {
                    context.startActivity(Intent(context, EventHandlerConfigActivity::class.java))
                }
            )
            
            // 超时与续传
            SettingsItem(
                title = "超时与续传",
                value = getTimeoutInfo(context),
                onClick = {
                    context.startActivity(Intent(context, TimeoutConfigActivity::class.java))
                }
            )
            
            // 最大回复 Token
            SettingsItem(
                title = "最大回复 Token",
                subtitle = "限制每条回复的长度",
                value = getMaxTokensInfo(context),
                onClick = { showMaxTokensDialog = true }
            )
            
            // 采样参数
            SamplingParamsCard(refreshTrigger)
            
            // 系统提示词
            SettingsItem(
                title = "系统提示词",
                value = getSystemPromptPreview(context),
                onClick = { showSystemPromptDialog = true }
            )
            
            // Token 用量统计
            TokenUsageCard(conversationRepo)
        }
    }
    
    // 对话框
    if (showMaxTokensDialog) {
        MaxTokensDialog(
            onDismiss = { showMaxTokensDialog = false },
            onSave = { refreshTrigger++ }
        )
    }
    
    if (showSystemPromptDialog) {
        SystemPromptDialog(
            onDismiss = { showSystemPromptDialog = false },
            onSave = { refreshTrigger++ }
        )
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

@Composable
fun SamplingParamsCard(refreshTrigger: Int) {
    val context = LocalContext.current
    val config = ProviderManager.getActiveConfig()
    val supportsTopK = config?.apiType == ApiType.CLAUDE || config?.apiType == ApiType.GEMINI
    
    var temperature by remember(refreshTrigger) { 
        mutableStateOf(ProviderManager.getTemperature(context))
    }
    var topP by remember(refreshTrigger) { 
        mutableStateOf(ProviderManager.getTopP(context))
    }
    var topK by remember(refreshTrigger) { 
        mutableStateOf(ProviderManager.getTopK(context))
    }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = "采样参数",
                fontSize = 15.sp,
                color = TextPrimary
            )
            Text(
                text = "控制 AI 回复的随机性和多样性",
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            // Temperature
            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Temperature", fontSize = 14.sp, color = TextPrimary)
                    Text(
                        text = if (temperature < 0f) "默认" else String.format("%.2f", temperature),
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
                Slider(
                    value = if (temperature < 0f) 0f else temperature,
                    onValueChange = { value ->
                        temperature = if (value == 0f) -1f else value
                        ProviderManager.setTemperature(context, temperature)
                    },
                    valueRange = 0f..2f,
                    steps = 199,
                    modifier = Modifier.padding(top = 4.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = TextPrimary,
                        activeTrackColor = TextPrimary,
                        inactiveTrackColor = TextSecondary.copy(alpha = 0.3f),
                        activeTickColor = TextPrimary,
                        inactiveTickColor = TextSecondary.copy(alpha = 0.3f),
                        disabledThumbColor = TextSecondary,
                        disabledActiveTrackColor = TextSecondary,
                        disabledInactiveTrackColor = TextSecondary.copy(alpha = 0.3f),
                        disabledActiveTickColor = TextSecondary,
                        disabledInactiveTickColor = TextSecondary.copy(alpha = 0.3f)
                    )
                )
                Text(
                    text = "值越高回复越多样，越低越确定 (0~2.0)",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
            
            // Top P
            Column(modifier = Modifier.padding(bottom = if (supportsTopK) 12.dp else 0.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Top P", fontSize = 14.sp, color = TextPrimary)
                    Text(
                        text = if (topP < 0f) "默认" else String.format("%.2f", topP),
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
                Slider(
                    value = if (topP < 0f) 0f else topP,
                    onValueChange = { value ->
                        topP = if (value == 0f) -1f else value
                        ProviderManager.setTopP(context, topP)
                    },
                    valueRange = 0f..1f,
                    steps = 99,
                    modifier = Modifier.padding(top = 4.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = TextPrimary,
                        activeTrackColor = TextPrimary,
                        inactiveTrackColor = TextSecondary.copy(alpha = 0.3f),
                        activeTickColor = TextPrimary,
                        inactiveTickColor = TextSecondary.copy(alpha = 0.3f),
                        disabledThumbColor = TextSecondary,
                        disabledActiveTrackColor = TextSecondary,
                        disabledInactiveTrackColor = TextSecondary.copy(alpha = 0.3f),
                        disabledActiveTickColor = TextSecondary,
                        disabledInactiveTickColor = TextSecondary.copy(alpha = 0.3f)
                    )
                )
                Text(
                    text = "核采样阈值，所有供应商均支持 (0~1.0)",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
            
            // Top K (仅 Claude 和 Gemini)
            if (supportsTopK) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Top K", fontSize = 14.sp, color = TextPrimary)
                        Text(
                            text = if (topK < 0) "默认" else topK.toString(),
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                    Slider(
                        value = if (topK < 0) 0f else topK.toFloat(),
                        onValueChange = { value ->
                            topK = if (value == 0f) -1 else value.toInt()
                            ProviderManager.setTopK(context, topK)
                        },
                        valueRange = 0f..100f,
                        steps = 99,
                        modifier = Modifier.padding(top = 4.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = TextPrimary,
                            activeTrackColor = TextPrimary,
                            inactiveTrackColor = TextSecondary.copy(alpha = 0.3f),
                            activeTickColor = TextPrimary,
                            inactiveTickColor = TextSecondary.copy(alpha = 0.3f),
                            disabledThumbColor = TextSecondary,
                            disabledActiveTrackColor = TextSecondary,
                            disabledInactiveTrackColor = TextSecondary.copy(alpha = 0.3f),
                            disabledActiveTickColor = TextSecondary,
                            disabledInactiveTickColor = TextSecondary.copy(alpha = 0.3f)
                        )
                    )
                    Text(
                        text = "仅 Claude 和 Gemini 支持 (0~100)",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun TokenUsageCard(conversationRepo: ConversationRepository) {
    var promptTokens by remember { mutableStateOf(0) }
    var completionTokens by remember { mutableStateOf(0) }
    var totalTokens by remember { mutableStateOf(0) }
    
    LaunchedEffect(Unit) {
        val stats = withContext(Dispatchers.IO) {
            conversationRepo.getTotalUsage()
        }
        promptTokens = stats.promptTokens
        completionTokens = stats.completionTokens
        totalTokens = stats.totalTokens
    }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Token 用量统计",
                fontSize = 15.sp,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = formatNumber(promptTokens),
                        fontSize = 18.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "提示",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = formatNumber(completionTokens),
                        fontSize = 18.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "回复",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = formatNumber(totalTokens),
                        fontSize = 18.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "总计",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun MaxTokensDialog(onDismiss: () -> Unit, onSave: () -> Unit) {
    val context = LocalContext.current
    var value by remember { mutableStateOf(ProviderManager.getMaxTokens(context).toString()) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary,
        title = { Text("最大回复 Token 数", color = TextPrimary) },
        text = {
            Column {
                Text("设置每条 AI 回复的最大 token 数量，0 或留空表示不限制。常用值：512、1024、2048、4096", color = TextPrimary)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    placeholder = { Text("0 表示不限制", color = TextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = TextPrimary,
                        unfocusedBorderColor = TextSecondary,
                        cursorColor = TextPrimary
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val tokens = value.trim().toIntOrNull() ?: 0
                    ProviderManager.setMaxTokens(context, tokens)
                    onSave()
                    onDismiss()
                },
                colors = ButtonDefaults.textButtonColors(contentColor = TextPrimary)
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
            ) {
                Text("取消")
            }
        }
    )
}

@Composable
fun SystemPromptDialog(onDismiss: () -> Unit, onSave: () -> Unit) {
    val context = LocalContext.current
    var value by remember { mutableStateOf(ProviderManager.getDefaultSystemPrompt(context)) }
    var showPresets by remember { mutableStateOf(false) }
    
    if (showPresets) {
        AlertDialog(
            onDismissRequest = { showPresets = false },
            containerColor = Color.White,
            titleContentColor = TextPrimary,
            title = { Text("选择预设模板", color = TextPrimary) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    SystemPromptManager.presets.forEach { preset ->
                        TextButton(
                            onClick = {
                                value = preset.prompt
                                showPresets = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(contentColor = TextPrimary)
                        ) {
                            Text(preset.name, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showPresets = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = TextPrimary)
                ) {
                    Text("关闭")
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = Color.White,
            titleContentColor = TextPrimary,
            textContentColor = TextPrimary,
            title = { Text("系统提示词", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    maxLines = 8,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = TextPrimary,
                        unfocusedBorderColor = TextSecondary,
                        cursorColor = TextPrimary
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (value.trim().isNotEmpty()) {
                            ProviderManager.setDefaultSystemPrompt(context, value.trim())
                            onSave()
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = TextPrimary)
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = { showPresets = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = TextPrimary)
                    ) {
                        Text("预设模板")
                    }
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
                    ) {
                        Text("取消")
                    }
                }
            }
        )
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

private fun formatNumber(n: Int): String {
    return when {
        n >= 1_000_000 -> String.format("%.1fM tokens", n / 1_000_000.0)
        n >= 1_000 -> String.format("%.1fK tokens", n / 1_000.0)
        else -> "$n tokens"
    }
}
