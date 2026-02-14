package com.lhzkml.jasmine.core.prompt.model

/**
 * 采样参数
 * 控制 LLM 生成文本时的随机性和多样性
 *
 * @param temperature 温度，控制随机性。值越高输出越多样，越低越确定。
 *   - OpenAI/DeepSeek/硅基流动: 0.0~2.0, 默认 1.0
 *   - Claude: 0.0~1.0, 默认 1.0
 *   - Gemini: 0.0~maxTemperature（模型相关）, 默认由模型决定
 * @param topP 核采样阈值，只考虑累积概率达到 topP 的 token 集合。
 *   - 所有供应商均支持: 0.0~1.0
 * @param topK 只考虑概率最高的 K 个 token。
 *   - 仅 Claude 和 Gemini 支持，OpenAI/DeepSeek/硅基流动不支持
 */
data class SamplingParams(
    val temperature: Double? = null,
    val topP: Double? = null,
    val topK: Int? = null
) {
    companion object {
        val DEFAULT = SamplingParams()
    }
}
