package com.lhzkml.jasmine.core.agent.tools.graph

import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.ChatResult
import com.lhzkml.jasmine.core.prompt.model.ToolCall

/**
 * 预定义图策略
 * 移植自 koog 的 AIAgentSimpleStrategies.kt，提供开箱即用的 Agent 执行策略。
 *
 * koog 的 singleRunStrategy 支持三种模式：
 * - SEQUENTIAL: 多工具调用顺序执行
 * - PARALLEL: 多工具调用并行执行
 * - SINGLE_RUN_SEQUENTIAL: 单工具调用顺序执行
 */
object PredefinedStrategies {

    /**
     * 节点生命周期回调 key 常量
     * 通过 context.storage 传递
     */
    const val KEY_ON_NODE_ENTER = "onNodeEnter"
    const val KEY_ON_NODE_EXIT = "onNodeExit"
    const val KEY_ON_EDGE = "onEdge"

    private suspend fun AgentGraphContext.fireNodeEnter(name: String) {
        @Suppress("UNCHECKED_CAST")
        val cb = get<suspend (String) -> Unit>(KEY_ON_NODE_ENTER)
        cb?.invoke(name)
    }

    private suspend fun AgentGraphContext.fireNodeExit(name: String, success: Boolean = true) {
        @Suppress("UNCHECKED_CAST")
        val cb = get<suspend (String, Boolean) -> Unit>(KEY_ON_NODE_EXIT)
        cb?.invoke(name, success)
    }

    private suspend fun AgentGraphContext.fireEdge(from: String, to: String, label: String = "") {
        @Suppress("UNCHECKED_CAST")
        val cb = get<suspend (String, String, String) -> Unit>(KEY_ON_EDGE)
        cb?.invoke(from, to, label)
    }

    /**
     * 单轮执行策略
     * 移植自 koog 的 singleRunStrategy，支持三种工具调用模式。
     *
     * @param runMode 工具调用模式，默认 SEQUENTIAL
     */
    fun singleRunStrategy(
        runMode: ToolCalls = ToolCalls.SEQUENTIAL,
        toolSelection: ToolSelectionStrategy = ToolSelectionStrategy.ALL
    ): AgentStrategy<String, String> {
        return when (runMode) {
            ToolCalls.SEQUENTIAL -> singleRunWithMultipleTools(parallelTools = false, toolSelection = toolSelection)
            ToolCalls.PARALLEL -> singleRunWithMultipleTools(parallelTools = true, toolSelection = toolSelection)
            ToolCalls.SINGLE_RUN_SEQUENTIAL -> singleRunSingleTool(toolSelection = toolSelection)
        }
    }

