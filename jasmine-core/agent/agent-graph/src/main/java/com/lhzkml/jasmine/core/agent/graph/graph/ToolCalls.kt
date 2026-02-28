package com.lhzkml.jasmine.core.agent.graph.graph

/**
 * 工具调用模式
 * 移植�?koog �?ToolCalls 枚举，定�?Agent 执行工具调用的方式�?
 *
 * - SEQUENTIAL: 允许多个工具调用，但按顺序执�?
 * - PARALLEL: 允许多个工具调用，并行执�?
 * - SINGLE_RUN_SEQUENTIAL: 不允许多个同时工具调用，单个顺序执行
 */
enum class ToolCalls {
    SEQUENTIAL,
    PARALLEL,
    SINGLE_RUN_SEQUENTIAL
}
