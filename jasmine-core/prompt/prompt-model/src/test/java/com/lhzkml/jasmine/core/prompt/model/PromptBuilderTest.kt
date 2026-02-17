package com.lhzkml.jasmine.core.prompt.model

import org.junit.Assert.*
import org.junit.Test

class PromptBuilderTest {

    @Test
    fun `build prompt with system and user messages`() {
        val p = Prompt.build("test") {
            system("You are helpful.")
            user("Hello!")
        }

        assertEquals("test", p.id)
        assertEquals(2, p.messages.size)
        assertEquals("system", p.messages[0].role)
        assertEquals("You are helpful.", p.messages[0].content)
        assertEquals("user", p.messages[1].role)
        assertEquals("Hello!", p.messages[1].content)
    }

    @Test
    fun `build prompt with all message types`() {
        val p = prompt("chat") {
            system("System prompt")
            user("User question")
            assistant("AI answer")
        }

        assertEquals(3, p.messages.size)
        assertEquals("system", p.messages[0].role)
        assertEquals("user", p.messages[1].role)
        assertEquals("assistant", p.messages[2].role)
    }

    @Test
    fun `build prompt with tool messages`() {
        val p = prompt("tool-test") {
            system("You can use tools.")
            user("Calculate 2+3")
            tool {
                call(id = "call_1", name = "calculator_plus", arguments = """{"a":2,"b":3}""")
                result(callId = "call_1", name = "calculator_plus", content = "5.0")
            }
            assistant("The result is 5.")
        }

        assertEquals(5, p.messages.size)
        assertEquals("system", p.messages[0].role)
        assertEquals("user", p.messages[1].role)
        // tool call is an assistant message with toolCalls
        assertEquals("assistant", p.messages[2].role)
        assertNotNull(p.messages[2].toolCalls)
        assertEquals("call_1", p.messages[2].toolCalls!![0].id)
        // tool result
        assertEquals("tool", p.messages[3].role)
        assertEquals("5.0", p.messages[3].content)
        assertEquals("call_1", p.messages[3].toolCallId)
        // final assistant
        assertEquals("assistant", p.messages[4].role)
    }

    @Test
    fun `append to existing prompt`() {
        val base = prompt("chat") {
            system("You are helpful.")
        }

        val extended = prompt(base) {
            user("What is 2+2?")
        }

        assertEquals(1, base.messages.size)
        assertEquals(2, extended.messages.size)
        assertEquals("user", extended.messages[1].role)
    }

    @Test
    fun `withMessages transforms messages`() {
        val p = prompt("test") {
            system("System")
            user("User 1")
            user("User 2")
        }

        val trimmed = p.withMessages { it.takeLast(2) }
        assertEquals(2, trimmed.messages.size)
        assertEquals("user", trimmed.messages[0].role)
    }

    @Test
    fun `withSamplingParams updates params`() {
        val p = prompt("test") { user("Hi") }
        val updated = p.withSamplingParams(SamplingParams(temperature = 0.5))
        assertEquals(0.5, updated.samplingParams.temperature!!, 0.001)
        assertNull(p.samplingParams.temperature)
    }

    @Test
    fun `withToolChoice updates tool choice`() {
        val p = prompt("test") { user("Hi") }
        val auto = p.withToolChoice(ToolChoice.Auto)
        val required = p.withToolChoice(ToolChoice.Required)
        val none = p.withToolChoice(ToolChoice.None)
        val named = p.withToolChoice(ToolChoice.Named("calculator"))

        assertTrue(auto.toolChoice is ToolChoice.Auto)
        assertTrue(required.toolChoice is ToolChoice.Required)
        assertTrue(none.toolChoice is ToolChoice.None)
        assertEquals("calculator", (named.toolChoice as ToolChoice.Named).toolName)
    }

    @Test
    fun `empty prompt`() {
        val p = Prompt.Empty
        assertTrue(p.messages.isEmpty())
        assertEquals("", p.id)
    }

    @Test
    fun `build with sampling params and max tokens`() {
        val p = Prompt.build(
            id = "configured",
            samplingParams = SamplingParams(temperature = 0.7, topP = 0.9),
            maxTokens = 1024
        ) {
            system("System")
            user("Hello")
        }

        assertEquals(0.7, p.samplingParams.temperature!!, 0.001)
        assertEquals(0.9, p.samplingParams.topP!!, 0.001)
        assertEquals(1024, p.maxTokens)
    }

    @Test
    fun `lastAssistantContent returns last assistant message`() {
        val p = prompt("test") {
            user("Q1")
            assistant("A1")
            user("Q2")
            assistant("A2")
        }
        assertEquals("A2", p.lastAssistantContent)
    }

    @Test
    fun `lastAssistantContent returns null when no assistant`() {
        val p = prompt("test") { user("Hello") }
        assertNull(p.lastAssistantContent)
    }
}
