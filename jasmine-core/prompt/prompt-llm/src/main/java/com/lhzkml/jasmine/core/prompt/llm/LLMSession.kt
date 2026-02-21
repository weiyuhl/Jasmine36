package com.lhzkml.jasmine.core.prompt.llm

import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.ChatResult
import com.lhzkml.jasmine.core.prompt.model.Message
import com.lhzkml.jasmine.core.prompt.model.Prompt
import com.lhzkml.jasmine.core.prompt.model.PromptBuilder
import com.lhzkml.jasmine.core.prompt.model.ToolChoice
import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.prompt
import com.lhzkml.jasmine.core.prompt.model.toChatMessage
import com.lhzkml.jasmine.core.prompt.model.toAssistantMessage
import com.lhzkml.jasmine.core.prompt.model.toMessages
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
 * LLM 会话基类
 * 移植自 koog 的 AIAgentLLMSession (sealed base class)。
 *
 * 提供所有 LLM 请求方法（只读），不自动追加响应到 prompt。
 * 属性 prompt/tools/model 在基类中为只读 (val)。
 *
 * 子类:
 * - LLMReadSession: 纯只读会话，不能修改 prompt
 * - LLMWriteSession: 可写会话，override 请求方法自动追加响应，提供 prompt 操作方法
 *
 * @param client LLM 客户端
 * @param model 模型名称
 * @param initialPrompt 初始提示词（通常包含 system 消息）
 * @param tools 可用工具描述列表
 */
