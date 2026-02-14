package com.lhzkml.jasmine.core.prompt.llm

import org.junit.Assert.*
import org.junit.Test

class ChatClientExceptionTest {

    @Test
    fun `message includes provider name`() {
        val ex = ChatClientException("DeepSeek", "连接超时", ErrorType.NETWORK)
        assertTrue(ex.message!!.contains("DeepSeek"))
        assertTrue(ex.message!!.contains("连接超时"))
    }

    @Test
    fun `provider name is accessible`() {
        val ex = ChatClientException("SiliconFlow", "error", ErrorType.UNKNOWN)
        assertEquals("SiliconFlow", ex.providerName)
    }

    @Test
    fun `cause is preserved`() {
        val cause = RuntimeException("network error")
        val ex = ChatClientException("Test", "failed", ErrorType.UNKNOWN, cause = cause)
        assertSame(cause, ex.cause)
    }

    @Test
    fun `cause defaults to null`() {
        val ex = ChatClientException("Test", "failed", ErrorType.UNKNOWN)
        assertNull(ex.cause)
    }

    @Test
    fun `error type is accessible`() {
        val ex = ChatClientException("Test", "auth failed", ErrorType.AUTHENTICATION)
        assertEquals(ErrorType.AUTHENTICATION, ex.errorType)
    }

    @Test
    fun `network errors are retryable`() {
        val ex = ChatClientException("Test", "timeout", ErrorType.NETWORK)
        assertTrue(ex.isRetryable)
    }

    @Test
    fun `rate limit errors are retryable`() {
        val ex = ChatClientException("Test", "too many requests", ErrorType.RATE_LIMIT)
        assertTrue(ex.isRetryable)
    }

    @Test
    fun `server errors are retryable`() {
        val ex = ChatClientException("Test", "internal error", ErrorType.SERVER_ERROR)
        assertTrue(ex.isRetryable)
    }

    @Test
    fun `authentication errors are not retryable`() {
        val ex = ChatClientException("Test", "invalid key", ErrorType.AUTHENTICATION)
        assertFalse(ex.isRetryable)
    }

    @Test
    fun `fromStatusCode creates correct error for 401`() {
        val ex = ChatClientException.fromStatusCode("Test", 401)
        assertEquals(ErrorType.AUTHENTICATION, ex.errorType)
        assertEquals(401, ex.statusCode)
        assertFalse(ex.isRetryable)
    }

    @Test
    fun `fromStatusCode creates correct error for 429`() {
        val ex = ChatClientException.fromStatusCode("Test", 429)
        assertEquals(ErrorType.RATE_LIMIT, ex.errorType)
        assertEquals(429, ex.statusCode)
        assertTrue(ex.isRetryable)
    }

    @Test
    fun `fromStatusCode creates correct error for 500`() {
        val ex = ChatClientException.fromStatusCode("Test", 500)
        assertEquals(ErrorType.SERVER_ERROR, ex.errorType)
        assertEquals(500, ex.statusCode)
        assertTrue(ex.isRetryable)
    }
}
