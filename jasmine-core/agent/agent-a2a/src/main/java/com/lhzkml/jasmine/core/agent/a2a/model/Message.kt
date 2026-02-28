package com.lhzkml.jasmine.core.agent.a2a.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * A2A 消息角色
 * 完整移植 koog �?Role
 */
@Serializable
enum class Role {
    @SerialName("user") User,
    @SerialName("agent") Agent
}

/**
 * A2A 消息
 * 完整移植 koog �?Message.kt
 *
 * 表示用户�?Agent 之间对话中的一条消息�?
 *
 * @param messageId 消息唯一标识（UUID�?
 * @param role 发送者角�?
 * @param parts 消息内容部分列表
 * @param extensions 相关扩展 URI 列表
 * @param taskId 所属任�?ID（新任务的第一条消息可省略�?
 * @param referenceTaskIds 引用的其他任�?ID 列表
 * @param contextId 上下�?ID
 * @param metadata 扩展元数�?
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Message(
    val messageId: String,
    val role: Role,
    val parts: List<Part>,
    val extensions: List<String>? = null,
    val taskId: String? = null,
    val referenceTaskIds: List<String>? = null,
    val contextId: String? = null,
    val metadata: JsonObject? = null
) : CommunicationEvent {
    @EncodeDefault
    override val kind: String = "message"
}
