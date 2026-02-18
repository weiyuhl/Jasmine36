package com.lhzkml.jasmine.core.agent.tools.graph

import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.ChatResult

/**
 * 预定义图策略
 * 参考 koog 的 AIAgentSimpleStrategies.kt，提供开箱即用的 Agent 执行策略。
 *
 * koog 的 singleRunStrategy 流程：
 *   Start → CallLLM → [有 tool_calls?] → ExecuteTool → SendToolResult → [循环或结束]
 *
 * jasmine 的实现使用 LLMSession + ToolRegistry 完成同样的流程。
 */
object PredefinedStrategies {

    /**
     * 节点生命周期回调 key 常量
     * 通过 context.storage 传递
     */
    const val KEY_ON_NODE_ENTER = "onNodeEnter"   // suspend (nodeName: String) -> Unit
    const val KEY_ON_NODE_EXIT = "onNodeExit"      // suspend (nodeName: String, success: Boolean) -> Unit
    const val KEY_ON_EDGE = "onEdge"               // suspend (from: String, to: String, label: String) -> Unit

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
     * 单轮执行策略（对应 koog 的 singleRunStrategy）
     *
     * 流程：
     *   nodeStart → nodeLLMRequest → [tool_calls] → nodeExecuteTool → nodeSendToolResult → [循环]
     *                              → [assistant]  → nodeFinish
     *
     * @param maxToolIterations 最大工具调用循环次数，防止死循环
     */
    fun singleRunStrategy(maxToolIterations: Int = 10): AgentStrategy<String, String> {
        return graphStrategy("single_run") {

            // 节点1: 发送用户消息给 LLM
            val nodeLLMRequest = node<String, ChatResult>("nodeLLMRequest") { input ->
                fireNodeEnter("nodeLLMRequest")
                session.appendPrompt { user(input) }
                val result = session.requestLLM()
                if (result.hasToolCalls) {
                    fireEdge("nodeLLMRequest", "nodeExecuteTool", "tool_calls ×${result.toolCalls.size}")
                } else {
                    fireEdge("nodeLLMRequest", "Finish", "assistant")
                }
                fireNodeExit("nodeLLMRequest")
                result
            }

            // 节点2: 执行工具调用
            val nodeExecuteTool = node<ChatResult, List<Pair<String, String>>>("nodeExecuteTool") { result ->
                fireNodeEnter("nodeExecuteTool")
                val toolResults = mutableListOf<Pair<String, String>>()
                for (call in result.toolCalls) {
                    @Suppress("UNCHECKED_CAST")
                    val onStart = get<suspend (String, String) -> Unit>("onToolCallStart")
                    onStart?.invoke(call.name, call.arguments)

                    val toolResult = toolRegistry.execute(call)

                    @Suppress("UNCHECKED_CAST")
                    val onResult = get<suspend (String, String) -> Unit>("onToolCallResult")
                    onResult?.invoke(call.name, toolResult.content)

                    toolResults.add(call.id to toolResult.content)
                    session.appendPrompt { message(ChatMessage.toolResult(toolResult)) }
                }
                fireEdge("nodeExecuteTool", "nodeSendToolResult", "${toolResults.size} 个结果")
                fireNodeExit("nodeExecuteTool")
                toolResults
            }

            // 节点3: 发送工具结果给 LLM，获取新回复
            val nodeSendToolResult = node<List<Pair<String, String>>, ChatResult>("nodeSendToolResult") { _ ->
                fireNodeEnter("nodeSendToolResult")
                // 工具结果已在 nodeExecuteTool 中追加到 prompt
                val result = session.requestLLM()
                if (result.hasToolCalls) {
                    fireEdge("nodeSendToolResult", "nodeExecuteTool", "继续调用 ×${result.toolCalls.size}")
                } else {
                    fireEdge("nodeSendToolResult", "Finish", "assistant")
                }
                fireNodeExit("nodeSendToolResult")
                result
            }

            // 边：Start → LLMRequest
            edge(nodeStart, nodeLLMRequest)

            // 边：LLMRequest → ExecuteTool（当有 tool_calls 时）
            conditionalEdge(nodeLLMRequest, nodeExecuteTool) { result ->
                if (result.hasToolCalls) result else null
            }

            // 边：LLMRequest → Finish（当无 tool_calls 时，返回文本内容）
            conditionalEdge(nodeLLMRequest, nodeFinish) { result ->
                if (!result.hasToolCalls) result.content else null
            }

            // 边：ExecuteTool → SendToolResult
            edge(nodeExecuteTool, nodeSendToolResult)

            // 边：SendToolResult → ExecuteTool（当 LLM 继续请求工具时）
            conditionalEdge(nodeSendToolResult, nodeExecuteTool) { result ->
                if (result.hasToolCalls) result else null
            }

            // 边：SendToolResult → Finish（当 LLM 返回最终文本时）
            conditionalEdge(nodeSendToolResult, nodeFinish) { result ->
                if (!result.hasToolCalls) result.content else null
            }
        }
    }

