package com.lhzkml.jasmine

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.prompt.executor.ApiType
import com.lhzkml.jasmine.core.config.ProviderConfig
import com.lhzkml.jasmine.ui.theme.BgPrimary
import com.lhzkml.jasmine.ui.theme.TextPrimary
import com.lhzkml.jasmine.ui.theme.TextSecondary
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowDropDown

class ProviderListActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ProviderListScreen(
                onBack = { finish() },
                onProviderClick = { providerId ->
                    startActivity(Intent(this, ProviderConfigActivity::class.java).apply {
                        putExtra("provider_id", providerId)
                    })
                }
            )
        }
    }
}


@Composable
fun ProviderListScreen(
    onBack: () -> Unit,
    onProviderClick: (String) -> Unit
) {
    val context = LocalContext.current
    val config = AppConfig.configRepo()
    val registry = AppConfig.providerRegistry()
    
    var refreshTrigger by remember { mutableStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }
    var providerToDelete by remember { mutableStateOf<ProviderConfig?>(null) }
    
    val providers by remember(refreshTrigger) {
        mutableStateOf(registry.providers)
    }
    
    val activeId by remember(refreshTrigger) {
        mutableStateOf(config.getActiveProviderId())
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
                Text("← 返回", fontSize = 14.sp)
            }
            
            Text(
                text = "模型供应商",
                fontSize = 17.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            
            Spacer(modifier = Modifier.width(56.dp))
        }
        
        HorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp)
        
        // 供应商列表
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            providers.forEach { provider ->
                ProviderItem(
                    provider = provider,
                    isActive = provider.id == activeId,
                    hasKey = config.getApiKey(provider.id) != null,
                    model = registry.getModel(provider.id),
                    onSwitchChange = { checked ->
                        if (checked) {
                            val key = config.getApiKey(provider.id)
                            if (key == null) {
                                onProviderClick(provider.id)
                            } else {
                                config.setActiveProviderId(provider.id)
                                refreshTrigger++
                            }
                        } else {
                            if (config.getActiveProviderId() == provider.id) {
                                config.setActiveProviderId("")
                                refreshTrigger++
                            }
                        }
                    },
                    onClick = { onProviderClick(provider.id) },
                    onDelete = if (provider.isCustom) {
                        { providerToDelete = provider }
                    } else null
                )
            }
        }
        
        // 底部添加按钮
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White
        ) {
            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TextPrimary,
                    contentColor = Color.White
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("+ 添加自定义供应商", fontSize = 15.sp)
            }
        }
    }
    
    // 添加自定义供应商对话框
    if (showAddDialog) {
        AddCustomProviderDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { provider ->
                val success = registry.registerProviderPersistent(provider)
                if (success) {
                    Toast.makeText(context, "添加成功", Toast.LENGTH_SHORT).show()
                    refreshTrigger++
                    showAddDialog = false
                } else {
                    Toast.makeText(context, "供应商 ID 已存在", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
    
    // 删除供应商确认对话框
    providerToDelete?.let { provider ->
        DeleteProviderDialog(
            provider = provider,
            onDismiss = { providerToDelete = null },
            onConfirm = {
                if (config.getActiveProviderId() == provider.id) {
                    config.setActiveProviderId("")
                }
                val success = registry.unregisterProviderPersistent(provider.id)
                if (success) {
                    Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                    refreshTrigger++
                } else {
                    Toast.makeText(context, "删除失败", Toast.LENGTH_SHORT).show()
                }
                providerToDelete = null
            }
        )
    }
}

@Composable
fun ProviderItem(
    provider: ProviderConfig,
    isActive: Boolean,
    hasKey: Boolean,
    model: String,
    onSwitchChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = provider.name,
                    fontSize = 15.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (hasKey) "已配置 · $model" else "未配置",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            
            if (onDelete != null) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除供应商",
                        tint = TextSecondary
                    )
                }
            }
            
            Switch(
                checked = isActive,
                onCheckedChange = onSwitchChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = TextPrimary,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = TextSecondary.copy(alpha = 0.5f),
                    uncheckedBorderColor = TextSecondary.copy(alpha = 0.5f)
                )
            )
        }
    }
}

