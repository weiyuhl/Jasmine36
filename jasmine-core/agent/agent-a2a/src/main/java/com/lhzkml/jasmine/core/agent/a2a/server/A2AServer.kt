package com.lhzkml.jasmine.core.agent.a2a.server

import com.lhzkml.jasmine.core.agent.a2a.*
import com.lhzkml.jasmine.core.agent.a2a.model.*
import com.lhzkml.jasmine.core.agent.a2a.server.notifications.PushNotificationConfigStorage
import com.lhzkml.jasmine.core.agent.a2a.server.notifications.PushNotificationSender
import com.lhzkml.jasmine.core.agent.a2a.transport.*
import com.lhzkml.jasmine.core.agent.a2a.utils.KeyedMutex
import com.lhzkml.jasmine.core.agent.a2a.utils.withLock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * A2A 服务�?
 * 完整移植 koog �?A2AServer
 *
 * 处理来自 A2A 客户端的请求，协调传输层、Agent 执行器和存储组件�?
 * 不提供认�?授权逻辑，生产环境应扩展此类添加安全逻辑�?
 *
 * @param agentExecutor Agent 执行�?
 * @param agentCard Agent 名片
 * @param agentCardExtended 扩展名片（认证后返回�?
 * @param taskStorage 任务存储
 * @param messageStorage 消息存储
 * @param pushConfigStorage 推送通知配置存储
 * @param pushSender 推送通知发送器
 * @param idGenerator ID 生成�?
 * @param coroutineScope 协程作用�?
 */
