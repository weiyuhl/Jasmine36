package com.lhzkml.jasmine.core.client

import com.lhzkml.jasmine.core.provider.LLMProvider

/**
 * DeepSeek 官方客户端。
 * 继承 OpenAI 兼容基类，使用 DeepSeek 官方 API 地址。
 *
 * @param apiKey DeepSeek API 密钥
 * @param baseUrl API 地址，默认为 DeepSeek 官方地址
 */
class DeepSeekClient(
    apiKey: String,
    baseUrl: String = DEFAULT_BASE_URL
) : OpenAICompatibleClient(
    apiKey = apiKey,
    baseUrl = baseUrl
) {
    override val provider: LLMProvider = LLMProvider.DeepSeek

    companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
        const val DEFAULT_MODEL = "deepseek-chat"
    }
}
