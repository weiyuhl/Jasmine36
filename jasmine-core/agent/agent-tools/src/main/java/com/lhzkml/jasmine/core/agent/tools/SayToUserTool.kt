package com.lhzkml.jasmine.core.agent.tools

import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 参考 koog 的 SayToUser，允许 agent 向用户输出消息
 */
class SayToUserTool(
    private val onMessage: (String) -> Unit = { println("Agent says: $it") }
) : Tool() {

    override val descriptor = ToolDescriptor(
        name = "say_to_user",
        description = "Service tool, used by the agent to talk.",
        requiredParameters = listOf(
            ToolParameterDescriptor("message", "Message from the agent", ToolParameterType.StringType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val message = obj["message"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'message'"
        onMessage(message)
        return "DONE"
    }
}
