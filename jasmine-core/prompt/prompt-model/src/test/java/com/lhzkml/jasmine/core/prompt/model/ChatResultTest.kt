package com.lhzkml.jasmine.core.prompt.model

import org.junit.Assert.*
import org.junit.Test

class ChatResultTest {

    @Test
    fun `ChatResult with usage`() {
        val usage = Usage(promptTokens = 10, completionTokens = 20, totalTokens = 30)
        val result = ChatResult(content = "Hello", usage = usage)
        assertEquals("Hello", result.content)
        assertEquals(10, result.usage?.promptTokens)
        assertEquals(20, result.usage?.completionTokens)
        assertEquals(30, result.usage?.totalTokens)
    }

    @Test
    fun `ChatResult without usage`() {
        val result = ChatResult(content = "Hello")
        assertEquals("Hello", result.content)
        assertNull(result.usage)
    }

    @Test
    fun `ChatResult equality`() {
        val usage = Usage(10, 20, 30)
        val a = ChatResult("hi", usage)
        val b = ChatResult("hi", usage)
        assertEquals(a, b)
    }
}
