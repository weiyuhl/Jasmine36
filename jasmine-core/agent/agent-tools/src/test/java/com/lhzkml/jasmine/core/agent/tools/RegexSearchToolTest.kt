package com.lhzkml.jasmine.core.agent.tools

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RegexSearchToolTest {

    private suspend fun withTempDir(block: suspend (File) -> Unit) {
        val dir = File(System.getProperty("java.io.tmpdir"), "jasmine-search-${System.nanoTime()}")
        dir.mkdirs()
        try { block(dir) } finally { dir.deleteRecursively() }
    }

    @Test
    fun `search finds matches`() = runBlocking {
        withTempDir { dir ->
            File(dir, "a.kt").writeText("fun hello() {\n  println(\"hello\")\n}")
            File(dir, "b.kt").writeText("val x = 42")
            val tool = RegexSearchTool(dir.absolutePath)
            val result = tool.execute("""{"path": "${dir.absolutePath.replace("\\", "\\\\")}", "regex": "hello"}""")
            assertTrue(result.contains("a.kt"))
            assertFalse(result.contains("b.kt"))
        }
    }

    @Test
    fun `search no matches`() = runBlocking {
        withTempDir { dir ->
            File(dir, "a.txt").writeText("nothing here")
            val tool = RegexSearchTool(dir.absolutePath)
            val result = tool.execute("""{"path": "${dir.absolutePath.replace("\\", "\\\\")}", "regex": "xyz123"}""")
            assertEquals("No matches found.", result)
        }
    }

    @Test
    fun `search invalid regex`() = runBlocking {
        val tool = RegexSearchTool()
        val result = tool.execute("""{"path": "/tmp", "regex": "[invalid"}""")
        assertTrue(result.contains("Error"))
    }

    @Test
    fun `descriptor name`() {
        assertEquals("search_by_regex", RegexSearchTool().name)
    }
}
