package com.lhzkml.jasmine.core.agent.tools

import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 单选工具 - 从几个选项中选一个
 */
class SingleSelectTool(
    private val onSelect: suspend (question: String, options: List<String>) -> String
) : Tool() {

    override val descriptor = ToolDescriptor(
        name = "single_select",
        description = "Ask user to select one option from a list of choices.",
        requiredParameters = listOf(
            ToolParameterDescriptor("question", "Question to ask the user", ToolParameterType.StringType),
            ToolParameterDescriptor("options", "List of options to choose from", ToolParameterType.ListType(ToolParameterType.StringType))
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val question = obj["question"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'question'"
        val options = obj["options"]?.jsonArray?.map { it.jsonPrimitive.content }
            ?: return "Error: Missing parameter 'options'"
        
        if (options.isEmpty()) {
            return "Error: Options list cannot be empty"
        }
        
        return onSelect(question, options)
    }
}
