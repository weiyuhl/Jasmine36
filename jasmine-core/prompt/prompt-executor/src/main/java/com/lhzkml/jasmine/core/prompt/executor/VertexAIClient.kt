package com.lhzkml.jasmine.core.prompt.executor

import com.lhzkml.jasmine.core.prompt.llm.ChatClient
import com.lhzkml.jasmine.core.prompt.llm.ChatClientException
import com.lhzkml.jasmine.core.prompt.llm.ErrorType
import com.lhzkml.jasmine.core.prompt.llm.LLMProvider
import com.lhzkml.jasmine.core.prompt.llm.RetryConfig
import com.lhzkml.jasmine.core.prompt.llm.StreamResult
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
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/**
 * Google Vertex AI 客户端
 * 使用服务账号 JSON 进行 OAuth2 Bearer Token 认证
 *
 * 区域端点: https://{LOCATION}-aiplatform.googleapis.com/v1/projects/{PROJECT_ID}/locations/{LOCATION}/publishers/google/models/{model}:generateContent
 * 全局端点: https://aiplatform.googleapis.com/v1/projects/{PROJECT_ID}/locations/global/publishers/google/models/{model}:generateContent
 *
 * 认证流程:
 * 1. 从服务账号 JSON 中提取 client_email 和 private_key
 * 2. 构造 JWT（Header + Claim Set），用 RSA-SHA256 签名
 * 3. POST 到 https://oauth2.googleapis.com/token 换取 access_token
 * 4. 请求时使用 Authorization: Bearer {access_token}
 */
