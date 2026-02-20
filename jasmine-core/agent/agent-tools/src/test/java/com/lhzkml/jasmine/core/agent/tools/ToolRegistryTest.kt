package com.lhzkml.jasmine.core.agent.tools

import com.lhzkml.jasmine.core.prompt.model.ToolCall
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ToolRegistryTest {

    @Test
    fun `register and find`() {
        val registry = ToolRegistry()
        registry.register(CalculatorTool.plus)
        assertNotNull(registry.findTool("calculator_plus"))
        assertNull(registry.findTool("nonexistent"))
    }

    @Test
    fun `registerAll`() {
        val registry = ToolRegistry()
        registry.registerAll(*CalculatorTool.allTools().toTypedArray())
        assertEquals(8, registry.descriptors().size)
    }

    @Test
    fun `execute known tool`() = runBlocking {
        val registry = ToolRegistry.build { register(CalculatorTool.plus) }
        val result = registry.execute(ToolCall("c1", "calculator_plus", """{"a":1,"b":2}"""))
        assertEquals("3.0", result.content)
    }

    @Test
    fun `execute unknown tool`() = runBlocking {
        val result = ToolRegistry().execute(ToolCall("c1", "unknown", "{}"))
        assertTrue(result.content.contains("Error"))
    }

    @Test
    fun `executeAll`() = runBlocking {
        val registry = ToolRegistry.build { registerAll(*CalculatorTool.allTools().toTypedArray()) }
        val results = registry.executeAll(listOf(
            ToolCall("c1", "calculator_plus", """{"a":1,"b":2}"""),
            ToolCall("c2", "calculator_multiply", """{"a":3,"b":4}""")
        ))
        assertEquals(2, results.size)
        assertEquals("3.0", results[0].content)
        assertEquals("12.0", results[1].content)
    }

    @Test
    fun `build DSL`() {
        val registry = ToolRegistry.build {
            register(GetCurrentTimeTool)
            registerAll(*CalculatorTool.allTools().toTypedArray())
        }
        assertEquals(9, registry.allTools().size)
    }
}
