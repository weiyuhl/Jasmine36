package com.lhzkml.jasmine.core.prompt.executor

import com.lhzkml.jasmine.core.prompt.llm.ChatClient
import com.lhzkml.jasmine.core.prompt.llm.ChatClientException
import com.lhzkml.jasmine.core.prompt.llm.StreamResult
import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.ChatRequest
import com.lhzkml.jasmine.core.prompt.model.ChatResponse
import com.lhzkml.jasmine.core.prompt.model.ChatResult
import com.lhzkml.jasmine.core.prompt.model.ChatStreamResponse
import com.lhzkml.jasmine.core.prompt.model.ModelInfo
import com.lhzkml.jasmine.core.prompt.model.ModelListResponse
import com.lhzkml.jasmine.core.prompt.model.Usage
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

/**
 * OpenAI 兼容 API 的基础客户端
 * DeepSeek、硅基流动等供应商都使用兼容 OpenAI 的接口格式
 */
abstract class OpenAICompatibleClient(
    protected val apiKey: String,
    protected val baseUrl: String,
    httpClient: HttpClient? = null
) : ChatClient {

    internal val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    internal val httpClient: HttpClient = httpClient ?: HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(this@OpenAICompatibleClient.json)
        }
    }

    override suspend fun chat(messages: List<ChatMessage>, model: String, maxTokens: Int?): String {
        return chatWithUsage(messages, model, maxTokens).content
    }

    override suspend fun chatWithUsage(messages: List<ChatMessage>, model: String, maxTokens: Int?): ChatResult {
        try {
            val request = ChatRequest(model = model, messages = messages, maxTokens = maxTokens)
            val response: ChatResponse = httpClient.post("${baseUrl}/v1/chat/completions") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(request)
            }.body()

            val content = response.choices.firstOrNull()?.message?.content
                ?: throw ChatClientException(provider.name, "响应中没有有效内容")

            return ChatResult(content = content, usage = response.usage)
        } catch (e: ChatClientException) {
            throw e
        } catch (e: Exception) {
            throw ChatClientException(provider.name, "请求失败: ${e.message}", e)
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
        try {
            val request = ChatRequest(model = model, messages = messages, stream = true, maxTokens = maxTokens)
            val statement = httpClient.preparePost("${baseUrl}/v1/chat/completions") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(request)
            }

            val fullContent = StringBuilder()
            var lastUsage: Usage? = null

            statement.execute { response ->
                if (!response.status.isSuccess()) {
                    throw ChatClientException(
                        provider.name,
                        "请求失败，状态码: ${response.status.value}"
                    )
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
                                val content = chunk.choices.firstOrNull()?.delta?.content
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

            return StreamResult(content = fullContent.toString(), usage = lastUsage)
        } catch (e: ChatClientException) {
            throw e
        } catch (e: Exception) {
            throw ChatClientException(provider.name, "流式请求失败: ${e.message}", e)
        }
    }

    override suspend fun listModels(): List<ModelInfo> {
        try {
            val response: ModelListResponse = httpClient.get("${baseUrl}/v1/models") {
                header("Authorization", "Bearer $apiKey")
            }.body()
            return response.data
        } catch (e: Exception) {
            throw ChatClientException(provider.name, "获取模型列表失败: ${e.message}", e)
        }
    }

    override fun close() {
        httpClient.close()
    }
}