open class A2AServer(
    protected val agentExecutor: AgentExecutor,
    protected val agentCard: AgentCard,
    protected val agentCardExtended: AgentCard? = null,
    protected val taskStorage: TaskStorage = InMemoryTaskStorage(),
    protected val messageStorage: MessageStorage = InMemoryMessageStorage(),
    protected val pushConfigStorage: PushNotificationConfigStorage? = null,
    protected val pushSender: PushNotificationSender? = null,
    protected val idGenerator: IdGenerator = UuidIdGenerator,
    protected val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob())
) : RequestHandler {

    /** 用于按任�?ID 锁定特定任务的互斥锁 */
    protected val tasksMutex: KeyedMutex<String> = KeyedMutex()

    /** 取消操作的特殊锁�?*/
    protected fun cancelKey(taskId: String): String = "cancel:$taskId"

    protected open val sessionManager: SessionManager = SessionManager(
        coroutineScope = coroutineScope,
        cancelKey = ::cancelKey,
        tasksMutex = tasksMutex,
        taskStorage = taskStorage,
        pushConfigStorage = pushConfigStorage,
        pushSender = pushSender
    )

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
                    "Extended agent card is supported but not configured on the server"
                ),
            id = request.id
        )
    }

    /**
     * 消息发送的通用逻辑
     * 完成所有设置和验证，创建事件流�?
     *
     * @return Agent 的事件流
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

        val (session, monitoringStarted) = tasksMutex.withLock(taskId) {
            // 如果同一任务有正在运行的会话，等待其完成
            sessionManager.getSession(taskId)?.join()

            // 检查消息是否关联已有任�?
            val task: Task? = message.taskId?.let { tid ->
                taskStorage.get(tid, historyLength = 0, includeArtifacts = false)
                    ?: throw A2ATaskNotFoundException("Task '$tid' not found")
            }

            // 创建事件处理�?
            val eventProcessor = SessionEventProcessor(
                contextId = task?.contextId
                    ?: message.contextId
                    ?: idGenerator.generateContextId(message),
                taskId = taskId,
                taskStorage = taskStorage
            )

            // 创建请求上下�?
            val requestContext = RequestContext(
                callContext = ctx,
                params = request.data,
                taskStorage = ContextTaskStorage(eventProcessor.contextId, taskStorage),
                messageStorage = ContextMessageStorage(eventProcessor.contextId, messageStorage),
                contextId = eventProcessor.contextId,
                taskId = eventProcessor.taskId,
                task = task
            )

            LazySession(
                coroutineScope = coroutineScope,
                eventProcessor = eventProcessor
            ) {
                agentExecutor.execute(requestContext, eventProcessor)
            }.let {
                it to sessionManager.addSession(it)
            }
        }

        // 事件收集已启动的信号
        val eventCollectionStarted: CompletableJob = Job()
        // 所有事件已收集的信�?
        val eventCollectionFinished: CompletableJob = Job()

        // 订阅事件流并开始发�?
        launch {
            session.events
                .onStart {
                    eventCollectionStarted.complete()
                }
                .collect { event ->
                    send(Response(data = event, id = request.id))
                }
            eventCollectionFinished.complete()
        }

        // 确保事件收集已设置好以流式传输响应中的事�?
        eventCollectionStarted.join()
        // 确保监控已准备好监控会话
        monitoringStarted.join()

        /*
         启动会话以执�?agent 并等待其完成�?
         使用 await 以传�?agent 执行抛出的任何异常�?
         */
        session.agentJob.await()
        // 确保所有事件已收集并发�?
        eventCollectionFinished.join()
    }

    override suspend fun onSendMessage(
        request: Request<MessageSendParams>,
        ctx: ServerCallContext
    ): Response<CommunicationEvent> {
        val messageConfiguration = request.data.configuration
        val eventStream = onSendMessageCommon(request, ctx)

        val event = if (messageConfiguration?.blocking == true) {
            eventStream.lastOrNull()
        } else {
            eventStream.firstOrNull()
        } ?: throw IllegalStateException("Can't get response from the agent: event stream is empty")

        return when (val eventData = event.data) {
            is Message -> Response(data = eventData, id = event.id)
            is TaskEvent ->
                taskStorage
                    .get(
                        eventData.taskId,
                        historyLength = messageConfiguration?.historyLength,
                        includeArtifacts = true
                    )
                    ?.let { Response(data = it, id = event.id) }
                    ?: throw A2ATaskNotFoundException(
                        "Task '${eventData.taskId}' not found after the agent execution"
                    )
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
        val taskParams = request.data
        return Response(
            data = taskStorage.get(taskParams.id, historyLength = taskParams.historyLength, includeArtifacts = false)
                ?: throw A2ATaskNotFoundException("Task '${taskParams.id}' not found"),
            id = request.id
        )
    }

    override suspend fun onCancelTask(
        request: Request<TaskIdParams>,
        ctx: ServerCallContext
    ): Response<Task> {
        val taskParams = request.data
        val taskId = taskParams.id

        /*
         取消使用两级锁。第一级是标准任务锁�?
         如果已被其他请求持有，忽略它，因为取消优先�?
         如果未被持有，获取它以在取消进行时阻止新请求�?
         */
        val lockAcquired = tasksMutex.tryLock(taskId)

        return try {
            /*
             第二级是每任务取消锁�?
             取消时始终获取，以序列化取消操作并允许它们在常规任务锁被持有时继续�?
             它防止重叠取消并延迟会话拆除，使事件处理器不会在 agent job 取消后立即关闭�?
             这允许取消处理器通过同一处理器和会话发出额外的取消事件，
             确保现有订阅者接收所有事件�?
             */
            tasksMutex.withLock(cancelKey(taskId)) {
                val session = sessionManager.getSession(taskParams.id)

                val task = taskStorage.get(taskParams.id, historyLength = 0, includeArtifacts = true)
                    ?: throw A2ATaskNotFoundException("Task '${taskParams.id}' not found")

                // 任务未运行，检查是否已在终�?
                if (session == null && task.status.state.terminal) {
                    throw A2ATaskNotCancelableException(
                        "Task '${taskParams.id}' is already in terminal state ${task.status.state}"
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

                // 尝试取消 agent 执行并等待完�?
                agentExecutor.cancel(requestContext, eventProcessor, session?.agentJob)

                // 返回最终任务状�?
                Response(
                    data = taskStorage.get(taskParams.id, historyLength = 0, includeArtifacts = true)
                        ?.also {
                            if (it.status.state != TaskState.Canceled) {
                                throw A2ATaskNotCancelableException(
                                    "Task '${taskParams.id}' was not canceled successfully, current state is ${it.status.state}"
                                )
                            }
                        }
                        ?: throw A2ATaskNotFoundException("Task '${taskParams.id}' not found"),
                    id = request.id
                )
            }
        } finally {
            if (lockAcquired) {
                tasksMutex.unlock(taskId)
            }
        }
    }

    override fun onResubscribeTask(
        request: Request<TaskIdParams>,
        ctx: ServerCallContext
    ): Flow<Response<Event>> = flow {
        checkStreamingSupport()
        val taskParams = request.data
        val session = sessionManager.getSession(taskParams.id) ?: return@flow
        session.events
            .map { event -> Response(data = event, id = request.id) }
            .collect(this)
    }

    override suspend fun onSetTaskPushNotificationConfig(
        request: Request<TaskPushNotificationConfig>,
        ctx: ServerCallContext
    ): Response<TaskPushNotificationConfig> {
        val pushStorage = storageIfPushNotificationSupported()
        val taskPushConfig = request.data
        pushStorage.save(taskPushConfig.taskId, taskPushConfig.pushNotificationConfig)
        return Response(data = taskPushConfig, id = request.id)
    }

    override suspend fun onGetTaskPushNotificationConfig(
        request: Request<TaskPushNotificationConfigParams>,
        ctx: ServerCallContext
    ): Response<TaskPushNotificationConfig> {
        val pushStorage = storageIfPushNotificationSupported()
        val pushConfigParams = request.data
        val pushConfig = pushStorage.get(pushConfigParams.id, pushConfigParams.pushNotificationConfigId)
            ?: throw NoSuchElementException(
                "Can't find push notification config with id '${pushConfigParams.pushNotificationConfigId}' " +
                    "for task '${pushConfigParams.id}'"
            )
        return Response(
            data = TaskPushNotificationConfig(
                taskId = pushConfigParams.id,
                pushNotificationConfig = pushConfig
            ),
            id = request.id
        )
    }

    override suspend fun onListTaskPushNotificationConfig(
        request: Request<TaskIdParams>,
        ctx: ServerCallContext
    ): Response<List<TaskPushNotificationConfig>> {
        val pushStorage = storageIfPushNotificationSupported()
        val taskParams = request.data
        return Response(
            data = pushStorage
                .getAll(taskParams.id)
                .map { TaskPushNotificationConfig(taskId = taskParams.id, pushNotificationConfig = it) },
            id = request.id
        )
    }

    override suspend fun onDeleteTaskPushNotificationConfig(
        request: Request<TaskPushNotificationConfigParams>,
        ctx: ServerCallContext
    ): Response<Nothing?> {
        val pushStorage = storageIfPushNotificationSupported()
        val taskPushConfigParams = request.data
        pushStorage.delete(taskPushConfigParams.id, taskPushConfigParams.pushNotificationConfigId)
        return Response(data = null, id = request.id)
    }

    protected open fun checkStreamingSupport() {
        if (agentCard.capabilities.streaming != true) {
            throw A2AUnsupportedOperationException("Streaming is not supported by the server")
        }
    }

    protected open fun storageIfPushNotificationSupported(): PushNotificationConfigStorage {
        if (agentCard.capabilities.pushNotifications != true) {
            throw A2APushNotificationNotSupportedException(
                "Push notifications are not supported by the server"
            )
        }
        if (pushConfigStorage == null) {
            throw A2APushNotificationNotSupportedException(
                "Push notifications are supported, but not configured on the server"
            )
        }
        return pushConfigStorage
    }

    /** 取消服务器及所有运行中的会�?*/
    open fun cancel(cause: CancellationException? = null) {
        coroutineScope.cancel(cause)
    }
}
