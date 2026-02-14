package com.lhzkml.jasmine.core.prompt.executor

import com.lhzkml.jasmine.core.prompt.llm.LLMProvider
import com.lhzkml.jasmine.core.prompt.llm.RetryConfig
import io.ktor.client.*

/**
 * 通用 Gemini 兼容客户端
 * 用于支持任意使用 Gemini generateContent API 格式的供应商
 */
class GenericGeminiClient(
    providerName: String,
    apiKey: String,
    baseUrl: String,
    generatePath: String = DEFAULT_GENERATE_PATH,
    streamPath: String = DEFAULT_STREAM_PATH,
    retryConfig: RetryConfig = RetryConfig.DEFAULT,
    httpClient: HttpClient? = null
) : GeminiClient(apiKey, baseUrl, retryConfig, httpClient, generatePath, streamPath) {

    override val provider = LLMProvider.Custom(providerName)
}
