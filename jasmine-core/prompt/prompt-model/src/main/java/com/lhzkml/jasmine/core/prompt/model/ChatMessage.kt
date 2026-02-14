package com.lhzkml.jasmine.core.prompt.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * 聊天消息
 * @param role 角色：system / user / assistant
 * @param content 消息内容
 * @param timestamp 消息创建时间戳（毫秒），null 表示未记录
 * @param finishReason 完成原因（仅 assistant 消息），如 "stop"、"length"、"tool_calls" 等
 * @param metadata 自定义元数据，可存储任意 JSON 键值对
 */
@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
    @kotlinx.serialization.Transient
    val timestamp: Long? = null,
    @kotlinx.serialization.Transient
    val finishReason: String? = null,
    @kotlinx.serialization.Transient
    val metadata: JsonObject? = null
) {
    companion object {
        /** 创建系统消息 */
        fun system(content: String) = ChatMessage("system", content)

        /** 创建用户消息 */
        fun user(content: String) = ChatMessage("user", content)

        /** 创建助手消息 */
        fun assistant(content: String) = ChatMessage("assistant", content)

        /** 创建带元数据的助手消息 */
        fun assistant(content: String, finishReason: String? = null, timestamp: Long? = null) =
            ChatMessage("assistant", content, timestamp = timestamp, finishReason = finishReason)
    }

    /** 创建带时间戳的副本 */
    fun withTimestamp(ts: Long = System.currentTimeMillis()): ChatMessage =
        copy(timestamp = ts)

    /** 创建带元数据的副本 */
    fun withMetadata(meta: JsonObject): ChatMessage =
        copy(metadata = meta)
}
