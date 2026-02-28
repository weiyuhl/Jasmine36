package com.lhzkml.jasmine.core.agent.a2a.server

import com.lhzkml.jasmine.core.agent.a2a.model.Task
import com.lhzkml.jasmine.core.agent.a2a.transport.ServerCallContext

/**
 * 请求上下�?
 * 完整移植 koog �?RequestContext
 *
 * 提供 Agent 执行所需的所有上下文信息�?
 *
 * @param T 请求参数类型
 * @param callContext 服务端调用上下文
 * @param params 请求参数
 * @param taskStorage 任务存储（限定在当前上下文）
 * @param messageStorage 消息存储（限定在当前上下文）
 * @param contextId 上下�?ID
 * @param taskId 任务 ID
 * @param task 关联的任务（如果存在�?
 */
data class RequestContext<T>(
    val callContext: ServerCallContext,
    val params: T,
    val taskStorage: ContextTaskStorage,
    val messageStorage: ContextMessageStorage,
    val contextId: String,
    val taskId: String,
    val task: Task? = null
)
