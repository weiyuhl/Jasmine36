package com.lhzkml.jasmine.core.agent.graph.graph

import com.lhzkml.jasmine.core.agent.observe.trace.TraceError
import com.lhzkml.jasmine.core.agent.observe.trace.TraceEvent
import com.lhzkml.jasmine.core.prompt.llm.StreamResult
import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.ToolResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Predefined graph strategies for common Agent patterns.
 */
object PredefinedStrategies {

    val KEY_ON_CHUNK = AgentStorageKey<suspend (String) -> Unit>("onChunk")
    val KEY_ON_THINKING = AgentStorageKey<suspend (String) -> Unit>("onThinking")
    val KEY_ON_TOOL_CALL_START = AgentStorageKey<suspend (String, String) -> Unit>("onToolCallStart")
    val KEY_ON_TOOL_CALL_RESULT = AgentStorageKey<suspend (String, String) -> Unit>("onToolCallResult")
    val KEY_ON_NODE_ENTER = AgentStorageKey<suspend (String) -> Unit>("onNodeEnter")
    val KEY_ON_NODE_EXIT = AgentStorageKey<suspend (String, Boolean) -> Unit>("onNodeExit")
    val KEY_ON_EDGE = AgentStorageKey<suspend (String, String, String) -> Unit>("onEdge")

    private val KEY_LAST_STREAM_RESULT = AgentStorageKey<StreamResult>("lastStreamResult")
    private val KEY_TOOL_CALL_MODE = AgentStorageKey<ToolCalls>("toolCallMode")
    private val KEY_TOOL_SELECTION = AgentStorageKey<ToolSelectionStrategy>("toolSelection")
    private val KEY_MAX_TOOL_RESULT_LENGTH = AgentStorageKey<Int>("maxToolResultLength")
    private const val DEFAULT_MAX_TOOL_RESULT_LENGTH = 8000

    /**
     * Single-run stream strategy: LLM -> tool execution loop until LLM returns no tool calls.
     *
     * Flow: Start -> LLMRequest -> [has tools?] -> ExecuteTool -> LLMRequest / -> Finish
     */
    fun singleRunStreamStrategy(
        toolCallMode: ToolCalls = ToolCalls.SEQUENTIAL,
        toolSelectionStrategy: ToolSelectionStrategy = ToolSelectionStrategy.ALL,
        maxToolResultLength: Int = DEFAULT_MAX_TOOL_RESULT_LENGTH
    ): AgentStrategy<String, String> = graphStrategy("singleRunStream") {

        val setup = node<String, String>("Setup") { input ->
            storage.set(KEY_TOOL_CALL_MODE, toolCallMode)
            storage.set(KEY_TOOL_SELECTION, toolSelectionStrategy)
            storage.set(KEY_MAX_TOOL_RESULT_LENGTH, maxToolResultLength)
            input
        }

        val llmRequest = node<String, String>("LLMRequest") { _ ->
            val onChunk = storage.get(KEY_ON_CHUNK) ?: {}
            val onThinking = storage.get(KEY_ON_THINKING) ?: {}

            val result = context.session.requestLLMStream(
                onChunk = onChunk,
                onThinking = onThinking
            )

            storage.set(KEY_LAST_STREAM_RESULT, result)
            result.content
        }

        val executeTool = node<String, String>("ExecuteTool") { _ ->
            val result = storage.get(KEY_LAST_STREAM_RESULT)
                ?: return@node "Error: No LLM result"
            val mode = storage.get(KEY_TOOL_CALL_MODE) ?: ToolCalls.SEQUENTIAL
            val maxLen = storage.get(KEY_MAX_TOOL_RESULT_LENGTH) ?: DEFAULT_MAX_TOOL_RESULT_LENGTH
            val registry = context.toolRegistry
            val tracing = context.tracing
            val onStart = storage.get(KEY_ON_TOOL_CALL_START)
            val onResult = storage.get(KEY_ON_TOOL_CALL_RESULT)

            val toolCalls = result.toolCalls

            val toolResults = when (mode) {
                ToolCalls.SEQUENTIAL, ToolCalls.SINGLE_RUN_SEQUENTIAL -> {
                    toolCalls.map { call ->
                        executeSingleTool(call, registry, tracing, context.runId, onStart, onResult, maxLen)
                    }
                }
                ToolCalls.PARALLEL -> {
                    coroutineScope {
                        toolCalls.map { call ->
                            async {
                                executeSingleTool(call, registry, tracing, context.runId, onStart, onResult, maxLen)
                            }
                        }.awaitAll()
                    }
                }
            }

            context.session.appendPrompt {
                tool {
                    toolResults.forEach { tr -> result(tr) }
                }
            }

            "Executed ${toolResults.size} tool(s)"
        }

        val route = node<String, String>("Route") { content ->
            val sr = storage.get(KEY_LAST_STREAM_RESULT)
            if (sr != null && sr.hasToolCalls) "HAS_TOOLS" else content
        }

        edge(nodeStart, setup)
        edge(setup, llmRequest)
        edge(llmRequest, route)
        conditionalEdge<String, String>(route, executeTool) { output ->
            if (output == "HAS_TOOLS") "" else null
        }
        conditionalEdge<String, String>(route, nodeFinish) { output ->
            if (output != "HAS_TOOLS") output else null
        }
        edge(executeTool, llmRequest)
    }

    private suspend fun executeSingleTool(
        call: com.lhzkml.jasmine.core.prompt.model.ToolCall,
        registry: com.lhzkml.jasmine.core.agent.tools.ToolRegistry,
        tracing: com.lhzkml.jasmine.core.agent.observe.trace.Tracing?,
        runId: String,
        onStart: (suspend (String, String) -> Unit)?,
        onResult: (suspend (String, String) -> Unit)?,
        maxLen: Int
    ): ToolResult {
        tracing?.emit(
            TraceEvent.ToolCallStarting(
                eventId = tracing.newEventId(), runId = runId,
                toolCallId = call.id, toolName = call.name, toolArgs = call.arguments
            )
        )
        onStart?.invoke(call.name, call.arguments)

        return try {
            val toolResult = registry.execute(call)
            val truncated = truncateResult(toolResult, maxLen)

            tracing?.emit(
                TraceEvent.ToolCallCompleted(
                    eventId = tracing.newEventId(), runId = runId,
                    toolCallId = call.id, toolName = call.name,
                    toolArgs = call.arguments, result = truncated.content.take(200)
                )
            )
            onResult?.invoke(call.name, truncated.content)
            truncated
        } catch (e: Exception) {
            tracing?.emit(
                TraceEvent.ToolCallFailed(
                    eventId = tracing.newEventId(), runId = runId,
                    toolCallId = call.id, toolName = call.name,
                    toolArgs = call.arguments, error = TraceError.from(e)
                )
            )
            val errContent = "Error executing ${call.name}: ${e.message}"
            onResult?.invoke(call.name, errContent)
            ToolResult(callId = call.id, name = call.name, content = errContent)
        }
    }

    private fun truncateResult(result: ToolResult, maxLen: Int): ToolResult {
        if (result.content.length <= maxLen) return result
        val truncated = result.content.take(maxLen) +
            "\n\n[Result truncated, original: ${result.content.length} chars, showing first $maxLen]"
        return result.copy(content = truncated)
    }
}
