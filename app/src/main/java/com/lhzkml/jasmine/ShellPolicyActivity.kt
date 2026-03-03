package com.lhzkml.jasmine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.agent.tools.ShellPolicy
import com.lhzkml.jasmine.ui.theme.BgPrimary
import com.lhzkml.jasmine.ui.theme.JasmineTheme
import com.lhzkml.jasmine.ui.theme.TextPrimary
import com.lhzkml.jasmine.ui.theme.TextSecondary
import com.lhzkml.jasmine.ui.components.*

class ShellPolicyActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JasmineTheme {
                ShellPolicyScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
fun ShellPolicyScreen(onBack: () -> Unit) {
    val config = AppConfig.configRepo()
    
    var selectedPolicy by remember { mutableStateOf(config.getShellPolicy()) }
    var blacklistText by remember { mutableStateOf(config.getShellBlacklist().joinToString("\n")) }
    var whitelistText by remember { mutableStateOf(config.getShellWhitelist().joinToString("\n")) }
    
    DisposableEffect(Unit) {
        onDispose {
            val blacklist = blacklistText.lines().filter { it.isNotBlank() }
            val whitelist = whitelistText.lines().filter { it.isNotBlank() }
            config.setShellBlacklist(blacklist)
            config.setShellWhitelist(whitelist)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(BgPrimary)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CustomTextButton(
                onClick = onBack,
                colors = CustomButtonDefaults.textButtonColors(contentColor = TextPrimary)
            ) {
                CustomText("返回", fontSize = 14.sp)
            }
            
            CustomText(
                text = "Shell 命令策略",
                fontSize = 18.sp,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            
            Spacer(modifier = Modifier.width(48.dp))
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CustomText(
                "执行策略",
                fontSize = 14.sp,
                color = TextSecondary
            )
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(8.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedPolicy == ShellPolicy.MANUAL,
                                onClick = {
                                    selectedPolicy = ShellPolicy.MANUAL
                                    config.setShellPolicy(ShellPolicy.MANUAL)
                                }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CustomRadioButton(
                            selected = selectedPolicy == ShellPolicy.MANUAL,
                            onClick = {
                                selectedPolicy = ShellPolicy.MANUAL
                                config.setShellPolicy(ShellPolicy.MANUAL)
                            }
                        )
                        CustomText(
                            "手动确认 - 所有命令都需要确认",
                            fontSize = 14.sp,
                            color = TextPrimary,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedPolicy == ShellPolicy.BLACKLIST,
                                onClick = {
                                    selectedPolicy = ShellPolicy.BLACKLIST
                                    config.setShellPolicy(ShellPolicy.BLACKLIST)
                                }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CustomRadioButton(
                            selected = selectedPolicy == ShellPolicy.BLACKLIST,
                            onClick = {
                                selectedPolicy = ShellPolicy.BLACKLIST
                                config.setShellPolicy(ShellPolicy.BLACKLIST)
                            }
                        )
                        CustomText(
                            "黑名单 - 匹配的命令需确认，其余自动执行",
                            fontSize = 14.sp,
                            color = TextPrimary,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedPolicy == ShellPolicy.WHITELIST,
                                onClick = {
                                    selectedPolicy = ShellPolicy.WHITELIST
                                    config.setShellPolicy(ShellPolicy.WHITELIST)
                                }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CustomRadioButton(
                            selected = selectedPolicy == ShellPolicy.WHITELIST,
                            onClick = {
                                selectedPolicy = ShellPolicy.WHITELIST
                                config.setShellPolicy(ShellPolicy.WHITELIST)
                            }
                        )
                        CustomText(
                            "白名单 - 匹配的命令自动执行，其余需确认",
                            fontSize = 14.sp,
                            color = TextPrimary,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
            
            if (selectedPolicy == ShellPolicy.BLACKLIST) {
                Column {
                    CustomText(
                        "黑名单关键词（每行一个，命令包含关键词则需确认）",
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        BasicTextField(
                            value = blacklistText,
                            onValueChange = { blacklistText = it },
                            textStyle = TextStyle(
                                fontSize = 13.sp,
                                color = TextPrimary
                            ),
                            cursorBrush = SolidColor(TextPrimary),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            
            if (selectedPolicy == ShellPolicy.WHITELIST) {
                Column {
                    CustomText(
                        "白名单关键词（每行一个，命令以关键词开头则自动执行）",
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        BasicTextField(
                            value = whitelistText,
                            onValueChange = { whitelistText = it },
                            textStyle = TextStyle(
                                fontSize = 13.sp,
                                color = TextPrimary
                            ),
                            cursorBrush = SolidColor(TextPrimary),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
