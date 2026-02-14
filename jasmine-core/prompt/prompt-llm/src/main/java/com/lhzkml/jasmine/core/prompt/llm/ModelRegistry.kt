package com.lhzkml.jasmine.core.prompt.llm

import com.lhzkml.jasmine.core.prompt.model.ModelInfo

/**
 * 模型注册表
 *
 * 维护各供应商模型的元数据信息，数据完全来自 API 动态获取。
 * 当用户在供应商配置页获取模型列表时，API 返回的元数据会自动注册到此处。
 * 如果模型不在注册表中，会返回一个带有默认值的 LLModel。
 */
object ModelRegistry {

    /** API 动态获取的模型数据 */
    private val models = mutableMapOf<String, LLModel>()

    /**
     * 根据模型 ID 查找模型元数据
     * 支持模糊匹配：如果精确匹配不到，会尝试前缀匹配
     */
    fun find(modelId: String): LLModel? {
        // 精确匹配
        models[modelId]?.let { return it }
        // 前缀匹配（处理带日期后缀的模型名，如 claude-sonnet-4-20250514）
        return models.entries
            .filter { modelId.startsWith(it.key) || it.key.startsWith(modelId) }
            .maxByOrNull { it.key.length }
            ?.value
            ?.copy(id = modelId)
    }

    /**
     * 根据模型 ID 获取模型元数据，找不到时返回默认值
     */
    fun getOrDefault(modelId: String, provider: LLMProvider): LLModel {
        return find(modelId) ?: LLModel(
            provider = provider,
            id = modelId,
            contextLength = DEFAULT_CONTEXT_LENGTH,
            maxOutputTokens = DEFAULT_MAX_OUTPUT,
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Streaming
            )
        )
    }

    /**
     * 从 API 返回的 ModelInfo 列表动态注册模型
     * 仅当 ModelInfo 包含元数据时才注册（如 Gemini 返回的 contextLength、maxOutputTokens）
     */
    fun registerFromApi(provider: LLMProvider, modelList: List<ModelInfo>) {
        for (info in modelList) {
            if (!info.hasMetadata) continue

            val capabilities = mutableListOf<LLMCapability>()
            if (info.temperature != null || info.maxTemperature != null) {
                capabilities.add(LLMCapability.Temperature)
            }
            capabilities.add(LLMCapability.Streaming)
            if (info.supportsThinking == true) {
                capabilities.add(LLMCapability.Reasoning)
            }

            val model = LLModel(
                provider = provider,
                id = info.id,
                displayName = info.displayName ?: info.id,
                contextLength = info.contextLength ?: DEFAULT_CONTEXT_LENGTH,
                maxOutputTokens = info.maxOutputTokens,
                capabilities = capabilities
            )
            models[info.id] = model
        }
    }

    /**
     * 手动注册模型
     */
    fun register(model: LLModel) {
        models[model.id] = model
    }

    /**
     * 获取所有已注册的模型
     */
    fun allModels(): List<LLModel> = models.values.toList()

    /**
     * 获取指定供应商的所有已注册模型
     */
    fun modelsFor(provider: LLMProvider): List<LLModel> =
        models.values.filter { it.provider.name == provider.name }

    /**
     * 清除指定供应商的数据（用于刷新）
     */
    fun clearDynamic(provider: LLMProvider) {
        models.entries.removeAll { it.value.provider.name == provider.name }
    }

    const val DEFAULT_CONTEXT_LENGTH = 8192
    const val DEFAULT_MAX_OUTPUT = 4096
}
