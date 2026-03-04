package com.lhzkml.jasmine.mnn

import android.content.Context
import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MnnDownloadManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "MnnDownloadManager"
        private const val PREFS_NAME = "mnn_download_prefs"
        private const val KEY_DOWNLOAD_SOURCE = "download_source"

        @Volatile private var instance: MnnDownloadManager? = null

        fun getInstance(context: Context): MnnDownloadManager {
            return instance ?: synchronized(this) {
                instance ?: MnnDownloadManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 600_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 120_000
        }
    }
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val downloadJobs = mutableMapOf<String, Job>()

    private val _tasks = MutableStateFlow<Map<String, MnnDownloadTask>>(emptyMap())
    val tasks: StateFlow<Map<String, MnnDownloadTask>> = _tasks.asStateFlow()

    fun getDownloadSource(): MnnDownloadSource {
        val saved = prefs.getString(KEY_DOWNLOAD_SOURCE, null)
        return if (saved != null) MnnDownloadSource.fromString(saved) else MnnDownloadSource.MODEL_SCOPE
    }

    fun setDownloadSource(source: MnnDownloadSource) {
        prefs.edit().putString(KEY_DOWNLOAD_SOURCE, source.displayName).apply()
    }

    fun getDownloadState(modelId: String): MnnDownloadState {
        val task = _tasks.value[modelId]
        if (task != null && task.state != MnnDownloadState.DOWNLOADED) {
            return task.state
        }
        if (MnnModelManager.isModelDownloaded(context, modelId)) return MnnDownloadState.DOWNLOADED
        return task?.state ?: MnnDownloadState.NOT_DOWNLOADED
    }

    fun getTask(modelId: String): MnnDownloadTask? = _tasks.value[modelId]

    fun startDownload(model: MnnMarketModel) {
        val source = getDownloadSource()
        val sourceKey = when (source) {
            MnnDownloadSource.HUGGING_FACE -> "HuggingFace"
            MnnDownloadSource.MODEL_SCOPE -> "ModelScope"
            MnnDownloadSource.MODELERS -> "Modelers"
        }
        val repoPath = model.sources[sourceKey]
        if (repoPath.isNullOrBlank()) {
            updateTask(model.modelId) {
                it.copy(state = MnnDownloadState.ERROR, error = "该模型不支持 ${source.displayName} 源")
            }
            return
        }

        val task = MnnDownloadTask(
            modelId = model.modelId,
            modelName = model.modelName,
            source = source,
            repoPath = repoPath
        )
        updateTasks { it + (model.modelId to task) }

        val job = scope.launch {
            try {
                downloadModel(task)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    updateTask(model.modelId) { it.copy(state = MnnDownloadState.PAUSED) }
                } else {
                    Log.e(TAG, "Download failed: ${model.modelId}", e)
                    updateTask(model.modelId) {
                        it.copy(state = MnnDownloadState.ERROR, error = e.message)
                    }
                }
            }
        }
        downloadJobs[model.modelId] = job
    }

    fun pauseDownload(modelId: String) {
        downloadJobs[modelId]?.cancel()
        downloadJobs.remove(modelId)
        updateTask(modelId) { it.copy(state = MnnDownloadState.PAUSED) }
    }

    fun cancelDownload(modelId: String) {
        downloadJobs[modelId]?.cancel()
        downloadJobs.remove(modelId)
        updateTasks { it - modelId }
        scope.launch {
            MnnModelManager.deleteModel(context, modelId)
        }
    }

    private suspend fun downloadModel(task: MnnDownloadTask) {
        val fileListUrl = buildFileListUrl(task)
        val fileListResponse = httpClient.get(fileListUrl)
        if (fileListResponse.status != HttpStatusCode.OK) {
            throw Exception("获取文件列表失败: ${fileListResponse.status}")
        }

        val fileListJson = fileListResponse.bodyAsText()
        val files = parseFileList(fileListJson, task.source)

        val dirName = MnnModelManager.safeModelId(task.modelId)
        val modelDir = File(MnnModelManager.getModelsDir(context), dirName)
        if (!modelDir.exists()) modelDir.mkdirs()

        val totalSize = files.sumOf { it.size }
        updateTask(task.modelId) { it.copy(totalBytes = totalSize) }

        var downloadedTotal = 0L
        for (fileInfo in files) {
            coroutineContext.ensureActive()
            val targetFile = File(modelDir, fileInfo.path)
            targetFile.parentFile?.mkdirs()

            if (targetFile.exists() && targetFile.length() == fileInfo.size) {
                downloadedTotal += fileInfo.size
                updateTask(task.modelId) {
                    it.copy(
                        downloadedBytes = downloadedTotal,
                        progress = if (totalSize > 0) downloadedTotal.toFloat() / totalSize else 0f
                    )
                }
                continue
            }

            val downloadUrl = buildDownloadUrl(task, fileInfo.path)
            httpClient.prepareGet(downloadUrl).execute { response ->
                val channel = response.bodyAsChannel()
                FileOutputStream(targetFile).use { fos ->
                    val buffer = ByteArray(8192)
                    while (!channel.isClosedForRead) {
                        coroutineContext.ensureActive()
                        val read = channel.readAvailable(buffer)
                        if (read > 0) {
                            fos.write(buffer, 0, read)
                            downloadedTotal += read
                            updateTask(task.modelId) {
                                it.copy(
                                    downloadedBytes = downloadedTotal,
                                    progress = if (totalSize > 0) downloadedTotal.toFloat() / totalSize else 0f
                                )
                            }
                        }
                    }
                }
            }
        }

        updateTask(task.modelId) { it.copy(state = MnnDownloadState.DOWNLOADED, progress = 1f) }
        downloadJobs.remove(task.modelId)
    }

    private fun buildFileListUrl(task: MnnDownloadTask): String {
        return when (task.source) {
            MnnDownloadSource.MODEL_SCOPE -> {
                "https://modelscope.cn/api/v1/models/${task.repoPath}/repo/files?Recursive=1"
            }
            MnnDownloadSource.HUGGING_FACE -> {
                "https://huggingface.co/api/models/${task.repoPath}/tree/main?recursive=true"
            }
            MnnDownloadSource.MODELERS -> {
                "https://modelers.cn/api/v1/models/${task.repoPath}/repo/files?Recursive=1"
            }
        }
    }

    private fun buildDownloadUrl(task: MnnDownloadTask, filePath: String): String {
        return when (task.source) {
            MnnDownloadSource.MODEL_SCOPE -> {
                "https://modelscope.cn/api/v1/models/${task.repoPath}/repo?FilePath=$filePath"
            }
            MnnDownloadSource.HUGGING_FACE -> {
                "https://huggingface.co/${task.repoPath}/resolve/main/$filePath"
            }
            MnnDownloadSource.MODELERS -> {
                "https://modelers.cn/api/v1/models/${task.repoPath}/repo?FilePath=$filePath"
            }
        }
    }

    private fun parseFileList(json: String, source: MnnDownloadSource): List<RepoFileInfo> {
        val gson = com.google.gson.Gson()
        val result = mutableListOf<RepoFileInfo>()

        try {
            when (source) {
                MnnDownloadSource.MODEL_SCOPE, MnnDownloadSource.MODELERS -> {
                    val root = gson.fromJson(json, com.google.gson.JsonObject::class.java)
                    val data = root.getAsJsonObject("Data")
                    val files = data?.getAsJsonArray("Files") ?: return emptyList()
                    for (element in files) {
                        val obj = element.asJsonObject
                        val path = obj.get("Path")?.asString ?: continue
                        val size = obj.get("Size")?.asLong ?: 0
                        val type = obj.get("Type")?.asString ?: "file"
                        if (type == "file" || type == "blob") {
                            result.add(RepoFileInfo(path, size))
                        }
                    }
                }
                MnnDownloadSource.HUGGING_FACE -> {
                    val array = gson.fromJson(json, com.google.gson.JsonArray::class.java)
                    for (element in array) {
                        val obj = element.asJsonObject
                        val type = obj.get("type")?.asString ?: continue
                        if (type == "file") {
                            val path = obj.get("path")?.asString ?: continue
                            val size = obj.get("size")?.asLong ?: 0
                            result.add(RepoFileInfo(path, size))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse file list error", e)
        }

        return result
    }

    private fun updateTask(modelId: String, transform: (MnnDownloadTask) -> MnnDownloadTask) {
        updateTasks { map ->
            val existing = map[modelId] ?: return@updateTasks map
            map + (modelId to transform(existing))
        }
    }

    private fun updateTasks(transform: (Map<String, MnnDownloadTask>) -> Map<String, MnnDownloadTask>) {
        _tasks.value = transform(_tasks.value)
    }

    private data class RepoFileInfo(val path: String, val size: Long)
}
