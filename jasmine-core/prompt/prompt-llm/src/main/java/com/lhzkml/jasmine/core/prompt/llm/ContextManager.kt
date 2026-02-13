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
 */
class ContextManager(
    val maxTokens: Int = DEFAULT_MAX_TOKENS,
    val reservedTokens: Int = DEFAULT_RESERVED_TOKENS
) {

    companion object {
        const val DEFAULT_MAX_TOKENS = 8192
        const val DEFAULT_RESERVED_TOKENS = 1024
    }

    /** 可用于消息的 token 预算 */
    val availableTokens: Int
        get() = maxTokens - reservedTokens

    /**
     * 裁剪消息列表，使其不超过 token 预算
     *
     * @param messages 完整的消息列表
     * @return 裁剪后的消息列表，保证：
     *   - 所有 system 消息保留
     *   - 尽可能多地保留最近的消息
     *   - 总 token 数不超过 availableTokens
     */
    fun trimMessages(messages: List<ChatMessage>): List<ChatMessage> {
        if (messages.isEmpty()) return messages

        val budget = availableTokens

        // 分离 system 消息和非 system 消息
        val systemMessages = messages.filter { it.role == "system" }
        val nonSystemMessages = messages.filter { it.role != "system" }

        // 计算 system 消息占用的 token
        var systemTokens = 0
        for (msg in systemMessages) {
            systemTokens += TokenEstimator.estimateMessage(msg.role, msg.content)
        }

        // system 消息本身就超预算，只保留 system
        if (systemTokens >= budget) {
            return systemMessages
        }

        // 从最新消息往前，尽可能多地保留
        val remainingBudget = budget - systemTokens
        var usedTokens = 0
        val keptNonSystem = mutableListOf<ChatMessage>()

        for (msg in nonSystemMessages.reversed()) {
            val msgTokens = TokenEstimator.estimateMessage(msg.role, msg.content)
            if (usedTokens + msgTokens > remainingBudget) {
                break
            }
            usedTokens += msgTokens
            keptNonSystem.add(0, msg) // 插入到头部，保持原始顺序
        }

        return systemMessages + keptNonSystem
    }

    /**
     * 估算消息列表的总 token 数
     */
    fun estimateTokens(messages: List<ChatMessage>): Int {
        return messages.sumOf { TokenEstimator.estimateMessage(it.role, it.content) }
    }

    /**
     * 检查消息列表是否超出预算
     */
    fun isOverBudget(messages: List<ChatMessage>): Boolean {
        return estimateTokens(messages) > availableTokens
    }
}
