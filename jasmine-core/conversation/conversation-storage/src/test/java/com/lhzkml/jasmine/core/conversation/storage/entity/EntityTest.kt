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
}
