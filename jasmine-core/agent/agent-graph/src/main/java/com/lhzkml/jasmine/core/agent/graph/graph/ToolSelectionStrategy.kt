package com.lhzkml.jasmine.core.agent.graph.graph

/**
 * Strategy for filtering which tools are available to the LLM during graph execution.
 */
sealed class ToolSelectionStrategy {
    /** All registered tools are available */
    object ALL : ToolSelectionStrategy()

    /** No tools are available (pure LLM mode) */
    object NONE : ToolSelectionStrategy()

    /** Only the specified tools are available */
    data class ByName(val names: Set<String>) : ToolSelectionStrategy()

    /** LLM auto-selects relevant tools based on a task description */
    data class AutoSelectForTask(val description: String) : ToolSelectionStrategy()
}
