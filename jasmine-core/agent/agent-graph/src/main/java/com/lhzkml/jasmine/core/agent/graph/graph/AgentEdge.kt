package com.lhzkml.jasmine.core.agent.graph.graph

import com.lhzkml.jasmine.core.prompt.model.ChatResult
import kotlin.reflect.KClass

/**
 * Agent 图的有向�?
 * 移植�?koog �?AIAgentEdge，连接两个节点，支持条件转发和数据变换�?
 *
 * @param toNode 目标节点
 * @param condition 条件函数，返�?null 表示不匹配，返回值表示转换后的输�?
 */
class AgentEdge<TFrom, TTo>(
    val toNode: AgentNode<TTo, *>,
    private val condition: suspend AgentGraphContext.(output: TFrom) -> TTo?
) {
    /**
     * 尝试转发：如果条件匹配返回转换后的值，否则返回 null
     */
    suspend fun tryForward(context: AgentGraphContext, output: TFrom): TTo? {
        return condition(context, output)
    }
}

// ========== 边构建器 DSL ==========
// 移植�?koog �?AIAgentEdgeBuilderIntermediate / forwardTo / onCondition / transformed

/**
 * 中间边构建器
 * 移植�?koog �?AIAgentEdgeBuilderIntermediate�?
 * 支持链式调用 onCondition / transformed 来构建复杂的边条件�?
 */
class EdgeBuilder<TFrom, TIntermediate, TTo>(
    internal val fromNode: AgentNode<*, TFrom>,
    internal val toNode: AgentNode<TTo, *>,
    internal val forwardOutput: suspend AgentGraphContext.(TFrom) -> TIntermediate?
) {
    /**
     * 添加条件过滤
     * 移植�?koog �?onCondition
     */
    infix fun onCondition(
        block: suspend AgentGraphContext.(output: TIntermediate) -> Boolean
    ): EdgeBuilder<TFrom, TIntermediate, TTo> {
        val prevForward = forwardOutput
        return EdgeBuilder(fromNode, toNode) { output ->
            val intermediate = prevForward(output) ?: return@EdgeBuilder null
            if (block(intermediate)) intermediate else null
        }
    }

    /**
     * 数据变换
     * 移植�?koog �?transformed
     */
    infix fun <TNew> transformed(
        block: suspend AgentGraphContext.(TIntermediate) -> TNew
    ): EdgeBuilder<TFrom, TNew, TTo> {
        val prevForward = forwardOutput
        return EdgeBuilder(fromNode, toNode) { output ->
            val intermediate = prevForward(output) ?: return@EdgeBuilder null
            block(intermediate)
        }
    }

    /**
     * 构建最终的 AgentEdge
     */
    @Suppress("UNCHECKED_CAST")
    internal fun build(): AgentEdge<TFrom, TTo> {
        return AgentEdge(toNode) { output ->
            val result = forwardOutput(output) ?: return@AgentEdge null
            result as? TTo
        }
    }
}

/**
 * forwardTo 操作�?
 * 移植�?koog �?forwardTo，创建从一个节点到另一个节点的边构建器�?
 *
 * 使用方式�?
 * ```kotlin
 * edge(nodeA forwardTo nodeB)
 * edge(nodeA forwardTo nodeB onToolCall { true })
 * edge(nodeA forwardTo nodeFinish onAssistantMessage { true } transformed { it.content })
 * ```
 */
infix fun <TFrom, TTo> AgentNode<*, TFrom>.forwardTo(
    other: AgentNode<TTo, *>
): EdgeBuilder<TFrom, TFrom, TTo> {
    return EdgeBuilder(
        fromNode = this,
        toNode = other,
        forwardOutput = { output -> output }
    )
}

// ========== 边条件扩展函�?==========
// 移植�?koog �?AIAgentEdges.kt

/**
 * �?LLM 返回工具调用时匹�?
 * 移植�?koog �?onToolCall
 */
