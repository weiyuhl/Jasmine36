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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.lhzkml.jasmine.core.prompt.llm.SystemPromptManager
import com.lhzkml.jasmine.ui.theme.*
import com.lhzkml.jasmine.ui.components.*

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
            CustomTextButton(
                onClick = onBack,
                contentColor = TextPrimary,
                contentPadding = PaddingValues(6.dp)
            ) {
                CustomText("<- 返回", fontSize = 14.sp, color = TextPrimary)
            }
            
            CustomText(
                text = "系统提示词",
                fontSize = 17.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            
            Spacer(modifier = Modifier.width(56.dp))
        }

        CustomHorizontalDivider(color = Color(0xFFE8E8E8), thickness = 1.dp)

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
                CustomText(
                    text = "系统提示词",
                    fontSize = 15.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                
                CustomText(
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
                CustomTextButton(
                    onClick = { showPresets = true },
                    contentColor = Accent
                ) {
                    CustomText("选择预设模板", fontSize = 14.sp, color = Accent)
                }
            }
        }
    }
    
    // 预设模板对话框
    if (showPresets) {
        CustomAlertDialog(
            onDismissRequest = { showPresets = false },
            containerColor = Color.White,
            titleContentColor = TextPrimary,
            title = { CustomText("选择预设模板", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    SystemPromptManager.presets.forEach { preset ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White)
                                .clickable {
                                    systemPrompt = preset.prompt
                                    showPresets = false
                                }
                        ) {
                            CustomText(
                                text = preset.name,
                                fontSize = 14.sp,
                                color = TextPrimary,
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp)
                            )
                        }
                        if (preset != SystemPromptManager.presets.last()) {
                            CustomHorizontalDivider(color = Color(0xFFE8E8E8), thickness = 1.dp)
                        }
                    }
                }
            },
            confirmButton = {
                CustomTextButton(
                    onClick = { showPresets = false },
                    contentColor = TextPrimary
                ) {
                    CustomText("关闭", color = TextPrimary)
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
            .background(Color.White, RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.TopStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                fontSize = 14.sp,
                color = TextPrimary,
                lineHeight = 20.sp
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
