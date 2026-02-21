package com.lhzkml.jasmine.core.agent.tools.graph

/**
 * 工具调用模式
 * 移植自 koog 的 ToolCalls 枚举，定义 Agent 执行工具调用的方式。
 *
 * - SEQUENTIAL: 允许多个工具调用，但按顺序执行
 * - PARALLEL: 允许多个工具调用，并行执行
 * - SINGLE_RUN_SEQUENTIAL: 不允许多个同时工具调用，单个顺序执行
 */
enum class ToolCalls {
    SEQUENTIAL,
    PARALLEL,
    SINGLE_RUN_SEQUENTIAL
}
