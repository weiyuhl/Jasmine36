package com.lhzkml.jasmine.core.agent.tools.a2a.server

import com.lhzkml.jasmine.core.agent.tools.a2a.InvalidEventException
import com.lhzkml.jasmine.core.agent.tools.a2a.model.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * 会话事件处理器
 * 完整移植 koog 的 SessionEventProcessor
 *
 * 负责接收 Agent 发布的事件，持久化到 TaskStorage，并转发给订阅者。
 *
 * @param contextId 上下文 ID
 * @param taskId 任务 ID
 * @param taskStorage 任务存储
 */
class SessionEventProcessor(
    val contextId: String,
    val taskId: String,
    private val taskStorage: TaskStorage
) {
    private val eventChannel = Channel<Event>(Channel.UNLIMITED)
    private var isClosed = false

    /** 事件流（供订阅者消费） */
    val events: Flow<Event> = eventChannel.receiveAsFlow()

    /**
     * 发送任务事件
     * 自动持久化到 TaskStorage 并转发给订阅者
     */
    suspend fun sendTaskEvent(event: TaskEvent) {
        check(!isClosed) { "SessionEventProcessor is closed" }
        validateTaskEvent(event)
        taskStorage.update(event)
        eventChannel.send(event)
    }

    /**
     * 发送消息事件
     */
    suspend fun sendMessage(message: Message) {
        check(!isClosed) { "SessionEventProcessor is closed" }
        eventChannel.send(message)
    }

    /**
     * 关闭事件处理器
     */
    fun close() {
        if (!isClosed) {
            isClosed = true
            eventChannel.close()
        }
    }

    private fun validateTaskEvent(event: TaskEvent) {
        if (event.taskId != taskId) {
            throw InvalidEventException(
                "Event taskId '${event.taskId}' does not match session taskId '$taskId'"
            )
        }
        if (event.contextId != contextId) {
            throw InvalidEventException(
                "Event contextId '${event.contextId}' does not match session contextId '$contextId'"
            )
        }
    }
}
