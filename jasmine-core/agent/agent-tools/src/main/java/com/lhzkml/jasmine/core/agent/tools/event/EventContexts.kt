package com.lhzkml.jasmine.core.agent.tools.event

import com.lhzkml.jasmine.core.agent.tools.graph.StrategyGraph

/**
 * 事件上下文类
 * 完整移植 koog 的各种 EventContext，为每种事件提供结构化的上下文信息。
 *
 * koog 中这些分散在 feature/handler 的多个子包中，
 * jasmine 统一放在一个文件中。
 */

// ========== Agent 生命周期 ==========

data class AgentStartingContext(
    val runId: String,
    val agentId: String,
    val model: String,
    val toolCount: Int
)

data class AgentCompletedContext(
    val runId: String,
    val agentId: String,
    val result: String?,
    val totalIterations: Int
)

data class AgentExecutionFailedContext(
    val runId: String,
    val agentId: String,
    val throwable: Throwable
)

data class AgentClosingContext(
    val runId: String,
    val agentId: String
)

// ========== 策略 ==========

data class StrategyStartingContext(
    val runId: String,
    val strategyName: String,
    val graph: StrategyGraph? = null
)

data class StrategyCompletedContext(
    val runId: String,
    val strategyName: String,
    val result: String?
)

// ========== 节点 ==========

data class NodeExecutionStartingContext(
    val runId: String,
    val nodeName: String,
    val input: String?
)

data class NodeExecutionCompletedContext(
    val runId: String,
    val nodeName: String,
    val input: String?,
    val output: String?
)

data class NodeExecutionFailedContext(
    val runId: String,
    val nodeName: String,
    val input: String?,
    val throwable: Throwable
)

// ========== 子图 ==========

data class SubgraphExecutionStartingContext(
    val runId: String,
    val subgraphName: String,
    val input: String?
)

data class SubgraphExecutionCompletedContext(
    val runId: String,
    val subgraphName: String,
    val result: String?
)

data class SubgraphExecutionFailedContext(
    val runId: String,
    val subgraphName: String,
    val throwable: Throwable
)

// ========== LLM 调用 ==========

data class LLMCallStartingContext(
    val runId: String,
    val model: String,
    val messageCount: Int,
    val tools: List<String>
)

data class LLMCallCompletedContext(
    val runId: String,
    val model: String,
    val responsePreview: String?,
    val hasToolCalls: Boolean,
    val toolCallCount: Int,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

// ========== 工具调用 ==========

data class ToolCallStartingContext(
    val runId: String,
    val toolCallId: String?,
    val toolName: String,
    val toolArgs: String
)

data class ToolValidationFailedContext(
    val runId: String,
    val toolName: String,
    val validationError: String
)

data class ToolCallFailedContext(
    val runId: String,
    val toolCallId: String?,
    val toolName: String,
    val toolArgs: String,
    val throwable: Throwable
)

data class ToolCallCompletedContext(
    val runId: String,
    val toolCallId: String?,
    val toolName: String,
    val toolArgs: String,
    val result: String?
)

// ========== LLM 流式 ==========

data class LLMStreamingStartingContext(
    val runId: String,
    val model: String,
    val messageCount: Int,
    val tools: List<String>
)

data class LLMStreamingFrameReceivedContext(
    val runId: String,
    val chunk: String
)

data class LLMStreamingFailedContext(
    val runId: String,
    val model: String,
    val throwable: Throwable
)

data class LLMStreamingCompletedContext(
    val runId: String,
    val model: String,
    val responsePreview: String?,
    val hasToolCalls: Boolean,
    val toolCallCount: Int,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)
