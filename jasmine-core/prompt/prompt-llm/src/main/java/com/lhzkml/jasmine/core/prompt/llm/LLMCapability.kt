package com.lhzkml.jasmine.core.prompt.llm

/**
 * LLM 模型能力声明
 * 用于描述模型支持的功能特性
 */
sealed class LLMCapability(val id: String) {

    /** 支持调节 temperature 参数 */
    data object Temperature : LLMCapability("temperature")

    /** 支持流式输出 */
    data object Streaming : LLMCapability("streaming")

    /** 支持 Tool/Function Calling */
    data object Tools : LLMCapability("tools")

    /** 支持图片输入（视觉理解） */
    data object Vision : LLMCapability("vision")

    /** 支持音频输入 */
    data object Audio : LLMCapability("audio")

    /** 支持视频输入 */
    data object Video : LLMCapability("video")

    /** 支持文件/文档输入 */
    data object Document : LLMCapability("document")

    /** 支持 JSON Schema 结构化输出 */
    data object StructuredOutput : LLMCapability("structured_output")

    /** 支持推理/思考链（如 DeepSeek-R1、o1） */
    data object Reasoning : LLMCapability("reasoning")

    /** 支持代码生成/执行 */
    data object CodeGeneration : LLMCapability("code_generation")

    /** 自定义能力 */
    data class Custom(val name: String) : LLMCapability(name)

    override fun toString(): String = id
}
