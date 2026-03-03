package com.lhzkml.jasmine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.agent.tools.ShellPolicy
import com.lhzkml.jasmine.ui.theme.BgPrimary
import com.lhzkml.jasmine.ui.theme.JasmineTheme
import com.lhzkml.jasmine.ui.theme.TextPrimary
import com.lhzkml.jasmine.ui.theme.TextSecondary

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
            TextButton(
                onClick = onBack,
                colors = ButtonDefaults.textButtonColors(contentColor = TextPrimary)
            ) {
                Text("返回", fontSize = 14.sp)
            }
            
            Text(
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
            Text(
                "执行策略",
                fontSize = 14.sp,
                color = TextSecondary
            )
            
            Surface(
                color = Color.White,
                shape = MaterialTheme.shapes.medium
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
                        RadioButton(
                            selected = selectedPolicy == ShellPolicy.MANUAL,
                            onClick = {
                                selectedPolicy = ShellPolicy.MANUAL
                                config.setShellPolicy(ShellPolicy.MANUAL)
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = TextPrimary,
                                unselectedColor = TextSecondary
                            )
                        )
                        Text(
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
                        RadioButton(
                            selected = selectedPolicy == ShellPolicy.BLACKLIST,
                            onClick = {
                                selectedPolicy = ShellPolicy.BLACKLIST
                                config.setShellPolicy(ShellPolicy.BLACKLIST)
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = TextPrimary,
                                unselectedColor = TextSecondary
                            )
                        )
                        Text(
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
                        RadioButton(
                            selected = selectedPolicy == ShellPolicy.WHITELIST,
                            onClick = {
                                selectedPolicy = ShellPolicy.WHITELIST
                                config.setShellPolicy(ShellPolicy.WHITELIST)
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = TextPrimary,
                                unselectedColor = TextSecondary
                            )
                        )
                        Text(
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
                    Text(
                        "黑名单关键词（每行一个，命令包含关键词则需确认）",
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(Color.White, MaterialTheme.shapes.medium)
                            .border(1.dp, Color(0xFFE8E8E8), MaterialTheme.shapes.medium)
                            .padding(12.dp)
                    ) {
                        BasicTextField(
                            value = blacklistText,
                            onValueChange = { blacklistText = it },
                            textStyle = LocalTextStyle.current.copy(
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
                    Text(
                        "白名单关键词（每行一个，命令以关键词开头则自动执行）",
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(Color.White, MaterialTheme.shapes.medium)
                            .border(1.dp, Color(0xFFE8E8E8), MaterialTheme.shapes.medium)
                            .padding(12.dp)
                    ) {
                        BasicTextField(
                            value = whitelistText,
                            onValueChange = { whitelistText = it },
                            textStyle = LocalTextStyle.current.copy(
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
