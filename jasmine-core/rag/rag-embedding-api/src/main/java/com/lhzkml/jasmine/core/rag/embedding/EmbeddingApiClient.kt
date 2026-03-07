package com.lhzkml.jasmine.core.rag.embedding

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.headers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Embedding API 客户端，用于获取模型列表等
 * 支持 OpenAI 兼容的 /v1/models 端点
 */
object EmbeddingApiClient {

    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient by lazy {
        HttpClient(OkHttp)
    }

    /**
     * 从 OpenAI 兼容 API 获取模型列表
     * @return 模型 id 列表，失败返回空列表
     */
    suspend fun listModels(baseUrl: String, apiKey: String): List<String> = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank() || apiKey.isBlank()) return@withContext emptyList()
        try {
            val url = baseUrl.trimEnd('/') + "/v1/models"
            val response = httpClient.get(url) {
                headers { append("Authorization", "Bearer $apiKey") }
            }.body<String>()
            val root = json.parseToJsonElement(response).jsonObject
            val dataArray = root["data"]?.jsonArray ?: return@withContext emptyList()
            dataArray.mapNotNull { elem ->
                (elem as? kotlinx.serialization.json.JsonObject)?.get("id")?.jsonPrimitive?.content
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
