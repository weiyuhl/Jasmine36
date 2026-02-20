package com.lhzkml.jasmine.core.agent.tools.trace

/**
 * 追踪事件类别枚举
 * 用于过滤要追踪的事件类型。空集合表示全部追踪。
 */
enum class TraceEventCategory {
    AGENT,       // Agent 生命周期
    LLM,         // LLM 调用
    TOOL,        // 工具调用
    STRATEGY,    // 策略执行
    NODE,        // 节点执行
    SUBGRAPH,    // 子图执行
    COMPRESSION  // 压缩事件
}
