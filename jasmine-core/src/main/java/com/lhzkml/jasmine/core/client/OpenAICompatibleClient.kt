package com.lhzkml.jasmine.core.client

import com.lhzkml.jasmine.core.ChatClient
import com.lhzkml.jasmine.core.ChatClientException
import com.lhzkml.jasmine.core.LLMProvider
import com.lhzkml.jasmine.core.model.ChatMessage
import com.lhzkml.jasmine.core.model.ChatRequest
import com.lhzkml.jasmine.core.model.ChatResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * OpenAI 兼容协议的基类客户端。
 * 参考 Koog 的 AbstractOpenAILLMClient 设计，
 * 所有兼容 OpenAI /v1/chat/completions 接口的供应商都可以继承此类。
 *
 * 子类只需提供 provider、apiKey、baseUrl 即可工作。
 * 如果供应商有特殊的请求/响应格式，可以重写 buildRequest() 和 parseResponse()。
 *
 * @param apiKey API 密钥
 * @param baseUrl API 基础地址
 * @param chatCompletionsPath 聊天补全接口路径，默认 /v1/chat/completions
 */
abstract class OpenAICompatibleClient(
    private val apiKey: String,
    private val baseUrl: String,
    private val chatCompletionsPath: String = "/v1/chat/completions"
) : ChatClient {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    override suspend fun chat(
        messages: List<ChatMessage>,
        model: String,
        temperature: Double
    ): String {
        val request = buildRequest(messages, model, temperature)
        val url = "${baseUrl.trimEnd('/')}${chatCompletionsPath}"

        val response = try {
            httpClient.post(url) {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(request)
            }
        } catch (e: Exception) {
            throw ChatClientException(provider.displayName, "网络请求失败: ${e.message}", e)
        }

        val responseText = response.bodyAsText()

        if (response.status.value !in 200..299) {
            throw ChatClientException(
                provider.displayName,
                "API 请求失败 (${response.status.value}): $responseText"
            )
        }

        return parseResponse(responseText)
    }

    /**
     * 构建请求体。子类可重写以自定义请求格式。
     */
    protected open fun buildRequest(
        messages: List<ChatMessage>,
        model: String,
        temperature: Double
    ): ChatRequest {
        return ChatRequest(
            model = model,
            messages = messages,
            temperature = temperature
        )
    }

    /**
     * 解析响应体，提取助手回复文本。子类可重写以处理特殊响应格式。
     */
    protected open fun parseResponse(responseText: String): String {
        val chatResponse = try {
            json.decodeFromString<ChatResponse>(responseText)
        } catch (e: Exception) {
            throw ChatClientException(provider.displayName, "响应解析失败: ${e.message}", e)
        }

        return chatResponse.choices.firstOrNull()?.message?.content
            ?: throw ChatClientException(provider.displayName, "API 返回了空回复")
    }

    override fun close() {
        httpClient.close()
    }
}
