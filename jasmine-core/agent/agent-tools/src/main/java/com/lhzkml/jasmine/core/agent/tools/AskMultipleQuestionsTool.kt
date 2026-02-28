package com.lhzkml.jasmine.core.agent.tools

import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 多问题工具 - 最多同时问多个问题
 */
class AskMultipleQuestionsTool(
    private val onAsk: suspend (questions: List<String>) -> List<String>
) : Tool() {

    override val descriptor = ToolDescriptor(
        name = "ask_multiple_questions",
        description = "Ask user multiple questions at once and get all answers.",
        requiredParameters = listOf(
            ToolParameterDescriptor("questions", "List of questions to ask", ToolParameterType.ListType(ToolParameterType.StringType))
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val questions = obj["questions"]?.jsonArray?.map { it.jsonPrimitive.content }
            ?: return "Error: Missing parameter 'questions'"
        
        if (questions.isEmpty()) {
            return "Error: Questions list cannot be empty"
        }
        
        val answers = onAsk(questions)
        return questions.mapIndexed { index, question ->
            "Q${index + 1}: $question\nA${index + 1}: ${answers.getOrNull(index) ?: "(无回复)"}"
        }.joinToString("\n\n")
    }
}
