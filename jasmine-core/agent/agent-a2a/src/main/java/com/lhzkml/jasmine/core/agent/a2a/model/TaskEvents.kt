package com.lhzkml.jasmine.core.agent.a2a.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * 任务状态更新事�?
 * 完整移植 koog �?TaskStatusUpdateEvent
 *
 * Agent 发送给客户端的任务状态变更通知，用于流式或订阅模式�?
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class TaskStatusUpdateEvent(
    override val taskId: String,
    override val contextId: String,
    val status: TaskStatus,
    val final: Boolean,
    val metadata: JsonObject? = null
) : TaskEvent {
    @EncodeDefault
    override val kind: String = "status-update"
}

/**
 * 任务 Artifact 更新事件
 * 完整移植 koog �?TaskArtifactUpdateEvent
 *
 * Agent 发送给客户端的 Artifact 生成/更新通知�?
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class TaskArtifactUpdateEvent(
    override val taskId: String,
    override val contextId: String,
    val artifact: Artifact,
    val append: Boolean? = null,
    val lastChunk: Boolean? = null,
    val metadata: JsonObject? = null
) : TaskEvent {
    @EncodeDefault
    override val kind: String = "artifact-update"
}
