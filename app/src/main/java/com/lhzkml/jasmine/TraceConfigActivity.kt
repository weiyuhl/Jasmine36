package com.lhzkml.jasmine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.agent.observe.trace.TraceEventCategory
import com.lhzkml.jasmine.ui.theme.*
import com.lhzkml.jasmine.ui.components.*

/**
 * 追踪配置界面
 * 追踪系统专注于数据记录（Android Log + 文件），不负责 UI 显示。
 * UI 实时通知由事件处理器(EventHandler)负责。
 */
class TraceConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JasmineTheme {
                TraceConfigScreen(
                    onBack = { finish() },
                    getTraceDir = { getExternalFilesDir("traces")?.absolutePath ?: "未知路径" }
                )
            }
        }
    }
}

@Composable
fun TraceConfigScreen(
    onBack: () -> Unit,
    getTraceDir: () -> String
) {
    val config = AppConfig.configRepo()
    
    var enabled by remember { mutableStateOf(config.isTraceEnabled()) }
    var fileOutputEnabled by remember { mutableStateOf(config.isTraceFileEnabled()) }
    
    val currentFilter = config.getTraceEventFilter()
    var filterAgent by remember { mutableStateOf(currentFilter.isEmpty() || TraceEventCategory.AGENT in currentFilter) }
    var filterLLM by remember { mutableStateOf(currentFilter.isEmpty() || TraceEventCategory.LLM in currentFilter) }
    var filterTool by remember { mutableStateOf(currentFilter.isEmpty() || TraceEventCategory.TOOL in currentFilter) }
    var filterStrategy by remember { mutableStateOf(currentFilter.isEmpty() || TraceEventCategory.STRATEGY in currentFilter) }
    var filterNode by remember { mutableStateOf(currentFilter.isEmpty() || TraceEventCategory.NODE in currentFilter) }
    var filterSubgraph by remember { mutableStateOf(currentFilter.isEmpty() || TraceEventCategory.SUBGRAPH in currentFilter) }
    var filterCompression by remember { mutableStateOf(currentFilter.isEmpty() || TraceEventCategory.COMPRESSION in currentFilter) }

    fun saveEventFilter() {
        val filters = mutableSetOf<TraceEventCategory>()
        if (filterAgent) filters.add(TraceEventCategory.AGENT)
        if (filterLLM) filters.add(TraceEventCategory.LLM)
        if (filterTool) filters.add(TraceEventCategory.TOOL)
        if (filterStrategy) filters.add(TraceEventCategory.STRATEGY)
        if (filterNode) filters.add(TraceEventCategory.NODE)
        if (filterSubgraph) filters.add(TraceEventCategory.SUBGRAPH)
        if (filterCompression) filters.add(TraceEventCategory.COMPRESSION)
        
        val allChecked = filters.size == 7
        val noneChecked = filters.isEmpty()
        
        val selected = if (allChecked || noneChecked) emptySet() else filters
        config.setTraceEventFilter(selected)
    }

    fun getConfigSummary(): String {
        val sb = StringBuilder()
        sb.append("输出: Android Log（默认）")
        if (fileOutputEnabled) sb.append(" + 文件")
        
        sb.append("\n过滤: ")
        val filter = config.getTraceEventFilter()
        if (filter.isEmpty()) {
            sb.append("全部事件")
        } else {
            val names = filter.map { cat ->
                when (cat) {
                    TraceEventCategory.AGENT -> "Agent"
                    TraceEventCategory.LLM -> "LLM"
                    TraceEventCategory.TOOL -> "Tool"
                    TraceEventCategory.STRATEGY -> "Strategy"
                    TraceEventCategory.NODE -> "Node"
                    TraceEventCategory.SUBGRAPH -> "Subgraph"
                    TraceEventCategory.COMPRESSION -> "Compression"
                }
            }
            sb.append(names.joinToString(", "))
        }
        return sb.toString()
    }

    DisposableEffect(Unit) {
        onDispose {
            config.setTraceEnabled(enabled)
            config.setTraceFileEnabled(fileOutputEnabled)
            saveEventFilter()
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
                CustomText("<- 返回", fontSize = 14.sp, color = TextPrimary)
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            CustomText(
                text = "追踪配置",
                fontSize = 17.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            
            Spacer(modifier = Modifier.weight(1f))
        }

        CustomHorizontalDivider(color = Color(0xFFE8E8E8), thickness = 1.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // 启用开关
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    CustomText(
                        text = "启用执行追踪",
                        fontSize = 15.sp,
                        color = TextPrimary
                    )
                    CustomText(
                        text = "记录 Agent 工具调用和 LLM 请求",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                
                CustomSwitch(
                    checked = enabled,
                    onCheckedChange = { 
                        enabled = it
                        config.setTraceEnabled(it)
                    },
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Accent,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFFE0E0E0)
                )
            }

            if (enabled) {
                Spacer(modifier = Modifier.height(16.dp))

                // 输出方式标题
                CustomText(
                    text = "输出方式",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // 角色说明
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(16.dp))
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    CustomText(
                        text = "追踪 vs 事件处理器",
                        fontSize = 15.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    CustomText(
                        text = "追踪(Trace): 纯数据记录，输出到 Android Log 和文件，用于调试和分析。\n事件处理器(EventHandler): UI 通知系统，在聊天界面实时显示 Agent 执行过程。",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 文件输出
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(16.dp))
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        CustomText(
                            text = "文件输出",
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        CustomText(
                            text = if (fileOutputEnabled) "保存到: ${getTraceDir()}" else "保存追踪日志到本地文件",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    
                    CustomSwitch(
                        checked = fileOutputEnabled,
                        onCheckedChange = { 
                            fileOutputEnabled = it
                            config.setTraceFileEnabled(it)
                        },
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Accent,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFE0E0E0)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                CustomHorizontalDivider(color = Color(0xFFE8E8E8), thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                // 事件过滤标题
                CustomText(
                    text = "事件过滤",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                CustomText(
                    text = "选择需要追踪的事件类型，不选则追踪全部",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // 事件类型列表
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    EventFilterItem("Agent 生命周期", filterAgent) { 
                        filterAgent = it
                        saveEventFilter()
                    }
                    CustomHorizontalDivider(color = Color(0xFFE8E8E8), thickness = 1.dp)
                    
                    EventFilterItem("LLM 调用", filterLLM) { 
                        filterLLM = it
                        saveEventFilter()
                    }
                    CustomHorizontalDivider(color = Color(0xFFE8E8E8), thickness = 1.dp)
                    
                    EventFilterItem("工具调用", filterTool) { 
                        filterTool = it
                        saveEventFilter()
                    }
                    CustomHorizontalDivider(color = Color(0xFFE8E8E8), thickness = 1.dp)
                    
                    EventFilterItem("策略执行", filterStrategy) { 
                        filterStrategy = it
                        saveEventFilter()
                    }
                    CustomHorizontalDivider(color = Color(0xFFE8E8E8), thickness = 1.dp)
                    
                    EventFilterItem("节点执行", filterNode) { 
                        filterNode = it
                        saveEventFilter()
                    }
                    CustomHorizontalDivider(color = Color(0xFFE8E8E8), thickness = 1.dp)
                    
                    EventFilterItem("子图执行", filterSubgraph) { 
                        filterSubgraph = it
                        saveEventFilter()
                    }
                    CustomHorizontalDivider(color = Color(0xFFE8E8E8), thickness = 1.dp)
                    
                    EventFilterItem("压缩事件", filterCompression) { 
                        filterCompression = it
                        saveEventFilter()
                    }
                }

                // 当前状态摘要
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    CustomText(
                        text = "当前配置",
                        fontSize = 15.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    CustomText(
                        text = getConfigSummary(),
                        fontSize = 13.sp,
                        color = TextSecondary,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EventFilterItem(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CustomText(
            text = label,
            fontSize = 14.sp,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
        CustomSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            checkedThumbColor = Color.White,
            checkedTrackColor = Accent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = Color(0xFFE0E0E0)
        )
    }
}
