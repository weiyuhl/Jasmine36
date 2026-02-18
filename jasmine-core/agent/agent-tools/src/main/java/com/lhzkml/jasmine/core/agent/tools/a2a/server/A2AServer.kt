package com.lhzkml.jasmine.core.agent.tools.a2a.server

import com.lhzkml.jasmine.core.agent.tools.a2a.*
import com.lhzkml.jasmine.core.agent.tools.a2a.model.*
import com.lhzkml.jasmine.core.agent.tools.a2a.transport.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * A2A 服务器
 * 完整移植 koog 的 A2AServer
 *
 * 处理来自 A2A 客户端的请求，协调传输层、Agent 执行器和存储组件。
 * 不提供认证/授权逻辑，生产环境应扩展此类添加安全逻辑。
 *
 * @param agentExecutor Agent 执行器
 * @param agentCard Agent 名片
 * @param agentCardExtended 扩展名片（认证后返回）
 * @param taskStorage 任务存储
 * @param messageStorage 消息存储
 * @param idGenerator ID 生成器
 * @param coroutineScope 协程作用域
 */
open class A2AServer(
    protected val agentExecutor: AgentExecutor,
    protected val agentCard: AgentCard,
    protected val agentCardExtended: AgentCard? = null,
    protected val taskStorage: TaskStorage = InMemoryTaskStorage(),
    protected val messageStorage: MessageStorage = InMemoryMessageStorage(),
    protected val idGenerator: IdGenerator = UuidIdGenerator,
    protected val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob())
) : RequestHandler {

    protected open val sessionManager = SessionManager(coroutineScope)

    override suspend fun onGetAuthenticatedExtendedAgentCard(
        request: Request<Nothing?>,
        ctx: ServerCallContext
    ): Response<AgentCard> {
        if (agentCard.supportsAuthenticatedExtendedCard != true) {
            throw A2AAuthenticatedExtendedCardNotConfiguredException(
                "Extended agent card is not supported"
            )
        }
        return Response(
            data = agentCardExtended
                ?: throw A2AAuthenticatedExtendedCardNotConfiguredException(
                    "Extended agent card is supported but not configured"
                ),
            id = request.id
        )
    }

    /**
     * 消息发送的通用逻辑
     * 返回事件流
     */
    protected open fun onSendMessageCommon(
        request: Request<MessageSendParams>,
        ctx: ServerCallContext
    ): Flow<Response<Event>> = channelFlow {
        val message = request.data.message

        if (message.parts.isEmpty()) {
            throw A2AInvalidParamsException("Empty message parts are not supported")
        }

        val taskId = message.taskId ?: idGenerator.generateTaskId(message)

        // 如果有正在运行的同任务会话，等待完成
        sessionManager.getSession(taskId)?.join()

        // 检查消息是否关联已有任务
        val task: Task? = message.taskId?.let { tid ->
            taskStorage.get(tid, historyLength = 0, includeArtifacts = false)
                ?: throw A2ATaskNotFoundException("Task '$tid' not found")
        }

        // 创建事件处理器
        val eventProcessor = SessionEventProcessor(
            contextId = task?.contextId
                ?: message.contextId
                ?: idGenerator.generateContextId(message),
            taskId = taskId,
            taskStorage = taskStorage
        )

        // 创建请求上下文
        val requestContext = RequestContext(
            callContext = ctx,
            params = request.data,
            taskStorage = ContextTaskStorage(eventProcessor.contextId, taskStorage),
            messageStorage = ContextMessageStorage(eventProcessor.contextId, messageStorage),
            contextId = eventProcessor.contextId,
            taskId = eventProcessor.taskId,
            task = task
        )

        // 创建懒启动会话
        val session = LazySession(
            coroutineScope = coroutineScope,
            eventProcessor = eventProcessor
        ) {
            agentExecutor.execute(requestContext, eventProcessor)
        }

        val monitoringJob = sessionManager.addSession(session)

        // 收集事件并转发
        val eventCollectionFinished = Job()

        launch {
            session.events.collect { event ->
                send(Response(data = event, id = request.id))
            }
            eventCollectionFinished.complete()
        }

        monitoringJob.join()

        // 启动 Agent 执行并等待完成
        session.agentJob.await()
        eventCollectionFinished.join()
    }

    override suspend fun onSendMessage(
        request: Request<MessageSendParams>,
        ctx: ServerCallContext
    ): Response<CommunicationEvent> {
        val config = request.data.configuration
        val eventStream = onSendMessageCommon(request, ctx)

        val event = if (config?.blocking == true) {
            eventStream.lastOrNull()
        } else {
            eventStream.firstOrNull()
        } ?: throw IllegalStateException("Event stream is empty")

        return when (val eventData = event.data) {
            is Message -> Response(data = eventData, id = event.id)
            is TaskEvent -> {
                val task = taskStorage.get(
                    eventData.taskId,
                    historyLength = config?.historyLength,
                    includeArtifacts = true
                ) ?: throw A2ATaskNotFoundException(
                    "Task '${eventData.taskId}' not found after agent execution"
                )
                Response(data = task, id = event.id)
            }
        }
    }

    override fun onSendMessageStreaming(
        request: Request<MessageSendParams>,
        ctx: ServerCallContext
    ): Flow<Response<Event>> = flow {
        checkStreamingSupport()
        onSendMessageCommon(request, ctx).collect(this)
    }

    override suspend fun onGetTask(
        request: Request<TaskQueryParams>,
        ctx: ServerCallContext
    ): Response<Task> {
        val params = request.data
        return Response(
            data = taskStorage.get(params.id, historyLength = params.historyLength, includeArtifacts = false)
                ?: throw A2ATaskNotFoundException("Task '${params.id}' not found"),
            id = request.id
        )
    }

    override suspend fun onCancelTask(
        request: Request<TaskIdParams>,
        ctx: ServerCallContext
    ): Response<Task> {
        val taskId = request.data.id
        val session = sessionManager.getSession(taskId)

        val task = taskStorage.get(taskId, historyLength = 0, includeArtifacts = true)
            ?: throw A2ATaskNotFoundException("Task '$taskId' not found")

        if (session == null && task.status.state.terminal) {
            throw A2ATaskNotCancelableException(
                "Task '$taskId' is already in terminal state ${task.status.state}"
            )
        }

        val eventProcessor = session?.eventProcessor ?: SessionEventProcessor(
            contextId = task.contextId,
            taskId = task.id,
            taskStorage = taskStorage
        )

        val requestContext = RequestContext(
            callContext = ctx,
            params = request.data,
            taskStorage = ContextTaskStorage(eventProcessor.contextId, taskStorage),
            messageStorage = ContextMessageStorage(eventProcessor.contextId, messageStorage),
            contextId = eventProcessor.contextId,
            taskId = eventProcessor.taskId,
            task = task
        )

        agentExecutor.cancel(requestContext, eventProcessor, session?.agentJob)

        return Response(
            data = taskStorage.get(taskId, historyLength = 0, includeArtifacts = true)
                ?.also {
                    if (it.status.state != TaskState.Canceled) {
                        throw A2ATaskNotCancelableException(
                            "Task '$taskId' was not canceled successfully, current state is ${it.status.state}"
                        )
                    }
                }
                ?: throw A2ATaskNotFoundException("Task '$taskId' not found"),
            id = request.id
        )
    }

    override fun onResubscribeTask(
        request: Request<TaskIdParams>,
        ctx: ServerCallContext
    ): Flow<Response<Event>> = flow {
        checkStreamingSupport()
        val session = sessionManager.getSession(request.data.id) ?: return@flow
        session.events
            .map { event -> Response(data = event, id = request.id) }
            .collect(this)
    }

    override suspend fun onSetTaskPushNotificationConfig(
        request: Request<TaskPushNotificationConfig>,
        ctx: ServerCallContext
    ): Response<TaskPushNotificationConfig> {
        checkPushNotificationsSupported()
        // 简化实现：直接返回（完整实现需要 PushNotificationConfigStorage）
        return Response(data = request.data, id = request.id)
    }

    override suspend fun onGetTaskPushNotificationConfig(
        request: Request<TaskPushNotificationConfigParams>,
        ctx: ServerCallContext
    ): Response<TaskPushNotificationConfig> {
        checkPushNotificationsSupported()
        throw A2APushNotificationNotSupportedException(
            "Push notification config storage not configured"
        )
    }

    override suspend fun onListTaskPushNotificationConfig(
        request: Request<TaskIdParams>,
        ctx: ServerCallContext
    ): Response<List<TaskPushNotificationConfig>> {
        checkPushNotificationsSupported()
        return Response(data = emptyList(), id = request.id)
    }

    override suspend fun onDeleteTaskPushNotificationConfig(
        request: Request<TaskPushNotificationConfigParams>,
        ctx: ServerCallContext
    ): Response<Nothing?> {
        checkPushNotificationsSupported()
        return Response(data = null, id = request.id)
    }

    protected open fun checkStreamingSupport() {
        if (agentCard.capabilities.streaming != true) {
            throw A2AUnsupportedOperationException("Streaming is not supported")
        }
    }

    protected open fun checkPushNotificationsSupported() {
        if (agentCard.capabilities.pushNotifications != true) {
            throw A2APushNotificationNotSupportedException("Push notifications are not supported")
        }
    }

    /** 取消服务器及所有运行中的会话 */
    open fun cancel(cause: CancellationException? = null) {
        coroutineScope.cancel(cause)
    }
}
