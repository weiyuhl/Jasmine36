package com.lhzkml.jasmine.core.agent.tools.event

/**
 * 事件处理器事件类别枚举
 * 用于过滤要监听的事件类型。空集合表示全部监听。
 */
enum class EventCategory {
    AGENT,      // Agent 开始/完成/失败
    TOOL,       // 工具调用开始/完成
    LLM,        // LLM 调用完成
    STRATEGY,   // 策略开始/完成
    NODE,       // 节点执行
    SUBGRAPH,   // 子图执行
    STREAMING   // LLM 流式事件
}
