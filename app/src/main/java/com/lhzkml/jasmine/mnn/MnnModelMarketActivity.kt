package com.lhzkml.jasmine.mnn

import android.os.Bundle
import android.widget.Toast
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import kotlinx.coroutines.launch

class MnnModelMarketActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            android.util.Log.e("MnnMarket", "Uncaught exception", throwable)
            try {
                val dir = File(filesDir, "mnn_debug_logs")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, "crash_${System.currentTimeMillis()}.txt")
                file.writeText("${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\n${throwable.stackTraceToString()}")
            } catch (_: Exception) {}
            runOnUiThread { finish() }
        }

        setContent { JasmineTheme { MnnModelMarketScreen(onBack = { finish() }) } }
    }
}

@Composable
fun MnnModelMarketScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showErrorFallback by remember { mutableStateOf(false) }

    fun writeErrorToFile(tag: String, error: String) {
        try {
            val dir = File(context.filesDir, "mnn_debug_logs")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "error_${System.currentTimeMillis()}.txt")
            file.writeText("[$tag] ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\n$error")
        } catch (_: Exception) {}
    }

    if (showErrorFallback) {
    Column(
            modifier = Modifier.fillMaxSize().background(BgPrimary).padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CustomText("模型市场出现错误", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            CustomText("错误已记录到 mnn_debug_logs 文件夹", fontSize = 13.sp, color = TextSecondary)
            Spacer(Modifier.height(16.dp))
            CustomButton(onClick = onBack) {
                CustomText("返回", fontSize = 14.sp, color = Color.White)
            }
        }
        return
    }

    val downloadManager = remember {
        try { MnnDownloadManager.getInstance(context) }
        catch (e: Exception) {
            writeErrorToFile("DownloadManager", e.stackTraceToString())
            showErrorFallback = true
            null
        }
    } ?: return

    var marketData by remember { mutableStateOf<MnnMarketData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var filterState by remember { mutableStateOf(MnnFilterState()) }
    var showFilterDialog by remember { mutableStateOf(false) }
    val downloadTasks by downloadManager.tasks.collectAsState()

    LaunchedEffect(Unit) {
        isLoading = true
        try {
            val data = MnnModelManager.fetchMarketData(context)
            if (data != null) marketData = data else errorMsg = "无法加载模型市场数据，请检查网络"
        } catch (e: Exception) {
            errorMsg = "加载失败:\n${e.stackTraceToString().take(800)}"
        }
        isLoading = false
    }

    fun getModelDownloadState(modelId: String): MnnDownloadState =
        downloadManager.getDownloadState(modelId)

    val currentSource = downloadManager.getDownloadSource()
    val sourceKey = when (currentSource) {
        MnnDownloadSource.HUGGING_FACE -> "HuggingFace"
        MnnDownloadSource.MODEL_SCOPE -> "ModelScope"
        MnnDownloadSource.MODELERS -> "Modelers"
    }

    val filteredModels = remember(marketData, searchQuery, filterState, downloadTasks, currentSource) {
        val all = marketData?.models ?: emptyList()
        all.filter { model ->
            val matchSource = model.sources.containsKey(sourceKey)
            val matchSearch = searchQuery.isBlank() ||
                model.modelName.contains(searchQuery, ignoreCase = true) ||
                model.vendor.contains(searchQuery, ignoreCase = true)
            val matchTag = filterState.tags.isEmpty() ||
                model.tags.any { it in filterState.tags }
            val matchSize = filterState.sizeCategories.isEmpty() ||
                model.sizeCategory in filterState.sizeCategories
            val matchVendor = filterState.vendors.isEmpty() ||
                model.vendor in filterState.vendors
            val matchDownload = filterState.downloadState == null || when (filterState.downloadState) {
                MnnDownloadFilterState.DOWNLOADED -> getModelDownloadState(model.modelId) == MnnDownloadState.DOWNLOADED
                MnnDownloadFilterState.NOT_DOWNLOADED -> getModelDownloadState(model.modelId) == MnnDownloadState.NOT_DOWNLOADED
                MnnDownloadFilterState.DOWNLOADING -> {
                    val s = getModelDownloadState(model.modelId)
                    s == MnnDownloadState.DOWNLOADING || s == MnnDownloadState.PAUSED
                }
                else -> true
            }
            matchSource && matchSearch && matchTag && matchSize && matchVendor && matchDownload
        }
    }

    val allTags = remember(marketData) {
        listOf("代码", "视频理解", "图像理解", "音频理解", "数学", "文生图",
            "音频生成", "深度思考") +
            (marketData?.quickFilterTags?.map { marketData?.tagTranslations?.get(it) ?: it }
                ?: emptyList())
    }.distinct()

    val sizeOptions = listOf("<1B", "1B-5B", "5B-15B", ">15B")

    val allVendors = remember(marketData) {
        listOf("Qwen", "Llama", "MobileLLM", "01.AI", "DeepSeek", "FastVLM",
            "TinyLlama", "Gemma", "MiMo", "Smol", "Baichuan", "stability.ai",
            "Phi", "THUDM", "InternLM", "Hunyuan", "MiniCPM", "GPT") +
            (marketData?.vendorOrder ?: emptyList())
    }.distinct()

    Column(modifier = Modifier.fillMaxSize().background(BgPrimary)) {
        // 顶部栏
        Row(
            modifier = Modifier.fillMaxWidth().height(40.dp)
                .background(Color.White).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CustomTextButton(onClick = onBack, contentColor = TextPrimary) {
                CustomText("← 返回", fontSize = 14.sp, color = TextPrimary)
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                CustomText(
                    "模型市场", fontSize = 17.sp, color = TextPrimary,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                )
            CustomText(
                    "下载源: ${currentSource.displayName}", fontSize = 11.sp, color = TextSecondary
                )
            }
            CustomTextButton(onClick = {
                scope.launch {
                    isLoading = true
                    marketData = MnnModelManager.fetchMarketData(context, forceRefresh = true)
                    isLoading = false
                }
            }, contentColor = TextPrimary) {
                CustomText("刷新", fontSize = 14.sp, color = TextPrimary)
            }
        }
        CustomHorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp)
        
        // 搜索栏 + 筛选按钮
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                CustomOutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { CustomText("搜索模型…", fontSize = 14.sp, color = TextSecondary) }
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (filterState.isEmpty) BgInput else TextPrimary)
                    .clickable { showFilterDialog = true }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CustomText(
                        "筛选", fontSize = 14.sp,
                        color = if (filterState.isEmpty) TextPrimary else Color.White
                    )
                    if (!filterState.isEmpty) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier.size(18.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(Color.White),
            contentAlignment = Alignment.Center
                        ) {
                            CustomText(
                                "${filterState.activeCount}", fontSize = 10.sp,
                                color = TextPrimary, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 内容
        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CustomText("加载中…", fontSize = 15.sp, color = TextSecondary)
                }
            }
            errorMsg != null -> {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CustomText(errorMsg!!, fontSize = 15.sp, color = TextSecondary)
                        Spacer(Modifier.height(16.dp))
                        CustomButton(onClick = {
                            scope.launch {
                                isLoading = true; errorMsg = null
                                val data = MnnModelManager.fetchMarketData(context, forceRefresh = true)
                                if (data != null) marketData = data else errorMsg = "加载失败，请检查网络"
                                isLoading = false
                            }
                        }) { CustomText("重试", fontSize = 14.sp, color = Color.White) }
                    }
                }
            }
            filteredModels.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CustomText(
                            if (searchQuery.isNotBlank() || !filterState.isEmpty) "未找到匹配的模型"
                            else "暂无可用模型",
                            fontSize = 15.sp, color = TextSecondary
                        )
                        if (!filterState.isEmpty) {
                            Spacer(Modifier.height(12.dp))
                            CustomTextButton(onClick = { filterState = MnnFilterState() }, contentColor = TextPrimary) {
                                CustomText("清除筛选", fontSize = 14.sp, color = TextPrimary)
                            }
                        }
                    }
                }
            }
            else -> {
                Box(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    CustomText("共 ${filteredModels.size} 个模型", fontSize = 12.sp, color = TextSecondary)
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredModels, key = { it.modelId }) { model ->
                        val state = getModelDownloadState(model.modelId)
                        val task = downloadTasks[model.modelId]
                        MarketModelCard(
                            model = model,
                            downloadState = state,
                            progress = task?.progress ?: 0f,
                            onDownloadClick = { downloadManager.startDownload(model) },
                            onPauseClick = { downloadManager.pauseDownload(model.modelId) },
                            onCancelClick = { downloadManager.cancelDownload(model.modelId) }
                        )
                    }
                    item { Spacer(Modifier.height(20.dp)) }
                }
            }
        }
    }

    if (showFilterDialog) {
        MnnFilterDialog(
            currentFilter = filterState,
            allTags = allTags,
            sizeOptions = sizeOptions,
            allVendors = allVendors,
            onConfirm = { filterState = it; showFilterDialog = false },
            onClear = { filterState = MnnFilterState(); showFilterDialog = false },
            onDismiss = { showFilterDialog = false }
        )
    }
}

