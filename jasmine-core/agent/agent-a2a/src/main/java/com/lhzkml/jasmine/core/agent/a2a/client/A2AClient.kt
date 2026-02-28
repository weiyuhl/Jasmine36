package com.lhzkml.jasmine.core.agent.a2a.client

import com.lhzkml.jasmine.core.agent.a2a.A2AException
import com.lhzkml.jasmine.core.agent.a2a.model.*
import com.lhzkml.jasmine.core.agent.a2a.transport.*
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.atomic.AtomicReference

/**
 * A2A 客户�?
 * 完整移植 koog �?A2AClient，负责向 A2A 服务器发送请求�?
 *
 * @param transport 客户端传输层实现
 * @param agentCardResolver Agent 名片解析�?
 */
open class A2AClient(
    private val transport: ClientTransport,
    private val agentCardResolver: AgentCardResolver
) {
    protected var agentCard: AtomicReference<AgentCard?> = AtomicReference(null)

    /** 初始化：获取 AgentCard */
    open suspend fun connect() {
        getAgentCard()
    }

    /** 获取 AgentCard 并缓�?*/
    open suspend fun getAgentCard(): AgentCard {
        return agentCardResolver.resolve().also {
            agentCard.set(it)
        }
    }

    /** 获取缓存�?AgentCard */
    open fun cachedAgentCard(): AgentCard {
        return agentCard.get()
            ?: throw IllegalStateException("Agent card is not initialized.")
    }

    /**
     * 获取认证扩展名片
     * 对应 agent/getAuthenticatedExtendedCard
     */
    suspend fun getAuthenticatedExtendedAgentCard(
        request: Request<Nothing?>,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Response<AgentCard> {
        check(cachedAgentCard().supportsAuthenticatedExtendedCard == true) {
            "Agent card reports that authenticated extended agent card is not supported."
        }
        return transport.getAuthenticatedExtendedAgentCard(request, ctx).also {
            agentCard.set(it.data)
        }
    }

    /**
     * 发送消�?
     * 对应 message/send
     */
    suspend fun sendMessage(
        request: Request<MessageSendParams>,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Response<CommunicationEvent> {
        return transport.sendMessage(request, ctx)
    }

    /**
     * 流式发送消�?
     * 对应 message/stream
     */
    fun sendMessageStreaming(
        request: Request<MessageSendParams>,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Flow<Response<Event>> {
        check(cachedAgentCard().capabilities.streaming == true) {
            "Agent card reports that streaming is not supported."
        }
        return transport.sendMessageStreaming(request, ctx)
    }

    /**
     * 获取任务
     * 对应 tasks/get
     */
    suspend fun getTask(
        request: Request<TaskQueryParams>,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Response<Task> {
        return transport.getTask(request, ctx)
    }

    /**
     * 取消任务
     * 对应 tasks/cancel
     */
    suspend fun cancelTask(
        request: Request<TaskIdParams>,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Response<Task> {
        return transport.cancelTask(request, ctx)
    }

    /**
     * 重新订阅任务事件
     * 对应 tasks/resubscribe
     */
    fun resubscribeTask(
        request: Request<TaskIdParams>,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Flow<Response<Event>> {
        return transport.resubscribeTask(request, ctx)
    }

    /**
     * 设置推送通知配置
     * 对应 tasks/pushNotificationConfig/set
     */
    suspend fun setTaskPushNotificationConfig(
        request: Request<TaskPushNotificationConfig>,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Response<TaskPushNotificationConfig> {
        checkPushNotificationsSupported()
        return transport.setTaskPushNotificationConfig(request, ctx)
    }

    /**
     * 获取推送通知配置
     * 对应 tasks/pushNotificationConfig/get
     */
    suspend fun getTaskPushNotificationConfig(
        request: Request<TaskPushNotificationConfigParams>,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Response<TaskPushNotificationConfig> {
        checkPushNotificationsSupported()
        return transport.getTaskPushNotificationConfig(request, ctx)
    }

    /**
     * 列出推送通知配置
     * 对应 tasks/pushNotificationConfig/list
     */
    suspend fun listTaskPushNotificationConfig(
        request: Request<TaskIdParams>,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Response<List<TaskPushNotificationConfig>> {
        checkPushNotificationsSupported()
        return transport.listTaskPushNotificationConfig(request, ctx)
    }

    /**
     * 删除推送通知配置
     * 对应 tasks/pushNotificationConfig/delete
     */
    suspend fun deleteTaskPushNotificationConfig(
        request: Request<TaskPushNotificationConfigParams>,
        ctx: ClientCallContext = ClientCallContext.Default
    ): Response<Nothing?> {
        checkPushNotificationsSupported()
        return transport.deleteTaskPushNotificationConfig(request, ctx)
    }

    protected fun checkPushNotificationsSupported() {
        check(cachedAgentCard().capabilities.pushNotifications == true) {
            "Agent card reports that push notifications are not supported."
        }
    }
}
