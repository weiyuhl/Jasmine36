package com.lhzkml.jasmine.core.prompt.executor

import com.lhzkml.jasmine.core.prompt.llm.LLMProvider
import io.ktor.client.*

/**
 * 硅基流动客户端
 */
class SiliconFlowClient(
    apiKey: String,
    baseUrl: String = DEFAULT_BASE_URL,
    httpClient: HttpClient? = null
) : OpenAICompatibleClient(apiKey, baseUrl, httpClient) {

    companion object {
        const val DEFAULT_BASE_URL = "https://api.siliconflow.cn"
    }

    override val provider = LLMProvider.SiliconFlow
}