infix fun <TFrom, TTo> EdgeBuilder<TFrom, ChatResult, TTo>.onToolCall(
    block: suspend (ChatResult) -> Boolean
): EdgeBuilder<TFrom, ChatResult, TTo> {
    return onCondition { result ->
        result.hasToolCalls && block(result)
    }
}

/**
 * �?LLM 返回助手消息（无工具调用）时匹配
 * 移植�?koog �?onAssistantMessage
 */
infix fun <TFrom, TTo> EdgeBuilder<TFrom, ChatResult, TTo>.onAssistantMessage(
    block: suspend (ChatResult) -> Boolean
): EdgeBuilder<TFrom, ChatResult, TTo> {
    return onCondition { result ->
        !result.hasToolCalls && block(result)
    }
}

/**
 * 当工具执行结果为成功时匹�?
 */
infix fun <TFrom, TTo> EdgeBuilder<TFrom, ReceivedToolResult, TTo>.onToolSuccess(
    block: suspend (ReceivedToolResult) -> Boolean
): EdgeBuilder<TFrom, ReceivedToolResult, TTo> {
    return onCondition { result ->
        result.resultKind is ToolResultKind.Success && block(result)
    }
}

/**
 * 当工具执行结果为失败时匹�?
 */
infix fun <TFrom, TTo> EdgeBuilder<TFrom, ReceivedToolResult, TTo>.onToolFailure(
    block: suspend (ReceivedToolResult) -> Boolean
): EdgeBuilder<TFrom, ReceivedToolResult, TTo> {
    return onCondition { result ->
        result.resultKind is ToolResultKind.Failure && block(result)
    }
}

/**
 * 当工具执行结果为验证错误时匹�?
 */
infix fun <TFrom, TTo> EdgeBuilder<TFrom, ReceivedToolResult, TTo>.onToolValidationError(
    block: suspend (ReceivedToolResult) -> Boolean
): EdgeBuilder<TFrom, ReceivedToolResult, TTo> {
    return onCondition { result ->
        result.resultKind is ToolResultKind.ValidationError && block(result)
    }
}

// ========== 中优先级边条�?==========
// 移植�?koog �?AIAgentEdges.kt

/**
 * 按类型过滤输�?
 * 移植�?koog �?onIsInstance，使�?is 检�?+ as 转换�?
 *
 * 使用方式�?
 * ```kotlin
 * edge(nodeA forwardTo nodeB onIsInstance ChatResult::class)
 * ```
 *
 * @param klass 目标类型�?KClass（用�?reified 类型推断，实际过滤通过 reified T 完成�?
 */
@Suppress("unused")
inline infix fun <IncomingOutput, IntermediateOutput, OutgoingInput, reified T : Any> EdgeBuilder<IncomingOutput, IntermediateOutput, OutgoingInput>.onIsInstance(
    klass: KClass<T>
): EdgeBuilder<IncomingOutput, T, OutgoingInput> {
    return onCondition { output -> output is T }
        .transformed { it as T }
}

/**
 * 按工具名过滤工具调用
 * 移植�?koog �?onToolCall(tool: Tool)，适配 jasmine 的字符串工具名�?
 *
 * 使用方式�?
 * ```kotlin
 * edge(nodeCallLLM forwardTo nodeExecTool onToolCall "read_file")
 * ```
 *
 * @param toolName 工具名称
 */
infix fun <TFrom, TTo> EdgeBuilder<TFrom, ChatResult, TTo>.onToolCall(
    toolName: String
): EdgeBuilder<TFrom, ChatResult, TTo> {
    return onCondition { result ->
        result.hasToolCalls && result.toolCalls.any { it.name == toolName }
    }
}

/**
 * 按工具名+参数条件过滤工具调用
 * 移植�?koog �?onToolCall(tool: Tool, block: (Args) -> Boolean)，适配 jasmine 的字符串工具名�?
 *
 * koog 原版通过 tool.decodeArgs() 反序列化参数后判断，jasmine 适配为对 JSON 参数字符串的条件判断�?
 *
 * 使用方式�?
 * ```kotlin
 * edge(nodeCallLLM forwardTo nodeExecTool onToolCallWithArgs("read_file") { args -> args.contains("important") })
 * ```
 *
 * @param toolName 工具名称
 * @param block 条件函数，接收工具调用的 JSON 参数字符�?
 */
