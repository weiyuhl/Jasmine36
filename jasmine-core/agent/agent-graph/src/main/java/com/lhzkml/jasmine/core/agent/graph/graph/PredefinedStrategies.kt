package com.lhzkml.jasmine.core.agent.graph.graph

import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.ChatResult
import com.lhzkml.jasmine.core.prompt.model.ToolCall

/**
 * 预定义图策略
 * 移植�?koog �?AIAgentSimpleStrategies.kt，提供开箱即用的 Agent 执行策略�?
 *
 * koog �?singleRunStrategy 支持三种模式�?
 * - SEQUENTIAL: 多工具调用顺序执�?
 * - PARALLEL: 多工具调用并行执�?
 * - SINGLE_RUN_SEQUENTIAL: 单工具调用顺序执�?
 */
object PredefinedStrategies {

    /**
     * 回调 key 常量（类型化 AgentStorageKey�?
     * 通过 context.storage 传�?
     */
    val KEY_ON_CHUNK = AgentStorageKey<suspend (String) -> Unit>("onChunk")
    val KEY_ON_THINKING = AgentStorageKey<suspend (String) -> Unit>("onThinking")
    val KEY_ON_TOOL_CALL_START = AgentStorageKey<suspend (String, String) -> Unit>("onToolCallStart")
    val KEY_ON_TOOL_CALL_RESULT = AgentStorageKey<suspend (String, String) -> Unit>("onToolCallResult")
    val KEY_ON_NODE_ENTER = AgentStorageKey<suspend (String) -> Unit>("onNodeEnter")
    val KEY_ON_NODE_EXIT = AgentStorageKey<suspend (String, Boolean) -> Unit>("onNodeExit")
    val KEY_ON_EDGE = AgentStorageKey<suspend (String, String, String) -> Unit>("onEdge")

    private suspend fun AgentGraphContext.fireNodeEnter(name: String) {
        val cb = storage.get(KEY_ON_NODE_ENTER)
        cb?.invoke(name)
    }

    private suspend fun AgentGraphContext.fireNodeExit(name: String, success: Boolean = true) {
        val cb = storage.get(KEY_ON_NODE_EXIT)
        cb?.invoke(name, success)
    }

    private suspend fun AgentGraphContext.fireEdge(from: String, to: String, label: String = "") {
        val cb = storage.get(KEY_ON_EDGE)
        cb?.invoke(from, to, label)
    }

