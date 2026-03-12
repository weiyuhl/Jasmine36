package com.lhzkml.jasmine.rag

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.lhzkml.jasmine.ui.theme.JasmineTheme
import com.lhzkml.jasmine.repository.RagConfigRepository
import com.lhzkml.jasmine.repository.MnnModelRepository
import com.lhzkml.jasmine.core.rag.embedding.EmbeddingApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.lhzkml.jasmine.ui.components.CustomButton
import com.lhzkml.jasmine.ui.components.CustomButtonDefaults
import com.lhzkml.jasmine.ui.components.CustomHorizontalDivider
import com.lhzkml.jasmine.ui.components.CustomText
import com.lhzkml.jasmine.ui.components.CustomTextButton
import com.lhzkml.jasmine.ui.components.CustomDropdownMenu
import com.lhzkml.jasmine.ui.components.CustomDropdownMenuItem
import com.lhzkml.jasmine.ui.theme.*
import androidx.compose.ui.text.style.TextOverflow
import org.koin.android.ext.android.inject

class EmbeddingConfigActivity : ComponentActivity() {
    private val ragRepository: RagConfigRepository by inject()
    private val mnnRepository: MnnModelRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JasmineTheme {
                EmbeddingConfigScreen(
                    ragRepository = ragRepository,
                    mnnRepository = mnnRepository,
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
fun EmbeddingConfigScreen(
    ragRepository: RagConfigRepository,
    mnnRepository: MnnModelRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var useLocal by remember { mutableStateOf(ragRepository.getRagEmbeddingUseLocal()) }
    var baseUrl by remember { mutableStateOf(ragRepository.getRagEmbeddingBaseUrl()) }
    var apiKey by remember { mutableStateOf(ragRepository.getRagEmbeddingApiKey()) }
    var model by remember { mutableStateOf(ragRepository.getRagEmbeddingModel()) }
    var modelPath by remember { mutableStateOf(ragRepository.getRagEmbeddingModelPath()) }
    val localModels = remember { mnnRepository.getLocalModels() }

    var apiModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var apiModelsLoading by remember { mutableStateOf(false) }
    var apiModelsStatus by remember { mutableStateOf<String?>(null) }

    fun fetchApiModels() {
        if (baseUrl.isBlank() || apiKey.isBlank()) {
            apiModelsStatus = "请先填写 API 地址和 Key"
            return
        }
        apiModelsLoading = true
        apiModelsStatus = "正在获取模型列表..."
        scope.launch {
            try {
                val list = withContext(Dispatchers.IO) {
                    EmbeddingApiClient.listModels(baseUrl.trim(), apiKey)
                }
                apiModels = list
                apiModelsLoading = false
                apiModelsStatus = if (list.isEmpty()) "未获取到模型" else "共 ${list.size} 个模型"
            } catch (e: Exception) {
                apiModelsLoading = false
                apiModelsStatus = "获取失败: ${e.message}"
            }
        }
    }

    fun save() {
        focusManager.clearFocus()
        ragRepository.setRagEmbeddingUseLocal(useLocal)
        ragRepository.setRagEmbeddingBaseUrl(baseUrl.trim())
        ragRepository.setRagEmbeddingApiKey(apiKey)
        ragRepository.setRagEmbeddingModel(model.trim().ifBlank { "text-embedding-3-small" })
        ragRepository.setRagEmbeddingModelPath(modelPath.trim())
        apiModels = emptyList()
        apiModelsStatus = null
        Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
    }

    DisposableEffect(Unit) {
        onDispose { save() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(Color.White)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CustomTextButton(onClick = onBack, contentColor = TextPrimary, contentPadding = PaddingValues(6.dp)) {
                CustomText("<- 返回", fontSize = 14.sp, color = TextPrimary)
            }
            CustomText(
                text = "Embedding 服务",
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
            // 模式切换
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf("远程 API" to false, "本地 MNN" to true).forEach { (label, local) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (useLocal == local) Accent.copy(alpha = 0.2f) else Color(0xFFF0F0F0))
                            .border(1.dp, if (useLocal == local) Accent else Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable { useLocal = local },
                        contentAlignment = Alignment.Center
                    ) {
                        CustomText(
                            text = label,
                            fontSize = 14.sp,
                            color = if (useLocal == local) Accent else TextSecondary,
                            fontWeight = if (useLocal == local) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            if (useLocal) {
                // 本地 MNN
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    CustomText(
                        text = "使用本地 MNN Embedding 模型，供 RAG 知识库向量化。请选择 Embedding 模型，勿选 LLM 对话模型。",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    CustomText(text = "Embedding 模型", fontSize = 14.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    if (localModels.isEmpty()) {
                        CustomText("无可用的 MNN 模型，请先在「本地 MNN」中导入 Embedding 模型", fontSize = 12.sp, color = TextSecondary)
                    } else {
                        var expanded by remember { mutableStateOf(false) }
                        val currentModel = localModels.find {
                            java.io.File(it.modelPath).parentFile?.absolutePath == modelPath
                        } ?: localModels.firstOrNull()
                        Box {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White)
                                    .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(8.dp))
                                    .clickable { expanded = true }
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                CustomText(
                                    text = currentModel?.modelName ?: "选择模型",
                                    fontSize = 14.sp,
                                    color = if (currentModel != null) TextPrimary else TextSecondary
                                )
                            }
                            com.lhzkml.jasmine.ui.components.CustomDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                localModels.forEach { m ->
                                    com.lhzkml.jasmine.ui.components.CustomDropdownMenuItem(
                                        text = { CustomText(m.modelName, fontSize = 14.sp, color = TextPrimary) },
                                        onClick = {
                                            modelPath = java.io.File(m.modelPath).parentFile?.absolutePath ?: ""
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // 远程 API
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    CustomText(
                        text = "使用 OpenAI 兼容的 /v1/embeddings 接口，供 RAG 知识库向量化使用",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    CustomText(text = "API 地址", fontSize = 14.sp, color = TextPrimary)
                    CustomText(text = "例如 https://api.openai.com", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
                    RagTextField(value = baseUrl, onValueChange = { baseUrl = it }, placeholder = "https://api.openai.com")
                    Spacer(modifier = Modifier.height(16.dp))
                    CustomText(text = "API Key", fontSize = 14.sp, color = TextPrimary)
                    CustomText(text = "Bearer 认证", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
                    RagTextField(value = apiKey, onValueChange = { apiKey = it }, placeholder = "sk-...", keyboardType = KeyboardType.Password)
                    Spacer(modifier = Modifier.height(16.dp))
                    CustomText(text = "模型名称", fontSize = 14.sp, color = TextPrimary)
                    CustomText(text = "可手动输入，或点击「获取列表」从 API 拉取后选择", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RagTextField(
                            modifier = Modifier.weight(1f),
                            value = model,
                            onValueChange = { model = it },
                            placeholder = "text-embedding-3-small"
                        )
                        CustomTextButton(
                            onClick = { fetchApiModels() },
                            enabled = !apiModelsLoading,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            colors = CustomButtonDefaults.textButtonColors(contentColor = Accent)
                        ) {
                            CustomText(if (apiModelsLoading) "获取中..." else "获取列表", fontSize = 13.sp)
                        }
                    }
                    apiModelsStatus?.let { status ->
                        CustomText(status, fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (apiModels.isNotEmpty()) {
                        CustomHorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
                        CustomText(text = "点击选择模型", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            items(apiModels, key = { it }) { m ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { model = m }
                                        .background(if (model == m) Accent.copy(alpha = 0.15f) else Color.Transparent)
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CustomText(
                                        text = m,
                                        fontSize = 14.sp,
                                        color = if (model == m) Accent else TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            CustomButton(
                onClick = { save() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = CustomButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp)
            ) {
                CustomText("保存", fontSize = 16.sp, color = Color.White)
            }
        }
    }
}

@Composable
private fun RagTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Uri,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = TextStyle(fontSize = 14.sp, color = TextPrimary),
            cursorBrush = SolidColor(TextPrimary),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                if (value.isEmpty()) {
                    CustomText(placeholder, fontSize = 14.sp, color = TextSecondary)
                }
                innerTextField()
            }
        )
    }
}
