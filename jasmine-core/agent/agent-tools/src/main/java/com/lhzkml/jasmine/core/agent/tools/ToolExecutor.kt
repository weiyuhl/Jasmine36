package com.lhzkml.jasmine.core.agent.tools

import com.lhzkml.jasmine.core.prompt.llm.ChatClient
import com.lhzkml.jasmine.core.prompt.llm.HistoryCompressionStrategy
import com.lhzkml.jasmine.core.prompt.llm.LLMSession
import com.lhzkml.jasmine.core.prompt.llm.StreamResult
import com.lhzkml.jasmine.core.prompt.llm.compressIfNeeded
import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.ChatResult
import com.lhzkml.jasmine.core.prompt.model.Prompt
import com.lhzkml.jasmine.core.prompt.model.SamplingParams
import com.lhzkml.jasmine.core.prompt.model.Usage
import com.lhzkml.jasmine.core.prompt.model.prompt

/**
 * 工具执行器 — Agent Loop
 * 参考 koog 的 agent 执行策略，实现自动工具调用循环：
 *
 * 1. 发送消息给 LLM（附带工具描述）
 * 2. 如果 LLM 返回 tool_calls → 执行工具 → 将结果追加到 prompt → 回到步骤 1
 * 3. 如果 LLM 返回普通文本 → 结束循环，返回最终结果
 *
 * 支持两种使用方式：
 * - 传统方式：传入 messages 列表（向后兼容）
 * - Prompt 方式：传入 Prompt 对象，利用 LLMSession 自动管理提示词
 */
class ToolExecutor(
    private val client: ChatClient,
    private val registry: ToolRegistry,
    private val maxIterations: Int = 10,
    private val compressionStrategy: HistoryCompressionStrategy.TokenBudget? = null
) {
    // ========== Prompt + LLMSession 方式 ==========

    /**
     * 使用 Prompt 执行 agent loop（非流式）
     * LLMSession 自动管理提示词累积
     */
    suspend fun execute(prompt: Prompt, model: String): ChatResult {
        val session = LLMSession(client, model, prompt, registry.descriptors())
        return session.use { executeLoop(it) }
    }

    /**
     * 使用 Prompt 执行 agent loop（流式）
     */
    suspend fun executeStream(
        prompt: Prompt,
        model: String,
        onChunk: suspend (String) -> Unit
    ): StreamResult {
        val session = LLMSession(client, model, prompt, registry.descriptors())
        return session.use { executeStreamLoop(it, onChunk) }
    }

    private suspend fun executeLoop(session: LLMSession): ChatResult {
        var totalUsage = Usage(0, 0, 0)
        var iterations = 0

        while (iterations < maxIterations) {
            iterations++

            // 自动压缩检查
            compressionStrategy?.let { session.compressIfNeeded(it) }

            val result = session.requestLLM()
            totalUsage = totalUsage.add(result.usage)

            if (!result.hasToolCalls) return result.copy(usage = totalUsage)

            // 执行工具，结果自动追加到 session.prompt
            val toolResults = registry.executeAll(result.toolCalls)
            session.appendPrompt {
                toolResults.forEach { message(ChatMessage.toolResult(it)) }
            }
        }

        return ChatResult(
            content = "Error: Exceeded maximum tool call iterations ($maxIterations)",
            usage = totalUsage, finishReason = "max_iterations"
        )
    }

    private suspend fun executeStreamLoop(
        session: LLMSession,
        onChunk: suspend (String) -> Unit
    ): StreamResult {
        var totalUsage = Usage(0, 0, 0)
        var iterations = 0

        while (iterations < maxIterations) {
            iterations++

            // 自动压缩检查
            compressionStrategy?.let { session.compressIfNeeded(it) }

            val result = session.requestLLMStream(onChunk)
            totalUsage = totalUsage.add(result.usage)

            if (!result.hasToolCalls) return result.copy(usage = totalUsage)

            val toolResults = registry.executeAll(result.toolCalls)
            session.appendPrompt {
                toolResults.forEach { message(ChatMessage.toolResult(it)) }
            }
        }

        return StreamResult(
            content = "Error: Exceeded maximum tool call iterations ($maxIterations)",
            usage = totalUsage, finishReason = "max_iterations"
        )
    }

    // ========== 传统方式（向后兼容） ==========

    /**
     * 传统方式：传入 messages 列表执行 agent loop（非流式）
     */
    suspend fun execute(
        messages: List<ChatMessage>,
        model: String,
        maxTokens: Int? = null,
        samplingParams: SamplingParams? = null
    ): ChatResult {
        val p = prompt("agent") {
            messages(messages)
        }.copy(maxTokens = maxTokens, samplingParams = samplingParams ?: SamplingParams.DEFAULT)
        return execute(p, model)
    }

    /**
     * 传统方式：传入 messages 列表执行 agent loop（流式）
     */
    suspend fun executeStream(
        messages: List<ChatMessage>,
        model: String,
        maxTokens: Int? = null,
        samplingParams: SamplingParams? = null,
        onChunk: suspend (String) -> Unit
    ): StreamResult {
        val p = prompt("agent") {
            messages(messages)
        }.copy(maxTokens = maxTokens, samplingParams = samplingParams ?: SamplingParams.DEFAULT)
        return executeStream(p, model, onChunk)
    }

    private fun Usage.add(other: Usage?): Usage {
        if (other == null) return this
        return Usage(
            promptTokens + other.promptTokens,
            completionTokens + other.completionTokens,
            totalTokens + other.totalTokens
        )
    }

    private suspend fun <T> LLMSession.use(block: suspend (LLMSession) -> T): T {
        try {
            return block(this)
        } finally {
            close()
        }
    }
}
