package com.lhzkml.jasmine.core.agent.tools

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ExecuteShellCommandToolTest {

    @Test
    fun `descriptor name`() {
        assertEquals("execute_shell_command", ExecuteShellCommandTool().name)
    }

    @Test
    fun `denied command`() = runBlocking {
        val tool = ExecuteShellCommandTool(confirmationHandler = { _, _, _ -> false })
        val result = tool.execute("""{"command": "echo hi", "purpose": "test echo", "timeoutSeconds": 5}""")
        assertTrue(result.contains("denied"))
    }

    @Test
    fun `execute echo command`() = runBlocking {
        val tool = ExecuteShellCommandTool()
        val result = tool.execute("""{"command": "echo hello", "purpose": "test echo output", "timeoutSeconds": 10}""")
        assertTrue(result.contains("hello"))
        assertTrue(result.contains("Exit code: 0"))
    }

    @Test
    fun `missing command param`() = runBlocking {
        val tool = ExecuteShellCommandTool()
        assertTrue(tool.execute("{}").contains("Error"))
    }

    @Test
    fun `missing purpose param`() = runBlocking {
        val tool = ExecuteShellCommandTool()
        val result = tool.execute("""{"command": "echo test", "timeoutSeconds": 5}""")
        assertTrue(result.contains("Error"))
        assertTrue(result.contains("purpose"))
    }

    @Test
    fun `purpose shown in output`() = runBlocking {
        val tool = ExecuteShellCommandTool()
        val result = tool.execute("""{"command": "echo test", "purpose": "verify echo works", "timeoutSeconds": 5}""")
        assertTrue(result.contains("Purpose: verify echo works"))
    }

    @Test
    fun `background mode returns immediately`() = runBlocking {
        val tool = ExecuteShellCommandTool()
        val result = tool.execute("""{"command": "echo bg", "purpose": "test background", "background": true}""")
        assertTrue(result.contains("background"))
    }
}
