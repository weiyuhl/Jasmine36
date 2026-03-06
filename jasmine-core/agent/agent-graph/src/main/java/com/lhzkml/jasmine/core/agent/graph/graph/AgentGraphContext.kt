package com.lhzkml.jasmine.core.agent.graph.graph

import com.lhzkml.jasmine.core.agent.tools.ToolRegistry
import com.lhzkml.jasmine.core.agent.observe.trace.Tracing
import com.lhzkml.jasmine.core.prompt.llm.ChatClient
import com.lhzkml.jasmine.core.prompt.llm.LLMReadSession
import com.lhzkml.jasmine.core.prompt.llm.LLMWriteSession

/**
 * ????????????????????
 * ??? LLM ??????????????????
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
