package com.lhzkml.jasmine.core.agent.tools.feature

/**
 * Agent 生命周期事件类型
 * 完整移植自 koog 的 AgentLifecycleEventType。
 *
 * 事件分为 7 大类：Agent、Strategy、Node、Subgraph、LLM、Tool、LLM Streaming。
 */
sealed interface AgentLifecycleEventType {

    // ========== Agent 事件 ==========

    /** Agent 开始执行 */
    object AgentStarting : AgentLifecycleEventType

    /** Agent 执行完成 */
    object AgentCompleted : AgentLifecycleEventType

    /** Agent 执行失败 */
    object AgentExecutionFailed : AgentLifecycleEventType

    /** Agent 关闭前 */
    object AgentClosing : AgentLifecycleEventType

    // ========== Strategy 事件 ==========

    /** 策略开始执行 */
    object StrategyStarting : AgentLifecycleEventType

    /** 策略执行完成 */
    object StrategyCompleted : AgentLifecycleEventType

    // ========== Node 事件 ==========

    /** 节点开始执行 */
    object NodeExecutionStarting : AgentLifecycleEventType

    /** 节点执行完成 */
    object NodeExecutionCompleted : AgentLifecycleEventType

    /** 节点执行失败 */
    object NodeExecutionFailed : AgentLifecycleEventType

    // ========== Subgraph 事件 ==========

    /** 子图开始执行 */
    object SubgraphExecutionStarting : AgentLifecycleEventType

    /** 子图执行完成 */
    object SubgraphExecutionCompleted : AgentLifecycleEventType

    /** 子图执行失败 */
    object SubgraphExecutionFailed : AgentLifecycleEventType

    // ========== LLM 事件 ==========

    /** LLM 调用开始 */
    object LLMCallStarting : AgentLifecycleEventType

    /** LLM 调用完成 */
    object LLMCallCompleted : AgentLifecycleEventType

    // ========== Tool 事件 ==========

    /** 工具调用开始 */
    object ToolCallStarting : AgentLifecycleEventType

    /** 工具参数验证失败 */
    object ToolValidationFailed : AgentLifecycleEventType

    /** 工具调用失败 */
    object ToolCallFailed : AgentLifecycleEventType

    /** 工具调用完成 */
    object ToolCallCompleted : AgentLifecycleEventType

    // ========== LLM Streaming 事件 ==========

    /** LLM 流式开始 */
    object LLMStreamingStarting : AgentLifecycleEventType

    /** LLM 流式帧接收 */
    object LLMStreamingFrameReceived : AgentLifecycleEventType

    /** LLM 流式失败 */
    object LLMStreamingFailed : AgentLifecycleEventType

    /** LLM 流式完成 */
    object LLMStreamingCompleted : AgentLifecycleEventType
}