    /**
     * 支持多工具调用的单轮策略（顺序或并行）
     * 移植自 koog 的 singleRunWithParallelAbility
     */
    private fun singleRunWithMultipleTools(parallelTools: Boolean, toolSelection: ToolSelectionStrategy = ToolSelectionStrategy.ALL): AgentStrategy<String, String> {
        return graphStrategy("single_run_sequential") {
            this.toolSelection = toolSelection

            val nodeLLMRequest = node<String, ChatResult>("nodeLLMRequest") { input ->
                fireNodeEnter("nodeLLMRequest")
                session.appendPrompt { user(input) }
                val result = session.requestLLM()
                fireNodeExit("nodeLLMRequest")
                result
            }

            val nodeExecuteTools = node<ChatResult, List<ReceivedToolResult>>("nodeExecuteTools") { result ->
                fireNodeEnter("nodeExecuteTools")
                val toolCalls = result.toolCalls.map { call ->
                    ToolCall(id = call.id, name = call.name, arguments = call.arguments)
                }
                for (call in toolCalls) {
                    @Suppress("UNCHECKED_CAST")
                    val onStart = get<suspend (String, String) -> Unit>("onToolCallStart")
                    onStart?.invoke(call.name, call.arguments)
                }
                val results = if (parallelTools) {
                    environment.executeTools(toolCalls)
                } else {
                    toolCalls.map { call ->
                        val toolResult = environment.executeTool(call)
                        @Suppress("UNCHECKED_CAST")
                        val onResult = get<suspend (String, String) -> Unit>("onToolCallResult")
                        onResult?.invoke(call.name, toolResult.content)
                        toolResult
                    }
                }
                if (parallelTools) {
                    for (r in results) {
                        @Suppress("UNCHECKED_CAST")
                        val onResult = get<suspend (String, String) -> Unit>("onToolCallResult")
                        onResult?.invoke(r.tool, r.content)
                    }
                }
                fireNodeExit("nodeExecuteTools")
                results
            }

            val nodeSendToolResults = node<List<ReceivedToolResult>, ChatResult>("nodeSendToolResults") { results ->
                fireNodeEnter("nodeSendToolResults")
                for (r in results) {
                    session.appendPrompt {
                        message(ChatMessage.toolResult(
                            com.lhzkml.jasmine.core.prompt.model.ToolResult(
                                callId = r.id, name = r.tool, content = r.content
                            )
                        ))
                    }
                }
                val llmResult = session.requestLLM()
                fireNodeExit("nodeSendToolResults")
                llmResult
            }

            edge(nodeStart, nodeLLMRequest)
            conditionalEdge(nodeLLMRequest, nodeExecuteTools) { r -> if (r.hasToolCalls) r else null }
            conditionalEdge(nodeLLMRequest, nodeFinish) { r -> if (!r.hasToolCalls) r.content else null }
            edge(nodeExecuteTools, nodeSendToolResults)
            conditionalEdge(nodeSendToolResults, nodeExecuteTools) { r -> if (r.hasToolCalls) r else null }
            conditionalEdge(nodeSendToolResults, nodeFinish) { r -> if (!r.hasToolCalls) r.content else null }
        }
    }

    /**
     * 单工具调用的单轮策略
     * 移植自 koog 的 singleRunModeStrategy
     */
    private fun singleRunSingleTool(toolSelection: ToolSelectionStrategy = ToolSelectionStrategy.ALL): AgentStrategy<String, String> {
        return graphStrategy("single_run") {
            this.toolSelection = toolSelection

            val nodeLLMRequest = node<String, ChatResult>("nodeLLMRequest") { input ->
                fireNodeEnter("nodeLLMRequest")
                session.appendPrompt { user(input) }
                val result = session.requestLLM()
                fireNodeExit("nodeLLMRequest")
                result
            }

            val nodeExecuteTool = node<ChatResult, ReceivedToolResult>("nodeExecuteTool") { result ->
                fireNodeEnter("nodeExecuteTool")
                val call = result.toolCalls.first()
                val toolCall = ToolCall(id = call.id, name = call.name, arguments = call.arguments)
                @Suppress("UNCHECKED_CAST")
                val onStart = get<suspend (String, String) -> Unit>("onToolCallStart")
                onStart?.invoke(call.name, call.arguments)
                val toolResult = environment.executeTool(toolCall)
                @Suppress("UNCHECKED_CAST")
                val onResult = get<suspend (String, String) -> Unit>("onToolCallResult")
                onResult?.invoke(call.name, toolResult.content)
                fireNodeExit("nodeExecuteTool")
                toolResult
            }

            val nodeSendToolResult = node<ReceivedToolResult, ChatResult>("nodeSendToolResult") { result ->
                fireNodeEnter("nodeSendToolResult")
                session.appendPrompt {
                    message(ChatMessage.toolResult(
                        com.lhzkml.jasmine.core.prompt.model.ToolResult(
                            callId = result.id, name = result.tool, content = result.content
                        )
                    ))
                }
                val llmResult = session.requestLLM()
                fireNodeExit("nodeSendToolResult")
                llmResult
            }

            edge(nodeStart, nodeLLMRequest)
            conditionalEdge(nodeLLMRequest, nodeExecuteTool) { r -> if (r.hasToolCalls) r else null }
            conditionalEdge(nodeLLMRequest, nodeFinish) { r -> if (!r.hasToolCalls) r.content else null }
            edge(nodeExecuteTool, nodeSendToolResult)
            conditionalEdge(nodeSendToolResult, nodeExecuteTool) { r -> if (r.hasToolCalls) r else null }
            conditionalEdge(nodeSendToolResult, nodeFinish) { r -> if (!r.hasToolCalls) r.content else null }
        }
    }

