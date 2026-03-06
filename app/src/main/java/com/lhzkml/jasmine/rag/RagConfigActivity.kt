package com.lhzkml.jasmine.rag

import android.widget.Toast
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
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
import com.lhzkml.jasmine.core.config.RagLibraryConfig
import com.lhzkml.jasmine.rag.RagLibraryContentActivity
import com.lhzkml.jasmine.core.rag.IndexDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
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
    var isIndexing by remember { mutableStateOf(false) }
    var indexStatus by remember { mutableStateOf<String?>(null) }
    var manualSaveStatus by remember { mutableStateOf<String?>(null) }

    var libraries by remember { mutableStateOf(ProviderManager.getRagLibraries(context)) }
    var activeIds by remember { mutableStateOf(ProviderManager.getRagActiveLibraryIds(context)) }
    var showAddLibraryDialog by remember { mutableStateOf(false) }
    var newLibName by remember { mutableStateOf("") }
    var newLibDesc by remember { mutableStateOf("") }
    var selectedLibForManual by remember { mutableStateOf(libraries.firstOrNull()?.id ?: "default") }
    var manualTitle by remember { mutableStateOf("") }
    var manualContent by remember { mutableStateOf("") }
    var workspaceTargetLib by remember { mutableStateOf(libraries.firstOrNull()?.id ?: "default") }
    var indexableExtensions by remember { mutableStateOf(ProviderManager.getRagIndexableExtensions(context).toMutableList().sorted()) }
    var showAddExtensionDialog by remember { mutableStateOf(false) }
    var newExtension by remember { mutableStateOf("") }

    LaunchedEffect(libraries) {
        if (selectedLibForManual !in libraries.map { it.id }) selectedLibForManual = libraries.firstOrNull()?.id ?: "default"
        if (workspaceTargetLib !in libraries.map { it.id }) workspaceTargetLib = libraries.firstOrNull()?.id ?: "default"
    }

    fun persistLibraries() {
        ProviderManager.setRagLibraries(context, libraries)
        ProviderManager.setRagActiveLibraryIds(context, activeIds)
    }

    fun persistExtensions() {
        ProviderManager.setRagIndexableExtensions(context, indexableExtensions.toSet())
    }

    DisposableEffect(Unit) {
        onDispose {
            ProviderManager.setRagEnabled(context, enabled)
            ProviderManager.setRagTopK(context, topK.toIntOrNull()?.coerceIn(1, 32) ?: 5)
            persistLibraries()
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
                    CustomText(
                        text = "Embedding 服务请在「设置」-「模型供应商」-「Embedding 服务」中配置",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    CustomText(text = "检索数量 TopK", fontSize = 14.sp, color = TextPrimary)
                    CustomText(text = "返回最相关的文档条数 (1~32)", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
                    RagTextField(
                        value = topK,
                        onValueChange = { topK = it.filter { c -> c.isDigit() }.take(2) },
                        placeholder = "5",
                        keyboardType = KeyboardType.Number
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // 知识库管理
                    CustomText(text = "知识库管理", fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                    CustomText(
                        text = "不同知识库用于区分用途，勾选「参与检索」的库会在对话时被搜索",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    libraries.forEach { lib ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CustomSwitch(
                                checked = lib.id in activeIds,
                                onCheckedChange = {
                                    activeIds = if (it) activeIds + lib.id else activeIds - lib.id
                                    persistLibraries()
                                },
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Accent,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = TextSecondary.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                CustomText(text = lib.name, fontSize = 14.sp, color = TextPrimary)
                                if (lib.description.isNotBlank()) {
                                    CustomText(text = lib.description, fontSize = 11.sp, color = TextSecondary)
                                }
                            }
                            CustomTextButton(
                                onClick = { RagLibraryContentActivity.start(context, lib.id, lib.name) },
                                contentColor = Accent,
                                contentPadding = PaddingValues(6.dp)
                            ) {
                                CustomText("查看", fontSize = 12.sp, color = Accent)
                            }
                            if (lib.id != "default") {
                                CustomTextButton(
                                    onClick = {
                                        libraries = libraries.filter { it.id != lib.id }
                                        activeIds = activeIds - lib.id
                                        persistLibraries()
                                    },
                                    contentColor = Color(0xFFE53935),
                                    contentPadding = PaddingValues(8.dp)
                                ) {
                                    CustomText("删除", fontSize = 12.sp, color = Color(0xFFE53935))
                                }
                            }
                        }
                    }
                    CustomTextButton(
                        onClick = { showAddLibraryDialog = true },
                        contentColor = Accent,
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        CustomText("+ 添加知识库", fontSize = 14.sp, color = Accent)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 手动添加知识
                    CustomText(text = "手动添加知识", fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                    CustomText(
                        text = "直接输入内容保存到指定知识库",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                    CustomText(text = "目标知识库", fontSize = 14.sp, color = TextPrimary)
                    libraries.forEach { lib ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedLibForManual = lib.id }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .border(2.dp, if (selectedLibForManual == lib.id) Accent else Color(0xFFE8E8E8), RoundedCornerShape(9.dp))
                                    .background(if (selectedLibForManual == lib.id) Accent else Color.Transparent)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            CustomText(text = lib.name, fontSize = 14.sp, color = TextPrimary)
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(8.dp))
                    ) {
                        BasicTextField(
                            value = manualTitle,
                            onValueChange = { manualTitle = it },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            textStyle = TextStyle(fontSize = 14.sp, color = TextPrimary),
                            cursorBrush = SolidColor(TextPrimary),
                            decorationBox = { inner ->
                                Box {
                                    if (manualTitle.isEmpty()) CustomText("标题（可选）", fontSize = 14.sp, color = TextSecondary)
                                    inner()
                                }
                            }
                        )
                        CustomHorizontalDivider(color = Color(0xFFE8E8E8), thickness = 1.dp)
                        BasicTextField(
                            value = manualContent,
                            onValueChange = { manualContent = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(12.dp),
                            textStyle = TextStyle(fontSize = 14.sp, color = TextPrimary),
                            cursorBrush = SolidColor(TextPrimary),
                            decorationBox = { inner ->
                                Box {
                                    if (manualContent.isEmpty()) CustomText("知识内容…", fontSize = 14.sp, color = TextSecondary)
                                    inner()
                                }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        CustomTextButton(
                            onClick = {
                                if (manualContent.isBlank()) {
                                    manualSaveStatus = "请输入知识内容"
                                    return@CustomTextButton
                                }
                                manualSaveStatus = null
                                CoroutineScope(Dispatchers.Main).launch {
                                    val msg = withContext(Dispatchers.IO) {
                                        saveManualEntry(context, selectedLibForManual, manualTitle.ifBlank { "manual_${System.currentTimeMillis()}" }, manualContent)
                                    }
                                    manualSaveStatus = msg
                                    if (msg.startsWith("已保存")) {
                                        manualTitle = ""
                                        manualContent = ""
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            contentColor = Accent,
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            CustomText("保存到知识库", fontSize = 14.sp, color = Accent)
                        }
                        manualSaveStatus?.let { CustomText(it, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(12.dp)) }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 可索引扩展名
                    CustomText(text = "可索引扩展名", fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                    CustomText(
                        text = "工作区索引时仅扫描以下扩展名的文件，可添加或删除",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                    indexableExtensions.sorted().chunked(6).forEach { rowExts ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            rowExts.forEach { ext ->
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(TextSecondary.copy(alpha = 0.15f))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CustomText(text = ".$ext", fontSize = 12.sp, color = TextPrimary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    CustomTextButton(
                                        onClick = {
                                            indexableExtensions = indexableExtensions - ext
                                            persistExtensions()
                                        },
                                        contentColor = Color(0xFFE53935),
                                        contentPadding = PaddingValues(2.dp)
                                    ) {
                                        CustomText("×", fontSize = 14.sp, color = Color(0xFFE53935))
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    CustomTextButton(
                        onClick = { showAddExtensionDialog = true },
                        contentColor = Accent,
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        CustomText("+ 添加扩展名", fontSize = 14.sp, color = Accent)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 工作区索引
                    CustomText(text = "工作区索引", fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                    CustomText(
                        text = "扫描工作区文件并建立向量索引，可指定目标知识库",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                    CustomText(text = "目标知识库", fontSize = 14.sp, color = TextPrimary)
                    libraries.forEach { lib ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { workspaceTargetLib = lib.id }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .border(2.dp, if (workspaceTargetLib == lib.id) Accent else Color(0xFFE8E8E8), RoundedCornerShape(9.dp))
                                    .then(
                                        if (workspaceTargetLib == lib.id) Modifier.background(Accent) else Modifier
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            CustomText(text = lib.name, fontSize = 14.sp, color = TextPrimary)
                        }
                    }
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
                                    runIndexing(context, wsPath, workspaceTargetLib)
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

    if (showAddExtensionDialog) {
        AlertDialog(
            onDismissRequest = { showAddExtensionDialog = false; newExtension = "" },
            title = { CustomText("添加扩展名", fontSize = 16.sp, color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    CustomText("扩展名（不含点号，如 kotlin 输入 kt）", fontSize = 14.sp, color = TextPrimary)
                    RagTextField(
                        value = newExtension,
                        onValueChange = { newExtension = it.filter { c -> c.isLetterOrDigit() }.lowercase().take(16) },
                        placeholder = "例如: kt"
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ext = newExtension.trim().lowercase()
                        if (ext.isBlank()) {
                            Toast.makeText(context, "请输入扩展名", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        if (ext in indexableExtensions) {
                            Toast.makeText(context, "已存在", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        indexableExtensions = (indexableExtensions + ext).distinct().sorted()
                        persistExtensions()
                        showAddExtensionDialog = false
                        newExtension = ""
                        Toast.makeText(context, "已添加 .$ext", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    CustomText("添加", fontSize = 14.sp, color = Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddExtensionDialog = false; newExtension = "" }) {
                    CustomText("取消", fontSize = 14.sp, color = TextSecondary)
                }
            }
        )
    }

    if (showAddLibraryDialog) {
        AlertDialog(
            onDismissRequest = { showAddLibraryDialog = false },
            title = { CustomText("添加知识库", fontSize = 16.sp, color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    CustomText("名称", fontSize = 14.sp, color = TextPrimary)
                    RagTextField(value = newLibName, onValueChange = { newLibName = it }, placeholder = "例如：项目文档")
                    Spacer(Modifier.height(12.dp))
                    CustomText("用途描述", fontSize = 14.sp, color = TextPrimary)
                    RagTextField(value = newLibDesc, onValueChange = { newLibDesc = it }, placeholder = "可选")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newLibName.trim()
                        if (name.isBlank()) {
                            Toast.makeText(context, "请输入名称", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        val id = "lib_${name.replace(Regex("[^a-zA-Z0-9\u4e00-\u9fa5]"), "_").lowercase()}_${UUID.randomUUID().toString().take(8)}"
                        libraries = libraries + RagLibraryConfig(id, name, newLibDesc.trim())
                        persistLibraries()
                        showAddLibraryDialog = false
                        newLibName = ""
                        newLibDesc = ""
                        Toast.makeText(context, "已添加", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    CustomText("添加", fontSize = 14.sp, color = Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddLibraryDialog = false }) {
                    CustomText("取消", fontSize = 14.sp, color = TextSecondary)
                }
            }
        )
    }
}

private suspend fun saveManualEntry(
    context: android.content.Context,
    libraryId: String,
    sourceId: String,
    content: String
): String {
    val configProvider = {
        com.lhzkml.jasmine.core.rag.RagConfig(
            enabled = ProviderManager.isRagEnabled(context),
            topK = ProviderManager.getRagTopK(context),
            embeddingBaseUrl = ProviderManager.getRagEmbeddingBaseUrl(context),
            embeddingApiKey = ProviderManager.getRagEmbeddingApiKey(context),
            embeddingModel = ProviderManager.getRagEmbeddingModel(context),
            activeLibraryIds = ProviderManager.getRagActiveLibraryIds(context)
        )
    }
    val indexingService = RagStore.buildIndexingService(configProvider)
        ?: return "请先配置 Embedding API 地址和 Key"
    return try {
        val result = indexingService.indexDocument(IndexDocument(sourceId, content, libraryId))
        if (result.success) "已保存，${result.chunksIndexed} 个块已索引" else "保存失败: ${result.error}"
    } catch (e: Exception) {
        "保存失败: ${e.message}"
    }
}

private suspend fun runIndexing(
    context: android.content.Context,
    workspacePath: String,
    libraryId: String = "default"
): String {
    val configProvider = {
        com.lhzkml.jasmine.core.rag.RagConfig(
            enabled = ProviderManager.isRagEnabled(context),
            topK = ProviderManager.getRagTopK(context),
            embeddingBaseUrl = ProviderManager.getRagEmbeddingBaseUrl(context),
            embeddingApiKey = ProviderManager.getRagEmbeddingApiKey(context),
            embeddingModel = ProviderManager.getRagEmbeddingModel(context),
            activeLibraryIds = ProviderManager.getRagActiveLibraryIds(context)
        )
    }
    val indexingService = RagStore.buildIndexingService(configProvider)
        ?: return "请先配置 Embedding API 地址和 Key"
    val root = File(workspacePath)
    val extensions = ProviderManager.getRagIndexableExtensions(context)
    val documents = mutableListOf<IndexDocument>()
    root.walkTopDown()
        .maxDepth(12)
        .filter { it.isFile && it.extension.lowercase() in extensions }
        .forEach { file ->
            try {
                val content = file.readText(Charsets.UTF_8)
                if (content.isNotBlank()) {
                    val relativePath = file.relativeTo(root).path
                    documents.add(IndexDocument(relativePath, content, libraryId = libraryId))
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
