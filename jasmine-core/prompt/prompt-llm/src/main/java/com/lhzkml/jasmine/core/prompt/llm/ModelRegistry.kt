package com.lhzkml.jasmine.core.prompt.llm

/**
 * 已知模型注册表
 *
 * 维护各供应商常见模型的元数据信息。
 * 当用户选择模型时，可以从这里查找元数据来自动配置上下文窗口等参数。
 *
 * 如果模型不在注册表中，会返回一个带有默认值的 LLModel。
 */
object ModelRegistry {

    private val models = mutableMapOf<String, LLModel>()

    init {
        registerBuiltInModels()
    }

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
     * 注册自定义模型
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

    const val DEFAULT_CONTEXT_LENGTH = 8192
    const val DEFAULT_MAX_OUTPUT = 4096

    private fun registerBuiltInModels() {
        // ========== OpenAI ==========
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

        // ========== Claude ==========
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

        // ========== Gemini ==========
        val geminiCommon = listOf(
            LLMCapability.Temperature,
            LLMCapability.Streaming,
            LLMCapability.Tools,
            LLMCapability.Vision,
            LLMCapability.StructuredOutput
        )

        register(LLModel(LLMProvider.Gemini, "gemini-2.5-flash", "Gemini 2.5 Flash", 1048576, 65536, geminiCommon + LLMCapability.Reasoning))
        register(LLModel(LLMProvider.Gemini, "gemini-2.5-pro", "Gemini 2.5 Pro", 1048576, 65536, geminiCommon + LLMCapability.Reasoning))
        register(LLModel(LLMProvider.Gemini, "gemini-2.0-flash", "Gemini 2.0 Flash", 1048576, 8192, geminiCommon))
        register(LLModel(LLMProvider.Gemini, "gemini-1.5-pro", "Gemini 1.5 Pro", 2097152, 8192, geminiCommon + LLMCapability.Audio + LLMCapability.Video))
        register(LLModel(LLMProvider.Gemini, "gemini-1.5-flash", "Gemini 1.5 Flash", 1048576, 8192, geminiCommon))

        // ========== DeepSeek ==========
        val deepseekCommon = listOf(
            LLMCapability.Temperature,
            LLMCapability.Streaming,
            LLMCapability.Tools
        )

        register(LLModel(LLMProvider.DeepSeek, "deepseek-chat", "DeepSeek V3", 65536, 8192, deepseekCommon))
        register(LLModel(LLMProvider.DeepSeek, "deepseek-reasoner", "DeepSeek R1", 65536, 8192, deepseekCommon + LLMCapability.Reasoning))

        // ========== 硅基流动（常用模型） ==========
        register(LLModel(LLMProvider.SiliconFlow, "deepseek-ai/DeepSeek-V3", "DeepSeek V3", 65536, 8192, deepseekCommon))
        register(LLModel(LLMProvider.SiliconFlow, "deepseek-ai/DeepSeek-R1", "DeepSeek R1", 65536, 8192, deepseekCommon + LLMCapability.Reasoning))
        register(LLModel(LLMProvider.SiliconFlow, "Qwen/Qwen3-235B-A22B", "Qwen3 235B", 131072, 8192, deepseekCommon + LLMCapability.Reasoning))
    }
}
