package com.lhzkml.jasmine.core.prompt.executor

import com.lhzkml.jasmine.core.prompt.llm.ChatClient
import com.lhzkml.jasmine.core.prompt.llm.ThinkingChatClient
import com.lhzkml.jasmine.core.prompt.llm.ChatClientException
import com.lhzkml.jasmine.core.prompt.llm.ErrorType
import com.lhzkml.jasmine.core.prompt.llm.RetryConfig
import com.lhzkml.jasmine.core.prompt.llm.StreamResult
import com.lhzkml.jasmine.core.prompt.llm.executeWithRetry
import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.ChatRequest
import com.lhzkml.jasmine.core.prompt.model.ChatResponse
import com.lhzkml.jasmine.core.prompt.model.ChatResult
import com.lhzkml.jasmine.core.prompt.model.ChatStreamResponse
import com.lhzkml.jasmine.core.prompt.model.ModelInfo
import com.lhzkml.jasmine.core.prompt.model.OpenAIFunctionCallDef
import com.lhzkml.jasmine.core.prompt.model.OpenAIFunctionDef
import com.lhzkml.jasmine.core.prompt.model.OpenAIRequestMessage
import com.lhzkml.jasmine.core.prompt.model.OpenAIToolCallDef
import com.lhzkml.jasmine.core.prompt.model.OpenAIToolDef
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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.coroutines.coroutineContext

/**
 * OpenAI 兼容 API 的基础客户端
 * DeepSeek、硅基流动等供应商都使用兼容 OpenAI 的接口格式
 */
