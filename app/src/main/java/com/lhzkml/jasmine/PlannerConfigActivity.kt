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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.repository.PlannerSettingsRepository
import com.lhzkml.jasmine.ui.theme.*
import com.lhzkml.jasmine.ui.components.*
import org.koin.android.ext.android.inject

class PlannerConfigActivity : ComponentActivity() {
    
    private val plannerRepository: PlannerSettingsRepository by inject()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JasmineTheme {
                PlannerConfigScreen(
                    repository = plannerRepository,
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
fun PlannerConfigScreen(
    repository: PlannerSettingsRepository,
    onBack: () -> Unit
) {
    var enabled by remember { mutableStateOf(repository.isPlannerEnabled()) }
    var maxIterations by remember { mutableStateOf(repository.getPlannerMaxIterations().toString()) }
    var criticEnabled by remember { mutableStateOf(repository.isPlannerCriticEnabled()) }

    fun getSummary(): String {
        val maxIter = maxIterations.toIntOrNull() ?: 1
        val critic = if (criticEnabled) "Critic 评估" else "无 Critic"
        return "迭代 $maxIter 次 · $critic"
    }

    DisposableEffect(Unit) {
        onDispose {
            repository.setPlannerEnabled(enabled)
            val maxIter = (maxIterations.trim().toIntOrNull() ?: 1).coerceIn(1, 20)
            repository.setPlannerMaxIterations(maxIter)
            repository.setPlannerCriticEnabled(criticEnabled)
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
                text = "规划配置",
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
                        text = "启用任务规划",
                        fontSize = 15.sp,
                        color = TextPrimary
                    )
                    CustomText(
                        text = "Agent 模式下先规划再执行",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                
                CustomSwitch(
                    checked = enabled,
                    onCheckedChange = { 
                        enabled = it
                        repository.setPlannerEnabled(it)
                    },
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Accent,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFFE0E0E0)
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
                    CustomText(
                        text = "最大迭代次数",
                        fontSize = 15.sp,
                        color = TextPrimary
                    )
                    CustomText(
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
                                repository.setPlannerMaxIterations(v.coerceIn(1, 20))
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
                                CustomText(
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
                        CustomText(
                            text = "Critic 评估",
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        CustomText(
                            text = "额外调用 LLM 评估计划质量，不合格时自动重新规划",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    
                    CustomSwitch(
                        checked = criticEnabled,
                        onCheckedChange = { 
                            criticEnabled = it
                            repository.setPlannerCriticEnabled(it)
                        },
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Accent,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFE0E0E0)
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
                    CustomText(
                        text = "当前配置",
                        fontSize = 15.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    CustomText(
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
