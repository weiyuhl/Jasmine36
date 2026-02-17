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
import com.lhzkml.jasmine.core.prompt.model.GeminiContent
import com.lhzkml.jasmine.core.prompt.model.GeminiFunctionCall
import com.lhzkml.jasmine.core.prompt.model.GeminiFunctionDeclaration
import com.lhzkml.jasmine.core.prompt.model.GeminiFunctionResponse
import com.lhzkml.jasmine.core.prompt.model.GeminiGenerationConfig
import com.lhzkml.jasmine.core.prompt.model.GeminiPart
import com.lhzkml.jasmine.core.prompt.model.GeminiRequest
import com.lhzkml.jasmine.core.prompt.model.GeminiResponse
import com.lhzkml.jasmine.core.prompt.model.GeminiToolDef
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
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Google Gemini 客户端
 * 使用 Gemini 原生 generateContent API，支持 Tool Calling
 */
open class GeminiClient(
    protected val apiKey: String,
    protected val baseUrl: String = DEFAULT_BASE_URL,
    protected val retryConfig: RetryConfig = RetryConfig.DEFAULT,
    httpClient: HttpClient? = null,
    protected val generatePath: String = DEFAULT_GENERATE_PATH,
    protected val streamPath: String = DEFAULT_STREAM_PATH
) : ChatClient {

    companion object {
        const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com"
        const val DEFAULT_GENERATE_PATH = "/v1beta/models/{model}:generateContent"
        const val DEFAULT_STREAM_PATH = "/v1beta/models/{model}:streamGenerateContent"
    }

    override val provider: LLMProvider = LLMProvider.Gemini

    internal val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    internal val httpClient: HttpClient = httpClient ?: HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(this@GeminiClient.json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = retryConfig.requestTimeoutMs
            connectTimeoutMillis = 30000
            socketTimeoutMillis = 60000
        }
    }

    // ========== 消息转换 ==========

    /**
     * 将通用 ChatMessage 转换为 Gemini 格式
     * 支持 functionCall（assistant 带 tool_calls）和 functionResponse（tool 结果）
     */
    private fun convertMessages(messages: List<ChatMessage>): Pair<GeminiContent?, List<GeminiContent>> {
        var systemInstruction: GeminiContent? = null
        val contents = mutableListOf<GeminiContent>()

        for (msg in messages) {
            val msgToolCalls = msg.toolCalls
            when {
                msg.role == "system" -> {
                    systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = msg.content)))
                }
                // assistant 消息带 tool_calls → model 角色 + functionCall parts
                msg.role == "assistant" && !msgToolCalls.isNullOrEmpty() -> {
                    val parts = mutableListOf<GeminiPart>()
                    if (msg.content.isNotEmpty()) {
                        parts.add(GeminiPart(text = msg.content))
                    }
                    msgToolCalls.forEach { tc ->
                        val argsJson = try {
                            json.parseToJsonElement(tc.arguments) as? JsonObject ?: buildJsonObject {}
                        } catch (_: Exception) {
                            buildJsonObject {}
                        }
                        parts.add(GeminiPart(functionCall = GeminiFunctionCall(name = tc.name, args = argsJson)))
                    }
                    contents.add(GeminiContent(role = "model", parts = parts))
                }
                // tool 结果消息 → user 角色 + functionResponse part
                msg.role == "tool" -> {
                    val responseJson = try {
                        json.parseToJsonElement(msg.content) as? JsonObject
                            ?: buildJsonObject { put("result", msg.content) }
                    } catch (_: Exception) {
                        buildJsonObject { put("result", msg.content) }
                    }
                    contents.add(GeminiContent(
                        role = "user",
                        parts = listOf(GeminiPart(functionResponse = GeminiFunctionResponse(
                            name = msg.toolName ?: "",
                            response = responseJson
                        )))
                    ))
                }
                msg.role == "user" -> contents.add(
                    GeminiContent(role = "user", parts = listOf(GeminiPart(text = msg.content)))
                )
                msg.role == "assistant" -> contents.add(
                    GeminiContent(role = "model", parts = listOf(GeminiPart(text = msg.content)))
                )
            }
        }
        return systemInstruction to contents
    }

    /**
     * 将 ToolDescriptor 转换为 Gemini tools 格式
     */
    private fun convertTools(tools: List<ToolDescriptor>): List<GeminiToolDef>? {
        if (tools.isEmpty()) return null
        return listOf(GeminiToolDef(
            functionDeclarations = tools.map { tool ->
                GeminiFunctionDeclaration(
                    name = tool.name,
                    description = tool.description,
                    parameters = tool.toJsonSchema()
                )
            }
        ))
    }

    /**
     * 从 GeminiResponse 中提取 ToolCall 列表
     */
    private fun extractToolCalls(response: GeminiResponse): List<ToolCall> {
        val parts = response.candidates?.firstOrNull()?.content?.parts ?: return emptyList()
        return parts.mapNotNull { part ->
            part.functionCall?.let { fc ->
                ToolCall(
                    id = "gemini_${fc.name}_${System.nanoTime()}",
                    name = fc.name,
                    arguments = fc.args?.toString() ?: "{}"
                )
            }
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
                val (systemInstruction, contents) = convertMessages(messages)
                val request = GeminiRequest(
                    contents = contents,
                    systemInstruction = systemInstruction,
                    generationConfig = GeminiGenerationConfig(
                        temperature = samplingParams?.temperature,
                        topP = samplingParams?.topP,
                        topK = samplingParams?.topK,
                        maxOutputTokens = maxTokens
                    ),
                    tools = convertTools(tools)
                )

                val response: HttpResponse = httpClient.post(
                    "${baseUrl}${generatePath.replace("{model}", model)}"
                ) {
                    contentType(ContentType.Application.Json)
                    parameter("key", apiKey)
                    setBody(request)
                }

                if (!response.status.isSuccess()) {
                    val body = try { response.bodyAsText() } catch (_: Exception) { null }
                    throw ChatClientException.fromStatusCode(provider.name, response.status.value, body)
                }

                val geminiResponse: GeminiResponse = response.body()
                val firstCandidate = geminiResponse.candidates?.firstOrNull()

                // 提取 tool calls
                val toolCalls = extractToolCalls(geminiResponse)

                // 提取文本内容
                val content = firstCandidate?.content?.parts
                    ?.mapNotNull { it.text }
                    ?.joinToString("") ?: ""

                val usage = geminiResponse.usageMetadata?.let {
                    Usage(
                        promptTokens = it.promptTokenCount,
                        completionTokens = it.candidatesTokenCount,
                        totalTokens = it.totalTokenCount
                    )
                }

                ChatResult(
                    content = content,
                    usage = usage,
                    finishReason = firstCandidate?.finishReason,
                    toolCalls = toolCalls
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
                val (systemInstruction, contents) = convertMessages(messages)
                val request = GeminiRequest(
                    contents = contents,
                    systemInstruction = systemInstruction,
                    generationConfig = GeminiGenerationConfig(
                        temperature = samplingParams?.temperature,
                        topP = samplingParams?.topP,
                        topK = samplingParams?.topK,
                        maxOutputTokens = maxTokens
                    ),
                    tools = convertTools(tools)
                )

                val statement = httpClient.preparePost(
                    "${baseUrl}${streamPath.replace("{model}", model)}"
                ) {
                    contentType(ContentType.Application.Json)
                    parameter("key", apiKey)
                    parameter("alt", "sse")
                    setBody(request)
                }

                val fullContent = StringBuilder()
                var lastUsage: Usage? = null
                var lastFinishReason: String? = null
                val toolCalls = mutableListOf<ToolCall>()

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
                                val chunk = json.decodeFromString<GeminiResponse>(data)
                                chunk.usageMetadata?.let {
                                    lastUsage = Usage(
                                        promptTokens = it.promptTokenCount,
                                        completionTokens = it.candidatesTokenCount,
                                        totalTokens = it.totalTokenCount
                                    )
                                }
                                val firstCandidate = chunk.candidates?.firstOrNull()
                                if (firstCandidate?.finishReason != null) {
                                    lastFinishReason = firstCandidate.finishReason
                                }
                                // 提取文本
                                firstCandidate?.content?.parts?.forEach { part ->
                                    val text = part.text
                                    if (!text.isNullOrEmpty()) {
                                        fullContent.append(text)
                                        onChunk(text)
                                    }
                                    // 提取 functionCall
                                    part.functionCall?.let { fc ->
                                        toolCalls.add(ToolCall(
                                            id = "gemini_${fc.name}_${System.nanoTime()}",
                                            name = fc.name,
                                            arguments = fc.args?.toString() ?: "{}"
                                        ))
                                    }
                                }
                            } catch (_: Exception) {
                                // 跳过无法解析的行
                            }
                        }
                    }
                }

                StreamResult(
                    content = fullContent.toString(),
                    usage = lastUsage,
                    finishReason = lastFinishReason,
                    toolCalls = toolCalls
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
                val response: HttpResponse = httpClient.get("${baseUrl}/v1beta/models") {
                    parameter("key", apiKey)
                    parameter("pageSize", 1000)
                }

                if (!response.status.isSuccess()) {
                    val body = try { response.bodyAsText() } catch (_: Exception) { null }
                    throw ChatClientException.fromStatusCode(provider.name, response.status.value, body)
                }

                val geminiResponse: com.lhzkml.jasmine.core.prompt.model.GeminiModelListResponse = response.body()
                geminiResponse.models
                    .filter { it.supportedGenerationMethods.contains("generateContent") }
                    .map { model ->
                        val id = model.name.removePrefix("models/")
                        ModelInfo(
                            id = id,
                            displayName = model.displayName.ifEmpty { null },
                            contextLength = model.inputTokenLimit,
                            maxOutputTokens = model.outputTokenLimit,
                            supportsThinking = model.thinking,
                            temperature = model.temperature,
                            maxTemperature = model.maxTemperature,
                            topP = model.topP,
                            topK = model.topK,
                            description = model.description.ifEmpty { null }
                        )
                    }
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
