package com.lhzkml.jasmine.core.config

import org.junit.Assert.*
import org.junit.Test

class AgentConfigTest {

    @Test
    fun `AgentStrategyType has expected values`() {
        assertEquals(2, AgentStrategyType.values().size)
        assertNotNull(AgentStrategyType.valueOf("SIMPLE_LOOP"))
        assertNotNull(AgentStrategyType.valueOf("SINGLE_RUN_GRAPH"))
    }

    @Test
    fun `GraphToolCallMode has expected values`() {
        assertEquals(3, GraphToolCallMode.values().size)
        assertNotNull(GraphToolCallMode.valueOf("SEQUENTIAL"))
        assertNotNull(GraphToolCallMode.valueOf("PARALLEL"))
        assertNotNull(GraphToolCallMode.valueOf("SINGLE_RUN_SEQUENTIAL"))
    }

    @Test
    fun `ToolSelectionStrategyType has expected values`() {
        assertEquals(4, ToolSelectionStrategyType.values().size)
    }

    @Test
    fun `ToolChoiceMode has expected values`() {
        assertEquals(5, ToolChoiceMode.values().size)
    }

    @Test
    fun `SnapshotStorageType has expected values`() {
        assertEquals(2, SnapshotStorageType.values().size)
        assertNotNull(SnapshotStorageType.valueOf("MEMORY"))
        assertNotNull(SnapshotStorageType.valueOf("FILE"))
    }

    @Test
    fun `McpTransportType has expected values`() {
        assertEquals(2, McpTransportType.values().size)
        assertNotNull(McpTransportType.valueOf("STREAMABLE_HTTP"))
        assertNotNull(McpTransportType.valueOf("SSE"))
    }

    @Test
    fun `McpServerConfig defaults`() {
        val config = McpServerConfig(name = "test", url = "http://localhost:8080")
        assertEquals(McpTransportType.STREAMABLE_HTTP, config.transportType)
        assertEquals("", config.headerName)
        assertEquals("", config.headerValue)
        assertTrue(config.enabled)
    }

    @Test
    fun `ProviderConfig data class`() {
        val config = ProviderConfig("id1", "Name", "https://api.example.com", "model-1")
        assertEquals("id1", config.id)
        assertFalse(config.isCustom)
        val custom = config.copy(isCustom = true)
        assertTrue(custom.isCustom)
    }
}
