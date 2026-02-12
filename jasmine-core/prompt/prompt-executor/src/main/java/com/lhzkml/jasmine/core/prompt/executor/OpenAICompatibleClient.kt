package com.lhzkml.jasmine.core.prompt.executor

import com.lhzkml.jasmine.core.prompt.llm.ChatClient
import com.lhzkml.jasmine.core.prompt.llm.ChatClientException
import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.ChatRequest
import com.lhzkml.jasmine.core.prompt.model.ChatResponse
import com.lhzkml.jasmine.core.prompt.model.ModelInfo
import com.lhzkml.jasmine.core.prompt.model.ModelListResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * OpenAI 兼容 API 的基础客户端
 * DeepSeek、硅基流动等供应商都使用兼容 OpenAI 的接口格式
 */
abstract class OpenAICompatibleClient(
    protected val apiKey: String,
    protected val baseUrl: String
) : ChatClient {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(this@OpenAICompatibleClient.json)
        }
    }

    override suspend fun chat(messages: List<ChatMessage>, model: String): String {
        try {
            val request = ChatRequest(model = model, messages = messages)
            val response: ChatResponse = httpClient.post("${baseUrl}/v1/chat/completions") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(request)
            }.body()

            return response.choices.firstOrNull()?.message?.content
                ?: throw ChatClientException(provider.name, "响应中没有有效内容")
        } catch (e: ChatClientException) {
            throw e
        } catch (e: Exception) {
            throw ChatClientException(provider.name, "请求失败: ${e.message}", e)
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
