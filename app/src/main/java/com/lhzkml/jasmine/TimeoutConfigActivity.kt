package com.lhzkml.jasmine

import android.os.Bundle
import com.lhzkml.jasmine.config.AppConfig
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.ui.theme.*
import com.lhzkml.jasmine.ui.components.*

class TimeoutConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JasmineTheme {
                TimeoutConfigScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
fun TimeoutConfigScreen(onBack: () -> Unit) {
    val config = AppConfig.configRepo()
    
    var requestTimeout by remember { 
        val value = config.getRequestTimeout()
        mutableStateOf(if (value > 0) value.toString() else "")
    }
    var socketTimeout by remember { 
        val value = config.getSocketTimeout()
        mutableStateOf(if (value > 0) value.toString() else "")
    }
    var connectTimeout by remember { 
        val value = config.getConnectTimeout()
        mutableStateOf(if (value > 0) value.toString() else "")
    }
    var resumeEnabled by remember { mutableStateOf(config.isStreamResumeEnabled()) }
    var maxRetries by remember { mutableStateOf(config.getStreamResumeMaxRetries().toString()) }
    
    DisposableEffect(Unit) {
        onDispose {
            val reqTimeout = requestTimeout.trim().toIntOrNull() ?: 0
            val sockTimeout = socketTimeout.trim().toIntOrNull() ?: 0
            val connTimeout = connectTimeout.trim().toIntOrNull() ?: 0
            val retries = (maxRetries.trim().toIntOrNull() ?: 3).coerceIn(1, 10)
            
            config.setRequestTimeout(reqTimeout)
            config.setSocketTimeout(sockTimeout)
            config.setConnectTimeout(connTimeout)
            config.setStreamResumeMaxRetries(retries)
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
                contentColor = TextPrimary,
                contentPadding = PaddingValues(6.dp)
            ) {
                CustomText("<- 返回", fontSize = 14.sp, color = TextPrimary)
            }
            
            CustomText(
                text = "超时与续传",
                fontSize = 17.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            
            Spacer(modifier = Modifier.width(56.dp))
        }

        CustomHorizontalDivider(color = Color(0xFFE8E8E8), thickness = 1.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // 超时设置区域
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                CustomText(
                    text = "超时设置",
                    fontSize = 15.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                
                CustomText(
                    text = "0 或留空表示使用默认值",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                // 请求超时
                CustomText(
                    text = "请求超时（秒）· 默认 600",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                TimeoutInputField(
                    value = requestTimeout,
                    onValueChange = { requestTimeout = it },
                    placeholder = "0"
                )
                
                // Socket 读取超时
                CustomText(
                    text = "Socket 读取超时（秒）· 默认 300",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
                TimeoutInputField(
                    value = socketTimeout,
                    onValueChange = { socketTimeout = it },
                    placeholder = "0"
                )
                
                // 连接超时
                CustomText(
                    text = "连接超时（秒）· 默认 30",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
                TimeoutInputField(
                    value = connectTimeout,
                    onValueChange = { connectTimeout = it },
                    placeholder = "0"
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 流式续传设置
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                CustomText(
                    text = "流式超时续传",
                    fontSize = 15.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                
                CustomText(
                    text = "流式输出因超时中断时自动从断点续传",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CustomText(
                        text = "启用续传",
                        fontSize = 14.sp,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    
                    CustomSwitch(
                        checked = resumeEnabled,
                        onCheckedChange = { 
                            resumeEnabled = it
                            config.setStreamResumeEnabled(it)
                        },
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Accent,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFE0E0E0)
                    )
                }
                
                if (resumeEnabled) {
                    CustomText(
                        text = "最大续传次数（1~10）",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    TimeoutInputField(
                        value = maxRetries,
                        onValueChange = { maxRetries = it },
                        placeholder = "3"
                    )
                }
            }
        }
    }
}

@Composable
fun TimeoutInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Color.White, RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 14.sp,
                color = TextPrimary
            ),
            cursorBrush = SolidColor(TextPrimary),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                if (value.isEmpty()) {
                    CustomText(
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
