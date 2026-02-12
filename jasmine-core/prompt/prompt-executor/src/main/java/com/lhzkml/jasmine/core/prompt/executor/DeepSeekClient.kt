package com.lhzkml.jasmine.core.prompt.executor

import com.lhzkml.jasmine.core.prompt.llm.LLMProvider

/**
 * DeepSeek 客户端
 */
class DeepSeekClient(
    apiKey: String,
    baseUrl: String = DEFAULT_BASE_URL
) : OpenAICompatibleClient(apiKey, baseUrl) {

    companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
    }

    override val provider = LLMProvider.DeepSeek
}
