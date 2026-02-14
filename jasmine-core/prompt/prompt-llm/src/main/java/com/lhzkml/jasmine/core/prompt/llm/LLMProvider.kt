package com.lhzkml.jasmine.core.prompt.llm

/**
 * LLM 供应商定义
 */
sealed class LLMProvider(val name: String) {
    /** OpenAI 官方 */
    data object OpenAI : LLMProvider("OpenAI")

    /** DeepSeek 官方 */
    data object DeepSeek : LLMProvider("DeepSeek")

    /** 硅基流动 */
    data object SiliconFlow : LLMProvider("SiliconFlow")

    /** Anthropic Claude */
    data object Claude : LLMProvider("Claude")

    /** Google Gemini */
    data object Gemini : LLMProvider("Gemini")

    /** 自定义供应商（用于动态注册的供应商） */
    class Custom(name: String) : LLMProvider(name)
}
