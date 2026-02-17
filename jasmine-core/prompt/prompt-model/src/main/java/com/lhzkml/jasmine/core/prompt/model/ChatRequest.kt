package com.lhzkml.jasmine.core.prompt.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * 聊天请求体，兼容 OpenAI API 格式
 * 适用于 OpenAI、DeepSeek、硅基流动等
 */
@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<OpenAIRequestMessage>,
    val temperature: Double? = null,
    @SerialName("top_p")
    val topP: Double? = null,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    val stream: Boolean = false,
    val tools: List<OpenAIToolDef>? = null
)

/**
 * OpenAI 请求消息格式（支持 tool 角色和 tool_calls）
 */
@Serializable
data class OpenAIRequestMessage(
    val role: String,
    val content: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<OpenAIToolCallDef>? = null,
    @SerialName("tool_call_id")
    val toolCallId: String? = null
)

/**
 * OpenAI 工具定义（请求体中的 tools 数组元素）
 */
@Serializable
data class OpenAIToolDef(
    val type: String = "function",
    val function: OpenAIFunctionDef
)

@Serializable
data class OpenAIFunctionDef(
    val name: String,
    val description: String? = null,
    val parameters: JsonObject? = null
)

/**
 * OpenAI tool_calls 定义（用于 assistant 消息中的 tool_calls 字段）
 */
@Serializable
data class OpenAIToolCallDef(
    val id: String,
    val type: String = "function",
    val function: OpenAIFunctionCallDef
)

@Serializable
data class OpenAIFunctionCallDef(
    val name: String,
    val arguments: String
)
