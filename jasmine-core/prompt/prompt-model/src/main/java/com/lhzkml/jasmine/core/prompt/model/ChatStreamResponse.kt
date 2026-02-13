package com.lhzkml.jasmine.core.prompt.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 流式聊天响应体，兼容 OpenAI SSE 格式
 * 与 ChatResponse 的区别：choices 中使用 delta 而非 message
 */
@Serializable
data class ChatStreamResponse(
    val id: String = "",
    val choices: List<StreamChoice> = emptyList(),
    val usage: Usage? = null
)

/** 流式响应中的单个选项 */
@Serializable
data class StreamChoice(
    val index: Int = 0,
    val delta: Delta = Delta(),
    @SerialName("finish_reason")
    val finishReason: String? = null
)

/** 流式增量内容 */
@Serializable
data class Delta(
    val role: String? = null,
    val content: String? = null
)
