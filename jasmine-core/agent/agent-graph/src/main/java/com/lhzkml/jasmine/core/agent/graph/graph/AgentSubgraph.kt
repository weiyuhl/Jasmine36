package com.lhzkml.jasmine.core.agent.graph.graph

import com.lhzkml.jasmine.core.agent.observe.trace.TraceError
import com.lhzkml.jasmine.core.agent.observe.trace.TraceEvent

/**
 * Sub-graph execution engine.
 * Traverses from StartNode along matching edges, executing each AgentNode,
 * until FinishNode is reached.
 */
class AgentSubgraph<TInput, TOutput>(
    val name: String,
    val startNode: StartNode<TInput>,
    val finishNode: FinishNode<TOutput>,
    val nodes: List<BaseAgentNode>
) {

    @Suppress("UNCHECKED_CAST")
    suspend fun execute(context: AgentGraphContext, input: TInput): TOutput? {
        val tracing = context.tracing

        tracing?.emit(
            TraceEvent.SubgraphStarting(
                eventId = tracing.newEventId(),
                runId = context.runId,
                subgraphName = name,
                input = input.toString().take(200)
            )
        )

        var currentNode: BaseAgentNode = startNode
        var currentValue: Any? = input

        try {
            while (true) {
                if (currentNode is FinishNode<*>) {
                    tracing?.emit(
                        TraceEvent.SubgraphCompleted(
                            eventId = tracing.newEventId(),
                            runId = context.runId,
                            subgraphName = name,
                            result = currentValue.toString().take(200)
                        )
                    )
                    return currentValue as? TOutput
                }

                if (currentNode is AgentNode<*, *>) {
                    val agentNode = currentNode as AgentNode<Any?, Any?>
                    val nodeCtx = AgentNodeContext(context.storage, context)

                    tracing?.emit(
                        TraceEvent.NodeExecutionStarting(
                            eventId = tracing.newEventId(),
                            runId = context.runId,
                            nodeName = currentNode.name,
                            input = currentValue.toString().take(200)
                        )
                    )

                    try {
                        currentValue = agentNode.execute.invoke(nodeCtx, currentValue)

                        tracing?.emit(
                            TraceEvent.NodeExecutionCompleted(
                                eventId = tracing.newEventId(),
                                runId = context.runId,
                                nodeName = currentNode.name,
                                input = null,
                                output = currentValue.toString().take(200)
                            )
                        )
                    } catch (e: Exception) {
                        tracing?.emit(
                            TraceEvent.NodeExecutionFailed(
                                eventId = tracing.newEventId(),
                                runId = context.runId,
                                nodeName = currentNode.name,
                                input = null,
                                error = TraceError.from(e)
                            )
                        )
                        throw e
                    }
                }

                val nextNode = findNextNode(currentNode, currentValue)
                    ?: throw IllegalStateException("Agent stuck in node '${currentNode.name}'")

                currentNode = nextNode.first
                currentValue = nextNode.second
            }
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            tracing?.emit(
                TraceEvent.SubgraphFailed(
                    eventId = tracing.newEventId(),
                    runId = context.runId,
                    subgraphName = name,
                    error = TraceError.from(e)
                )
            )
            throw e
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun findNextNode(
        currentNode: BaseAgentNode,
        currentValue: Any?
    ): Pair<BaseAgentNode, Any?>? {
        for (edge in currentNode._edges) {
            val typedEdge = edge as AgentEdge<Any?, Any?>
            if (typedEdge.condition != null) {
                val result = typedEdge.condition.invoke(currentValue)
                if (result != null) {
                    return typedEdge.toNode to result
                }
            } else {
                return typedEdge.toNode to currentValue
            }
        }
        return null
    }
}
