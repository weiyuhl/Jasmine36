package com.lhzkml.jasmine.core.agent.tools.graph

import com.lhzkml.jasmine.core.agent.tools.ToolRegistry
import com.lhzkml.jasmine.core.agent.tools.feature.pipeline.AgentPipeline
import com.lhzkml.jasmine.core.agent.tools.trace.Tracing
import com.lhzkml.jasmine.core.prompt.llm.ChatClient
import com.lhzkml.jasmine.core.prompt.llm.LLMSession
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
 * @param session LLM 会话（累积式 prompt）
 * @param toolRegistry 工具注册表
 * @param environment Agent 环境（工具执行、问题报告）
 * @param tracing 追踪系统（可选）
 * @param pipeline Feature/Pipeline 系统（可选，移植自 koog）
 * @param storage 自定义存储（节点间共享数据）
 */
class AgentGraphContext(
    val agentId: String,
    val runId: String,
    val client: ChatClient,
    val model: String,
    val session: LLMSession,
    val toolRegistry: ToolRegistry,
    val environment: AgentEnvironment,
    val tracing: Tracing? = null,
    val pipeline: AgentPipeline? = null,
    val storage: MutableMap<String, Any?> = mutableMapOf()
) {
    /** 当前策略名称 */
    var strategyName: String = ""
        internal set

    /** 迭代计数 */
    var iterations: Int = 0
        internal set

    /** 便捷方法：从 storage 取值 */
    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? = storage[key] as? T

    /** 便捷方法：向 storage 存值 */
    fun put(key: String, value: Any?) {
        storage[key] = value
    }

    /**
     * 创建上下文的副本（用于并行节点执行等场景）
     * 移植自 koog 的 AIAgentGraphContext.fork()
     */
    fun fork(): AgentGraphContext {
        return AgentGraphContext(
            agentId = agentId,
            runId = runId,
            client = client,
            model = model,
            session = session,
            toolRegistry = toolRegistry,
            environment = environment,
            tracing = tracing,
            pipeline = pipeline,
            storage = storage.toMutableMap()
        )
    }
}
