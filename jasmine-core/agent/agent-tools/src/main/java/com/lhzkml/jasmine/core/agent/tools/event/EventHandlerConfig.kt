package com.lhzkml.jasmine.core.agent.tools.event

/**
 * EventHandler 配置类
 * 完整移植 koog 的 EventHandlerConfig，支持所有事件类型的回调注册。
 *
 * 每个 on* 方法支持链式追加（多次调用会依次执行所有注册的回调）。
 * 参考 koog 的实现：每次注册都包装原有 handler，形成调用链。
 */
class EventHandlerConfig {

    // ========== Agent 生命周期 ==========

    private var _onAgentStarting: suspend (AgentStartingContext) -> Unit = {}
    private var _onAgentCompleted: suspend (AgentCompletedContext) -> Unit = {}
    private var _onAgentExecutionFailed: suspend (AgentExecutionFailedContext) -> Unit = {}
    private var _onAgentClosing: suspend (AgentClosingContext) -> Unit = {}

    fun onAgentStarting(handler: suspend (AgentStartingContext) -> Unit) {
        val original = _onAgentStarting
        _onAgentStarting = { original(it); handler(it) }
    }

    fun onAgentCompleted(handler: suspend (AgentCompletedContext) -> Unit) {
        val original = _onAgentCompleted
        _onAgentCompleted = { original(it); handler(it) }
    }

    fun onAgentExecutionFailed(handler: suspend (AgentExecutionFailedContext) -> Unit) {
        val original = _onAgentExecutionFailed
        _onAgentExecutionFailed = { original(it); handler(it) }
    }

    fun onAgentClosing(handler: suspend (AgentClosingContext) -> Unit) {
        val original = _onAgentClosing
        _onAgentClosing = { original(it); handler(it) }
    }

    // ========== 策略 ==========

    private var _onStrategyStarting: suspend (StrategyStartingContext) -> Unit = {}
    private var _onStrategyCompleted: suspend (StrategyCompletedContext) -> Unit = {}

    fun onStrategyStarting(handler: suspend (StrategyStartingContext) -> Unit) {
        val original = _onStrategyStarting
        _onStrategyStarting = { original(it); handler(it) }
    }

    fun onStrategyCompleted(handler: suspend (StrategyCompletedContext) -> Unit) {
        val original = _onStrategyCompleted
        _onStrategyCompleted = { original(it); handler(it) }
    }

    // ========== 节点 ==========

    private var _onNodeExecutionStarting: suspend (NodeExecutionStartingContext) -> Unit = {}
    private var _onNodeExecutionCompleted: suspend (NodeExecutionCompletedContext) -> Unit = {}
    private var _onNodeExecutionFailed: suspend (NodeExecutionFailedContext) -> Unit = {}

    fun onNodeExecutionStarting(handler: suspend (NodeExecutionStartingContext) -> Unit) {
        val original = _onNodeExecutionStarting
        _onNodeExecutionStarting = { original(it); handler(it) }
    }

    fun onNodeExecutionCompleted(handler: suspend (NodeExecutionCompletedContext) -> Unit) {
        val original = _onNodeExecutionCompleted
        _onNodeExecutionCompleted = { original(it); handler(it) }
    }

    fun onNodeExecutionFailed(handler: suspend (NodeExecutionFailedContext) -> Unit) {
        val original = _onNodeExecutionFailed
        _onNodeExecutionFailed = { original(it); handler(it) }
    }

    // ========== 子图 ==========

    private var _onSubgraphExecutionStarting: suspend (SubgraphExecutionStartingContext) -> Unit = {}
    private var _onSubgraphExecutionCompleted: suspend (SubgraphExecutionCompletedContext) -> Unit = {}
    private var _onSubgraphExecutionFailed: suspend (SubgraphExecutionFailedContext) -> Unit = {}

    fun onSubgraphExecutionStarting(handler: suspend (SubgraphExecutionStartingContext) -> Unit) {
        val original = _onSubgraphExecutionStarting
        _onSubgraphExecutionStarting = { original(it); handler(it) }
    }

    fun onSubgraphExecutionCompleted(handler: suspend (SubgraphExecutionCompletedContext) -> Unit) {
        val original = _onSubgraphExecutionCompleted
        _onSubgraphExecutionCompleted = { original(it); handler(it) }
    }

    fun onSubgraphExecutionFailed(handler: suspend (SubgraphExecutionFailedContext) -> Unit) {
        val original = _onSubgraphExecutionFailed
        _onSubgraphExecutionFailed = { original(it); handler(it) }
    }

    // ========== LLM 调用 ==========

    private var _onLLMCallStarting: suspend (LLMCallStartingContext) -> Unit = {}
    private var _onLLMCallCompleted: suspend (LLMCallCompletedContext) -> Unit = {}

    fun onLLMCallStarting(handler: suspend (LLMCallStartingContext) -> Unit) {
        val original = _onLLMCallStarting
        _onLLMCallStarting = { original(it); handler(it) }
    }

    fun onLLMCallCompleted(handler: suspend (LLMCallCompletedContext) -> Unit) {
        val original = _onLLMCallCompleted
        _onLLMCallCompleted = { original(it); handler(it) }
    }

    // ========== 工具调用 ==========

    private var _onToolCallStarting: suspend (ToolCallStartingContext) -> Unit = {}
    private var _onToolValidationFailed: suspend (ToolValidationFailedContext) -> Unit = {}
    private var _onToolCallFailed: suspend (ToolCallFailedContext) -> Unit = {}
    private var _onToolCallCompleted: suspend (ToolCallCompletedContext) -> Unit = {}

