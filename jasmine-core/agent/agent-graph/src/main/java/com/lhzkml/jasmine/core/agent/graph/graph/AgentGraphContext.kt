package com.lhzkml.jasmine.core.agent.graph.graph

import com.lhzkml.jasmine.core.agent.tools.ToolRegistry
import com.lhzkml.jasmine.core.agent.observe.trace.Tracing
import com.lhzkml.jasmine.core.prompt.llm.ChatClient
import com.lhzkml.jasmine.core.prompt.llm.LLMReadSession
import com.lhzkml.jasmine.core.prompt.llm.LLMWriteSession

/**
 * 图执行上下文，在整个策略执行周期内共享。
 * 聚合了 LLM 会话、工具注册表、追踪、存储等资源。
 */
data class AgentGraphContext(
    val agentId: String,
    val runId: String,
    val client: ChatClient,
    val model: String,
    val session: LLMWriteSession,
    val readSession: LLMReadSession,
    val toolRegistry: ToolRegistry,
    val environment: AgentEnvironment,
    val tracing: Tracing? = null,
    val storage: AgentStorage = AgentStorage()
) {
    var strategyName: String = ""
}
