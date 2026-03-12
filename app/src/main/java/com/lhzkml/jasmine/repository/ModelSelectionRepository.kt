package com.lhzkml.jasmine.repository

import android.content.Context
import com.lhzkml.jasmine.core.config.ConfigRepository
import com.lhzkml.jasmine.mnn.MnnModelManager

/**
 * 模型选择 Repository
 *
 * 负责：
 * - 当前模型选择
 * - selectedModels 列表
 * - 本地模型候选读取
 * - Thinking 模式开关
 *
 * 对应页面/功能：
 * - ModelViewModel
 * - ChatViewModel 中模型切换相关逻辑
 * - 聊天页模型选择器
 *
 * 说明：
 * - 它不负责模型文件导入导出
 * - 模型文件管理属于 MnnModelRepository
 */
interface ModelSelectionRepository {
    
    /**
     * 获取当前选中的模型
     */
    fun getCurrentModel(providerId: String): String
    
    /**
     * 获取 selectedModels 列表
     */
    fun getSelectedModels(providerId: String): List<String>
    
    /**
     * 设置 selectedModels 列表
     */
    fun setSelectedModels(providerId: String, models: List<String>)
    
    /**
     * 获取本地 MNN 模型列表（只读）
     */
    fun getLocalMnnModels(): List<LocalMnnModel>
    
    data class LocalMnnModel(
        val modelId: String,
        val modelName: String,
        val supportThinkingSwitch: Boolean
    )
    
    /**
     * Thinking 模式开关
     */
    fun isThinkingEnabled(modelId: String): Boolean
    fun setThinkingEnabled(modelId: String, enabled: Boolean)
    
    /**
     * 判断当前模型是否支持 Thinking 开关
     */
    fun currentModelSupportsThinking(providerId: String): Boolean
}

class DefaultModelSelectionRepository(
    private val context: Context,
    private val configRepo: ConfigRepository
) : ModelSelectionRepository {
    
    override fun getCurrentModel(providerId: String): String {
        val selectedModels = configRepo.getSelectedModels(providerId)
        return selectedModels.firstOrNull() ?: configRepo.getModel(providerId)
    }
    
    override fun getSelectedModels(providerId: String): List<String> {
        return configRepo.getSelectedModels(providerId)
    }
    
    override fun setSelectedModels(providerId: String, models: List<String>) {
        configRepo.setSelectedModels(providerId, models)
    }
    
    override fun getLocalMnnModels(): List<ModelSelectionRepository.LocalMnnModel> {
        return MnnModelManager.getLocalModels(context).map { info ->
            ModelSelectionRepository.LocalMnnModel(
                modelId = info.modelId,
                modelName = info.modelName,
                supportThinkingSwitch = MnnModelManager.isSupportThinkingSwitch(context, info.modelId)
            )
        }
    }
    
    override fun isThinkingEnabled(modelId: String): Boolean {
        // 使用 SharedPreferences 直接读取
        val prefs = context.getSharedPreferences("jasmine_mnn", android.content.Context.MODE_PRIVATE)
        return prefs.getBoolean("thinking_$modelId", true)
    }
    
    override fun setThinkingEnabled(modelId: String, enabled: Boolean) {
        // 使用 SharedPreferences 直接保存
        context.getSharedPreferences("jasmine_mnn", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("thinking_$modelId", enabled).apply()
    }
    
    override fun currentModelSupportsThinking(providerId: String): Boolean {
        val currentModel = getCurrentModel(providerId)
        
        // 检查是否是本地 MNN 模型
        val localModels = getLocalMnnModels()
        val localModel = localModels.find { it.modelId == currentModel }
        if (localModel != null) {
            return localModel.supportThinkingSwitch
        }
        
        // 检查模型名称是否包含 "thinking"
        return currentModel.contains("thinking", ignoreCase = true)
    }
}
