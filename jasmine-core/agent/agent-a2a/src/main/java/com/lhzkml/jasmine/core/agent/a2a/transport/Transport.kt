package com.lhzkml.jasmine.core.agent.a2a.transport

import com.lhzkml.jasmine.core.agent.a2a.model.*
import kotlinx.coroutines.flow.Flow

/**
 * A2A 传输层抽�?
 * 完整移植 koog �?transport 层，适配�?Android 友好的接口�?
 *
 * koog 使用 Ktor 实现，jasmine 提供抽象接口�?
 * 具体实现可以使用 OkHttp、Ktor 或其�?HTTP 客户端�?
 */

// ========== 请求/响应包装 ==========

/**
 * A2A 请求
 * 参�?koog �?Request
 */
data class Request<T>(
    val data: T,
    val id: String? = null
)

/**
 * A2A 响应
 * 参�?koog �?Response
 */
data class Response<T>(
    val data: T,
    val id: String? = null
)

// ========== 调用上下�?==========

/**
 * 客户端调用上下文
 * 参�?koog �?ClientCallContext
 */
data class ClientCallContext(
    val headers: Map<String, List<String>> = emptyMap()
) {
    companion object {
        val Default = ClientCallContext()
    }
}

/**
 * 服务端调用上下文
 * 参�?koog �?ServerCallContext
 */
data class ServerCallContext(
    val headers: Map<String, List<String>> = emptyMap(),
    val state: Map<StateKey<*>, Any?> = emptyMap()
) {
    @Suppress("UNCHECKED_CAST")
    fun <T> getFromState(key: StateKey<T>): T =
        state[key] as? T ?: throw IllegalStateException("State key '${key.name}' not found")

    @Suppress("UNCHECKED_CAST")
    fun <T> getFromStateOrNull(key: StateKey<T>): T? =
        state[key] as? T

    companion object {
        val Default = ServerCallContext()
    }
}

/** 类型安全的状态键 */
data class StateKey<T>(val name: String)

// ========== 客户端传输接�?==========

/**
 * 客户端传输接�?
 * 完整移植 koog �?ClientTransport
 */
interface ClientTransport {
    suspend fun getAuthenticatedExtendedAgentCard(
        request: Request<Nothing?>,
        ctx: ClientCallContext
    ): Response<AgentCard>

    suspend fun sendMessage(
        request: Request<MessageSendParams>,
        ctx: ClientCallContext
    ): Response<CommunicationEvent>

    fun sendMessageStreaming(
        request: Request<MessageSendParams>,
        ctx: ClientCallContext
    ): Flow<Response<Event>>

    suspend fun getTask(
        request: Request<TaskQueryParams>,
        ctx: ClientCallContext
    ): Response<Task>

    suspend fun cancelTask(
        request: Request<TaskIdParams>,
        ctx: ClientCallContext
    ): Response<Task>

    fun resubscribeTask(
        request: Request<TaskIdParams>,
        ctx: ClientCallContext
    ): Flow<Response<Event>>

    suspend fun setTaskPushNotificationConfig(
        request: Request<TaskPushNotificationConfig>,
        ctx: ClientCallContext
    ): Response<TaskPushNotificationConfig>

    suspend fun getTaskPushNotificationConfig(
        request: Request<TaskPushNotificationConfigParams>,
        ctx: ClientCallContext
    ): Response<TaskPushNotificationConfig>

    suspend fun listTaskPushNotificationConfig(
        request: Request<TaskIdParams>,
        ctx: ClientCallContext
    ): Response<List<TaskPushNotificationConfig>>

    suspend fun deleteTaskPushNotificationConfig(
        request: Request<TaskPushNotificationConfigParams>,
        ctx: ClientCallContext
    ): Response<Nothing?>
}

// ========== 服务端请求处理接�?==========

/**
 * 服务端请求处理接�?
 * 完整移植 koog �?RequestHandler
 */
interface RequestHandler {
    suspend fun onGetAuthenticatedExtendedAgentCard(
        request: Request<Nothing?>,
        ctx: ServerCallContext
    ): Response<AgentCard>

    suspend fun onSendMessage(
        request: Request<MessageSendParams>,
        ctx: ServerCallContext
    ): Response<CommunicationEvent>

    fun onSendMessageStreaming(
        request: Request<MessageSendParams>,
        ctx: ServerCallContext
    ): Flow<Response<Event>>

    suspend fun onGetTask(
        request: Request<TaskQueryParams>,
        ctx: ServerCallContext
    ): Response<Task>

    suspend fun onCancelTask(
        request: Request<TaskIdParams>,
        ctx: ServerCallContext
    ): Response<Task>

    fun onResubscribeTask(
        request: Request<TaskIdParams>,
        ctx: ServerCallContext
    ): Flow<Response<Event>>

    suspend fun onSetTaskPushNotificationConfig(
        request: Request<TaskPushNotificationConfig>,
        ctx: ServerCallContext
    ): Response<TaskPushNotificationConfig>

    suspend fun onGetTaskPushNotificationConfig(
        request: Request<TaskPushNotificationConfigParams>,
        ctx: ServerCallContext
    ): Response<TaskPushNotificationConfig>

    suspend fun onListTaskPushNotificationConfig(
        request: Request<TaskIdParams>,
        ctx: ServerCallContext
    ): Response<List<TaskPushNotificationConfig>>

    suspend fun onDeleteTaskPushNotificationConfig(
        request: Request<TaskPushNotificationConfigParams>,
        ctx: ServerCallContext
    ): Response<Nothing?>
}
