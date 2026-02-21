package com.lhzkml.jasmine.core.agent.tools.graph

import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor

/**
 * 工具选择策略
 * 移植自 koog 的 ToolSelectionStrategy，决定子图执行时可用的工具集合。
 *
 * 使用方式：
 * ```kotlin
 * val strategy = graphStrategy<String, String>("my-strategy") {
 *     // 设置子图的工具选择策略
 *     toolSelection = ToolSelectionStrategy.Tools(
 *         listOf(readFileTool.descriptor, writeFileTool.descriptor)
 *     )
 *
 *     val process by node<String, String>("process") { input -> ... }
 *     edge(nodeStart, process)
 *     edge(process, nodeFinish)
 * }
 * ```
 */
sealed interface ToolSelectionStrategy {

    /**
     * 使用所有可用工具（默认）
     */
    data object ALL : ToolSelectionStrategy

    /**
     * 不使用任何工具
     */
    data object NONE : ToolSelectionStrategy

    /**
     * 使用指定的工具列表
     *
     * @param tools 允许使用的工具描述列表
     */
    data class Tools(val tools: List<ToolDescriptor>) : ToolSelectionStrategy

    /**
     * 按工具名称过滤
     *
     * @param names 允许使用的工具名称集合
     */
    data class ByName(val names: Set<String>) : ToolSelectionStrategy
}
