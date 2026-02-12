package com.lhzkml.jasmine.core.prompt.llm

/**
 * LLM 供应商定义
 */
sealed class LLMProvider(val name: String) {
    /** DeepSeek 官方 */
    data object DeepSeek : LLMProvider("DeepSeek")

    /** 硅基流动 */
    data object SiliconFlow : LLMProvider("SiliconFlow")
}
