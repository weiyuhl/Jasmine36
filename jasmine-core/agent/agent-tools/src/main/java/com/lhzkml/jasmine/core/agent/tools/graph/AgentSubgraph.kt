package com.lhzkml.jasmine.core.agent.tools.graph

import com.lhzkml.jasmine.core.agent.tools.feature.pipeline.AgentGraphPipeline
import com.lhzkml.jasmine.core.agent.tools.trace.TraceError
import com.lhzkml.jasmine.core.agent.tools.trace.TraceEvent
import com.lhzkml.jasmine.core.prompt.llm.replaceHistoryWithTLDR

/**
 * AutoSelectForTask 工具选择结果
 * 用于 LLM 返回的结构化工具选择响应。
 */
@kotlinx.serialization.Serializable
internal data class SelectedToolNames(val tools: List<String>)

/**
 * Agent 子图
 * 移植自 koog 的 AIAgentSubgraph，包含起始节点和结束节点，
 * 按照边的连接关系依次执行节点。
 *
 * 实现 ExecutionPointNode 接口，支持 checkpoint/rollback 场景。
 *
 * @param name 子图名称
 * @param start 起始节点
 * @param finish 结束节点
 * @param maxIterations 最大迭代次数（防止死循环）
 * @param toolSelectionStrategy 工具选择策略，决定子图执行时可用的工具集合
 */
