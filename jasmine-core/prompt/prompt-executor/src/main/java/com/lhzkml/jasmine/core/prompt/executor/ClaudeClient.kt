package com.lhzkml.jasmine.core.prompt.executor

import com.lhzkml.jasmine.core.prompt.llm.ChatClient
import com.lhzkml.jasmine.core.prompt.llm.ChatClientException
import com.lhzkml.jasmine.core.prompt.llm.ErrorType
import com.lhzkml.jasmine.core.prompt.llm.LLMProvider
import com.lhzkml.jasmine.core.prompt.llm.RetryConfig
import com.lhzkml.jasmine.core.prompt.llm.StreamResult
import com.lhzkml.jasmine.core.prompt.llm.executeWithRetry
import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.ChatResult
import com.lhzkml.jasmine.core.prompt.model.ClaudeContentBlock
import com.lhzkml.jasmine.core.prompt.model.ClaudeMessage
import com.lhzkml.jasmine.core.prompt.model.ClaudeMessageContent
import com.lhzkml.jasmine.core.prompt.model.ClaudeRequest
import com.lhzkml.jasmine.core.prompt.model.ClaudeResponse
import com.lhzkml.jasmine.core.prompt.model.ClaudeStreamEvent
import com.lhzkml.jasmine.core.prompt.model.ClaudeToolDef
import com.lhzkml.jasmine.core.prompt.model.ModelInfo
import com.lhzkml.jasmine.core.prompt.model.SamplingParams
import com.lhzkml.jasmine.core.prompt.model.ToolCall
import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.Usage
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Anthropic Claude 客户端
 * 使用 Claude 原生 Messages API，支持 Tool Calling
 */
