package com.lhzkml.jasmine.mnn

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.ui.components.*
import com.lhzkml.jasmine.ui.theme.BgPrimary
import com.lhzkml.jasmine.ui.theme.BgInput
import com.lhzkml.jasmine.ui.theme.JasmineTheme
import com.lhzkml.jasmine.ui.theme.TextPrimary
import com.lhzkml.jasmine.ui.theme.TextSecondary
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ExportImportProgress(val progress: Float, val message: String)

class MnnManagementActivity : ComponentActivity() {
    private var refreshCallback: (() -> Unit)? = null
    private val progressState = mutableStateOf<ExportImportProgress?>(null)

    private fun updateProgress(progress: Float, message: String) {
        runOnUiThread { progressState.value = ExportImportProgress(progress, message) }
    }

    private fun clearProgress() {
        runOnUiThread { progressState.value = null }
    }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri ?: return@registerForActivityResult
        val modelId = pendingExportModelId ?: return@registerForActivityResult
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                updateProgress(0f, "正在打包…")
                val zip = MnnModelManager.createModelZip(this@MnnManagementActivity, modelId) { p, msg ->
                    updateProgress(p * 0.9f, msg)
                }
                if (zip != null) {
                    val zipSize = zip.length()
                    contentResolver.openOutputStream(uri)?.use { out ->
                        zip.inputStream().use { input ->
                            val buf = ByteArray(64 * 1024)
                            var read: Int
                            var written = 0L
                            while (input.read(buf).also { read = it } != -1) {
                                out.write(buf, 0, read)
                                written += read
                                if (zipSize > 0) {
                                    val p = 0.9f + 0.1f * (written.toFloat() / zipSize)
                                    updateProgress(p, "正在写入… ${MnnModelManager.formatSize(written)} / ${MnnModelManager.formatSize(zipSize)}")
                                }
                            }
                        }
                    }
                    zip.delete()
                    clearProgress()
                    runOnUiThread {
                        Toast.makeText(this@MnnManagementActivity, "模型已导出", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    clearProgress()
                    runOnUiThread {
                        Toast.makeText(this@MnnManagementActivity, "导出失败：模型不存在", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                clearProgress()
                runOnUiThread {
                    Toast.makeText(this@MnnManagementActivity, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val importFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            updateProgress(0f, "正在导入…")
            try {
                val result = MnnModelManager.importModelFromTree(this@MnnManagementActivity, uri) { p, msg ->
                    updateProgress(p, msg)
                }
                clearProgress()
                if (result != null) {
                    Toast.makeText(this@MnnManagementActivity, "已导入模型: $result", Toast.LENGTH_SHORT).show()
                    refreshCallback?.invoke()
                } else {
                    Toast.makeText(this@MnnManagementActivity, "导入失败：未找到有效模型目录", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                clearProgress()
                Toast.makeText(this@MnnManagementActivity, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val importZipLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            updateProgress(0f, "正在导入…")
            try {
                val result = MnnModelManager.importModelFromZip(this@MnnManagementActivity, uri) { p, msg ->
                    updateProgress(p, msg)
                }
                clearProgress()
                if (result != null) {
                    Toast.makeText(this@MnnManagementActivity, "已导入模型: $result", Toast.LENGTH_SHORT).show()
                    refreshCallback?.invoke()
                } else {
                    Toast.makeText(this@MnnManagementActivity, "导入失败：无效的模型压缩包", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                clearProgress()
                Toast.makeText(this@MnnManagementActivity, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var pendingExportModelId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JasmineTheme {
                val progress by progressState
                MnnManagementScreen(
                    onBack = { finish() },
                    onRefreshCallbackSet = { refreshCallback = it },
                    onExportModel = { modelId ->
                        pendingExportModelId = modelId
                        val dirName = if (modelId.contains("/")) MnnModelManager.safeModelId(modelId) else modelId
                        exportLauncher.launch("${dirName}.zip")
                    },
                    onImportFolder = { importFolderLauncher.launch(null) },
                    onImportZip = { importZipLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed")) },
                    progressState = progress
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshCallback?.invoke()
    }
}

@Composable
fun MnnManagementScreen(
    onBack: () -> Unit,
    onRefreshCallbackSet: ((() -> Unit) -> Unit)? = null,
    onExportModel: ((String) -> Unit)? = null,
    onImportFolder: (() -> Unit)? = null,
    onImportZip: (() -> Unit)? = null,
    progressState: ExportImportProgress? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var models by remember { mutableStateOf(MnnModelManager.getLocalModels(context)) }
    var showDeleteDialog by remember { mutableStateOf<MnnModelInfo?>(null) }
    var showSourceDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    val downloadManager = remember { MnnDownloadManager.getInstance(context) }
    var currentSource by remember { mutableStateOf(downloadManager.getDownloadSource()) }

    val refresh: () -> Unit = {
        models = MnnModelManager.getLocalModels(context)
        currentSource = downloadManager.getDownloadSource()
    }

    LaunchedEffect(Unit) { onRefreshCallbackSet?.invoke(refresh) }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(BgPrimary)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(40.dp)
                .background(Color.White).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CustomTextButton(onClick = onBack, contentColor = TextPrimary) {
                CustomText("← 返回", fontSize = 14.sp, color = TextPrimary)
            }
            CustomText(
                text = "本地 MNN 模型", fontSize = 17.sp, color = TextPrimary,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(56.dp))
        }
        CustomHorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp)

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                MnnSettingsItem(
                    title = "模型市场",
                    subtitle = "浏览和下载 MNN 模型",
                    value = "进入",
                    onClick = {
                        context.startActivity(Intent(context, MnnModelMarketActivity::class.java))
                    }
                )
            }
            item {
                MnnSettingsItem(
                    title = "下载源",
                    subtitle = "选择模型下载渠道",
                    value = currentSource.displayName,
                    onClick = { showSourceDialog = true }
                )
            }
            item {
                MnnSettingsItem(
                    title = "默认推理设置",
                    subtitle = "最大Token数、采样器、Temperature 等",
                    value = "配置",
                    onClick = {
                        context.startActivity(
                            Intent(context, MnnModelSettingsActivity::class.java)
                                .putExtra("modelId", "__global_defaults__")
                        )
                    }
                )
            }
            item {
                MnnSettingsItem(
                    title = "导入本地模型",
                    subtitle = "从文件夹或 zip 导入 MNN 模型",
                    value = "导入",
                    onClick = { showImportDialog = true }
                )
            }
            item {
                MnnSettingsItem(
                    title = "本地模型",
                    value = if (models.isEmpty()) "暂无" else "${models.size} 个"
                )
            }

            if (models.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CustomText("暂无本地模型", fontSize = 15.sp, color = TextSecondary)
                            Spacer(modifier = Modifier.height(6.dp))
                            CustomText("前往模型市场下载模型", fontSize = 13.sp, color = TextSecondary)
                        }
                    }
                }
            } else {
                items(models, key = { it.modelId }) { model ->
                    MnnModelItem(
                        model = model,
                        onExportClick = { onExportModel?.invoke(model.modelId) },
                        onDeleteClick = { showDeleteDialog = model }
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }

    showDeleteDialog?.let { model ->
        CustomAlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = {
                CustomText("删除模型", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                CustomText("确定要删除模型 ${model.modelName} 吗？此操作不可恢复。", fontSize = 14.sp, color = TextPrimary)
            },
            confirmButton = {
                CustomTextButton(onClick = {
                    MnnModelManager.deleteModel(context, model.modelId)
                    showDeleteDialog = null
                    refresh()
                }, contentColor = Color(0xFFF44336)) {
                    CustomText("删除", fontSize = 14.sp, color = Color(0xFFF44336))
                }
            },
            dismissButton = {
                CustomTextButton(onClick = { showDeleteDialog = null }, contentColor = TextSecondary) {
                    CustomText("取消", fontSize = 14.sp, color = TextSecondary)
                }
            }
        )
    }

    if (progressState != null) {
        MnnProgressDialog(progress = progressState.progress, message = progressState.message)
    }

    if (showImportDialog) {
        MnnImportDialog(
            onImportFolder = {
                showImportDialog = false
                onImportFolder?.invoke()
            },
            onImportZip = {
                showImportDialog = false
                onImportZip?.invoke()
            },
            onDismiss = { showImportDialog = false }
        )
    }

    if (showSourceDialog) {
        MnnSourceSelectionDialog(
            currentSource = currentSource,
            onSourceSelected = { source ->
                downloadManager.setDownloadSource(source)
                currentSource = source
                showSourceDialog = false
            },
            onDismiss = { showSourceDialog = false }
        )
    }
}

@Composable
fun MnnProgressDialog(progress: Float, message: String) {
    CustomDialog(onDismissRequest = {}) {
        Column(modifier = Modifier.fillMaxWidth()) {
            CustomText(message, fontSize = 15.sp, color = TextPrimary)
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = TextPrimary,
                trackColor = BgInput
            )
            Spacer(modifier = Modifier.height(8.dp))
            CustomText("${(progress * 100).toInt()}%", fontSize = 13.sp, color = TextSecondary)
        }
    }
}

@Composable
fun MnnSourceSelectionDialog(
    currentSource: MnnDownloadSource,
    onSourceSelected: (MnnDownloadSource) -> Unit,
    onDismiss: () -> Unit
) {
    CustomDialog(onDismissRequest = onDismiss) {
        Column {
            CustomText("选择下载源", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(16.dp))
            MnnDownloadSource.entries.forEach { source ->
                val selected = source == currentSource
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) BgInput else Color.Transparent)
                        .clickable { onSourceSelected(source) }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CustomRadioButton(selected = selected, onClick = { onSourceSelected(source) })
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            CustomText(source.displayName, fontSize = 15.sp, color = TextPrimary)
                            val desc = when (source) {
                                MnnDownloadSource.HUGGING_FACE -> "huggingface.co"
                                MnnDownloadSource.MODEL_SCOPE -> "modelscope.cn"
                                MnnDownloadSource.MODELERS -> "modelers.cn"
                            }
                            CustomText(desc, fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MnnModelItem(
    model: MnnModelInfo,
    onExportClick: (() -> Unit)? = null,
    onDeleteClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.White)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                CustomText(model.modelName, fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(2.dp))
                CustomText(MnnModelManager.formatSize(model.sizeBytes), fontSize = 12.sp, color = TextSecondary)
            }
            if (onExportClick != null) {
                CustomTextButton(onClick = onExportClick, contentColor = TextPrimary,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                    CustomText("导出", fontSize = 13.sp, color = TextPrimary)
                }
            }
            CustomTextButton(onClick = onDeleteClick, contentColor = Color(0xFFF44336),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                CustomText("删除", fontSize = 13.sp, color = Color(0xFFF44336))
            }
        }
    }
}

@Composable
fun MnnImportDialog(
    onImportFolder: () -> Unit,
    onImportZip: () -> Unit,
    onDismiss: () -> Unit
) {
    CustomDialog(onDismissRequest = onDismiss) {
        Column {
            CustomText("导入本地模型", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(BgInput)
                    .clickable { onImportFolder() }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Column {
                    CustomText("从文件夹导入", fontSize = 15.sp, color = TextPrimary)
                    CustomText("选择包含 config.json 和 .mnn 文件的模型目录", fontSize = 12.sp, color = TextSecondary)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(BgInput)
                    .clickable { onImportZip() }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Column {
                    CustomText("从 zip 导入", fontSize = 15.sp, color = TextPrimary)
                    CustomText("选择导出的模型压缩包", fontSize = 12.sp, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
fun MnnSettingsItem(
    title: String, subtitle: String? = null, value: String = "", onClick: (() -> Unit)? = null
) {
    val modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.White)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                CustomText(title, fontSize = 15.sp, color = TextPrimary)
                if (subtitle != null) CustomText(subtitle, fontSize = 12.sp, color = TextSecondary)
            }
            if (value.isNotEmpty()) {
                CustomText(value, fontSize = 13.sp, color = TextSecondary, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 150.dp).padding(end = 8.dp))
            }
            if (onClick != null) CustomText("›", fontSize = 18.sp, color = TextSecondary)
        }
    }
}
