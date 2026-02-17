package com.lhzkml.jasmine.core.prompt.llm

import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.ChatResult
import com.lhzkml.jasmine.core.prompt.model.Prompt
import com.lhzkml.jasmine.core.prompt.model.PromptBuilder
import com.lhzkml.jasmine.core.prompt.model.ToolChoice
import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.prompt

/**
 * LLM 会话
 * 参考 koog 的 AIAgentLLMWriteSession，管理提示词的累积式构建和 LLM 交互。
 *
 * 核心设计：
 * - prompt 是累积式的，每次 LLM 交互后 response 自动追加
 * - 工具描述通过 API 参数传递（function calling），不拼接到文本
 * - appendPrompt {} 用于追加消息
 * - rewritePrompt {} 用于完全重写（如历史压缩）
 *
 * @param client LLM 客户端
 * @param model 模型名称
 * @param initialPrompt 初始提示词（通常包含 system 消息）
 * @param tools 可用工具描述列表
 */
class LLMSession(
    private val client: ChatClient,
    val model: String,
    initialPrompt: Prompt,
    var tools: List<ToolDescriptor> = emptyList()
) : AutoCloseable {

    /** 暴露 client 用于创建临时 session（如 retrievalModel 场景） */
    internal val currentClient: ChatClient get() = client

    /** 当前提示词（累积式） */
    var prompt: Prompt = initialPrompt
        internal set

    private var isActive = true

    private fun checkActive() {
        check(isActive) { "Cannot use session after it was closed" }
    }

    // ========== Prompt 操作 ==========

    /**
     * 追加消息到当前 prompt
     * ```kotlin
     * session.appendPrompt {
     *     user("What is the weather?")
     * }
     * ```
     */
    fun appendPrompt(body: PromptBuilder.() -> Unit) {
        checkActive()
        prompt = prompt(prompt, body)
    }

    /**
     * 完全重写 prompt（用于历史压缩等场景）
     */
    fun rewritePrompt(body: (Prompt) -> Prompt) {
        checkActive()
        prompt = body(prompt)
    }

    /**
     * 清空历史消息
     */
    fun clearHistory() {
        checkActive()
        prompt = prompt.withMessages { emptyList() }
    }

    /**
     * 保留最后 N 条消息（可选保留 system 消息）
     */
    fun leaveLastNMessages(n: Int, preserveSystem: Boolean = true) {
        checkActive()
        prompt = prompt.withMessages { messages ->
            val threshold = messages.size - n
            messages.filterIndexed { index, msg ->
                index >= threshold || (preserveSystem && msg.role == "system")
            }
        }
    }

    /**
     * 删除末尾 N 条消息
     */
    fun dropLastNMessages(n: Int) {
        checkActive()
        prompt = prompt.withMessages { it.dropLast(n) }
    }

    /**
     * 只保留指定时间戳之后的消息（保留 system 消息）
     * 参考 koog 的 leaveMessagesFromTimestamp
     *
     * @param timestamp 起始时间戳（毫秒），只保留此时间之后的消息
     * @param preserveSystem 是否保留所有 system 消息，默认 true
     */
    fun leaveMessagesFromTimestamp(timestamp: Long, preserveSystem: Boolean = true) {
        checkActive()
        prompt = prompt.withMessages { messages ->
            messages.filter { msg ->
                val ts = msg.timestamp
                (preserveSystem && msg.role == "system") ||
                    (ts != null && ts >= timestamp)
            }
        }
    }

    /**
     * 删除末尾的工具调用消息
     */
    fun dropTrailingToolCalls() {
        checkActive()
        prompt = prompt.withMessages { messages ->
            messages.dropLastWhile { it.role == "tool" || (it.role == "assistant" && it.toolCalls != null) }
        }
    }

    // ========== 工具选择策略 ==========

    fun setToolChoiceAuto() {
        prompt = prompt.withToolChoice(ToolChoice.Auto)
    }

    fun setToolChoiceRequired() {
        prompt = prompt.withToolChoice(ToolChoice.Required)
    }

    fun setToolChoiceNone() {
        prompt = prompt.withToolChoice(ToolChoice.None)
    }

    fun setToolChoiceNamed(toolName: String) {
        prompt = prompt.withToolChoice(ToolChoice.Named(toolName))
    }

    fun unsetToolChoice() {
        prompt = prompt.withToolChoice(null)
    }

    // ========== LLM 请求 ==========

    /**
     * 发送请求给 LLM（带工具），自动追加 response 到 prompt
     * @return LLM 的回复结果
     */
    suspend fun requestLLM(): ChatResult {
        checkActive()
        val result = client.chatWithUsage(
            messages = prompt.messages,
            model = model,
            maxTokens = prompt.maxTokens,
            samplingParams = prompt.samplingParams,
            tools = tools
        )
        // 自动追加 response
        appendPrompt {
            if (result.hasToolCalls) {
                assistantWithToolCalls(result.toolCalls, result.content)
            } else {
                assistant(result.content)
            }
        }
        return result
    }

    /**
     * 发送请求给 LLM（不带工具），自动追加 response
     */
    suspend fun requestLLMWithoutTools(): ChatResult {
        checkActive()
        val result = client.chatWithUsage(
            messages = prompt.messages,
            model = model,
            maxTokens = prompt.maxTokens,
            samplingParams = prompt.samplingParams,
            tools = emptyList()
        )
        appendPrompt { assistant(result.content) }
        return result
    }

    /**
     * 流式请求 LLM（带工具），自动追加 response
     */
    suspend fun requestLLMStream(
        onChunk: suspend (String) -> Unit,
        onThinking: suspend (String) -> Unit = {}
    ): StreamResult {
        checkActive()
        val result = client.chatStreamWithUsageAndThinking(
            messages = prompt.messages,
            model = model,
            maxTokens = prompt.maxTokens,
            samplingParams = prompt.samplingParams,
            tools = tools,
            onChunk = onChunk,
            onThinking = onThinking
        )
        appendPrompt {
            if (result.hasToolCalls) {
                assistantWithToolCalls(result.toolCalls, result.content)
            } else {
                assistant(result.content)
            }
        }
        return result
    }

    /**
     * 流式请求 LLM（不带工具），自动追加 response
     */
    suspend fun requestLLMStreamWithoutTools(
        onChunk: suspend (String) -> Unit,
        onThinking: suspend (String) -> Unit = {}
    ): StreamResult {
        checkActive()
        val result = client.chatStreamWithUsageAndThinking(
            messages = prompt.messages,
            model = model,
            maxTokens = prompt.maxTokens,
            samplingParams = prompt.samplingParams,
            tools = emptyList(),
            onChunk = onChunk,
            onThinking = onThinking
        )
        appendPrompt { assistant(result.content) }
        return result
    }

    override fun close() {
        isActive = false
    }
}

