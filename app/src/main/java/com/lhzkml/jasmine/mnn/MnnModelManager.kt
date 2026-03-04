package com.lhzkml.jasmine.mnn

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
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

object MnnModelManager {

    private const val TAG = "MnnModelManager"
    private const val MODELS_DIR = "mnn_models"
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

    fun getModelsDir(context: Context): File {
        val dir = File(context.filesDir, MODELS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** 将 modelId 转为安全的目录名（官方 demo 的 safeModelId 做法） */
    fun safeModelId(modelId: String): String = modelId.replace("/", "_")

    fun getLocalModels(context: Context): List<MnnModelInfo> {
        val modelsDir = getModelsDir(context)
        val models = mutableListOf<MnnModelInfo>()

        modelsDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                val mnnFiles = dir.listFiles { f -> f.extension == "mnn" }
                if (mnnFiles?.isNotEmpty() == true) {
                    val configFile = File(dir, CONFIG_FILE)
                    val config = if (configFile.exists()) {
                        try {
                            Gson().fromJson(configFile.readText(), MnnModelConfig::class.java)
                        } catch (e: Exception) {
                            null
                        }
                    } else null
                    val modelId = dir.name
                    val parts = modelId.split("_")
                    val displayName = if (parts.size > 1) parts.drop(1).joinToString("_") else modelId
                    models.add(
                        MnnModelInfo(
                            modelId = modelId,
                            modelName = displayName,
                            modelPath = mnnFiles[0].absolutePath,
                            sizeBytes = calculateDirSize(dir),
                            isDownloaded = true,
                            config = config
                        )
                    )
                }
            }
        }

        return models
    }

    private const val GLOBAL_DEFAULTS_ID = "__global_defaults__"
    private const val GLOBAL_CONFIG_FILE = "mnn_global_defaults.json"

    fun getModelConfig(context: Context, modelId: String): MnnModelConfig? {
        if (modelId == GLOBAL_DEFAULTS_ID) return getGlobalDefaults(context)
        val dirName = if (modelId.contains("/")) safeModelId(modelId) else modelId
        val configFile = File(getModelsDir(context), "$dirName/$CONFIG_FILE")
        return if (configFile.exists()) {
            try {
                Gson().fromJson(configFile.readText(), MnnModelConfig::class.java)
            } catch (e: Exception) {
                null
            }
        } else null
    }

    fun saveModelConfig(context: Context, modelId: String, config: MnnModelConfig): Boolean {
        if (modelId == GLOBAL_DEFAULTS_ID) return saveGlobalDefaults(context, config)
        return try {
            val dirName = if (modelId.contains("/")) safeModelId(modelId) else modelId
            val modelDir = File(getModelsDir(context), dirName)
            if (!modelDir.exists()) modelDir.mkdirs()
            File(modelDir, CONFIG_FILE).writeText(Gson().toJson(config))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getGlobalDefaults(context: Context): MnnModelConfig? {
        val file = File(context.filesDir, GLOBAL_CONFIG_FILE)
        return if (file.exists()) {
            try { Gson().fromJson(file.readText(), MnnModelConfig::class.java) }
            catch (e: Exception) { null }
        } else null
    }

    /** 官方 demo 的默认配置（ModelConfig.defaultConfig） */
    fun defaultGlobalConfig(): MnnModelConfig = MnnModelConfig(
        backendType = "cpu",
        threadNum = 4,
        precision = "low",
        useMmap = false,
        memory = "low",
        samplerType = "mixed",
        temperature = 0.6f,
        topP = 0.95f,
        topK = 20,
        minP = 0.05f,
        tfsZ = 1.0f,
        typical = 0.95f,
        penalty = 1.02f,
        nGram = 8,
        nGramFactor = 1.02f,
        maxNewTokens = 2048
    )

    private fun saveGlobalDefaults(context: Context, config: MnnModelConfig): Boolean {
        return try {
            File(context.filesDir, GLOBAL_CONFIG_FILE).writeText(Gson().toJson(config))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 递归删除目录。参考官方 MNN DownloadFileUtils.deleteDirectoryRecursively，
     * 使用 Files.walkFileTree 先删文件再删目录，相比 File.deleteRecursively() 更可靠。
     */
    private fun deleteDirectoryRecursively(dir: File?): Boolean {
        if (dir == null || !dir.exists()) return false
        val dirPath: Path = dir.toPath()
        return try {
            Files.walkFileTree(dirPath, object : SimpleFileVisitor<Path>() {
                @Throws(IOException::class)
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.delete(file)
                    return FileVisitResult.CONTINUE
                }

                @Throws(IOException::class)
                override fun postVisitDirectory(directory: Path, exc: IOException?): FileVisitResult {
                    Files.delete(directory)
                    return FileVisitResult.CONTINUE
                }

                @Throws(IOException::class)
                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                    Log.e(TAG, "visitFileFailed: $file", exc)
                    return FileVisitResult.TERMINATE
                }
            })
            true
        } catch (e: IOException) {
            Log.e(TAG, "deleteDirectoryRecursively failed: ${dir.absolutePath}", e)
            false
        }
    }

    fun deleteModel(context: Context, modelId: String): Boolean {
        return try {
            val dirName = if (modelId.contains("/")) safeModelId(modelId) else modelId
            val modelDir = File(getModelsDir(context), dirName)
            if (!modelDir.exists()) return true // 目录不存在视为已删除
            val ok = deleteDirectoryRecursively(modelDir)
            if (!ok) Log.e(TAG, "deleteModel failed: $modelId -> ${modelDir.absolutePath}")
            ok
        } catch (e: Exception) {
            Log.e(TAG, "deleteModel error: $modelId", e)
            false
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

    fun getDownloadedModelIds(context: Context): Set<String> {
        return getLocalModels(context).map { it.modelId }.toSet()
    }

    /** 检查市场 modelId（如 MNN/Qwen3.5-2B-MNN）是否已下载 */
    fun isModelDownloaded(context: Context, marketModelId: String): Boolean {
        val safe = safeModelId(marketModelId)
        return getDownloadedModelIds(context).contains(safe)
    }

    private fun calculateDirSize(dir: File): Long {
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) calculateDirSize(file) else file.length()
        }
        return size
    }

    fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024L * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
            bytes >= 1024L * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024))
            bytes >= 1024L -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    fun formatSizeB(sizeB: Double): String {
        return when {
            sizeB >= 1.0 -> String.format("%.1fB", sizeB)
            sizeB >= 0.1 -> String.format("%.1fB", sizeB)
            else -> String.format("%.2fB", sizeB)
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