sealed class LLMSession(
    private val client: ChatClient,
    open val model: String,
    initialPrompt: Prompt,
    open val tools: List<ToolDescriptor> = emptyList()
) : AutoCloseable {

    /** 暴露 client 用于创建临时 session（如 retrievalModel 场景） */
    internal val currentClient: ChatClient get() = client

    /** 当前提示词 */
    open val prompt: Prompt = initialPrompt

    protected var isActive = true

    protected fun checkActive() {
        check(isActive) { "Cannot use session after it was closed" }
    }

    // ========== LLM 请求（只读，不自动追加响应） ==========

    /**
     * 发送请求给 LLM（带工具）
     * 移植自 koog 的 AIAgentLLMSession.requestLLM
     *
     * @return LLM 的回复结果
     */
    open suspend fun requestLLM(): ChatResult {
        checkActive()
        return client.chatWithUsage(
            messages = prompt.messages,
            model = model,
            maxTokens = prompt.maxTokens,
            samplingParams = prompt.samplingParams,
            tools = tools,
            toolChoice = prompt.toolChoice
        )
    }

    /**
     * 发送请求给 LLM（不带工具）
     * 移植自 koog 的 AIAgentLLMSession.requestLLMWithoutTools
     */
    open suspend fun requestLLMWithoutTools(): ChatResult {
        checkActive()
        return client.chatWithUsage(
            messages = prompt.messages,
            model = model,
            maxTokens = prompt.maxTokens,
            samplingParams = prompt.samplingParams,
            tools = emptyList()
        )
    }

    /**
     * 强制 LLM 只能调用工具（不能生成纯文本）
     * 移植自 koog 的 AIAgentLLMSession.requestLLMOnlyCallingTools
     *
     * @return LLM 响应结果（通常包含 tool_calls）
     */
    open suspend fun requestLLMOnlyCallingTools(): ChatResult {
        checkActive()
        return client.chatWithUsage(
            messages = prompt.messages,
            model = model,
            maxTokens = prompt.maxTokens,
            samplingParams = prompt.samplingParams,
            tools = tools,
            toolChoice = ToolChoice.Required
        )
    }

    /**
     * 强制 LLM 使用指定工具
     * 移植自 koog 的 AIAgentLLMSession.requestLLMForceOneTool
     *
     * @param toolName 强制使用的工具名称
     * @return LLM 响应结果
     */
    open suspend fun requestLLMForceOneTool(toolName: String): ChatResult {
        checkActive()
        return client.chatWithUsage(
            messages = prompt.messages,
            model = model,
            maxTokens = prompt.maxTokens,
            samplingParams = prompt.samplingParams,
            tools = tools,
            toolChoice = ToolChoice.Named(toolName)
        )
    }

    /**
     * 请求 LLM 返回多个响应
     * 移植自 koog 的 AIAgentLLMSession.requestLLMMultiple
     *
     * @return LLM 响应结果列表
     */
    open suspend fun requestLLMMultiple(): List<ChatResult> {
        checkActive()
        val result = client.chatWithUsage(
            messages = prompt.messages,
            model = model,
            maxTokens = prompt.maxTokens,
            samplingParams = prompt.samplingParams,
            tools = tools
        )
        return listOf(result)
    }

    /**
     * 请求 LLM 返回多个响应，且只能调用工具
     * 移植自 koog 的 AIAgentLLMSession.requestLLMMultipleOnlyCallingTools
     *
     * @return LLM 响应结果列表
     */
    open suspend fun requestLLMMultipleOnlyCallingTools(): List<ChatResult> {
        checkActive()
        val result = client.chatWithUsage(
            messages = prompt.messages,
            model = model,
            maxTokens = prompt.maxTokens,
            samplingParams = prompt.samplingParams,
            tools = tools,
            toolChoice = ToolChoice.Required
        )
        return listOf(result)
    }

    /**
     * 请求 LLM 返回多个响应（不带工具）
     * 移植自 koog 的 AIAgentLLMSession.requestLLMMultipleWithoutTools
     *
     * @return LLM 响应结果列表
     */
    open suspend fun requestLLMMultipleWithoutTools(): List<ChatResult> {
        checkActive()
        val result = client.chatWithUsage(
            messages = prompt.messages,
            model = model,
            maxTokens = prompt.maxTokens,
            samplingParams = prompt.samplingParams,
            tools = emptyList()
        )
        return listOf(result)
    }


    /**
     * 流式请求 LLM（带工具）
     * 移植自 koog 的 AIAgentLLMSession.requestLLMStreaming
     */
    open suspend fun requestLLMStream(
        onChunk: suspend (String) -> Unit,
        onThinking: suspend (String) -> Unit = {}
    ): StreamResult {
        checkActive()
        return client.chatStreamWithUsageAndThinking(
            messages = prompt.messages,
            model = model,
            maxTokens = prompt.maxTokens,
            samplingParams = prompt.samplingParams,
            tools = tools,
            toolChoice = prompt.toolChoice,
            onChunk = onChunk,
            onThinking = onThinking
        )
    }

    /**
     * 流式请求 LLM（不带工具）
     */
    open suspend fun requestLLMStreamWithoutTools(
        onChunk: suspend (String) -> Unit,
        onThinking: suspend (String) -> Unit = {}
    ): StreamResult {
        checkActive()
        return client.chatStreamWithUsageAndThinking(
            messages = prompt.messages,
            model = model,
            maxTokens = prompt.maxTokens,
            samplingParams = prompt.samplingParams,
            tools = emptyList(),
            onChunk = onChunk,
            onThinking = onThinking
        )
    }

    // ========== Message 类型的 LLM 请求 ==========

    /**
     * 发送请求给 LLM，返回 Message.Response
     * 移植自 koog 的类型化消息系统
     */
    open suspend fun requestLLMAsMessage(): Message.Response {
        return requestLLM().toAssistantMessage()
    }

    /**
     * 发送请求给 LLM（不带工具），返回 Message.Response
     */
    open suspend fun requestLLMWithoutToolsAsMessage(): Message.Response {
        return requestLLMWithoutTools().toAssistantMessage()
    }

    /**
     * 发送请求给 LLM，返回完整的 Message.Response 列表
     * 包含 thinking + assistant + tool calls
     */
    open suspend fun requestLLMAsMessages(): List<Message.Response> {
        return requestLLM().toMessages()
    }

    // ========== 结构化输出 ==========

    companion object {
        /** JSON 解析器，宽松模式以容忍 LLM 输出的格式偏差 */
        internal val lenientJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }

    /**
     * 请求 LLM 返回结构化 JSON 输出
     * 移植自 koog 的 AIAgentLLMSession.requestLLMStructured
     *
     * 注意: 基类版本不自动追加响应到 prompt。
     * LLMWriteSession 会 override 此方法以自动追加。
     *
     * @param serializer 目标类型的序列化器
     * @param examples 可选的示例列表，帮助 LLM 理解输出格式
     * @return Result 包含解析后的 StructuredResponse 或错误
     */
    open suspend fun <T> requestLLMStructured(
        serializer: KSerializer<T>,
        examples: List<T> = emptyList()
    ): Result<StructuredResponse<T>> {
        checkActive()

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

        // 构建临时 prompt（不修改 session 的 prompt）
        val tempPrompt = prompt(prompt) { user(instructionPrompt) }
        val result = client.chatWithUsage(
            messages = tempPrompt.messages,
            model = model,
            maxTokens = tempPrompt.maxTokens,
            samplingParams = tempPrompt.samplingParams,
            tools = emptyList()
        )

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
     */
    protected fun extractJson(content: String): String {
        val trimmed = content.trim()

        val codeBlockRegex = Regex("```(?:json)?\\s*\\n?(.*?)\\n?```", RegexOption.DOT_MATCHES_ALL)
        val match = codeBlockRegex.find(trimmed)
        if (match != null) {
            return match.groupValues[1].trim()
        }

        val jsonStart = trimmed.indexOfFirst { it == '{' || it == '[' }
        val jsonEnd = trimmed.indexOfLast { it == '}' || it == ']' }
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            return trimmed.substring(jsonStart, jsonEnd + 1)
        }

        return trimmed
    }

    final override fun close() {
        isActive = false
    }
}


