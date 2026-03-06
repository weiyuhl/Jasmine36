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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.prompt.llm.CompressionStrategyType
import com.lhzkml.jasmine.ui.theme.*
import com.lhzkml.jasmine.ui.components.*

class CompressionConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JasmineTheme {
                CompressionConfigScreen(
                    onBack = { finish() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        saveConfig()
    }

    private fun saveConfig() {
        // 保存逻辑在 Composable 中处理
    }
}

@Composable
fun CompressionConfigScreen(onBack: () -> Unit) {
    val config = AppConfig.configRepo()
    
    var enabled by remember { mutableStateOf(config.isCompressionEnabled()) }
    var selectedStrategy by remember { mutableStateOf(config.getCompressionStrategy()) }
    
    var maxTokens by remember { 
        val value = config.getCompressionMaxTokens()
        mutableStateOf(if (value > 0) value.toString() else "")
    }
    var threshold by remember { mutableStateOf(config.getCompressionThreshold().toString()) }
    var lastN by remember { mutableStateOf(config.getCompressionLastN().toString()) }
    var chunkSize by remember { mutableStateOf(config.getCompressionChunkSize().toString()) }
    var keepRecentRounds by remember { mutableStateOf(config.getCompressionKeepRecentRounds().toString()) }

    DisposableEffect(Unit) {
        onDispose {
            config.setCompressionEnabled(enabled)
            config.setCompressionStrategy(selectedStrategy)
            
            when (selectedStrategy) {
                CompressionStrategyType.TOKEN_BUDGET -> {
                    val maxTokensValue = maxTokens.trim().toIntOrNull() ?: 0
                    val thresholdValue = (threshold.trim().toIntOrNull() ?: 75).coerceIn(1, 99)
                    config.setCompressionMaxTokens(maxTokensValue)
                    config.setCompressionThreshold(thresholdValue)
                }
                CompressionStrategyType.LAST_N -> {
                    val n = (lastN.trim().toIntOrNull() ?: 10).coerceAtLeast(2)
                    config.setCompressionLastN(n)
                }
                CompressionStrategyType.CHUNKED -> {
                    val size = (chunkSize.trim().toIntOrNull() ?: 20).coerceAtLeast(5)
                    config.setCompressionChunkSize(size)
                }
                CompressionStrategyType.PROGRESSIVE -> {
                    val maxTokensValue = maxTokens.trim().toIntOrNull() ?: 0
                    val thresholdValue = (threshold.trim().toIntOrNull() ?: 75).coerceIn(1, 99)
                    val rounds = (keepRecentRounds.trim().toIntOrNull() ?: 4).coerceAtLeast(1)
                    config.setCompressionMaxTokens(maxTokensValue)
                    config.setCompressionThreshold(thresholdValue)
                    config.setCompressionKeepRecentRounds(rounds)
                }
                CompressionStrategyType.WHOLE_HISTORY -> { }
            }
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
                text = "压缩配置",
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
                        text = "启用智能上下文压缩",
                        fontSize = 15.sp,
                        color = TextPrimary
                    )
                    CustomText(
                        text = "对话过长时自动压缩历史",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                
                CustomSwitch(
                    checked = enabled,
                    onCheckedChange = { 
                        enabled = it
                        config.setCompressionEnabled(it)
                    },
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Accent,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFFE0E0E0)
                )
            }

            if (enabled) {
                Spacer(modifier = Modifier.height(16.dp))

                // 策略选择标题
                CustomText(
                    text = "压缩策略",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Progressive 策略（推荐）
                StrategyCard(
                    title = "渐进式压缩（推荐）",
                    description = "保留近期对话原文，仅摘要旧历史，增量式不重复",
                    isSelected = selectedStrategy == CompressionStrategyType.PROGRESSIVE,
                    onClick = {
                        selectedStrategy = CompressionStrategyType.PROGRESSIVE
                        config.setCompressionStrategy(CompressionStrategyType.PROGRESSIVE)
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Token Budget 策略
                StrategyCard(
                    title = "Token 预算",
                    description = "超过阈值全量压缩",
                    isSelected = selectedStrategy == CompressionStrategyType.TOKEN_BUDGET,
                    onClick = { 
                        selectedStrategy = CompressionStrategyType.TOKEN_BUDGET
                        config.setCompressionStrategy(CompressionStrategyType.TOKEN_BUDGET)
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Whole History 策略
                StrategyCard(
                    title = "整体压缩",
                    description = "整个历史生成摘要",
                    isSelected = selectedStrategy == CompressionStrategyType.WHOLE_HISTORY,
                    onClick = { 
                        selectedStrategy = CompressionStrategyType.WHOLE_HISTORY
                        config.setCompressionStrategy(CompressionStrategyType.WHOLE_HISTORY)
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Last N 策略
                StrategyCard(
                    title = "保留最后 N 条",
                    description = "只压缩最近消息",
                    isSelected = selectedStrategy == CompressionStrategyType.LAST_N,
                    onClick = { 
                        selectedStrategy = CompressionStrategyType.LAST_N
                        config.setCompressionStrategy(CompressionStrategyType.LAST_N)
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Chunked 策略
                StrategyCard(
                    title = "分块压缩",
                    description = "按固定大小分块",
                    isSelected = selectedStrategy == CompressionStrategyType.CHUNKED,
                    onClick = { 
                        selectedStrategy = CompressionStrategyType.CHUNKED
                        config.setCompressionStrategy(CompressionStrategyType.CHUNKED)
                    }
                )

                // 参数配置区域
                when (selectedStrategy) {
                    CompressionStrategyType.TOKEN_BUDGET -> {
                        Spacer(modifier = Modifier.height(16.dp))
                        CustomHorizontalDivider(color = Color(0xFFE8E8E8), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        ParamsSection(title = "Token 预算参数") {
                            ParamInputField(
                                label = "最大 Token 数（0 = 跟随模型上下文窗口）",
                                value = maxTokens,
                                onValueChange = { maxTokens = it },
                                placeholder = "0"
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            ParamInputField(
                                label = "触发阈值 %（1~99）",
                                value = threshold,
                                onValueChange = { threshold = it },
                                placeholder = "75"
                            )
                        }
                    }
                    CompressionStrategyType.LAST_N -> {
                        Spacer(modifier = Modifier.height(16.dp))
                        CustomHorizontalDivider(color = Color(0xFFE8E8E8), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        ParamsSection(title = "保留最后 N 条参数") {
                            ParamInputField(
                                label = "保留的消息数",
                                value = lastN,
                                onValueChange = { lastN = it },
                                placeholder = "10"
                            )
                        }
                    }
                    CompressionStrategyType.CHUNKED -> {
                        Spacer(modifier = Modifier.height(16.dp))
                        CustomHorizontalDivider(color = Color(0xFFE8E8E8), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        ParamsSection(title = "分块压缩参数") {
                            ParamInputField(
                                label = "每块消息数",
                                value = chunkSize,
                                onValueChange = { chunkSize = it },
                                placeholder = "20"
                            )
                        }
                    }
                    CompressionStrategyType.PROGRESSIVE -> {
                        Spacer(modifier = Modifier.height(16.dp))
                        CustomHorizontalDivider(color = Color(0xFFE8E8E8), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        ParamsSection(title = "渐进式压缩参数") {
                            ParamInputField(
                                label = "保留最近几轮对话（1轮=1问1答）",
                                value = keepRecentRounds,
                                onValueChange = { keepRecentRounds = it },
                                placeholder = "4"
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            ParamInputField(
                                label = "最大 Token 数（0 = 跟随模型上下文窗口）",
                                value = maxTokens,
                                onValueChange = { maxTokens = it },
                                placeholder = "0"
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            ParamInputField(
                                label = "触发阈值 %（1~99）",
                                value = threshold,
                                onValueChange = { threshold = it },
                                placeholder = "75"
                            )
                        }
                    }
                    CompressionStrategyType.WHOLE_HISTORY -> { }
                }
            }
        }
    }
}

@Composable
fun StrategyCard(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) Color(0xFFF5F5F5) else Color.White
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Accent else Color(0xFFE8E8E8),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            CustomText(
                text = title,
                fontSize = 15.sp,
                color = TextPrimary
            )
            CustomText(
                text = description,
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        
        if (isSelected) {
            CustomText(
                text = "✓",
                fontSize = 16.sp,
                color = Color(0xFF2196F3)
            )
        }
    }
}

@Composable
fun ParamsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        CustomText(
            text = title,
            fontSize = 15.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        content()
    }
}

@Composable
fun ParamInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    Column {
        CustomText(
            text = label,
            fontSize = 13.sp,
            color = TextSecondary
        )
        
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                fontSize = 14.sp,
                color = TextPrimary
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .background(BgInput, RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(8.dp))
                .padding(12.dp),
            decorationBox = { innerTextField ->
                if (value.isEmpty()) {
                    CustomText(
                        text = placeholder,
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                }
                innerTextField()
            }
        )
    }
}