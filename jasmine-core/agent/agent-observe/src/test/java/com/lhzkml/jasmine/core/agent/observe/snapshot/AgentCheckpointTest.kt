package com.lhzkml.jasmine.core.agent.observe.snapshot

import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import org.junit.Assert.*
import org.junit.Test

class AgentCheckpointTest {

    @Test
    fun `normal checkpoint is not tombstone`() {
        val cp = AgentCheckpoint(
            checkpointId = "cp-1",
            createdAt = 1000L,
            nodePath = "processInput",
            lastInput = "hello",
            messageHistory = listOf(ChatMessage.user("hi")),
            version = 1
        )
        assertFalse(cp.isTombstone())
        assertEquals("processInput", cp.nodePath)
        assertEquals(1, cp.messageHistory.size)
    }

    @Test
    fun `tombstone checkpoint is detected`() {
        val tombstone = AgentCheckpoint.tombstone(5)
        assertTrue(tombstone.isTombstone())
        assertEquals(PersistenceUtils.TOMBSTONE_CHECKPOINT_NAME, tombstone.nodePath)
        assertTrue(tombstone.messageHistory.isEmpty())
        assertEquals(5L, tombstone.version)
    }

    @Test
    fun `typedMessageHistory converts correctly`() {
        val cp = AgentCheckpoint(
            checkpointId = "cp-1",
            createdAt = 1000L,
            nodePath = "n1",
            lastInput = null,
            messageHistory = listOf(
                ChatMessage.system("sys"),
                ChatMessage.user("q"),
                ChatMessage.assistant("a")
            ),
            version = 1
        )
        val typed = cp.typedMessageHistory
        assertEquals(3, typed.size)
    }

    @Test
    fun `RollbackStrategy enum values`() {
        assertEquals(3, RollbackStrategy.values().size)
        assertNotNull(RollbackStrategy.valueOf("RESTART_FROM_NODE"))
        assertNotNull(RollbackStrategy.valueOf("SKIP_NODE"))
        assertNotNull(RollbackStrategy.valueOf("USE_DEFAULT_OUTPUT"))
    }
}
