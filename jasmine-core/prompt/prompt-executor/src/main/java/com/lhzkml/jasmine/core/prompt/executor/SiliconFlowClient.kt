package com.lhzkml.jasmine.core.prompt.executor

import com.lhzkml.jasmine.core.prompt.llm.LLMProvider

/**
 * 硅基流动客户端
 */
class SiliconFlowClient(
    apiKey: String,
    baseUrl: String = DEFAULT_BASE_URL
) : OpenAICompatibleClient(apiKey, baseUrl) {

    companion object {
        const val DEFAULT_BASE_URL = "https://api.siliconflow.cn"
    }

    override val provider = LLMProvider.SiliconFlow
}
