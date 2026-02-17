package com.lhzkml.jasmine.core.prompt.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 聊天响应体，兼容 OpenAI API 格式
 */
@Serializable
data class ChatResponse(
    val id: String = "",
    val choices: List<Choice> = emptyList(),
    val usage: Usage? = null
)

/** 响应中的单个选项 */
@Serializable
data class Choice(
    val index: Int = 0,
    val message: ResponseMessage,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

/**
 * 响应消息（支持 tool_calls）
 */
@Serializable
data class ResponseMessage(
    val role: String? = null,
    val content: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<OpenAIToolCallDef>? = null,
    /** DeepSeek R1 等模型的推理过程 */
    @SerialName("reasoning_content")
    val reasoningContent: String? = null
)

/** Token 用量统计 */
@Serializable
data class Usage(
    @SerialName("prompt_tokens")
    val promptTokens: Int = 0,
    @SerialName("completion_tokens")
    val completionTokens: Int = 0,
    @SerialName("total_tokens")
    val totalTokens: Int = 0
)
