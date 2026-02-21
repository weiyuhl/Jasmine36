package com.lhzkml.jasmine.core.agent.tools.graph

import com.lhzkml.jasmine.core.prompt.model.ChatResult

/**
 * Agent 图的有向边
 * 移植自 koog 的 AIAgentEdge，连接两个节点，支持条件转发和数据变换。
 *
 * @param toNode 目标节点
 * @param condition 条件函数，返回 null 表示不匹配，返回值表示转换后的输入
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
// 移植自 koog 的 AIAgentEdgeBuilderIntermediate / forwardTo / onCondition / transformed

/**
 * 中间边构建器
 * 移植自 koog 的 AIAgentEdgeBuilderIntermediate，
 * 支持链式调用 onCondition / transformed 来构建复杂的边条件。
 */
class EdgeBuilder<TFrom, TIntermediate, TTo>(
    internal val fromNode: AgentNode<*, TFrom>,
    internal val toNode: AgentNode<TTo, *>,
    internal val forwardOutput: suspend AgentGraphContext.(TFrom) -> TIntermediate?
) {
    /**
     * 添加条件过滤
     * 移植自 koog 的 onCondition
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
     * 移植自 koog 的 transformed
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
 * forwardTo 操作符
 * 移植自 koog 的 forwardTo，创建从一个节点到另一个节点的边构建器。
 *
 * 使用方式：
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

// ========== 边条件扩展函数 ==========
// 移植自 koog 的 AIAgentEdges.kt

/**
 * 当 LLM 返回工具调用时匹配
 * 移植自 koog 的 onToolCall
 */
infix fun <TFrom, TTo> EdgeBuilder<TFrom, ChatResult, TTo>.onToolCall(
    block: suspend (ChatResult) -> Boolean
): EdgeBuilder<TFrom, ChatResult, TTo> {
    return onCondition { result ->
        result.hasToolCalls && block(result)
    }
}

/**
 * 当 LLM 返回助手消息（无工具调用）时匹配
 * 移植自 koog 的 onAssistantMessage
 */
infix fun <TFrom, TTo> EdgeBuilder<TFrom, ChatResult, TTo>.onAssistantMessage(
    block: suspend (ChatResult) -> Boolean
): EdgeBuilder<TFrom, ChatResult, TTo> {
    return onCondition { result ->
        !result.hasToolCalls && block(result)
    }
}

/**
 * 当工具执行结果为成功时匹配
 */
infix fun <TFrom, TTo> EdgeBuilder<TFrom, ReceivedToolResult, TTo>.onToolSuccess(
    block: suspend (ReceivedToolResult) -> Boolean
): EdgeBuilder<TFrom, ReceivedToolResult, TTo> {
    return onCondition { result ->
        result.resultKind is ToolResultKind.Success && block(result)
    }
}

/**
 * 当工具执行结果为失败时匹配
 */
infix fun <TFrom, TTo> EdgeBuilder<TFrom, ReceivedToolResult, TTo>.onToolFailure(
    block: suspend (ReceivedToolResult) -> Boolean
): EdgeBuilder<TFrom, ReceivedToolResult, TTo> {
    return onCondition { result ->
        result.resultKind is ToolResultKind.Failure && block(result)
    }
}

/**
 * 当工具执行结果为验证错误时匹配
 */
infix fun <TFrom, TTo> EdgeBuilder<TFrom, ReceivedToolResult, TTo>.onToolValidationError(
    block: suspend (ReceivedToolResult) -> Boolean
): EdgeBuilder<TFrom, ReceivedToolResult, TTo> {
    return onCondition { result ->
        result.resultKind is ToolResultKind.ValidationError && block(result)
    }
}