class VertexAIClient(
    private val serviceAccountJson: String,
    private val projectId: String,
    private val location: String = "global",
    private val retryConfig: RetryConfig = RetryConfig.DEFAULT,
    httpClient: HttpClient? = null
) : ChatClient {

    companion object {
        private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        private const val SCOPE = "https://www.googleapis.com/auth/cloud-platform"
        /** access_token 有效期（秒），Google 默认 3600 */
        private const val TOKEN_LIFETIME_SECS = 3600L
    }

    override val provider: LLMProvider = LLMProvider.Gemini

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private val client: HttpClient = httpClient ?: HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(this@VertexAIClient.json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = retryConfig.requestTimeoutMs
            connectTimeoutMillis = 30000
            socketTimeoutMillis = 60000
        }
    }

    /** 缓存的 access_token 和过期时间 */
    @Volatile private var cachedToken: String? = null
    @Volatile private var tokenExpiresAt: Long = 0L

    /** 从服务账号 JSON 解析出的字段 */
    private val serviceAccount: ServiceAccountInfo by lazy {
        parseServiceAccountJson(serviceAccountJson)
    }

    private data class ServiceAccountInfo(
        val clientEmail: String,
        val privateKeyPem: String
    )

    private fun parseServiceAccountJson(jsonStr: String): ServiceAccountInfo {
        val obj = json.decodeFromString<JsonObject>(jsonStr)
        val clientEmail = obj["client_email"]?.jsonPrimitive?.content
            ?: throw ChatClientException(provider.name, "服务账号 JSON 缺少 client_email", ErrorType.AUTHENTICATION)
        val privateKey = obj["private_key"]?.jsonPrimitive?.content
            ?: throw ChatClientException(provider.name, "服务账号 JSON 缺少 private_key", ErrorType.AUTHENTICATION)
        return ServiceAccountInfo(clientEmail, privateKey)
    }

    /**
     * 获取有效的 access_token，如果缓存过期则重新获取
     */
    private suspend fun getAccessToken(): String {
        val now = System.currentTimeMillis() / 1000
        cachedToken?.let { token ->
            if (now < tokenExpiresAt - 60) return token // 提前 60 秒刷新
        }

        val signedJwt = createSignedJwt(now)
        val tokenResponse = client.submitForm(
            url = TOKEN_URL,
            formParameters = parameters {
                append("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                append("assertion", signedJwt)
            }
        )

        if (!tokenResponse.status.isSuccess()) {
            val body = try { tokenResponse.bodyAsText() } catch (_: Exception) { null }
            throw ChatClientException(
                provider.name,
                "获取 access_token 失败: ${tokenResponse.status.value} $body",
                ErrorType.AUTHENTICATION
            )
        }

        val responseObj = json.decodeFromString<JsonObject>(tokenResponse.body<String>())
        val accessToken = responseObj["access_token"]?.jsonPrimitive?.content
            ?: throw ChatClientException(provider.name, "token 响应缺少 access_token", ErrorType.AUTHENTICATION)

        cachedToken = accessToken
        tokenExpiresAt = now + TOKEN_LIFETIME_SECS
        return accessToken
    }

    /**
     * 构造并签名 JWT
     * Header: {"alg":"RS256","typ":"JWT"}
     * Claim Set: {"iss":clientEmail, "scope":SCOPE, "aud":TOKEN_URL, "iat":now, "exp":now+3600}
     */
    private fun createSignedJwt(nowSecs: Long): String {
        val header = """{"alg":"RS256","typ":"JWT"}"""
        val claimSet = """{"iss":"${serviceAccount.clientEmail}","scope":"$SCOPE","aud":"$TOKEN_URL","iat":$nowSecs,"exp":${nowSecs + TOKEN_LIFETIME_SECS}}"""

        val encoder = Base64.getUrlEncoder().withoutPadding()
        val headerB64 = encoder.encodeToString(header.toByteArray())
        val claimB64 = encoder.encodeToString(claimSet.toByteArray())
        val signingInput = "$headerB64.$claimB64"

        // 解析 PEM 私钥
        val privateKeyPem = serviceAccount.privateKeyPem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\n", "")
            .replace("\n", "")
            .replace("\r", "")
            .replace(" ", "")

        val keyBytes = Base64.getDecoder().decode(privateKeyPem)
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        val privateKey = keyFactory.generatePrivate(keySpec)

        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(privateKey)
        signature.update(signingInput.toByteArray())
        val signatureBytes = signature.sign()
        val signatureB64 = encoder.encodeToString(signatureBytes)

        return "$signingInput.$signatureB64"
    }

    /** 构建 Vertex AI 端点 URL
     * - location = "global" → https://aiplatform.googleapis.com/v1/projects/{PROJECT}/locations/global/...
     * - location = "us-central1" 等 → https://{LOCATION}-aiplatform.googleapis.com/v1/projects/{PROJECT}/locations/{LOCATION}/...
     */
    private fun buildUrl(model: String, stream: Boolean): String {
        val action = if (stream) "streamGenerateContent" else "generateContent"
        val host = if (location == "global") {
            "https://aiplatform.googleapis.com"
        } else {
            "https://${location}-aiplatform.googleapis.com"
        }
        return "${host}/v1/projects/${projectId}/locations/${location}/publishers/google/models/${model}:${action}"
    }

    private fun convertMessages(messages: List<ChatMessage>): Pair<GeminiContent?, List<GeminiContent>> {
        var systemInstruction: GeminiContent? = null
        val contents = mutableListOf<GeminiContent>()
        for (msg in messages) {
            when (msg.role) {
                "system" -> systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = msg.content)))
                "user" -> contents.add(GeminiContent(role = "user", parts = listOf(GeminiPart(text = msg.content))))
                "assistant" -> contents.add(GeminiContent(role = "model", parts = listOf(GeminiPart(text = msg.content))))
            }
        }
        return systemInstruction to contents
    }

    override suspend fun chat(messages: List<ChatMessage>, model: String, maxTokens: Int?): String {
        return chatWithUsage(messages, model, maxTokens).content
    }

    override suspend fun chatWithUsage(messages: List<ChatMessage>, model: String, maxTokens: Int?): ChatResult {
        return com.lhzkml.jasmine.core.prompt.llm.executeWithRetry(retryConfig) {
            try {
                val token = getAccessToken()
                val (systemInstruction, contents) = convertMessages(messages)
                val request = GeminiRequest(
                    contents = contents,
                    systemInstruction = systemInstruction,
                    generationConfig = GeminiGenerationConfig(maxOutputTokens = maxTokens)
                )

                val response: HttpResponse = client.post(buildUrl(model, false)) {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer $token")
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
                    Usage(promptTokens = it.promptTokenCount, completionTokens = it.candidatesTokenCount, totalTokens = it.totalTokenCount)
                }
                ChatResult(content = content, usage = usage)
            } catch (e: ChatClientException) { throw e }
            catch (e: UnknownHostException) { throw ChatClientException(provider.name, "无法连接到服务器，请检查网络", ErrorType.NETWORK, cause = e) }
            catch (e: ConnectException) { throw ChatClientException(provider.name, "连接失败，请检查网络", ErrorType.NETWORK, cause = e) }
            catch (e: SocketTimeoutException) { throw ChatClientException(provider.name, "请求超时，请稍后重试", ErrorType.NETWORK, cause = e) }
            catch (e: HttpRequestTimeoutException) { throw ChatClientException(provider.name, "请求超时，请稍后重试", ErrorType.NETWORK, cause = e) }
            catch (e: Exception) { throw ChatClientException(provider.name, "请求失败: ${e.message}", ErrorType.UNKNOWN, cause = e) }
        }
    }

    override fun chatStream(messages: List<ChatMessage>, model: String, maxTokens: Int?): Flow<String> = flow {
        chatStreamWithUsage(messages, model, maxTokens) { chunk -> emit(chunk) }
    }

    override suspend fun chatStreamWithUsage(
        messages: List<ChatMessage>,
        model: String,
        maxTokens: Int?,
        onChunk: suspend (String) -> Unit
    ): StreamResult {
        return com.lhzkml.jasmine.core.prompt.llm.executeWithRetry(retryConfig) {
            try {
                val token = getAccessToken()
                val (systemInstruction, contents) = convertMessages(messages)
                val request = GeminiRequest(
                    contents = contents,
                    systemInstruction = systemInstruction,
                    generationConfig = GeminiGenerationConfig(maxOutputTokens = maxTokens)
                )

                val statement = client.preparePost(buildUrl(model, true)) {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer $token")
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
                        val line = try { channel.readUTF8Line() } catch (_: Exception) { break } ?: break
                        if (line.startsWith("data: ")) {
                            val data = line.removePrefix("data: ").trim()
                            if (data.isEmpty()) continue
                            try {
                                val chunk = json.decodeFromString<GeminiResponse>(data)
                                chunk.usageMetadata?.let {
                                    lastUsage = Usage(promptTokens = it.promptTokenCount, completionTokens = it.candidatesTokenCount, totalTokens = it.totalTokenCount)
                                }
                                val text = chunk.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                                if (!text.isNullOrEmpty()) {
                                    fullContent.append(text)
                                    onChunk(text)
                                }
                            } catch (_: Exception) { /* skip */ }
                        }
                    }
                }

                StreamResult(content = fullContent.toString(), usage = lastUsage)
            } catch (e: ChatClientException) { throw e }
            catch (e: UnknownHostException) { throw ChatClientException(provider.name, "无法连接到服务器，请检查网络", ErrorType.NETWORK, cause = e) }
            catch (e: ConnectException) { throw ChatClientException(provider.name, "连接失败，请检查网络", ErrorType.NETWORK, cause = e) }
            catch (e: SocketTimeoutException) { throw ChatClientException(provider.name, "请求超时，请稍后重试", ErrorType.NETWORK, cause = e) }
            catch (e: HttpRequestTimeoutException) { throw ChatClientException(provider.name, "请求超时，请稍后重试", ErrorType.NETWORK, cause = e) }
            catch (e: Exception) { throw ChatClientException(provider.name, "流式请求失败: ${e.message}", ErrorType.UNKNOWN, cause = e) }
        }
    }

    override suspend fun listModels(): List<ModelInfo> = emptyList()

    override fun close() {
        client.close()
    }
}