    /**
     * 单轮流式执行策略
     * @param runMode 工具调用模式
     */
    fun singleRunStreamStrategy(
        runMode: ToolCalls = ToolCalls.SEQUENTIAL,
        toolSelection: ToolSelectionStrategy = ToolSelectionStrategy.ALL
    ): AgentStrategy<String, String> {
        return when (runMode) {
            ToolCalls.SEQUENTIAL -> singleRunStreamWithMultipleTools(parallelTools = false, toolSelection = toolSelection)
            ToolCalls.PARALLEL -> singleRunStreamWithMultipleTools(parallelTools = true, toolSelection = toolSelection)
            ToolCalls.SINGLE_RUN_SEQUENTIAL -> singleRunStreamSingleTool(toolSelection = toolSelection)
        }
    }

    private fun singleRunStreamWithMultipleTools(parallelTools: Boolean, toolSelection: ToolSelectionStrategy = ToolSelectionStrategy.ALL): AgentStrategy<String, String> {
        return graphStrategy("single_run_stream") {
            this.toolSelection = toolSelection

            val nodeLLMRequest = node<String, ChatResult>("nodeLLMRequestStream") { input ->
                fireNodeEnter("nodeLLMRequest")
                session.appendPrompt { user(input) }
                @Suppress("UNCHECKED_CAST")
                val onChunk = get<suspend (String) -> Unit>("onChunk") ?: { _ -> }
                @Suppress("UNCHECKED_CAST")
                val onThinking = get<suspend (String) -> Unit>("onThinking") ?: { _ -> }
                val streamResult = session.requestLLMStream(onChunk, onThinking)
                val result = ChatResult(
                    content = streamResult.content, usage = streamResult.usage,
                    finishReason = streamResult.finishReason,
                    toolCalls = streamResult.toolCalls, thinking = streamResult.thinking
                )
                fireNodeExit("nodeLLMRequest")
                result
            }

            val nodeExecuteTools = node<ChatResult, List<ReceivedToolResult>>("nodeExecuteTools") { result ->
                fireNodeEnter("nodeExecuteTools")
                val toolCalls = result.toolCalls.map { call ->
                    ToolCall(id = call.id, name = call.name, arguments = call.arguments)
                }
                for (call in toolCalls) {
                    @Suppress("UNCHECKED_CAST")
                    val onStart = get<suspend (String, String) -> Unit>("onToolCallStart")
                    onStart?.invoke(call.name, call.arguments)
                }
                val results = if (parallelTools) {
                    environment.executeTools(toolCalls)
                } else {
                    toolCalls.map { call ->
                        val toolResult = environment.executeTool(call)
                        @Suppress("UNCHECKED_CAST")
                        val onResult = get<suspend (String, String) -> Unit>("onToolCallResult")
                        onResult?.invoke(call.name, toolResult.content)
                        toolResult
                    }
                }
                if (parallelTools) {
                    for (r in results) {
                        @Suppress("UNCHECKED_CAST")
                        val onResult = get<suspend (String, String) -> Unit>("onToolCallResult")
                        onResult?.invoke(r.tool, r.content)
                    }
                }
                fireNodeExit("nodeExecuteTools")
                results
            }

            val nodeSendToolResults = node<List<ReceivedToolResult>, ChatResult>("nodeSendToolResultsStream") { results ->
                fireNodeEnter("nodeSendToolResults")
                for (r in results) {
                    session.appendPrompt {
                        message(ChatMessage.toolResult(
                            com.lhzkml.jasmine.core.prompt.model.ToolResult(
                                callId = r.id, name = r.tool, content = r.content
                            )
                        ))
                    }
                }
                @Suppress("UNCHECKED_CAST")
                val onChunk = get<suspend (String) -> Unit>("onChunk") ?: { _ -> }
                @Suppress("UNCHECKED_CAST")
                val onThinking = get<suspend (String) -> Unit>("onThinking") ?: { _ -> }
                val streamResult = session.requestLLMStream(onChunk, onThinking)
                val llmResult = ChatResult(
                    content = streamResult.content, usage = streamResult.usage,
                    finishReason = streamResult.finishReason,
                    toolCalls = streamResult.toolCalls, thinking = streamResult.thinking
                )
                fireNodeExit("nodeSendToolResults")
                llmResult
            }

            edge(nodeStart, nodeLLMRequest)
            conditionalEdge(nodeLLMRequest, nodeExecuteTools) { r -> if (r.hasToolCalls) r else null }
            conditionalEdge(nodeLLMRequest, nodeFinish) { r -> if (!r.hasToolCalls) r.content else null }
            edge(nodeExecuteTools, nodeSendToolResults)
            conditionalEdge(nodeSendToolResults, nodeExecuteTools) { r -> if (r.hasToolCalls) r else null }
            conditionalEdge(nodeSendToolResults, nodeFinish) { r -> if (!r.hasToolCalls) r.content else null }
        }
    }

