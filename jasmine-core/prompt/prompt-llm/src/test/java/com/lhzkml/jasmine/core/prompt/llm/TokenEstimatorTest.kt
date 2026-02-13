package com.lhzkml.jasmine.core.prompt.llm

import org.junit.Assert.*
import org.junit.Test

class TokenEstimatorTest {

    @Test
    fun `empty string is 0 tokens`() {
        assertEquals(0, TokenEstimator.estimate(""))
    }

    @Test
    fun `short ascii text`() {
        // "hello" = 5 chars, (5+3)/4 = 2
        val tokens = TokenEstimator.estimate("hello")
        assertTrue("Expected > 0, got $tokens", tokens > 0)
    }

    @Test
    fun `chinese text estimates higher than ascii`() {
        val chineseTokens = TokenEstimator.estimate("你好世界")
        val asciiTokens = TokenEstimator.estimate("abcd")
        assertTrue(
            "Chinese ($chineseTokens) should estimate more tokens than same-length ASCII ($asciiTokens)",
            chineseTokens > asciiTokens
        )
    }

    @Test
    fun `mixed content`() {
        val tokens = TokenEstimator.estimate("Hello你好World世界")
        assertTrue("Mixed content should have positive tokens", tokens > 0)
    }

    @Test
    fun `message overhead is added`() {
        val textOnly = TokenEstimator.estimate("hello")
        val withMessage = TokenEstimator.estimateMessage("user", "hello")
        assertTrue(
            "Message estimate ($withMessage) should be greater than text-only ($textOnly)",
            withMessage > textOnly
        )
    }

    @Test
    fun `message overhead includes role`() {
        val estimate = TokenEstimator.estimateMessage("user", "")
        // At minimum: MESSAGE_OVERHEAD + role tokens
        assertTrue("Empty content message should still have overhead", estimate >= TokenEstimator.MESSAGE_OVERHEAD)
    }

    @Test
    fun `longer text has more tokens`() {
        val short = TokenEstimator.estimate("hi")
        val long = TokenEstimator.estimate("This is a much longer piece of text that should have more tokens")
        assertTrue("Longer text ($long) should have more tokens than short ($short)", long > short)
    }
}
