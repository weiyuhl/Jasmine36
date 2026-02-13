package com.lhzkml.jasmine.core.conversation.storage.entity

import org.junit.Assert.*
import org.junit.Test

class EntityTest {

    @Test
    fun `ConversationEntity data class equality`() {
        val a = ConversationEntity("id1", "title", "deepseek", "model", 1000L, 2000L)
        val b = ConversationEntity("id1", "title", "deepseek", "model", 1000L, 2000L)
        assertEquals(a, b)
    }

    @Test
    fun `ConversationEntity copy updates fields`() {
        val original = ConversationEntity("id1", "old", "deepseek", "model", 1000L, 2000L)
        val updated = original.copy(title = "new", updatedAt = 3000L)
        assertEquals("new", updated.title)
        assertEquals(3000L, updated.updatedAt)
        assertEquals("id1", updated.id)
    }

    @Test
    fun `MessageEntity default id is 0`() {
        val msg = MessageEntity(
            conversationId = "conv1",
            role = "user",
            content = "hello",
            createdAt = 1000L
        )
        assertEquals(0L, msg.id)
    }

    @Test
    fun `MessageEntity data class equality`() {
        val a = MessageEntity(1, "conv1", "user", "hello", 1000L)
        val b = MessageEntity(1, "conv1", "user", "hello", 1000L)
        assertEquals(a, b)
    }

    @Test
    fun `MessageEntity stores all roles`() {
        val system = MessageEntity(conversationId = "c", role = "system", content = "sys", createdAt = 1L)
        val user = MessageEntity(conversationId = "c", role = "user", content = "usr", createdAt = 2L)
        val assistant = MessageEntity(conversationId = "c", role = "assistant", content = "ast", createdAt = 3L)

        assertEquals("system", system.role)
        assertEquals("user", user.role)
        assertEquals("assistant", assistant.role)
    }

    @Test
    fun `UsageEntity default id is 0`() {
        val usage = UsageEntity(
            conversationId = "conv1",
            providerId = "deepseek",
            model = "deepseek-chat",
            promptTokens = 100,
            completionTokens = 50,
            totalTokens = 150,
            createdAt = 1000L
        )
        assertEquals(0L, usage.id)
    }

    @Test
    fun `UsageEntity data class equality`() {
        val a = UsageEntity(1, "c1", "deepseek", "model", 10, 20, 30, 1000L)
        val b = UsageEntity(1, "c1", "deepseek", "model", 10, 20, 30, 1000L)
        assertEquals(a, b)
    }

    @Test
    fun `UsageEntity stores token counts`() {
        val usage = UsageEntity(
            conversationId = "c",
            providerId = "siliconflow",
            model = "deepseek-v3",
            promptTokens = 500,
            completionTokens = 200,
            totalTokens = 700,
            createdAt = 1L
        )
        assertEquals(500, usage.promptTokens)
        assertEquals(200, usage.completionTokens)
        assertEquals(700, usage.totalTokens)
        assertEquals("siliconflow", usage.providerId)
    }
}
