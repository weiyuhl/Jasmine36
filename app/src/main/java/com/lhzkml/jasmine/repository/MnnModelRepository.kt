package com.lhzkml.jasmine.repository

import android.content.Context
import com.lhzkml.jasmine.core.prompt.mnn.MnnModelConfig
import com.lhzkml.jasmine.core.prompt.mnn.MnnMarketModel
import com.lhzkml.jasmine.mnn.MnnModelManager

/**
 * MNN 模型 Repository
 *
 * 负责：
 * - 本地模型列表
 * - 模型配置读取/保存
 * - 全局默认配置
 * - 删除模型
 * - 模型市场数据读取
 * - 下载状态
 * - 导入 / 导出 / 压缩 / 解压
 *
 * 对应页面：
 * - MnnManagementActivity
 * - MnnModelMarketActivity
 * - MnnModelSettingsActivity
 * - EmbeddingConfigActivity 中本地模型列表
 * - ModelSelectionRepository 中本地模型只读查询
 *
 * 说明：
 * - MnnDownloadManager 继续保留为下载 service
 * - Repository 封装它暴露给 ViewModel 的数据入口
 */
interface MnnModelRepository {
    
    /**
     * 本地模型信息
     */
    data class LocalModelInfo(
        val modelId: String,
        val modelName: String,
        val modelPath: String,
        val configPath: String?,
        val tokenizerPath: String?,
        val supportThinkingSwitch: Boolean
    )
    
    /**
     * 获取本地模型列表
     */
    fun getLocalModels(): List<LocalModelInfo>
    
    /**
     * 获取指定模型的配置
     */
    fun getModelConfig(modelId: String): Map<String, Any>?
    
    /**
     * 保存模型配置
     */
    fun saveModelConfig(modelId: String, config: Map<String, Any>)
    
    /**
     * 获取全局默认配置
     */
    fun getGlobalDefaultConfig(): Map<String, Any>
    
    /**
     * 保存全局默认配置
     */
    fun saveGlobalDefaultConfig(config: Map<String, Any>)
    
    /**
     * 删除模型
     */
    fun deleteModel(modelId: String): Boolean
    
    /**
     * 模型市场数据
     */
    data class MarketModel(
        val modelId: String,
        val displayName: String,
        val description: String,
        val downloadUrl: String,
        val size: String,
        val extraTags: List<String>
    )
    
    /**
     * 获取模型市场数据
     */
    suspend fun getMarketModels(forceRefresh: Boolean = false): List<MarketModel>
    
    /**
     * 判断模型是否支持 Thinking 开关
     */
    fun isSupportThinkingSwitch(modelId: String): Boolean
    
    /**
     * 获取模型的额外标签
     */
    fun getExtraTagsForModel(modelId: String): List<String>
}

