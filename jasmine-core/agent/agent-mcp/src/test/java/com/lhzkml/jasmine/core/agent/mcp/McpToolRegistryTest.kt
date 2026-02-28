package com.lhzkml.jasmine.core.agent.mcp

import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class McpToolRegistryTest {

    private fun stubClient(results: Map<String, String> = emptyMap()): McpClient = object : McpClient {
        override suspend fun connect() {}
        override suspend fun listTools(): List<McpToolDefinition> = emptyList()
        override suspend fun callTool(name: String, arguments: String): McpToolResult {
            return McpToolResult(content = results[name] ?: "unknown")
        }
        override fun close() {}
    }

    private fun createRegistry(vararg names: String): McpToolRegistry {
        val client = stubClient(names.associate { it to "result_$it" })
        val tools = names.map { name ->
            McpTool(client, ToolDescriptor(name, "desc_$name", emptyList(), emptyList()))
        }
        val descriptors = tools.map { it.descriptor }
        return McpToolRegistry(tools, descriptors)
    }

    @Test
    fun `descriptors returns all tool descriptors`() {
        val registry = createRegistry("search", "calc")
        assertEquals(2, registry.descriptors().size)
        assertEquals("search", registry.descriptors()[0].name)
    }

    @Test
    fun `findTool returns tool by name`() {
        val registry = createRegistry("search", "calc")
        assertNotNull(registry.findTool("search"))
        assertNull(registry.findTool("nonexistent"))
    }

    @Test
    fun `execute calls correct tool`() = runBlocking {
        val registry = createRegistry("search", "calc")
        val result = registry.execute("search", "{}")
        assertEquals("result_search", result.content)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `execute throws for unknown tool`() = runBlocking {
        val registry = createRegistry("search")
        registry.execute("nonexistent", "{}")
        Unit
    }

    @Test
    fun `size returns tool count`() {
        assertEquals(0, createRegistry().size)
        assertEquals(3, createRegistry("a", "b", "c").size)
    }
}
