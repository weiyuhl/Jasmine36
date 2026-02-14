package com.lhzkml.jasmine.core.prompt.executor

import com.lhzkml.jasmine.core.prompt.llm.LLMProvider
import com.lhzkml.jasmine.core.prompt.llm.RetryConfig
import io.ktor.client.*

/**
 * 通用 Claude 兼容客户端
 * 用于支持任意使用 Claude Messages API 格式的供应商
 */
class GenericClaudeClient(
    providerName: String,
    apiKey: String,
    baseUrl: String,
    retryConfig: RetryConfig = RetryConfig.DEFAULT,
    httpClient: HttpClient? = null
) : ClaudeClient(apiKey, baseUrl, retryConfig, httpClient) {

    override val provider = LLMProvider.Custom(providerName)
}
