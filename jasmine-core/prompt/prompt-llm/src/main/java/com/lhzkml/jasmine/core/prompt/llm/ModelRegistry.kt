package com.lhzkml.jasmine.core.prompt.llm

import com.lhzkml.jasmine.core.prompt.model.ModelInfo

/**
 * 已知模型注册表
 *
 * 维护各供应商模型的元数据信息。
 * 优先使用 API 返回的动态元数据，硬编码数据仅作为后备。
 *
 * 数据来源优先级：
 * 1. API 动态获取（如 Gemini 返回 inputTokenLimit、outputTokenLimit 等）
 * 2. 硬编码后备数据（用于 API 不返回元数据的供应商，如 OpenAI、Claude）
 */
object ModelRegistry {

    /** 硬编码后备数据 */
    private val fallbackModels = mutableMapOf<String, LLModel>()
    /** API 动态获取的数据（优先级更高） */
    private val dynamicModels = mutableMapOf<String, LLModel>()

    init {
        registerFallbackModels()
    }

    /**
     * 根据模型 ID 查找模型元数据
     * 优先返回 API 动态数据，其次返回硬编码后备数据
     * 支持模糊匹配：如果精确匹配不到，会尝试前缀匹配
     */
    fun find(modelId: String): LLModel? {
        // 优先精确匹配动态数据
        dynamicModels[modelId]?.let { return it }
        // 精确匹配后备数据
        fallbackModels[modelId]?.let { return it }

        // 前缀匹配（处理带日期后缀的模型名，如 claude-sonnet-4-20250514）
        val allModels = dynamicModels + fallbackModels
        return allModels.entries
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
     * 对于不返回元数据的供应商（OpenAI、Claude），不会覆盖后备数据
     */
    fun registerFromApi(provider: LLMProvider, models: List<ModelInfo>) {
        for (info in models) {
            if (!info.hasMetadata) continue

            val capabilities = mutableListOf<LLMCapability>()
            // 根据 API 返回的字段推断能力
            if (info.temperature != null || info.maxTemperature != null) {
                capabilities.add(LLMCapability.Temperature)
            }
            capabilities.add(LLMCapability.Streaming) // 所有主流模型都支持流式
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
            dynamicModels[info.id] = model
        }
    }

    /**
     * 手动注册自定义模型（写入后备数据）
     */
    fun register(model: LLModel) {
        fallbackModels[model.id] = model
    }

    /**
     * 获取所有已注册的模型（动态 + 后备，动态优先）
     */
    fun allModels(): List<LLModel> {
        val merged = fallbackModels.toMutableMap()
        merged.putAll(dynamicModels) // 动态数据覆盖后备
        return merged.values.toList()
    }

    /**
     * 获取指定供应商的所有已注册模型
     */
    fun modelsFor(provider: LLMProvider): List<LLModel> =
        allModels().filter { it.provider.name == provider.name }

    /**
     * 清除指定供应商的动态数据（用于刷新）
     */
    fun clearDynamic(provider: LLMProvider) {
        dynamicModels.entries.removeAll { it.value.provider.name == provider.name }
    }

    const val DEFAULT_CONTEXT_LENGTH = 8192
    const val DEFAULT_MAX_OUTPUT = 4096

    /**
     * 硬编码后备模型数据
     * 仅用于 API 不返回元数据的供应商（OpenAI、Claude、DeepSeek、硅基流动）
     * Gemini 的数据会被 API 动态数据覆盖
     */
    private fun registerFallbackModels() {
        // ========== OpenAI（API 不返回 context_length） ==========
        val openaiCommon = listOf(
            LLMCapability.Temperature,
            LLMCapability.Streaming,
            LLMCapability.Tools,
            LLMCapability.StructuredOutput
        )
        val openaiVision = openaiCommon + LLMCapability.Vision

        register(LLModel(LLMProvider.OpenAI, "gpt-4o", "GPT-4o", 128000, 16384, openaiVision))
        register(LLModel(LLMProvider.OpenAI, "gpt-4o-mini", "GPT-4o Mini", 128000, 16384, openaiVision))
        register(LLModel(LLMProvider.OpenAI, "gpt-4-turbo", "GPT-4 Turbo", 128000, 4096, openaiVision))
        register(LLModel(LLMProvider.OpenAI, "gpt-4", "GPT-4", 8192, 8192, openaiCommon))
        register(LLModel(LLMProvider.OpenAI, "gpt-3.5-turbo", "GPT-3.5 Turbo", 16385, 4096, openaiCommon))
        register(LLModel(LLMProvider.OpenAI, "o1", "o1", 200000, 100000, openaiCommon + LLMCapability.Reasoning))
        register(LLModel(LLMProvider.OpenAI, "o1-mini", "o1 Mini", 128000, 65536, openaiCommon + LLMCapability.Reasoning))
        register(LLModel(LLMProvider.OpenAI, "o3-mini", "o3 Mini", 200000, 100000, openaiCommon + LLMCapability.Reasoning))

        // ========== Claude（API 不返回 context_window） ==========
        val claudeCommon = listOf(
            LLMCapability.Temperature,
            LLMCapability.Streaming,
            LLMCapability.Tools,
            LLMCapability.Vision
        )

        register(LLModel(LLMProvider.Claude, "claude-sonnet-4-20250514", "Claude Sonnet 4", 200000, 16384, claudeCommon))
        register(LLModel(LLMProvider.Claude, "claude-3-7-sonnet-20250219", "Claude 3.7 Sonnet", 200000, 16384, claudeCommon + LLMCapability.Reasoning))
        register(LLModel(LLMProvider.Claude, "claude-3-5-sonnet-20241022", "Claude 3.5 Sonnet", 200000, 8192, claudeCommon))
        register(LLModel(LLMProvider.Claude, "claude-3-5-haiku-20241022", "Claude 3.5 Haiku", 200000, 8192, claudeCommon))
        register(LLModel(LLMProvider.Claude, "claude-3-opus-20240229", "Claude 3 Opus", 200000, 4096, claudeCommon))
        register(LLModel(LLMProvider.Claude, "claude-3-haiku-20240307", "Claude 3 Haiku", 200000, 4096, claudeCommon))

        // ========== DeepSeek（OpenAI 兼容，API 不返回元数据） ==========
        val deepseekCommon = listOf(
            LLMCapability.Temperature,
            LLMCapability.Streaming,
            LLMCapability.Tools
        )

        register(LLModel(LLMProvider.DeepSeek, "deepseek-chat", "DeepSeek V3", 65536, 8192, deepseekCommon))
        register(LLModel(LLMProvider.DeepSeek, "deepseek-reasoner", "DeepSeek R1", 65536, 8192, deepseekCommon + LLMCapability.Reasoning))

        // ========== 硅基流动（OpenAI 兼容，API 不返回元数据） ==========
        register(LLModel(LLMProvider.SiliconFlow, "deepseek-ai/DeepSeek-V3", "DeepSeek V3", 65536, 8192, deepseekCommon))
        register(LLModel(LLMProvider.SiliconFlow, "deepseek-ai/DeepSeek-R1", "DeepSeek R1", 65536, 8192, deepseekCommon + LLMCapability.Reasoning))
        register(LLModel(LLMProvider.SiliconFlow, "Qwen/Qwen3-235B-A22B", "Qwen3 235B", 131072, 8192, deepseekCommon + LLMCapability.Reasoning))

        // 注意：Gemini 不需要硬编码后备数据，因为 Gemini API 返回完整的元数据
        // 如果 API 调用失败，会使用 getOrDefault() 的默认值
    }
}
