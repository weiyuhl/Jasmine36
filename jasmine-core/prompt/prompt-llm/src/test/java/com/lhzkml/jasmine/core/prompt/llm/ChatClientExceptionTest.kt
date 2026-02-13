package com.lhzkml.jasmine.core.prompt.llm

import org.junit.Assert.*
import org.junit.Test

class ChatClientExceptionTest {

    @Test
    fun `message includes provider name`() {
        val ex = ChatClientException("DeepSeek", "连接超时")
        assertTrue(ex.message!!.contains("DeepSeek"))
        assertTrue(ex.message!!.contains("连接超时"))
    }

    @Test
    fun `provider name is accessible`() {
        val ex = ChatClientException("SiliconFlow", "error")
        assertEquals("SiliconFlow", ex.providerName)
    }

    @Test
    fun `cause is preserved`() {
        val cause = RuntimeException("network error")
        val ex = ChatClientException("Test", "failed", cause)
        assertSame(cause, ex.cause)
    }

    @Test
    fun `cause defaults to null`() {
        val ex = ChatClientException("Test", "failed")
        assertNull(ex.cause)
    }
}
