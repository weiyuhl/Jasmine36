package com.lhzkml.jasmine.core.agent.graph.graph

import com.lhzkml.jasmine.core.agent.tools.ToolRegistry

/**
 * Agent 运行环境，提供 Agent 身份和可用工具信息。
 */
interface AgentEnvironment {
    val agentId: String
    val toolRegistry: ToolRegistry
}

/**
 * 通用 Agent 环境实现。
 */
class GenericAgentEnvironment(
    override val agentId: String,
    override val toolRegistry: ToolRegistry
) : AgentEnvironment