/**
 * 便捷函数：创建 session 并执行操作
 * ```kotlin
 * val result = client.session(model, prompt, tools) {
 *     appendPrompt { user("Hello!") }
 *     requestLLM()
 * }
 * ```
 */
suspend fun <T> ChatClient.session(
    model: String,
    prompt: Prompt,
    tools: List<ToolDescriptor> = emptyList(),
    block: suspend LLMSession.() -> T
): T {
    val session = LLMSession(this, model, prompt, tools)
    return session.use { it.block() }
}

private suspend fun <T> LLMSession.use(block: suspend (LLMSession) -> T): T {
    try {
        return block(this)
    } finally {
        close()
    }
}

// ========== 历史压缩扩展 ==========

/**
 * 用 TLDR 摘要替换历史消息
 * 参考 koog 的 replaceHistoryWithTLDR
 *
 * @param strategy 压缩策略，默认 WholeHistory
 * @param preserveMemory 是否保留记忆相关消息
 */
suspend fun LLMSession.replaceHistoryWithTLDR(
    strategy: HistoryCompressionStrategy = HistoryCompressionStrategy.WholeHistory,
    preserveMemory: Boolean = true
) {
    val memoryMessages = if (preserveMemory) {
        prompt.messages.filter { msg ->
            msg.content.contains("Here are the relevant facts from memory") ||
                msg.content.contains("Memory feature is not enabled") ||
                msg.content.contains("CONTEXT RESTORATION") ||
                msg.content.contains("compressed summary") ||
                msg.content.contains("Key Objectives")
        }
    } else {
        emptyList()
    }
    strategy.compress(this, memoryMessages)
}

/**
 * 检查是否需要压缩并自动执行
 * 配合 TokenBudget 策略使用
 */
suspend fun LLMSession.compressIfNeeded(
    strategy: HistoryCompressionStrategy.TokenBudget
) {
    if (strategy.shouldCompress(prompt.messages)) {
        replaceHistoryWithTLDR(strategy)
    }
}
