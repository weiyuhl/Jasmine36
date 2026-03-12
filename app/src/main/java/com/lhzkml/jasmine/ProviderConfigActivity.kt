package com.lhzkml.jasmine

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.config.AppConfig
import com.lhzkml.jasmine.core.config.ProviderConfig
import com.lhzkml.jasmine.core.prompt.executor.ApiType
import com.lhzkml.jasmine.core.prompt.executor.ChatClientConfig
import com.lhzkml.jasmine.core.prompt.executor.ChatClientFactory
import com.lhzkml.jasmine.core.prompt.llm.ModelRegistry
import com.lhzkml.jasmine.core.prompt.model.ModelInfo
import com.lhzkml.jasmine.ui.components.*
import com.lhzkml.jasmine.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.lhzkml.jasmine.repository.ProviderRepository
import org.koin.android.ext.android.inject

class ProviderConfigActivity : ComponentActivity() {
    private val providerRepository: ProviderRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val providerId = intent.getStringExtra("provider_id") ?: run { finish(); return }
        val provider = providerRepository.getProvider(providerId) ?: run { finish(); return }
        val initialTab = intent.getIntExtra("tab", 0).coerceIn(0, 1)

        setContent {
            ProviderConfigScreen(
                provider = provider,
                repository = providerRepository,
                initialTab = initialTab,
                onBack = { finish() }
            )
        }
    }
}

@Composable
fun ProviderConfigScreen(
    provider: ProviderConfig,
    repository: ProviderRepository,
    initialTab: Int,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(initialTab) }

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
                text = provider.name,
                fontSize = 17.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(56.dp))
        }

        // Tab 栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(Color.White)
        ) {
            val tabs = listOf("配置", "模型列表")
            tabs.forEachIndexed { index, title ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { selectedTab = index },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CustomText(
                            text = title,
                            fontSize = 14.sp,
                            color = if (selectedTab == index) TextPrimary else TextSecondary,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(2.dp)
                                .background(
                                    if (selectedTab == index) Accent else Color.Transparent,
                                    RoundedCornerShape(1.dp)
                                )
                        )
                    }
                }
            }
        }

        CustomHorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp)

        // 内容区域
        when (selectedTab) {
            0 -> ProviderConfigTab(provider = provider, repository = repository)
            1 -> ModelListTab(provider = provider, repository = repository)
        }
    }
}

// ==================== 配置 Tab ====================