@Composable
fun AddCustomProviderDialog(
    onDismiss: () -> Unit,
    onAdd: (ProviderConfig) -> Unit
) {
    var selectedApiType by remember { mutableStateOf(ApiType.OPENAI) }
    var providerId by remember { mutableStateOf("") }
    var providerName by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    
    val context = LocalContext.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        titleContentColor = TextPrimary,
        title = { Text("添加自定义供应商", color = TextPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // API 渠道类型
                Column {
                    Text("API 渠道类型", fontSize = 14.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    var expanded by remember { mutableStateOf(false) }
                    val apiTypes = listOf(
                        "OpenAI 兼容" to ApiType.OPENAI,
                        "Claude" to ApiType.CLAUDE,
                        "Gemini" to ApiType.GEMINI
                    )
                    
                    Box {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = TextPrimary
                            )
                        ) {
                            Text(
                                text = apiTypes.find { it.second == selectedApiType }?.first ?: "OpenAI 兼容",
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null
                            )
                        }
                        
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            apiTypes.forEach { (label, type) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        selectedApiType = type
                                        if (baseUrl.isEmpty()) {
                                            baseUrl = when (type) {
                                                ApiType.OPENAI -> ""
                                                ApiType.CLAUDE -> "https://api.anthropic.com"
                                                ApiType.GEMINI -> "https://generativelanguage.googleapis.com"
                                            }
                                        }
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                
                // 供应商 ID
                Column {
                    Text("供应商 ID", fontSize = 14.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = providerId,
                        onValueChange = { providerId = it },
                        placeholder = { Text("例如: openai", color = TextSecondary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = TextPrimary,
                            unfocusedBorderColor = TextSecondary,
                            cursorColor = TextPrimary
                        )
                    )
                }
                
                // 供应商名称
                Column {
                    Text("供应商名称", fontSize = 14.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = providerName,
                        onValueChange = { providerName = it },
                        placeholder = { Text("例如: OpenAI", color = TextSecondary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = TextPrimary,
                            unfocusedBorderColor = TextSecondary,
                            cursorColor = TextPrimary
                        )
                    )
                }
                
                // API 地址
                Column {
                    Text("API 地址", fontSize = 14.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        placeholder = { Text("例如: https://api.openai.com", color = TextSecondary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = TextPrimary,
                            unfocusedBorderColor = TextSecondary,
                            cursorColor = TextPrimary
                        )
                    )
                }
                
                // 默认模型
                Column {
                    Text("默认模型", fontSize = 14.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        placeholder = { Text("例如: gpt-4", color = TextSecondary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = TextPrimary,
                            unfocusedBorderColor = TextSecondary,
                            cursorColor = TextPrimary
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        providerId.isEmpty() -> {
                            Toast.makeText(context, "请输入供应商 ID", Toast.LENGTH_SHORT).show()
                        }
                        providerName.isEmpty() -> {
                            Toast.makeText(context, "请输入供应商名称", Toast.LENGTH_SHORT).show()
                        }
                        baseUrl.isEmpty() -> {
                            Toast.makeText(context, "请输入 API 地址", Toast.LENGTH_SHORT).show()
                        }
                        model.isEmpty() -> {
                            Toast.makeText(context, "请输入默认模型", Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            val provider = ProviderConfig(
                                id = providerId.trim(),
                                name = providerName.trim(),
                                defaultBaseUrl = baseUrl.trim(),
                                defaultModel = model.trim(),
                                apiType = selectedApiType,
                                isCustom = true
                            )
                            onAdd(provider)
                        }
                    }
                },
                colors = ButtonDefaults.textButtonColors(contentColor = TextPrimary)
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
            ) {
                Text("取消")
            }
        }
    )
}

@Composable
fun DeleteProviderDialog(
    provider: ProviderConfig,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary,
        title = { Text("删除供应商", color = TextPrimary) },
        text = {
            Text(
                "确定要删除「${provider.name}」吗？\n删除后配置信息将保留，但供应商将从列表中移除。",
                color = TextPrimary
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = TextPrimary)
            ) {
                Text("删除")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
            ) {
                Text("取消")
            }
        }
    )
}
