package com.lhzkml.jasmine.core.prompt.llm

import com.lhzkml.jasmine.core.prompt.model.Tokenizer
import org.junit.Assert.*
import org.junit.Test

class TokenEstimatorTest {

    @Test
    fun `empty string is 0 tokens`() {
        assertEquals(0, TokenEstimator.countTokens(""))
    }

    @Test
    fun `short ascii text`() {
        val tokens = TokenEstimator.countTokens("hello")
        assertTrue("Expected > 0, got $tokens", tokens > 0)
    }

    @Test
    fun `chinese text estimates higher than ascii`() {
        val chineseTokens = TokenEstimator.countTokens("你好世界")
        val asciiTokens = TokenEstimator.countTokens("abcd")
        assertTrue(
            "Chinese ($chineseTokens) should estimate more tokens than same-length ASCII ($asciiTokens)",
            chineseTokens > asciiTokens
        )
    }

    @Test
    fun `mixed content`() {
        val tokens = TokenEstimator.countTokens("Hello你好World世界")
        assertTrue("Mixed content should have positive tokens", tokens > 0)
    }

    @Test
    fun `message overhead is added`() {
        val textOnly = TokenEstimator.countTokens("hello")
        val withMessage = TokenEstimator.countMessageTokens("user", "hello")
        assertTrue(
            "Message estimate ($withMessage) should be greater than text-only ($textOnly)",
            withMessage > textOnly
        )
    }

    @Test
    fun `message overhead includes role`() {
        val estimate = TokenEstimator.countMessageTokens("user", "")
        assertTrue("Empty content message should still have overhead", estimate >= Tokenizer.MESSAGE_OVERHEAD)
    }

    @Test
    fun `longer text has more tokens`() {
        val short = TokenEstimator.countTokens("hi")
        val long = TokenEstimator.countTokens("This is a much longer piece of text that should have more tokens")
        assertTrue("Longer text ($long) should have more tokens than short ($short)", long > short)
    }

    @Test
    fun `implements Tokenizer interface`() {
        val tokenizer: Tokenizer = TokenEstimator
        assertTrue(tokenizer.countTokens("hello") > 0)
        assertTrue(tokenizer.countMessageTokens("user", "hello") > tokenizer.countTokens("hello"))
    }

    @Test
    fun `legacy estimate method still works`() {
        assertEquals(TokenEstimator.countTokens("hello"), TokenEstimator.estimate("hello"))
    }

    @Test
    fun `legacy estimateMessage method still works`() {
        assertEquals(
            TokenEstimator.countMessageTokens("user", "hello"),
            TokenEstimator.estimateMessage("user", "hello")
        )
    }
}
