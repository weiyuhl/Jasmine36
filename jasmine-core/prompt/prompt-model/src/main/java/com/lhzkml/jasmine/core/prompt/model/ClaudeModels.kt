package com.lhzkml.jasmine.core.prompt.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    val temperature: Double = 0.7,
    val stream: Boolean = false
)

@Serializable
data class ClaudeMessage(
    val role: String,
    val content: String
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
    val text: String = ""
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
 * GET /v1/models
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
