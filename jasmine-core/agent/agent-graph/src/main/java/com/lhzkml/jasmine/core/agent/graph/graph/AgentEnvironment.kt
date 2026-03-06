package com.lhzkml.jasmine.core.agent.graph.graph

import com.lhzkml.jasmine.core.agent.tools.ToolRegistry

/**
 * Agent ??????? Agent ??????????
 */
interface AgentEnvironment {
    val agentId: String
    val toolRegistry: ToolRegistry
}

/**
 * ?? Agent ?????
 */
class GenericAgentEnvironment(
    override val agentId: String,
    override val toolRegistry: ToolRegistry
) : AgentEnvironment
