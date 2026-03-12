package com.lhzkml.jasmine

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.lhzkml.jasmine.ui.theme.Accent
import com.lhzkml.jasmine.ui.theme.BgPrimary
import com.lhzkml.jasmine.ui.theme.TextPrimary
import com.lhzkml.jasmine.ui.theme.TextSecondary
import com.lhzkml.jasmine.ui.components.*
import com.lhzkml.jasmine.repository.ProviderRepository
import com.lhzkml.jasmine.repository.RagConfigRepository
import org.koin.android.ext.android.inject

class ProviderListActivity : ComponentActivity() {
    private val providerRepository: ProviderRepository by inject()
    private val ragRepository: RagConfigRepository by inject()

    private val addProviderLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            /* 添加成功，ProviderListScreen 通过 onResume 刷新 */
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ProviderListScreen(
                repository = providerRepository,
                ragRepository = ragRepository,
                onBack = { finish() },
                onProviderClick = { providerId ->
                    startActivity(Intent(this, ProviderConfigActivity::class.java).apply {
                        putExtra("provider_id", providerId)
                    })
                },
                onNavigateToEmbedding = {
                    startActivity(Intent(this, com.lhzkml.jasmine.rag.EmbeddingConfigActivity::class.java))
                },
                onNavigateToMnnManagement = {
                    startActivity(Intent(this, com.lhzkml.jasmine.mnn.MnnManagementActivity::class.java))
                },
                onNavigateToAddCustomProvider = {
                    addProviderLauncher.launch(Intent(this, AddCustomProviderActivity::class.java))
                }
            )
        }
    }
}


@Composable
fun ProviderListScreen(
    repository: ProviderRepository,
    ragRepository: RagConfigRepository,
    onBack: () -> Unit,
    onProviderClick: (String) -> Unit,
    onNavigateToEmbedding: () -> Unit = {},
    onNavigateToMnnManagement: () -> Unit = {},
    onNavigateToAddCustomProvider: () -> Unit = {}
) {
    val context = LocalContext.current
    
    var refreshTrigger by remember { mutableStateOf(0) }
    var providerToDelete by remember { mutableStateOf<ProviderConfig?>(null) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val providers by remember(refreshTrigger) {
        mutableStateOf(repository.getAllProviders())
    }
    
    val activeId by remember(refreshTrigger) {
        mutableStateOf(repository.getActiveProviderId())
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
                colors = CustomButtonDefaults.textButtonColors(contentColor = TextPrimary)
            ) {
                CustomText("← 返回", fontSize = 14.sp)
            }
            
            CustomText(
                text = "模型供应商",
                fontSize = 17.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            
            Spacer(modifier = Modifier.width(56.dp))
        }
        
        CustomHorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp)
        
        // Embedding 服务入口
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToEmbedding() }
            ) {
                ProviderItem(
                    provider = ProviderConfig(
                        id = "embedding",
                        name = "Embedding 服务",
                        defaultBaseUrl = "",
                        defaultModel = "",
                        apiType = ApiType.OPENAI,
                        isCustom = false
                    ),
                    isActive = false,
                    hasKey = run {
                        val useLocal = ragRepository.getRagEmbeddingUseLocal()
                        if (useLocal) ragRepository.getRagEmbeddingModelPath().isNotBlank()
                        else ragRepository.getRagEmbeddingBaseUrl().isNotBlank() && ragRepository.getRagEmbeddingApiKey().isNotBlank()
                    },
                    model = when {
                        ragRepository.getRagEmbeddingUseLocal() -> if (ragRepository.getRagEmbeddingModelPath().isNotBlank()) "本地 MNN · 已配置" else "未配置"
                        else -> if (ragRepository.getRagEmbeddingBaseUrl().isNotBlank()) "远程 API · 已配置" else "未配置"
                    },
                    onSwitchChange = { },
                    onClick = { onNavigateToEmbedding() },
                    onDelete = null,
                    showSwitch = false
                )
            }
            providers.forEach { provider ->
                val isLocal = provider.apiType == ApiType.LOCAL
                ProviderItem(
                    provider = provider,
                    isActive = provider.id == activeId,
                    hasKey = isLocal || repository.getApiKey(provider.id) != null,
                    model = if (isLocal) "本地推理" else repository.getModel(provider.id),
                    onSwitchChange = { checked ->
                        if (checked) {
                            if (isLocal) {
                                repository.setActiveProviderId(provider.id)
                                refreshTrigger++
                            } else {
                                val key = repository.getApiKey(provider.id)
                                if (key == null) {
                                    onProviderClick(provider.id)
                                } else {
                                    repository.setActiveProviderId(provider.id)
                                    refreshTrigger++
                                }
                            }
                        } else {
                            if (repository.getActiveProviderId() == provider.id) {
                                repository.setActiveProviderId("")
                                refreshTrigger++
                            }
                        }
                    },
                    onClick = {
                        if (isLocal) {
                            onNavigateToMnnManagement()
                        } else {
                            onProviderClick(provider.id)
                        }
                    },
                    onDelete = if (provider.isCustom) {
                        { providerToDelete = provider }
                    } else null
                )
            }
        }
        
        // 底部添加按钮
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
        ) {
            CustomButton(
                onClick = { onNavigateToAddCustomProvider() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(48.dp),
                colors = CustomButtonDefaults.buttonColors(
                    containerColor = TextPrimary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                CustomText("+ 添加自定义供应商", fontSize = 15.sp)
            }
        }
    }

    // 删除供应商确认对话框
    providerToDelete?.let { provider ->
        DeleteProviderDialog(
            provider = provider,
            onDismiss = { providerToDelete = null },
            onConfirm = {
                if (repository.getActiveProviderId() == provider.id) {
                    repository.setActiveProviderId("")
                }
                val success = repository.unregisterProvider(provider.id)
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
    onDelete: (() -> Unit)?,
    showSwitch: Boolean = true
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                CustomText(
                    text = provider.name,
                    fontSize = 15.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                CustomText(
                    text = if (hasKey) "已配置 · $model" else "未配置",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            
            if (onDelete != null) {
                CustomTextButton(
                    onClick = onDelete,
                    colors = CustomButtonDefaults.textButtonColors(contentColor = TextSecondary)
                ) {
                    CustomText("删除", fontSize = 14.sp)
                }
            }
            
            if (showSwitch) {
                CustomSwitch(
                    checked = isActive,
                    onCheckedChange = onSwitchChange,
                    colors = CustomSwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Accent,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = TextSecondary.copy(alpha = 0.5f),
                        uncheckedBorderColor = TextSecondary.copy(alpha = 0.5f)
                    )
                )
            }
        }
    }
}

@Composable
fun DeleteProviderDialog(
    provider: ProviderConfig,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    CustomAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary,
        title = { CustomText("删除供应商", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            CustomText(
                "确定要删除「${provider.name}」吗？\n删除后配置信息将保留，但供应商将从列表中移除。",
                color = TextPrimary,
                fontSize = 14.sp
            )
        },
        confirmButton = {
            CustomTextButton(
                onClick = onConfirm,
                colors = CustomButtonDefaults.textButtonColors(contentColor = TextPrimary)
            ) {
                CustomText("删除", fontSize = 14.sp)
            }
        },
        dismissButton = {
            CustomTextButton(
                onClick = onDismiss,
                colors = CustomButtonDefaults.textButtonColors(contentColor = TextSecondary)
            ) {
                CustomText("取消", fontSize = 14.sp)
            }
        }
    )
}
