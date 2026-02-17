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
