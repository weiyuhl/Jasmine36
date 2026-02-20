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
import com.lhzkml.jasmine.core.prompt.model.GeminiFunctionCall
import com.lhzkml.jasmine.core.prompt.model.GeminiFunctionDeclaration
import com.lhzkml.jasmine.core.prompt.model.GeminiFunctionResponse
import com.lhzkml.jasmine.core.prompt.model.GeminiGenerationConfig
import com.lhzkml.jasmine.core.prompt.model.GeminiPart
import com.lhzkml.jasmine.core.prompt.model.GeminiRequest
import com.lhzkml.jasmine.core.prompt.model.GeminiResponse
import com.lhzkml.jasmine.core.prompt.model.GeminiToolDef
import com.lhzkml.jasmine.core.prompt.model.ModelInfo
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
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.coroutines.coroutineContext
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/**
 * Google Vertex AI 客户端
 * 使用服务账号 JSON 进行 OAuth2 Bearer Token 认证
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
            connectTimeoutMillis = retryConfig.connectTimeoutMs
            socketTimeoutMillis = retryConfig.socketTimeoutMs
        }
    }

    @Volatile private var cachedToken: String? = null
    @Volatile private var tokenExpiresAt: Long = 0L

    private val serviceAccount: ServiceAccountInfo by lazy {
        parseServiceAccountJson(serviceAccountJson)
    }

    private data class ServiceAccountInfo(val clientEmail: String, val privateKeyPem: String)

    private fun parseServiceAccountJson(jsonStr: String): ServiceAccountInfo {
        val obj = json.decodeFromString<JsonObject>(jsonStr)
        val clientEmail = obj["client_email"]?.jsonPrimitive?.content
            ?: throw ChatClientException(provider.name, "服务账号 JSON 缺少 client_email", ErrorType.AUTHENTICATION)
        val privateKey = obj["private_key"]?.jsonPrimitive?.content
            ?: throw ChatClientException(provider.name, "服务账号 JSON 缺少 private_key", ErrorType.AUTHENTICATION)
        return ServiceAccountInfo(clientEmail, privateKey)
    }

    private suspend fun getAccessToken(): String {
        val now = System.currentTimeMillis() / 1000
        cachedToken?.let { token ->
            if (now < tokenExpiresAt - 60) return token
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
            throw ChatClientException(provider.name, "获取 access_token 失败: ${tokenResponse.status.value} $body", ErrorType.AUTHENTICATION)
        }
        val responseObj = json.decodeFromString<JsonObject>(tokenResponse.body<String>())
        val accessToken = responseObj["access_token"]?.jsonPrimitive?.content
            ?: throw ChatClientException(provider.name, "token 响应缺少 access_token", ErrorType.AUTHENTICATION)
        cachedToken = accessToken
        tokenExpiresAt = now + TOKEN_LIFETIME_SECS
        return accessToken
    }

    private fun createSignedJwt(nowSecs: Long): String {
        val header = """{"alg":"RS256","typ":"JWT"}"""
        val claimSet = """{"iss":"${serviceAccount.clientEmail}","scope":"$SCOPE","aud":"$TOKEN_URL","iat":$nowSecs,"exp":${nowSecs + TOKEN_LIFETIME_SECS}}"""
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val headerB64 = encoder.encodeToString(header.toByteArray())
        val claimB64 = encoder.encodeToString(claimSet.toByteArray())
        val signingInput = "$headerB64.$claimB64"
        val privateKeyPem = serviceAccount.privateKeyPem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\n", "").replace("\n", "").replace("\r", "").replace(" ", "")
        val keyBytes = Base64.getDecoder().decode(privateKeyPem)
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        val privateKey = keyFactory.generatePrivate(keySpec)
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(privateKey)
        signature.update(signingInput.toByteArray())
        val signatureB64 = encoder.encodeToString(signature.sign())
        return "$signingInput.$signatureB64"
    }

    private fun buildUrl(model: String, stream: Boolean): String {
        val action = if (stream) "streamGenerateContent" else "generateContent"
        val host = if (location == "global") "https://aiplatform.googleapis.com"
        else "https://${location}-aiplatform.googleapis.com"
        return "${host}/v1/projects/${projectId}/locations/${location}/publishers/google/models/${model}:${action}"
    }

    private fun convertMessages(messages: List<ChatMessage>): Pair<GeminiContent?, List<GeminiContent>> {
        var systemInstruction: GeminiContent? = null
        val contents = mutableListOf<GeminiContent>()
        for (msg in messages) {
            val msgToolCalls = msg.toolCalls
            when {
                msg.role == "system" -> systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = msg.content)))
                msg.role == "assistant" && !msgToolCalls.isNullOrEmpty() -> {
                    val parts = mutableListOf<GeminiPart>()
                    if (msg.content.isNotEmpty()) parts.add(GeminiPart(text = msg.content))
                    msgToolCalls.forEach { tc ->
                        val argsJson = try {
                            json.parseToJsonElement(tc.arguments) as? JsonObject ?: buildJsonObject {}
                        } catch (_: Exception) { buildJsonObject {} }
                        parts.add(GeminiPart(functionCall = GeminiFunctionCall(name = tc.name, args = argsJson)))
                    }
                    contents.add(GeminiContent(role = "model", parts = parts))
                }
                msg.role == "tool" -> {
                    val responseJson = try {
                        json.parseToJsonElement(msg.content) as? JsonObject
                            ?: buildJsonObject { put("result", msg.content) }
                    } catch (_: Exception) { buildJsonObject { put("result", msg.content) } }
                    contents.add(GeminiContent(role = "user", parts = listOf(
                        GeminiPart(functionResponse = GeminiFunctionResponse(name = msg.toolName ?: "", response = responseJson))
                    )))
                }
                msg.role == "user" -> contents.add(GeminiContent(role = "user", parts = listOf(GeminiPart(text = msg.content))))
                msg.role == "assistant" -> contents.add(GeminiContent(role = "model", parts = listOf(GeminiPart(text = msg.content))))
            }
        }
        return systemInstruction to contents
    }

    private fun convertTools(tools: List<ToolDescriptor>): List<GeminiToolDef>? {
        if (tools.isEmpty()) return null
        return listOf(GeminiToolDef(functionDeclarations = tools.map { tool ->
            GeminiFunctionDeclaration(name = tool.name, description = tool.description, parameters = tool.toJsonSchema())
        }))
    }

    override suspend fun chat(
        messages: List<ChatMessage>, model: String, maxTokens: Int?,
        samplingParams: SamplingParams?, tools: List<ToolDescriptor>
    ): String = chatWithUsage(messages, model, maxTokens, samplingParams, tools).content

    override suspend fun chatWithUsage(
        messages: List<ChatMessage>, model: String, maxTokens: Int?,
        samplingParams: SamplingParams?, tools: List<ToolDescriptor>
    ): ChatResult {
        val content = StringBuilder()
        val streamResult = chatStreamWithUsage(messages, model, maxTokens, samplingParams, tools) { chunk ->
            content.append(chunk)
        }
        return ChatResult(
            content = content.toString(),
            usage = streamResult.usage,
            finishReason = streamResult.finishReason,
            toolCalls = streamResult.toolCalls
        )
    }

    override fun chatStream(
        messages: List<ChatMessage>, model: String, maxTokens: Int?,
        samplingParams: SamplingParams?, tools: List<ToolDescriptor>
    ): Flow<String> = flow {
        chatStreamWithUsage(messages, model, maxTokens, samplingParams, tools) { chunk -> emit(chunk) }
    }

    override suspend fun chatStreamWithUsage(
        messages: List<ChatMessage>, model: String, maxTokens: Int?,
        samplingParams: SamplingParams?, tools: List<ToolDescriptor>,
        onChunk: suspend (String) -> Unit
    ): StreamResult {
        return com.lhzkml.jasmine.core.prompt.llm.executeWithRetry(retryConfig) {
            try {
                val token = getAccessToken()
                val (systemInstruction, contents) = convertMessages(messages)
                val request = GeminiRequest(
                    contents = contents, systemInstruction = systemInstruction,
                    generationConfig = GeminiGenerationConfig(
                        temperature = samplingParams?.temperature, topP = samplingParams?.topP,
                        topK = samplingParams?.topK, maxOutputTokens = maxTokens
                    ),
                    tools = convertTools(tools)
                )
                val statement = client.preparePost(buildUrl(model, true)) {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer $token")
                    parameter("alt", "sse")
                    setBody(request)
                }
                val fullContent = StringBuilder()
                var lastUsage: Usage? = null
                var lastFinishReason: String? = null
                val toolCalls = mutableListOf<ToolCall>()

                statement.execute { response ->
                    if (!response.status.isSuccess()) {
                        val body = try { response.bodyAsText() } catch (_: Exception) { null }
                        throw ChatClientException.fromStatusCode(provider.name, response.status.value, body)
                    }
                    val channel: ByteReadChannel = response.bodyAsChannel()
                    while (!channel.isClosedForRead) {
                        coroutineContext.ensureActive()
                        val line = try { channel.readUTF8Line() } catch (_: Exception) { break } ?: break
                        if (line.startsWith("data: ")) {
                            val data = line.removePrefix("data: ").trim()
                            if (data.isEmpty()) continue
                            try {
                                val chunk = json.decodeFromString<GeminiResponse>(data)
                                chunk.usageMetadata?.let {
                                    lastUsage = Usage(promptTokens = it.promptTokenCount, completionTokens = it.candidatesTokenCount, totalTokens = it.totalTokenCount)
                                }
                                val firstCandidate = chunk.candidates?.firstOrNull()
                                if (firstCandidate?.finishReason != null) lastFinishReason = firstCandidate.finishReason
                                firstCandidate?.content?.parts?.forEach { part ->
                                    val text = part.text
                                    if (!text.isNullOrEmpty()) { fullContent.append(text); onChunk(text) }
                                    part.functionCall?.let { fc ->
                                        toolCalls.add(ToolCall(id = "vertex_${fc.name}_${System.nanoTime()}", name = fc.name, arguments = fc.args?.toString() ?: "{}"))
                                    }
                                }
                            } catch (_: Exception) { /* skip */ }
                        }
                    }
                }
                StreamResult(content = fullContent.toString(), usage = lastUsage, finishReason = lastFinishReason, toolCalls = toolCalls)
            } catch (e: ChatClientException) { throw e }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
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
