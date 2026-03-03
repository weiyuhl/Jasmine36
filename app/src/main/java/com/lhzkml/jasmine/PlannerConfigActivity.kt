package com.lhzkml.jasmine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.ui.theme.*

class PlannerConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JasmineTheme {
                PlannerConfigScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
fun PlannerConfigScreen(onBack: () -> Unit) {
    val config = AppConfig.configRepo()
    
    var enabled by remember { mutableStateOf(config.isPlannerEnabled()) }
    var maxIterations by remember { mutableStateOf(config.getPlannerMaxIterations().toString()) }
    var criticEnabled by remember { mutableStateOf(config.isPlannerCriticEnabled()) }

    fun getSummary(): String {
        val maxIter = maxIterations.toIntOrNull() ?: 1
        val critic = if (criticEnabled) "Critic 评估" else "无 Critic"
        return "迭代 $maxIter 次 · $critic"
    }

    DisposableEffect(Unit) {
        onDispose {
            config.setPlannerEnabled(enabled)
            val maxIter = (maxIterations.trim().toIntOrNull() ?: 1).coerceIn(1, 20)
            config.setPlannerMaxIterations(maxIter)
            config.setPlannerCriticEnabled(criticEnabled)
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
                Text("<- 返回", fontSize = 14.sp, color = TextPrimary)
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Text(
                text = "规划配置",
                fontSize = 17.sp,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
                style = androidx.compose.ui.text.font.FontWeight.Bold.let {
                    TextStyle(fontWeight = it, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            )
            
            Spacer(modifier = Modifier.weight(1f))
        }

        HorizontalDivider(color = Color(0xFFE8E8E8), thickness = 1.dp)

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
                    Text(
                        text = "启用任务规划",
                        fontSize = 15.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "Agent 模式下先规划再执行",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                
                Switch(
                    checked = enabled,
                    onCheckedChange = { 
                        enabled = it
                        config.setPlannerEnabled(it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Accent,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFE0E0E0)
                    )
                )
            }

            if (enabled) {
                Spacer(modifier = Modifier.height(16.dp))

                // 最大迭代次数
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "最大迭代次数",
                        fontSize = 15.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "规划器每次迭代让 LLM 生成/更新计划，次数越多计划越精细但消耗更多 token（1~20）",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    
                    BasicTextField(
                        value = maxIterations,
                        onValueChange = { 
                            maxIterations = it
                            val v = it.trim().toIntOrNull()
                            if (v != null) {
                                config.setPlannerMaxIterations(v.coerceIn(1, 20))
                            }
                        },
                        textStyle = TextStyle(
                            fontSize = 14.sp,
                            color = TextPrimary
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .background(BgInput, RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        decorationBox = { innerTextField ->
                            if (maxIterations.isEmpty()) {
                                Text(
                                    text = "默认 1",
                                    fontSize = 14.sp,
                                    color = TextSecondary
                                )
                            }
                            innerTextField()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Critic 评估
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Critic 评估",
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "额外调用 LLM 评估计划质量，不合格时自动重新规划",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    
                    Switch(
                        checked = criticEnabled,
                        onCheckedChange = { 
                            criticEnabled = it
                            config.setPlannerCriticEnabled(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Accent,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFFE0E0E0)
                        )
                    )
                }

                // 摘要
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "当前配置",
                        fontSize = 15.sp,
                        color = TextPrimary,
                        style = androidx.compose.ui.text.font.FontWeight.Bold.let {
                            TextStyle(fontWeight = it)
                        }
                    )
                    Text(
                        text = getSummary(),
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
