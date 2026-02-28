package com.lhzkml.jasmine.core.agent.graph.graph

/**
 * 工具执行结果分类
 * 移植�?koog �?ToolResultKind，用于区分工具执行的成功、失败和验证错误�?
 *
 * - Success: 工具执行成功
 * - Failure: 工具执行失败（运行时错误、工具未找到等）
 * - ValidationError: 工具参数验证失败（可以让 LLM 重试�?
 */
sealed class ToolResultKind {
    object Success : ToolResultKind()
    data class Failure(val error: Throwable?) : ToolResultKind()
    data class ValidationError(val error: Throwable) : ToolResultKind()
}

/**
 * 工具执行结果
 * 移植�?koog �?ReceivedToolResult，包含工具调用的完整信息�?
 *
 * @param id 工具调用 ID
 * @param tool 工具名称
 * @param toolArgs 工具参数（原始字符串�?
 * @param toolDescription 工具描述
 * @param content 结果内容（字符串形式，发送给 LLM�?
 * @param resultKind 结果分类
 */
data class ReceivedToolResult(
    val id: String,
    val tool: String,
    val toolArgs: String,
    val toolDescription: String?,
    val content: String,
    val resultKind: ToolResultKind
) {
    /**
     * 转换�?Message.Tool.Result
     * 移植�?koog �?ReceivedToolResult.toMessage()
     */
    fun toMessage(): com.lhzkml.jasmine.core.prompt.model.Message.Tool.Result =
        com.lhzkml.jasmine.core.prompt.model.Message.Tool.Result(
            id = id,
            tool = tool,
            content = content
        )
}

/**
 * PromptBuilder.ToolMessageBuilder �?ReceivedToolResult 扩展
 * 移植�?koog �?PromptBuilder.ToolMessageBuilder.result(ReceivedToolResult)
 *
 * �?ReceivedToolResult 转换�?Message.Tool.Result 并添加到 prompt �?
 */
fun com.lhzkml.jasmine.core.prompt.model.PromptBuilder.ToolMessageBuilder.result(
    result: ReceivedToolResult
) {
    result(result.toMessage())
}
