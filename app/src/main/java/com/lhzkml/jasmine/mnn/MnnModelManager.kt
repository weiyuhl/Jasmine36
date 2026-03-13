package com.lhzkml.jasmine.mnn

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.lhzkml.jasmine.core.prompt.mnn.MnnModelManager as CoreMnnModelManager
import com.lhzkml.jasmine.core.prompt.mnn.MnnModelConfig
import com.lhzkml.jasmine.core.prompt.mnn.MnnModelInfo
import com.lhzkml.jasmine.core.prompt.mnn.MnnMarketData
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 应用层 MNN 模型管理器
 * 委托核心功能给框架层，添加下载、导入导出等应用层特定功能
 */
object MnnModelManager {

    private const val TAG = "MnnModelManager"
    private const val CONFIG_FILE = "config.json"
    private const val MARKET_CACHE_FILE = "mnn_market_cache.json"
    private const val MARKET_URL = "https://meta.alicdn.com/data/mnn/apis/model_market.json"
    private const val CACHE_EXPIRY_MS = 3600_000L

    private val httpClient by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 30_000
            }
        }
    }

    // 委托给框架层
    fun getModelsDir(context: Context): File = CoreMnnModelManager.getModelsDir(context)
    fun safeModelId(modelId: String): String = CoreMnnModelManager.safeModelId(modelId)
    fun getLocalModels(context: Context): List<MnnModelInfo> = CoreMnnModelManager.getLocalModels(context)
    fun getModelConfig(context: Context, modelId: String): MnnModelConfig? = CoreMnnModelManager.getModelConfig(context, modelId)
    fun saveModelConfig(context: Context, modelId: String, config: MnnModelConfig): Boolean = CoreMnnModelManager.saveModelConfig(context, modelId, config)
    fun getGlobalDefaults(context: Context): MnnModelConfig? = CoreMnnModelManager.getGlobalDefaults(context)
    fun defaultGlobalConfig(): MnnModelConfig = CoreMnnModelManager.defaultGlobalConfig()
    fun deleteModel(context: Context, modelId: String): Boolean = CoreMnnModelManager.deleteModel(context, modelId)
    fun isSupportThinkingSwitch(context: Context, modelId: String): Boolean = CoreMnnModelManager.isSupportThinkingSwitch(context, modelId)

    /**
     * 获取模型的额外标签（来自市场缓存），用于判断能力如 ThinkingSwitch
     */
    fun getExtraTagsForModel(context: Context, modelId: String): List<String> {
        val cacheFile = File(context.filesDir, MARKET_CACHE_FILE)
        if (!cacheFile.exists()) return emptyList()
        return try {
            val data = Gson().fromJson(cacheFile.readText(), MnnMarketData::class.java) ?: return emptyList()
            val safe = safeModelId(modelId)
            val marketModel = data.models.find { m ->
                val mSafe = safeModelId(m.modelId)
                m.modelId == modelId || mSafe == safe ||
                    mSafe.contains(modelId)
            }
            marketModel?.extraTags ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "getExtraTagsForModel error", e)
            emptyList()
        }
    }

    /**
     * 保存全局默认配置
     */
    fun saveGlobalDefaults(context: Context, config: MnnModelConfig): Boolean = 
        CoreMnnModelManager.saveGlobalDefaults(context, config)

    /**
     * 获取已下载的模型 ID 集合
     */
    fun getDownloadedModelIds(context: Context): Set<String> {
        return getLocalModels(context).map { it.modelId }.toSet()
    }

    /** 检查市场 modelId（如 MNN/Qwen3.5-2B-MNN）是否已下载 */
    fun isModelDownloaded(context: Context, marketModelId: String): Boolean {
        val safe = safeModelId(marketModelId)
        return getDownloadedModelIds(context).contains(safe)
    }

    fun formatSize(bytes: Long): String = CoreMnnModelManager.formatSize(bytes)

    fun formatSizeB(sizeB: Double): String {
        return when {
            sizeB >= 1.0 -> String.format("%.1fB", sizeB)
            sizeB >= 0.1 -> String.format("%.1fB", sizeB)
            else -> String.format("%.2fB", sizeB)
        }
    }

    suspend fun fetchMarketData(context: Context, forceRefresh: Boolean = false): MnnMarketData? {
        return withContext(Dispatchers.IO) {
            val cacheFile = File(context.filesDir, MARKET_CACHE_FILE)

            if (!forceRefresh && cacheFile.exists()) {
                val age = System.currentTimeMillis() - cacheFile.lastModified()
                if (age < CACHE_EXPIRY_MS) {
                    try {
                        val cached = Gson().fromJson(cacheFile.readText(), MnnMarketData::class.java)
                        if (cached != null) return@withContext cached
                    } catch (e: Exception) {
                        Log.w(TAG, "Cache parse error", e)
                    }
                }
            }

            try {
                val response = httpClient.get(MARKET_URL)
                if (response.status == HttpStatusCode.OK) {
                    val json = response.bodyAsText()
                    val data = Gson().fromJson(json, MnnMarketData::class.java)
                    if (data != null) {
                        cacheFile.writeText(json)
                        return@withContext data
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Network fetch error", e)
            }

            try {
                val assetJson = context.assets.open("model_market.json").bufferedReader().readText()
                return@withContext Gson().fromJson(assetJson, MnnMarketData::class.java)
            } catch (e: Exception) {
                Log.w(TAG, "Asset fallback error", e)
            }

            if (cacheFile.exists()) {
                try {
                    return@withContext Gson().fromJson(cacheFile.readText(), MnnMarketData::class.java)
                } catch (_: Exception) {}
            }

            null
        }
    }

    /** 将模型目录打包为 zip，返回临时 zip 文件（调用方负责写入目标 URI 后删除） */
    fun createModelZip(
        context: Context,
        modelId: String,
        onProgress: ((Float, String) -> Unit)? = null
    ): File? {
        val dirName = if (modelId.contains("/")) safeModelId(modelId) else modelId
        val modelDir = File(getModelsDir(context), dirName)
        if (!modelDir.exists() || !modelDir.isDirectory) return null
        return try {
            val fileList = collectFilesWithSize(modelDir)
            val totalBytes = fileList.sumOf { it.second }
            onProgress?.invoke(0f, "正在打包模型…")
            val zipFile = File(context.cacheDir, "mnn_export_${dirName}.zip")
            if (zipFile.exists()) zipFile.delete()
            var writtenBytes = 0L
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                fileList.forEach { (file, size) ->
                    val entryName = "$dirName/${file.relativeTo(modelDir).path}".replace("\\", "/")
                    if (file.isDirectory) {
                        zos.putNextEntry(ZipEntry("$entryName/"))
                        zos.closeEntry()
                    } else {
                        zos.putNextEntry(ZipEntry(entryName))
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                    writtenBytes += size
                    if (totalBytes > 0) {
                        onProgress?.invoke((writtenBytes.toFloat() / totalBytes).coerceIn(0f, 1f), "正在打包… ${formatSize(writtenBytes)} / ${formatSize(totalBytes)}")
                    }
                }
            }
            onProgress?.invoke(1f, "打包完成")
            zipFile
        } catch (e: Exception) {
            Log.e(TAG, "createModelZip failed", e)
            null
        }
    }

    /** 收集所有文件（含目录占位）及大小，目录记为 0 */
    private fun collectFilesWithSize(dir: File): List<Pair<File, Long>> {
        val result = mutableListOf<Pair<File, Long>>()
        fun collect(d: File, basePath: String) {
            d.listFiles()?.forEach { f ->
                val rel = if (basePath.isEmpty()) f.name else "$basePath/${f.name}"
                if (f.isDirectory) {
                    result.add(f to 0L)
                    collect(f, rel)
                } else {
                    result.add(f to f.length())
                }
            }
        }
        result.add(dir to 0L)
        collect(dir, dir.name)
        return result
    }

    /**
     * 从 SAF 树 URI 导入模型。支持：1) 所选目录即模型目录（含 config.json 和 .mnn）
     * 2) 所选目录下子目录为模型目录。
     * @return 导入的 modelId，失败返回 null
     */
    suspend fun importModelFromTree(
        context: Context,
        treeUri: Uri,
        onProgress: ((Float, String) -> Unit)? = null
    ): String? = withContext(Dispatchers.IO) {
        val doc = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext null
        val modelDir = findModelDir(doc) ?: return@withContext null
        val dirName = modelDir.name ?: "imported_${System.currentTimeMillis()}"
        val targetDir = File(getModelsDir(context), safeModelId(dirName))
        if (targetDir.exists()) targetDir.deleteRecursively()
        targetDir.mkdirs()
        val totalSize = getDocumentTreeSize(modelDir)
        val copiedRef = longArrayOf(0L)
        copyDocumentToFileWithProgress(context, modelDir, targetDir, copiedRef) { current ->
            if (totalSize > 0) {
                onProgress?.invoke((current.toFloat() / totalSize).coerceIn(0f, 1f), "正在导入… ${formatSize(current)} / ${formatSize(totalSize)}")
            } else {
                onProgress?.invoke(0f, "正在导入…")
            }
        }
        if (targetDir.listFiles { f -> f.extension == "mnn" }?.isNotEmpty() == true) dirName else null
    }

    private fun getDocumentTreeSize(doc: DocumentFile): Long {
        if (!doc.isDirectory) return doc.length()
        var size = 0L
        doc.listFiles().forEach { child ->
            size += if (child.isDirectory) getDocumentTreeSize(child) else child.length()
        }
        return size
    }

    private fun findModelDir(doc: DocumentFile): DocumentFile? {
        val hasConfig = doc.findFile("config.json") != null
        val hasMnn = doc.listFiles().any { it.name?.endsWith(".mnn") == true }
        if (hasConfig && hasMnn) return doc
        for (child in doc.listFiles()) {
            if (child.isDirectory) {
                findModelDir(child)?.let { return it }
            }
        }
        return null
    }

    private fun copyDocumentToFileWithProgress(
        context: Context,
        doc: DocumentFile,
        targetDir: File,
        copiedRef: LongArray,
        onProgress: (Long) -> Unit
    ) {
        for (child in doc.listFiles()) {
            val name = child.name ?: continue
            if (child.isDirectory) {
                val sub = File(targetDir, name)
                sub.mkdirs()
                copyDocumentToFileWithProgress(context, child, sub, copiedRef, onProgress)
            } else {
                context.contentResolver.openInputStream(child.uri)?.use { input ->
                    File(targetDir, name).outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var read: Int
                        while (input.read(buf).also { read = it } != -1) {
                            out.write(buf, 0, read)
                            copiedRef[0] += read
                            onProgress(copiedRef[0])
                        }
                    }
                }
            }
        }
    }

    /** 从 zip 文件 URI 导入模型 */
    suspend fun importModelFromZip(
        context: Context,
        zipUri: Uri,
        onProgress: ((Float, String) -> Unit)? = null
    ): String? = withContext(Dispatchers.IO) {
        val zipFile = File(context.cacheDir, "mnn_import_${System.currentTimeMillis()}.zip")
        try {
            val totalSize = try {
                context.contentResolver.openFileDescriptor(zipUri, "r")?.use { it.statSize }
            } catch (_: Exception) { null } ?: -1L
            var copiedBytes = 0L
            context.contentResolver.openInputStream(zipUri)?.use { input ->
                zipFile.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                        copiedBytes += read
                        if (totalSize > 0) {
                            onProgress?.invoke(
                                (copiedBytes.toFloat() / totalSize * 0.4f).coerceIn(0f, 0.4f),
                                "正在复制… ${formatSize(copiedBytes)} / ${formatSize(totalSize)}"
                            )
                        } else {
                            onProgress?.invoke(0f, "正在复制…")
                        }
                    }
                }
            } ?: return@withContext null
            onProgress?.invoke(0.4f, "正在解压…")
            var entryCount = 0
            var totalEntries = 0
            java.util.zip.ZipInputStream(zipFile.inputStream()).use { zis ->
                var e = zis.nextEntry
                while (e != null) {
                    if (!e.name.contains("..")) totalEntries++
                    e = zis.nextEntry
                }
            }
            var rootDirName: String? = null
            val extractDir = File(context.cacheDir, "mnn_import_extract_${System.currentTimeMillis()}")
            extractDir.mkdirs()
            try {
                java.util.zip.ZipInputStream(zipFile.inputStream()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        if (!name.contains("..")) {
                            if (rootDirName == null && name.contains("/")) {
                                rootDirName = name.substringBefore("/")
                            }
                            val file = File(extractDir, name)
                            if (entry.isDirectory) {
                                file.mkdirs()
                            } else {
                                file.parentFile?.mkdirs()
                                file.outputStream().use { zis.copyTo(it) }
                            }
                            entryCount++
                            if (totalEntries > 0) {
                                val p = 0.4f + 0.6f * (entryCount.toFloat() / totalEntries)
                                onProgress?.invoke(p.coerceIn(0f, 1f), "正在解压… $entryCount / $totalEntries")
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
                val modelDir = rootDirName?.let { File(extractDir, it) }
                    ?: extractDir.listFiles()?.firstOrNull { it.isDirectory }
                    ?: extractDir
                val hasConfig = File(modelDir, CONFIG_FILE).exists()
                val hasMnn = modelDir.listFiles { f -> f.extension == "mnn" }?.isNotEmpty() == true
                if (hasConfig && hasMnn) {
                    onProgress?.invoke(0.95f, "正在写入…")
                    val dirName = modelDir.name
                    val targetDir = File(getModelsDir(context), safeModelId(dirName))
                    if (targetDir.exists()) targetDir.deleteRecursively()
                    modelDir.copyRecursively(targetDir, overwrite = true)
                    onProgress?.invoke(1f, "导入完成")
                    dirName
                } else null
            } finally {
                extractDir.deleteRecursively()
            }
        } finally {
            zipFile.delete()
        }
    }
}