    fun onToolCallStarting(handler: suspend (ToolCallStartingContext) -> Unit) {
        val original = _onToolCallStarting
        _onToolCallStarting = { original(it); handler(it) }
    }

    fun onToolValidationFailed(handler: suspend (ToolValidationFailedContext) -> Unit) {
        val original = _onToolValidationFailed
        _onToolValidationFailed = { original(it); handler(it) }
    }

    fun onToolCallFailed(handler: suspend (ToolCallFailedContext) -> Unit) {
        val original = _onToolCallFailed
        _onToolCallFailed = { original(it); handler(it) }
    }

    fun onToolCallCompleted(handler: suspend (ToolCallCompletedContext) -> Unit) {
        val original = _onToolCallCompleted
        _onToolCallCompleted = { original(it); handler(it) }
    }

    // ========== LLM 流式 ==========

    private var _onLLMStreamingStarting: suspend (LLMStreamingStartingContext) -> Unit = {}
    private var _onLLMStreamingFrameReceived: suspend (LLMStreamingFrameReceivedContext) -> Unit = {}
    private var _onLLMStreamingFailed: suspend (LLMStreamingFailedContext) -> Unit = {}
    private var _onLLMStreamingCompleted: suspend (LLMStreamingCompletedContext) -> Unit = {}

    fun onLLMStreamingStarting(handler: suspend (LLMStreamingStartingContext) -> Unit) {
        val original = _onLLMStreamingStarting
        _onLLMStreamingStarting = { original(it); handler(it) }
    }

    fun onLLMStreamingFrameReceived(handler: suspend (LLMStreamingFrameReceivedContext) -> Unit) {
        val original = _onLLMStreamingFrameReceived
        _onLLMStreamingFrameReceived = { original(it); handler(it) }
    }

    fun onLLMStreamingFailed(handler: suspend (LLMStreamingFailedContext) -> Unit) {
        val original = _onLLMStreamingFailed
        _onLLMStreamingFailed = { original(it); handler(it) }
    }

    fun onLLMStreamingCompleted(handler: suspend (LLMStreamingCompletedContext) -> Unit) {
        val original = _onLLMStreamingCompleted
        _onLLMStreamingCompleted = { original(it); handler(it) }
    }

    // ========== Internal invoke ==========

    internal suspend fun invokeOnAgentStarting(ctx: AgentStartingContext) = _onAgentStarting(ctx)
    internal suspend fun invokeOnAgentCompleted(ctx: AgentCompletedContext) = _onAgentCompleted(ctx)
    internal suspend fun invokeOnAgentExecutionFailed(ctx: AgentExecutionFailedContext) = _onAgentExecutionFailed(ctx)
    internal suspend fun invokeOnAgentClosing(ctx: AgentClosingContext) = _onAgentClosing(ctx)
    internal suspend fun invokeOnStrategyStarting(ctx: StrategyStartingContext) = _onStrategyStarting(ctx)
    internal suspend fun invokeOnStrategyCompleted(ctx: StrategyCompletedContext) = _onStrategyCompleted(ctx)
    internal suspend fun invokeOnNodeExecutionStarting(ctx: NodeExecutionStartingContext) = _onNodeExecutionStarting(ctx)
    internal suspend fun invokeOnNodeExecutionCompleted(ctx: NodeExecutionCompletedContext) = _onNodeExecutionCompleted(ctx)
    internal suspend fun invokeOnNodeExecutionFailed(ctx: NodeExecutionFailedContext) = _onNodeExecutionFailed(ctx)
    internal suspend fun invokeOnSubgraphExecutionStarting(ctx: SubgraphExecutionStartingContext) = _onSubgraphExecutionStarting(ctx)
    internal suspend fun invokeOnSubgraphExecutionCompleted(ctx: SubgraphExecutionCompletedContext) = _onSubgraphExecutionCompleted(ctx)
    internal suspend fun invokeOnSubgraphExecutionFailed(ctx: SubgraphExecutionFailedContext) = _onSubgraphExecutionFailed(ctx)
    internal suspend fun invokeOnLLMCallStarting(ctx: LLMCallStartingContext) = _onLLMCallStarting(ctx)
    internal suspend fun invokeOnLLMCallCompleted(ctx: LLMCallCompletedContext) = _onLLMCallCompleted(ctx)
    internal suspend fun invokeOnToolCallStarting(ctx: ToolCallStartingContext) = _onToolCallStarting(ctx)
    internal suspend fun invokeOnToolValidationFailed(ctx: ToolValidationFailedContext) = _onToolValidationFailed(ctx)
    internal suspend fun invokeOnToolCallFailed(ctx: ToolCallFailedContext) = _onToolCallFailed(ctx)
    internal suspend fun invokeOnToolCallCompleted(ctx: ToolCallCompletedContext) = _onToolCallCompleted(ctx)
    internal suspend fun invokeOnLLMStreamingStarting(ctx: LLMStreamingStartingContext) = _onLLMStreamingStarting(ctx)
    internal suspend fun invokeOnLLMStreamingFrameReceived(ctx: LLMStreamingFrameReceivedContext) = _onLLMStreamingFrameReceived(ctx)
    internal suspend fun invokeOnLLMStreamingFailed(ctx: LLMStreamingFailedContext) = _onLLMStreamingFailed(ctx)
    internal suspend fun invokeOnLLMStreamingCompleted(ctx: LLMStreamingCompletedContext) = _onLLMStreamingCompleted(ctx)
}