fun <TFrom, TTo> EdgeBuilder<TFrom, ChatResult, TTo>.onToolCallWithArgs(
    toolName: String,
    block: suspend (String) -> Boolean
): EdgeBuilder<TFrom, ChatResult, TTo> {
    return onCondition { result ->
        result.hasToolCalls && result.toolCalls.any { toolCall ->
            toolCall.name == toolName && block(toolCall.arguments)
        }
    }
}

/**
 * 排除指定工具的调�?
 * 移植�?koog �?onToolNotCalled(tool: Tool)，适配 jasmine 的字符串工具名�?
 *
 * @param toolName 要排除的工具名称
 */
infix fun <TFrom, TTo> EdgeBuilder<TFrom, ChatResult, TTo>.onToolNotCalled(
    toolName: String
): EdgeBuilder<TFrom, ChatResult, TTo> {
    return onCondition { result ->
        result.hasToolCalls && result.toolCalls.none { it.name == toolName }
    }
}

/**
 * 多工具调用列表过�?
 * 移植�?koog �?onMultipleToolCalls，用�?List<ChatResult> 类型的节点输出�?
 *
 * 从结果列表中提取所有包含工具调用的 ChatResult，如果非空则匹配�?
 *
 * @param block 条件函数，接收包含工具调用的 ChatResult 列表
 */
infix fun <TFrom, TTo> EdgeBuilder<TFrom, List<ChatResult>, TTo>.onMultipleToolCalls(
    block: suspend (List<ChatResult>) -> Boolean
): EdgeBuilder<TFrom, List<ChatResult>, TTo> {
    return transformed { results -> results.filter { it.hasToolCalls } }
        .onCondition { it.isNotEmpty() }
        .onCondition { toolCallResults -> block(toolCallResults) }
}

/**
 * 多助手消息过�?
 * 移植�?koog �?onMultipleAssistantMessages，用�?List<ChatResult> 类型的节点输出�?
 *
 * 从结果列表中提取所有不包含工具调用�?ChatResult（纯助手消息），如果非空则匹配�?
 *
 * @param block 条件函数，接收纯助手消息�?ChatResult 列表
 */
infix fun <TFrom, TTo> EdgeBuilder<TFrom, List<ChatResult>, TTo>.onMultipleAssistantMessages(
    block: suspend (List<ChatResult>) -> Boolean
): EdgeBuilder<TFrom, List<ChatResult>, TTo> {
    return transformed { results -> results.filter { !it.hasToolCalls } }
        .onCondition { it.isNotEmpty() }
        .onCondition { assistantResults -> block(assistantResults) }
}

/**
 * 多工具结果列表过�?
 * 移植�?koog �?onMultipleToolResults
 *
 * @param block 条件函数，接收工具执行结果列�?
 */
infix fun <TFrom, TTo> EdgeBuilder<TFrom, List<ReceivedToolResult>, TTo>.onMultipleToolResults(
    block: suspend (List<ReceivedToolResult>) -> Boolean
): EdgeBuilder<TFrom, List<ReceivedToolResult>, TTo> {
    return onCondition { it.isNotEmpty() }
        .onCondition { results -> block(results) }
}

/**
 * 推理消息过滤
 * 移植�?koog �?onReasoningMessage，适配 jasmine �?ChatResult.thinking�?
 *
 * �?ChatResult 包含 thinking 内容时匹配�?
 *
 * @param block 条件函数，接�?thinking 内容
 */
infix fun <TFrom, TTo> EdgeBuilder<TFrom, ChatResult, TTo>.onReasoningMessage(
    block: suspend (String) -> Boolean
): EdgeBuilder<TFrom, ChatResult, TTo> {
    return onCondition { result ->
        val t = result.thinking
        t != null && block(t)
    }
}

