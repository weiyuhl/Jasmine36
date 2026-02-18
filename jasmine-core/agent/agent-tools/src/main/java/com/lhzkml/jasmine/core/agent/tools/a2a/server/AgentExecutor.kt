package com.lhzkml.jasmine.core.agent.tools.a2a.server

import com.lhzkml.jasmine.core.agent.tools.a2a.A2ATaskNotCancelableException
import com.lhzkml.jasmine.core.agent.tools.a2a.model.MessageSendParams
import com.lhzkml.jasmine.core.agent.tools.a2a.model.TaskIdParams
import kotlinx.coroutines.Deferred

/**
 * Agent 执行器接口
 * 完整移植 koog 的 AgentExecutor
 *
 * 包含 Agent 的核心逻辑，根据请求执行操作并发布事件。
 */
interface AgentExecutor {
    /**
     * 执行 Agent 逻辑
     *
     * Agent 应从 [context] 读取必要信息，并通过 [eventProcessor] 发布 TaskEvent 或 Message。
     * 方法在 Agent 执行完成或让出控制时返回。
     *
     * @param context 请求上下文
     * @param eventProcessor 事件处理器
     */
    suspend fun execute(
        context: RequestContext<MessageSendParams>,
        eventProcessor: SessionEventProcessor
    )

    /**
     * 取消任务
     *
     * 默认实现抛出 A2ATaskNotCancelableException。
     * 子类可以覆盖此方法实现取消逻辑。
     *
     * @param context 请求上下文
     * @param eventProcessor 事件处理器
     * @param agentJob 正在执行的 Agent 任务（如果有）
     */
    suspend fun cancel(
        context: RequestContext<TaskIdParams>,
        eventProcessor: SessionEventProcessor,
        agentJob: Deferred<Unit>?
    ) {
        throw A2ATaskNotCancelableException("Cancellation is not supported")
    }
}
