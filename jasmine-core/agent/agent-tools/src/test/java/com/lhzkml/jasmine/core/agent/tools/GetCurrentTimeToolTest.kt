package com.lhzkml.jasmine.core.agent.tools

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GetCurrentTimeToolTest {

    @Test
    fun `returns time with default timezone`() = runBlocking {
        val result = GetCurrentTimeTool.execute("{}")
        assertTrue(result.contains("\"currentTime\""))
        assertTrue(result.contains("\"format\": \"locale\""))
        assertTrue(result.contains("\"year\""))
        assertTrue(result.contains("\"weekday\""))
    }

    @Test
    fun `returns time for UTC`() = runBlocking {
        val result = GetCurrentTimeTool.execute("""{"timezone": "UTC"}""")
        assertTrue(result.contains("\"timezone\": \"UTC\""))
    }

    @Test
    fun `iso format`() = runBlocking {
        val result = GetCurrentTimeTool.execute("""{"format": "iso"}""")
        assertTrue(result.contains("\"format\": \"iso\""))
        // ISO 8601 contains T separator
        assertTrue(result.contains("T"))
    }

    @Test
    fun `timestamp format`() = runBlocking {
        val result = GetCurrentTimeTool.execute("""{"format": "timestamp"}""")
        assertTrue(result.contains("\"format\": \"timestamp\""))
        assertTrue(result.contains("\"milliseconds\""))
        assertTrue(result.contains("\"seconds\""))
    }

    @Test
    fun `invalid timezone returns error`() = runBlocking {
        assertTrue(GetCurrentTimeTool.execute("""{"timezone": "Invalid/Zone"}""").startsWith("Error:"))
    }

    @Test
    fun `empty arguments uses default`() = runBlocking {
        val result = GetCurrentTimeTool.execute("")
        assertTrue(result.contains("\"currentTime\""))
        assertTrue(result.contains("\"format\": \"locale\""))
    }

    @Test
    fun `descriptor name`() {
        assertEquals("get_current_time", GetCurrentTimeTool.name)
    }
}