open class ClaudeClient(
    protected val apiKey: String,
    protected val baseUrl: String = DEFAULT_BASE_URL,
    protected val retryConfig: RetryConfig = RetryConfig.DEFAULT,
    httpClient: HttpClient? = null
) : ChatClient {

    companion object {
        const val DEFAULT_BASE_URL = "https://api.anthropic.com"
        const val ANTHROPIC_VERSION = "2023-06-01"
    }

    override val provider: LLMProvider = LLMProvider.Claude

    internal val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    internal val httpClient: HttpClient = httpClient ?: HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(this@ClaudeClient.json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = retryConfig.requestTimeoutMs
            connectTimeoutMillis = 30000
            socketTimeoutMillis = 60000
        }
    }

    // ========== 消息转换 ==========

    /**
     * 将通用 ChatMessage 转换为 Claude 格式
     * Claude 的 system 消息不在 messages 中，而是作为顶层字段
     * tool_use 结果通过 content blocks 传递
     */
    private fun convertMessages(messages: List<ChatMessage>): Pair<String?, List<ClaudeMessage>> {
        var systemPrompt: String? = null
        val claudeMessages = mutableListOf<ClaudeMessage>()

        for (msg in messages) {
            val msgToolCalls = msg.toolCalls
            when {
                msg.role == "system" -> systemPrompt = msg.content

                // assistant 消息带 tool_calls → 转为 tool_use content blocks
                msg.role == "assistant" && !msgToolCalls.isNullOrEmpty() -> {
                    val blocks = mutableListOf<ClaudeContentBlock>()
                    if (msg.content.isNotEmpty()) {
                        blocks.add(ClaudeContentBlock(type = "text", text = msg.content))
                    }
                    msgToolCalls.forEach { tc ->
                        val inputJson = try {
                            json.parseToJsonElement(tc.arguments) as? JsonObject ?: buildJsonObject {}
                        } catch (_: Exception) {
                            buildJsonObject {}
                        }
                        blocks.add(ClaudeContentBlock(type = "tool_use", id = tc.id, name = tc.name, input = inputJson))
                    }
                    claudeMessages.add(ClaudeMessage(role = "assistant", content = ClaudeMessageContent.Blocks(blocks)))
                }

                // tool 结果消息 → 转为 tool_result content block
                msg.role == "tool" -> {
                    val block = ClaudeContentBlock(type = "tool_result", toolUseId = msg.toolCallId, content = msg.content)
                    // Claude 要求 tool_result 在 user 角色消息中
                    claudeMessages.add(ClaudeMessage(role = "user", content = ClaudeMessageContent.Blocks(listOf(block))))
                }

                // 普通 user/assistant 消息
                msg.role == "user" || msg.role == "assistant" -> {
                    claudeMessages.add(ClaudeMessage(role = msg.role, content = ClaudeMessageContent.Text(msg.content)))
                }
            }
        }
        return systemPrompt to claudeMessages
    }

    /**
     * 将 ToolDescriptor 转换为 Claude 工具定义格式
     */
    private fun convertTools(tools: List<ToolDescriptor>): List<ClaudeToolDef>? {
        if (tools.isEmpty()) return null
        return tools.map { tool ->
            ClaudeToolDef(
                name = tool.name,
                description = tool.description,
                inputSchema = tool.toJsonSchema()
            )
        }
    }

    // ========== ChatClient 实现 ==========

    override suspend fun chat(
        messages: List<ChatMessage>, model: String, maxTokens: Int?,
        samplingParams: SamplingParams?, tools: List<ToolDescriptor>
    ): String {
        return chatWithUsage(messages, model, maxTokens, samplingParams, tools).content
    }

    override suspend fun chatWithUsage(
        messages: List<ChatMessage>, model: String, maxTokens: Int?,
        samplingParams: SamplingParams?, tools: List<ToolDescriptor>
    ): ChatResult {
        return executeWithRetry(retryConfig) {
            try {
                val (systemPrompt, claudeMessages) = convertMessages(messages)
                val request = ClaudeRequest(
                    model = model,
                    messages = claudeMessages,
                    maxTokens = maxTokens ?: 4096,
                    system = systemPrompt,
                    temperature = samplingParams?.temperature,
                    topP = samplingParams?.topP,
                    topK = samplingParams?.topK,
                    tools = convertTools(tools)
                )

                val response: HttpResponse = httpClient.post("${baseUrl}/v1/messages") {
                    contentType(ContentType.Application.Json)
                    header("x-api-key", apiKey)
                    header("anthropic-version", ANTHROPIC_VERSION)
                    setBody(request)
                }

                if (!response.status.isSuccess()) {
                    val body = try { response.bodyAsText() } catch (_: Exception) { null }
                    throw ChatClientException.fromStatusCode(provider.name, response.status.value, body)
                }

                val claudeResponse: ClaudeResponse = response.body()

                // 提取文本内容
                val textContent = claudeResponse.content
                    .filter { it.type == "text" }
                    .mapNotNull { it.text }
                    .joinToString("")

                // 提取 thinking 内容
                val thinkingContent = claudeResponse.content
                    .filter { it.type == "thinking" }
                    .mapNotNull { it.text }
                    .joinToString("")
                    .ifEmpty { null }

                // 提取 tool_use 块 → ToolCall
                val toolCalls = claudeResponse.content
                    .filter { it.type == "tool_use" }
                    .map { block ->
                        ToolCall(
                            id = block.id ?: "",
                            name = block.name ?: "",
                            arguments = block.input?.toString() ?: "{}"
                        )
                    }

                val usage = claudeResponse.usage?.let {
                    Usage(
                        promptTokens = it.inputTokens,
                        completionTokens = it.outputTokens,
                        totalTokens = it.inputTokens + it.outputTokens
                    )
                }

                ChatResult(
                    content = textContent,
                    usage = usage,
                    finishReason = claudeResponse.stopReason,
                    toolCalls = toolCalls,
                    thinking = thinkingContent
                )
            } catch (e: ChatClientException) {
                throw e
            } catch (e: UnknownHostException) {
                throw ChatClientException(provider.name, "无法连接到服务器，请检查网络", ErrorType.NETWORK, cause = e)
            } catch (e: ConnectException) {
                throw ChatClientException(provider.name, "连接失败，请检查网络", ErrorType.NETWORK, cause = e)
            } catch (e: SocketTimeoutException) {
                throw ChatClientException(provider.name, "请求超时，请稍后重试", ErrorType.NETWORK, cause = e)
            } catch (e: HttpRequestTimeoutException) {
                throw ChatClientException(provider.name, "请求超时，请稍后重试", ErrorType.NETWORK, cause = e)
            } catch (e: Exception) {
                throw ChatClientException(provider.name, "请求失败: ${e.message}", ErrorType.UNKNOWN, cause = e)
            }
        }
    }

    override fun chatStream(
        messages: List<ChatMessage>, model: String, maxTokens: Int?,
        samplingParams: SamplingParams?, tools: List<ToolDescriptor>
    ): Flow<String> = flow {
        chatStreamWithUsage(messages, model, maxTokens, samplingParams, tools) { chunk ->
            emit(chunk)
        }
    }

    override suspend fun chatStreamWithUsage(
        messages: List<ChatMessage>, model: String, maxTokens: Int?,
        samplingParams: SamplingParams?, tools: List<ToolDescriptor>,
        onChunk: suspend (String) -> Unit
    ): StreamResult {
        return executeWithRetry(retryConfig) {
            try {
                val (systemPrompt, claudeMessages) = convertMessages(messages)
                val request = ClaudeRequest(
                    model = model,
                    messages = claudeMessages,
                    maxTokens = maxTokens ?: 4096,
                    system = systemPrompt,
                    stream = true,
                    temperature = samplingParams?.temperature,
                    topP = samplingParams?.topP,
                    topK = samplingParams?.topK,
                    tools = convertTools(tools)
                )

                val statement = httpClient.preparePost("${baseUrl}/v1/messages") {
                    contentType(ContentType.Application.Json)
                    header("x-api-key", apiKey)
                    header("anthropic-version", ANTHROPIC_VERSION)
                    setBody(request)
                }

                val fullContent = StringBuilder()
                var inputTokens = 0
                var outputTokens = 0
                var stopReason: String? = null
                // 流式 tool_use 累积
                val toolCallAccumulator = mutableMapOf<Int, Triple<String, String, StringBuilder>>()
                var currentBlockIndex = -1
                var currentBlockType = ""
                // thinking 内容累积
                val thinkingContent = StringBuilder()

                statement.execute { response ->
                    if (!response.status.isSuccess()) {
                        val body = try { response.bodyAsText() } catch (_: Exception) { null }
                        throw ChatClientException.fromStatusCode(provider.name, response.status.value, body)
                    }

                    val channel: ByteReadChannel = response.bodyAsChannel()

                    while (!channel.isClosedForRead) {
                        val line = try {
                            channel.readUTF8Line()
                        } catch (_: Exception) {
                            break
                        } ?: break

                        if (line.startsWith("data: ")) {
                            val data = line.removePrefix("data: ").trim()
                            if (data.isEmpty()) continue

                            try {
                                val event = json.decodeFromString<ClaudeStreamEvent>(data)
                                when (event.type) {
                                    "message_start" -> {
                                        event.message?.usage?.let { inputTokens = it.inputTokens }
                                    }
                                    "content_block_start" -> {
                                        val idx = event.index ?: 0
                                        currentBlockIndex = idx
                                        val block = event.contentBlock
                                        currentBlockType = block?.type ?: "text"
                                        if (currentBlockType == "tool_use") {
                                            toolCallAccumulator[idx] = Triple(
                                                block?.id ?: "",
                                                block?.name ?: "",
                                                StringBuilder()
                                            )
                                        }
                                    }
                                    "content_block_delta" -> {
                                        val delta = event.delta
                                        if (delta != null) {
                                            when (delta.type) {
                                                "text_delta" -> {
                                                    val text = delta.text
                                                    if (!text.isNullOrEmpty()) {
                                                        fullContent.append(text)
                                                        onChunk(text)
                                                    }
                                                }
                                                "thinking_delta" -> {
                                                    val text = delta.thinking
                                                    if (!text.isNullOrEmpty()) {
                                                        thinkingContent.append(text)
                                                    }
                                                }
                                                "input_json_delta" -> {
                                                    val partial = delta.partialJson
                                                    if (!partial.isNullOrEmpty()) {
                                                        toolCallAccumulator[currentBlockIndex]?.let { (_, _, args) ->
                                                            args.append(partial)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    "message_delta" -> {
                                        event.usage?.let { outputTokens = it.outputTokens }
                                        event.delta?.stopReason?.let { stopReason = it }
                                    }
                                }
                            } catch (_: Exception) {
                                // 跳过无法解析的行
                            }
                        }
                    }
                }

                val usage = Usage(
                    promptTokens = inputTokens,
                    completionTokens = outputTokens,
                    totalTokens = inputTokens + outputTokens
                )

                val toolCalls = toolCallAccumulator.entries
                    .sortedBy { it.key }
                    .map { (_, triple) ->
                        ToolCall(id = triple.first, name = triple.second, arguments = triple.third.toString().ifEmpty { "{}" })
                    }

                StreamResult(
                    content = fullContent.toString(),
                    usage = usage,
                    finishReason = stopReason,
                    toolCalls = toolCalls,
                    thinking = thinkingContent.toString().ifEmpty { null }
                )
            } catch (e: ChatClientException) {
                throw e
            } catch (e: UnknownHostException) {
                throw ChatClientException(provider.name, "无法连接到服务器，请检查网络", ErrorType.NETWORK, cause = e)
            } catch (e: ConnectException) {
                throw ChatClientException(provider.name, "连接失败，请检查网络", ErrorType.NETWORK, cause = e)
            } catch (e: SocketTimeoutException) {
                throw ChatClientException(provider.name, "请求超时，请稍后重试", ErrorType.NETWORK, cause = e)
            } catch (e: HttpRequestTimeoutException) {
                throw ChatClientException(provider.name, "请求超时，请稍后重试", ErrorType.NETWORK, cause = e)
            } catch (e: Exception) {
                throw ChatClientException(provider.name, "流式请求失败: ${e.message}", ErrorType.UNKNOWN, cause = e)
            }
        }
    }

    override suspend fun listModels(): List<ModelInfo> {
        return executeWithRetry(retryConfig) {
            try {
                val response: HttpResponse = httpClient.get("${baseUrl}/v1/models") {
                    header("x-api-key", apiKey)
                    header("anthropic-version", ANTHROPIC_VERSION)
                    parameter("limit", 1000)
                }

                if (!response.status.isSuccess()) {
                    val body = try { response.bodyAsText() } catch (_: Exception) { null }
                    throw ChatClientException.fromStatusCode(provider.name, response.status.value, body)
                }

                val claudeResponse: com.lhzkml.jasmine.core.prompt.model.ClaudeModelListResponse = response.body()
                claudeResponse.data.map { ModelInfo(
                    id = it.id,
                    displayName = it.displayName.ifEmpty { null }
                ) }
            } catch (e: ChatClientException) {
                throw e
            } catch (e: Exception) {
                throw ChatClientException(provider.name, "获取模型列表失败: ${e.message}", ErrorType.UNKNOWN, cause = e)
            }
        }
    }

    override fun close() {
        httpClient.close()
    }
}
