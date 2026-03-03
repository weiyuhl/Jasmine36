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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.lhzkml.jasmine.core.prompt.executor.ApiType
import com.lhzkml.jasmine.ui.theme.*

class SamplingParamsConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JasmineTheme {
                SamplingParamsConfigScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
fun SamplingParamsConfigScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val config = ProviderManager.getActiveConfig()
    val supportsTopK = config?.apiType == ApiType.CLAUDE || config?.apiType == ApiType.GEMINI
    
    var temperature by remember { 
        val value = ProviderManager.getTemperature(context)
        mutableStateOf(if (value < 0f) "" else String.format("%.2f", value))
    }
    var topP by remember { 
        val value = ProviderManager.getTopP(context)
        mutableStateOf(if (value < 0f) "" else String.format("%.2f", value))
    }
    var topK by remember { 
        val value = ProviderManager.getTopK(context)
        mutableStateOf(if (value < 0) "" else value.toString())
    }
    
    DisposableEffect(Unit) {
        onDispose {
            val tempValue = temperature.trim().toFloatOrNull() ?: -1f
            val topPValue = topP.trim().toFloatOrNull() ?: -1f
            val topKValue = topK.trim().toIntOrNull() ?: -1
            
            ProviderManager.setTemperature(context, tempValue)
            ProviderManager.setTopP(context, topPValue)
            ProviderManager.setTopK(context, topKValue)
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
                colors = ButtonDefaults.textButtonColors(contentColor = TextPrimary),
                contentPadding = PaddingValues(6.dp)
            ) {
                Text("<- 返回", fontSize = 14.sp, color = TextPrimary)
            }
            
            Text(
                text = "采样参数",
                fontSize = 17.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            
            Spacer(modifier = Modifier.width(56.dp))
        }

        HorizontalDivider(color = Color(0xFFE8E8E8), thickness = 1.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "采样参数",
                    fontSize = 15.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = "控制 AI 回复的随机性和多样性，留空表示使用默认值",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )
                
                // Temperature
                Text(
                    text = "Temperature",
                    fontSize = 14.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "值越高回复越多样，越低越确定 (0~2.0)",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                )
                SamplingParamInputField(
                    value = temperature,
                    onValueChange = { temperature = it },
                    placeholder = "默认"
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Top P
                Text(
                    text = "Top P",
                    fontSize = 14.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "核采样阈值，所有供应商均支持 (0~1.0)",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                )
                SamplingParamInputField(
                    value = topP,
                    onValueChange = { topP = it },
                    placeholder = "默认"
                )
                
                // Top K (仅 Claude 和 Gemini)
                if (supportsTopK) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Top K",
                        fontSize = 14.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "仅 Claude 和 Gemini 支持 (0~100)",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                    )
                    SamplingParamInputField(
                        value = topK,
                        onValueChange = { topK = it },
                        placeholder = "默认"
                    )
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "注意：Top K 参数仅 Claude 和 Gemini 供应商支持",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun SamplingParamInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Color.White, MaterialTheme.shapes.medium)
            .border(1.dp, Color(0xFFE8E8E8), MaterialTheme.shapes.medium)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = LocalTextStyle.current.copy(
                fontSize = 14.sp,
                color = TextPrimary
            ),
            cursorBrush = SolidColor(TextPrimary),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                }
                innerTextField()
            }
        )
    }
}
