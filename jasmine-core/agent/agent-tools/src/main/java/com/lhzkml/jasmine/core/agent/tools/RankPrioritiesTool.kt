package com.lhzkml.jasmine.core.agent.tools

import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 排序工具 - 把选项手动排优先级
 */
class RankPrioritiesTool(
    private val onRank: suspend (question: String, items: List<String>) -> List<String>
) : Tool() {

    override val descriptor = ToolDescriptor(
        name = "rank_priorities",
        description = "Ask user to rank items by priority (drag to reorder).",
        requiredParameters = listOf(
            ToolParameterDescriptor("question", "Question to ask the user", ToolParameterType.StringType),
            ToolParameterDescriptor("items", "List of items to rank", ToolParameterType.ListType(ToolParameterType.StringType))
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val question = obj["question"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'question'"
        val items = obj["items"]?.jsonArray?.map { it.jsonPrimitive.content }
            ?: return "Error: Missing parameter 'items'"
        
        if (items.isEmpty()) {
            return "Error: Items list cannot be empty"
        }
        
        val ranked = onRank(question, items)
        return ranked.mapIndexed { index, item -> "${index + 1}. $item" }.joinToString("\n")
    }
}
