package com.lhzkml.jasmine.core.prompt.executor

import com.lhzkml.jasmine.core.prompt.llm.ChatClient
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
import com.lhzkml.jasmine.core.prompt.model.SamplingParams
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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * OpenAI 兼容 API 的基础客户端
 * DeepSeek、硅基流动等供应商都使用兼容 OpenAI 的接口格式
 */
abstract class OpenAICompatibleClient(
    protected val apiKey: String,
    protected val baseUrl: String,
    protected val retryConfig: RetryConfig = RetryConfig.DEFAULT,
    httpClient: HttpClient? = null,
    /** Chat completions API 路径，默认 /v1/chat/completions */
    protected val chatPath: String = "/v1/chat/completions"
) : ChatClient {

    internal val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    internal val httpClient: HttpClient = httpClient ?: HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(this@OpenAICompatibleClient.json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = this@OpenAICompatibleClient.retryConfig.requestTimeoutMs
            connectTimeoutMillis = 30000
            socketTimeoutMillis = 60000
        }
    }

    override suspend fun chat(messages: List<ChatMessage>, model: String, maxTokens: Int?, samplingParams: SamplingParams?): String {
        return chatWithUsage(messages, model, maxTokens, samplingParams).content
    }

    override suspend fun chatWithUsage(messages: List<ChatMessage>, model: String, maxTokens: Int?, samplingParams: SamplingParams?): ChatResult {
        return executeWithRetry(retryConfig) {
            try {
                val request = ChatRequest(
                    model = model,
                    messages = messages,
                    temperature = samplingParams?.temperature,
                    topP = samplingParams?.topP,
                    maxTokens = maxTokens
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
                val content = firstChoice?.message?.content
                    ?: throw ChatClientException(provider.name, "响应中没有有效内容", ErrorType.PARSE_ERROR)

                ChatResult(content = content, usage = chatResponse.usage, finishReason = firstChoice.finishReason)
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

    override fun chatStream(messages: List<ChatMessage>, model: String, maxTokens: Int?, samplingParams: SamplingParams?): Flow<String> = flow {
        chatStreamWithUsage(messages, model, maxTokens, samplingParams) { chunk ->
            emit(chunk)
        }
    }

    override suspend fun chatStreamWithUsage(
        messages: List<ChatMessage>,
        model: String,
        maxTokens: Int?,
        samplingParams: SamplingParams?,
        onChunk: suspend (String) -> Unit
    ): StreamResult {
        return executeWithRetry(retryConfig) {
            try {
                val request = ChatRequest(
                    model = model,
                    messages = messages,
                    stream = true,
                    temperature = samplingParams?.temperature,
                    topP = samplingParams?.topP,
                    maxTokens = maxTokens
                )
                val statement = httpClient.preparePost("${baseUrl}${chatPath}") {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer $apiKey")
                    setBody(request)
                }

                val fullContent = StringBuilder()
                var lastUsage: Usage? = null
                var lastFinishReason: String? = null

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
                            if (data == "[DONE]") return@execute
                            if (data.isNotEmpty()) {
                                try {
                                    val chunk = json.decodeFromString<ChatStreamResponse>(data)
                                    // 捕获 usage（通常在最后一个 chunk）
                                    if (chunk.usage != null) {
                                        lastUsage = chunk.usage
                                    }
                                    val firstChoice = chunk.choices.firstOrNull()
                                    if (firstChoice?.finishReason != null) {
                                        lastFinishReason = firstChoice.finishReason
                                    }
                                    val content = firstChoice?.delta?.content
                                    if (!content.isNullOrEmpty()) {
                                        fullContent.append(content)
                                        onChunk(content)
                                    }
                                } catch (_: Exception) {
                                    // 跳过无法解析的行
                                }
                            }
                        }
                    }
                }

                StreamResult(content = fullContent.toString(), usage = lastUsage, finishReason = lastFinishReason)
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
                    header("Authorization", "Bearer $apiKey")
                }

                if (!response.status.isSuccess()) {
                    val body = try { response.bodyAsText() } catch (_: Exception) { null }
                    throw ChatClientException.fromStatusCode(provider.name, response.status.value, body)
                }

                val body = response.bodyAsText()
                val root = json.parseToJsonElement(body).jsonObject
                val dataArray = root["data"]?.jsonArray ?: return@executeWithRetry emptyList()

                dataArray.map { element ->
                    val obj = element.jsonObject
                    parseModelInfoFromJson(obj)
                }
            } catch (e: ChatClientException) {
                throw e
            } catch (e: Exception) {
                throw ChatClientException(provider.name, "获取模型列表失败: ${e.message}", ErrorType.UNKNOWN, cause = e)
            }
        }
    }

    /**
     * 从 JSON 对象解析模型信息，自动提取各供应商可能返回的元数据字段
     * 支持的字段名（兼容不同供应商的命名风格）：
     * - context_length / context_window / max_context_length → contextLength
     * - max_tokens / max_output_tokens / max_completion_tokens → maxOutputTokens
     * - display_name / name → displayName
     * - description → description
     * - temperature / default_temperature → temperature
     * - max_temperature / top_temperature → maxTemperature
     * - top_p → topP
     * - top_k → topK
     */
    protected fun parseModelInfoFromJson(obj: JsonObject): ModelInfo {
        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: ""
        val objectType = obj["object"]?.jsonPrimitive?.contentOrNull ?: "model"
        val ownedBy = obj["owned_by"]?.jsonPrimitive?.contentOrNull ?: ""

        // 上下文长度
        val contextLength = (obj["context_length"] ?: obj["context_window"]
            ?: obj["max_context_length"])?.jsonPrimitive?.intOrNull

        // 最大输出 token
        val maxOutputTokens = (obj["max_tokens"] ?: obj["max_output_tokens"]
            ?: obj["max_completion_tokens"])?.jsonPrimitive?.intOrNull

        // 显示名称
        val displayName = (obj["display_name"] ?: obj["name"])?.jsonPrimitive?.contentOrNull

        // 描述
        val description = obj["description"]?.jsonPrimitive?.contentOrNull

        // temperature
        val temperature = (obj["temperature"] ?: obj["default_temperature"])
            ?.jsonPrimitive?.doubleOrNull
        val maxTemperature = (obj["max_temperature"] ?: obj["top_temperature"])
            ?.jsonPrimitive?.doubleOrNull

        // topP / topK
        val topP = obj["top_p"]?.jsonPrimitive?.doubleOrNull
        val topK = obj["top_k"]?.jsonPrimitive?.intOrNull

        return ModelInfo(
            id = id,
            objectType = objectType,
            ownedBy = ownedBy,
            displayName = displayName,
            contextLength = contextLength,
            maxOutputTokens = maxOutputTokens,
            description = description,
            temperature = temperature,
            maxTemperature = maxTemperature,
            topP = topP,
            topK = topK
        )
    }

    override fun close() {
        httpClient.close()
    }
}
