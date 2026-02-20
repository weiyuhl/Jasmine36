package com.lhzkml.jasmine.core.agent.tools

import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.*

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

    // ========== 新增工具（移植自 AetherLink @aether/calculator） ==========

    /**
     * 数学表达式计算（支持科学函数）
     * 移植自 AetherLink calculate
     */
    val calculate = object : Tool() {
        override val descriptor = ToolDescriptor(
            name = "calculate",
            description = "Evaluates a math expression. Supports: +, -, *, /, %, ^, " +
                "sin, cos, tan, asin, acos, atan, sqrt, pow, log, ln, log10, log2, " +
                "abs, ceil, floor, round, exp, pi, e. Example: \"sin(30)\", \"pow(2,10)\", \"sqrt(16)\"",
            requiredParameters = listOf(
                ToolParameterDescriptor("expression", "Math expression", StringType)
            )
        )

        override suspend fun execute(arguments: String): String {
            val obj = json.parseToJsonElement(arguments).jsonObject
            val expr = obj["expression"]?.jsonPrimitive?.content
                ?: return "Error: Missing parameter 'expression'"
            return try {
                val result = evaluateExpression(expr)
                "Expression: $expr\nResult: ${formatNumber(result)}"
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
    }

    /**
     * 进制转换
     * 移植自 AetherLink convert_base
     */
    val convertBase = object : Tool() {
        override val descriptor = ToolDescriptor(
            name = "convert_base",
            description = "Converts a number between bases (2, 8, 10, 16).",
            requiredParameters = listOf(
                ToolParameterDescriptor("value", "Number value as string", StringType),
                ToolParameterDescriptor("fromBase", "Source base (2, 8, 10, 16)", IntegerType),
                ToolParameterDescriptor("toBase", "Target base (2, 8, 10, 16)", IntegerType)
            )
        )

        override suspend fun execute(arguments: String): String {
            val obj = json.parseToJsonElement(arguments).jsonObject
            val value = obj["value"]?.jsonPrimitive?.content
                ?: return "Error: Missing parameter 'value'"
            val fromBase = obj["fromBase"]?.jsonPrimitive?.int
                ?: return "Error: Missing parameter 'fromBase'"
            val toBase = obj["toBase"]?.jsonPrimitive?.int
                ?: return "Error: Missing parameter 'toBase'"

            if (fromBase !in listOf(2, 8, 10, 16) || toBase !in listOf(2, 8, 10, 16)) {
                return "Error: Only bases 2, 8, 10, 16 are supported"
            }

            return try {
                val decimal = value.toLong(fromBase)
                val result = when (toBase) {
                    2 -> decimal.toString(2)
                    8 -> decimal.toString(8)
                    16 -> decimal.toString(16).uppercase()
                    else -> decimal.toString()
                }
                "Input: $value (base $fromBase)\nDecimal: $decimal\nResult: $result (base $toBase)"
            } catch (e: NumberFormatException) {
                "Error: Invalid number '$value' for base $fromBase"
            }
        }
    }

    /**
     * 单位转换
     * 移植自 AetherLink convert_unit
     */
    val convertUnit = object : Tool() {
        override val descriptor = ToolDescriptor(
            name = "convert_unit",
            description = "Converts between units. Categories: length (mm,cm,m,km,inch,foot,yard,mile), " +
                "weight (mg,g,kg,ton,oz,lb), temperature (celsius/c,fahrenheit/f,kelvin/k), " +
                "area (sqmm,sqcm,sqm,sqkm,sqinch,sqfoot,sqyard,acre,hectare), " +
                "volume (ml,l,m3,gallon,quart,pint,cup,floz).",
            requiredParameters = listOf(
                ToolParameterDescriptor("value", "Numeric value to convert", FloatType),
                ToolParameterDescriptor("category", "Unit category",
                    EnumType(listOf("length", "weight", "temperature", "area", "volume"))),
                ToolParameterDescriptor("fromUnit", "Source unit", StringType),
                ToolParameterDescriptor("toUnit", "Target unit", StringType)
            )
        )

        override suspend fun execute(arguments: String): String {
            val obj = json.parseToJsonElement(arguments).jsonObject
            val value = obj["value"]?.jsonPrimitive?.double
                ?: return "Error: Missing parameter 'value'"
            val category = obj["category"]?.jsonPrimitive?.content
                ?: return "Error: Missing parameter 'category'"
            val fromUnit = obj["fromUnit"]?.jsonPrimitive?.content
                ?: return "Error: Missing parameter 'fromUnit'"
            val toUnit = obj["toUnit"]?.jsonPrimitive?.content
                ?: return "Error: Missing parameter 'toUnit'"

            return try {
                val result = when (category) {
                    "length" -> convertByFactor(value, fromUnit, toUnit, lengthFactors)
                    "weight" -> convertByFactor(value, fromUnit, toUnit, weightFactors)
                    "temperature" -> convertTemperature(value, fromUnit, toUnit)
                    "area" -> convertByFactor(value, fromUnit, toUnit, areaFactors)
                    "volume" -> convertByFactor(value, fromUnit, toUnit, volumeFactors)
                    else -> return "Error: Unsupported category '$category'"
                }
                "$value $fromUnit = ${formatNumber(result)} $toUnit"
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
    }

    /**
     * 统计计算
     * 移植自 AetherLink statistics
     */
    val statistics = object : Tool() {
        override val descriptor = ToolDescriptor(
            name = "statistics",
            description = "Calculates statistics for a list of numbers: count, sum, mean, median, " +
                "mode, variance, standard deviation, min, max, range.",
            requiredParameters = listOf(
                ToolParameterDescriptor("numbers", "Array of numbers, e.g. [1, 2, 3, 4, 5]",
                    ListType(FloatType))
            )
        )

        override suspend fun execute(arguments: String): String {
            val obj = json.parseToJsonElement(arguments).jsonObject
            val numbersArr = obj["numbers"]?.jsonArray
                ?: return "Error: Missing parameter 'numbers'"
            val numbers = numbersArr.map { it.jsonPrimitive.double }

            if (numbers.isEmpty()) return "Error: Empty number list"

            val sorted = numbers.sorted()
            val count = numbers.size
            val sum = numbers.sum()
            val mean = sum / count
            val median = if (count % 2 == 0) {
                (sorted[count / 2 - 1] + sorted[count / 2]) / 2.0
            } else {
                sorted[count / 2]
            }
            val variance = numbers.sumOf { (it - mean).pow(2) } / count
            val stdDev = sqrt(variance)
            val min = sorted.first()
            val max = sorted.last()
            val range = max - min
            val mode = findMode(numbers)

            return buildString {
                appendLine("Count: $count")
                appendLine("Sum: ${formatNumber(sum)}")
                appendLine("Mean: ${formatNumber(mean)}")
                appendLine("Median: ${formatNumber(median)}")
                appendLine("Mode: ${mode?.let { formatNumber(it) } ?: "none"}")
                appendLine("Variance: ${formatNumber(variance)}")
                appendLine("Std Dev: ${formatNumber(stdDev)}")
                appendLine("Min: ${formatNumber(min)}")
                appendLine("Max: ${formatNumber(max)}")
                append("Range: ${formatNumber(range)}")
            }
        }
    }

    // ========== 辅助方法 ==========

    private fun evaluateExpression(expr: String): Double {
        var e = expr.trim()
            .replace(Regex("\\bpi\\b", RegexOption.IGNORE_CASE), PI.toString())
            .replace(Regex("\\be\\b", RegexOption.IGNORE_CASE), E.toString())
            .replace(Regex("\\bln\\b"), "log")
        return ExprParser(e).parse()
    }

    private fun formatNumber(num: Double): String {
        return if (num == num.toLong().toDouble()) num.toLong().toString()
        else "%.10g".format(num)
    }

    private fun findMode(numbers: List<Double>): Double? {
        val freq = numbers.groupingBy { it }.eachCount()
        val maxFreq = freq.values.max()
        return if (maxFreq > 1) freq.entries.first { it.value == maxFreq }.key else null
    }

    // 单位转换因子表（转换到基准单位）
    private val lengthFactors = mapOf(
        "mm" to 0.001, "cm" to 0.01, "m" to 1.0, "km" to 1000.0,
        "inch" to 0.0254, "foot" to 0.3048, "yard" to 0.9144, "mile" to 1609.344
    )
    private val weightFactors = mapOf(
        "mg" to 0.000001, "g" to 0.001, "kg" to 1.0, "ton" to 1000.0,
        "oz" to 0.0283495, "lb" to 0.453592, "pound" to 0.453592
    )
    private val areaFactors = mapOf(
        "sqmm" to 0.000001, "sqcm" to 0.0001, "sqm" to 1.0, "sqkm" to 1000000.0,
        "sqinch" to 0.00064516, "sqfoot" to 0.092903, "sqyard" to 0.836127,
        "acre" to 4046.86, "hectare" to 10000.0
    )
    private val volumeFactors = mapOf(
        "ml" to 0.001, "l" to 1.0, "m3" to 1000.0,
        "gallon" to 3.78541, "quart" to 0.946353, "pint" to 0.473176,
        "cup" to 0.236588, "floz" to 0.0295735
    )

    private fun convertByFactor(value: Double, from: String, to: String, factors: Map<String, Double>): Double {
        val fromFactor = factors[from.lowercase()] ?: throw IllegalArgumentException("Unsupported unit: $from")
        val toFactor = factors[to.lowercase()] ?: throw IllegalArgumentException("Unsupported unit: $to")
        return (value * fromFactor) / toFactor
    }

    private fun convertTemperature(value: Double, from: String, to: String): Double {
        val celsius = when (from.lowercase()) {
            "celsius", "c" -> value
            "fahrenheit", "f" -> (value - 32) * 5.0 / 9.0
            "kelvin", "k" -> value - 273.15
            else -> throw IllegalArgumentException("Unsupported temperature unit: $from")
        }
        return when (to.lowercase()) {
            "celsius", "c" -> celsius
            "fahrenheit", "f" -> celsius * 9.0 / 5.0 + 32
            "kelvin", "k" -> celsius + 273.15
            else -> throw IllegalArgumentException("Unsupported temperature unit: $to")
        }
    }

    /**
     * 递归下降表达式解析器
     * 支持: +, -, *, /, %, ^, 括号, 科学函数
     */
    private class ExprParser(private val input: String) {
        private var pos = 0

        fun parse(): Double {
            val result = parseExpr()
            if (pos < input.length) throw IllegalArgumentException("Unexpected char at pos $pos: '${input[pos]}'")
            return result
        }

        private fun parseExpr(): Double {
            var left = parseTerm()
            while (pos < input.length) {
                skipSpaces()
                if (pos >= input.length) break
                val op = input[pos]
                if (op != '+' && op != '-') break
                pos++
                val right = parseTerm()
                left = if (op == '+') left + right else left - right
            }
            return left
        }

        private fun parseTerm(): Double {
            var left = parsePower()
            while (pos < input.length) {
                skipSpaces()
                if (pos >= input.length) break
                val op = input[pos]
                if (op != '*' && op != '/' && op != '%') break
                pos++
                val right = parsePower()
                left = when (op) {
                    '*' -> left * right
                    '/' -> { if (right == 0.0) throw ArithmeticException("Division by zero"); left / right }
                    '%' -> left % right
                    else -> left
                }
            }
            return left
        }

        private fun parsePower(): Double {
            var base = parseUnary()
            skipSpaces()
            if (pos < input.length && input[pos] == '^') {
                pos++
                val exp = parseUnary()
                base = base.pow(exp)
            }
            return base
        }

        private fun parseUnary(): Double {
            skipSpaces()
            if (pos < input.length && input[pos] == '-') {
                pos++; return -parseUnary()
            }
            if (pos < input.length && input[pos] == '+') {
                pos++; return parseUnary()
            }
            return parseAtom()
        }

        private fun parseAtom(): Double {
            skipSpaces()
            if (pos >= input.length) throw IllegalArgumentException("Unexpected end of expression")

            // 括号
            if (input[pos] == '(') {
                pos++
                val result = parseExpr()
                skipSpaces()
                if (pos < input.length && input[pos] == ')') pos++
                return result
            }

            // 函数名
            if (input[pos].isLetter()) {
                val start = pos
                while (pos < input.length && (input[pos].isLetterOrDigit() || input[pos] == '_')) pos++
                val name = input.substring(start, pos).lowercase()
                skipSpaces()
                if (pos < input.length && input[pos] == '(') {
                    pos++
                    val args = mutableListOf(parseExpr())
                    while (pos < input.length && input[pos] == ',') {
                        pos++; args.add(parseExpr())
                    }
                    skipSpaces()
                    if (pos < input.length && input[pos] == ')') pos++
                    return callFunction(name, args)
                }
                // 常量
                return when (name) {
                    "pi" -> PI; "e" -> E
                    else -> throw IllegalArgumentException("Unknown identifier: $name")
                }
            }

            // 数字
            val start = pos
            while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) pos++
            if (pos == start) throw IllegalArgumentException("Expected number at pos $pos")
            return input.substring(start, pos).toDouble()
        }

        private fun callFunction(name: String, args: List<Double>): Double {
            val a = args[0]
            return when (name) {
                "sin" -> sin(a); "cos" -> cos(a); "tan" -> tan(a)
                "asin" -> asin(a); "acos" -> acos(a); "atan" -> atan(a)
                "atan2" -> { if (args.size < 2) throw IllegalArgumentException("atan2 needs 2 args"); atan2(a, args[1]) }
                "sqrt" -> sqrt(a); "abs" -> abs(a)
                "ceil" -> ceil(a); "floor" -> floor(a); "round" -> round(a)
                "log", "ln" -> ln(a); "log10" -> log10(a); "log2" -> log2(a)
                "exp" -> exp(a)
                "pow" -> { if (args.size < 2) throw IllegalArgumentException("pow needs 2 args"); a.pow(args[1]) }
                "max" -> { if (args.size < 2) throw IllegalArgumentException("max needs 2 args"); max(a, args[1]) }
                "min" -> { if (args.size < 2) throw IllegalArgumentException("min needs 2 args"); min(a, args[1]) }
                else -> throw IllegalArgumentException("Unknown function: $name")
            }
        }

        private fun skipSpaces() { while (pos < input.length && input[pos] == ' ') pos++ }
    }

    fun allTools(): List<Tool> = listOf(plus, minus, multiply, divide,
        calculate, convertBase, convertUnit, statistics)
}
