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
import com.lhzkml.jasmine.core.prompt.model.GeminiGenerationConfig
import com.lhzkml.jasmine.core.prompt.model.GeminiPart
import com.lhzkml.jasmine.core.prompt.model.GeminiRequest
import com.lhzkml.jasmine.core.prompt.model.GeminiResponse
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
 * Google Gemini 客户端
 * 使用 Gemini 原生 generateContent API
 */
open class GeminiClient(
    protected val apiKey: String,
    protected val baseUrl: String = DEFAULT_BASE_URL,
    protected val retryConfig: RetryConfig = RetryConfig.DEFAULT,
    httpClient: HttpClient? = null,
    /**
     * generateContent 路径模板，{model} 会被替换为实际模型名
     * AI Studio: /v1beta/models/{model}:generateContent
     * Vertex AI Express: /v1/publishers/google/models/{model}:generateContent
     */
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

    /**
     * 将通用 ChatMessage 转换为 Gemini 格式
     * Gemini 使用 "user"/"model" 角色，system 消息作为 systemInstruction
     */
    private fun convertMessages(messages: List<ChatMessage>): Pair<GeminiContent?, List<GeminiContent>> {
        var systemInstruction: GeminiContent? = null
        val contents = mutableListOf<GeminiContent>()

        for (msg in messages) {
            when (msg.role) {
                "system" -> {
                    systemInstruction = GeminiContent(
                        parts = listOf(GeminiPart(text = msg.content))
                    )
                }
                "user" -> contents.add(
                    GeminiContent(role = "user", parts = listOf(GeminiPart(text = msg.content)))
                )
                "assistant" -> contents.add(
                    GeminiContent(role = "model", parts = listOf(GeminiPart(text = msg.content)))
                )
            }
        }
        return systemInstruction to contents
    }

    override suspend fun chat(messages: List<ChatMessage>, model: String, maxTokens: Int?): String {
        return chatWithUsage(messages, model, maxTokens).content
    }

    override suspend fun chatWithUsage(messages: List<ChatMessage>, model: String, maxTokens: Int?): ChatResult {
        return executeWithRetry(retryConfig) {
            try {
                val (systemInstruction, contents) = convertMessages(messages)
                val request = GeminiRequest(
                    contents = contents,
                    systemInstruction = systemInstruction,
                    generationConfig = GeminiGenerationConfig(
                        maxOutputTokens = maxTokens
                    )
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
                val content = geminiResponse.candidates?.firstOrNull()
                    ?.content?.parts?.firstOrNull()?.text
                    ?: throw ChatClientException(provider.name, "响应中没有有效内容", ErrorType.PARSE_ERROR)

                val usage = geminiResponse.usageMetadata?.let {
                    Usage(
                        promptTokens = it.promptTokenCount,
                        completionTokens = it.candidatesTokenCount,
                        totalTokens = it.totalTokenCount
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
                val (systemInstruction, contents) = convertMessages(messages)
                val request = GeminiRequest(
                    contents = contents,
                    systemInstruction = systemInstruction,
                    generationConfig = GeminiGenerationConfig(
                        maxOutputTokens = maxTokens
                    )
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
                                // 提取 usage
                                chunk.usageMetadata?.let {
                                    lastUsage = Usage(
                                        promptTokens = it.promptTokenCount,
                                        completionTokens = it.candidatesTokenCount,
                                        totalTokens = it.totalTokenCount
                                    )
                                }
                                // 提取文本
                                val text = chunk.candidates?.firstOrNull()
                                    ?.content?.parts?.firstOrNull()?.text
                                if (!text.isNullOrEmpty()) {
                                    fullContent.append(text)
                                    onChunk(text)
                                }
                            } catch (_: Exception) {
                                // 跳过无法解析的行
                            }
                        }
                    }
                }

                StreamResult(content = fullContent.toString(), usage = lastUsage)
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
                        // name 格式为 "models/gemini-2.5-flash"，提取模型 ID
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
