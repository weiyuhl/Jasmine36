package com.lhzkml.jasmine.core.prompt.executor

import com.lhzkml.jasmine.core.prompt.llm.ChatClient
import com.lhzkml.jasmine.core.prompt.llm.RetryConfig

/**
 * API 渠道类型
 * 决定使用哪种 API 协议与 LLM 通信。
 */
enum class ApiType {
    /** OpenAI 兼容格式（DeepSeek、硅基流动等） */
    OPENAI,
    /** Claude Messages API 格式 */
    CLAUDE,
    /** Gemini generateContent API 格式 */
    GEMINI
}

/**
 * ChatClient 创建配置
 */
data class ChatClientConfig(
    val providerId: String,
    val providerName: String,
    val apiKey: String,
    val baseUrl: String,
    val apiType: ApiType,
    val chatPath: String? = null,
    val vertexEnabled: Boolean = false,
    val vertexProjectId: String = "",
    val vertexLocation: String = "global",
    val vertexServiceAccountJson: String = "",
    /** 请求超时（毫秒），0 表示使用默认值 */
    val requestTimeoutMs: Long = 0,
    /** 连接超时（毫秒），0 表示使用默认值 */
    val connectTimeoutMs: Long = 0,
    /** Socket 读取超时（毫秒），0 表示使用默认值 */
    val socketTimeoutMs: Long = 0
)

/**
 * ChatClient 工厂
 * 根据 API 类型和供应商 ID 创建对应的 ChatClient 实例。
 */
object ChatClientFactory {

    /**
     * 根据配置创建 ChatClient 实例
     */
    fun create(config: ChatClientConfig): ChatClient {
        val retryConfig = buildRetryConfig(config)
        return when (config.apiType) {
            ApiType.OPENAI -> createOpenAICompatible(config, retryConfig)
            ApiType.CLAUDE -> createClaudeCompatible(config, retryConfig)
            ApiType.GEMINI -> createGeminiCompatible(config, retryConfig)
        }
    }

    private fun buildRetryConfig(config: ChatClientConfig): RetryConfig {
        val default = RetryConfig.DEFAULT
        return default.copy(
            requestTimeoutMs = if (config.requestTimeoutMs > 0) config.requestTimeoutMs else default.requestTimeoutMs,
            connectTimeoutMs = if (config.connectTimeoutMs > 0) config.connectTimeoutMs else default.connectTimeoutMs,
            socketTimeoutMs = if (config.socketTimeoutMs > 0) config.socketTimeoutMs else default.socketTimeoutMs
        )
    }

    private fun createOpenAICompatible(config: ChatClientConfig, retryConfig: RetryConfig): ChatClient {
        val chatPath = config.chatPath ?: "/v1/chat/completions"
        return when (config.providerId) {
            "openai" -> OpenAIClient(apiKey = config.apiKey, baseUrl = config.baseUrl, retryConfig = retryConfig, chatPath = chatPath)
            "deepseek" -> DeepSeekClient(apiKey = config.apiKey, baseUrl = config.baseUrl, retryConfig = retryConfig, chatPath = chatPath)
            "siliconflow" -> SiliconFlowClient(apiKey = config.apiKey, baseUrl = config.baseUrl, retryConfig = retryConfig, chatPath = chatPath)
            else -> GenericOpenAIClient(
                providerName = config.providerName,
                apiKey = config.apiKey,
                baseUrl = config.baseUrl,
                retryConfig = retryConfig,
                chatPath = chatPath
            )
        }
    }

    private fun createClaudeCompatible(config: ChatClientConfig, retryConfig: RetryConfig): ChatClient {
        return when (config.providerId) {
            "claude" -> ClaudeClient(apiKey = config.apiKey, baseUrl = config.baseUrl, retryConfig = retryConfig)
            else -> GenericClaudeClient(
                providerName = config.providerName,
                apiKey = config.apiKey,
                baseUrl = config.baseUrl,
                retryConfig = retryConfig
            )
        }
    }

    private fun createGeminiCompatible(config: ChatClientConfig, retryConfig: RetryConfig): ChatClient {
        if (config.vertexEnabled && config.vertexServiceAccountJson.isNotEmpty()) {
            return VertexAIClient(
                serviceAccountJson = config.vertexServiceAccountJson,
                projectId = config.vertexProjectId,
                location = config.vertexLocation,
                retryConfig = retryConfig
            )
        }

        val genPath = config.chatPath ?: GeminiClient.DEFAULT_GENERATE_PATH
        val streamPath = genPath.replace(":generateContent", ":streamGenerateContent")
        return when (config.providerId) {
            "gemini" -> GeminiClient(
                apiKey = config.apiKey,
                baseUrl = config.baseUrl,
                retryConfig = retryConfig,
                generatePath = genPath,
                streamPath = streamPath
            )
            else -> GenericGeminiClient(
                providerName = config.providerName,
                apiKey = config.apiKey,
                baseUrl = config.baseUrl,
                retryConfig = retryConfig,
                generatePath = genPath,
                streamPath = streamPath
            )
        }
    }
}
