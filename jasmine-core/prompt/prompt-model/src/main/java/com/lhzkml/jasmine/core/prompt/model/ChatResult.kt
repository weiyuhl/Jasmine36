package com.lhzkml.jasmine.core.prompt.model

/**
 * 聊天结果，包含回复内容和 token 用量
 * @param content 助手回复的文本内容
 * @param usage token 用量统计，可能为 null（流式模式下部分供应商不返回）
 */
data class ChatResult(
    val content: String,
    val usage: Usage? = null
)
