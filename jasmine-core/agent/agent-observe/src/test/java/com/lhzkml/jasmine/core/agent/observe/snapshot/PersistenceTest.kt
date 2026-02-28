package com.lhzkml.jasmine.core.agent.observe.snapshot

import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PersistenceTest {

    private lateinit var persistence: Persistence
    private lateinit var provider: InMemoryPersistenceStorageProvider

    @Before
    fun setUp() {
        provider = InMemoryPersistenceStorageProvider()
        persistence = Persistence(provider, autoCheckpoint = true)
    }

    @Test
    fun `createCheckpoint saves and retrieves`() = runBlocking {
        val cp = persistence.createCheckpoint(
            agentId = "agent-1",
            nodePath = "node-A",
            lastInput = "hello",
            messageHistory = listOf(ChatMessage.user("hi"))
        )
        assertNotNull(cp.checkpointId)
        assertEquals("node-A", cp.nodePath)
        assertEquals("hello", cp.lastInput)
        assertEquals(1, cp.messageHistory.size)

        val latest = persistence.getLatestCheckpoint("agent-1")
        assertNotNull(latest)
        assertEquals(cp.checkpointId, latest!!.checkpointId)
    }

    @Test
    fun `getCheckpoints returns all in order`() = runBlocking {
        persistence.createCheckpoint("a1", "n1", null, emptyList())
        persistence.createCheckpoint("a1", "n2", null, emptyList())
        persistence.createCheckpoint("a1", "n3", null, emptyList())

        val all = persistence.getCheckpoints("a1")
        assertEquals(3, all.size)
    }

    @Test
    fun `getLatestCheckpoint returns highest version`() = runBlocking {
        persistence.createCheckpoint("a1", "n1", null, emptyList())
        persistence.createCheckpoint("a1", "n2", null, emptyList())

        val latest = persistence.getLatestCheckpoint("a1")
        assertEquals("n2", latest!!.nodePath)
        assertEquals(2L, latest.version)
    }

    @Test
    fun `getCheckpointById finds correct checkpoint`() = runBlocking {
        val cp1 = persistence.createCheckpoint("a1", "n1", null, emptyList())
        val cp2 = persistence.createCheckpoint("a1", "n2", null, emptyList())

        val found = persistence.getCheckpointById("a1", cp1.checkpointId)
        assertNotNull(found)
        assertEquals("n1", found!!.nodePath)

        val found2 = persistence.getCheckpointById("a1", cp2.checkpointId)
        assertEquals("n2", found2!!.nodePath)

        val notFound = persistence.getCheckpointById("a1", "nonexistent")
        assertNull(notFound)
    }

    @Test
    fun `clearCheckpoints removes all`() = runBlocking {
        persistence.createCheckpoint("a1", "n1", null, emptyList())
        persistence.createCheckpoint("a1", "n2", null, emptyList())
        persistence.clearCheckpoints("a1")

        val all = persistence.getCheckpoints("a1")
        assertTrue(all.isEmpty())
    }

    @Test
    fun `deleteCheckpoint removes single`() = runBlocking {
        val cp1 = persistence.createCheckpoint("a1", "n1", null, emptyList())
        persistence.createCheckpoint("a1", "n2", null, emptyList())
        persistence.deleteCheckpoint("a1", cp1.checkpointId)

        val all = persistence.getCheckpoints("a1")
        assertEquals(1, all.size)
        assertEquals("n2", all[0].nodePath)
    }

    @Test
    fun `markCompleted creates tombstone`() = runBlocking {
        persistence.createCheckpoint("a1", "n1", null, emptyList())
        persistence.markCompleted("a1")

        val latest = persistence.getLatestCheckpoint("a1")
        assertNotNull(latest)
        assertTrue(latest!!.isTombstone())
    }

    @Test
    fun `onNodeCompleted creates checkpoint when autoCheckpoint enabled`() = runBlocking {
        persistence.onNodeCompleted("a1", "n1", "input", listOf(ChatMessage.user("msg")))
        val all = persistence.getCheckpoints("a1")
        assertEquals(1, all.size)
    }

    @Test
    fun `onNodeCompleted skips when autoCheckpoint disabled`() = runBlocking {
        val noAuto = Persistence(provider, autoCheckpoint = false)
        noAuto.onNodeCompleted("a1", "n1", "input", emptyList())
        val all = provider.getCheckpoints("a1")
        assertTrue(all.isEmpty())
    }

    @Test
    fun `persistence with autoCheckpoint false skips on onNodeCompleted`() = runBlocking {
        val noAutoProvider = InMemoryPersistenceStorageProvider()
        val noAuto = Persistence(noAutoProvider, autoCheckpoint = false)
        noAuto.onNodeCompleted("a1", "n1", "input", emptyList())
        val all = noAutoProvider.getCheckpoints("a1")
        assertTrue(all.isEmpty())
        // But explicit createCheckpoint still works
        noAuto.createCheckpoint("a1", "n1", null, emptyList())
        assertEquals(1, noAutoProvider.getCheckpoints("a1").size)
    }

    @Test
    fun `messageHistoryDiff returns new messages`() {
        val checkpoint = listOf(ChatMessage.system("sys"), ChatMessage.user("q1"))
        val current = listOf(ChatMessage.system("sys"), ChatMessage.user("q1"), ChatMessage.assistant("a1"), ChatMessage.user("q2"))

        val diff = persistence.messageHistoryDiff(current, checkpoint)
        assertEquals(2, diff.size)
        assertEquals("assistant", diff[0].role)
        assertEquals("user", diff[1].role)
    }

    @Test
    fun `messageHistoryDiff returns empty when checkpoint is longer`() {
        val checkpoint = listOf(ChatMessage.system("sys"), ChatMessage.user("q1"), ChatMessage.assistant("a1"))
        val current = listOf(ChatMessage.system("sys"))

        val diff = persistence.messageHistoryDiff(current, checkpoint)
        assertTrue(diff.isEmpty())
    }

    @Test
    fun `messageHistoryDiff returns empty when messages diverge`() {
        val checkpoint = listOf(ChatMessage.system("sys"), ChatMessage.user("q1"))
        val current = listOf(ChatMessage.system("sys"), ChatMessage.user("different"), ChatMessage.assistant("a1"))

        val diff = persistence.messageHistoryDiff(current, checkpoint)
        assertTrue(diff.isEmpty())
    }

    @Test
    fun `rebuildHistoryFromCheckpoints combines correctly`() {
        val cp1 = AgentCheckpoint("c1", 1000, "n1", null, listOf(ChatMessage.user("q1"), ChatMessage.assistant("a1")), 1)
        val cp2 = AgentCheckpoint("c2", 2000, "n2", null, listOf(ChatMessage.user("q2"), ChatMessage.assistant("a2")), 2)

        val rebuilt = Persistence.rebuildHistoryFromCheckpoints(listOf(cp1, cp2), "You are a helper")
        assertEquals(5, rebuilt.size)
        assertEquals("system", rebuilt[0].role)
        assertEquals("You are a helper", rebuilt[0].content)
        assertEquals("q1", rebuilt[1].content)
        assertEquals("a2", rebuilt[4].content)
    }
}