class AgentSubgraph<TInput, TOutput>(
    val name: String,
    val start: StartNode<TInput>,
    val finish: FinishNode<TOutput>,
    private val maxIterations: Int = 100,
    private val toolSelectionStrategy: ToolSelectionStrategy = ToolSelectionStrategy.ALL
) : ExecutionPointNode {
    /** 子图中所有注册的节点（用于元数据/追踪） */
    internal val nodes = mutableListOf<AgentNode<*, *>>()

    // ========== ExecutionPointNode 实现 ==========
    // 移植自 koog 的 AIAgentSubgraph

    private var forcedNode: AgentNode<*, *>? = null
    private var forcedInput: Any? = null

    override fun getExecutionPoint(): ExecutionPoint? {
        val node = forcedNode ?: return null
        return ExecutionPoint(node, forcedInput)
    }

    override fun resetExecutionPoint() {
        forcedNode = null
        forcedInput = null
    }

    override fun enforceExecutionPoint(node: AgentNode<*, *>, input: Any?) {
        if (forcedNode != null) {
            throw IllegalStateException("Forced node is already set to ${forcedNode!!.name}")
        }
        forcedNode = node
        forcedInput = input
    }

    init {
        nodes.add(start)
        nodes.add(finish)
    }

    fun registerNode(node: AgentNode<*, *>) {
        nodes.add(node)
    }

    /**
     * 执行子图
     * 从 start 节点开始，沿着边依次执行，直到到达 finish 节点。
     * 执行前根据 toolSelectionStrategy 过滤可用工具。
     */
    suspend fun execute(context: AgentGraphContext, input: TInput): TOutput? {
        val tracing = context.tracing
        val graphPipeline = context.pipeline as? AgentGraphPipeline

        tracing?.emit(TraceEvent.SubgraphStarting(
            eventId = tracing.newEventId(), runId = context.runId,
            subgraphName = name, input = input.toString().take(100)
        ))
        graphPipeline?.onSubgraphExecutionStarting(
            eventId = tracing?.newEventId() ?: "", subgraphName = name,
            input = input.toString().take(100), context = context
        )

        // 根据 ToolSelectionStrategy 过滤工具
        val originalTools = context.session.tools
        context.session.tools = selectTools(originalTools, context)

        var currentNode: AgentNode<*, *> = start
        var currentInput: Any? = input

        // 检查是否有强制执行点（checkpoint/rollback）
        val executionPoint = getExecutionPoint()
        if (executionPoint != null) {
            currentNode = executionPoint.node
            currentInput = executionPoint.input
            resetExecutionPoint()
        }

        try {
            var iterations = 0
            while (true) {
                iterations++
                if (iterations > maxIterations) {
                    throw IllegalStateException("Subgraph '$name' exceeded max iterations ($maxIterations)")
                }

                // 发射节点执行开始事件
                tracing?.emit(TraceEvent.NodeExecutionStarting(
                    eventId = tracing.newEventId(), runId = context.runId,
                    nodeName = currentNode.name, input = currentInput.toString().take(100)
                ))
                graphPipeline?.onNodeExecutionStarting(
                    eventId = tracing?.newEventId() ?: "", nodeName = currentNode.name,
                    input = currentInput.toString().take(100), context = context
                )

                // 执行当前节点
                @Suppress("UNCHECKED_CAST")
                val nodeOutput = try {
                    (currentNode as AgentNode<Any?, Any?>).execute(context, currentInput)
                } catch (e: Exception) {
                    tracing?.emit(TraceEvent.NodeExecutionFailed(
                        eventId = tracing.newEventId(), runId = context.runId,
                        nodeName = currentNode.name, input = currentInput.toString().take(100),
                        error = TraceError.from(e)
                    ))
                    graphPipeline?.onNodeExecutionFailed(
                        eventId = tracing?.newEventId() ?: "", nodeName = currentNode.name,
                        input = currentInput.toString().take(100), throwable = e, context = context
                    )
                    throw e
                }

                // 发射节点执行完成事件
                tracing?.emit(TraceEvent.NodeExecutionCompleted(
                    eventId = tracing.newEventId(), runId = context.runId,
                    nodeName = currentNode.name, input = currentInput.toString().take(100),
                    output = nodeOutput.toString().take(100)
                ))
                graphPipeline?.onNodeExecutionCompleted(
                    eventId = tracing?.newEventId() ?: "", nodeName = currentNode.name,
                    input = currentInput.toString().take(100), output = nodeOutput.toString().take(100),
                    context = context
                )

                // 如果到达 finish 节点，返回结果
                if (currentNode === finish) {
                    @Suppress("UNCHECKED_CAST")
                    val result = nodeOutput as? TOutput
                    tracing?.emit(TraceEvent.SubgraphCompleted(
                        eventId = tracing.newEventId(), runId = context.runId,
                        subgraphName = name, result = result.toString().take(100)
                    ))
                    graphPipeline?.onSubgraphExecutionCompleted(
                        eventId = tracing?.newEventId() ?: "", subgraphName = name,
                        input = input.toString().take(100), output = result.toString().take(100),
                        context = context
                    )
                    return result
                }

                // 解析出边
                @Suppress("UNCHECKED_CAST")
                val resolved = (currentNode as AgentNode<Any?, Any?>).resolveEdge(context, nodeOutput)
                    ?: throw IllegalStateException("Agent stuck in node '${currentNode.name}': no matching edge")

                currentNode = resolved.node
                currentInput = resolved.input
            }
        } catch (e: Exception) {
            tracing?.emit(TraceEvent.SubgraphFailed(
                eventId = tracing.newEventId(), runId = context.runId,
                subgraphName = name, error = TraceError.from(e)
            ))
            graphPipeline?.onSubgraphExecutionFailed(
                eventId = tracing?.newEventId() ?: "", subgraphName = name,
                input = input.toString().take(100), throwable = e, context = context
            )
            throw e
        } finally {
            // 恢复原始工具列表
            context.session.tools = originalTools
        }
    }

    /**
     * 根据 ToolSelectionStrategy 过滤工具列表
     * 移植自 koog 的 AIAgentSubgraph.selectTools
     */
    private suspend fun selectTools(
        allTools: List<com.lhzkml.jasmine.core.prompt.model.ToolDescriptor>,
        context: AgentGraphContext
    ): List<com.lhzkml.jasmine.core.prompt.model.ToolDescriptor> {
        return when (toolSelectionStrategy) {
            is ToolSelectionStrategy.ALL -> allTools
            is ToolSelectionStrategy.NONE -> emptyList()
            is ToolSelectionStrategy.Tools -> toolSelectionStrategy.tools
            is ToolSelectionStrategy.ByName -> {
                val nameSet = toolSelectionStrategy.names
                allTools.filter { it.name in nameSet }
            }
            is ToolSelectionStrategy.AutoSelectForTask -> {
                autoSelectTools(allTools, context, toolSelectionStrategy)
            }
        }
    }

    /**
     * 使用 LLM 自动选择与子任务相关的工具
     * 移植自 koog 的 AIAgentSubgraph.selectTools(AutoSelectForTask 分支)
     */
    private suspend fun autoSelectTools(
        allTools: List<com.lhzkml.jasmine.core.prompt.model.ToolDescriptor>,
        context: AgentGraphContext,
        strategy: ToolSelectionStrategy.AutoSelectForTask
    ): List<com.lhzkml.jasmine.core.prompt.model.ToolDescriptor> {
        // 保存当前 prompt 状态
        val initialPrompt = context.session.prompt

        // 压缩历史以减少 token 消耗
        context.session.replaceHistoryWithTLDR()

        // 构建工具选择提示
        val toolListText = allTools.joinToString("\n") { tool ->
            "- ${tool.name}: ${tool.description}"
        }
        val selectionPrompt = buildString {
            appendLine("Given the following available tools:")
            appendLine(toolListText)
            appendLine()
            appendLine("Select the tools that are relevant for the following subtask:")
            appendLine(strategy.subtaskDescription)
            appendLine()
            appendLine("Return a JSON object with a single field \"tools\" containing a list of selected tool names.")
        }

        context.session.appendPrompt { user(selectionPrompt) }

        val result = context.session.requestLLMStructured<SelectedToolNames>(
            examples = listOf(
                SelectedToolNames(emptyList()),
                SelectedToolNames(allTools.map { it.name }.take(3))
            )
        )

        // 恢复原始 prompt
        context.session.rewritePrompt { initialPrompt }

        val selectedNames = result.getOrNull()?.data?.tools?.toSet() ?: return allTools
        return allTools.filter { it.name in selectedNames }
    }
}
