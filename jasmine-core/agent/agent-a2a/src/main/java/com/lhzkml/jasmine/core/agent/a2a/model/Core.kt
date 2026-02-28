package com.lhzkml.jasmine.core.agent.a2a.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A2A 协议核心接口
 * 完整移植 koog �?a2a-core/model/Core.kt
 */

/**
 * 所有事件的基接�?
 */
sealed interface Event {
    /** 事件类型鉴别�?*/
    val kind: String
}

/**
 * 通信事件基接口（消息或任务）
 */
sealed interface CommunicationEvent : Event

/**
 * 任务事件基接�?
 */
sealed interface TaskEvent : Event {
    /** 关联的任�?ID */
    val taskId: String
    /** 关联的上下文 ID */
    val contextId: String
}
