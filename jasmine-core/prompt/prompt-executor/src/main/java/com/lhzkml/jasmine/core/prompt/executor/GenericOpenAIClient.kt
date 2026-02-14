package com.lhzkml.jasmine.core.prompt.executor

import com.lhzkml.jasmine.core.prompt.llm.LLMProvider
import com.lhzkml.jasmine.core.prompt.llm.RetryConfig
import io.ktor.client.*

/**
 * 通用 OpenAI 兼容客户端
 * 用于支持任意 OpenAI 兼容的供应商
 */
class GenericOpenAIClient(
    providerName: String,
    apiKey: String,
    baseUrl: String,
    retryConfig: RetryConfig = RetryConfig.DEFAULT,
    httpClient: HttpClient? = null
) : OpenAICompatibleClient(apiKey, baseUrl, retryConfig, httpClient) {

    override val provider = LLMProvider.Custom(providerName)
}
