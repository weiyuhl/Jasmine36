package com.lhzkml.jasmine.core.prompt.executor

import com.lhzkml.jasmine.core.prompt.llm.LLMProvider
import com.lhzkml.jasmine.core.prompt.llm.RetryConfig
import io.ktor.client.*

/**
 * OpenAI 官方客户端
 */
class OpenAIClient(
    apiKey: String,
    baseUrl: String = DEFAULT_BASE_URL,
    chatPath: String = "/v1/chat/completions",
    retryConfig: RetryConfig = RetryConfig.DEFAULT,
    httpClient: HttpClient? = null
) : OpenAICompatibleClient(apiKey, baseUrl, retryConfig, httpClient, chatPath) {

    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com"
    }

    override val provider = LLMProvider.OpenAI
}
