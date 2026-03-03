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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.lhzkml.jasmine.core.prompt.llm.SystemPromptManager
import com.lhzkml.jasmine.ui.theme.*

class SystemPromptConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JasmineTheme {
                SystemPromptConfigScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
fun SystemPromptConfigScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    
    var systemPrompt by remember { 
        mutableStateOf(ProviderManager.getDefaultSystemPrompt(context))
    }
    var showPresets by remember { mutableStateOf(false) }
    
    DisposableEffect(Unit) {
        onDispose {
            if (systemPrompt.trim().isNotEmpty()) {
                ProviderManager.setDefaultSystemPrompt(context, systemPrompt.trim())
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
            TextButton(
                onClick = onBack,
                colors = ButtonDefaults.textButtonColors(contentColor = TextPrimary),
                contentPadding = PaddingValues(6.dp)
            ) {
                Text("<- 返回", fontSize = 14.sp, color = TextPrimary)
            }
            
            Text(
                text = "系统提示词",
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 系统提示词输入
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "系统提示词",
                    fontSize = 15.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = "设置 AI 的角色和行为规则",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                
                SystemPromptInputField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    placeholder = "输入系统提示词..."
                )
                
                // 预设模板按钮
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick = { showPresets = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = Accent)
                ) {
                    Text("选择预设模板", fontSize = 14.sp)
                }
            }
        }
    }
    
    // 预设模板对话框
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
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    systemPrompt = preset.prompt
                                    showPresets = false
                                },
                            color = Color.White
                        ) {
                            Text(
                                text = preset.name,
                                fontSize = 14.sp,
                                color = TextPrimary,
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp)
                            )
                        }
                        if (preset != SystemPromptManager.presets.last()) {
                            HorizontalDivider(color = Color(0xFFE8E8E8), thickness = 1.dp)
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
    }
}

@Composable
fun SystemPromptInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp)
            .background(Color.White, MaterialTheme.shapes.medium)
            .border(1.dp, Color(0xFFE8E8E8), MaterialTheme.shapes.medium)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.TopStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = LocalTextStyle.current.copy(
                fontSize = 14.sp,
                color = TextPrimary,
                lineHeight = 20.sp
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
