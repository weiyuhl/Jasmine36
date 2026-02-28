package com.lhzkml.jasmine.core.agent.a2a.server

import com.lhzkml.jasmine.core.agent.a2a.InvalidEventException
import com.lhzkml.jasmine.core.agent.a2a.SessionNotActiveException
import com.lhzkml.jasmine.core.agent.a2a.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 会话事件处理�?
 * 完整移植 koog �?SessionEventProcessor
 *
 * 负责接收 Agent 发布的事件，验证事件，持久化�?[taskStorage]，并通过 [events] 发送给订阅者�?
 *
 * 验证逻辑执行基本验证，确保事件符合正确的 A2A 服务器实现预期：
 *
 * - 会话类型排他性：一个会话只能处�?[Message] 事件�?[TaskEvent] 事件，不能同时处理两者�?
 * - 上下�?ID 验证：所有事件必须具有与提供�?[contextId] 相同的上下文 ID�?
 * - 单消息限制：每个会话只能发送一�?[Message]，之后处理器关闭�?
 * - 任务 ID 一致性：[TaskEvent] 事件的任�?ID 必须等于提供�?[taskId]�?
 * - 最终事件强制：发�?`final=true` �?[TaskStatusUpdateEvent] 后，处理器关闭�?
 * - 终态关闭：发送终态事件时，处理器关闭�?
 *
 * @param taskStorage 任务事件将被保存到的存储�?
 * @property contextId 与此会话关联的上下文 ID
 * @property taskId 与此会话关联的任�?ID
 */
class SessionEventProcessor(
    val contextId: String,
    val taskId: String,
    private val taskStorage: TaskStorage
) {
    private companion object {
        const val SESSION_CLOSED = "Session event processor is closed, can't send events"
        const val INVALID_CONTEXT_ID = "Event contextId must be same as provided contextId"
        const val INVALID_TASK_ID = "Event taskId must be same as provided taskId"
        const val TASK_EVENT_SENT =
            "Task has already been initialized in this session, only TaskEvent's with the same taskId can be sent from now on"
    }

    private val _isOpen = AtomicBoolean(true)

    /** 会话是否开�?*/
    val isOpen: Boolean get() = _isOpen.get()

    /** 跟踪此会话中是否已发送任务事件，意味着必须拒绝 [Message] 事件 */
    private var isTaskEventSent: Boolean = false

    private val sessionMutex = Mutex()

    /** 内部流事件：实际事件或终止信�?*/
    private sealed interface FlowEvent {
        @JvmInline
        value class Data(val data: Event) : FlowEvent
        object Close : FlowEvent
    }

    private val _events = MutableSharedFlow<FlowEvent>()

    /**
     * 此会话中的热事件流，可被订阅�?
     */
    val events: Flow<Event> = _events
        .onSubscription { if (!_isOpen.get()) emit(FlowEvent.Close) }
        .takeWhile { it !is FlowEvent.Close }
        .filterIsInstance<FlowEvent.Data>()
        .map { it.data }

    /**
     * 发�?[Message] 到会话事件处理器�?
     * 验证消息并更新会话状态�?
     *
     * @param message 要发送的消息
     * @throws InvalidEventException 无效事件
     * @throws SessionNotActiveException 会话已关�?
     */
    suspend fun sendMessage(message: Message): Unit = sessionMutex.withLock {
        if (_isOpen.get()) {
            if (isTaskEventSent) {
                throw InvalidEventException(TASK_EVENT_SENT)
            }

            if (message.contextId != this.contextId) {
                throw InvalidEventException(INVALID_CONTEXT_ID)
            }

            _events.emit(FlowEvent.Data(message))
            _isOpen.set(false)
        } else {
            throw SessionNotActiveException(SESSION_CLOSED)
        }
    }

    /**
     * 发�?[TaskEvent] 到会话事件处理器�?
     * 验证事件并更�?[taskStorage]�?
     *
     * @param event 要发送的事件
     * @throws InvalidEventException 无效事件
     * @throws SessionNotActiveException 会话已关�?
     */
    suspend fun sendTaskEvent(event: TaskEvent): Unit = sessionMutex.withLock {
        if (_isOpen.get()) {
            isTaskEventSent = true

            if (event.contextId != this.contextId) {
                throw InvalidEventException(INVALID_CONTEXT_ID)
            }

            if (event.taskId != this.taskId) {
                throw InvalidEventException(INVALID_TASK_ID)
            }

            taskStorage.update(event)
            _events.emit(FlowEvent.Data(event))

            val isFinalEvent = (event is TaskStatusUpdateEvent && (event.status.state.terminal || event.final)) ||
                (event is Task && event.status.state.terminal)

            if (isFinalEvent) {
                _isOpen.set(false)
            }
        } else {
            throw SessionNotActiveException(SESSION_CLOSED)
        }
    }

    /**
     * 关闭会话事件处理器，同时关闭事件流�?
     */
    suspend fun close(): Unit = sessionMutex.withLock {
        _isOpen.set(false)
        _events.emit(FlowEvent.Close)
    }
}
