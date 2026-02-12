package com.lhzkml.jasmine.core.client

import com.lhzkml.jasmine.core.provider.LLMProvider

/**
 * 硅基流动客户端。
 * 继承 OpenAI 兼容基类，使用硅基流动 API 地址。
 *
 * @param apiKey 硅基流动 API 密钥
 * @param baseUrl API 地址，默认为硅基流动官方地址
 */
class SiliconFlowClient(
    apiKey: String,
    baseUrl: String = DEFAULT_BASE_URL
) : OpenAICompatibleClient(
    apiKey = apiKey,
    baseUrl = baseUrl
) {
    override val provider: LLMProvider = LLMProvider.SiliconFlow

    companion object {
        const val DEFAULT_BASE_URL = "https://api.siliconflow.cn"
        const val DEFAULT_MODEL = "deepseek-ai/DeepSeek-V3"
    }
}
