package com.lhzkml.jasmine.core.agent.tools

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GetCurrentTimeToolTest {

    @Test
    fun `returns time with default timezone`() = runBlocking {
        val result = GetCurrentTimeTool.execute("{}")
        assertTrue(result.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} \\(.+\\)")))
    }

    @Test
    fun `returns time for UTC`() = runBlocking {
        val result = GetCurrentTimeTool.execute("""{"timezone": "UTC"}""")
        assertTrue(result.contains("(UTC)"))
    }

    @Test
    fun `invalid timezone returns error`() = runBlocking {
        assertTrue(GetCurrentTimeTool.execute("""{"timezone": "Invalid/Zone"}""").startsWith("Error:"))
    }

    @Test
    fun `empty arguments uses default`() = runBlocking {
        val result = GetCurrentTimeTool.execute("")
        assertTrue(result.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} \\(.+\\)")))
    }

    @Test
    fun `descriptor name`() {
        assertEquals("get_current_time", GetCurrentTimeTool.name)
    }
}
