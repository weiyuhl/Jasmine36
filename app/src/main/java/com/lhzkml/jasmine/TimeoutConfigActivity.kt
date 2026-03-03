package com.lhzkml.jasmine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.ui.theme.*

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
            TextButton(
                onClick = onBack,
                colors = ButtonDefaults.textButtonColors(contentColor = TextPrimary),
                contentPadding = PaddingValues(6.dp)
            ) {
                Text("<- 返回", fontSize = 14.sp, color = TextPrimary)
            }
            
            Text(
                text = "超时与续传",
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
            // 超时设置区域
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "超时设置",
                    fontSize = 15.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = "0 或留空表示使用默认值",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                // 请求超时
                Text(
                    text = "请求超时（秒）· 默认 600",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                OutlinedTextField(
                    value = requestTimeout,
                    onValueChange = { requestTimeout = it },
                    placeholder = { Text("0", fontSize = 14.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Color(0xFFE0E0E0)
                    )
                )
                
                // Socket 读取超时
                Text(
                    text = "Socket 读取超时（秒）· 默认 300",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = socketTimeout,
                    onValueChange = { socketTimeout = it },
                    placeholder = { Text("0", fontSize = 14.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Color(0xFFE0E0E0)
                    )
                )
                
                // 连接超时
                Text(
                    text = "连接超时（秒）· 默认 30",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = connectTimeout,
                    onValueChange = { connectTimeout = it },
                    placeholder = { Text("0", fontSize = 14.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Color(0xFFE0E0E0)
                    )
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
                Text(
                    text = "流式超时续传",
                    fontSize = 15.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
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
                    Text(
                        text = "启用续传",
                        fontSize = 14.sp,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Switch(
                        checked = resumeEnabled,
                        onCheckedChange = { 
                            resumeEnabled = it
                            config.setStreamResumeEnabled(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Accent,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFFE0E0E0)
                        )
                    )
                }
                
                if (resumeEnabled) {
                    Text(
                        text = "最大续传次数（1~10）",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    OutlinedTextField(
                        value = maxRetries,
                        onValueChange = { maxRetries = it },
                        placeholder = { Text("3", fontSize = 14.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = Color(0xFFE0E0E0)
                        )
                    )
                }
            }
        }
    }
}
