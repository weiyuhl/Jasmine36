package com.lhzkml.jasmine.core.agent.tools

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ServiceToolsTest {

    @Test
    fun `SayToUser sends message`() = runBlocking {
        var received: String? = null
        val tool = SayToUserTool { received = it }
        val result = tool.execute("""{"message": "hello"}""")
        assertEquals("DONE", result)
        assertEquals("hello", received)
    }

    @Test
    fun `SayToUser missing message`() = runBlocking {
        val tool = SayToUserTool {}
        assertTrue(tool.execute("{}").contains("Error"))
    }

    @Test
    fun `AskUser returns user response`() = runBlocking {
        val tool = AskUserTool { "user reply" }
        assertEquals("user reply", tool.execute("""{"message": "question?"}"""))
    }

    @Test
    fun `ExitTool returns DONE`() = runBlocking {
        assertEquals("DONE", ExitTool.execute("""{"message": "bye"}"""))
    }

    @Test
    fun `ExitTool descriptor`() {
        assertEquals("exit", ExitTool.name)
    }

    @Test
    fun `SayToUser descriptor`() {
        assertEquals("say_to_user", SayToUserTool().name)
    }

    @Test
    fun `AskUser descriptor`() {
        assertEquals("ask_user", AskUserTool().name)
    }
}
