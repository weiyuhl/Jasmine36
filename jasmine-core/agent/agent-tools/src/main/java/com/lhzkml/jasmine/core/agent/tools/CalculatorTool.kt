package com.lhzkml.jasmine.core.agent.tools

import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 计算器工具集
 * 参考 koog 的 CalculatorTools，提供基本四则运算
 */
object CalculatorTool {

    private val json = Json { ignoreUnknownKeys = true }

    private fun parseAB(arguments: String): Pair<Float, Float> {
        val obj = json.parseToJsonElement(arguments).jsonObject
        val a = obj["a"]?.jsonPrimitive?.float ?: throw IllegalArgumentException("Missing parameter 'a'")
        val b = obj["b"]?.jsonPrimitive?.float ?: throw IllegalArgumentException("Missing parameter 'b'")
        return a to b
    }

    private val abParams = listOf(
        ToolParameterDescriptor("a", "First number", ToolParameterType.FloatType),
        ToolParameterDescriptor("b", "Second number", ToolParameterType.FloatType)
    )

    val plus = object : Tool() {
        override val descriptor = ToolDescriptor(
            name = "calculator_plus", description = "Adds two numbers and returns the result",
            requiredParameters = abParams
        )
        override suspend fun execute(arguments: String): String {
            val (a, b) = parseAB(arguments); return (a + b).toString()
        }
    }

    val minus = object : Tool() {
        override val descriptor = ToolDescriptor(
            name = "calculator_minus", description = "Subtracts the second number from the first and returns the result",
            requiredParameters = abParams
        )
        override suspend fun execute(arguments: String): String {
            val (a, b) = parseAB(arguments); return (a - b).toString()
        }
    }

    val multiply = object : Tool() {
        override val descriptor = ToolDescriptor(
            name = "calculator_multiply", description = "Multiplies two numbers and returns the result",
            requiredParameters = abParams
        )
        override suspend fun execute(arguments: String): String {
            val (a, b) = parseAB(arguments); return (a * b).toString()
        }
    }

    val divide = object : Tool() {
        override val descriptor = ToolDescriptor(
            name = "calculator_divide", description = "Divides the first number by the second and returns the result",
            requiredParameters = abParams
        )
        override suspend fun execute(arguments: String): String {
            val (a, b) = parseAB(arguments)
            if (b == 0f) return "Error: Division by zero"
            return (a / b).toString()
        }
    }

    fun allTools(): List<Tool> = listOf(plus, minus, multiply, divide)
}
