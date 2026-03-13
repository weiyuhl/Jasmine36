package com.lhzkml.jasmine.core.prompt.mnn

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.File

/**
 * MNN 模型管理器 - 框架层简化版
 * 提供核心的模型管理功能，不包含下载和导入导出（由应用层实现）
 */
object MnnModelManager {

    private const val TAG = "MnnModelManager"
    private const val MODELS_DIR = "mnn_models"
    private const val CONFIG_FILE = "config.json"
    private const val GLOBAL_CONFIG_FILE = "mnn_global_defaults.json"

    fun getModelsDir(context: Context): File {
        val dir = File(context.filesDir, MODELS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** 将 modelId 转为安全的目录名 */
    fun safeModelId(modelId: String): String = modelId.replace("/", "_")

    /**
     * 获取本地已下载的模型列表
     */
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

    /**
     * 获取模型配置
     */
    fun getModelConfig(context: Context, modelId: String): MnnModelConfig? {
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

    /**
     * 保存模型配置
     */
    fun saveModelConfig(context: Context, modelId: String, config: MnnModelConfig): Boolean {
        return try {
            val dirName = if (modelId.contains("/")) safeModelId(modelId) else modelId
            val modelDir = File(getModelsDir(context), dirName)
            if (!modelDir.exists()) modelDir.mkdirs()
            File(modelDir, CONFIG_FILE).writeText(Gson().toJson(config))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save config", e)
            false
        }
    }

    /**
     * 获取全局默认配置
     */
    fun getGlobalDefaults(context: Context): MnnModelConfig? {
        val file = File(context.filesDir, GLOBAL_CONFIG_FILE)
        return if (file.exists()) {
            try {
                Gson().fromJson(file.readText(), MnnModelConfig::class.java)
            } catch (e: Exception) {
                null
            }
        } else null
    }

    /**
     * 官方 demo 的默认配置
     */
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

    /**
     * 保存全局默认配置
     */
    fun saveGlobalDefaults(context: Context, config: MnnModelConfig): Boolean {
        return try {
            File(context.filesDir, GLOBAL_CONFIG_FILE).writeText(Gson().toJson(config))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save global defaults", e)
            false
        }
    }

    /**
     * 删除模型
     */
    fun deleteModel(context: Context, modelId: String): Boolean {
        return try {
            val dirName = if (modelId.contains("/")) safeModelId(modelId) else modelId
            val modelDir = File(getModelsDir(context), dirName)
            if (!modelDir.exists()) return true
            modelDir.deleteRecursively()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete model", e)
            false
        }
    }

    /**
     * 模型是否支持 Thinking 开关
     */
    fun isSupportThinkingSwitch(context: Context, modelId: String): Boolean {
        if (modelId.contains("Thinking", ignoreCase = true)) return true

        val dirName = if (modelId.contains("/")) safeModelId(modelId) else modelId
        val modelDir = File(getModelsDir(context), dirName)
        if (!modelDir.exists() || !modelDir.isDirectory) return false

        val localInfo = getLocalModels(context).find { it.modelId == modelId }
        if (localInfo != null && localInfo.modelName.contains("Thinking", ignoreCase = true)) return true

        val configFile = File(modelDir, CONFIG_FILE)
        if (configFile.exists()) {
            try {
                val root = Gson().fromJson(configFile.readText(), JsonObject::class.java) ?: return false
                val jinja = root.getAsJsonObject("jinja")
                val contextObj = jinja?.getAsJsonObject("context")
                if (contextObj?.has("enable_thinking") == true) return true
            } catch (_: Exception) {}
        }

        return false
    }

    /**
     * 计算目录大小
     */
    private fun calculateDirSize(dir: File): Long {
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) calculateDirSize(file) else file.length()
        }
        return size
    }

    /**
     * 格式化文件大小
     */
    fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024L * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
            bytes >= 1024L * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024))
            bytes >= 1024L -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
