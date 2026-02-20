package com.lhzkml.jasmine.core.agent.tools

import com.lhzkml.jasmine.core.prompt.model.ToolCall
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CalculatorToolTest {

    @Test
    fun `plus adds two numbers`() = runBlocking {
        assertEquals("30.0", CalculatorTool.plus.execute("""{"a": 10, "b": 20}"""))
    }

    @Test
    fun `minus subtracts`() = runBlocking {
        assertEquals("30.0", CalculatorTool.minus.execute("""{"a": 50, "b": 20}"""))
    }

    @Test
    fun `multiply`() = runBlocking {
        assertEquals("42.0", CalculatorTool.multiply.execute("""{"a": 6, "b": 7}"""))
    }

    @Test
    fun `divide`() = runBlocking {
        assertEquals("25.0", CalculatorTool.divide.execute("""{"a": 100, "b": 4}"""))
    }

    @Test
    fun `divide by zero`() = runBlocking {
        assertEquals("Error: Division by zero", CalculatorTool.divide.execute("""{"a": 10, "b": 0}"""))
    }

    @Test
    fun `execute ToolCall returns ToolResult`() = runBlocking {
        val call = ToolCall(id = "c1", name = "calculator_plus", arguments = """{"a": 3, "b": 4}""")
        val result = CalculatorTool.plus.execute(call)
        assertEquals("c1", result.callId)
        assertEquals("7.0", result.content)
    }

    @Test
    fun `allTools returns 8`() {
        assertEquals(8, CalculatorTool.allTools().size)
    }

    @Test
    fun `toJsonSchema valid`() {
        val schema = CalculatorTool.plus.descriptor.toJsonSchema()
        assertEquals("object", schema["type"].toString().trim('"'))
    }
}
