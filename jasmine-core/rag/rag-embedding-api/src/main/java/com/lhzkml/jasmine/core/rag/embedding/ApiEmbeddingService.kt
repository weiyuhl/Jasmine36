package com.lhzkml.jasmine.core.rag.embedding

import com.lhzkml.jasmine.core.rag.EmbeddingService
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

/**
 * 基于远程 API 的 Embedding 服务
 * 支持 OpenAI 兼容的 /v1/embeddings 端点。
 */
class ApiEmbeddingService(
    private val config: EmbeddingRequestConfig,
    private val httpClient: HttpClient = defaultClient()
) : EmbeddingService {

    override val dimensions: Int get() = config.dimensions

    override suspend fun embed(text: String): FloatArray? = withContext(Dispatchers.IO) {
        request(listOf(text))?.data?.firstOrNull()?.embedding?.toFloatArray()
    }

    override suspend fun embedBatch(texts: List<String>): List<FloatArray?> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyList()
        val response = request(texts) ?: return@withContext texts.map { null }
        response.data.map { it.embedding?.toFloatArray() }
    }

    private suspend fun request(input: List<String>): EmbeddingResponse? {
        val url = "${config.baseUrl.trimEnd('/')}/v1/embeddings"
        val dims = config.dimensions.takeIf { it in 256..3072 }
        val body = if (input.size == 1) {
            Json.encodeToString(EmbeddingReqSingle(config.model, input.first(), dims))
        } else {
            Json.encodeToString(EmbeddingReqBatch(config.model, input, dims))
        }
        return try {
            httpClient.post(url) {
                headers { append("Authorization", "Bearer ${config.apiKey}") }
                contentType(ContentType.Application.Json)
                setBody(body)
            }.body<EmbeddingResponse>()
        } catch (e: Exception) {
            null
        }
    }

    @Serializable
    private data class EmbeddingReqSingle(
        val model: String,
        val input: String,
        val dimensions: Int? = null
    )

    @Serializable
    private data class EmbeddingReqBatch(
        val model: String,
        val input: List<String>,
        val dimensions: Int? = null
    )

    @Serializable
    private data class EmbeddingResponse(
        val data: List<EmbeddingData>
    )

    @Serializable
    private data class EmbeddingData(
        val embedding: List<Double> = emptyList()
    )

    companion object {
        private fun defaultClient() = HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
}

private fun List<Double>.toFloatArray(): FloatArray = FloatArray(size) { get(it).toFloat() }