// ========== LLMReadSession ==========

/**
 * 只读 LLM 会话
 * 移植自 koog 的 AIAgentLLMReadSession。
 *
 * 只能执行 LLM 请求，不能修改 prompt/tools/model。
 * 所有请求方法不自动追加响应到 prompt。
 *
 * 适用场景:
 * - 需要查询 LLM 但不影响主对话流的场景
 * - 并行请求多个 LLM 的场景
 * - 只读分析/评估场景
 */
class LLMReadSession(
    client: ChatClient,
    model: String,
    prompt: Prompt,
    tools: List<ToolDescriptor> = emptyList()
) : LLMSession(client, model, prompt, tools)

// ========== LLMWriteSession ==========

/**
 * 可写 LLM 会话
 * 移植自 koog 的 AIAgentLLMWriteSession。
 *
 * 核心设计:
 * - prompt 是累积式的，每次 LLM 交互后 response 自动追加
 * - 工具描述通过 API 参数传递（function calling），不拼接到文本
 * - appendPrompt {} 用于追加消息
 * - rewritePrompt {} 用于完全重写（如历史压缩）
 * - changeModel() 用于动态切换模型
 *
 * 与基类的区别:
 * - prompt/tools/model 属性可变 (var)
 * - 所有 LLM 请求方法自动追加响应到 prompt
 * - 提供 appendPrompt/rewritePrompt/clearHistory 等 prompt 操作方法
 *
 * @param client LLM 客户端
 * @param model 模型名称
 * @param initialPrompt 初始提示词
 * @param tools 可用工具描述列表
 */