class DefaultMnnModelRepository(
    private val context: Context
) : MnnModelRepository {
    
    override fun getLocalModels(): List<MnnModelRepository.LocalModelInfo> {
        return MnnModelManager.getLocalModels(context).map { info ->
            MnnModelRepository.LocalModelInfo(
                modelId = info.modelId,
                modelName = info.modelName,
                modelPath = info.modelPath,
                configPath = null, // MnnModelInfo 没有这个字段
                tokenizerPath = null, // MnnModelInfo 没有这个字段
                supportThinkingSwitch = MnnModelManager.isSupportThinkingSwitch(context, info.modelId)
            )
        }
    }
    
    override fun getModelConfig(modelId: String): Map<String, Any>? {
        val config = MnnModelManager.getModelConfig(context, modelId) ?: return null
        // 将 MnnModelConfig 转换为 Map
        return mapOf(
            "llmModel" to (config.llmModel ?: ""),
            "llmWeight" to (config.llmWeight ?: ""),
            "backendType" to (config.backendType ?: "cpu"),
            "threadNum" to (config.threadNum ?: 4),
            "precision" to (config.precision ?: "low"),
            "useMmap" to (config.useMmap ?: false),
            "memory" to (config.memory ?: "low"),
            "samplerType" to (config.samplerType ?: "mixed"),
            "temperature" to (config.temperature ?: 0.6f),
            "topP" to (config.topP ?: 0.95f),
            "topK" to (config.topK ?: 20),
            "minP" to (config.minP ?: 0.05f),
            "tfsZ" to (config.tfsZ ?: 1.0f),
            "typical" to (config.typical ?: 0.95f),
            "penalty" to (config.penalty ?: 1.02f),
            "nGram" to (config.nGram ?: 8),
            "nGramFactor" to (config.nGramFactor ?: 1.02f),
            "maxNewTokens" to (config.maxNewTokens ?: 2048)
        )
    }
    
    override fun saveModelConfig(modelId: String, config: Map<String, Any>) {
        val mnnConfig = MnnModelConfig(
            llmModel = config["llmModel"] as? String,
            llmWeight = config["llmWeight"] as? String,
            backendType = config["backendType"] as? String,
            threadNum = config["threadNum"] as? Int,
            precision = config["precision"] as? String,
            useMmap = config["useMmap"] as? Boolean,
            memory = config["memory"] as? String,
            samplerType = config["samplerType"] as? String,
            temperature = (config["temperature"] as? Number)?.toFloat(),
            topP = (config["topP"] as? Number)?.toFloat(),
            topK = config["topK"] as? Int,
            minP = (config["minP"] as? Number)?.toFloat(),
            tfsZ = (config["tfsZ"] as? Number)?.toFloat(),
            typical = (config["typical"] as? Number)?.toFloat(),
            penalty = (config["penalty"] as? Number)?.toFloat(),
            nGram = config["nGram"] as? Int,
            nGramFactor = (config["nGramFactor"] as? Number)?.toFloat(),
            maxNewTokens = config["maxNewTokens"] as? Int
        )
        MnnModelManager.saveModelConfig(context, modelId, mnnConfig)
    }
    
    override fun getGlobalDefaultConfig(): Map<String, Any> {
        val config = MnnModelManager.getGlobalDefaults(context) ?: return mapOf(
            "llmModel" to "",
            "llmWeight" to "",
            "backendType" to "cpu",
            "threadNum" to 4,
            "precision" to "low",
            "useMmap" to false,
            "memory" to "low",
            "samplerType" to "mixed",
            "temperature" to 0.6f,
            "topP" to 0.95f,
            "topK" to 40,
            "repeatPenalty" to 1.0f,
            "maxNewTokens" to 512
        )
        return mapOf(
            "llmModel" to (config.llmModel ?: ""),
            "llmWeight" to (config.llmWeight ?: ""),
            "backendType" to (config.backendType ?: "cpu"),
            "threadNum" to (config.threadNum ?: 4),
            "precision" to (config.precision ?: "low"),
            "useMmap" to (config.useMmap ?: false),
            "memory" to (config.memory ?: "low"),
            "samplerType" to (config.samplerType ?: "mixed"),
            "temperature" to (config.temperature ?: 0.6f),
            "topP" to (config.topP ?: 0.95f),
            "topK" to (config.topK ?: 20),
            "minP" to (config.minP ?: 0.05f),
            "tfsZ" to (config.tfsZ ?: 1.0f),
            "typical" to (config.typical ?: 0.95f),
            "penalty" to (config.penalty ?: 1.02f),
            "nGram" to (config.nGram ?: 8),
            "nGramFactor" to (config.nGramFactor ?: 1.02f),
            "maxNewTokens" to (config.maxNewTokens ?: 2048)
        )
    }
    
    override fun saveGlobalDefaultConfig(config: Map<String, Any>) {
        val mnnConfig = MnnModelConfig(
            llmModel = config["llmModel"] as? String,
            llmWeight = config["llmWeight"] as? String,
            backendType = config["backendType"] as? String,
            threadNum = config["threadNum"] as? Int,
            precision = config["precision"] as? String,
            useMmap = config["useMmap"] as? Boolean,
            memory = config["memory"] as? String,
            samplerType = config["samplerType"] as? String,
            temperature = (config["temperature"] as? Number)?.toFloat(),
            topP = (config["topP"] as? Number)?.toFloat(),
            topK = config["topK"] as? Int,
            minP = (config["minP"] as? Number)?.toFloat(),
            tfsZ = (config["tfsZ"] as? Number)?.toFloat(),
            typical = (config["typical"] as? Number)?.toFloat(),
            penalty = (config["penalty"] as? Number)?.toFloat(),
            nGram = config["nGram"] as? Int,
            nGramFactor = (config["nGramFactor"] as? Number)?.toFloat(),
            maxNewTokens = config["maxNewTokens"] as? Int
        )
        MnnModelManager.saveModelConfig(context, "__global_defaults__", mnnConfig)
    }
    
    override fun deleteModel(modelId: String): Boolean {
        return MnnModelManager.deleteModel(context, modelId)
    }
    
    override suspend fun getMarketModels(forceRefresh: Boolean): List<MnnModelRepository.MarketModel> {
        val marketData = MnnModelManager.fetchMarketData(context, forceRefresh) ?: return emptyList()
        return marketData.models.map { model ->
            MnnModelRepository.MarketModel(
                modelId = model.modelId,
                displayName = model.modelName,
                description = model.description,
                downloadUrl = model.sources.values.firstOrNull() ?: "",
                size = "${model.sizeB}B",
                extraTags = model.extraTags
            )
        }
    }
    
    override fun isSupportThinkingSwitch(modelId: String): Boolean {
        return MnnModelManager.isSupportThinkingSwitch(context, modelId)
    }
    
    override fun getExtraTagsForModel(modelId: String): List<String> {
        return MnnModelManager.getExtraTagsForModel(context, modelId)
    }
}