// ==================== 筛选弹窗 ====================

@Composable
fun MnnFilterDialog(
    currentFilter: MnnFilterState,
    allTags: List<String>,
    sizeOptions: List<String>,
    allVendors: List<String>,
    onConfirm: (MnnFilterState) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var tags by remember { mutableStateOf(currentFilter.tags) }
    var sizes by remember { mutableStateOf(currentFilter.sizeCategories) }
    var vendors by remember { mutableStateOf(currentFilter.vendors) }
    var downloadState by remember { mutableStateOf(currentFilter.downloadState) }

    CustomDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
        ) {
            CustomText("筛选", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(16.dp))

            // 模型标签
            FilterSectionTitle("模型标签")
            FilterChipGroup(
                options = allTags,
                selected = tags,
                onToggle = { tags = if (it in tags) tags - it else tags + it }
            )
            Spacer(Modifier.height(12.dp))

            // 模型大小
            FilterSectionTitle("模型大小")
            FilterChipGroup(
                options = sizeOptions,
                selected = sizes,
                onToggle = { sizes = if (it in sizes) sizes - it else sizes + it }
            )
            Spacer(Modifier.height(12.dp))

            // 厂商
            FilterSectionTitle("厂商")
            FilterChipGroup(
                options = allVendors,
                selected = vendors,
                onToggle = { vendors = if (it in vendors) vendors - it else vendors + it }
            )
            Spacer(Modifier.height(12.dp))

            // 下载状态
            FilterSectionTitle("下载状态")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MnnDownloadFilterState.entries.forEach { state ->
                    val selected = downloadState == state
                    FilterChip(
                        label = state.displayName,
                        selected = selected,
                        onClick = { downloadState = if (selected) null else state }
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            // 按钮
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CustomTextButton(
                    onClick = onClear, contentColor = TextSecondary,
                    modifier = Modifier.weight(1f)
                ) {
                    CustomText("清空选择", fontSize = 14.sp, color = TextSecondary)
                }
                CustomButton(
                    onClick = {
                        onConfirm(MnnFilterState(tags, sizes, vendors, downloadState))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    CustomText("确认", fontSize = 14.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun FilterSectionTitle(title: String) {
    CustomText(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
    Spacer(Modifier.height(8.dp))
}

@Composable
fun FilterChipGroup(
    options: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        options.forEach { option ->
            FilterChip(
                label = option,
                selected = option in selected,
                onClick = { onToggle(option) }
            )
        }
    }
}

// ==================== 模型卡片 ====================

@Composable
fun MarketModelCard(
    model: MnnMarketModel,
    downloadState: MnnDownloadState,
    progress: Float,
    onDownloadClick: () -> Unit,
    onPauseClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.White)) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    CustomText(
                        model.modelName, fontSize = 15.sp, color = TextPrimary,
                        fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    CustomText(model.vendor, fontSize = 12.sp, color = TextSecondary)
                }
                MarketModelAction(downloadState, progress, onDownloadClick, onPauseClick, onCancelClick)
            }

            if (model.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                CustomText(model.description, fontSize = 13.sp, color = TextSecondary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }

            if (model.tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                ) {
                    model.tags.forEach { tag ->
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(4.dp))
                                .background(BgInput).padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            CustomText(tag, fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                }
            }

            // 下载进度条
            if (downloadState == MnnDownloadState.DOWNLOADING || downloadState == MnnDownloadState.PAUSED) {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().height(4.dp)
                        .clip(RoundedCornerShape(2.dp)).background(Color(0xFFE0E0E0))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (downloadState == MnnDownloadState.PAUSED) Color(0xFFFFC107)
                                else TextPrimary
                            )
                    )
                }
                Spacer(Modifier.height(4.dp))
                CustomText(
                    "${(progress * 100).toInt()}%",
                    fontSize = 11.sp, color = TextSecondary
                )
            }

            Spacer(Modifier.height(10.dp))
            CustomHorizontalDivider(color = Color(0xFFF0F0F0), thickness = 1.dp)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (model.sizeB > 0) CustomText("参数: ${MnnModelManager.formatSizeB(model.sizeB)}", fontSize = 12.sp, color = TextSecondary)
                if (model.fileSize > 0) CustomText("大小: ${MnnModelManager.formatSize(model.fileSize)}", fontSize = 12.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
fun MarketModelAction(
    state: MnnDownloadState,
    progress: Float,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onCancel: () -> Unit
) {
    when (state) {
        MnnDownloadState.DOWNLOADED -> {
            Box(
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF4CAF50).copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                CustomText("已下载", fontSize = 11.sp, color = Color(0xFF4CAF50))
            }
        }
        MnnDownloadState.DOWNLOADING -> {
            CustomTextButton(onClick = onPause, contentColor = Color(0xFFFFC107),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                CustomText("暂停", fontSize = 13.sp, color = Color(0xFFFFC107))
            }
        }
        MnnDownloadState.PAUSED -> {
            Row {
                CustomTextButton(onClick = onDownload, contentColor = TextPrimary,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                    CustomText("继续", fontSize = 13.sp, color = TextPrimary)
                }
                CustomTextButton(onClick = onCancel, contentColor = Color(0xFFF44336),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                    CustomText("取消", fontSize = 13.sp, color = Color(0xFFF44336))
                }
            }
        }
        MnnDownloadState.ERROR -> {
            CustomTextButton(onClick = onDownload, contentColor = Color(0xFFF44336),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                CustomText("重试", fontSize = 13.sp, color = Color(0xFFF44336))
            }
        }
        MnnDownloadState.NOT_DOWNLOADED -> {
            CustomButton(
                onClick = onDownload,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                CustomText("下载", fontSize = 13.sp, color = Color.White)
            }
        }
    }
}

@Composable
fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bgColor = if (selected) TextPrimary else BgInput
    val textColor = if (selected) Color.White else TextSecondary
    Box(
        modifier = Modifier
            .wrapContentWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        CustomText(label, fontSize = 13.sp, color = textColor, maxLines = 1)
    }
}
