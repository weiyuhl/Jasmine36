package com.lhzkml.jasmine.core.prompt.llm

import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.ChatResult
import com.lhzkml.jasmine.core.prompt.model.Prompt
import com.lhzkml.jasmine.core.prompt.model.PromptBuilder
import com.lhzkml.jasmine.core.prompt.model.ToolChoice
import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.prompt
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 结构化响应
 * 参考 koog 的 StructuredResponse，封装解析后的结构化数据和原始内容。
 *
 * @param T 结构化数据类型
 * @property data 解析后的结构化数据
 * @property content 原始 LLM 响应内容
 */
data class StructuredResponse<T>(
    val data: T,
    val content: String
)

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

    // ========== 结构化输出 ==========

    companion object {
        /** JSON 解析器，宽松模式以容忍 LLM 输出的格式偏差 */
        private val lenientJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }

    /**
     * 请求 LLM 返回结构化 JSON 输出
     * 参考 koog 的 requestLLMStructured，使用 Manual 模式：
     * 向 LLM 发送 JSON 格式指令和示例，然后解析响应。
     *
     * @param serializer 目标类型的序列化器
     * @param examples 可选的示例列表，帮助 LLM 理解输出格式
     * @return Result 包含解析后的 StructuredResponse 或错误
     */
    suspend fun <T> requestLLMStructured(
        serializer: KSerializer<T>,
        examples: List<T> = emptyList()
    ): Result<StructuredResponse<T>> {
        checkActive()

        // 构建结构化输出指令
        val instructionPrompt = buildString {
            appendLine("You MUST respond with a valid JSON object that matches the following structure.")
            appendLine("Do NOT include any text before or after the JSON. Only output the JSON object.")
            appendLine()
            if (examples.isNotEmpty()) {
                appendLine("## Examples")
                examples.forEachIndexed { index, example ->
                    appendLine("Example ${index + 1}:")
                    appendLine("```json")
                    appendLine(lenientJson.encodeToString(serializer, example))
                    appendLine("```")
                }
                appendLine()
            }
            appendLine("Respond ONLY with a valid JSON object. No markdown, no explanation, just JSON.")
        }

        appendPrompt { user(instructionPrompt) }
        val result = requestLLMWithoutTools()

        return runCatching {
            val jsonContent = extractJson(result.content)
            val data = lenientJson.decodeFromString(serializer, jsonContent)
            StructuredResponse(data = data, content = result.content)
        }
    }

    /**
     * 请求 LLM 返回结构化 JSON 输出（inline reified 版本）
     */
    suspend inline fun <reified T> requestLLMStructured(
        examples: List<T> = emptyList()
    ): Result<StructuredResponse<T>> {
        return requestLLMStructured(
            serializer = kotlinx.serialization.serializer<T>(),
            examples = examples
        )
    }

    /**
     * 从 LLM 响应中提取 JSON 内容
     * 处理 LLM 可能包裹在 markdown 代码块中的情况
     */
    private fun extractJson(content: String): String {
        val trimmed = content.trim()

        // 尝试提取 ```json ... ``` 代码块
        val codeBlockRegex = Regex("```(?:json)?\\s*\\n?(.*?)\\n?```", RegexOption.DOT_MATCHES_ALL)
        val match = codeBlockRegex.find(trimmed)
        if (match != null) {
            return match.groupValues[1].trim()
        }

        // 尝试提取第一个 { ... } 或 [ ... ]
        val jsonStart = trimmed.indexOfFirst { it == '{' || it == '[' }
        val jsonEnd = trimmed.indexOfLast { it == '}' || it == ']' }
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            return trimmed.substring(jsonStart, jsonEnd + 1)
        }

        return trimmed
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
 */
suspend fun LLMSession.replaceHistoryWithTLDR(
    strategy: HistoryCompressionStrategy = HistoryCompressionStrategy.WholeHistory,
    listener: CompressionEventListener? = null
) {
    strategy.compress(this, listener)
}

/**
 * 检查是否需要压缩并自动执行
 * 配合 TokenBudget 策略使用
 */
suspend fun LLMSession.compressIfNeeded(
    strategy: HistoryCompressionStrategy.TokenBudget,
    listener: CompressionEventListener? = null
) {
    if (strategy.shouldCompress(prompt.messages)) {
        replaceHistoryWithTLDR(strategy, listener = listener)
    }
}
