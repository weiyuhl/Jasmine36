package com.lhzkml.jasmine.core.agent.tools

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FileToolsTest {

    private suspend fun withTempDir(block: suspend (File) -> Unit) {
        val dir = File(System.getProperty("java.io.tmpdir"), "jasmine-test-${System.nanoTime()}")
        dir.mkdirs()
        try { block(dir) } finally { dir.deleteRecursively() }
    }

    @Test
    fun `read file returns content`() = runBlocking {
        withTempDir { dir ->
            val f = File(dir, "test.txt")
            f.writeText("line0\nline1\nline2\nline3")
            val tool = ReadFileTool(dir.absolutePath)
            val result = tool.execute("""{"path": "${f.absolutePath.replace("\\", "\\\\")}"}""")
            assertTrue(result.contains("line0"))
            assertTrue(result.contains("line3"))
        }
    }

    @Test
    fun `read file with line range`() = runBlocking {
        withTempDir { dir ->
            val f = File(dir, "test.txt")
            f.writeText("a\nb\nc\nd\ne")
            val tool = ReadFileTool(dir.absolutePath)
            val result = tool.execute("""{"path": "${f.absolutePath.replace("\\", "\\\\")}", "startLine": 1, "endLine": 3}""")
            assertTrue(result.contains("b"))
            assertTrue(result.contains("c"))
            assertFalse(result.contains("0: a"))
        }
    }

    @Test
    fun `read nonexistent file`() = runBlocking {
        val tool = ReadFileTool()
        assertTrue(tool.execute("""{"path": "/nonexistent/file.txt"}""").contains("Error"))
    }

    @Test
    fun `write file creates file`() = runBlocking {
        withTempDir { dir ->
            val path = File(dir, "sub/new.txt").absolutePath.replace("\\", "\\\\")
            val tool = WriteFileTool(dir.absolutePath)
            val result = tool.execute("""{"path": "$path", "content": "hello world"}""")
            assertTrue(result.contains("Written"))
            assertEquals("hello world", File(dir, "sub/new.txt").readText())
        }
    }

    @Test
    fun `write empty content rejected`() = runBlocking {
        val tool = WriteFileTool()
        assertTrue(tool.execute("""{"path": "/tmp/x.txt", "content": ""}""").contains("Error"))
    }

    @Test
    fun `edit file exact match`() = runBlocking {
        withTempDir { dir ->
            val f = File(dir, "edit.txt")
            f.writeText("hello world")
            val tool = EditFileTool(dir.absolutePath)
            val result = tool.execute("""{"path": "${f.absolutePath.replace("\\", "\\\\")}", "original": "world", "replacement": "jasmine"}""")
            assertTrue(result.contains("Successfully"))
            assertEquals("hello jasmine", f.readText())
        }
    }

    @Test
    fun `edit file create new`() = runBlocking {
        withTempDir { dir ->
            val path = File(dir, "new.txt").absolutePath.replace("\\", "\\\\")
            val tool = EditFileTool(dir.absolutePath)
            val result = tool.execute("""{"path": "$path", "original": "", "replacement": "new content"}""")
            assertTrue(result.contains("Created"))
            assertEquals("new content", File(dir, "new.txt").readText())
        }
    }

    @Test
    fun `list directory`() = runBlocking {
        withTempDir { dir ->
            File(dir, "a.txt").writeText("a")
            File(dir, "sub").mkdirs()
            File(dir, "sub/b.txt").writeText("b")
            val tool = ListDirectoryTool(dir.absolutePath)
            val result = tool.execute("""{"path": "${dir.absolutePath.replace("\\", "\\\\")}", "depth": 2}""")
            assertTrue(result.contains("a.txt"))
            assertTrue(result.contains("sub/"))
            assertTrue(result.contains("b.txt"))
        }
    }

    @Test
    fun `list nonexistent dir`() = runBlocking {
        val tool = ListDirectoryTool()
        assertTrue(tool.execute("""{"path": "/nonexistent/dir"}""").contains("Error"))
    }
}
