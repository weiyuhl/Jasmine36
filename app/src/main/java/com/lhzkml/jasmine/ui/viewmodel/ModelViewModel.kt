package com.lhzkml.jasmine.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhzkml.jasmine.config.ProviderManager
import com.lhzkml.jasmine.core.prompt.executor.ApiType
import com.lhzkml.jasmine.mnn.MnnChatClient
import com.lhzkml.jasmine.mnn.MnnModelManager
import com.lhzkml.jasmine.core.prompt.llm.ChatClientRouter
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 模型选择 ViewModel
 * 
 * 职责：
 * - 模型列表管理
 * - 当前模型选择
 * - Thinking 模式管理
 * - 本地/远程模型切换
 * 
 * 从原 ChatViewModel 中拆分出来，专注于模型相关逻辑
 */
class ModelViewModel(
    private val context: Context,
    private val clientRouter: ChatClientRouter
) : ViewModel() {

    // 模型列表
    private val _modelList = MutableStateFlow<List<String>>(emptyList())
    val modelList: StateFlow<List<String>> = _modelList.asStateFlow()

    // 当前模型
    private val _currentModel = MutableStateFlow("")
    val currentModel: StateFlow<String> = _currentModel.asStateFlow()

    // 当前模型显示名称
    private val _currentModelDisplay = MutableStateFlow("")
    val currentModelDisplay: StateFlow<String> = _currentModelDisplay.asStateFlow()

    // 是否支持 Thinking 模式
    private val _supportsThinkingMode = MutableStateFlow(false)
    val supportsThinkingMode: StateFlow<Boolean> = _supportsThinkingMode.asStateFlow()

    // Thinking 模式是否启用
    private val _isThinkingModeEnabled = MutableStateFlow(true)
    val isThinkingModeEnabled: StateFlow<Boolean> = _isThinkingModeEnabled.asStateFlow()

    // 覆盖模型（用于临时切换）
    var overrideModel: String? = null
        private set

    /**
     * 刷新模型选择器
     */
    fun refreshModelSelector() {
        viewModelScope.launch {
            val activeId = ProviderManager.getActiveId()
            if (activeId == null) {
                _currentModelDisplay.value = "未配置"
                _modelList.value = emptyList()
                _currentModel.value = ""
                _supportsThinkingMode.value = false
                return@launch
            }

            val provider = ProviderManager.getProvider(activeId)
            if (provider?.apiType == ApiType.LOCAL) {
                refreshLocalModels(activeId)
            } else {
                refreshRemoteModels(activeId)
            }
        }
    }

    /**
     * 刷新本地模型（MNN）
     */
    private fun refreshLocalModels(providerId: String) {
        val localModels = MnnModelManager.getLocalModels(context)
        val localModelIds = localModels.map { it.modelId }
        _modelList.value = localModelIds

        val model = overrideModel ?: ProviderManager.getModel(context, providerId)
        val selectedModel = if (model.isNotEmpty() && model in localModelIds) model
            else localModelIds.firstOrNull() ?: ""

        if (selectedModel != model && selectedModel.isNotEmpty()) {
            overrideModel = selectedModel
        }

        _currentModel.value = selectedModel
        _currentModelDisplay.value = "${shortenModelName(selectedModel).ifEmpty { "请下载模型" }} \u02C7"
        _supportsThinkingMode.value = MnnModelManager.isSupportThinkingSwitch(context, selectedModel)
        _isThinkingModeEnabled.value = ProviderManager.getMnnThinkingEnabled(context, selectedModel)
    }

    /**
     * 刷新远程模型
     */
    private fun refreshRemoteModels(providerId: String) {
        _supportsThinkingMode.value = false
        val model = overrideModel ?: ProviderManager.getModel(context, providerId)
        _currentModel.value = model
        _currentModelDisplay.value = "${shortenModelName(model).ifEmpty { "未选择模型" }} \u02C7"

        val selectedModels = ProviderManager.getSelectedModels(context, providerId)
        _modelList.value = if (selectedModels.isEmpty()) {
            if (model.isNotEmpty()) listOf(model) else emptyList()
        } else {
            if (model.isNotEmpty() && model !in selectedModels) {
                listOf(model) + selectedModels
            } else selectedModels
        }
    }

    /**
     * 选择模型
     */
    fun selectModel(model: String) {
        viewModelScope.launch {
            val activeId = ProviderManager.getActiveId() ?: return@launch
            overrideModel = model
            val key = ProviderManager.getApiKey(context, activeId) ?: ""
            val baseUrl = ProviderManager.getBaseUrl(context, activeId)
            ProviderManager.saveConfig(context, activeId, key, baseUrl, model)
            refreshModelSelector()
        }
    }

    /**
     * 设置 Thinking 模式
     */
    fun setThinkingMode(enabled: Boolean) {
        viewModelScope.launch {
            if (!_supportsThinkingMode.value || _currentModel.value.isEmpty()) return@launch
            _isThinkingModeEnabled.value = enabled
            ProviderManager.setMnnThinkingEnabled(context, _currentModel.value, enabled)
            
            // 更新 MNN 客户端
            val client = clientRouter.getClient(MnnChatClient.PROVIDER_ID) as? MnnChatClient
            client?.updateThinking(enabled)
        }
    }

    /**
     * 缩短模型名称（去除路径前缀）
     */
    fun shortenModelName(model: String): String = model.substringAfterLast("/")
}
