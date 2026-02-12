package com.lhzkml.jasmine.core.provider

/**
 * LLM 供应商标识。
 * 参考 Koog 的 LLMProvider 设计，用 sealed class 保证类型安全。
 *
 * @property id 供应商唯一标识
 * @property displayName 供应商显示名称
 */
sealed class LLMProvider(val id: String, val displayName: String) {

    /** DeepSeek 官方 */
    data object DeepSeek : LLMProvider("deepseek", "DeepSeek")

    /** 硅基流动 */
    data object SiliconFlow : LLMProvider("siliconflow", "硅基流动")
}
