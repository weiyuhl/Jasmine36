package com.lhzkml.jasmine.core.prompt.llm

import com.lhzkml.jasmine.core.prompt.model.ChatMessage

/**
 * 上下文窗口管理器
 *
 * 策略：滑动窗口
 * 1. 始终保留所有 system 消息（通常在列表开头）
 * 2. 从最新消息往前保留，直到接近 maxTokens 上限
 * 3. 为模型回复预留 reservedTokens 的空间
 *
 * @param maxTokens 模型的最大上下文长度（token 数）
 * @param reservedTokens 为模型回复预留的 token 数
 * @param tokenizer Token 计数器实现
 */
class ContextManager(
    val maxTokens: Int = DEFAULT_MAX_TOKENS,
    val reservedTokens: Int = DEFAULT_RESERVED_TOKENS,
    private val tokenizer: Tokenizer = TokenEstimator
) {

    companion object {
        const val DEFAULT_MAX_TOKENS = 8192
        const val DEFAULT_RESERVED_TOKENS = 1024

        /**
         * 从模型元数据自动创建 ContextManager
         */
        fun fromModel(model: LLModel, tokenizer: Tokenizer = TokenEstimator): ContextManager {
            return ContextManager(
                maxTokens = model.contextLength,
                reservedTokens = model.recommendedReservedTokens,
                tokenizer = tokenizer
            )
        }

        /**
         * 根据模型 ID 和供应商自动创建 ContextManager
         */
        fun forModel(modelId: String, provider: LLMProvider, tokenizer: Tokenizer = TokenEstimator): ContextManager {
            val model = ModelRegistry.getOrDefault(modelId, provider)
            return fromModel(model, tokenizer)
        }
    }

    /** 可用于消息的 token 预算 */
    val availableTokens: Int
        get() = maxTokens - reservedTokens

    /**
     * 裁剪消息列表，使其不超过 token 预算
     */
    fun trimMessages(messages: List<ChatMessage>): List<ChatMessage> {
        if (messages.isEmpty()) return messages

        val budget = availableTokens

        val systemMessages = messages.filter { it.role == "system" }
        val nonSystemMessages = messages.filter { it.role != "system" }

        var systemTokens = 0
        for (msg in systemMessages) {
            systemTokens += tokenizer.countMessageTokens(msg.role, msg.content)
        }

        if (systemTokens >= budget) {
            return systemMessages
        }

        val remainingBudget = budget - systemTokens
        var usedTokens = 0
        val keptNonSystem = mutableListOf<ChatMessage>()

        for (msg in nonSystemMessages.reversed()) {
            val msgTokens = tokenizer.countMessageTokens(msg.role, msg.content)
            if (usedTokens + msgTokens > remainingBudget) {
                break
            }
            usedTokens += msgTokens
            keptNonSystem.add(0, msg)
        }

        return systemMessages + keptNonSystem
    }

    /**
     * 估算消息列表的总 token 数
     */
    fun estimateTokens(messages: List<ChatMessage>): Int {
        return messages.sumOf { tokenizer.countMessageTokens(it.role, it.content) }
    }

    /**
     * 检查消息列表是否超出预算
     */
    fun isOverBudget(messages: List<ChatMessage>): Boolean {
        return estimateTokens(messages) > availableTokens
    }
}
