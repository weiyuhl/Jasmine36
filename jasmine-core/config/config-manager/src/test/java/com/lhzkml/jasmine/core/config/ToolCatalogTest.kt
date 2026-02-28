package com.lhzkml.jasmine.core.config

import org.junit.Assert.*
import org.junit.Test

class ToolCatalogTest {

    @Test
    fun `allTools is not empty`() {
        assertTrue(ToolCatalog.allTools.isNotEmpty())
    }

    @Test
    fun `all tools have unique ids`() {
        val ids = ToolCatalog.allTools.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `all tools have non-empty descriptions`() {
        for (tool in ToolCatalog.allTools) {
            assertTrue("Tool ${tool.id} has empty description", tool.description.isNotEmpty())
        }
    }

    @Test
    fun `contains expected core tools`() {
        val ids = ToolCatalog.allTools.map { it.id }.toSet()
        assertTrue("calculator" in ids)
        assertTrue("execute_shell_command" in ids)
        assertTrue("file_tools" in ids)
    }
}
