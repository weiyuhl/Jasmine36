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
        val tool = ExecuteShellCommandTool(confirmationHandler = { _, _ -> false })
        val result = tool.execute("""{"command": "echo hi", "timeoutSeconds": 5}""")
        assertTrue(result.contains("denied"))
    }

    @Test
    fun `execute echo command`() = runBlocking {
        val tool = ExecuteShellCommandTool()
        val result = tool.execute("""{"command": "echo hello", "timeoutSeconds": 10}""")
        assertTrue(result.contains("hello"))
        assertTrue(result.contains("Exit code: 0"))
    }

    @Test
    fun `missing command param`() = runBlocking {
        val tool = ExecuteShellCommandTool()
        assertTrue(tool.execute("{}").contains("Error"))
    }
}
