package com.lhzkml.jasmine.rag

import android.widget.Toast
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.ProviderManager
import com.lhzkml.jasmine.RagStore
import com.lhzkml.jasmine.core.rag.IndexDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.lhzkml.jasmine.ui.components.CustomHorizontalDivider
import com.lhzkml.jasmine.ui.components.CustomSwitch
import com.lhzkml.jasmine.ui.components.CustomText
import com.lhzkml.jasmine.ui.components.CustomTextButton
import com.lhzkml.jasmine.ui.theme.*

class RagConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JasmineTheme {
                RagConfigScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
fun RagConfigScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var enabled by remember { mutableStateOf(ProviderManager.isRagEnabled(context)) }
    var topK by remember { mutableStateOf(ProviderManager.getRagTopK(context).toString()) }
    var baseUrl by remember { mutableStateOf(ProviderManager.getRagEmbeddingBaseUrl(context)) }
    var apiKey by remember { mutableStateOf(ProviderManager.getRagEmbeddingApiKey(context)) }
    var model by remember { mutableStateOf(ProviderManager.getRagEmbeddingModel(context)) }
    var isIndexing by remember { mutableStateOf(false) }
    var indexStatus by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            ProviderManager.setRagEnabled(context, enabled)
            ProviderManager.setRagTopK(context, topK.toIntOrNull()?.coerceIn(1, 32) ?: 5)
            ProviderManager.setRagEmbeddingBaseUrl(context, baseUrl.trim())
            ProviderManager.setRagEmbeddingApiKey(context, apiKey)
            ProviderManager.setRagEmbeddingModel(context, model.trim().ifBlank { "text-embedding-3-small" })
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
                .height(40.dp)
                .background(Color.White)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CustomTextButton(onClick = onBack, contentColor = TextPrimary, contentPadding = PaddingValues(6.dp)) {
                CustomText("<- 返回", fontSize = 14.sp, color = TextPrimary)
            }
            CustomText(
                text = "RAG 知识库",
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
            // 启用开关
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        CustomText(text = "启用 RAG 知识库", fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                        CustomText(
                            text = "根据用户问题检索相关文档并注入上下文",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    CustomSwitch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Accent,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = TextSecondary.copy(alpha = 0.5f)
                    )
                }
            }

            if (enabled) {
                // 配置区
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    CustomText(text = "Embedding 服务", fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                    CustomText(
                        text = "使用 OpenAI 兼容的 /v1/embeddings 接口",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                    )

                    CustomText(text = "API 地址", fontSize = 14.sp, color = TextPrimary)
                    CustomText(text = "例如 https://api.openai.com", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
                    RagTextField(value = baseUrl, onValueChange = { baseUrl = it }, placeholder = "https://api.openai.com")

                    Spacer(modifier = Modifier.height(16.dp))

                    CustomText(text = "API Key", fontSize = 14.sp, color = TextPrimary)
                    CustomText(text = "Bearer 认证", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
                    RagTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        placeholder = "sk-...",
                        keyboardType = KeyboardType.Password
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    CustomText(text = "模型名称", fontSize = 14.sp, color = TextPrimary)
                    CustomText(text = "如 text-embedding-3-small（384 维）", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
                    RagTextField(value = model, onValueChange = { model = it }, placeholder = "text-embedding-3-small")

                    Spacer(modifier = Modifier.height(16.dp))

                    CustomText(text = "检索数量 TopK", fontSize = 14.sp, color = TextPrimary)
                    CustomText(text = "返回最相关的文档条数 (1~32)", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
                    RagTextField(
                        value = topK,
                        onValueChange = { topK = it.filter { c -> c.isDigit() }.take(2) },
                        placeholder = "5",
                        keyboardType = KeyboardType.Number
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // 重建索引
                    CustomText(text = "工作区索引", fontSize = 14.sp, color = TextPrimary)
                    CustomText(
                        text = "扫描工作区文件并建立向量索引",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    CustomTextButton(
                        onClick = {
                            if (isIndexing) return@CustomTextButton
                            val wsPath = ProviderManager.getWorkspacePath(context)
                            if (wsPath.isBlank()) {
                                Toast.makeText(context, "请先在 Agent 模式下选择工作区", Toast.LENGTH_SHORT).show()
                                return@CustomTextButton
                            }
                            val root = File(wsPath)
                            if (!root.exists() || !root.isDirectory) {
                                Toast.makeText(context, "工作区路径无效", Toast.LENGTH_SHORT).show()
                                return@CustomTextButton
                            }
                            isIndexing = true
                            indexStatus = "正在扫描…"
                            CoroutineScope(Dispatchers.Main).launch {
                                val result = withContext(Dispatchers.IO) {
                                    runIndexing(context, wsPath)
                                }
                                isIndexing = false
                                indexStatus = result
                                Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                            }
                        },
                        contentColor = Accent,
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        CustomText(
                            text = if (isIndexing) "索引中…" else "重建索引",
                            fontSize = 14.sp,
                            color = Accent
                        )
                    }
                    indexStatus?.let { status ->
                        CustomText(
                            text = status,
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

private suspend fun runIndexing(
    context: android.content.Context,
    workspacePath: String
): String {
    val configProvider = {
        com.lhzkml.jasmine.core.rag.RagConfig(
            enabled = ProviderManager.isRagEnabled(context),
            topK = ProviderManager.getRagTopK(context),
            embeddingBaseUrl = ProviderManager.getRagEmbeddingBaseUrl(context),
            embeddingApiKey = ProviderManager.getRagEmbeddingApiKey(context),
            embeddingModel = ProviderManager.getRagEmbeddingModel(context)
        )
    }
    val indexingService = RagStore.buildIndexingService(configProvider)
        ?: return "请先配置 Embedding API 地址和 Key"
    val root = File(workspacePath)
    val extensions = setOf("kt", "java", "md", "txt", "py", "ts", "tsx", "js", "jsx", "json", "xml", "gradle", "kts")
    val documents = mutableListOf<IndexDocument>()
    root.walkTopDown()
        .maxDepth(4)
        .filter { it.isFile && it.extension.lowercase() in extensions }
        .forEach { file ->
            try {
                val content = file.readText(Charsets.UTF_8)
                if (content.isNotBlank()) {
                    val relativePath = file.relativeTo(root).path
                    documents.add(IndexDocument(relativePath, content))
                }
            } catch (_: Exception) { /* skip */ }
        }
    if (documents.isEmpty()) return "未找到可索引文件"
    val results = indexingService.indexDocuments(documents)
    val success = results.count { it.success }
    val totalChunks = results.sumOf { it.chunksIndexed }
    return "已索引 $success/${documents.size} 个文件，共 $totalChunks 个块"
}

@Composable
private fun RagTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Uri
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
