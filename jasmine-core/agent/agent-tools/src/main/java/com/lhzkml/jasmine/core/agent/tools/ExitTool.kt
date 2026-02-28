package com.lhzkml.jasmine.core.agent.tools

import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 参考 koog 的 ExitTool，用于结束对话
 * 
 * 当 Agent 调用此工具时，会抛出 ExitSignalException 来停止生成。
 */
object ExitTool : Tool() {

    override val descriptor = ToolDescriptor(
        name = "exit",
        description = "Service tool, used by the agent to end conversation on user request or agent decision.",
        requiredParameters = listOf(
            ToolParameterDescriptor("message", "Final message of the agent", ToolParameterType.StringType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val message = obj["message"]?.jsonPrimitive?.content ?: "对话已结束"
        throw ExitSignalException(message)
    }
}

/**
 * Exit 信号异常
 * 当 Agent 调用 exit 工具时抛出，用于停止生成并显示结束消息。
 */
class ExitSignalException(val finalMessage: String) : Exception("Agent exit: $finalMessage")