class LLMWriteSession(
    client: ChatClient,
    model: String,
    initialPrompt: Prompt,
    tools: List<ToolDescriptor> = emptyList()
) : LLMSession(client, model, initialPrompt, tools) {

    /** 可变 prompt */
    override var prompt: Prompt = initialPrompt

    /** 可变 tools */
    override var tools: List<ToolDescriptor> = tools

    /** 可变 model */
    override var model: String = model

    // ========== Prompt 操作 ==========

    /**
     * 追加消息到当前 prompt
     * 移植自 koog 的 AIAgentLLMWriteSession.appendPrompt
     */
    fun appendPrompt(body: PromptBuilder.() -> Unit) {
        checkActive()
        prompt = prompt(prompt, body)
    }

    /**
     * 完全重写 prompt（用于历史压缩等场景）
     * 移植自 koog 的 AIAgentLLMWriteSession.rewritePrompt
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
     * 移植自 koog 的 leaveMessagesFromTimestamp
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

    // ========== 模型/参数变更 ==========

    /**
     * 动态切换模型
     * 移植自 koog 的 AIAgentLLMWriteSession.changeModel
     */
    fun changeModel(newModel: String) {
        checkActive()
        model = newModel
    }

    // ========== Message 类型的 Prompt 操作 ==========

    /**
     * 追加 Message 类型的消息到 prompt
     */
    fun appendMessage(message: Message) {
        checkActive()
        appendPrompt { message(message.toChatMessage()) }
    }

    /**
     * 批量追加 Message 类型的消息到 prompt
     */
    fun appendMessages(messages: List<Message>) {
        checkActive()
        appendPrompt {
            messages.forEach { message(it.toChatMessage()) }
        }
    }

    // ========== LLM 请求（自动追加响应到 prompt） ==========

    private fun appendAssistantResponse(result: ChatResult) {
        appendPrompt {
            if (result.hasToolCalls) {
                assistantWithToolCalls(result.toolCalls, result.content)
            } else {
                assistant(result.content)
            }
        }
    }

    override suspend fun requestLLM(): ChatResult {
        return super.requestLLM().also { appendAssistantResponse(it) }
    }

    override suspend fun requestLLMWithoutTools(): ChatResult {
        return super.requestLLMWithoutTools().also { result ->
            appendPrompt { assistant(result.content) }
        }
    }

    override suspend fun requestLLMOnlyCallingTools(): ChatResult {
        return super.requestLLMOnlyCallingTools().also { appendAssistantResponse(it) }
    }

    override suspend fun requestLLMForceOneTool(toolName: String): ChatResult {
        return super.requestLLMForceOneTool(toolName).also { appendAssistantResponse(it) }
    }

    override suspend fun requestLLMMultiple(): List<ChatResult> {
        return super.requestLLMMultiple().also { results ->
            results.forEach { appendAssistantResponse(it) }
        }
    }

    override suspend fun requestLLMMultipleOnlyCallingTools(): List<ChatResult> {
        return super.requestLLMMultipleOnlyCallingTools().also { results ->
            results.forEach { appendAssistantResponse(it) }
        }
    }

    override suspend fun requestLLMMultipleWithoutTools(): List<ChatResult> {
        return super.requestLLMMultipleWithoutTools().also { results ->
            results.forEach { result -> appendPrompt { assistant(result.content) } }
        }
    }

    override suspend fun requestLLMStream(
        onChunk: suspend (String) -> Unit,
        onThinking: suspend (String) -> Unit
    ): StreamResult {
        return super.requestLLMStream(onChunk, onThinking).also { result ->
            appendPrompt {
                if (result.hasToolCalls) {
                    assistantWithToolCalls(result.toolCalls, result.content)
                } else {
                    assistant(result.content)
                }
            }
        }
    }

    override suspend fun requestLLMStreamWithoutTools(
        onChunk: suspend (String) -> Unit,
        onThinking: suspend (String) -> Unit
    ): StreamResult {
        return super.requestLLMStreamWithoutTools(onChunk, onThinking).also { result ->
            appendPrompt { assistant(result.content) }
        }
    }

    override suspend fun <T> requestLLMStructured(
        serializer: KSerializer<T>,
        examples: List<T>
    ): Result<StructuredResponse<T>> {
        checkActive()

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
}


// ========== 便捷函数 ==========

/**
 * 便捷函数: 创建 LLMWriteSession 并执行操作
 * 移植自 koog 的 session {} DSL
 */
suspend fun <T> ChatClient.session(
    model: String,
    prompt: Prompt,
    tools: List<ToolDescriptor> = emptyList(),
    block: suspend LLMWriteSession.() -> T
): T {
    val session = LLMWriteSession(this, model, prompt, tools)
    return session.use { it.block() }
}

/**
 * 便捷函数: 创建 LLMReadSession 并执行操作
 */
suspend fun <T> ChatClient.readSession(
    model: String,
    prompt: Prompt,
    tools: List<ToolDescriptor> = emptyList(),
    block: suspend LLMReadSession.() -> T
): T {
    val session = LLMReadSession(this, model, prompt, tools)
    return session.use { it.block() }
}

private suspend fun <T : LLMSession, R> T.use(block: suspend (T) -> R): R {
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
 */
suspend fun LLMWriteSession.replaceHistoryWithTLDR(
    strategy: HistoryCompressionStrategy = HistoryCompressionStrategy.WholeHistory,
    listener: CompressionEventListener? = null
) {
    strategy.compress(this, listener)
}

/**
 * 检查是否需要压缩并自动执行
 */
suspend fun LLMWriteSession.compressIfNeeded(
    strategy: HistoryCompressionStrategy.TokenBudget,
    listener: CompressionEventListener? = null
) {
    if (strategy.shouldCompress(prompt.messages)) {
        replaceHistoryWithTLDR(strategy, listener = listener)
    }
}
