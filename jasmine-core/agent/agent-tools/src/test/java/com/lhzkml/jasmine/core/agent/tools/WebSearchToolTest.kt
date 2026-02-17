package com.lhzkml.jasmine.core.agent.tools

import org.junit.Assert.*
import org.junit.Test

class WebSearchToolTest {

    @Test
    fun `search descriptor`() {
        val tool = WebSearchTool("test-key")
        assertEquals("web_search", tool.search.name)
        assertEquals(1, tool.search.descriptor.requiredParameters.size)
        tool.close()
    }

    @Test
    fun `scrape descriptor`() {
        val tool = WebSearchTool("test-key")
        assertEquals("web_scrape", tool.scrape.name)
        assertEquals(1, tool.scrape.descriptor.requiredParameters.size)
        tool.close()
    }

    @Test
    fun `allTools returns 2`() {
        val tool = WebSearchTool("test-key")
        assertEquals(2, tool.allTools().size)
        tool.close()
    }

    @Test
    fun `toJsonSchema valid`() {
        val tool = WebSearchTool("test-key")
        val schema = tool.search.descriptor.toJsonSchema()
        assertEquals("object", schema["type"].toString().trim('"'))
        tool.close()
    }
}
