package com.lhzkml.jasmine.core.agent.tools.a2a.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * A2A 任务
 * 完整移植 koog 的 Task.kt
 *
 * 表示客户端和 Agent 之间的一次有状态操作或对话。
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Task(
    val id: String,
    override val contextId: String,
    val status: TaskStatus,
    val history: List<Message>? = null,
    val artifacts: List<Artifact>? = null,
    val metadata: JsonObject? = null
) : CommunicationEvent, TaskEvent {
    @EncodeDefault
    override val kind: String = "task"
    override val taskId: String get() = id
}

/**
 * 任务状态
 * @param state 当前生命周期状态
 * @param message 可选的状态描述消息
 * @param timestamp 状态记录时间戳（毫秒）
 */
@Serializable
data class TaskStatus(
    val state: TaskState,
    val message: Message? = null,
    val timestamp: Long? = System.currentTimeMillis()
)

/**
 * 任务生命周期状态
 * 完整移植 koog 的 TaskState
 */
@Serializable
enum class TaskState(val terminal: Boolean) {
    @SerialName("submitted")
    Submitted(terminal = false),

    @SerialName("working")
    Working(terminal = false),

    @SerialName("input-required")
    InputRequired(terminal = false),

    @SerialName("completed")
    Completed(terminal = true),

    @SerialName("canceled")
    Canceled(terminal = true),

    @SerialName("failed")
    Failed(terminal = true),

    @SerialName("rejected")
    Rejected(terminal = true),

    @SerialName("auth-required")
    AuthRequired(terminal = false),

    @SerialName("unknown")
    Unknown(terminal = false)
}
