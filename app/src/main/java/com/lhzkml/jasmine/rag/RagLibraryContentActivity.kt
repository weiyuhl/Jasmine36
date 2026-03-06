package com.lhzkml.jasmine.rag

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.ProviderManager
import com.lhzkml.jasmine.RagStore
import com.lhzkml.jasmine.core.rag.IndexDocument
import com.lhzkml.jasmine.core.rag.SourceSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.lhzkml.jasmine.ui.components.CustomText
import com.lhzkml.jasmine.ui.components.CustomTextButton
import com.lhzkml.jasmine.ui.theme.*

class RagLibraryContentActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val libraryId = intent.getStringExtra("library_id") ?: run { finish(); return }
        val libraryName = intent.getStringExtra("library_name") ?: libraryId

        setContent {
            JasmineTheme {
                RagLibraryContentScreen(
                    libraryId = libraryId,
                    libraryName = libraryName,
                    onBack = { finish() }
                )
            }
        }
    }

    companion object {
        fun start(context: android.content.Context, libraryId: String, libraryName: String) {
            context.startActivity(Intent(context, RagLibraryContentActivity::class.java).apply {
                putExtra("library_id", libraryId)
                putExtra("library_name", libraryName)
            })
        }
    }
}

@Composable
fun RagLibraryContentScreen(
    libraryId: String,
    libraryName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val index = RagStore.getKnowledgeIndex()

    var items by remember { mutableStateOf<List<SourceSummary>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var editTarget by remember { mutableStateOf<SourceSummary?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editContent by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<SourceSummary?>(null) }

    fun loadItems() {
        if (index == null) return
        isLoading = true
        CoroutineScope(Dispatchers.Main).launch {
            items = withContext(Dispatchers.IO) { index.listSources(libraryId) }
            isLoading = false
        }
    }

    LaunchedEffect(libraryId) { loadItems() }

    LaunchedEffect(editTarget) {
        editTarget?.let { target ->
            if (editContent.isEmpty() && index != null) {
                val chunks = withContext(Dispatchers.IO) { index.getChunksBySourceId(target.sourceId) }
                editContent = chunks.sortedBy { it.id }.joinToString("\n") { it.content }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(BgPrimary)) {
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
                text = "$libraryName · 知识内容",
                fontSize = 17.sp,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
        }

        if (index == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CustomText("知识索引未初始化", fontSize = 14.sp, color = TextSecondary)
            }
        } else if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CustomText("加载中…", fontSize = 14.sp, color = TextSecondary)
            }
        } else if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CustomText("暂无知识内容", fontSize = 14.sp, color = TextSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items, key = { it.sourceId }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(8.dp))
                            .clickable {
                                editTarget = item
                                editTitle = item.sourceId
                                editContent = "" // will load in LaunchedEffect
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            CustomText(
                                text = item.sourceId,
                                fontSize = 14.sp,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            CustomText(
                                text = item.preview.ifBlank { "（空）" },
                                fontSize = 12.sp,
                                color = TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            CustomText(
                                text = "${item.chunkCount} 个块",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        CustomTextButton(
                            onClick = {
                                editTarget = item
                                editTitle = item.sourceId
                                editContent = ""
                            },
                            contentColor = Accent,
                            contentPadding = PaddingValues(6.dp)
                        ) {
                            CustomText("编辑", fontSize = 12.sp, color = Accent)
                        }
                        CustomTextButton(
                            onClick = { deleteTarget = item },
                            contentColor = Color(0xFFE53935),
                            contentPadding = PaddingValues(6.dp)
                        ) {
                            CustomText("删除", fontSize = 12.sp, color = Color(0xFFE53935))
                        }
                    }
                }
            }
        }
    }

    editTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { editTarget = null },
            title = { CustomText("编辑知识", fontSize = 16.sp, color = TextPrimary) },
            text = {
                Column {
                    CustomText("标题（来源ID）", fontSize = 14.sp, color = TextPrimary, modifier = Modifier.padding(bottom = 4.dp))
                    BasicTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .background(Color.White, RoundedCornerShape(4.dp))
                            .padding(8.dp),
                        textStyle = TextStyle(fontSize = 14.sp, color = TextPrimary),
                        cursorBrush = SolidColor(TextPrimary)
                    )
                    CustomText("内容", fontSize = 14.sp, color = TextPrimary, modifier = Modifier.padding(bottom = 4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(Color.White, RoundedCornerShape(4.dp))
                    ) {
                        BasicTextField(
                            value = editContent,
                            onValueChange = { editContent = it },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState()),
                            textStyle = TextStyle(fontSize = 14.sp, color = TextPrimary),
                            cursorBrush = SolidColor(TextPrimary)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val title = editTitle.trim()
                    val content = editContent.trim()
                    if (content.isBlank()) {
                        Toast.makeText(context, "内容不能为空", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    CoroutineScope(Dispatchers.Main).launch {
                        val msg = withContext(Dispatchers.IO) {
                            val idx = RagStore.getKnowledgeIndex() ?: return@withContext "索引未初始化"
                            idx.deleteBySourceId(target.sourceId)
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
                            val svc = RagStore.buildIndexingService(configProvider)
                                ?: return@withContext "请先配置 Embedding"
                            val result = svc.indexDocument(IndexDocument(title.ifBlank { target.sourceId }, content, libraryId))
                            if (result.success) "已保存" else "保存失败: ${result.error}"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        editTarget = null
                        loadItems()
                    }
                }) {
                    CustomText("保存", fontSize = 14.sp, color = Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { editTarget = null }) {
                    CustomText("取消", fontSize = 14.sp, color = TextSecondary)
                }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { CustomText("删除知识", fontSize = 16.sp, color = TextPrimary) },
            text = {
                CustomText(
                    "确定删除「${target.sourceId}」？共 ${target.chunkCount} 个块。",
                    fontSize = 14.sp,
                    color = TextPrimary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    CoroutineScope(Dispatchers.Main).launch {
                        withContext(Dispatchers.IO) {
                            index?.deleteBySourceId(target.sourceId)
                        }
                        Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                        deleteTarget = null
                        loadItems()
                    }
                }) {
                    CustomText("删除", fontSize = 14.sp, color = Color(0xFFE53935))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    CustomText("取消", fontSize = 14.sp, color = TextSecondary)
                }
            }
        )
    }
}
