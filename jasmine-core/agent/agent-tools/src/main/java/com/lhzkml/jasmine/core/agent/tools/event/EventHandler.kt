package com.lhzkml.jasmine.core.agent.tools.event

import com.lhzkml.jasmine.core.agent.tools.trace.TraceEvent

/**
 * Agent 事件处理器
 * 完整移植 koog 的 EventHandler feature，提供对 Agent 生命周期各阶段的回调注册。
 *
 * koog 通过 Feature/Pipeline 插件架构实现，jasmine 简化为直接回调注册，
 * 但保留了 koog 的所有事件类型和链式注册能力。
 *
 * 使用方式：
 * ```kotlin
 * val handler = EventHandler.build {
 *     onAgentStarting { ctx -> println("Agent started: ${ctx.agentId}") }
 *     onToolCallStarting { ctx -> println("Tool: ${ctx.toolName}") }
 *     onLLMCallCompleted { ctx -> println("LLM done, tokens: ${ctx.totalTokens}") }
 * }
 * ```
 */
class EventHandler private constructor(
    private val config: EventHandlerConfig
) {
    /** 触发 Agent 开始事件 */
    suspend fun fireAgentStarting(context: AgentStartingContext) =
        config.invokeOnAgentStarting(context)

    /** 触发 Agent 完成事件 */
    suspend fun fireAgentCompleted(context: AgentCompletedContext) =
        config.invokeOnAgentCompleted(context)

    /** 触发 Agent 失败事件 */
    suspend fun fireAgentExecutionFailed(context: AgentExecutionFailedContext) =
        config.invokeOnAgentExecutionFailed(context)

    /** 触发 Agent 关闭事件 */
    suspend fun fireAgentClosing(context: AgentClosingContext) =
        config.invokeOnAgentClosing(context)

    /** 触发策略开始事件 */
    suspend fun fireStrategyStarting(context: StrategyStartingContext) =
        config.invokeOnStrategyStarting(context)

    /** 触发策略完成事件 */
    suspend fun fireStrategyCompleted(context: StrategyCompletedContext) =
        config.invokeOnStrategyCompleted(context)

    /** 触发节点执行开始事件 */
    suspend fun fireNodeExecutionStarting(context: NodeExecutionStartingContext) =
        config.invokeOnNodeExecutionStarting(context)

    /** 触发节点执行完成事件 */
    suspend fun fireNodeExecutionCompleted(context: NodeExecutionCompletedContext) =
        config.invokeOnNodeExecutionCompleted(context)

    /** 触发节点执行失败事件 */
    suspend fun fireNodeExecutionFailed(context: NodeExecutionFailedContext) =
        config.invokeOnNodeExecutionFailed(context)

    /** 触发子图执行开始事件 */
    suspend fun fireSubgraphExecutionStarting(context: SubgraphExecutionStartingContext) =
        config.invokeOnSubgraphExecutionStarting(context)

    /** 触发子图执行完成事件 */
    suspend fun fireSubgraphExecutionCompleted(context: SubgraphExecutionCompletedContext) =
        config.invokeOnSubgraphExecutionCompleted(context)

    /** 触发子图执行失败事件 */
    suspend fun fireSubgraphExecutionFailed(context: SubgraphExecutionFailedContext) =
        config.invokeOnSubgraphExecutionFailed(context)

    /** 触发 LLM 调用开始事件 */
    suspend fun fireLLMCallStarting(context: LLMCallStartingContext) =
        config.invokeOnLLMCallStarting(context)

    /** 触发 LLM 调用完成事件 */
    suspend fun fireLLMCallCompleted(context: LLMCallCompletedContext) =
        config.invokeOnLLMCallCompleted(context)

    /** 触发工具调用开始事件 */
    suspend fun fireToolCallStarting(context: ToolCallStartingContext) =
        config.invokeOnToolCallStarting(context)

    /** 触发工具验证失败事件 */
    suspend fun fireToolValidationFailed(context: ToolValidationFailedContext) =
        config.invokeOnToolValidationFailed(context)

    /** 触发工具调用失败事件 */
    suspend fun fireToolCallFailed(context: ToolCallFailedContext) =
        config.invokeOnToolCallFailed(context)

    /** 触发工具调用完成事件 */
    suspend fun fireToolCallCompleted(context: ToolCallCompletedContext) =
        config.invokeOnToolCallCompleted(context)

    /** 触发 LLM 流式开始事件 */
    suspend fun fireLLMStreamingStarting(context: LLMStreamingStartingContext) =
        config.invokeOnLLMStreamingStarting(context)

    /** 触发 LLM 流式帧事件 */
    suspend fun fireLLMStreamingFrameReceived(context: LLMStreamingFrameReceivedContext) =
        config.invokeOnLLMStreamingFrameReceived(context)

    /** 触发 LLM 流式失败事件 */
    suspend fun fireLLMStreamingFailed(context: LLMStreamingFailedContext) =
        config.invokeOnLLMStreamingFailed(context)

    /** 触发 LLM 流式完成事件 */
    suspend fun fireLLMStreamingCompleted(context: LLMStreamingCompletedContext) =
        config.invokeOnLLMStreamingCompleted(context)

    companion object {
        /** 构建 EventHandler */
        fun build(configure: EventHandlerConfig.() -> Unit): EventHandler {
            val config = EventHandlerConfig().apply(configure)
            return EventHandler(config)
        }

        /** 空 EventHandler（不做任何事） */
        val NOOP = EventHandler(EventHandlerConfig())
    }
}