    private fun singleRunStreamSingleTool(toolSelection: ToolSelectionStrategy = ToolSelectionStrategy.ALL): AgentStrategy<String, String> {
        return graphStrategy("single_run_stream") {
            this.toolSelection = toolSelection

            val nodeLLMRequest = node<String, ChatResult>("nodeLLMRequestStream") { input ->
                fireNodeEnter("nodeLLMRequest")
                session.appendPrompt { user(input) }
                @Suppress("UNCHECKED_CAST")
                val onChunk = get<suspend (String) -> Unit>("onChunk") ?: { _ -> }
                @Suppress("UNCHECKED_CAST")
                val onThinking = get<suspend (String) -> Unit>("onThinking") ?: { _ -> }
                val streamResult = session.requestLLMStream(onChunk, onThinking)
                val result = ChatResult(
                    content = streamResult.content, usage = streamResult.usage,
                    finishReason = streamResult.finishReason,
                    toolCalls = streamResult.toolCalls, thinking = streamResult.thinking
                )
                fireNodeExit("nodeLLMRequest")
                result
            }

            val nodeExecuteTool = node<ChatResult, ReceivedToolResult>("nodeExecuteTool") { result ->
                fireNodeEnter("nodeExecuteTool")
                val call = result.toolCalls.first()
                val toolCall = ToolCall(id = call.id, name = call.name, arguments = call.arguments)
                @Suppress("UNCHECKED_CAST")
                val onStart = get<suspend (String, String) -> Unit>("onToolCallStart")
                onStart?.invoke(call.name, call.arguments)
                val toolResult = environment.executeTool(toolCall)
                @Suppress("UNCHECKED_CAST")
                val onResult = get<suspend (String, String) -> Unit>("onToolCallResult")
                onResult?.invoke(call.name, toolResult.content)
                fireNodeExit("nodeExecuteTool")
                toolResult
            }

            val nodeSendToolResult = node<ReceivedToolResult, ChatResult>("nodeSendToolResultStream") { result ->
                fireNodeEnter("nodeSendToolResult")
                session.appendPrompt {
                    message(ChatMessage.toolResult(
                        com.lhzkml.jasmine.core.prompt.model.ToolResult(
                            callId = result.id, name = result.tool, content = result.content
                        )
                    ))
                }
                @Suppress("UNCHECKED_CAST")
                val onChunk = get<suspend (String) -> Unit>("onChunk") ?: { _ -> }
                @Suppress("UNCHECKED_CAST")
                val onThinking = get<suspend (String) -> Unit>("onThinking") ?: { _ -> }
                val streamResult = session.requestLLMStream(onChunk, onThinking)
                val llmResult = ChatResult(
                    content = streamResult.content, usage = streamResult.usage,
                    finishReason = streamResult.finishReason,
                    toolCalls = streamResult.toolCalls, thinking = streamResult.thinking
                )
                fireNodeExit("nodeSendToolResult")
                llmResult
            }

            edge(nodeStart, nodeLLMRequest)
            conditionalEdge(nodeLLMRequest, nodeExecuteTool) { r -> if (r.hasToolCalls) r else null }
            conditionalEdge(nodeLLMRequest, nodeFinish) { r -> if (!r.hasToolCalls) r.content else null }
            edge(nodeExecuteTool, nodeSendToolResult)
            conditionalEdge(nodeSendToolResult, nodeExecuteTool) { r -> if (r.hasToolCalls) r else null }
            conditionalEdge(nodeSendToolResult, nodeFinish) { r -> if (!r.hasToolCalls) r.content else null }
        }
    }
}