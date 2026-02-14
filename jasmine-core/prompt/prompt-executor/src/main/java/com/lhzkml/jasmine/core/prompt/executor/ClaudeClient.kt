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
import com.lhzkml.jasmine.core.prompt.model.ClaudeMessage
import com.lhzkml.jasmine.core.prompt.model.ClaudeRequest
import com.lhzkml.jasmine.core.prompt.model.ClaudeResponse
import com.lhzkml.jasmine.core.prompt.model.ClaudeStreamEvent
import com.lhzkml.jasmine.core.prompt.model.ModelInfo
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
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Anthropic Claude 客户端
 * 使用 Claude 原生 Messages API
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

    /**
     * 将通用 ChatMessage 转换为 Claude 格式
     * Claude 的 system 消息不在 messages 中，而是作为顶层字段
     */
    private fun convertMessages(messages: List<ChatMessage>): Pair<String?, List<ClaudeMessage>> {
        var systemPrompt: String? = null
        val claudeMessages = mutableListOf<ClaudeMessage>()

        for (msg in messages) {
            when (msg.role) {
                "system" -> systemPrompt = msg.content
                "user", "assistant" -> claudeMessages.add(ClaudeMessage(role = msg.role, content = msg.content))
            }
        }
        return systemPrompt to claudeMessages
    }

    override suspend fun chat(messages: List<ChatMessage>, model: String, maxTokens: Int?): String {
        return chatWithUsage(messages, model, maxTokens).content
    }

    override suspend fun chatWithUsage(messages: List<ChatMessage>, model: String, maxTokens: Int?): ChatResult {
        return executeWithRetry(retryConfig) {
            try {
                val (systemPrompt, claudeMessages) = convertMessages(messages)
                val request = ClaudeRequest(
                    model = model,
                    messages = claudeMessages,
                    maxTokens = maxTokens ?: 4096,
                    system = systemPrompt
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
                val content = claudeResponse.content.firstOrNull { it.type == "text" }?.text
                    ?: throw ChatClientException(provider.name, "响应中没有有效内容", ErrorType.PARSE_ERROR)

                val usage = claudeResponse.usage?.let {
                    Usage(
                        promptTokens = it.inputTokens,
                        completionTokens = it.outputTokens,
                        totalTokens = it.inputTokens + it.outputTokens
                    )
                }

                ChatResult(content = content, usage = usage)
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

    override fun chatStream(messages: List<ChatMessage>, model: String, maxTokens: Int?): Flow<String> = flow {
        chatStreamWithUsage(messages, model, maxTokens) { chunk ->
            emit(chunk)
        }
    }

    override suspend fun chatStreamWithUsage(
        messages: List<ChatMessage>,
        model: String,
        maxTokens: Int?,
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
                    stream = true
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
                                        event.message?.usage?.let {
                                            inputTokens = it.inputTokens
                                        }
                                    }
                                    "content_block_delta" -> {
                                        val text = event.delta?.text
                                        if (!text.isNullOrEmpty()) {
                                            fullContent.append(text)
                                            onChunk(text)
                                        }
                                    }
                                    "message_delta" -> {
                                        event.usage?.let {
                                            outputTokens = it.outputTokens
                                        }
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

                StreamResult(content = fullContent.toString(), usage = usage)
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
