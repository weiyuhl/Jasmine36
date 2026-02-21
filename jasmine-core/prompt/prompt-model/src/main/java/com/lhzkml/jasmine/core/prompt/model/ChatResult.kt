package com.lhzkml.jasmine.core.prompt.model

/**
 * 聊天结果，包含回复内容和 token 用量
 * @param content 助手回复的文本内容
 * @param usage token 用量统计，可能为 null（流式模式下部分供应商不返回）
 * @param finishReason 完成原因，如 "stop"、"length"、"tool_calls" 等
 * @param toolCalls LLM 请求的工具调用列表，为空表示无工具调用
 */
data class ChatResult(
    val content: String,
    val usage: Usage? = null,
    val finishReason: String? = null,
    val toolCalls: List<ToolCall> = emptyList(),
    /** 思考/推理过程内容（Claude extended thinking 等） */
    val thinking: String? = null
) {
    /** 是否包含工具调用 */
    val hasToolCalls: Boolean get() = toolCalls.isNotEmpty()
}


/**
 * 将 ChatResult 转换为 Message.Response
 *
 * 转换逻辑:
 * - 有 thinking -> Reasoning 消息
 * - 有 toolCalls -> Assistant 消息 (toolCalls 信息保留在 finishReason 中)
 * - 其他 -> Assistant 消息
 */
fun ChatResult.toAssistantMessage(): Message.Response {
    val responseMetaInfo = ResponseMetaInfo(
        totalTokensCount = usage?.totalTokens,
        inputTokensCount = usage?.promptTokens,
        outputTokensCount = usage?.completionTokens
    )
    return if (thinking != null) {
        Message.Reasoning(
            content = thinking,
            metaInfo = responseMetaInfo
        )
    } else {
        Message.Assistant(
            content = content,
            metaInfo = responseMetaInfo,
            finishReason = finishReason
        )
    }
}

/**
 * 将 ChatResult 转换为完整的 Message.Response 列表
 * 包含 thinking (如果有) + assistant 消息 + tool calls (如果有)
 */
fun ChatResult.toMessages(): List<Message.Response> {
    val messages = mutableListOf<Message.Response>()
    val responseMetaInfo = ResponseMetaInfo(
        totalTokensCount = usage?.totalTokens,
        inputTokensCount = usage?.promptTokens,
        outputTokensCount = usage?.completionTokens
    )

    // 推理消息
    if (thinking != null) {
        messages.add(Message.Reasoning(content = thinking, metaInfo = responseMetaInfo))
    }

    // 助手消息
    messages.add(Message.Assistant(
        content = content,
        metaInfo = responseMetaInfo,
        finishReason = finishReason
    ))

    // 工具调用消息
    for (tc in toolCalls) {
        messages.add(Message.Tool.Call(
            id = tc.id,
            tool = tc.name,
            content = tc.arguments,
            metaInfo = responseMetaInfo
        ))
    }

    return messages
}
