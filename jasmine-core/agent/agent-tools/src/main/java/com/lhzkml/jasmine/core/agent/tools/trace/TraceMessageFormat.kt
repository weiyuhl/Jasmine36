package com.lhzkml.jasmine.core.agent.tools.trace

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 追踪事件格式化
 * 参考 koog 的 traceMessageFormat.kt，为每种事件类型提供人类可读的格式化输出。
 */
object TraceMessageFormat {

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    /** 格式化事件为人类可读字符串 */
    fun format(event: TraceEvent): String {
        val time = timeFormat.format(Date(event.timestamp))
        val body = when (event) {
            // Agent 生命周期
            is TraceEvent.AgentStarting ->
                "AgentStarting (agentId: ${event.agentId}, runId: ${event.runId}, model: ${event.model}, tools: ${event.toolCount})"
            is TraceEvent.AgentCompleted ->
                "AgentCompleted (agentId: ${event.agentId}, runId: ${event.runId}, iterations: ${event.totalIterations}, result: ${event.result?.take(100)})"
            is TraceEvent.AgentFailed ->
                "AgentFailed (agentId: ${event.agentId}, runId: ${event.runId}, error: ${event.error.message})"

            // LLM 调用
            is TraceEvent.LLMCallStarting ->
                "LLMCallStarting (runId: ${event.runId}, model: ${event.model}, messages: ${event.messageCount}, tools: [${event.tools.joinToString()}])"
            is TraceEvent.LLMCallCompleted ->
                "LLMCallCompleted (runId: ${event.runId}, model: ${event.model}, hasToolCalls: ${event.hasToolCalls}, toolCalls: ${event.toolCallCount}, tokens: ${event.promptTokens}/${event.completionTokens}/${event.totalTokens})"

            // LLM 流式
            is TraceEvent.LLMStreamStarting ->
                "LLMStreamStarting (runId: ${event.runId}, model: ${event.model}, messages: ${event.messageCount}, tools: [${event.tools.joinToString()}])"
            is TraceEvent.LLMStreamFrame ->
                "LLMStreamFrame (runId: ${event.runId}, chunk: ${event.chunk.take(50)})"
            is TraceEvent.LLMStreamCompleted ->
                "LLMStreamCompleted (runId: ${event.runId}, model: ${event.model}, hasToolCalls: ${event.hasToolCalls}, tokens: ${event.promptTokens}/${event.completionTokens}/${event.totalTokens})"
            is TraceEvent.LLMStreamFailed ->
                "LLMStreamFailed (runId: ${event.runId}, model: ${event.model}, error: ${event.error.message})"

            // 工具调用
            is TraceEvent.ToolCallStarting ->
                "ToolCallStarting (runId: ${event.runId}, tool: ${event.toolName}, args: ${event.toolArgs.take(100)})"
            is TraceEvent.ToolCallCompleted ->
                "ToolCallCompleted (runId: ${event.runId}, tool: ${event.toolName}, result: ${event.result?.take(100)})"
            is TraceEvent.ToolCallFailed ->
                "ToolCallFailed (runId: ${event.runId}, tool: ${event.toolName}, error: ${event.error.message})"

            // 压缩
            is TraceEvent.CompressionStarting ->
                "CompressionStarting (runId: ${event.runId}, strategy: ${event.strategyName}, messages: ${event.originalMessageCount})"
            is TraceEvent.CompressionCompleted ->
                "CompressionCompleted (runId: ${event.runId}, strategy: ${event.strategyName}, compressed: ${event.compressedMessageCount})"

            // 策略
            is TraceEvent.StrategyStarting ->
                "StrategyStarting (runId: ${event.runId}, strategy: ${event.strategyName}, nodes: ${event.graph?.nodes?.size ?: 0}, edges: ${event.graph?.edges?.size ?: 0})"
            is TraceEvent.StrategyCompleted ->
                "StrategyCompleted (runId: ${event.runId}, strategy: ${event.strategyName}, result: ${event.result?.take(100)})"

            // 节点
            is TraceEvent.NodeExecutionStarting ->
                "NodeExecutionStarting (runId: ${event.runId}, node: ${event.nodeName}, input: ${event.input?.take(100)})"
            is TraceEvent.NodeExecutionCompleted ->
                "NodeExecutionCompleted (runId: ${event.runId}, node: ${event.nodeName}, input: ${event.input?.take(100)}, output: ${event.output?.take(100)})"
            is TraceEvent.NodeExecutionFailed ->
                "NodeExecutionFailed (runId: ${event.runId}, node: ${event.nodeName}, error: ${event.error.message})"

            // 子图
            is TraceEvent.SubgraphStarting ->
                "SubgraphStarting (runId: ${event.runId}, subgraph: ${event.subgraphName}, input: ${event.input?.take(100)})"
            is TraceEvent.SubgraphCompleted ->
                "SubgraphCompleted (runId: ${event.runId}, subgraph: ${event.subgraphName}, result: ${event.result?.take(100)})"
            is TraceEvent.SubgraphFailed ->
                "SubgraphFailed (runId: ${event.runId}, subgraph: ${event.subgraphName}, error: ${event.error.message})"
        }
        return "[$time] $body"
    }
}
