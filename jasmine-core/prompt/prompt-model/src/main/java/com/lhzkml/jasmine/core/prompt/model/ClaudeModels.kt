package com.lhzkml.jasmine.core.prompt.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Claude Messages API 请求体
 */
@Serializable
data class ClaudeRequest(
    val model: String,
    val messages: List<ClaudeMessage>,
    @SerialName("max_tokens")
    val maxTokens: Int = 4096,
    val system: String? = null,
    val temperature: Double? = null,
    @SerialName("top_p")
    val topP: Double? = null,
    @SerialName("top_k")
    val topK: Int? = null,
    val stream: Boolean = false,
    val tools: List<ClaudeToolDef>? = null
)

/**
 * Claude 消息（支持多内容块）
 */
@Serializable
data class ClaudeMessage(
    val role: String,
    val content: @Serializable ClaudeMessageContent
)

/**
 * Claude 消息内容：可以是纯文本字符串或内容块列表
 * 使用 kotlinx.serialization 的多态序列化
 */
@Serializable(with = ClaudeMessageContentSerializer::class)
sealed class ClaudeMessageContent {
    data class Text(val text: String) : ClaudeMessageContent()
    data class Blocks(val blocks: List<ClaudeContentBlock>) : ClaudeMessageContent()
}

/**
 * Claude 工具定义
 */
@Serializable
data class ClaudeToolDef(
    val name: String,
    val description: String? = null,
    @SerialName("input_schema")
    val inputSchema: JsonObject
)

/**
 * Claude Messages API 响应体
 */
@Serializable
data class ClaudeResponse(
    val id: String = "",
    val type: String = "message",
    val role: String = "assistant",
    val content: List<ClaudeContentBlock> = emptyList(),
    val model: String = "",
    @SerialName("stop_reason")
    val stopReason: String? = null,
    val usage: ClaudeUsage? = null
)

@Serializable
data class ClaudeContentBlock(
    val type: String = "text",
    val text: String? = null,
    /** tool_use 块的字段 */
    val id: String? = null,
    val name: String? = null,
    val input: JsonObject? = null,
    /** tool_result 块的字段 */
    @SerialName("tool_use_id")
    val toolUseId: String? = null,
    val content: String? = null
)

@Serializable
data class ClaudeUsage(
    @SerialName("input_tokens")
    val inputTokens: Int = 0,
    @SerialName("output_tokens")
    val outputTokens: Int = 0
)

/**
 * Claude 流式事件
 */
@Serializable
data class ClaudeStreamEvent(
    val type: String = "",
    val index: Int? = null,
    val delta: ClaudeStreamDelta? = null,
    val message: ClaudeStreamMessage? = null,
    @SerialName("content_block")
    val contentBlock: ClaudeContentBlock? = null,
    val usage: ClaudeUsage? = null
)

@Serializable
data class ClaudeStreamDelta(
    val type: String = "",
    val text: String? = null,
    @SerialName("partial_json")
    val partialJson: String? = null,
    @SerialName("stop_reason")
    val stopReason: String? = null,
    val usage: ClaudeUsage? = null
)

@Serializable
data class ClaudeStreamMessage(
    val id: String = "",
    val usage: ClaudeUsage? = null
)

/**
 * Claude List Models API 响应
 */
@Serializable
data class ClaudeModelListResponse(
    val data: List<ClaudeModelInfo> = emptyList(),
    @SerialName("has_more")
    val hasMore: Boolean = false,
    @SerialName("first_id")
    val firstId: String? = null,
    @SerialName("last_id")
    val lastId: String? = null
)

@Serializable
data class ClaudeModelInfo(
    val id: String = "",
    val type: String = "model",
    @SerialName("display_name")
    val displayName: String = "",
    @SerialName("created_at")
    val createdAt: String = ""
)
