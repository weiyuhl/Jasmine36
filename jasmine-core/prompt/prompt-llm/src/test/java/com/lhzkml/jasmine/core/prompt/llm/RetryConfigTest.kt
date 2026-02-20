package com.lhzkml.jasmine.core.prompt.llm

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RetryConfigTest {

    @Test
    fun `default config has reasonable values`() {
        val config = RetryConfig.DEFAULT
        assertEquals(3, config.maxRetries)
        assertEquals(1000L, config.initialDelayMs)
        assertEquals(10000L, config.maxDelayMs)
        assertEquals(2.0, config.backoffMultiplier, 0.01)
        assertEquals(600000L, config.requestTimeoutMs)
        assertEquals(30000L, config.connectTimeoutMs)
        assertEquals(300000L, config.socketTimeoutMs)
    }

    @Test
    fun `executeWithRetry succeeds on first attempt`() = runBlocking {
        var attempts = 0
        val result = executeWithRetry {
            attempts++
            "success"
        }
        assertEquals("success", result)
        assertEquals(1, attempts)
    }

    @Test
    fun `executeWithRetry retries on retryable error`() = runBlocking {
        var attempts = 0
        try {
            executeWithRetry(RetryConfig(maxRetries = 2, initialDelayMs = 10)) {
                attempts++
                throw ChatClientException("Test", "network error", ErrorType.NETWORK)
            }
            fail("Should have thrown exception")
        } catch (e: ChatClientException) {
            assertEquals(3, attempts) // initial + 2 retries
        }
    }

    @Test
    fun `executeWithRetry does not retry on non-retryable error`() = runBlocking {
        var attempts = 0
        try {
            executeWithRetry(RetryConfig(maxRetries = 2, initialDelayMs = 10)) {
                attempts++
                throw ChatClientException("Test", "auth failed", ErrorType.AUTHENTICATION)
            }
            fail("Should have thrown exception")
        } catch (e: ChatClientException) {
            assertEquals(1, attempts) // no retries
        }
    }

    @Test
    fun `executeWithRetry succeeds after retry`() = runBlocking {
        var attempts = 0
        val result = executeWithRetry(RetryConfig(maxRetries = 2, initialDelayMs = 10)) {
            attempts++
            if (attempts < 2) {
                throw ChatClientException("Test", "network error", ErrorType.NETWORK)
            }
            "success"
        }
        assertEquals("success", result)
        assertEquals(2, attempts)
    }
}
