package com.lhzkml.jasmine.core.prompt.llm

import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import org.junit.Assert.*
import org.junit.Test

class ContextManagerTest {

    @Test
    fun `empty messages returns empty`() {
        val cm = ContextManager()
        val result = cm.trimMessages(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `small conversation is not trimmed`() {
        val cm = ContextManager(maxTokens = 8192)
        val messages = listOf(
            ChatMessage.system("You are a helper."),
            ChatMessage.user("Hi"),
            ChatMessage.assistant("Hello!")
        )
        val result = cm.trimMessages(messages)
        assertEquals(3, result.size)
    }

    @Test
    fun `system messages are always preserved`() {
        // Very small budget to force trimming
        val cm = ContextManager(maxTokens = 50, reservedTokens = 0)
        val messages = listOf(
            ChatMessage.system("sys"),
            ChatMessage.user("msg1"),
            ChatMessage.user("msg2"),
            ChatMessage.user("msg3"),
            ChatMessage.user("msg4 with some longer content to use up tokens")
        )
        val result = cm.trimMessages(messages)
        // System message must be first
        assertTrue("System message should be preserved", result.any { it.role == "system" })
        assertEquals("system", result.first().role)
    }

    @Test
    fun `recent messages are preferred over older ones`() {
        // Small budget
        val cm = ContextManager(maxTokens = 80, reservedTokens = 0)
        val messages = listOf(
            ChatMessage.system("sys"),
            ChatMessage.user("old message 1"),
            ChatMessage.assistant("old reply 1"),
            ChatMessage.user("recent message"),
            ChatMessage.assistant("recent reply")
        )
        val result = cm.trimMessages(messages)

        // Should contain system + at least the most recent messages
        assertTrue(result.first().role == "system")
        // The last message in result should be the most recent
        if (result.size > 1) {
            assertEquals("recent reply", result.last().content)
        }
    }

    @Test
    fun `trimmed messages maintain order`() {
        val cm = ContextManager(maxTokens = 8192)
        val messages = listOf(
            ChatMessage.system("sys"),
            ChatMessage.user("first"),
            ChatMessage.assistant("second"),
            ChatMessage.user("third")
        )
        val result = cm.trimMessages(messages)
        assertEquals("sys", result[0].content)
        assertEquals("first", result[1].content)
        assertEquals("second", result[2].content)
        assertEquals("third", result[3].content)
    }

    @Test
    fun `availableTokens is maxTokens minus reserved`() {
        val cm = ContextManager(maxTokens = 4096, reservedTokens = 512)
        assertEquals(3584, cm.availableTokens)
    }

    @Test
    fun `isOverBudget detects excess`() {
        val cm = ContextManager(maxTokens = 20, reservedTokens = 0)
        val small = listOf(ChatMessage.user("hi"))
        val large = listOf(ChatMessage.user("a".repeat(200)))

        assertFalse(cm.isOverBudget(small))
        assertTrue(cm.isOverBudget(large))
    }

    @Test
    fun `estimateTokens returns positive for non-empty messages`() {
        val cm = ContextManager()
        val messages = listOf(
            ChatMessage.system("You are helpful."),
            ChatMessage.user("Hello")
        )
        assertTrue(cm.estimateTokens(messages) > 0)
    }

    @Test
    fun `default values`() {
        val cm = ContextManager()
        assertEquals(ContextManager.DEFAULT_MAX_TOKENS, cm.maxTokens)
        assertEquals(ContextManager.DEFAULT_RESERVED_TOKENS, cm.reservedTokens)
    }

    @Test
    fun `multiple system messages are all preserved`() {
        val cm = ContextManager(maxTokens = 100, reservedTokens = 0)
        val messages = listOf(
            ChatMessage.system("rule 1"),
            ChatMessage.system("rule 2"),
            ChatMessage.user("hello"),
            ChatMessage.assistant("hi")
        )
        val result = cm.trimMessages(messages)
        val systemCount = result.count { it.role == "system" }
        assertEquals(2, systemCount)
    }

    @Test
    fun `conversation without system messages works`() {
        val cm = ContextManager(maxTokens = 8192)
        val messages = listOf(
            ChatMessage.user("hello"),
            ChatMessage.assistant("hi")
        )
        val result = cm.trimMessages(messages)
        assertEquals(2, result.size)
    }
}
