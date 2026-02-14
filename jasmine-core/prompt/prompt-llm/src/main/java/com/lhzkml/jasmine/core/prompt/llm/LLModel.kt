package com.lhzkml.jasmine.core.prompt.llm

/**
 * LLM 模型元数据
 *
 * 描述一个具体模型的完整信息，包括供应商、上下文长度、能力等。
 * 可用于自动配置上下文窗口、判断模型是否支持某功能等。
 *
 * @param provider 所属供应商
 * @param id 模型标识（用于 API 调用）
 * @param displayName 显示名称
 * @param contextLength 最大上下文长度（token 数）
 * @param maxOutputTokens 最大输出 token 数，null 表示未知
 * @param capabilities 模型支持的能力列表
 */
data class LLModel(
    val provider: LLMProvider,
    val id: String,
    val displayName: String = id,
    val contextLength: Int,
    val maxOutputTokens: Int? = null,
    val capabilities: List<LLMCapability> = emptyList()
) {
    /**
     * 检查模型是否支持某项能力
     */
    fun supports(capability: LLMCapability): Boolean =
        capabilities.any { it.id == capability.id }

    /**
     * 推荐的上下文预留 token 数（为回复留空间）
     * 如果已知 maxOutputTokens 则使用它，否则默认预留 contextLength 的 1/8
     */
    val recommendedReservedTokens: Int
        get() = maxOutputTokens ?: (contextLength / 8)
}
