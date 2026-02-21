package com.lhzkml.jasmine.core.agent.tools.graph

import com.lhzkml.jasmine.core.agent.tools.ToolRegistry
import com.lhzkml.jasmine.core.agent.tools.feature.pipeline.AgentPipeline
import com.lhzkml.jasmine.core.agent.tools.trace.Tracing
import com.lhzkml.jasmine.core.prompt.llm.ChatClient
import com.lhzkml.jasmine.core.prompt.llm.LLMReadSession
import com.lhzkml.jasmine.core.prompt.llm.LLMWriteSession
import com.lhzkml.jasmine.core.prompt.model.Prompt

/**
 * Agent 图执行上下文
 * 移植自 koog 的 AIAgentGraphContext / AIAgentContext，
 * 提供节点执行时需要的所有资源。
 *
 * @param agentId Agent 唯一标识
 * @param runId 本次运行的唯一标识
 * @param client LLM 客户端
 * @param model 模型名称
 * @param session LLM 可写会话（累积式 prompt）
 * @param readSession LLM 只读会话（不修改 prompt，移植自 koog 的 ReadSession）
 * @param toolRegistry 工具注册表
 * @param environment Agent 环境（工具执行、问题报告）
 * @param tracing 追踪系统（可选）
 * @param pipeline Feature/Pipeline 系统（可选，移植自 koog）
 * @param storage 并发安全的类型化存储（节点间共享数据，移植自 koog 的 AIAgentStorage）
 * @param executionInfo 执行路径信息（移植自 koog 的 AgentExecutionInfo）
 */
class AgentGraphContext(
    val agentId: String,
    val runId: String,
    val client: ChatClient,
    val model: String,
    val session: LLMWriteSession,
    val readSession: LLMReadSession,
    val toolRegistry: ToolRegistry,
    val environment: AgentEnvironment,
    val tracing: Tracing? = null,
    val pipeline: AgentPipeline? = null,
    val storage: AgentStorage = AgentStorage(),
    val executionInfo: AgentExecutionInfo = AgentExecutionInfo(null, "")
) {
    /** 可变的执行路径信息（用于 with 扩展函数临时切换） */
    var currentExecutionInfo: AgentExecutionInfo = executionInfo
    /** 当前策略名称 */
    var strategyName: String = ""
        internal set

    /** 迭代计数 */
    var iterations: Int = 0
        internal set

    /**
     * 便捷方法：从 storage 取值（suspend 版本，类型化 key）
     * 移植自 koog 的 AIAgentStorage.get
     */
    suspend fun <T : Any> get(key: AgentStorageKey<T>): T? = storage.get(key)

    /**
     * 便捷方法：向 storage 存值（suspend 版本，类型化 key）
     * 移植自 koog 的 AIAgentStorage.set
     */
    suspend fun <T : Any> put(key: AgentStorageKey<T>, value: T) = storage.set(key, value)

    /**
     * 创建上下文的副本（用于并行节点执行等场景）
     * 移植自 koog 的 AIAgentGraphContext.fork()
     */
    suspend fun fork(): AgentGraphContext {
        return AgentGraphContext(
            agentId = agentId,
            runId = runId,
            client = client,
            model = model,
            session = session,
            readSession = readSession,
            toolRegistry = toolRegistry,
            environment = environment,
            tracing = tracing,
            pipeline = pipeline,
            storage = storage.copy(),
            executionInfo = executionInfo.copy()
        )
    }
}

/**
 * 在指定的执行信息下执行代码块
 * 移植自 koog 的 AIAgentContext.with(executionInfo, block)
 *
 * 执行完毕后自动恢复原始的 executionInfo。
 *
 * @param executionInfo 临时设置的执行信息
 * @param block 要执行的代码块，接收 executionInfo 和 eventId
 * @return 代码块的返回值
 */
inline fun <T> AgentGraphContext.with(
    executionInfo: AgentExecutionInfo,
    block: (executionInfo: AgentExecutionInfo, eventId: String) -> T
): T {
    val originalExecutionInfo = this.currentExecutionInfo
    val eventId = java.util.UUID.randomUUID().toString()

    return try {
        this.currentExecutionInfo = executionInfo
        block(executionInfo, eventId)
    } finally {
        this.currentExecutionInfo = originalExecutionInfo
    }
}

/**
 * 在指定的 partName 下执行代码块，自动创建父子层级关系
 * 移植自 koog 的 AIAgentContext.with(partName, block)
 *
 * @param partName 执行部分名称，追加到当前执行路径
 * @param block 要执行的代码块，接收 executionInfo 和 eventId
 * @return 代码块的返回值
 */
inline fun <T> AgentGraphContext.with(
    partName: String,
    block: (executionInfo: AgentExecutionInfo, eventId: String) -> T
): T {
    val executionInfo = AgentExecutionInfo(parent = this.currentExecutionInfo, partName = partName)
    return with(executionInfo = executionInfo, block = block)
}