    /**
     * 单轮流式执行策略
     *
     * 与 singleRunStrategy 类似，但 LLM 请求使用流式输出。
     * 通过 context.storage 传递 onChunk 回调。
     *
     * 使用方式：
     * ```kotlin
     * context.put("onChunk", onChunk)
     * context.put("onThinking", onThinking)
     * ```
     */
    fun singleRunStreamStrategy(): AgentStrategy<String, String> {
        return graphStrategy("single_run_stream") {

            // 节点1: 流式发送用户消息给 LLM
            val nodeLLMRequest = node<String, ChatResult>("nodeLLMRequestStream") { input ->
                fireNodeEnter("nodeLLMRequest")
                session.appendPrompt { user(input) }

                @Suppress("UNCHECKED_CAST")
                val onChunk = get<suspend (String) -> Unit>("onChunk") ?: { _ -> }
                @Suppress("UNCHECKED_CAST")
                val onThinking = get<suspend (String) -> Unit>("onThinking") ?: { _ -> }

                val streamResult = session.requestLLMStream(onChunk, onThinking)
                // 转换为 ChatResult 以复用相同的边条件
                val result = ChatResult(
                    content = streamResult.content,
                    usage = streamResult.usage,
                    finishReason = streamResult.finishReason,
                    toolCalls = streamResult.toolCalls,
                    thinking = streamResult.thinking
                )
                if (result.hasToolCalls) {
                    fireEdge("nodeLLMRequest", "nodeExecuteTool", "tool_calls ×${result.toolCalls.size}")
                } else {
                    fireEdge("nodeLLMRequest", "Finish", "assistant")
                }
                fireNodeExit("nodeLLMRequest")
                result
            }

            // 节点2: 执行工具调用
            val nodeExecuteTool = node<ChatResult, List<Pair<String, String>>>("nodeExecuteTool") { result ->
                fireNodeEnter("nodeExecuteTool")
                val toolResults = mutableListOf<Pair<String, String>>()
                for (call in result.toolCalls) {
                    @Suppress("UNCHECKED_CAST")
                    val onStart = get<suspend (String, String) -> Unit>("onToolCallStart")
                    onStart?.invoke(call.name, call.arguments)

                    val toolResult = toolRegistry.execute(call)

                    @Suppress("UNCHECKED_CAST")
                    val onResult = get<suspend (String, String) -> Unit>("onToolCallResult")
                    onResult?.invoke(call.name, toolResult.content)

                    toolResults.add(call.id to toolResult.content)
                    session.appendPrompt { message(ChatMessage.toolResult(toolResult)) }
                }
                fireEdge("nodeExecuteTool", "nodeSendToolResult", "${toolResults.size} 个结果")
                fireNodeExit("nodeExecuteTool")
                toolResults
            }

            // 节点3: 流式发送工具结果给 LLM
            val nodeSendToolResult = node<List<Pair<String, String>>, ChatResult>("nodeSendToolResultStream") { _ ->
                fireNodeEnter("nodeSendToolResult")
                @Suppress("UNCHECKED_CAST")
                val onChunk = get<suspend (String) -> Unit>("onChunk") ?: { _ -> }
                @Suppress("UNCHECKED_CAST")
                val onThinking = get<suspend (String) -> Unit>("onThinking") ?: { _ -> }

                val streamResult = session.requestLLMStream(onChunk, onThinking)
                val result = ChatResult(
                    content = streamResult.content,
                    usage = streamResult.usage,
                    finishReason = streamResult.finishReason,
                    toolCalls = streamResult.toolCalls,
                    thinking = streamResult.thinking
                )
                if (result.hasToolCalls) {
                    fireEdge("nodeSendToolResult", "nodeExecuteTool", "继续调用 ×${result.toolCalls.size}")
                } else {
                    fireEdge("nodeSendToolResult", "Finish", "assistant")
                }
                fireNodeExit("nodeSendToolResult")
                result
            }

            // 边
            edge(nodeStart, nodeLLMRequest)
            conditionalEdge(nodeLLMRequest, nodeExecuteTool) { result ->
                if (result.hasToolCalls) result else null
            }
            conditionalEdge(nodeLLMRequest, nodeFinish) { result ->
                if (!result.hasToolCalls) result.content else null
            }
            edge(nodeExecuteTool, nodeSendToolResult)
            conditionalEdge(nodeSendToolResult, nodeExecuteTool) { result ->
                if (result.hasToolCalls) result else null
            }
            conditionalEdge(nodeSendToolResult, nodeFinish) { result ->
                if (!result.hasToolCalls) result.content else null
            }
        }
    }
}
