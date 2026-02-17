package com.lhzkml.jasmine.core.agent.tools

import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 参考 koog 的 AskUser，允许 agent 向用户提问并等待回复
 */
class AskUserTool(
    private val onAsk: suspend (String) -> String = { message ->
        println(message)
        readln()
    }
) : Tool() {

    override val descriptor = ToolDescriptor(
        name = "ask_user",
        description = "Service tool, used by the agent to ask the user a question and wait for a response.",
        requiredParameters = listOf(
            ToolParameterDescriptor("message", "Message from the agent", ToolParameterType.StringType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val message = obj["message"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'message'"
        return onAsk(message)
    }
}