/**
 * 多推理消息过�?
 * 移植�?koog �?onMultipleReasoningMessages，用�?List<ChatResult> 类型的节点输出�?
 *
 * 从结果列表中提取所有包�?thinking �?ChatResult，如果非空则匹配�?
 *
 * @param block 条件函数，接收包�?thinking �?ChatResult 列表
 */
infix fun <TFrom, TTo> EdgeBuilder<TFrom, List<ChatResult>, TTo>.onMultipleReasoningMessages(
    block: suspend (List<ChatResult>) -> Boolean
): EdgeBuilder<TFrom, List<ChatResult>, TTo> {
    return transformed { results -> results.filter { it.thinking != null } }
        .onCondition { it.isNotEmpty() }
        .onCondition { reasoningResults -> block(reasoningResults) }
}

/**
 * 按工具名过滤工具执行结果
 * 移植�?koog �?onToolResult(tool, block)，适配 jasmine �?ReceivedToolResult�?
 *
 * �?ReceivedToolResult 的工具名匹配且条件满足时匹配�?
 *
 * 使用方式�?
 * ```kotlin
 * edge(nodeExecTool forwardTo nodeNext onToolResult("calculator") { result -> result.resultKind is ToolResultKind.Success })
 * ```
 *
 * @param toolName 工具名称
 * @param block 条件函数，接收匹配的 ReceivedToolResult
 */
fun <TFrom, TTo> EdgeBuilder<TFrom, ReceivedToolResult, TTo>.onToolResult(
    toolName: String,
    block: suspend (ReceivedToolResult) -> Boolean
): EdgeBuilder<TFrom, ReceivedToolResult, TTo> {
    return onCondition { result ->
        result.tool == toolName && block(result)
    }
}


// ========== Message.Response 版本的边条件 ==========
// 移植�?koog 的类型化消息系统

/**
 * �?Message.Response �?Assistant 消息时匹�?
 * 移植�?koog �?onAssistantMessage (Message.Response 版本)
 */
infix fun <TFrom, TTo> EdgeBuilder<TFrom, com.lhzkml.jasmine.core.prompt.model.Message.Response, TTo>.onAssistant(
    block: suspend (com.lhzkml.jasmine.core.prompt.model.Message.Assistant) -> Boolean
): EdgeBuilder<TFrom, com.lhzkml.jasmine.core.prompt.model.Message.Response, TTo> {
    return onCondition { response ->
        response is com.lhzkml.jasmine.core.prompt.model.Message.Assistant && block(response)
    }
}

/**
 * �?Message.Response �?Tool.Call 消息时匹�?
 * 移植�?koog �?onToolCall (Message.Response 版本)
 */
infix fun <TFrom, TTo> EdgeBuilder<TFrom, com.lhzkml.jasmine.core.prompt.model.Message.Response, TTo>.onToolCallMessage(
    block: suspend (com.lhzkml.jasmine.core.prompt.model.Message.Tool.Call) -> Boolean
): EdgeBuilder<TFrom, com.lhzkml.jasmine.core.prompt.model.Message.Response, TTo> {
    return onCondition { response ->
        response is com.lhzkml.jasmine.core.prompt.model.Message.Tool.Call && block(response)
    }
}

/**
 * �?Message.Response �?Reasoning 消息时匹�?
 * 移植�?koog �?onReasoningMessage (Message.Response 版本)
 */
infix fun <TFrom, TTo> EdgeBuilder<TFrom, com.lhzkml.jasmine.core.prompt.model.Message.Response, TTo>.onReasoning(
    block: suspend (com.lhzkml.jasmine.core.prompt.model.Message.Reasoning) -> Boolean
): EdgeBuilder<TFrom, com.lhzkml.jasmine.core.prompt.model.Message.Response, TTo> {
    return onCondition { response ->
        response is com.lhzkml.jasmine.core.prompt.model.Message.Reasoning && block(response)
    }
}