@Composable
fun ProviderConfigTab(provider: ProviderConfig, repository: ProviderRepository) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var apiKey by remember { mutableStateOf(repository.getApiKey(provider.id) ?: "") }
    var baseUrl by remember { mutableStateOf(repository.getBaseUrl(provider.id)) }
    var chatPath by remember {
        mutableStateOf(repository.getChatPath(provider.id) ?: "")
    }
    var vertexEnabled by remember {
        mutableStateOf(provider.apiType == ApiType.GEMINI && repository.isVertexAIEnabled(provider.id))
    }
    var vertexProjectId by remember { mutableStateOf(repository.getVertexProjectId(provider.id)) }
    var vertexLocation by remember { mutableStateOf(repository.getVertexLocation(provider.id)) }
    var vertexSaJson by remember { mutableStateOf<String>(repository.getVertexServiceAccountJson(provider.id)) }

    var balanceText by remember { mutableStateOf("") }
    var isQuerying by remember { mutableStateOf(false) }
    val supportsBalance = provider.id == "deepseek" || provider.id == "siliconflow"

    val showChatPath = !vertexEnabled && (provider.apiType == ApiType.OPENAI || provider.apiType == ApiType.GEMINI)
    val chatPathHint = when (provider.apiType) {
        ApiType.OPENAI -> "/v1/chat/completions"
        ApiType.GEMINI -> "/v1beta/models/{model}:generateContent"
        ApiType.CLAUDE -> "/v1/messages"
        ApiType.LOCAL -> ""
    }
    val keyLabel = if (vertexEnabled) "API Key (可选)" else "API Key"
    val keyHint = if (vertexEnabled) "Vertex AI 使用服务账号认证" else "sk-..."

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .padding(20.dp)
        ) {
            // API 地址
            FieldLabel("API 地址")
            Spacer(modifier = Modifier.height(8.dp))
            CustomOutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                placeholder = { CustomText(provider.defaultBaseUrl, color = TextSecondary, fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                focusedBorderColor = TextPrimary,
                unfocusedBorderColor = TextSecondary
            )

            // API 路径
            if (showChatPath) {
                Spacer(modifier = Modifier.height(16.dp))
                FieldLabel("API 路径")
                Spacer(modifier = Modifier.height(8.dp))
                CustomOutlinedTextField(
                    value = chatPath,
                    onValueChange = { chatPath = it },
                    placeholder = { CustomText(chatPathHint, color = TextSecondary, fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    focusedBorderColor = TextPrimary,
                    unfocusedBorderColor = TextSecondary
                )
                CustomText(
                    text = "默认: $chatPathHint",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // API Key
            Spacer(modifier = Modifier.height(16.dp))
            FieldLabel(keyLabel)
            Spacer(modifier = Modifier.height(8.dp))
            CustomOutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                placeholder = { CustomText(keyHint, color = TextSecondary, fontSize = 13.sp) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                focusedBorderColor = TextPrimary,
                unfocusedBorderColor = TextSecondary
            )

            // Vertex AI（仅 Gemini）
            if (provider.apiType == ApiType.GEMINI) {
                Spacer(modifier = Modifier.height(16.dp))
                CustomHorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        CustomText("使用 Vertex AI", fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                        CustomText(
                            "启用后将使用 Vertex AI 端点和服务账号认证",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    CustomSwitch(
                        checked = vertexEnabled,
                        onCheckedChange = { vertexEnabled = it },
                        colors = CustomSwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Accent,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = TextSecondary.copy(alpha = 0.5f)
                        )
                    )
                }

                if (vertexEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    FieldLabel("项目 ID")
                    Spacer(modifier = Modifier.height(8.dp))
                    CustomOutlinedTextField(
                        value = vertexProjectId,
                        onValueChange = { vertexProjectId = it },
                        placeholder = { CustomText("my-gcp-project", color = TextSecondary, fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        focusedBorderColor = TextPrimary,
                        unfocusedBorderColor = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    FieldLabel("区域 (Location)")
                    Spacer(modifier = Modifier.height(8.dp))
                    CustomOutlinedTextField(
                        value = vertexLocation,
                        onValueChange = { vertexLocation = it },
                        placeholder = { CustomText("global 或 us-central1", color = TextSecondary, fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        focusedBorderColor = TextPrimary,
                        unfocusedBorderColor = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    FieldLabel("服务账号 JSON")
                    Spacer(modifier = Modifier.height(8.dp))
                    CustomOutlinedTextField(
                        value = vertexSaJson,
                        onValueChange = { vertexSaJson = it },
                        placeholder = { CustomText("粘贴服务账号 JSON 内容...", color = TextSecondary, fontSize = 12.sp) },
                        singleLine = false,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                        focusedBorderColor = TextPrimary,
                        unfocusedBorderColor = TextSecondary
                    )
                }
            }

            // 保存按钮
            Spacer(modifier = Modifier.height(20.dp))
            CustomButton(
                onClick = {
                    focusManager.clearFocus()
                    if (vertexEnabled) {
                        when {
                            vertexProjectId.isBlank() -> {
                                Toast.makeText(context, "请输入项目 ID", Toast.LENGTH_SHORT).show(); return@CustomButton
                            }
                            vertexLocation.isBlank() -> {
                                Toast.makeText(context, "请输入区域 (Location)", Toast.LENGTH_SHORT).show(); return@CustomButton
                            }
                            vertexSaJson.isBlank() -> {
                                Toast.makeText(context, "请粘贴服务账号 JSON", Toast.LENGTH_SHORT).show(); return@CustomButton
                            }
                            !vertexSaJson.contains("\"private_key\"") || !vertexSaJson.contains("\"client_email\"") -> {
                                Toast.makeText(context, "服务账号 JSON 格式不正确", Toast.LENGTH_LONG).show(); return@CustomButton
                            }
                        }
                        repository.saveProviderCredentials(provider.id, apiKey.trim(), baseUrl.trim().ifEmpty { null }, null)
                        repository.setVertexAIEnabled(provider.id, true)
                        repository.setVertexProjectId(provider.id, vertexProjectId.trim())
                        repository.setVertexLocation(provider.id, vertexLocation.trim())
                        repository.setVertexServiceAccountJson(provider.id, vertexSaJson.trim())
                    } else {
                        if (apiKey.isBlank()) {
                            Toast.makeText(context, "请输入 API Key", Toast.LENGTH_SHORT).show(); return@CustomButton
                        }
                        if (baseUrl.isBlank()) {
                            Toast.makeText(context, "请输入 API 地址", Toast.LENGTH_SHORT).show(); return@CustomButton
                        }
                        repository.saveProviderCredentials(provider.id, apiKey.trim(), baseUrl.trim(), null)
                        if (provider.apiType == ApiType.GEMINI) {
                            repository.setVertexAIEnabled(provider.id, false)
                        }
                    }
                    if (chatPath.isNotBlank()) {
                        repository.saveChatPath(provider.id, chatPath.trim())
                    }
                    repository.setActiveProviderId(provider.id)
                    Toast.makeText(context, "已保存并启用 ${provider.name}", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                CustomText("保存并启用", fontSize = 15.sp)
            }

            // 余额查询
            if (supportsBalance) {
                Spacer(modifier = Modifier.height(16.dp))
                CustomHorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CustomText("账户余额", fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    CustomTextButton(
                        onClick = {
                            if (apiKey.isBlank()) {
                                Toast.makeText(context, "请先输入 API Key", Toast.LENGTH_SHORT).show(); return@CustomTextButton
                            }
                            if (baseUrl.isBlank()) {
                                Toast.makeText(context, "请先输入 API 地址", Toast.LENGTH_SHORT).show(); return@CustomTextButton
                            }
                            isQuerying = true
                            balanceText = "正在查询余额..."
                            scope.launch {
                                try {
                                    val client = ChatClientFactory.create(ChatClientConfig(
                                        providerId = provider.id, providerName = provider.name,
                                        apiKey = apiKey.trim(), baseUrl = baseUrl.trim(), apiType = provider.apiType
                                    ))
                                    val balance = withContext(Dispatchers.IO) {
                                        val b = client.getBalance()
                                        client.close()
                                        b
                                    }
                                    isQuerying = false
                                    if (balance != null) {
                                        balanceText = balance.balances.joinToString("\n") { detail ->
                                            buildString {
                                                append("${detail.currency}: ${detail.totalBalance}")
                                                if (detail.grantedBalance != null && detail.toppedUpBalance != null) {
                                                    append("（赠送 ${detail.grantedBalance} + 充值 ${detail.toppedUpBalance}）")
                                                }
                                            }
                                        }
                                    } else {
                                        balanceText = "该供应商不支持余额查询"
                                    }
                                } catch (e: Exception) {
                                    isQuerying = false
                                    balanceText = "查询失败: ${e.message}"
                                }
                            }
                        },
                        enabled = !isQuerying,
                        colors = CustomButtonDefaults.textButtonColors(contentColor = Accent)
                    ) {
                        CustomText(if (isQuerying) "查询中..." else "查询", fontSize = 13.sp)
                    }
                }

                if (balanceText.isNotEmpty()) {
                    CustomText(
                        text = balanceText,
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

// ==================== 模型列表 Tab ====================

@Composable
fun ModelListTab(provider: ProviderConfig, repository: ProviderRepository) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var statusText by remember { mutableStateOf("") }
    var models by remember { mutableStateOf<List<ModelInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val currentModel = remember { repository.getModel(provider.id) }
    val checkedModels = remember {
        val saved = repository.getSelectedModels(provider.id).toMutableSet()
        if (currentModel.isNotEmpty()) saved.add(currentModel)
        mutableStateOf(saved)
    }

    fun fetchModels() {
        val apiKey = repository.getApiKey(provider.id) ?: ""
        val baseUrl = repository.getBaseUrl(provider.id)
        val vertexEnabled = provider.apiType == ApiType.GEMINI && repository.isVertexAIEnabled(provider.id)

        if (!vertexEnabled && apiKey.isEmpty()) {
            statusText = "请先在配置页输入 API Key"; return
        }
        if (!vertexEnabled && baseUrl.isEmpty()) {
            statusText = "请先在配置页输入 API 地址"; return
        }
        if (vertexEnabled) {
            statusText = "Vertex AI 不支持获取模型列表，请手动输入"; return
        }

        isLoading = true
        statusText = "正在获取模型列表..."
        val chatPath = repository.getChatPath(provider.id)
        val client = ChatClientFactory.create(ChatClientConfig(
            providerId = provider.id, providerName = provider.name,
            apiKey = apiKey, baseUrl = baseUrl,
            apiType = provider.apiType, chatPath = chatPath
        ))

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val r = client.listModels()
                    ModelRegistry.registerFromApi(client.provider, r)
                    client.close()
                    r
                }
                models = result
                isLoading = false
                statusText = "共 ${result.size} 个模型"
            } catch (e: Exception) {
                withContext(Dispatchers.IO) { client.close() }
                isLoading = false
                statusText = "获取失败: ${e.message}"
            }
        }
    }

    LaunchedEffect(Unit) { fetchModels() }

    Column(modifier = Modifier.fillMaxSize()) {
        // 状态栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(Color(0xFFF5F5F5))
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CustomText(
                text = statusText,
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            CustomText(
                text = "已选 ${checkedModels.value.size} 个",
                fontSize = 12.sp,
                color = Accent
            )
            CustomTextButton(
                onClick = { fetchModels() },
                enabled = !isLoading,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                colors = CustomButtonDefaults.textButtonColors(contentColor = Accent)
            ) {
                CustomText("刷新", fontSize = 12.sp)
            }
        }

        // 已选模型名称
        if (checkedModels.value.isNotEmpty()) {
            CustomText(
                text = checkedModels.value.joinToString(", ") { it.substringAfterLast("/") },
                fontSize = 11.sp,
                color = Accent,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            )
        }

        CustomHorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp)

        // 模型列表
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(models, key = { it.id }) { model ->
                ModelItem(
                    model = model,
                    isChecked = model.id in checkedModels.value,
                    isCurrent = model.id == currentModel,
                    onToggle = {
                        val newSet = checkedModels.value.toMutableSet()
                        if (model.id in newSet) newSet.remove(model.id) else newSet.add(model.id)
                        checkedModels.value = newSet
                    }
                )
            }
        }

        // 底部确认按钮
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(16.dp)
        ) {
            CustomButton(
                onClick = {
                    focusManager.clearFocus()
                    repository.setSelectedModels(provider.id, checkedModels.value.toList())
                    if (checkedModels.value.isNotEmpty()) {
                        val modelToSave = if (currentModel in checkedModels.value) currentModel else checkedModels.value.first()
                        repository.saveProviderCredentials(
                            provider.id,
                            repository.getApiKey(provider.id) ?: "",
                            repository.getBaseUrl(provider.id),
                            modelToSave
                        )
                    }
                    Toast.makeText(context, "已保存 ${checkedModels.value.size} 个模型", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                CustomText("确认选择", fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun ModelItem(
    model: ModelInfo,
    isChecked: Boolean,
    isCurrent: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CustomCheckbox(
            checked = isChecked,
            onCheckedChange = { onToggle() },
            checkedColor = Accent,
            uncheckedColor = TextSecondary.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            val displayText = if (model.displayName != null && model.displayName != model.id) {
                "${model.displayName}  (${model.id})"
            } else {
                model.id
            }
            CustomText(
                text = displayText,
                fontSize = 14.sp,
                color = if (isCurrent) Accent else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val meta = buildModelMeta(model)
            if (meta.isNotEmpty()) {
                CustomText(
                    text = meta,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

private fun buildModelMeta(model: ModelInfo): String {
    val parts = mutableListOf<String>()
    model.contextLength?.let { parts.add("输入 ${formatTokenCount(it)}") }
    model.maxOutputTokens?.let { parts.add("输出 ${formatTokenCount(it)}") }
    if (model.supportsThinking == true) parts.add("思考")
    return parts.joinToString(" · ")
}

private fun formatTokenCount(tokens: Int): String {
    return when {
        tokens >= 1_000_000 -> "${tokens / 1_000_000}M"
        tokens >= 1_000 -> "${tokens / 1_000}K"
        else -> tokens.toString()
    }
}

@Composable
private fun FieldLabel(text: String) {
    CustomText(text, fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
}