abstract class OpenAICompatibleClient(
    protected val apiKey: String,
    protected val baseUrl: String,
    protected val retryConfig: RetryConfig = RetryConfig.DEFAULT,
    httpClient: HttpClient? = null,
    protected val chatPath: String = "/v1/chat/completions"
) : ThinkingChatClient {

    internal val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    internal val httpClient: HttpClient = httpClient ?: HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(this@OpenAICompatibleClient.json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 300000  // 流式输出可能持续较长时间
            connectTimeoutMillis = 30000
            socketTimeoutMillis = 120000   // 单次读取超时
        }
    }

    // ========== 消息/工具转换 ==========

    private fun convertMessages(messages: List<ChatMessage>): List<OpenAIRequestMessage> {
        return messages.map { msg ->
            val tc = msg.toolCalls
            when {
                msg.role == "assistant" && !tc.isNullOrEmpty() -> OpenAIRequestMessage(
                    role = "assistant",
                    content = msg.content.ifEmpty { null },
                    toolCalls = tc.map {
                        OpenAIToolCallDef(
                            id = it.id,
                            function = OpenAIFunctionCallDef(name = it.name, arguments = it.arguments)
                        )
                    }
                )
                msg.role == "tool" -> OpenAIRequestMessage(
                    role = "tool",
                    content = msg.content,
                    toolCallId = msg.toolCallId
                )
                else -> OpenAIRequestMessage(role = msg.role, content = msg.content)
            }
        }
    }

    private fun convertTools(tools: List<ToolDescriptor>): List<OpenAIToolDef>? {
        if (tools.isEmpty()) return null
        return tools.map {
            OpenAIToolDef(
                function = OpenAIFunctionDef(
                    name = it.name,
                    description = it.description,
                    parameters = it.toJsonSchema()
                )
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
                val request = ChatRequest(
                    model = model,
                    messages = convertMessages(messages),
                    temperature = samplingParams?.temperature,
                    topP = samplingParams?.topP,
                    maxTokens = maxTokens,
                    tools = convertTools(tools)
                )
                val response: HttpResponse = httpClient.post("${baseUrl}${chatPath}") {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer $apiKey")
                    setBody(request)
                }

                if (!response.status.isSuccess()) {
                    val body = try { response.bodyAsText() } catch (_: Exception) { null }
                    throw ChatClientException.fromStatusCode(provider.name, response.status.value, body)
                }

                val chatResponse: ChatResponse = response.body()
                val firstChoice = chatResponse.choices.firstOrNull()
                    ?: throw ChatClientException(provider.name, "响应中没有有效内容", ErrorType.PARSE_ERROR)

                val toolCalls = firstChoice.message.toolCalls?.map {
                    ToolCall(id = it.id, name = it.function.name, arguments = it.function.arguments)
                } ?: emptyList()

                ChatResult(
                    content = firstChoice.message.content ?: "",
                    usage = chatResponse.usage,
                    finishReason = firstChoice.finishReason,
                    toolCalls = toolCalls,
                    thinking = firstChoice.message.reasoningContent
                )
            } catch (e: ChatClientException) { throw e }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: UnknownHostException) { throw ChatClientException(provider.name, "无法连接到服务器，请检查网络", ErrorType.NETWORK, cause = e) }
            catch (e: ConnectException) { throw ChatClientException(provider.name, "连接失败，请检查网络", ErrorType.NETWORK, cause = e) }
            catch (e: SocketTimeoutException) { throw ChatClientException(provider.name, "请求超时，请稍后重试", ErrorType.NETWORK, cause = e) }
            catch (e: HttpRequestTimeoutException) { throw ChatClientException(provider.name, "请求超时，请稍后重试", ErrorType.NETWORK, cause = e) }
            catch (e: Exception) { throw ChatClientException(provider.name, "请求失败: ${e.message}", ErrorType.UNKNOWN, cause = e) }
        }
    }

    override fun chatStream(
        messages: List<ChatMessage>, model: String, maxTokens: Int?,
        samplingParams: SamplingParams?, tools: List<ToolDescriptor>
    ): Flow<String> = flow {
        chatStreamWithUsage(messages, model, maxTokens, samplingParams, tools) { emit(it) }
    }

    override suspend fun chatStreamWithUsage(
        messages: List<ChatMessage>, model: String, maxTokens: Int?,
        samplingParams: SamplingParams?, tools: List<ToolDescriptor>,
        onChunk: suspend (String) -> Unit
    ): StreamResult = chatStreamWithThinking(messages, model, maxTokens, samplingParams, tools, onChunk, {})

    override suspend fun chatStreamWithThinking(
        messages: List<ChatMessage>, model: String, maxTokens: Int?,
        samplingParams: SamplingParams?, tools: List<ToolDescriptor>,
        onChunk: suspend (String) -> Unit,
        onThinking: suspend (String) -> Unit
    ): StreamResult {
        return executeWithRetry(retryConfig) {
            try {
                val request = ChatRequest(
                    model = model,
                    messages = convertMessages(messages),
                    stream = true,
                    temperature = samplingParams?.temperature,
                    topP = samplingParams?.topP,
                    maxTokens = maxTokens,
                    tools = convertTools(tools)
                )
                val statement = httpClient.preparePost("${baseUrl}${chatPath}") {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer $apiKey")
                    setBody(request)
                }

                val fullContent = StringBuilder()
                val thinkingContent = StringBuilder()
                var lastUsage: Usage? = null
                var lastFinishReason: String? = null
                val toolCallAccumulator = mutableMapOf<Int, Triple<String, String, StringBuilder>>()

                statement.execute { response ->
                    if (!response.status.isSuccess()) {
                        val body = try { response.bodyAsText() } catch (_: Exception) { null }
                        throw ChatClientException.fromStatusCode(provider.name, response.status.value, body)
                    }

                    val channel: ByteReadChannel = response.bodyAsChannel()
                    val lineBuffer = StringBuilder()
                    while (!channel.isClosedForRead) {
                        coroutineContext.ensureActive()
                        val line = try { channel.readUTF8Line() } catch (_: Exception) { break } ?: break
                        if (line.isEmpty()) continue
                        if (line.startsWith("data: ")) {
                            val data = line.removePrefix("data: ").trim()
                            if (data == "[DONE]") return@execute
                            if (data.isNotEmpty()) {
                                try {
                                    val chunk = json.decodeFromString<ChatStreamResponse>(data)
                                    if (chunk.usage != null) lastUsage = chunk.usage
                                    val firstChoice = chunk.choices.firstOrNull()
                                    if (firstChoice?.finishReason != null) lastFinishReason = firstChoice.finishReason
                                    val content = firstChoice?.delta?.content
                                    if (!content.isNullOrEmpty()) {
                                        fullContent.append(content)
                                        onChunk(content)
                                    }
                                    // 推理过程（DeepSeek R1 等）
                                    val reasoning = firstChoice?.delta?.reasoningContent
                                    if (!reasoning.isNullOrEmpty()) {
                                        thinkingContent.append(reasoning)
                                        onThinking(reasoning)
                                    }
                                    // 累积流式 tool_calls
                                    firstChoice?.delta?.toolCalls?.forEach { stc ->
                                        val tcId = stc.id
                                        if (tcId != null) {
                                            toolCallAccumulator[stc.index] = Triple(
                                                tcId,
                                                stc.function?.name ?: "",
                                                StringBuilder(stc.function?.arguments ?: "")
                                            )
                                        } else {
                                            toolCallAccumulator[stc.index]?.let { (_, _, args) ->
                                                args.append(stc.function?.arguments ?: "")
                                            }
                                        }
                                    }
                                } catch (_: Exception) {
                                    // 单个 chunk 解析失败，跳过继续读取下一个
                                }
                            }
                        }
                    }
                }

                val toolCalls = toolCallAccumulator.entries
                    .sortedBy { it.key }
                    .map { (_, t) -> ToolCall(id = t.first, name = t.second, arguments = t.third.toString()) }

                StreamResult(
                    content = fullContent.toString(),
                    usage = lastUsage,
                    finishReason = lastFinishReason,
                    toolCalls = toolCalls,
                    thinking = thinkingContent.toString().ifEmpty { null }
                )
            } catch (e: ChatClientException) { throw e }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: UnknownHostException) { throw ChatClientException(provider.name, "无法连接到服务器，请检查网络", ErrorType.NETWORK, cause = e) }
            catch (e: ConnectException) { throw ChatClientException(provider.name, "连接失败，请检查网络", ErrorType.NETWORK, cause = e) }
            catch (e: SocketTimeoutException) { throw ChatClientException(provider.name, "请求超时，请稍后重试", ErrorType.NETWORK, cause = e) }
            catch (e: HttpRequestTimeoutException) { throw ChatClientException(provider.name, "请求超时，请稍后重试", ErrorType.NETWORK, cause = e) }
            catch (e: Exception) { throw ChatClientException(provider.name, "流式请求失败: ${e.message}", ErrorType.UNKNOWN, cause = e) }
        }
    }

    // ========== 模型列表 ==========

    override suspend fun listModels(): List<ModelInfo> {
        return executeWithRetry(retryConfig) {
            try {
                val response: HttpResponse = httpClient.get("${baseUrl}/v1/models") {
                    header("Authorization", "Bearer $apiKey")
                }
                if (!response.status.isSuccess()) {
                    val body = try { response.bodyAsText() } catch (_: Exception) { null }
                    throw ChatClientException.fromStatusCode(provider.name, response.status.value, body)
                }
                val body = response.bodyAsText()
                val root = json.parseToJsonElement(body).jsonObject
                val dataArray = root["data"]?.jsonArray ?: return@executeWithRetry emptyList()
                dataArray.map { parseModelInfoFromJson(it.jsonObject) }
            } catch (e: ChatClientException) { throw e }
            catch (e: Exception) { throw ChatClientException(provider.name, "获取模型列表失败: ${e.message}", ErrorType.UNKNOWN, cause = e) }
        }
    }

    protected fun parseModelInfoFromJson(obj: JsonObject): ModelInfo {
        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: ""
        val objectType = obj["object"]?.jsonPrimitive?.contentOrNull ?: "model"
        val ownedBy = obj["owned_by"]?.jsonPrimitive?.contentOrNull ?: ""
        val contextLength = (obj["context_length"] ?: obj["context_window"]
            ?: obj["max_context_length"])?.jsonPrimitive?.intOrNull
        val maxOutputTokens = (obj["max_tokens"] ?: obj["max_output_tokens"]
            ?: obj["max_completion_tokens"])?.jsonPrimitive?.intOrNull
        val displayName = (obj["display_name"] ?: obj["name"])?.jsonPrimitive?.contentOrNull
        val description = obj["description"]?.jsonPrimitive?.contentOrNull
        val temperature = (obj["temperature"] ?: obj["default_temperature"])?.jsonPrimitive?.doubleOrNull
        val maxTemperature = (obj["max_temperature"] ?: obj["top_temperature"])?.jsonPrimitive?.doubleOrNull
        val topP = obj["top_p"]?.jsonPrimitive?.doubleOrNull
        val topK = obj["top_k"]?.jsonPrimitive?.intOrNull
        return ModelInfo(
            id = id, objectType = objectType, ownedBy = ownedBy,
            displayName = displayName, contextLength = contextLength,
            maxOutputTokens = maxOutputTokens, description = description,
            temperature = temperature, maxTemperature = maxTemperature,
            topP = topP, topK = topK
        )
    }

    override fun close() {
        httpClient.close()
    }
}
