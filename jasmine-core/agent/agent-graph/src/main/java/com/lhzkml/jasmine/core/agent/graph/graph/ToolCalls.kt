package com.lhzkml.jasmine.core.agent.graph.graph

/**
 * Tool call execution mode for graph strategy.
 */
enum class ToolCalls {
    /** Execute tool calls sequentially, one at a time */
    SEQUENTIAL,
    /** Execute tool calls in parallel */
    PARALLEL,
    /** Single run: execute all tool calls sequentially then finish */
    SINGLE_RUN_SEQUENTIAL
}
