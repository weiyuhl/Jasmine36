package com.lhzkml.jasmine.core.agent.tools.graph

import com.lhzkml.jasmine.core.agent.tools.trace.TraceError
import com.lhzkml.jasmine.core.agent.tools.trace.TraceEvent

/**
 * Agent 子图
 * 参考 koog 的 AIAgentSubgraph，包含起始节点和结束节点，
 * 按照边的连接关系依次执行节点。
 *
 * @param name 子图名称
 * @param start 起始节点
 * @param finish 结束节点
 * @param maxIterations 最大迭代次数（防止死循环）
 */
class AgentSubgraph<TInput, TOutput>(
    val name: String,
    val start: StartNode<TInput>,
    val finish: FinishNode<TOutput>,
    private val maxIterations: Int = 100
) {
    /** 子图中所有注册的节点（用于元数据/追踪） */
    internal val nodes = mutableListOf<AgentNode<*, *>>()

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
     */
    suspend fun execute(context: AgentGraphContext, input: TInput): TOutput? {
        val tracing = context.tracing

        tracing?.emit(TraceEvent.SubgraphStarting(
            eventId = tracing.newEventId(), runId = context.runId,
            subgraphName = name, input = input.toString().take(100)
        ))

        var currentNode: AgentNode<*, *> = start
        var currentInput: Any? = input

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
                    throw e
                }

                // 发射节点执行完成事件
                tracing?.emit(TraceEvent.NodeExecutionCompleted(
                    eventId = tracing.newEventId(), runId = context.runId,
                    nodeName = currentNode.name, input = currentInput.toString().take(100),
                    output = nodeOutput.toString().take(100)
                ))

                // 如果到达 finish 节点，返回结果
                if (currentNode === finish) {
                    @Suppress("UNCHECKED_CAST")
                    val result = nodeOutput as? TOutput
                    tracing?.emit(TraceEvent.SubgraphCompleted(
                        eventId = tracing.newEventId(), runId = context.runId,
                        subgraphName = name, result = result.toString().take(100)
                    ))
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
            throw e
        }
    }
}