    /**
     * 单轮执行策略
     * 移植�?koog �?singleRunStrategy，支持三种工具调用模式�?
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
                val toolCalls = result.toolCalls.map { call -> ToolCall(id = call.id, name = call.name, arguments = call.arguments) }
                for (call in toolCalls) { storage.get(KEY_ON_TOOL_CALL_START)?.invoke(call.name, call.arguments) }
                val results = if (parallelTools) {
                    environment.executeTools(toolCalls)
                } else {
                    toolCalls.map { call ->
                        val toolResult = environment.executeTool(call)
                        storage.get(KEY_ON_TOOL_CALL_RESULT)?.invoke(call.name, toolResult.content)
                        toolResult
                    }
                }
                if (parallelTools) { for (r in results) { storage.get(KEY_ON_TOOL_CALL_RESULT)?.invoke(r.tool, r.content) } }
                fireNodeExit("nodeExecuteTools")
                results
            }
            val nodeSendToolResults = node<List<ReceivedToolResult>, ChatResult>("nodeSendToolResults") { results ->
                fireNodeEnter("nodeSendToolResults")
                for (r in results) { session.appendPrompt { message(ChatMessage.toolResult(com.lhzkml.jasmine.core.prompt.model.ToolResult(callId = r.id, name = r.tool, content = r.content))) } }
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
                storage.get(KEY_ON_TOOL_CALL_START)?.invoke(call.name, call.arguments)
                val toolResult = environment.executeTool(toolCall)
                storage.get(KEY_ON_TOOL_CALL_RESULT)?.invoke(call.name, toolResult.content)
                fireNodeExit("nodeExecuteTool")
                toolResult
            }
            val nodeSendToolResult = node<ReceivedToolResult, ChatResult>("nodeSendToolResult") { result ->
                fireNodeEnter("nodeSendToolResult")
                session.appendPrompt { message(ChatMessage.toolResult(com.lhzkml.jasmine.core.prompt.model.ToolResult(callId = result.id, name = result.tool, content = result.content))) }
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
                val onChunk: suspend (String) -> Unit = storage.get(KEY_ON_CHUNK) ?: {}
                val onThinking: suspend (String) -> Unit = storage.get(KEY_ON_THINKING) ?: {}
                val streamResult = session.requestLLMStream(onChunk, onThinking)
                val result = ChatResult(content = streamResult.content, usage = streamResult.usage, finishReason = streamResult.finishReason, toolCalls = streamResult.toolCalls, thinking = streamResult.thinking)
                fireNodeExit("nodeLLMRequest")
                result
            }
            val nodeExecuteTools = node<ChatResult, List<ReceivedToolResult>>("nodeExecuteTools") { result ->
                fireNodeEnter("nodeExecuteTools")
                val toolCalls = result.toolCalls.map { call -> ToolCall(id = call.id, name = call.name, arguments = call.arguments) }
                for (call in toolCalls) { storage.get(KEY_ON_TOOL_CALL_START)?.invoke(call.name, call.arguments) }
                val results = if (parallelTools) {
                    environment.executeTools(toolCalls)
                } else {
                    toolCalls.map { call ->
                        val toolResult = environment.executeTool(call)
                        storage.get(KEY_ON_TOOL_CALL_RESULT)?.invoke(call.name, toolResult.content)
                        toolResult
                    }
                }
                if (parallelTools) { for (r in results) { storage.get(KEY_ON_TOOL_CALL_RESULT)?.invoke(r.tool, r.content) } }
                fireNodeExit("nodeExecuteTools")
                results
            }
            val nodeSendToolResults = node<List<ReceivedToolResult>, ChatResult>("nodeSendToolResultsStream") { results ->
                fireNodeEnter("nodeSendToolResults")
                for (r in results) { session.appendPrompt { message(ChatMessage.toolResult(com.lhzkml.jasmine.core.prompt.model.ToolResult(callId = r.id, name = r.tool, content = r.content))) } }
                val onChunk: suspend (String) -> Unit = storage.get(KEY_ON_CHUNK) ?: {}
                val onThinking: suspend (String) -> Unit = storage.get(KEY_ON_THINKING) ?: {}
                val streamResult = session.requestLLMStream(onChunk, onThinking)
                val llmResult = ChatResult(content = streamResult.content, usage = streamResult.usage, finishReason = streamResult.finishReason, toolCalls = streamResult.toolCalls, thinking = streamResult.thinking)
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
                val onChunk: suspend (String) -> Unit = storage.get(KEY_ON_CHUNK) ?: {}
                val onThinking: suspend (String) -> Unit = storage.get(KEY_ON_THINKING) ?: {}
                val streamResult = session.requestLLMStream(onChunk, onThinking)
                val result = ChatResult(content = streamResult.content, usage = streamResult.usage, finishReason = streamResult.finishReason, toolCalls = streamResult.toolCalls, thinking = streamResult.thinking)
                fireNodeExit("nodeLLMRequest")
                result
            }
            val nodeExecuteTool = node<ChatResult, ReceivedToolResult>("nodeExecuteTool") { result ->
                fireNodeEnter("nodeExecuteTool")
                val call = result.toolCalls.first()
                val toolCall = ToolCall(id = call.id, name = call.name, arguments = call.arguments)
                storage.get(KEY_ON_TOOL_CALL_START)?.invoke(call.name, call.arguments)
                val toolResult = environment.executeTool(toolCall)
                storage.get(KEY_ON_TOOL_CALL_RESULT)?.invoke(call.name, toolResult.content)
                fireNodeExit("nodeExecuteTool")
                toolResult
            }
            val nodeSendToolResult = node<ReceivedToolResult, ChatResult>("nodeSendToolResultStream") { result ->
                fireNodeEnter("nodeSendToolResult")
                session.appendPrompt { message(ChatMessage.toolResult(com.lhzkml.jasmine.core.prompt.model.ToolResult(callId = result.id, name = result.tool, content = result.content))) }
                val onChunk: suspend (String) -> Unit = storage.get(KEY_ON_CHUNK) ?: {}
                val onThinking: suspend (String) -> Unit = storage.get(KEY_ON_THINKING) ?: {}
                val streamResult = session.requestLLMStream(onChunk, onThinking)
                val llmResult = ChatResult(content = streamResult.content, usage = streamResult.usage, finishReason = streamResult.finishReason, toolCalls = streamResult.toolCalls, thinking = streamResult.thinking)
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
