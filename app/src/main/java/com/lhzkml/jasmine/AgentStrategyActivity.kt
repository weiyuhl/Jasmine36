package com.lhzkml.jasmine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.config.AgentStrategyType
import com.lhzkml.jasmine.core.config.GraphToolCallMode
import com.lhzkml.jasmine.core.config.ToolSelectionStrategyType
import com.lhzkml.jasmine.core.config.ToolChoiceMode
import com.lhzkml.jasmine.ui.theme.BgInput
import com.lhzkml.jasmine.ui.theme.BgPrimary
import com.lhzkml.jasmine.ui.theme.TextPrimary
import com.lhzkml.jasmine.ui.theme.TextSecondary
import com.lhzkml.jasmine.ui.components.*

/**
 * Agent 策略选择界面
 * 展示可选策略卡片 + 图策略子选项（工具调用模式、工具选择策略、ToolChoice）。
 */
class AgentStrategyActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AgentStrategyScreen(
                onBack = { finish() }
            )
        }
    }
}


@Composable
fun AgentStrategyScreen(onBack: () -> Unit) {
    val config = AppConfig.configRepo()
    
    var selectedStrategy by remember { mutableStateOf(config.getAgentStrategy()) }
    var toolCallMode by remember { mutableStateOf(config.getGraphToolCallMode()) }
    var toolSelectionStrategy by remember { mutableStateOf(config.getToolSelectionStrategy()) }
    var toolChoiceMode by remember { mutableStateOf(config.getToolChoiceMode()) }
    
    var byNameTools by remember { mutableStateOf(config.getToolSelectionNames().joinToString(",")) }
    var autoTaskDesc by remember { mutableStateOf(config.getToolSelectionTaskDesc()) }
    var namedTool by remember { mutableStateOf(config.getToolChoiceNamedTool()) }
    
    DisposableEffect(Unit) {
        onDispose {
            // 保存配置
            val byNameText = byNameTools.trim()
            if (byNameText.isNotEmpty()) {
                config.setToolSelectionNames(byNameText.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet())
            } else {
                config.setToolSelectionNames(emptySet())
            }
            config.setToolSelectionTaskDesc(autoTaskDesc.trim())
            config.setToolChoiceNamedTool(namedTool.trim())
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
                onClick = onBack
            ) {
                CustomText("← 返回", fontSize = 14.sp)
            }
            
            CustomText(
                text = "Agent 策略",
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
            // 策略选择：简单循环
            StrategyCardWithDiagram(
                icon = "[Loop]",
                title = "简单循环",
                subtitle = "ToolExecutor while 循环，简单高效",
                isSelected = selectedStrategy == AgentStrategyType.SIMPLE_LOOP,
                onClick = {
                    selectedStrategy = AgentStrategyType.SIMPLE_LOOP
                    config.setAgentStrategy(AgentStrategyType.SIMPLE_LOOP)
                },
                showDiagram = true,
                diagramContent = {
                    SimpleLoopDiagram()
                }
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 策略选择：图策略
            StrategyCardWithDiagram(
                icon = "[Graph]",
                title = "图策略",
                subtitle = "GraphAgent 节点图执行，参考 koog",
                isSelected = selectedStrategy == AgentStrategyType.SINGLE_RUN_GRAPH,
                onClick = {
                    selectedStrategy = AgentStrategyType.SINGLE_RUN_GRAPH
                    config.setAgentStrategy(AgentStrategyType.SINGLE_RUN_GRAPH)
                },
                showDiagram = true,
                diagramContent = {
                    GraphStrategyDiagram()
                }
            )
            
            CustomHorizontalDivider(
                color = Color(0xFFE0E0E0),
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            
            // 策略说明区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(8.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    CustomText(
                        text = "策略说明",
                        fontSize = 15.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    CustomText(
                        text = if (selectedStrategy == AgentStrategyType.SIMPLE_LOOP) {
                            "简单循环模式使用 ToolExecutor 的 while 循环执行工具调用。\n\n" +
                            "流程：发送消息 -> LLM 回复 -> 检查工具调用 -> 执行工具 -> 循环直到无工具调用。\n\n" +
                            "适合简单场景，开销小，易于理解和调试。"
                        } else {
                            "图策略模式使用 GraphAgent 按节点图执行。\n\n" +
                            "移植自 koog 的 singleRunStrategy，支持条件分支、子图嵌套、复杂工具调用流程。\n\n" +
                            "可配置工具调用模式（Sequential/Parallel/Single）、工具选择策略（All/None/ByName/Auto）和 ToolChoice（Default/Auto/Required/None/Named）。"
                        },
                        fontSize = 13.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                }
            }
            
            // 图策略子选项
            if (selectedStrategy == AgentStrategyType.SINGLE_RUN_GRAPH) {
                Spacer(modifier = Modifier.height(8.dp))
                
                // 工具调用模式
                CustomText("工具调用模式", fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                CustomText(
                    "移植自 koog ToolCalls，控制多工具调用的执行方式",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                OptionCard(
                    title = "SEQUENTIAL",
                    subtitle = "多工具顺序执行（默认）",
                    isSelected = toolCallMode == GraphToolCallMode.SEQUENTIAL,
                    onClick = {
                        toolCallMode = GraphToolCallMode.SEQUENTIAL
                        config.setGraphToolCallMode(GraphToolCallMode.SEQUENTIAL)
                    }
                )
                
                OptionCard(
                    title = "PARALLEL",
                    subtitle = "多工具并行执行",
                    isSelected = toolCallMode == GraphToolCallMode.PARALLEL,
                    onClick = {
                        toolCallMode = GraphToolCallMode.PARALLEL
                        config.setGraphToolCallMode(GraphToolCallMode.PARALLEL)
                    }
                )
                
                OptionCard(
                    title = "SINGLE_RUN_SEQUENTIAL",
                    subtitle = "单工具顺序执行",
                    isSelected = toolCallMode == GraphToolCallMode.SINGLE_RUN_SEQUENTIAL,
                    onClick = {
                        toolCallMode = GraphToolCallMode.SINGLE_RUN_SEQUENTIAL
                        config.setGraphToolCallMode(GraphToolCallMode.SINGLE_RUN_SEQUENTIAL)
                    }
                )
                
                CustomHorizontalDivider(
                    color = Color(0xFFE0E0E0),
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                
                // 工具选择策略
                CustomText("工具选择策略", fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                CustomText(
                    "移植自 koog ToolSelectionStrategy，决定子图可用的工具集合",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                OptionCard(
                    title = "ALL",
                    subtitle = "使用所有可用工具（默认）",
                    isSelected = toolSelectionStrategy == ToolSelectionStrategyType.ALL,
                    onClick = {
                        toolSelectionStrategy = ToolSelectionStrategyType.ALL
                        config.setToolSelectionStrategy(ToolSelectionStrategyType.ALL)
                    }
                )
                
                OptionCard(
                    title = "NONE",
                    subtitle = "不使用任何工具",
                    isSelected = toolSelectionStrategy == ToolSelectionStrategyType.NONE,
                    onClick = {
                        toolSelectionStrategy = ToolSelectionStrategyType.NONE
                        config.setToolSelectionStrategy(ToolSelectionStrategyType.NONE)
                    }
                )
                
                OptionCard(
                    title = "BY_NAME",
                    subtitle = "按名称过滤工具",
                    isSelected = toolSelectionStrategy == ToolSelectionStrategyType.BY_NAME,
                    onClick = {
                        toolSelectionStrategy = ToolSelectionStrategyType.BY_NAME
                        config.setToolSelectionStrategy(ToolSelectionStrategyType.BY_NAME)
                    }
                )
                
                if (toolSelectionStrategy == ToolSelectionStrategyType.BY_NAME) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                        CustomText(
                            "工具名称（逗号分隔）",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BgInput, RoundedCornerShape(4.dp))
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                        ) {
                            BasicTextField(
                                value = byNameTools,
                                onValueChange = { byNameTools = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 13.sp,
                                    color = TextPrimary
                                ),
                                cursorBrush = SolidColor(TextPrimary),
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { innerTextField ->
                                    if (byNameTools.isEmpty()) {
                                        CustomText(
                                            "read_file,write_file,shell",
                                            fontSize = 13.sp,
                                            color = TextSecondary
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                
                OptionCard(
                    title = "AUTO_SELECT_FOR_TASK",
                    subtitle = "LLM 根据子任务描述自动选择工具",
                    isSelected = toolSelectionStrategy == ToolSelectionStrategyType.AUTO_SELECT_FOR_TASK,
                    onClick = {
                        toolSelectionStrategy = ToolSelectionStrategyType.AUTO_SELECT_FOR_TASK
                        config.setToolSelectionStrategy(ToolSelectionStrategyType.AUTO_SELECT_FOR_TASK)
                    }
                )
                
                if (toolSelectionStrategy == ToolSelectionStrategyType.AUTO_SELECT_FOR_TASK) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                        CustomText(
                            "子任务描述",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BgInput, RoundedCornerShape(4.dp))
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                        ) {
                            BasicTextField(
                                value = autoTaskDesc,
                                onValueChange = { autoTaskDesc = it },
                                textStyle = TextStyle(
                                    fontSize = 13.sp,
                                    color = TextPrimary
                                ),
                                cursorBrush = SolidColor(TextPrimary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 60.dp),
                                decorationBox = { innerTextField ->
                                    if (autoTaskDesc.isEmpty()) {
                                        CustomText(
                                            "Read and analyze source code files",
                                            fontSize = 13.sp,
                                            color = TextSecondary
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                
                CustomHorizontalDivider(
                    color = Color(0xFFE0E0E0),
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                
                // ToolChoice
                CustomText("ToolChoice", fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                CustomText(
                    "移植自 koog ToolChoice，控制 LLM 是否/如何调用工具",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                OptionCard(
                    title = "DEFAULT",
                    subtitle = "不指定，由模型决定（默认）",
                    isSelected = toolChoiceMode == ToolChoiceMode.DEFAULT,
                    onClick = {
                        toolChoiceMode = ToolChoiceMode.DEFAULT
                        config.setToolChoiceMode(ToolChoiceMode.DEFAULT)
                    }
                )
                
                OptionCard(
                    title = "AUTO",
                    subtitle = "LLM 自动决定是否调用工具",
                    isSelected = toolChoiceMode == ToolChoiceMode.AUTO,
                    onClick = {
                        toolChoiceMode = ToolChoiceMode.AUTO
                        config.setToolChoiceMode(ToolChoiceMode.AUTO)
                    }
                )
                
                OptionCard(
                    title = "REQUIRED",
                    subtitle = "强制 LLM 调用工具",
                    isSelected = toolChoiceMode == ToolChoiceMode.REQUIRED,
                    onClick = {
                        toolChoiceMode = ToolChoiceMode.REQUIRED
                        config.setToolChoiceMode(ToolChoiceMode.REQUIRED)
                    }
                )
                
                OptionCard(
                    title = "NONE",
                    subtitle = "禁止 LLM 调用工具",
                    isSelected = toolChoiceMode == ToolChoiceMode.NONE,
                    onClick = {
                        toolChoiceMode = ToolChoiceMode.NONE
                        config.setToolChoiceMode(ToolChoiceMode.NONE)
                    }
                )
                
                OptionCard(
                    title = "NAMED",
                    subtitle = "强制使用指定工具",
                    isSelected = toolChoiceMode == ToolChoiceMode.NAMED,
                    onClick = {
                        toolChoiceMode = ToolChoiceMode.NAMED
                        config.setToolChoiceMode(ToolChoiceMode.NAMED)
                    }
                )
                
                if (toolChoiceMode == ToolChoiceMode.NAMED) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                        CustomText(
                            "工具名称",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BgInput, RoundedCornerShape(4.dp))
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                        ) {
                            BasicTextField(
                                value = namedTool,
                                onValueChange = { namedTool = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 13.sp,
                                    color = TextPrimary
                                ),
                                cursorBrush = SolidColor(TextPrimary),
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { innerTextField ->
                                    if (namedTool.isEmpty()) {
                                        CustomText(
                                            "calculator",
                                            fontSize = 13.sp,
                                            color = TextSecondary
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StrategyCardWithDiagram(
    icon: String,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    showDiagram: Boolean = false,
    diagramContent: @Composable () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(8.dp))
            .then(
                if (isSelected) Modifier.border(2.dp, TextPrimary, RoundedCornerShape(8.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                CustomText(
                    text = icon,
                    fontSize = 22.sp,
                    color = TextPrimary
                )
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    CustomText(
                        text = title,
                        fontSize = 16.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    CustomText(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                
                if (isSelected) {
                    CustomText(
                        text = "✓",
                        fontSize = 18.sp,
                        color = Color(0xFF2196F3),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            if (showDiagram) {
                Spacer(modifier = Modifier.height(12.dp))
                diagramContent()
            }
        }
    }
}

@Composable
fun SimpleLoopDiagram() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DiagramNode("[Send] 发送")
            DiagramArrow()
            DiagramNode("[LLM]")
            DiagramArrow()
            DiagramNode("[Tool] 工具?")
            DiagramArrow()
            DiagramNode("[OK] 结果")
        }
        
        CustomText(
            text = "<- 有工具调用时循环",
            fontSize = 10.sp,
            color = TextSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun GraphStrategyDiagram() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DiagramNode("[>] Start", modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        
        CustomText("↓", fontSize = 14.sp, color = TextSecondary)
        
        DiagramNode("[LLM] nodeLLMRequest", modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(top = 2.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(end = 16.dp)
            ) {
                CustomText("↙ tool_calls", fontSize = 10.sp, color = Color(0xFF2196F3))
                DiagramNode("[Tool] nodeExecuteTool", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 11.sp)
                CustomText("↓", fontSize = 12.sp, color = TextSecondary)
                DiagramNode("[Result] nodeSendToolResult", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 11.sp)
                CustomText("↑ 循环", fontSize = 10.sp, color = Color(0xFFFF9800))
            }
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(start = 16.dp)
            ) {
                CustomText("↘ assistant", fontSize = 10.sp, color = Color(0xFF4CAF50))
                CustomText("↓", fontSize = 12.sp, color = TextSecondary)
            }
        }
        
        DiagramNode("[x] Finish", modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).padding(top = 2.dp))
    }
}

@Composable
fun DiagramNode(
    text: String,
    modifier: Modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    fontSize: TextUnit = 11.sp
) {
    CustomText(
        text = text,
        fontSize = fontSize,
        color = TextPrimary,
        modifier = modifier
            .background(Color(0xFFF5F5F5), RoundedCornerShape(4.dp))
    )
}

@Composable
fun DiagramArrow() {
    CustomText(
        text = " → ",
        fontSize = 11.sp,
        color = TextSecondary
    )
}

@Composable
fun OptionCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(4.dp))
            .then(
                if (isSelected) Modifier.border(2.dp, TextPrimary, RoundedCornerShape(4.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                CustomText(
                    text = title,
                    fontSize = 14.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                CustomText(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            
            if (isSelected) {
                CustomText(
                    text = "✓",
                    fontSize = 18.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

